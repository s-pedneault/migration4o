package service;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;
import dataobjects.api.models.schema.DOSchema;
import dataobjects.api.models.schema.DOSchemaClass;
import dataobjects.impl.models.schema.DOSchemaClassImpl;
import java.util.*;

/**
 * Encapsulates all business class/object inventory for a DB4O database.
 * Now uses SchemaClass objects instead of StoredClassInfo for unified class
 * representation.
 */
public class DatabaseContentsInformation {
    private final List<DOSchemaClass> businessClassInfos;
    private final Map<String, Integer> businessObjectCounts;
    private final List<String> businessClassNames;
    private final int totalBusinessClasses;
    private final int totalBusinessObjects;
    private final List<SuperclassAnalysis> superclassAnalyses;

    public DatabaseContentsInformation(ExtObjectContainer extDb, DOSchema schema) {
        List<DOSchemaClass> classInfos = new ArrayList<>();
        Map<String, Integer> objectCounts = new LinkedHashMap<>();
        List<String> classNames = new ArrayList<>();
        int classCount = 0;
        int objectCount = 0;
        List<SuperclassAnalysis> analyses = new ArrayList<>();

        if (extDb != null) {
            StoredClass[] storedClasses = extDb.storedClasses();
            for (StoredClass sc : storedClasses) {
                String className = sc.getName();
                long instanceCount = sc.instanceCount();
                if (!className.startsWith("com.db4o.") && instanceCount > 0) {

                    // First try to find existing DOSchemaClass from schema
                    DOSchemaClass schemaClass = null;
                    if (schema != null) {
                        // Search through schema classes to find matching className
                        for (DOSchemaClass clazz : schema.getClasses()) {
                            if (clazz.getAbsoluteName() != null && clazz.getAbsoluteName().equals(className)) {
                                schemaClass = clazz;
                                break;
                            }
                        }
                    }

                    if (schemaClass != null) {
                        // Use existing schema class but create a new instance with correct database
                        // object count
                        System.out.println("DatabaseContentsInformation: Found schema class for " + className +
                                " (db: " + instanceCount + " objects) - using database count for migration");
                        // Create a new DOSchemaClass with the same metadata for migration
                        // Note: Object counts are now handled separately via DODatabaseClass
                        schemaClass = new DOSchemaClassImpl(
                                schemaClass.getAbsoluteName(),
                                schemaClass.getShortName(),
                                schemaClass.getDescription(),
                                schemaClass.getTitle(),
                                schemaClass.getSuperClassAbsoluteName(),
                                schemaClass.getFields(),
                                schemaClass.getExportName());
                    } else {
                        // Create new DOSchemaClass for database-discovered class
                        schemaClass = new DOSchemaClassImpl(className, className, "", className, null,
                                new dataobjects.api.models.DOField[0], className);
                        System.out.println(
                                "DatabaseContentsInformation: Created new SchemaClass for database-discovered class "
                                        + className +
                                        " with " + instanceCount + " objects");
                    }

                    objectCounts.put(schemaClass.getAbsoluteName(), (int) instanceCount);
                    classNames.add(schemaClass.getAbsoluteName());
                    classInfos.add(schemaClass);
                    classCount++;
                    objectCount += instanceCount;
                }
            }

            // Perform enhanced superclass analysis
            analyses = performSuperclassAnalysis(classInfos, extDb);
        }

        this.businessClassInfos = Collections.unmodifiableList(classInfos);
        this.businessObjectCounts = Collections.unmodifiableMap(objectCounts);
        this.businessClassNames = Collections.unmodifiableList(classNames);
        this.totalBusinessClasses = classCount;
        this.totalBusinessObjects = objectCount;
        this.superclassAnalyses = Collections.unmodifiableList(analyses);
    }

    /**
     * Backward-compatible constructor for cases where no schema is available
     */
    public DatabaseContentsInformation(ExtObjectContainer extDb) {
        this(extDb, null);
    }

    public List<DOSchemaClass> getBusinessClassInfos() {
        return businessClassInfos;
    }

    public Map<String, Integer> getBusinessObjectCounts() {
        return businessObjectCounts;
    }

    public List<String> getBusinessClassNames() {
        return businessClassNames;
    }

    public int getTotalBusinessClasses() {
        return totalBusinessClasses;
    }

    public int getTotalBusinessObjects() {
        return totalBusinessObjects;
    }

    public List<SuperclassAnalysis> getSuperclassAnalyses() {
        return superclassAnalyses;
    }

    /**
     * Holds detailed analysis results for a superclass and its subclasses
     */
    public static class SuperclassAnalysis {
        private final String superclassName;
        private final int totalSuperclassObjects;
        private final int matchedSubclassInstances;
        private final int directSuperclassInstances;
        private final List<String> subclassNames;

        public SuperclassAnalysis(String superclassName, int totalSuperclassObjects,
                int matchedSubclassInstances, List<String> subclassNames) {
            this.superclassName = superclassName;
            this.totalSuperclassObjects = totalSuperclassObjects;
            this.matchedSubclassInstances = matchedSubclassInstances;
            this.directSuperclassInstances = totalSuperclassObjects - matchedSubclassInstances;
            this.subclassNames = new ArrayList<>(subclassNames);
        }

        public String getSuperclassName() {
            return superclassName;
        }

        public int getTotalSuperclassObjects() {
            return totalSuperclassObjects;
        }

        public int getMatchedSubclassInstances() {
            return matchedSubclassInstances;
        }

        public int getDirectSuperclassInstances() {
            return directSuperclassInstances;
        }

        public List<String> getSubclassNames() {
            return Collections.unmodifiableList(subclassNames);
        }

        @Override
        public String toString() {
            return String.format("SuperclassAnalysis{%s: total=%d, matched=%d, direct=%d, subclasses=%s}",
                    superclassName, totalSuperclassObjects, matchedSubclassInstances,
                    directSuperclassInstances, subclassNames);
        }
    }

    /**
     * Performs enhanced superclass analysis by comparing IDs of superclass
     * instances
     * against IDs of subclass instances to determine direct vs inherited instances.
     */
    private List<SuperclassAnalysis> performSuperclassAnalysis(List<DOSchemaClass> classInfos,
            ExtObjectContainer extDb) {
        List<SuperclassAnalysis> analyses = new ArrayList<>();

        // System.out.println("[DEBUG] Starting superclass analysis with " +
        // classInfos.size() + " classes");

        // Build a map of class name to DOSchemaClass for quick lookup
        Map<String, DOSchemaClass> classInfoMap = new HashMap<>();
        for (DOSchemaClass classInfo : classInfos) {
            classInfoMap.put(classInfo.getAbsoluteName(), classInfo);
        }

        // Find all classes that have subclasses
        Set<String> superclassNames = new HashSet<>();
        Map<String, List<String>> superclassToSubclasses = new HashMap<>();

        for (DOSchemaClass classInfo : classInfos) {
            String parentClassName = classInfo.getSuperClassAbsoluteName();
            if (parentClassName != null && classInfoMap.containsKey(parentClassName)) {
                superclassNames.add(parentClassName);
                superclassToSubclasses.computeIfAbsent(parentClassName, k -> new ArrayList<>())
                        .add(classInfo.getAbsoluteName());
                // System.out.println("[DEBUG] Found subclass: " + classInfo.getName() + " ->
                // parent: "
                // + parentClassName);
            }
        }

        // System.out.println("[DEBUG] Found " + superclassNames.size() + "
        // superclasses: " + superclassNames);

        // For each superclass, perform detailed ID comparison
        for (String superclassName : superclassNames) {
            try {
                // System.out.println("[DEBUG] Analyzing superclass: " + superclassName);
                Set<Long> superclassIds = getInstanceIds(extDb, superclassName);
                Set<Long> allSubclassIds = new HashSet<>();
                List<String> subclassNames = superclassToSubclasses.get(superclassName);

                // System.out.println(
                // "[DEBUG] - Superclass " + superclassName + " has " + superclassIds.size() + "
                // instances");
                // System.out.println("[DEBUG] - Subclasses: " + subclassNames);

                // Collect all IDs from subclasses
                for (String subclassName : subclassNames) {
                    Set<Long> subclassIds = getInstanceIds(extDb, subclassName);
                    System.out.println(
                            "[DEBUG] - Subclass " + subclassName + " has " + subclassIds.size() + " instances");
                    allSubclassIds.addAll(subclassIds);
                }

                // Count how many superclass IDs have corresponding subclass instances
                int matchedCount = 0;
                for (Long superclassId : superclassIds) {
                    if (allSubclassIds.contains(superclassId)) {
                        matchedCount++;
                    }
                }

                // System.out.println("[DEBUG] - Total subclass instances: " +
                // allSubclassIds.size());
                // System.out.println("[DEBUG] - Matched IDs: " + matchedCount);
                // System.out.println("[DEBUG] - Direct instances: " + (superclassIds.size() -
                // matchedCount));

                analyses.add(new SuperclassAnalysis(
                        superclassName,
                        superclassIds.size(),
                        matchedCount,
                        subclassNames));

            } catch (Exception e) {
                System.err.println("Warning: Could not analyze superclass " + superclassName + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Sort analyses by superclass name for consistent output
        analyses.sort(Comparator.comparing(SuperclassAnalysis::getSuperclassName));

        // System.out.println("[DEBUG] Completed superclass analysis. Found " +
        // analyses.size() + " analyses:");
        // for (SuperclassAnalysis analysis : analyses) {
        // System.out.println("[DEBUG] " + analysis.toString());
        // }

        return analyses;
    }

    /**
     * Gets all instance IDs for a given class name from the database.
     */
    private Set<Long> getInstanceIds(ExtObjectContainer extDb, String className) {
        Set<Long> ids = new HashSet<>();
        try {
            StoredClass[] storedClasses = extDb.storedClasses();
            for (StoredClass sc : storedClasses) {
                if (className.equals(sc.getName())) {
                    long[] instanceIds = sc.getIDs();
                    for (long id : instanceIds) {
                        ids.add(id);
                    }
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not get IDs for class " + className + ": " + e.getMessage());
        }
        return ids;
    }
}
