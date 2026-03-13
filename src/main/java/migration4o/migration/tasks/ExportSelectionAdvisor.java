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
import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;
import com.db4o.reflect.generic.GenericObject;

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
 * Each edge carries whether it is IDEntite-mediated so the DB scan below
 * can choose the right resolution path.</li>
 * <li>Scan every source-class object: read the relevant field via
 * {@link DatabaseUtil#getStoredFieldValue}, then resolve to the target
 * object ID — directly via {@code container.ext().getID()} for plain
 * references, or via {@link ReferenceUtil#resolveIDEntiteReference} for
 * IDEntite-mediated ones.</li>
 * <li>Run iterative scoring passes (source pass then target pass) until
 * stable, keeping the top-N objects per class at each step.</li>
 * <li>Return the final selections as {@code Map<className, long[]>} in
 * original DB order for deterministic output.</li>
 * </ol>
 */
public class ExportSelectionAdvisor {

    // ── Edge record ──────────────────────────────────────────────────────────

    static final class ReferenceEdge {
        final String sourceClass; // fully-qualified source class name
        final String fieldName; // DB4O / source field name (from schema field.source)
        final String targetClass; // fully-qualified target class name
        final boolean isIDEntite; // true → resolve via ReferenceUtil.resolveIDEntiteReference

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
     *   <li>{@code rankedIds} — full per-class ID arrays, required objects first.
     *   <li>{@code requiredCounts} — how many leading IDs in each array are
     *       "required" (closure-driven) and must be exported unconditionally.
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

    // ── Constructor ──────────────────────────────────────────────────────────

    public ExportSelectionAdvisor(ExtObjectContainer container, DOSchema referenceSchema, DOSchema databaseSchema, int cap) {
        this.container = container;
        this.referenceSchema = referenceSchema;
        this.databaseSchema = databaseSchema;
        this.cap = cap;
    }

    // ── Public entry point ───────────────────────────────────────────────────

    /**
     * Analyses the modules to export and returns a {@link SelectionResult}
     * containing per-class ranked ID arrays (required objects first) and the
     * count of required objects at the front of each array.
     *
     * @param modules  modules that will be exported
     * @param monitor  optional progress monitor (may be null)
     * @return selection result; maps are empty when no actionable edges exist
     */
    public SelectionResult computeSelection(List<DOSchemaModule> modules, DOExportMonitor monitor) {
        // Step 1 – collect all exported classes
        Map<String, long[]> classObjectIds = new LinkedHashMap<>();
        for (DOSchemaModule m : modules) {
            collectClasses(m, classObjectIds);
        }
        System.out.println("[Advisor] computeSelection: " + classObjectIds.size() + " classes collected, cap=" + cap);
        if (classObjectIds.isEmpty()) {
            System.out.println("[Advisor] No classes collected \u2014 advisor inactive");
            return new SelectionResult(Collections.emptyMap(), Collections.emptyMap());
        }

        Set<String> exportedNames = classObjectIds.keySet();

        // Step 2 – find reference edges using the pre-built schemaReferences graph
        List<ReferenceEdge> edges = findReferenceEdges(exportedNames);
        System.out.println("[Advisor] Found " + edges.size() + " reference edges:");
        for (ReferenceEdge e : edges) {
            System.out.println("  " + simpleClassName(e.sourceClass) + "." + e.fieldName + " -> " + simpleClassName(e.targetClass) + (e.isIDEntite ? " (IDEntite)" : " (direct)"));
        }
        if (edges.isEmpty()) {
            System.out.println("[Advisor] No edges found — skipping smart selection");
            return new SelectionResult(Collections.emptyMap(), Collections.emptyMap()); // nothing to optimise
        }

        // Step 3 – scan DB objects to resolve actual object-ID relationships
        status(monitor, "Smart selection: scanning object references\u2026");
        Map<ReferenceEdge, Map<Long, Long>> edgeData = scanReferences(edges, classObjectIds, monitor);
        int totalLinks = 0;
        for (Map<Long, Long> m : edgeData.values())
            totalLinks += m.size();
        System.out.println("[Advisor] scanReferences: " + totalLinks + " total source\u2192target links found");

        // Step 4 – closure propagation: guarantee every referenced object is included
        status(monitor, "Smart selection: propagating referential closure\u2026");
        SelectionResult result = buildRankedOrder(classObjectIds, edges, edgeData);
        System.out.println("[Advisor] Final preselection: " + result.rankedIds.size() + " class(es) reordered:");
        for (Map.Entry<String, long[]> e : result.rankedIds.entrySet()) {
            int req = result.requiredCounts.getOrDefault(e.getKey(), 0);
            System.out.println("  " + simpleClassName(e.getKey()) + ": " + e.getValue().length
                    + " objects total, " + req + " required (cap-exempt) at front");
        }
        return result;
    }

    // ── Step 1: collect exported classes ────────────────────────────────────

    private void collectClasses(DOSchemaModule module, Map<String, long[]> out) {
        for (ClassExportConfig cfg : module.classConfigs) {
            String name = cfg.getClassName();
            if (!out.containsKey(name)) {
                // IMPORTANT: objectIds are populated only on the *database* schema
                // (from storedClass.getIDs()). The reference schema never has them.
                DOSchemaClass dbClass = databaseSchema.findClassByName(name);
                if (dbClass != null && dbClass.objectIds != null && dbClass.objectIds.length > 0) {
                    out.put(name, dbClass.objectIds);
                    System.out.println("[Advisor] Collected class " + name + " with " + dbClass.objectIds.length + " objects");
                } else {
                    System.out.println("[Advisor] Skipping class " + name + " — no objectIds in databaseSchema (dbClass=" + dbClass + ")");
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
     * {@code DOReferenceDetector} populates {@code schemaClass.schemaReferences}
     * at startup and already resolves IDEntite indirection, so we don't need to
     * re-scan fields manually.
     *
     * <p><b>Inherited-field expansion:</b> {@code DOReferenceDetector} records
     * the <em>declaring</em> class of each field as the reference source (e.g.
     * {@code gest.gen.EntiteContientID} for the inherited {@code mIDDossPrev}
     * field).  When that declaring class is not itself exported, we expand the
     * edge to every exported subclass of the declaring class — for example
     * {@code Prevention}, {@code Intervention}, etc. — so that each concrete
     * exported class that inherits the field is properly connected to its target.
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

                // Collect the concrete exported source classes for this reference.
                // Case A: the declaring class is itself exported → single edge.
                // Case B: the declaring class is a base class → expand to all
                //         exported subclasses that inherit the field.
                List<String> srcNames = new ArrayList<>();
                if (exportedNames.contains(declaringClass)) {
                    srcNames.add(declaringClass);
                } else {
                    for (DOSchemaClass candidate : exportedClasses) {
                        if (!candidate.source.equals(tgtName)
                                && candidate.isDescendantOf(declaringClass, referenceSchema)) {
                            srcNames.add(candidate.source);
                        }
                    }
                    if (!srcNames.isEmpty()) {
                        System.out.println("[Advisor] Inherited field expanded: "
                                + simpleClassName(declaringClass) + "." + ref.fieldName
                                + " \u2192 " + simpleClassName(tgtName)
                                + " \u2014 " + srcNames.size() + " exported subclass(es): "
                                + srcNames.stream().map(ExportSelectionAdvisor::simpleClassName).collect(java.util.stream.Collectors.joining(", ")));
                    }
                }

                for (String srcName : srcNames) {
                    // Locate the schema field (walking ancestors) to get IDEntite status.
                    DOSchemaClass srcClass = referenceSchema.findClassByName(srcName);
                    if (srcClass == null)
                        continue;
                    DOSchemaField schemaField = DatabaseUtil.findSchemaFieldByNameIncludingAncestors(srcClass, ref.fieldName, referenceSchema);
                    // Resolve shared field definition — a reference-only field (definition="...")
                    // carries no inline type or isExported flag; those live in the shared definition.
                    DOSchemaField effectiveField = schemaField;
                    if (schemaField != null && schemaField.isSharedField()) {
                        DOSchemaField def = referenceSchema.sharedFields.get(schemaField.definitionId);
                        if (def != null) effectiveField = def;
                    }
                    // Skip if the effective field is explicitly marked not exported.
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
     * Pre-builds a lookup map {@code targetClassName → (mID → objectId)} for every
     * class that is the target of an IDEntite edge.  This avoids O(n²) linear scans
     * inside {@link ReferenceUtil#findObjectByMID} per source object.
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
                System.out.println("[Advisor] mID index: no databaseSchema entry for " + targetClass);
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
                    Object midVal = readStoredField(activation.object, "mID");
                    if (midVal instanceof Number) {
                        long mid = ((Number) midVal).longValue();
                        if (mid > 0)
                            midMap.put(mid, objId);
                    }
                } catch (Exception ignored) {
                    // best-effort
                }
            }
            System.out.println("[Advisor] mID index for " + simpleClassName(targetClass) + ": " + midMap.size() + " entries");
            index.put(targetClass, midMap);
        }
        return index;
    }

    // ── Step 3b: scan DB objects ──────────────────────────────────────────────

    /**
     * For each edge, builds a map {@code sourceObjectId → targetObjectId}.
     * Uses {@link ObjectActivator#getAndActivate} for retrieval,
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

            int resolvedCount = 0;
            for (long objectId : objectIds) {
                try {
                    // Use ObjectActivator — the proven recipe for object retrieval
                    ObjectActivator.ActivationResult activation = ObjectActivator.getAndActivate(container, objectId);
                    if (activation == null)
                        continue;
                    Object obj = activation.object;

                    for (ReferenceEdge edge : classEdges) {
                        // Read the field using the proven FieldExporter pattern:
                        // DatabaseUtil.getAllFieldsIncludingAncestors + name match
                        Object fv = readStoredField(obj, edge.fieldName);
                        if (fv == null)
                            continue;

                        Long targetId;
                        if (edge.isIDEntite) {
                            // Activate the IDEntite wrapper, then read its mID
                            // using the same ancestor-walking pattern
                            ObjectResolverUtil.activateObjectShallow(container, fv, null);
                            Object midVal = readStoredField(fv, "mID");
                            if (!(midVal instanceof Number))
                                continue;
                            long mid = ((Number) midVal).longValue();
                            if (mid <= 0)
                                continue;
                            // Look up the target object ID from the pre-built index
                            Map<Long, Long> idx = midIndex.get(edge.targetClass);
                            targetId = idx != null ? idx.get(mid) : null;
                        } else {
                            // Direct persistent reference
                            targetId = ObjectResolverUtil.getObjectId(container, fv);
                        }

                        if (targetId != null) {
                            result.get(edge).put(objectId, targetId);
                            resolvedCount++;
                        }
                    }
                } catch (Exception ignored) {
                    // best-effort
                }
            }
            System.out.println("[Advisor] " + simpleClassName(srcClass) + ": " + resolvedCount + " references resolved across " + classEdges.size() + " edge(s)");
        }
        return result;
    }

    // ── Step 4: referential-closure selection ───────────────────────────────

    /**
     * Builds the final per-class selection using two-phase referential closure.
     *
     * <h3>Phase 1 — Seed</h3>
     * Each class is seeded with the first {@code min(cap, total)} object IDs in
     * original DB order.
     *
     * <h3>Phase 2 — Closure propagation</h3>
     * For every reference edge {@code S → T}, every target object that is actually
     * referenced by a currently-selected source object is unconditionally added to
     * {@code required[T]}, even if that pushes the count above {@code cap}.
     * Iteration continues until stable, naturally covering multi-hop chains.
     *
     * <h3>Phase 3 — Ranked output</h3>
     * The FULL object-ID array is returned for each class with this ordering:
     * <ol>
     *   <li><b>Required</b> objects (added by closure) in original DB order —
     *       these appear first regardless of their DB position, and are exempt
     *       from the cap check in {@link ObjectExportLoop}.</li>
     *   <li><b>Seed-only</b> objects (seeded but not required) in original DB
     *       order — fill available cap slots after required.</li>
     *   <li><b>Unselected</b> objects in original DB order — fallback pool for
     *       criteria-filtered objects that eat into the cap.</li>
     * </ol>
     * The required count is recorded so {@link ObjectExportLoop} can skip the cap
     * check for the leading required IDs.
     */
    private SelectionResult buildRankedOrder(Map<String, long[]> classObjectIds, List<ReferenceEdge> edges, Map<ReferenceEdge, Map<Long, Long>> edgeData) {

        // ── Phase 1: seed with first min(cap, total) IDs in DB order ──────────

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

        // required: objects added SOLELY by closure (not part of the initial seed)
        Map<String, Set<Long>> required = new LinkedHashMap<>();
        for (String cls : classObjectIds.keySet())
            required.put(cls, new LinkedHashSet<>());

        // ── Phase 2: closure propagation ──────────────────────────────────────

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

                // For every currently-selected source object, mark its referenced
                // target as required and add it to the target's selected set.
                // newLinks records which srcId drove each newly-required tgtId (for logging).
                List<Long> toAdd = new ArrayList<>();
                Map<Long, Long> newLinks = new LinkedHashMap<>();
                for (Long srcId : srcSelected) {
                    Long tgtId = links.get(srcId);
                    if (tgtId != null && !tgtSelected.contains(tgtId)) {
                        toAdd.add(tgtId);
                        newLinks.put(tgtId, srcId); // tgtId → driving srcId
                    }
                }

                if (!toAdd.isEmpty()) {
                    int before = tgtSelected.size();
                    tgtSelected.addAll(toAdd);
                    tgtRequired.addAll(toAdd); // track as closure-driven
                    int added = tgtSelected.size() - before;
                    if (added > 0) {
                        System.out.println("[Advisor] pass " + (iter + 1) + " — " + simpleClassName(edge.targetClass)
                                + ": +" + added + " required object(s) added via "
                                + simpleClassName(edge.sourceClass) + "." + edge.fieldName
                                + " (now " + tgtSelected.size()
                                + (tgtSelected.size() > cap ? ", EXCEEDS cap " + cap + " by " + (tgtSelected.size() - cap) : ", within cap " + cap)
                                + ")");
                        // Per-object detail: one line per source object that drove a new requirement
                        // Group by source ID so we see "SrcClass #X → TargetClass #Y, #Z via field"
                        Map<Long, List<Long>> bySrc = new LinkedHashMap<>();
                        for (Map.Entry<Long, Long> nl : newLinks.entrySet()) {
                            long tgt = nl.getKey();
                            long src = nl.getValue();
                            bySrc.computeIfAbsent(src, k -> new ArrayList<>()).add(tgt);
                        }
                        for (Map.Entry<Long, List<Long>> entry : bySrc.entrySet()) {
                            List<Long> tgts = entry.getValue();
                            StringBuilder sb = new StringBuilder();
                            sb.append("[Advisor]   ").append(simpleClassName(edge.sourceClass))
                              .append(" #").append(entry.getKey())
                              .append(" → ").append(simpleClassName(edge.targetClass)).append(" #");
                            for (int i = 0; i < tgts.size(); i++) {
                                if (i > 0) sb.append(", #");
                                sb.append(tgts.get(i));
                            }
                            sb.append("  (via ").append(edge.fieldName).append(")");
                            System.out.println(sb);
                        }
                        changed = true;
                    }
                }
            }

            if (!changed) {
                System.out.println("[Advisor] Closure converged after " + (iter + 1) + " pass(es)");
                break;
            }
        }

        // ── Phase 3: build required-first ranked arrays ───────────────────────

        Map<String, long[]> rankedIds = new LinkedHashMap<>();
        Map<String, Integer> requiredCounts = new LinkedHashMap<>();

        for (Map.Entry<String, long[]> e : classObjectIds.entrySet()) {
            String cls = e.getKey();
            long[] allIds = e.getValue();
            Set<Long> sel = selected.get(cls);
            Set<Long> req = required.get(cls);
            Set<Long> seedSet = seed.get(cls);

            // Emit a reordered entry when the class has more total objects than
            // the seed, OR closure pushed required beyond the initial seed.
            int seedSize = Math.min(cap, allIds.length);
            boolean hasRequired = req != null && !req.isEmpty();
            boolean needsReorder = allIds.length > cap || hasRequired;
            if (!needsReorder || sel == null || sel.isEmpty())
                continue;

            int reqCount = (req != null ? req.size() : 0);
            int seedOnlyCount = (sel.size() - reqCount);
            System.out.println("[Advisor] " + simpleClassName(cls) + ": "
                    + reqCount + " required (cap-exempt) + "
                    + seedOnlyCount + " seed-fill"
                    + (sel.size() > cap ? " (EXCEEDS cap " + cap + " by " + (sel.size() - cap) + ")" : " (within cap " + cap + ")")
                    + ", " + (allIds.length - sel.size()) + " appended as fallback");

            // Build output:
            //   1. Required objects first (in original DB order)
            //   2. Seed-only objects (seeded but NOT required) in original DB order
            //   3. Unselected objects in original DB order
            long[] ranked = new long[allIds.length];
            int idx = 0;
            // Pass 1: required
            for (long id : allIds) {
                if (req != null && req.contains(id))
                    ranked[idx++] = id;
            }
            int actualRequiredCount = idx; // required objects occupy [0..actualRequiredCount-1]
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

    // ── Field reading (proven FieldExporter pattern) ─────────────────────────

    /**
     * Reads a single field value by name from a DB4O object, searching the
     * <em>full</em> stored-class hierarchy.  This is the same proven pattern
     * used by {@link migration4o.migration.FieldExporter#exportAllFields}:
     * {@link DatabaseUtil#getAllFieldsIncludingAncestors} + name match.
     *
     * <p>CRITICAL: many reference fields (e.g.&nbsp;{@code mIDDossPrev}) are
     * declared on a parent class like {@code EntiteContientID}.  Calling
     * {@code storedField(name, null)} on the child stored class alone would
     * miss them.  This helper avoids that pitfall.</p>
     */
    private Object readStoredField(Object obj, String fieldName) {
        if (!(obj instanceof GenericObject))
            return null;
        StoredClass storedClass = container.ext().storedClass(obj);
        if (storedClass == null)
            return null;
        for (StoredField sf : DatabaseUtil.getAllFieldsIncludingAncestors(storedClass)) {
            if (fieldName.equals(sf.getName())) {
                try {
                    return sf.get(obj);
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String simpleClassName(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }

    private static void status(DOExportMonitor monitor, String msg) {
        if (monitor != null)
            monitor.onStatusMessage(msg);
    }
}
