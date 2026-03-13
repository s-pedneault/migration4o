package migration4o.migration.tasks;

import java.util.ArrayList;
import java.util.Arrays;
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
     * Analyses the modules to export and returns a map of
     * {@code className → selected object-ID array} (length ≤ cap, preserving
     * original DB iteration order).
     *
     * @param modules  modules that will be exported
     * @param monitor  optional progress monitor (may be null)
     * @return per-class selected IDs; only classes with &gt; cap objects that
     *         participated in at least one reference edge are included
     */
    public Map<String, long[]> computeSelection(List<DOSchemaModule> modules, DOExportMonitor monitor) {
        // Step 1 – collect all exported classes
        Map<String, long[]> classObjectIds = new LinkedHashMap<>();
        for (DOSchemaModule m : modules) {
            collectClasses(m, classObjectIds);
        }
        System.out.println("[Advisor] computeSelection: " + classObjectIds.size() + " classes collected, cap=" + cap);
        if (classObjectIds.isEmpty()) {
            System.out.println("[Advisor] No classes collected \u2014 advisor inactive");
            return Collections.emptyMap();
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
            return Collections.emptyMap(); // nothing to optimise
        }

        // Step 3 – scan DB objects to resolve actual object-ID relationships
        status(monitor, "Smart selection: scanning object references\u2026");
        Map<ReferenceEdge, Map<Long, Long>> edgeData = scanReferences(edges, classObjectIds, monitor);
        int totalLinks = 0;
        for (Map<Long, Long> m : edgeData.values())
            totalLinks += m.size();
        System.out.println("[Advisor] scanReferences: " + totalLinks + " total source\u2192target links found");

        // Step 4 – iterative scoring to converge on the best mutual selection
        status(monitor, "Smart selection: computing optimal object selection\u2026");
        Map<String, long[]> finalResult = buildRankedOrder(classObjectIds, edges, edgeData);
        System.out.println("[Advisor] Final preselection: " + finalResult.size() + " class(es) reordered:");
        for (Map.Entry<String, long[]> e : finalResult.entrySet()) {
            System.out.println("  " + simpleClassName(e.getKey()) + ": " + e.getValue().length + " objects ranked best-first");
        }
        return finalResult;
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
     */
    private List<ReferenceEdge> findReferenceEdges(Set<String> exportedNames) {
        List<ReferenceEdge> edges = new ArrayList<>();
        // For each exported class that is a *target*, read who points to it
        for (String tgtName : exportedNames) {
            DOSchemaClass tgtClass = referenceSchema.findClassByName(tgtName);
            if (tgtClass == null || tgtClass.schemaReferences == null)
                continue;

            for (DOSchemaReference ref : tgtClass.schemaReferences) {
                String srcName = ref.className;
                // Only edges between two *different* exported classes
                if (!exportedNames.contains(srcName) || srcName.equals(tgtName))
                    continue;

                // Locate the schema field to determine IDEntite status.
                // CRITICAL: search ancestor classes too — fields like mIDDossPrev
                // are declared on parent classes (e.g. EntiteContientID).
                DOSchemaClass srcClass = referenceSchema.findClassByName(srcName);
                if (srcClass == null)
                    continue;
                DOSchemaField schemaField = DatabaseUtil.findSchemaFieldByNameIncludingAncestors(srcClass, ref.fieldName, referenceSchema);
                // If field is not found or not exported, skip
                if (schemaField != null && !schemaField.isExported)
                    continue;

                boolean isIDEntite = schemaField != null && TypeUtil.isIDEntiteField(schemaField, referenceSchema);

                // Avoid duplicate edges
                if (!containsEdge(edges, srcName, ref.fieldName, tgtName)) {
                    edges.add(new ReferenceEdge(srcName, ref.fieldName, tgtName, isIDEntite));
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

    // ── Step 4: iterative mutual selection ────────────────────────────────────

    /**
     * Iteratively selects the best N objects per class so the selected subsets are
     * as mutually cross-referenced as possible, then returns the FULL ranked array
     * for each class (best-N first, remaining objects appended after).
     *
     * <p>Returning the full array — rather than trimming to N — is critical because
     * per-destination criteria filtering (e.g. {@code mIDDossPrevOld == -1}) happens
     * AFTER this pre-selection in {@link ObjectExportLoop}. Criteria-filtered objects
     * do not count toward the cap, so the loop must be able to reach beyond position N
     * to fill the cap with criteria-passing objects.  The important thing is that the
     * <em>most mutually-related</em> objects appear at the front of the array.</p>
     *
     * <p>Classes whose total object count ≤ cap are excluded (no reordering needed).</p>
     */
    private Map<String, long[]> buildRankedOrder(Map<String, long[]> classObjectIds, List<ReferenceEdge> edges, Map<ReferenceEdge, Map<Long, Long>> edgeData) {

        // ── 4a: iterative selection to find the mutually-coherent best-N ──────

        // Seed: first min(cap, total) IDs for each class
        Map<String, Set<Long>> selected = new LinkedHashMap<>();
        for (Map.Entry<String, long[]> e : classObjectIds.entrySet()) {
            long[] ids = e.getValue();
            Set<Long> s = new LinkedHashSet<>();
            for (int i = 0; i < Math.min(cap, ids.length); i++)
                s.add(ids[i]);
            selected.put(e.getKey(), s);
        }

        Set<String> sourceClasses = new LinkedHashSet<>();
        Set<String> targetClasses = new LinkedHashSet<>();
        for (ReferenceEdge e : edges) {
            sourceClasses.add(e.sourceClass);
            targetClasses.add(e.targetClass);
        }

        final int MAX_ITERATIONS = 8;
        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            boolean changed = false;

            // Source pass: prefer source objects whose refs land in the currently-selected targets
            for (String src : sourceClasses) {
                long[] allIds = classObjectIds.get(src);
                if (allIds == null || allIds.length <= cap)
                    continue;

                List<ReferenceEdge> outEdges = edgesFrom(edges, src);
                Map<Long, Integer> scoreMap = new HashMap<>();
                for (long id : allIds) {
                    int score = 0;
                    for (ReferenceEdge e : outEdges) {
                        Long refId = edgeData.get(e).get(id);
                        if (refId != null && selected.getOrDefault(e.targetClass, Collections.emptySet()).contains(refId)) {
                            score++;
                        }
                    }
                    scoreMap.put(id, score);
                }
                Set<Long> newSel = topN(allIds, scoreMap, cap);
                if (!newSel.equals(selected.get(src))) {
                    selected.put(src, newSel);
                    changed = true;
                }
            }

            // Target pass: prefer target objects referenced by the currently-selected sources
            for (String tgt : targetClasses) {
                long[] allIds = classObjectIds.get(tgt);
                if (allIds == null || allIds.length <= cap)
                    continue;

                List<ReferenceEdge> inEdges = edgesTo(edges, tgt);
                Map<Long, Integer> refCount = new HashMap<>();
                for (ReferenceEdge e : inEdges) {
                    for (Long srcId : selected.getOrDefault(e.sourceClass, Collections.emptySet())) {
                        Long refId = edgeData.get(e).get(srcId);
                        if (refId != null)
                            refCount.merge(refId, 1, Integer::sum);
                    }
                }
                if (refCount.isEmpty())
                    continue;

                Set<Long> newSel = topN(allIds, refCount, cap);
                if (!newSel.equals(selected.get(tgt))) {
                    selected.put(tgt, newSel);
                    changed = true;
                }
            }

            if (!changed) {
                System.out.println("[Advisor] Converged after " + (iter + 1) + " iteration(s)");
                break;
            }
        }

        // ── 4b: build full ranked arrays (selected-N first, rest appended) ────

        Map<String, long[]> result = new LinkedHashMap<>();
        for (Map.Entry<String, long[]> e : classObjectIds.entrySet()) {
            String cls = e.getKey();
            long[] allIds = e.getValue();
            if (allIds.length <= cap)
                continue; // no reordering needed

            Set<Long> sel = selected.get(cls);
            if (sel == null || sel.isEmpty())
                continue;

            // Count how many cross-references each selected object has (for logging)
            int linkedCount = 0;
            for (long id : sel) {
                for (ReferenceEdge edge : edges) {
                    if (edge.sourceClass.equals(cls)) {
                        Long refId = edgeData.get(edge).get(id);
                        if (refId != null && selected.getOrDefault(edge.targetClass, Collections.emptySet()).contains(refId)) {
                            linkedCount++;
                            break;
                        }
                    }
                    if (edge.targetClass.equals(cls)) {
                        // Check if any selected source references this object
                        for (Long srcId : selected.getOrDefault(edge.sourceClass, Collections.emptySet())) {
                            if (id == edgeData.get(edge).getOrDefault(srcId, -1L)) {
                                linkedCount++;
                                break;
                            }
                        }
                    }
                }
            }
            System.out.println("[Advisor] " + simpleClassName(cls) + ": " + sel.size() + " best objects selected (approx " + linkedCount + " cross-linked), " + (allIds.length - sel.size()) + " remaining appended");

            // Build output: selected objects first (in original DB order), then the rest
            long[] ranked = new long[allIds.length];
            int idx = 0;
            for (long id : allIds) {
                if (sel.contains(id))
                    ranked[idx++] = id;
            }
            for (long id : allIds) {
                if (!sel.contains(id))
                    ranked[idx++] = id;
            }
            result.put(cls, ranked);
        }
        return result;
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

    private static Set<Long> topN(long[] allIds, Map<Long, Integer> scores, int n) {
        int total = allIds.length;
        final Map<Long, Integer> originalIndex = new HashMap<>(total);
        for (int i = 0; i < total; i++)
            originalIndex.put(allIds[i], i);

        Long[] boxed = new Long[total];
        for (int i = 0; i < total; i++)
            boxed[i] = allIds[i];
        Arrays.sort(boxed, (a, b) -> {
            int sa = scores.getOrDefault(a, 0);
            int sb = scores.getOrDefault(b, 0);
            if (sb != sa)
                return sb - sa;
            return Integer.compare(originalIndex.getOrDefault(a, 0), originalIndex.getOrDefault(b, 0));
        });

        Set<Long> result = new LinkedHashSet<>(n * 2);
        for (int i = 0; i < Math.min(n, total); i++)
            result.add(boxed[i]);
        return result;
    }

    private static List<ReferenceEdge> edgesFrom(List<ReferenceEdge> edges, String src) {
        List<ReferenceEdge> out = new ArrayList<>();
        for (ReferenceEdge e : edges)
            if (e.sourceClass.equals(src))
                out.add(e);
        return out;
    }

    private static List<ReferenceEdge> edgesTo(List<ReferenceEdge> edges, String tgt) {
        List<ReferenceEdge> out = new ArrayList<>();
        for (ReferenceEdge e : edges)
            if (e.targetClass.equals(tgt))
                out.add(e);
        return out;
    }

    private static String simpleClassName(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }

    private static void status(DOExportMonitor monitor, String msg) {
        if (monitor != null)
            monitor.onStatusMessage(msg);
    }
}
