package dataobjects.impl.report.reachability.data;

import dataobjects.impl.models.database.DODatabase;
import dataobjects.impl.models.database.DODatabaseClass;
import dataobjects.impl.models.schema.DOSchema;
import dataobjects.impl.models.schema.DOSchemaClass;
import dataobjects.impl.models.DOField;
import dataobjects.impl.engine.DOEngine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Analyzes database content for reachability report
 */
public class DatabaseAnalyzer {

    public static class DatabaseClassSummary {
        public String className;
        public String shortName;
        public long totalEntryCount; // Total entries (includes inheritance duplicates)
        public long uniqueObjectCount; // Unique resolved objects
        public long reachedObjectCount; // Objects marked as reachable
        public long unreachedObjectCount; // Objects marked as unreachable
        public List<DatabaseObjectSample> samples = new ArrayList<>();

        public DatabaseClassSummary(String className) {
            this.className = className;
            this.shortName = className.substring(className.lastIndexOf('.') + 1);
        }
    }

    public static class DatabaseObjectSample {
        public String objectId;
        public Map<String, Object> fieldValues = new HashMap<>();
        public List<String> referencedClasses = new ArrayList<>();

        public DatabaseObjectSample(String objectId) {
            this.objectId = objectId;
        }
    }

    public static class ReachabilityMap {
        public String rootClass;
        public Map<String, List<String>> classReferences = new HashMap<>();
        public Map<String, Integer> reachabilityDepths = new HashMap<>();

        public ReachabilityMap(String rootClass) {
            this.rootClass = rootClass;
        }
    }

    private DOEngine engine;

    public DatabaseAnalyzer(DOEngine engine) {
        this.engine = engine;
    }

    public Map<String, DatabaseClassSummary> analyzeDatabaseContent() {
        Map<String, DatabaseClassSummary> classSummaries = new HashMap<>();
        DODatabase database = engine.getDatabase();
        dataobjects.impl.resolution.DOObjectReachabilityTracker tracker = engine.getReachabilityTracker();

        DODatabaseClass[] classes = database.getClasses();
        for (DODatabaseClass dbClass : classes) {
            String className = dbClass.getAbsoluteName();
            DatabaseClassSummary summary = new DatabaseClassSummary(className);

            // Count total entries (includes inheritance duplicates)
            summary.totalEntryCount = dbClass.getTotalObjectCount();

            // Get exact counts from the reachability tracker
            summary.uniqueObjectCount = tracker.getObjectCountByClass(dbClass);
            summary.reachedObjectCount = tracker.getReachedObjectCountByClass(dbClass);
            summary.unreachedObjectCount = tracker.getUnreachedObjectCountByClass(dbClass);

            classSummaries.put(className, summary);
        }

        return classSummaries;
    }

    public ReachabilityMap buildReachabilityMap(String rootClassName) {
        ReachabilityMap reachabilityMap = new ReachabilityMap(rootClassName);
        Map<String, Integer> visited = new HashMap<>();

        buildReachabilityMapRecursive(rootClassName, 0, reachabilityMap, visited);

        return reachabilityMap;
    }

    private void buildReachabilityMapRecursive(String className, int depth,
            ReachabilityMap reachabilityMap,
            Map<String, Integer> visited) {
        // Avoid infinite recursion and limit depth
        if (depth > 10 || (visited.containsKey(className) && visited.get(className) <= depth)) {
            return;
        }

        visited.put(className, depth);
        reachabilityMap.reachabilityDepths.put(className, depth);

        // For now, we'll build reachability based on schema classes
        // since the database field API is more complex
        DOSchema schema = engine.getSchema();
        DOSchemaClass schemaClass = findSchemaClass(schema, className);

        if (schemaClass == null) {
            return;
        }

        List<String> references = new ArrayList<>();
        DOField[] fields = schemaClass.getFields();

        if (fields != null) {
            for (DOField field : fields) {
                String fieldType = field.getTypeName();

                if (isReferenceType(fieldType)) {
                    String referencedClass = extractReferencedClassName(fieldType);
                    if (referencedClass != null && !referencedClass.equals(className)) {
                        references.add(referencedClass);

                        // Recursively analyze referenced class
                        buildReachabilityMapRecursive(referencedClass, depth + 1,
                                reachabilityMap, visited);
                    }
                }
            }
        }

        reachabilityMap.classReferences.put(className, references);
    }

    private DOSchemaClass findSchemaClass(DOSchema schema, String className) {
        DOSchemaClass[] classes = schema.getClasses();
        for (DOSchemaClass schemaClass : classes) {
            if (className.equals(schemaClass.getAbsoluteName())) {
                return schemaClass;
            }
        }
        return null;
    }

    private boolean isReferenceType(String typeName) {
        if (typeName == null)
            return false;

        // Not primitive types
        return !isPrimitiveTypeName(typeName) &&
                !isCollectionType(typeName) &&
                !typeName.startsWith("java.lang") &&
                !typeName.startsWith("java.util.Date");
    }

    private boolean isPrimitiveTypeName(String typeName) {
        return typeName.equals("int") || typeName.equals("long") ||
                typeName.equals("double") || typeName.equals("float") ||
                typeName.equals("boolean") || typeName.equals("byte") ||
                typeName.equals("short") || typeName.equals("char") ||
                typeName.equals("java.lang.String") ||
                typeName.endsWith("[]");
    }

    private boolean isCollectionType(String typeName) {
        return typeName.contains("Vector") || typeName.contains("List") ||
                typeName.contains("Set") || typeName.contains("Collection") ||
                typeName.contains("Map") || typeName.contains("VectRechID");
    }

    private String extractReferencedClassName(String typeName) {
        // For simple class references, return as-is
        if (!typeName.contains("<") && !typeName.contains("[")) {
            return typeName;
        }

        // For generic types like Vector<SomeClass>, extract SomeClass
        if (typeName.contains("<") && typeName.contains(">")) {
            int start = typeName.indexOf('<') + 1;
            int end = typeName.indexOf('>', start);
            if (end > start) {
                return typeName.substring(start, end).trim();
            }
        }

        // For array types, extract base type
        if (typeName.endsWith("[]")) {
            return typeName.substring(0, typeName.length() - 2);
        }

        return null;
    }
}