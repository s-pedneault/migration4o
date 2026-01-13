package migration4o.schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.db4o.ObjectSet;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.query.Query;

import migration4o.database.DODatabaseReader;
import migration4o.models.database.DODatabase;
import migration4o.models.database.DODatabaseClass;
import migration4o.models.database.DODatabaseField;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.schema.DOSchemaModule;
import migration4o.util.CollectionTypeUtil;

/**
 * Infers a DOSchema model from an actual DB4O database file.
 * This class bridges the gap between database reading
 * (DODatabase/DODatabaseClass)
 * and schema representation (DOSchema/DOSchemaClass), allowing comparison
 * between
 * actual database structure and intended schema.
 * 
 * Uses existing database reading infrastructure rather than direct DB4O calls.
 */
public class DODatabaseSchemaInferrer {

    private final DODatabaseReader databaseReader;

    public DODatabaseSchemaInferrer() {
        this.databaseReader = new DODatabaseReader();
    }

    /**
     * Infers a complete DOSchema from a database that has already been read.
     * 
     * @param database The database structure read from a DB4O file
     * @return A DOSchema representing the actual database structure
     */
    public DOSchema inferSchemaFromDatabase(DODatabase database) {
        if (database == null || database.getClasses() == null) {
            return createEmptySchema();
        }

        System.out.println("Inferring schema from database with " + database.getClasses().length + " classes");

        // Convert database classes to schema classes
        List<DOSchemaClass> schemaClasses = new ArrayList<>();
        Map<String, DODatabaseClass> databaseClassMap = createDatabaseClassMap(database);
        ExtObjectContainer container = database.getContainer();

        for (DODatabaseClass dbClass : database.getClasses()) {
            try {
                DOSchemaClass schemaClass = convertDatabaseClassToSchemaClass(dbClass, databaseClassMap, container);
                schemaClasses.add(schemaClass);
            } catch (Exception e) {
                System.out.println("Warning: Could not convert database class '" +
                        dbClass.getAbsoluteName() + "' to schema class: " + e.getMessage());
            }
        }

        // Create modules - single module containing all classes
        DOSchemaModule[] modules = new DOSchemaModule[] {
                new DOSchemaModule("Database Classes", schemaClasses.toArray(new DOSchemaClass[0]))
        };

        // Create schema
        DOSchema schema = new DOSchema(
                schemaClasses.toArray(new DOSchemaClass[0]),
                modules,
                new DOSchemaClass[0] // No foundation classes from database
        );

        // Deduplicate object IDs across inheritance hierarchies
        schema = deduplicateObjectIdsInInheritanceHierarchies(schema);

        System.out.println("Successfully inferred schema with " + schemaClasses.size() + " classes");

        return schema;
    }

    /**
     * Converts a DODatabaseClass to a DOSchemaClass.
     */
    private DOSchemaClass convertDatabaseClassToSchemaClass(
            DODatabaseClass dbClass,
            Map<String, DODatabaseClass> databaseClassMap,
            ExtObjectContainer container) {

        String absoluteName = dbClass.getAbsoluteName();
        String simpleName = dbClass.getShortName();
        String description = buildDescriptionFromDatabase(dbClass);
        String title = dbClass.getTitle();
        String parentClassName = dbClass.getSuperClassAbsoluteName();

        // Convert fields
        DOSchemaField[] schemaFields = convertDatabaseFieldsToSchemaFields(
                dbClass.getFields(),
                databaseClassMap,
                container,
                absoluteName);

        // Get object IDs from DODatabaseClass (already captured during database
        // loading)
        long[] objectIds = dbClass.getObjectIds();

        // Create schema class - all database classes are marked as migrate=true
        return new DOSchemaClass(
                absoluteName,
                simpleName,
                description,
                title,
                parentClassName,
                schemaFields,
                null, // schemaReferences - not available from database
                true, // migrate - assume all database classes should be migrated
                objectIds);
    }

    /**
     * Converts database fields to schema fields.
     */
    private DOSchemaField[] convertDatabaseFieldsToSchemaFields(
            DODatabaseField[] dbFields,
            Map<String, DODatabaseClass> databaseClassMap,
            ExtObjectContainer container,
            String className) {

        if (dbFields == null || dbFields.length == 0) {
            return new DOSchemaField[0];
        }

        List<DOSchemaField> schemaFields = new ArrayList<>();

        for (DODatabaseField dbField : dbFields) {
            try {
                DOSchemaField schemaField = convertDatabaseFieldToSchemaField(dbField, databaseClassMap, container,
                        className);
                schemaFields.add(schemaField);
            } catch (Exception e) {
                System.out.println("Warning: Could not convert field '" +
                        dbField.getName() + "': " + e.getMessage());
            }
        }

        return schemaFields.toArray(new DOSchemaField[0]);
    }

    /**
     * Converts a single database field to a schema field.
     */
    private DOSchemaField convertDatabaseFieldToSchemaField(
            DODatabaseField dbField,
            Map<String, DODatabaseClass> databaseClassMap,
            ExtObjectContainer container,
            String className) {

        String source = dbField.getName();
        String destination = source; // Use same name for destination
        String type = determineFieldType(dbField);
        boolean isExported = true; // Assume all database fields are exported
        boolean skipIfEmpty = true; // Default behavior
        boolean isCollection = CollectionTypeUtil.isCollection(dbField);
        boolean embedContents = false; // Default - don't embed
        String childrenType = determineChildrenType(dbField, databaseClassMap, container, className);

        return new DOSchemaField(
                source,
                destination,
                type,
                isExported,
                skipIfEmpty,
                isCollection,
                embedContents,
                childrenType,
                null, // title
                dbField.getDescription(),
                null, // databaseClass - will be linked later
                null // childrenSchemaClass - will be linked later
        );
    }

    /**
     * Type normalization map - converts fully qualified types to their canonical
     * form.
     * Edit this map to add more type normalizations as needed.
     */
    private static final java.util.Map<String, String> TYPE_NORMALIZATION_MAP = new java.util.HashMap<String, String>() {
        {
            put("java.lang.String", "string");
            put("java.util.Date", "date");
            put("java.lang.Object", "object");
            put("java.lang.Integer", "int");
            put("java.lang.Long", "long");
            put("java.lang.Boolean", "boolean");
            put("java.lang.Double", "double");
            put("java.lang.Float", "float");
        }
    };

    /**
     * Normalizes a type name to match schema conventions.
     */
    private String normalizeTypeName(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return typeName;
        }

        // Check if we have a normalization rule for this type
        String normalized = TYPE_NORMALIZATION_MAP.get(typeName);
        return normalized != null ? normalized : typeName;
    }

    /**
     * Determines the type of a field.
     */
    private String determineFieldType(DODatabaseField dbField) {
        String typeName = dbField.getTypeName();

        if (typeName == null || typeName.isEmpty()) {
            return "java.lang.Object";
        }

        // For arrays, get the component type
        if (dbField.isArray() && typeName.endsWith("[]")) {
            typeName = typeName.substring(0, typeName.length() - 2);
        }

        // Normalize the type name
        return normalizeTypeName(typeName);
    }

    /**
     * Determines the children type for collection fields.
     */
    private String determineChildrenType(DODatabaseField dbField, Map<String, DODatabaseClass> databaseClassMap,
            ExtObjectContainer container, String className) {
        if (!CollectionTypeUtil.isCollection(dbField)) {
            return "";
        }

        // Check if content type class is already set
        if (dbField.getContentTypeClass() != null) {
            return dbField.getContentTypeClass().getAbsoluteName();
        }

        // Check if content type name is already set
        if (dbField.getContentTypeName() != null && !dbField.getContentTypeName().isEmpty()) {
            return dbField.getContentTypeName();
        }

        // Fallback to empty string
        return "";
    }

    /**
     * Builds a description from database class information.
     */
    private String buildDescriptionFromDatabase(DODatabaseClass dbClass) {
        StringBuilder desc = new StringBuilder();

        if (dbClass.getDescription() != null && !dbClass.getDescription().isEmpty()) {
            desc.append(dbClass.getDescription());
        } else {
            desc.append("Inferred from database");
        }

        // Add object count information
        if (dbClass.getTotalObjectCount() > 0) {
            desc.append(" (").append(dbClass.getTotalObjectCount()).append(" objects)");
        }

        return desc.toString();
    }

    /**
     * Creates a map of database classes by name for quick lookup.
     */
    private Map<String, DODatabaseClass> createDatabaseClassMap(DODatabase database) {
        Map<String, DODatabaseClass> map = new HashMap<>();

        if (database.getClasses() != null) {
            for (DODatabaseClass dbClass : database.getClasses()) {
                map.put(dbClass.getAbsoluteName(), dbClass);
                // Also map by short name for convenience
                if (!dbClass.getShortName().equals(dbClass.getAbsoluteName())) {
                    map.put(dbClass.getShortName(), dbClass);
                }
            }
        }

        return map;
    }

    /**
     * Creates an empty schema when database is null or empty.
     */
    private DOSchema createEmptySchema() {
        return new DOSchema(
                new DOSchemaClass[0],
                new DOSchemaModule[0],
                new DOSchemaClass[0]);
    }

    /**
     * Deduplicates object IDs across inheritance hierarchies.
     * 
     * DB4O stores each object at every level of its inheritance chain, so the same
     * object ID appears in the parent class, grandparent class, etc. This method
     * removes duplicate IDs by keeping them only in the most derived (leaf) class.
     * 
     * Algorithm:
     * 1. Find all leaf classes (classes with no subclasses)
     * 2. For each leaf class, get its object IDs
     * 3. For each object ID, walk up the parent chain and remove it from ancestors
     * 
     * @param schema The schema with potentially duplicate object IDs
     * @return A new schema with deduplicated object IDs
     */
    private DOSchema deduplicateObjectIdsInInheritanceHierarchies(DOSchema schema) {
        System.out.println("Deduplicating object IDs across inheritance hierarchies...");

        // Build class map for quick lookup
        Map<String, DOSchemaClass> classMap = new HashMap<>();
        for (DOSchemaClass schemaClass : schema.getClasses()) {
            classMap.put(schemaClass.getAbsoluteName(), schemaClass);
        }

        // Build subclass map to identify leaf classes
        Map<String, List<String>> subclassMap = new HashMap<>();
        for (DOSchemaClass schemaClass : schema.getClasses()) {
            String parentName = schemaClass.getSuperClassAbsoluteName();
            if (parentName != null && !parentName.isEmpty()) {
                subclassMap.computeIfAbsent(parentName, k -> new ArrayList<>())
                        .add(schemaClass.getAbsoluteName());
            }
        }

        int leafClassCount = 0;
        int nonLeafClassCount = 0;

        // Collect IDs to remove from each class
        Map<String, java.util.Set<Long>> idsToRemove = new HashMap<>();

        // Process each class
        for (DOSchemaClass schemaClass : schema.getClasses()) {
            String className = schemaClass.getAbsoluteName();

            // Check if this is a leaf class (no subclasses)
            boolean isLeaf = !subclassMap.containsKey(className);

            if (isLeaf) {
                leafClassCount++;
            } else {
                nonLeafClassCount++;
            }

            if (isLeaf && schemaClass.getObjectIds() != null) {
                // For each object ID in this leaf class
                for (long objectId : schemaClass.getObjectIds()) {
                    // Walk up the parent chain and mark this ID for removal
                    String currentParent = schemaClass.getSuperClassAbsoluteName();
                    while (currentParent != null && !currentParent.isEmpty()) {
                        idsToRemove.computeIfAbsent(currentParent, k -> new java.util.HashSet<>())
                                .add(objectId);

                        DOSchemaClass parentClass = classMap.get(currentParent);
                        if (parentClass == null) {
                            break;
                        }
                        currentParent = parentClass.getSuperClassAbsoluteName();
                    }
                }
            }
        }

        // Update uniqueObjectIds in classes based on deduplication
        for (DOSchemaClass schemaClass : schema.getClasses()) {
            String className = schemaClass.getAbsoluteName();
            java.util.Set<Long> toRemove = idsToRemove.get(className);

            if (toRemove != null && !toRemove.isEmpty() && schemaClass.getObjectIds() != null) {
                // Filter out the IDs that belong to derived classes
                long[] originalIds = schemaClass.getObjectIds();
                List<Long> filteredIds = new ArrayList<>();

                for (long id : originalIds) {
                    if (!toRemove.contains(id)) {
                        filteredIds.add(id);
                    }
                }

                // Set unique IDs on the class
                long[] uniqueIds = new long[filteredIds.size()];
                for (int i = 0; i < filteredIds.size(); i++) {
                    uniqueIds[i] = filteredIds.get(i);
                }
                schemaClass.setUniqueObjectIds(uniqueIds);

                int removedCount = originalIds.length - uniqueIds.length;
                if (removedCount > 0) {
                    System.out.println("Deduplicated " + removedCount + " object IDs from " + className +
                            " (" + originalIds.length + " -> " + uniqueIds.length + ")");
                }
            }
            // else: uniqueObjectIds is already initialized as a copy of objectIds
        }

        System.out.println("Object ID deduplication complete: " + leafClassCount + " leaf classes, " +
                nonLeafClassCount + " non-leaf classes");

        return schema;
    }
}
