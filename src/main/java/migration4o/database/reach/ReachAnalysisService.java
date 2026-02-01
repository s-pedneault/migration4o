package migration4o.database.reach;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.db4o.ext.ExtObjectContainer;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;

/**
 * Service for performing database reachability analysis.
 * Identifies which objects are reachable from root classes (EntiteContientID
 * and EntiteParam).
 * 
 * This service orchestrates the reach analysis process and delegates to
 * specialized
 * components for object traversal and field processing.
 */
public class ReachAnalysisService {

    private final DOSchema referenceSchema;
    private final DOSchema databaseSchema;
    private final ExtObjectContainer container;

    private final ObjectTraverser objectTraverser;
    private final ReachResultAggregator resultAggregator;

    /**
     * Creates a new reach analysis service.
     * 
     * @param referenceSchema The reference schema defining class hierarchies
     * @param databaseSchema  The database schema containing object IDs
     * @param container       The database container
     */
    public ReachAnalysisService(DOSchema referenceSchema, DOSchema databaseSchema, ExtObjectContainer container) {
        this.referenceSchema = referenceSchema;
        this.databaseSchema = databaseSchema;
        this.container = container;

        this.objectTraverser = new ObjectTraverser(referenceSchema, databaseSchema, container);
        this.resultAggregator = new ReachResultAggregator(databaseSchema);
    }

    /**
     * Performs reach analysis and returns the results.
     * 
     * @param progressCallback Optional callback for progress updates
     * @return ReachAnalysisResult containing reached objects organized by class
     */
    public ReachAnalysisResult performAnalysis(ReachProgressCallback progressCallback) {
        // Track all reached objects globally to avoid infinite loops
        Set<Long> reachedObjectIds = new HashSet<>();

        // Maps to track object processing progress per class
        Map<String, Integer> classProcessedCount = new HashMap<>();
        Map<String, Integer> classTotalCount = new HashMap<>();

        // Pre-calculate total counts for each class
        for (DOSchemaClass schemaClass : databaseSchema.getClasses()) {
            if (schemaClass.isEntite(referenceSchema) || schemaClass.isParam(referenceSchema)) {
                long[] uniqueIds = schemaClass.uniqueObjectIds;
                if (uniqueIds != null) {
                    classTotalCount.put(schemaClass.source, uniqueIds.length);
                    classProcessedCount.put(schemaClass.source, 0);
                }
            }
        }

        // Count total root classes
        int classCount = 0;
        for (DOSchemaClass schemaClass : databaseSchema.getClasses()) {
            if (schemaClass.isEntite(referenceSchema) || schemaClass.isParam(referenceSchema)) {
                classCount++;
            }
        }

        if (progressCallback != null) {
            progressCallback.onStatusUpdate("Found " + classCount + " root classes to process");
        }

        // Process all root classes (descendants of EntiteContientID or EntiteParam)
        int processedCount = 0;
        for (DOSchemaClass schemaClass : databaseSchema.getClasses()) {
            if (schemaClass.isEntite(referenceSchema) || schemaClass.isParam(referenceSchema)) {
                processedCount++;
                String simpleName = schemaClass.source;
                if (simpleName.contains(".")) {
                    simpleName = simpleName.substring(simpleName.lastIndexOf('.') + 1);
                }

                if (progressCallback != null) {
                    progressCallback.onStatusUpdate(
                            String.format("Exploring %d/%d: %s (%d objects)",
                                    processedCount, classCount, simpleName,
                                    schemaClass.uniqueObjectIds != null ? schemaClass.uniqueObjectIds.length : 0));
                }

                // Explore all objects in this root class recursively
                long[] uniqueIds = schemaClass.uniqueObjectIds;
                if (uniqueIds != null) {
                    for (long objectId : uniqueIds) {
                        objectTraverser.exploreObjectRecursively(
                                objectId,
                                reachedObjectIds,
                                classProcessedCount,
                                classTotalCount,
                                progressCallback);
                    }
                }
            }
        }

        if (progressCallback != null) {
            progressCallback.onStatusUpdate("Reached " + reachedObjectIds.size() + " total objects");
            progressCallback.onStatusUpdate("Aggregating results by class...");
        }

        // Aggregate reached objects by class
        Map<String, Set<Long>> reachedByClass = resultAggregator.aggregateReachedObjects(
                reachedObjectIds,
                container);

        // Update schema with reached object IDs
        for (Map.Entry<String, Set<Long>> entry : reachedByClass.entrySet()) {
            String childClassName = entry.getKey();
            Set<Long> idsToAdd = entry.getValue();

            DOSchemaClass childClass = SchemaUtil.findClassInSchemaByName(databaseSchema, childClassName);
            if (childClass != null) {
                resultAggregator.addReachedIdsToClass(childClass, idsToAdd);
            }
        }

        if (progressCallback != null) {
            progressCallback.onStatusUpdate("Finalizing reach analysis...");
        }

        return new ReachAnalysisResult(reachedObjectIds, reachedByClass);
    }
}
