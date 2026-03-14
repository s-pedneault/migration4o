package migration4o.migration.tasks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.db4o.ext.ExtObjectContainer;

import migration4o.migration.recipes.ObjectActivator;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.schema.DOSchemaModule;
import migration4o.models.schema.DOSchemaReference;
import migration4o.models.ui.ClassExportConfig;
import migration4o.ui.common.DOExportMonitor;
import migration4o.util.DatabaseUtil;
import migration4o.util.ObjectResolverUtil;
import migration4o.util.TypeUtil;

/**
 * Pre-flight analyser that selects the best N objects per class when an export
 * cap is active, favouring objects that are mutually cross-referenced with
 * objects in other exported classes.
 *
 * <h2>Algorithm overview</h2>
 * <ol>
 * <li>Collect all classes being exported and their DB object ID arrays.</li>
 * <li>Find <em>reference edges</em> by reading each exported class's
 * {@code DOSchemaClass.schemaReferences} — populated at startup by
 * {@code DOReferenceDetector}, which already resolves IDEntite indirection.
 * Each edge carries whether it is IDEntite-mediated so the DB scan below can
 * choose the right resolution path.</li>
 * <li>Scan every source-class object: read the relevant field via
 * {@link DatabaseUtil#getStoredFieldValue}, then resolve to the target object
 * ID — directly via {@code container.ext().getID()} for plain references, or
 * via {@link ReferenceUtil#resolveIDEntiteReference} for IDEntite-mediated
 * ones.</li>
 * <li>Run iterative scoring passes (source pass then target pass) until stable,
 * keeping the top-N objects per class at each step.</li>
 * <li>Return the final selections as {@code Map<className, long[]>} in original
 * DB order for deterministic output.</li>
 * </ol>
 */
public class ExportSelectionAdvisor {

    // ── Edge record ──────────────────────────────────────────────────────────

    static final class ReferenceEdge {
        final String sourceClass; // fully-qualified source class name
        final String fieldName; // DB4O / source field name (from schema
                                // field.source)
        final String targetClass; // fully-qualified target class name
        final boolean isIDEntite; // true → resolve via
                                  // ReferenceUtil.resolveIDEntiteReference

        ReferenceEdge(String sourceClass, String fieldName, String targetClass, boolean isIDEntite) {
            this.sourceClass = sourceClass;
            this.fieldName = fieldName;
            this.targetClass = targetClass;
            this.isIDEntite = isIDEntite;
        }
    }

    // ── Result record ─────────────────────────────────────────────────────

    /**
     * Holds both outputs of {@link #computeSelection}:
     * <ul>
     * <li>{@code rankedIds} — full per-class ID arrays, required objects first.
     * <li>{@code requiredCounts} — how many leading IDs in each array are
     * "required" (closure-driven) and must be exported unconditionally.
     * </ul>
     */
    public static final class SelectionResult {
        public final Map<String, long[]> rankedIds;
        public final Map<String, Integer> requiredCounts;

        SelectionResult(Map<String, long[]> rankedIds, Map<String, Integer> requiredCounts) {
            this.rankedIds = rankedIds;
            this.requiredCounts = requiredCounts;
        }
    }

    // ── Fields ───────────────────────────────────────────────────────────────

    private final ExtObjectContainer container;
    private final DOSchema referenceSchema;
    private final DOSchema databaseSchema;
    private final int cap;
    private final List<migration4o.models.ui.SeedQuery> seedQueries;

    // ── Constructors ─────────────────────────────────────────────────────────

    public ExportSelectionAdvisor(ExtObjectContainer container, DOSchema referenceSchema, DOSchema databaseSchema, int cap) {
        this.container = container;
        this.referenceSchema = referenceSchema;
        this.databaseSchema = databaseSchema;
        this.cap = cap;
        this.seedQueries = null;
    }

    /**
     * Creates a seed-based advisor. When {@code seedCap} is non-null and > 0,
     * seed query results are limited to that many objects per seed class before
     * closure propagation.
     */
    public ExportSelectionAdvisor(ExtObjectContainer container, DOSchema referenceSchema, DOSchema databaseSchema, List<migration4o.models.ui.SeedQuery> seedQueries, Integer seedCap) {
        this.container = container;
        this.referenceSchema = referenceSchema;
        this.databaseSchema = databaseSchema;
        this.cap = (seedCap != null && seedCap > 0) ? seedCap : 0;
        this.seedQueries = seedQueries;
    }

    // ── Public entry point ───────────────────────────────────────────────────

    /**
     * Analyses the modules to export and returns a {@link SelectionResult}
     * containing per-class ranked ID arrays (required objects first) and the
     * count of required objects at the front of each array.
     *
     * @param modules modules that will be exported
     * @param monitor optional progress monitor (may be null)
     * @return selection result; maps are empty when no actionable edges exist
     */
    public SelectionResult computeSelection(List<DOSchemaModule> modules, DOExportMonitor monitor) {
        // Step 1 – collect all exported classes
        Map<String, long[]> classObjectIds = new LinkedHashMap<>();
        for (DOSchemaModule m : modules) {
            collectClasses(m, classObjectIds);
        }
        if (classObjectIds.isEmpty()) {
            return new SelectionResult(Collections.emptyMap(), Collections.emptyMap());
        }

        Set<String> exportedNames = classObjectIds.keySet();

        // Step 2 – find reference edges using the pre-built schemaReferences
        // graph
        List<ReferenceEdge> edges = findReferenceEdges(exportedNames);
        if (edges.isEmpty()) {
            return new SelectionResult(Collections.emptyMap(), Collections.emptyMap());
        }

        // Step 3 – scan DB objects to resolve actual object-ID relationships
        status(monitor, "Smart selection: scanning object references\u2026");
        Map<ReferenceEdge, Map<Long, Long>> edgeData = scanReferences(edges, classObjectIds, monitor);
        // Step 4 \u2013 closure propagation: guarantee every referenced object
        // is included
        status(monitor, "Smart selection: propagating referential closure\u2026");
        SelectionResult result = buildRankedOrder(classObjectIds, edges, edgeData);
        return result;
    }

    // ── Seed-based entry point ───────────────────────────────────────────────

    /**
     * Seed-based selection: executes user-defined queries to find initial
     * objects, then examines each seed class's {@code schemaReferences} to
     * discover objects in other classes that reference the seed objects.
     *
     * <h3>Algorithm</h3>
     * <ol>
     * <li>Execute seed queries to find matching objects.</li>
     * <li>For each seed class, read its {@code schemaReferences} to find which
     * exported classes reference it.</li>
     * <li>Scan those referencing classes to find the specific objects that
     * point to seed objects — these become <em>required</em> (cap-exempt).</li>
     * <li>All other classes are left without preselection, so the normal cap
     * applies at export time via {@link ObjectExportLoop}.</li>
     * </ol>
     *
     * @param modules modules that will be exported
     * @param monitor optional progress monitor (may be null)
     * @return selection result with seed objects and their related references
     */
    public SelectionResult computeSeedSelection(List<DOSchemaModule> modules, DOExportMonitor monitor) {
        if (seedQueries == null || seedQueries.isEmpty()) {
            return new SelectionResult(Collections.emptyMap(), Collections.emptyMap());
        }

        // Step 1 – collect all exported classes
        Map<String, long[]> classObjectIds = new LinkedHashMap<>();
        for (DOSchemaModule m : modules) {
            collectClasses(m, classObjectIds);
        }
        if (classObjectIds.isEmpty()) {
            return new SelectionResult(Collections.emptyMap(), Collections.emptyMap());
        }

        Set<String> exportedNames = classObjectIds.keySet();

        // Step 2 – execute seed queries to find initial matching objects
        status(monitor, "Seed selection: executing seed queries\u2026");
        Map<String, Set<Long>> seedObjects = executeSeedQueries(classObjectIds, monitor);

        // Apply per-class cap to seed matches before reference scan
        if (cap > 0) {
            for (Map.Entry<String, Set<Long>> entry : seedObjects.entrySet()) {
                Set<Long> matches = entry.getValue();
                if (matches.size() > cap) {
                    Set<Long> capped = new LinkedHashSet<>();
                    int count = 0;
                    for (Long id : matches) {
                        if (count++ >= cap)
                            break;
                        capped.add(id);
                    }
                    entry.setValue(capped);
                }
            }
        }

        int totalSeeds = 0;
        for (Set<Long> s : seedObjects.values())
            totalSeeds += s.size();
        if (totalSeeds == 0) {
            return new SelectionResult(Collections.emptyMap(), Collections.emptyMap());
        }

        // Step 3 – for each seed class, find objects in other classes that
        // reference the seed objects (via schemaReferences)
        status(monitor, "Seed selection: finding objects that reference seed objects\u2026");
        Map<String, Set<Long>> relatedObjects = findObjectsReferencingSeeds(classObjectIds, exportedNames, seedObjects, monitor);

        // Step 4 – build output:
        // - Seed classes: ONLY seed-matched objects, all required
        // - Referencing classes: related objects required (cap-exempt), then
        // fill
        // - Other classes: no preselection (cap applies normally at export
        // time)
        return buildSeedResult(classObjectIds, seedObjects, relatedObjects);
    }

    // ── Seed query execution ────────────────────────────────────────────────

    /**
     * Executes each SeedQuery against the database and collects matching object
     * IDs.
     */
    private Map<String, Set<Long>> executeSeedQueries(Map<String, long[]> classObjectIds, DOExportMonitor monitor) {
        Map<String, Set<Long>> result = new LinkedHashMap<>();

        for (migration4o.models.ui.SeedQuery query : seedQueries) {
            String className = query.getClassName();
            long[] objectIds = classObjectIds.get(className);
            if (objectIds == null) {
                continue;
            }

            status(monitor, "Seed selection: querying " + simpleClassName(className) + " (" + objectIds.length + " objects)\u2026");

            Set<Long> matches = result.computeIfAbsent(className, k -> new LinkedHashSet<>());
            List<migration4o.models.ui.SeedCondition> conditions = query.getConditions();

            System.out.println("[DEBUG-DossPrev] SeedQuery for '" + className + "': conditions=" + (conditions != null ? conditions.size() : "null") + ", objectIds=" + objectIds.length);

            // Pre-translate all condition destinationName paths to DB source
            // paths
            List<String> sourceFieldPaths = new ArrayList<>();
            boolean isDossPrev = className.contains("DossPrev");
            if (conditions != null) {
                for (migration4o.models.ui.SeedCondition cond : conditions) {
                    String sourcePath = resolveDestinationPathToSourcePath(className, cond.getFieldPath());
                    sourceFieldPaths.add(sourcePath);
                    if (isDossPrev) {
                        System.out.println("[DEBUG-DossPrev] executeSeedQueries: fieldPath='" + cond.getFieldPath() + "' -> sourcePath='" + sourcePath + "', operator=" + cond.getOperator() + ", value='" + cond.getValue() + "'");
                    }
                }
            }

            int matchCount = 0;
            int nullFieldCount = 0;
            int samplesPrinted = 0;
            final int MAX_SAMPLES = 5;

            for (long objId : objectIds) {
                try {
                    ObjectActivator.ActivationResult activation = ObjectActivator.getAndActivate(container, objId);
                    if (activation == null)
                        continue;

                    boolean allMatch = true;
                    if (conditions != null && !conditions.isEmpty()) {
                        for (int i = 0; i < conditions.size(); i++) {
                            String sourcePath = sourceFieldPaths.get(i);
                            if (sourcePath == null) {
                                allMatch = false;
                                break;
                            }
                            Object fieldValue = DatabaseUtil.getFieldValueByPath(container, activation.object, sourcePath);
                            if (isDossPrev && samplesPrinted < MAX_SAMPLES) {
                                System.out.println("[DEBUG-DossPrev] Object " + objId + ": field '" + sourcePath + "' = '" + fieldValue + "'" + " (type=" + (fieldValue != null ? fieldValue.getClass().getSimpleName() : "null") + ")" + ", matches=" + conditions.get(i).matches(fieldValue));
                                samplesPrinted++;
                            }
                            if (fieldValue == null)
                                nullFieldCount++;
                            if (!conditions.get(i).matches(fieldValue)) {
                                allMatch = false;
                                break;
                            }
                        }
                    }
                    // If no conditions, match all objects of this class
                    if (allMatch) {
                        matches.add(objId);
                        matchCount++;
                    }
                } catch (Exception e) {
                    // best-effort
                }
            }
            if (isDossPrev) {
                System.out.println("[DEBUG-DossPrev] executeSeedQueries: " + matchCount + " match(es) out of " + objectIds.length + " for " + className + " (nullFields=" + nullFieldCount + ", conditions=" + (conditions != null ? conditions.size() : 0) + ")");
            }
        }
        return result;
    }

    // ── Seed reference scan ────────────────────────────────────────────────

    /**
     * For each seed class, examines its {@code schemaReferences} to find which
     * exported classes reference it, then scans those classes to find the
     * specific objects that point to seed objects.
     *
     * @return map of className → set of object IDs that reference seed objects
     */
    private Map<String, Set<Long>> findObjectsReferencingSeeds(Map<String, long[]> classObjectIds, Set<String> exportedNames, Map<String, Set<Long>> seedObjects, DOExportMonitor monitor) {

        Map<String, Set<Long>> relatedObjects = new LinkedHashMap<>();

        // Pre-collect exported DOSchemaClass objects for descendant expansion
        List<DOSchemaClass> exportedClasses = new ArrayList<>();
        for (String name : exportedNames) {
            DOSchemaClass cls = referenceSchema.findClassByName(name);
            if (cls != null)
                exportedClasses.add(cls);
        }

        for (String seedClassName : seedObjects.keySet()) {
            Set<Long> seedIds = seedObjects.get(seedClassName);
            if (seedIds == null || seedIds.isEmpty())
                continue;

            DOSchemaClass seedClass = referenceSchema.findClassByName(seedClassName);
            if (seedClass == null || seedClass.schemaReferences == null) {
                System.out.println("[Seed] No schemaReferences on seed class '" + seedClassName + "'");
                continue;
            }

            System.out.println("[Seed] Seed class '" + simpleClassName(seedClassName) + "' has " + seedClass.schemaReferences.length + " schema reference(s), " + seedIds.size() + " seed object(s)");

            // Build edges from classes that reference this seed class
            List<ReferenceEdge> seedEdges = new ArrayList<>();
            for (DOSchemaReference ref : seedClass.schemaReferences) {
                String declaringClass = ref.className;
                if (declaringClass.equals(seedClassName))
                    continue;

                // Resolve concrete exported source classes (handle inherited
                // fields)
                List<String> srcNames = new ArrayList<>();
                if (exportedNames.contains(declaringClass)) {
                    srcNames.add(declaringClass);
                } else {
                    for (DOSchemaClass candidate : exportedClasses) {
                        if (!candidate.source.equals(seedClassName) && candidate.isDescendantOf(declaringClass, referenceSchema)) {
                            srcNames.add(candidate.source);
                        }
                    }
                }

                for (String srcName : srcNames) {
                    // Skip if source is also a seed class (its selection
                    // is determined by its own seed query)
                    if (seedObjects.containsKey(srcName) && !seedObjects.get(srcName).isEmpty())
                        continue;

                    DOSchemaClass srcClass = referenceSchema.findClassByName(srcName);
                    if (srcClass == null)
                        continue;
                    DOSchemaField schemaField = DatabaseUtil.findSchemaFieldByNameIncludingAncestors(srcClass, ref.fieldName, referenceSchema);
                    DOSchemaField effectiveField = schemaField;
                    if (schemaField != null && schemaField.isSharedField()) {
                        DOSchemaField def = referenceSchema.sharedFields.get(schemaField.definitionId);
                        if (def != null)
                            effectiveField = def;
                    }
                    if (effectiveField != null && !effectiveField.isExported)
                        continue;

                    boolean isIDEntite = effectiveField != null && TypeUtil.isIDEntiteField(effectiveField, referenceSchema);

                    if (!containsEdge(seedEdges, srcName, ref.fieldName, seedClassName)) {
                        seedEdges.add(new ReferenceEdge(srcName, ref.fieldName, seedClassName, isIDEntite));
                        System.out.println("[Seed]   Edge: " + simpleClassName(srcName) + "." + ref.fieldName + " \u2192 " + simpleClassName(seedClassName) + (isIDEntite ? " (IDEntite)" : " (direct)"));
                    }
                }
            }

            if (seedEdges.isEmpty()) {
                System.out.println("[Seed]   No applicable edges for seed class '" + simpleClassName(seedClassName) + "'");
                continue;
            }

            // Scan the referencing classes to find which objects reference seed
            // objects
            Map<ReferenceEdge, Map<Long, Long>> edgeData = scanReferences(seedEdges, classObjectIds, monitor);

            for (ReferenceEdge edge : seedEdges) {
                Map<Long, Long> links = edgeData.get(edge);
                if (links == null)
                    continue;

                int linkedCount = 0;
                for (Map.Entry<Long, Long> link : links.entrySet()) {
                    Long srcId = link.getKey();
                    Long tgtId = link.getValue();
                    if (seedIds.contains(tgtId)) {
                        relatedObjects.computeIfAbsent(edge.sourceClass, k -> new LinkedHashSet<>()).add(srcId);
                        linkedCount++;
                    }
                }
                if (linkedCount > 0) {
                    System.out.println("[Seed]   Found " + linkedCount + " " + simpleClassName(edge.sourceClass) + " object(s) referencing seed " + simpleClassName(seedClassName) + " via " + edge.fieldName);
                }
            }
        }
        return relatedObjects;
    }

    /**
     * Builds the final {@link SelectionResult} from seed objects and their
     * related objects.
     * <ul>
     * <li><b>Seed classes</b>: export ONLY seed-matched objects (all required,
     * cap-exempt).</li>
     * <li><b>Referencing classes</b>: related objects are required (cap-exempt)
     * and placed first, then remaining objects fill up to cap.</li>
     * <li><b>Other classes</b>: not included in preselection — the normal cap
     * applies at export time via {@link ObjectExportLoop}.</li>
     * </ul>
     */
    private SelectionResult buildSeedResult(Map<String, long[]> classObjectIds, Map<String, Set<Long>> seedObjects, Map<String, Set<Long>> relatedObjects) {

        Map<String, long[]> rankedIds = new LinkedHashMap<>();
        Map<String, Integer> requiredCounts = new LinkedHashMap<>();

        for (Map.Entry<String, long[]> e : classObjectIds.entrySet()) {
            String cls = e.getKey();
            long[] allIds = e.getValue();

            boolean isSeedClass = seedObjects.containsKey(cls) && seedObjects.get(cls) != null && !seedObjects.get(cls).isEmpty();
            Set<Long> related = relatedObjects.get(cls);
            boolean hasRelated = related != null && !related.isEmpty();

            if (isSeedClass) {
                // Seed classes: export ONLY matched objects, all required
                Set<Long> seedIds = seedObjects.get(cls);
                long[] ranked = new long[seedIds.size()];
                int idx = 0;
                for (long id : allIds) {
                    if (seedIds.contains(id))
                        ranked[idx++] = id;
                }
                rankedIds.put(cls, ranked);
                requiredCounts.put(cls, idx);
                System.out.println("[Seed] Result: " + simpleClassName(cls) + " \u2192 " + idx + " seed object(s) (all required)");

            } else if (hasRelated) {
                // Referencing classes: related objects first
                // (required/cap-exempt), then fill
                long[] ranked = new long[allIds.length];
                int idx = 0;
                for (long id : allIds) {
                    if (related.contains(id))
                        ranked[idx++] = id;
                }
                int reqCount = idx;
                for (long id : allIds) {
                    if (!related.contains(id))
                        ranked[idx++] = id;
                }
                rankedIds.put(cls, ranked);
                requiredCounts.put(cls, reqCount);
                System.out.println("[Seed] Result: " + simpleClassName(cls) + " \u2192 " + reqCount + " required + " + (allIds.length - reqCount) + " fill (total " + allIds.length + ")");
            }
            // Other classes: not included in preselection → cap applies
            // normally
        }
        return new SelectionResult(rankedIds, requiredCounts);
    }

    // ── Step 1: collect exported classes ────────────────────────────────────

    private void collectClasses(DOSchemaModule module, Map<String, long[]> out) {
        for (ClassExportConfig cfg : module.classConfigs) {
            String name = cfg.getClassName();
            if (!out.containsKey(name)) {
                // IMPORTANT: objectIds are populated only on the *database*
                // schema
                // (from storedClass.getIDs()). The reference schema never has
                // them.
                DOSchemaClass dbClass = databaseSchema.findClassByName(name);
                if (dbClass != null && dbClass.objectIds != null && dbClass.objectIds.length > 0) {
                    out.put(name, dbClass.objectIds);

                }
            }
        }
        for (DOSchemaModule child : module.children) {
            collectClasses(child, out);
        }
    }

    // ── Step 2: find reference edges ─────────────────────────────────────────

    /**
     * Builds the edge list from the pre-built {@code schemaReferences} graph.
     * {@code DOReferenceDetector} populates
     * {@code schemaClass.schemaReferences} at startup and already resolves
     * IDEntite indirection, so we don't need to re-scan fields manually.
     *
     * <p>
     * <b>Inherited-field expansion:</b> {@code DOReferenceDetector} records the
     * <em>declaring</em> class of each field as the reference source (e.g.
     * {@code gest.gen.EntiteContientID} for the inherited {@code mIDDossPrev}
     * field). When that declaring class is not itself exported, we expand the
     * edge to every exported subclass of the declaring class — for example
     * {@code Prevention}, {@code Intervention}, etc. — so that each concrete
     * exported class that inherits the field is properly connected to its
     * target.
     */
    private List<ReferenceEdge> findReferenceEdges(Set<String> exportedNames) {
        List<ReferenceEdge> edges = new ArrayList<>();

        // Pre-collect exported DOSchemaClass objects for descendant expansion.
        List<DOSchemaClass> exportedClasses = new ArrayList<>();
        for (String name : exportedNames) {
            DOSchemaClass cls = referenceSchema.findClassByName(name);
            if (cls != null)
                exportedClasses.add(cls);
        }

        // For each exported class that is a *target*, read who points to it.
        for (String tgtName : exportedNames) {
            DOSchemaClass tgtClass = referenceSchema.findClassByName(tgtName);
            if (tgtClass == null || tgtClass.schemaReferences == null)
                continue;

            for (DOSchemaReference ref : tgtClass.schemaReferences) {
                String declaringClass = ref.className;
                if (declaringClass.equals(tgtName))
                    continue;

                // Collect the concrete exported source classes for this
                // reference.
                // Case A: the declaring class is itself exported → single edge.
                // Case B: the declaring class is a base class → expand to all
                // exported subclasses that inherit the field.
                List<String> srcNames = new ArrayList<>();
                if (exportedNames.contains(declaringClass)) {
                    srcNames.add(declaringClass);
                } else {
                    for (DOSchemaClass candidate : exportedClasses) {
                        if (!candidate.source.equals(tgtName) && candidate.isDescendantOf(declaringClass, referenceSchema)) {
                            srcNames.add(candidate.source);
                        }
                    }
                }

                for (String srcName : srcNames) {
                    // Locate the schema field (walking ancestors) to get
                    // IDEntite status.
                    DOSchemaClass srcClass = referenceSchema.findClassByName(srcName);
                    if (srcClass == null)
                        continue;
                    DOSchemaField schemaField = DatabaseUtil.findSchemaFieldByNameIncludingAncestors(srcClass, ref.fieldName, referenceSchema);
                    // Resolve shared field definition — a reference-only field
                    // (definition="...")
                    // carries no inline type or isExported flag; those live in
                    // the shared definition.
                    DOSchemaField effectiveField = schemaField;
                    if (schemaField != null && schemaField.isSharedField()) {
                        DOSchemaField def = referenceSchema.sharedFields.get(schemaField.definitionId);
                        if (def != null)
                            effectiveField = def;
                    }
                    // Skip if the effective field is explicitly marked not
                    // exported.
                    if (effectiveField != null && !effectiveField.isExported)
                        continue;

                    boolean isIDEntite = effectiveField != null && TypeUtil.isIDEntiteField(effectiveField, referenceSchema);

                    if (!containsEdge(edges, srcName, ref.fieldName, tgtName)) {
                        edges.add(new ReferenceEdge(srcName, ref.fieldName, tgtName, isIDEntite));
                    }
                }
            }
        }
        return edges;
    }

    private static boolean containsEdge(List<ReferenceEdge> edges, String src, String field, String tgt) {
        for (ReferenceEdge e : edges) {
            if (e.sourceClass.equals(src) && e.fieldName.equals(field) && e.targetClass.equals(tgt))
                return true;
        }
        return false;
    }

    // ── Step 3a: build mID → objectId index for IDEntite target classes ──────

    /**
     * Pre-builds a lookup map {@code targetClassName → (mID → objectId)} for
     * every class that is the target of an IDEntite edge. This avoids O(n²)
     * linear scans inside {@link ReferenceUtil#findObjectByMID} per source
     * object.
     */
    private Map<String, Map<Long, Long>> buildMIDIndex(List<ReferenceEdge> edges) {
        // Collect the IDEntite target class names we care about.
        Set<String> idEntiteTargets = new LinkedHashSet<>();
        for (ReferenceEdge e : edges) {
            if (e.isIDEntite)
                idEntiteTargets.add(e.targetClass);
        }

        Map<String, Map<Long, Long>> index = new HashMap<>();
        for (String targetClass : idEntiteTargets) {
            Map<Long, Long> midMap = new HashMap<>();
            DOSchemaClass dbClass = databaseSchema.findClassByName(targetClass);
            if (dbClass == null || dbClass.objectIds == null) {
                index.put(targetClass, midMap);
                continue;
            }
            for (long objId : dbClass.objectIds) {
                try {
                    ObjectActivator.ActivationResult activation = ObjectActivator.getAndActivate(container, objId);
                    if (activation == null)
                        continue;
                    // Read mID using the proven ancestor-walking pattern
                    // (mID is often declared on a parent class like Entite)
                    Object midVal = DatabaseUtil.getStoredFieldValue(container, activation.object, "mID");
                    if (midVal instanceof Number) {
                        long mid = ((Number) midVal).longValue();
                        if (mid > 0)
                            midMap.put(mid, objId);
                    }
                } catch (Exception ignored) {
                    // best-effort
                }
            }
            index.put(targetClass, midMap);
        }
        return index;
    }

    // ── Step 3b: scan DB objects
    // ──────────────────────────────────────────────

    /**
     * For each edge, builds a map {@code sourceObjectId → targetObjectId}. Uses
     * {@link ObjectActivator#getAndActivate} for retrieval,
     * {@link DatabaseUtil#getAllFieldsIncludingAncestors} for field reading,
     * and {@link ObjectResolverUtil} for target ID resolution.
     */
    private Map<ReferenceEdge, Map<Long, Long>> scanReferences(List<ReferenceEdge> edges, Map<String, long[]> classObjectIds, DOExportMonitor monitor) {

        // Pre-build mID → objectId index for all IDEntite target classes
        Map<String, Map<Long, Long>> midIndex = buildMIDIndex(edges);

        Map<ReferenceEdge, Map<Long, Long>> result = new LinkedHashMap<>();
        for (ReferenceEdge e : edges)
            result.put(e, new HashMap<>());

        // Group edges by source class so each object is activated only once
        Map<String, List<ReferenceEdge>> bySource = new LinkedHashMap<>();
        for (ReferenceEdge e : edges) {
            bySource.computeIfAbsent(e.sourceClass, k -> new ArrayList<>()).add(e);
        }

        for (Map.Entry<String, List<ReferenceEdge>> entry : bySource.entrySet()) {
            String srcClass = entry.getKey();
            List<ReferenceEdge> classEdges = entry.getValue();
            long[] objectIds = classObjectIds.get(srcClass);
            if (objectIds == null)
                continue;

            status(monitor, "Smart selection: scanning " + simpleClassName(srcClass) + " (" + objectIds.length + " objects)\u2026");

            for (long objectId : objectIds) {
                try {
                    // Use ObjectActivator — the proven recipe for object
                    // retrieval
                    ObjectActivator.ActivationResult activation = ObjectActivator.getAndActivate(container, objectId);
                    if (activation == null)
                        continue;
                    Object obj = activation.object;

                    for (ReferenceEdge edge : classEdges) {
                        // Read the field using the proven FieldExporter
                        // pattern:
                        // DatabaseUtil.getAllFieldsIncludingAncestors + name
                        // match
                        Object fv = DatabaseUtil.getStoredFieldValue(container, obj, edge.fieldName);
                        if (fv == null)
                            continue;

                        Long targetId;
                        if (edge.isIDEntite) {
                            // Activate the IDEntite wrapper, then read its mID
                            // using the same ancestor-walking pattern
                            ObjectResolverUtil.activateObjectShallow(container, fv, null);
                            Object midVal = DatabaseUtil.getStoredFieldValue(container, fv, "mID");
                            if (!(midVal instanceof Number))
                                continue;
                            long mid = ((Number) midVal).longValue();
                            if (mid <= 0)
                                continue;
                            // Look up the target object ID from the pre-built
                            // index
                            Map<Long, Long> idx = midIndex.get(edge.targetClass);
                            targetId = idx != null ? idx.get(mid) : null;
                        } else {
                            // Direct persistent reference
                            targetId = ObjectResolverUtil.getObjectId(container, fv);
                        }

                        if (targetId != null) {
                            result.get(edge).put(objectId, targetId);
                        }
                    }
                } catch (Exception ignored) {
                    // best-effort
                }
            }
        }
        return result;
    }

    // ── Step 4: referential-closure selection ───────────────────────────────

    /**
     * Builds the final per-class selection using two-phase referential closure.
     *
     * <h3>Phase 1 — Seed</h3> Each class is seeded with the first
     * {@code min(cap, total)} object IDs in original DB order.
     *
     * <h3>Phase 2 — Closure propagation</h3> For every reference edge
     * {@code S → T}, every target object that is actually referenced by a
     * currently-selected source object is unconditionally added to
     * {@code required[T]}, even if that pushes the count above {@code cap}.
     * Iteration continues until stable, naturally covering multi-hop chains.
     *
     * <h3>Phase 3 — Ranked output</h3> The FULL object-ID array is returned for
     * each class with this ordering:
     * <ol>
     * <li><b>Required</b> objects (added by closure) in original DB order —
     * these appear first regardless of their DB position, and are exempt from
     * the cap check in {@link ObjectExportLoop}.</li>
     * <li><b>Seed-only</b> objects (seeded but not required) in original DB
     * order — fill available cap slots after required.</li>
     * <li><b>Unselected</b> objects in original DB order — fallback pool for
     * criteria-filtered objects that eat into the cap.</li>
     * </ol>
     * The required count is recorded so {@link ObjectExportLoop} can skip the
     * cap check for the leading required IDs.
     */
    private SelectionResult buildRankedOrder(Map<String, long[]> classObjectIds, List<ReferenceEdge> edges, Map<ReferenceEdge, Map<Long, Long>> edgeData) {

        // ── Phase 1: seed with first min(cap, total) IDs in DB order
        // ──────────

        // seed: objects selected purely because they're first in DB order
        Map<String, Set<Long>> seed = new LinkedHashMap<>();
        for (Map.Entry<String, long[]> e : classObjectIds.entrySet()) {
            long[] ids = e.getValue();
            Set<Long> s = new LinkedHashSet<>();
            for (int i = 0; i < Math.min(cap, ids.length); i++)
                s.add(ids[i]);
            seed.put(e.getKey(), s);
        }

        // Build initial selected = seed; required starts empty.
        Map<String, Set<Long>> selected = new LinkedHashMap<>();
        for (Map.Entry<String, Set<Long>> e : seed.entrySet())
            selected.put(e.getKey(), new LinkedHashSet<>(e.getValue()));

        // required: objects added SOLELY by closure (not part of the initial
        // seed)
        Map<String, Set<Long>> required = new LinkedHashMap<>();
        for (String cls : classObjectIds.keySet())
            required.put(cls, new LinkedHashSet<>());

        // ── Phase 2: closure propagation
        // ──────────────────────────────────────

        final int MAX_ITERATIONS = 20;
        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            boolean changed = false;

            for (ReferenceEdge edge : edges) {
                Set<Long> srcSelected = selected.get(edge.sourceClass);
                Set<Long> tgtSelected = selected.get(edge.targetClass);
                Set<Long> tgtRequired = required.get(edge.targetClass);
                if (srcSelected == null || tgtSelected == null || tgtRequired == null)
                    continue;

                Map<Long, Long> links = edgeData.get(edge);
                if (links == null || links.isEmpty())
                    continue;

                // For every currently-selected source object, mark its
                // referenced
                // target as required and add it to the target's selected set.
                List<Long> toAdd = new ArrayList<>();
                for (Long srcId : srcSelected) {
                    Long tgtId = links.get(srcId);
                    if (tgtId != null && !tgtSelected.contains(tgtId)) {
                        toAdd.add(tgtId);
                    }
                }

                if (!toAdd.isEmpty()) {
                    int before = tgtSelected.size();
                    tgtSelected.addAll(toAdd);
                    tgtRequired.addAll(toAdd); // track as closure-driven
                    int added = tgtSelected.size() - before;
                    if (added > 0) {
                        changed = true;
                    }
                }
            }

            if (!changed) {
                break;
            }
        }

        // ── Phase 3: build required-first ranked arrays
        // ───────────────────────

        Map<String, long[]> rankedIds = new LinkedHashMap<>();
        Map<String, Integer> requiredCounts = new LinkedHashMap<>();

        for (Map.Entry<String, long[]> e : classObjectIds.entrySet()) {
            String cls = e.getKey();
            long[] allIds = e.getValue();
            Set<Long> sel = selected.get(cls);
            Set<Long> req = required.get(cls);

            // Emit a reordered entry when the class has more total objects than
            // the seed, OR closure pushed required beyond the initial seed.
            boolean hasRequired = req != null && !req.isEmpty();
            boolean needsReorder = allIds.length > cap || hasRequired;
            if (!needsReorder || sel == null || sel.isEmpty())
                continue;

            // Build output:
            // 1. Required objects first (in original DB order)
            // 2. Seed-only objects (seeded but NOT required) in original DB
            // order
            // 3. Unselected objects in original DB order
            long[] ranked = new long[allIds.length];
            int idx = 0;
            // Pass 1: required
            for (long id : allIds) {
                if (req != null && req.contains(id))
                    ranked[idx++] = id;
            }
            int actualRequiredCount = idx; // required objects occupy
                                           // [0..actualRequiredCount-1]
            // Pass 2: seed-only (selected but not required)
            for (long id : allIds) {
                if (sel.contains(id) && (req == null || !req.contains(id)))
                    ranked[idx++] = id;
            }
            // Pass 3: unselected
            for (long id : allIds) {
                if (!sel.contains(id))
                    ranked[idx++] = id;
            }
            rankedIds.put(cls, ranked);
            if (actualRequiredCount > 0)
                requiredCounts.put(cls, actualRequiredCount);
        }
        return new SelectionResult(rankedIds, requiredCounts);
    }

    // ── Field path resolution ──────────────────────────────────────────────

    /**
     * Translates a destinationName-based field path (e.g. "adresse.rue") into
     * the corresponding DB4O source field path (e.g. "mAdresse.mRue") by
     * walking the reference schema class hierarchy.
     *
     * @param className fully-qualified class name (e.g.
     * "gest.dossPrev.DossPrev")
     * @param destPath dot-separated destinationName path from the UI
     * @return dot-separated source field path, or null if resolution fails
     */
    private String resolveDestinationPathToSourcePath(String className, String destPath) {
        if (destPath == null || destPath.isEmpty())
            return null;

        String[] segments = destPath.split("\\.");
        StringBuilder sourcePath = new StringBuilder();
        DOSchemaClass currentClass = referenceSchema.findClassByName(className);

        for (int i = 0; i < segments.length; i++) {
            if (currentClass == null)
                return null;

            // Search all fields including inherited ones
            DOSchemaField field = null;
            List<DOSchemaField> allFields = DatabaseUtil.getAllSchemaFieldsIncludingAncestors(currentClass, referenceSchema);
            for (DOSchemaField f : allFields) {
                if (segments[i].equals(f.destinationName)) {
                    field = f;
                    break;
                }
            }
            if (field == null)
                return null;

            if (sourcePath.length() > 0)
                sourcePath.append(".");
            sourcePath.append(field.source);

            // If there are more segments, resolve the embedded type for the
            // next level
            if (i < segments.length - 1) {
                String typeName = field.isCollection ? field.childrenType : field.type;
                currentClass = (typeName != null) ? referenceSchema.findClassByName(typeName) : null;
            }
        }
        return sourcePath.toString();
    }

    // ── Helpers
    // ───────────────────────────────────────────────────────────────

    private static String simpleClassName(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }

    private static void status(DOExportMonitor monitor, String msg) {
        if (monitor != null)
            monitor.onStatusMessage(msg);
    }
}
