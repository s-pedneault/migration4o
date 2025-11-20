package dataobjects;

import dataobjects.api.analysis.DOObjectAnalysis;
import dataobjects.api.engine.DOEngine;
import dataobjects.api.models.database.*;
import dataobjects.api.models.DOClass;
import dataobjects.api.models.DOField;
import dataobjects.api.analysis.*;
import dataobjects.impl.analysis.*;

public class DOTest {
    public static void main(String[] args) {
        System.out.println("DataObjectAPI Test");
        try {
            // DOEngine engine = DataObjectAPI.newEngine("schema/migration-schema.xml",
            // "local/PremLigne.dat");
            // DOEngine engine = DataObjectAPI.newEngine("schema/migration-schema.xml",
            // "local/54060/BackupManuel.dat");

            // DOEngine engine = DataObjectAPI.newEngine("schema/migration-schema.xml",
            // "local/54060/BackupManuel.dat");
            DOEngine engine = DataObjectAPI.newEngine("schema/migration-schema.xml",
                    "local/00000/PremLigne.dat");

            // Print the original hierarchy
            System.out.println("=== ORIGINAL HIERARCHY ===");
            // DataObjectAPI.printEngineHierarchy(engine);

            // Run the new migration pre-analysis
            System.out.println("\n=== MIGRATION PRE-ANALYSIS ===");
            // DataObjectAPI.analyzeMigration(engine);

            // TEST: Verify that references can be resolved
            // System.out.println("\n=== TESTING REFERENCE RESOLUTION ===");
            // testReferenceResolution(engine);

            // TEST: Analyze DossPrev objects specifically
            // System.out.println("\n=== ANALYZING DossPrev OBJECTS ===");
            // analyzeDossPrevResolvability(engine);

            // DOObjectAnalysis analysis = new DOObjectAnalysisNewImpl();
            // analysis.analyze(engine);

            // Generate comprehensive HTML structure report
            // System.out.println("\n=== GENERATING STRUCTURE REPORT ===");
            // DataObjectAPI.generateStructureReport(engine);
            // System.out.println("HTML structure report generated successfully!");

            // Generate interactive HTML tree report
            // System.out.println("\n=== GENERATING TREE REPORT ===");
            // DataObjectAPI.generateTreeReport(engine);
            // System.out.println("HTML tree report generated successfully!");

            // Generate new reachability analysis report
            // System.out.println("\n=== GENERATING REACHABILITY ANALYSIS REPORT ===");
            // DataObjectAPI.generateReachabilityReport(engine);
            // System.out.println("Reachability analysis report generated successfully!");

            // Export database to Excel files
            // System.out.println("\n=== EXPORTING TO EXCEL ===");
            // DataObjectAPI.exportToExcel(engine);
            // System.out.println("Excel export completed successfully!");

            // Export database to XML files with schema and report
            System.out.println("\n=== EXPORTING TO XML (V2) ===");
            DataObjectAPI.exportToXMLV2(engine);
            System.out.println("XML migration export completed successfully!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void testReferenceResolution(DOEngine engine) {
        System.out.println("Testing if references are properly resolved in DODatabaseObjects...");

        DODatabase database = engine.getDatabase();
        DODatabaseClass[] classes = database.getClasses();

        int totalObjects = 0;
        int objectsWithReferences = 0;
        int totalReferences = 0;
        int resolvedReferences = 0;
        int unresolvedReferences = 0;

        // Build a map of all resolved objects for O(1) lookup
        java.util.Map<Long, DODatabaseObject> allObjectsById = new java.util.HashMap<>();
        for (DODatabaseClass dbClass : classes) {
            DODatabaseObject[] objects = dbClass.getResolvedObjects();
            if (objects != null) {
                for (DODatabaseObject obj : objects) {
                    allObjectsById.put(obj.getObjectId(), obj);
                }
            }
        }

        System.out.println("Built index of " + allObjectsById.size() + " resolved objects");

        // Build a map of unresolved target IDs and their count
        java.util.Map<Long, Integer> unresolvedTargets = new java.util.HashMap<>();

        // Track mContrainte values for ID objects with resolved vs unresolved
        // references
        java.util.Map<Object, Integer> contrainteValuesResolved = new java.util.HashMap<>();
        java.util.Map<Object, Integer> contrainteValuesUnresolved = new java.util.HashMap<>();
        int debugCount = 0; // Limit debug output

        // Now check if references can be resolved
        for (DODatabaseClass dbClass : classes) {
            DODatabaseObject[] objects = dbClass.getResolvedObjects();
            if (objects != null) {
                for (DODatabaseObject obj : objects) {
                    totalObjects++;

                    DOObjectReference[] refs = obj.getReferences();
                    if (refs != null && refs.length > 0) {
                        objectsWithReferences++;

                        for (DOObjectReference ref : refs) {
                            totalReferences++;
                            Long targetId = ref.getTargetObjectId();

                            if (targetId != null) {
                                DODatabaseObject target = allObjectsById.get(targetId);

                                // Try to extract mContrainte value if this is an ID object with synthetic
                                // reference
                                Object contrainteValue = null;
                                String fieldNameDebug = (ref.getField() != null) ? ref.getField().getName() : "null";
                                boolean isSynthetic = (ref.getField() == null || "[synthetic]".equals(fieldNameDebug));
                                String className = obj.getMostSpecificClass().getShortName();

                                if (isSynthetic && className != null && className.startsWith("ID")) {
                                    // This is a synthetic reference from an ID object
                                    contrainteValue = extractFieldValue(obj, "mContrainte", database);
                                    // Debug: show first few values
                                    if (debugCount < 5 && contrainteValue != null) {
                                        System.out.println("  DEBUG: Object " + obj.getObjectId() + " (" + className +
                                                ") -> mContrainte=" + contrainteValue);
                                        debugCount++;
                                    }
                                }

                                if (target != null) {
                                    resolvedReferences++;
                                    if (contrainteValue != null) {
                                        contrainteValuesResolved.put(contrainteValue,
                                                contrainteValuesResolved.getOrDefault(contrainteValue, 0) + 1);
                                    }
                                } else {
                                    unresolvedReferences++;
                                    unresolvedTargets.put(targetId, unresolvedTargets.getOrDefault(targetId, 0) + 1);
                                    if (contrainteValue != null) {
                                        contrainteValuesUnresolved.put(contrainteValue,
                                                contrainteValuesUnresolved.getOrDefault(contrainteValue, 0) + 1);
                                    }

                                    if (unresolvedReferences <= 10) { // Show first 10 examples
                                        System.out.println("  UNRESOLVED: Object " + obj.getObjectId() +
                                                " (" + obj.getMostSpecificClass().getShortName() + ") -> " +
                                                "target " + targetId + " (field: " +
                                                (ref.getField() != null ? ref.getField().getName() : "[synthetic]")
                                                + ")"
                                                + (contrainteValue != null ? " [mContrainte=" + contrainteValue + "]"
                                                        : ""));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        System.out.println("\n=== REFERENCE RESOLUTION STATISTICS ===");
        System.out.println("Total objects: " + String.format("%,d", totalObjects));
        System.out.println("Objects with references: " + String.format("%,d", objectsWithReferences));
        System.out.println("Total references: " + String.format("%,d", totalReferences));
        System.out.println("Successfully resolved: " + String.format("%,d", resolvedReferences) +
                " (" + String.format("%.2f%%", (resolvedReferences * 100.0 / totalReferences)) + ")");
        System.out.println("Unresolved: " + String.format("%,d", unresolvedReferences) +
                " (" + String.format("%.2f%%", (unresolvedReferences * 100.0 / totalReferences)) + ")");
        System.out.println("Unique unresolved target objects: " + String.format("%,d", unresolvedTargets.size()));

        if (unresolvedReferences > 0) {
            System.out.println("\n⚠️  WARNING: Some references cannot be resolved!");
            System.out.println("This means the referenced objects were not included in the resolution process.");

            // Analyze mContrainte pattern
            if (!contrainteValuesResolved.isEmpty() || !contrainteValuesUnresolved.isEmpty()) {
                System.out.println("\n=== mContrainte PATTERN ANALYSIS ===");
                System.out.println("For synthetic references from ID objects:");

                System.out.println("\nmContrainte values for RESOLVED references:");
                contrainteValuesResolved.entrySet().stream()
                        .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                        .forEach(entry -> {
                            System.out.println("  " + entry.getKey() + ": " +
                                    String.format("%,d", entry.getValue()) + " references");
                        });

                System.out.println("\nmContrainte values for UNRESOLVED references:");
                contrainteValuesUnresolved.entrySet().stream()
                        .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                        .forEach(entry -> {
                            System.out.println("  " + entry.getKey() + ": " +
                                    String.format("%,d", entry.getValue()) + " references");
                        });
            }

            // Show the most commonly referenced unresolved objects
            System.out.println("\nTop 10 most referenced unresolved objects:");
            unresolvedTargets.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .limit(10)
                    .forEach(entry -> {
                        System.out.println("  Object ID " + entry.getKey() + ": " +
                                String.format("%,d", entry.getValue()) + " references");

                        // Try to find this object in the database using the engine
                        try {
                            com.db4o.ext.ExtObjectContainer container = database.getContainer();
                            Object obj = container.getByID(entry.getKey());
                            if (obj != null) {
                                String className = obj.getClass().getName();
                                System.out.println("    -> Found in database as: " + className);
                                System.out.println("    -> This object EXISTS but was NOT resolved!");
                            } else {
                                System.out.println("    -> Not found in database (deleted or invalid ID)");
                            }
                        } catch (Exception e) {
                            System.out.println("    -> Error checking database: " + e.getClass().getSimpleName() +
                                    ": " + (e.getMessage() != null ? e.getMessage() : "<no message>"));
                        }
                    });
        } else {
            System.out.println("\n✅ SUCCESS: All references can be resolved!");
        }
    }

    /**
     * Helper method to extract a field value from a DODatabaseObject
     */
    private static Object extractFieldValue(DODatabaseObject obj, String fieldName, DODatabase database) {
        try {
            // Get the actual object from the database
            com.db4o.ext.ExtObjectContainer container = database.getContainer();
            Object actualObj = container.getByID(obj.getObjectId());
            if (actualObj == null) {
                return null;
            }

            // Activate the object properly
            dataobjects.util.ObjectResolverUtil.activateObject(container, actualObj, obj.getObjectId());

            // Find the field in the class hierarchy
            DOClass dbClass = obj.getMostSpecificClass();
            DOField field = findFieldInHierarchy(dbClass, fieldName, database);
            if (field == null) {
                return null;
            }

            // Use the existing utility to get the field value (handles GenericObject
            // properly)
            return dataobjects.util.ObjectResolverUtil.getFieldValue(container, actualObj, field);

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Helper method to find a field in the class hierarchy
     */
    private static DOField findFieldInHierarchy(DOClass dbClass, String fieldName, DODatabase database) {
        if (dbClass == null) {
            return null;
        }

        // Check fields in this class
        DOField[] fields = dbClass.getFields();
        if (fields != null) {
            for (DOField field : fields) {
                if (field.getName().equals(fieldName)) {
                    return field;
                }
            }
        }

        // Check parent class recursively
        String parentName = dbClass.getSuperClassAbsoluteName();
        if (parentName != null && !parentName.isEmpty()) {
            // Find the parent class in the database
            for (DODatabaseClass parentClass : database.getClasses()) {
                if (parentClass.getAbsoluteName().equals(parentName)) {
                    return findFieldInHierarchy(parentClass, fieldName, database);
                }
            }
        }

        return null;
    }

    /**
     * Analyzes resolvability of all fields in DossPrev objects
     */
    private static void analyzeDossPrevResolvability(DOEngine engine) {
        DODatabase database = engine.getDatabase();

        // Find the DossPrev class
        DODatabaseClass dossPrevClass = null;
        for (DODatabaseClass dbClass : database.getClasses()) {
            if (dbClass.getShortName().equals("DossPrev")) {
                dossPrevClass = dbClass;
                break;
            }
        }

        if (dossPrevClass == null) {
            System.out.println("DossPrev class not found in database!");
            return;
        }

        System.out.println("Found DossPrev class: " + dossPrevClass.getAbsoluteName());
        DODatabaseObject[] dossPrevObjects = dossPrevClass.getResolvedObjects();
        System.out.println("Total DossPrev objects: " + (dossPrevObjects != null ? dossPrevObjects.length : 0));

        if (dossPrevObjects == null || dossPrevObjects.length == 0) {
            return;
        }

        // Build index of all resolved objects for lookup
        java.util.Map<Long, DODatabaseObject> allObjectsById = new java.util.HashMap<>();
        for (DODatabaseClass dbClass : database.getClasses()) {
            DODatabaseObject[] objects = dbClass.getResolvedObjects();
            if (objects != null) {
                for (DODatabaseObject obj : objects) {
                    allObjectsById.put(obj.getObjectId(), obj);
                }
            }
        }

        // Track statistics per field
        java.util.Map<String, FieldStats> fieldStatsMap = new java.util.LinkedHashMap<>();

        // Analyze each DossPrev object
        for (DODatabaseObject dossPrevObj : dossPrevObjects) {
            // Analyze direct references
            DOObjectReference[] refs = dossPrevObj.getReferences();
            if (refs != null) {
                for (DOObjectReference ref : refs) {
                    String fieldName = ref.getField() != null ? ref.getField().getName() : "[synthetic]";
                    Long targetId = ref.getTargetObjectId();

                    FieldStats stats = fieldStatsMap.computeIfAbsent(fieldName, k -> new FieldStats(fieldName));
                    stats.totalReferences++;

                    if (targetId != null) {
                        // Check if this is an ID-type reference
                        DODatabaseObject idObject = allObjectsById.get(targetId);
                        if (idObject != null) {
                            DOClass targetClass = idObject.getMostSpecificClass();

                            // If the target is an ID object, we need to follow the mID field
                            if (targetClass.getShortName().startsWith("ID")) {
                                // Extract the mID field value from the ID object
                                Object mIdValue = extractFieldValue(idObject, "mID", database);
                                if (mIdValue instanceof Integer) {
                                    Long finalTargetId = ((Integer) mIdValue).longValue();
                                    DODatabaseObject finalTarget = allObjectsById.get(finalTargetId);
                                    if (finalTarget != null) {
                                        stats.resolvedReferences++;
                                    } else {
                                        stats.unresolvedReferences++;
                                        stats.unresolvedTargetIds.add(finalTargetId);
                                        // Extract mContrainte for unresolved ID references
                                        Object contrainte = extractFieldValue(idObject, "mContrainte", database);
                                        if (contrainte != null) {
                                            stats.contrainteValues.put(contrainte,
                                                    stats.contrainteValues.getOrDefault(contrainte, 0) + 1);
                                        }
                                    }
                                } else {
                                    // mID field not found or invalid
                                    stats.unresolvedReferences++;
                                }
                            } else {
                                // Direct reference to a non-ID object - it's resolved
                                stats.resolvedReferences++;
                            }
                        } else {
                            // The immediate target doesn't exist
                            stats.unresolvedReferences++;
                            stats.unresolvedTargetIds.add(targetId);
                        }
                    }
                }
            }

            // Analyze collection references
            DOCollectionReference[] collRefs = dossPrevObj.getCollections();
            if (collRefs != null) {
                for (DOCollectionReference collRef : collRefs) {
                    String fieldName = collRef.getField() != null ? collRef.getField().getName() : "[unknown]";
                    Long[] containedIds = collRef.getContainedObjectIds();

                    if (containedIds != null) {
                        FieldStats stats = fieldStatsMap.computeIfAbsent(fieldName, k -> new FieldStats(fieldName));

                        for (Long containedId : containedIds) {
                            stats.totalReferences++;

                            if (containedId != null) {
                                // Check if this is an ID-type reference
                                DODatabaseObject idObject = allObjectsById.get(containedId);
                                if (idObject != null) {
                                    DOClass targetClass = idObject.getMostSpecificClass();

                                    // If the target is an ID object, follow the mID field
                                    if (targetClass.getShortName().startsWith("ID")) {
                                        Object mIdValue = extractFieldValue(idObject, "mID", database);
                                        if (mIdValue instanceof Integer) {
                                            Long finalTargetId = ((Integer) mIdValue).longValue();
                                            DODatabaseObject finalTarget = allObjectsById.get(finalTargetId);
                                            if (finalTarget != null) {
                                                stats.resolvedReferences++;
                                            } else {
                                                stats.unresolvedReferences++;
                                                stats.unresolvedTargetIds.add(finalTargetId);
                                            }
                                        } else {
                                            stats.unresolvedReferences++;
                                        }
                                    } else {
                                        // Direct reference to a non-ID object
                                        stats.resolvedReferences++;
                                    }
                                } else {
                                    stats.unresolvedReferences++;
                                    stats.unresolvedTargetIds.add(containedId);
                                }
                            }
                        }
                    }
                }
            }
        }

        // Print statistics
        System.out.println("\n=== FIELD RESOLVABILITY STATISTICS FOR DossPrev ===");
        System.out.println(String.format("%-40s %10s %10s %10s %10s",
                "Field Name", "Total", "Resolved", "Unresolved", "% Resolved"));
        System.out.println("-".repeat(80));

        for (FieldStats stats : fieldStatsMap.values()) {
            double percentResolved = stats.totalReferences > 0
                    ? (stats.resolvedReferences * 100.0 / stats.totalReferences)
                    : 0;

            System.out.println(String.format("%-40s %,10d %,10d %,10d %9.1f%%",
                    stats.fieldName,
                    stats.totalReferences,
                    stats.resolvedReferences,
                    stats.unresolvedReferences,
                    percentResolved));
        }
    }

    /**
     * Helper class to track field statistics
     */
    private static class FieldStats {
        String fieldName;
        int totalReferences = 0;
        int resolvedReferences = 0;
        int unresolvedReferences = 0;
        java.util.Set<Long> unresolvedTargetIds = new java.util.HashSet<>();
        java.util.Map<Object, Integer> contrainteValues = new java.util.HashMap<>();

        FieldStats(String fieldName) {
            this.fieldName = fieldName;
        }
    }

}
