package dataobjects;

import dataobjects.impl.models.DOClass;
import dataobjects.impl.models.DOField;
import dataobjects.impl.models.DOReference;
import dataobjects.impl.models.database.DODatabase;
import dataobjects.impl.models.database.DODatabaseClass;
import dataobjects.impl.engine.DOEngine;
import dataobjects.impl.models.schema.DOSchema;
import dataobjects.impl.models.schema.DOSchemaClass;
import dataobjects.util.CollectionTypeUtil;
import dataobjects.util.TypeUtil;

import java.util.ArrayList;
import java.util.List;

public class DOEnginePrintout {

    // Data structure to store information about unresolved collection fields
    private static class UnresolvedCollectionField {
        public String className;
        public String fieldName;
        public String collectionType;
        public String contentTypeName;

        public UnresolvedCollectionField(String className, String fieldName, String collectionType,
                String contentTypeName) {
            this.className = className;
            this.fieldName = fieldName;
            this.collectionType = collectionType;
            this.contentTypeName = contentTypeName;
        }
    }

    // List to collect all unresolved collection fields during printing
    private List<UnresolvedCollectionField> unresolvedCollectionFields;

    /**
     * Prints the complete hierarchy of a DOEngine, including schema and database
     * structures.
     * This method validates that the engine is fully loaded with resolved field
     * types and references.
     */
    public void printEngineHierarchy(DOEngine engine) {
        if (engine == null) {
            System.out.println("Engine is null");
            return;
        }

        // Initialize the list to collect unresolved collection fields
        unresolvedCollectionFields = new ArrayList<>();

        System.out.println("=".repeat(80));
        System.out.println("DOEngine Hierarchy Report");
        System.out.println("=".repeat(80));

        printSchema(engine.getSchema());
        printDatabase(engine.getDatabase());

        // Print summary of unresolved collection fields
        printUnresolvedCollectionFieldsSummary();

        System.out.println("=".repeat(80));
        System.out.println("End of DOEngine Hierarchy Report");
        System.out.println("=".repeat(80));
    }

    private void printSchema(DOSchema schema) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("SCHEMA STRUCTURE");
        System.out.println("=".repeat(60));

        if (schema == null) {
            System.out.println("Schema is null");
            return;
        }

        DOSchemaClass[] classes = schema.getClasses();
        if (classes == null || classes.length == 0) {
            System.out.println("No schema classes found");
            return;
        }

        System.out.println("Total Schema Classes: " + classes.length);
        System.out.println();

        for (int i = 0; i < classes.length; i++) {
            DOSchemaClass schemaClass = classes[i];
            System.out.println("[" + (i + 1) + "/" + classes.length + "] Schema Class:");
            printClass(schemaClass, "  ");

            // Print schema-specific information
            printSchemaClassInfo(schemaClass, "  ");
        }
    }

    private void printDatabase(DODatabase database) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("DATABASE STRUCTURE");
        System.out.println("=".repeat(60));

        if (database == null) {
            System.out.println("Database is null");
            return;
        }

        System.out.println("Database Statistics:");
        System.out.println("  Total Classes: " + database.getTotalClasses());
        System.out.println("  Total Objects: " + database.getTotalObjects());
        System.out.println("  Database Size: " + database.getDatabaseSize());
        System.out.println();

        DODatabaseClass[] classes = database.getClasses();
        if (classes == null || classes.length == 0) {
            System.out.println("No database classes found");
            return;
        }

        for (int i = 0; i < classes.length; i++) {
            DODatabaseClass databaseClass = classes[i];
            System.out.println("[" + (i + 1) + "/" + classes.length + "] Database Class:");
            printClass(databaseClass, "  ");

            // Print database-specific information
            System.out.println("  Database Info:");
            System.out.println("    Total Objects: " + databaseClass.getTotalObjectCount());
            System.out.println("    Migrated Objects: " + databaseClass.getMigratedObjectCount());
        }
    }

    private void printClass(DOClass clazz, String indent) {
        if (clazz == null) {
            System.out.println(indent + "Class is null");
            return;
        }

        System.out.println(indent + "Class: " + clazz.getAbsoluteName());
        System.out.println(indent + "  Name: " + clazz.getShortName());
        System.out.println(
                indent + "  Description: " + (clazz.getDescription() != null ? clazz.getDescription() : "N/A"));
        System.out.println(indent + "  Title: " + (clazz.getTitle() != null ? clazz.getTitle() : "N/A"));
        System.out.println(indent + "  Super Class: "
                + (clazz.getSuperClassAbsoluteName() != null ? clazz.getSuperClassAbsoluteName() : "N/A"));

        // Print fields
        DOField[] fields = clazz.getFields();
        if (fields != null && fields.length > 0) {
            System.out.println(indent + "  Fields (" + fields.length + "):");
            for (DOField field : fields) {
                printField(field, clazz.getAbsoluteName(), indent + "    ");
            }
        } else {
            System.out.println(indent + "  Fields: None");
        }

        // Print references
        DOReference[] references = clazz.getReferences();
        if (references != null && references.length > 0) {
            System.out.println(indent + "  Referenced by (" + references.length + "):");
            for (DOReference reference : references) {
                printReference(reference, indent + "    ");
            }
        } else {
            System.out.println(indent + "  Referenced by: None");
        }

        System.out.println();
    }

    private void printField(DOField field, String className, String indent) {
        if (field == null) {
            System.out.println(indent + "Field is null");
            return;
        }

        // Build field info on one line
        StringBuilder fieldInfo = new StringBuilder();
        fieldInfo.append(field.getName());

        // Add type information
        String typeName = field.getTypeName();
        boolean isArray = field.isArray();
        boolean isCollection = CollectionTypeUtil.isCollection(field);

        if (isArray) {
            fieldInfo.append(": ").append(typeName).append("[]");
        } else {
            fieldInfo.append(": ").append(typeName);
        }

        // Add type resolution status with appropriate emoji
        DOClass typeClass = field.getTypeClass();
        if (TypeUtil.isPrimitiveType(field)) {
            fieldInfo.append(" 🔧"); // Wrench emoji for primitive/supported types
        } else if (typeClass != null) {
            fieldInfo.append(" ✅"); // Check mark for resolved complex types
        } else {
            fieldInfo.append(" ❌"); // X mark for unresolved complex types
        }

        // Add collection content type info if applicable
        if (isCollection) {
            String contentTypeName = CollectionTypeUtil.getCollectionContentType(field);

            if (contentTypeName != null && !contentTypeName.equals(typeName)) {
                if (TypeUtil.isPrimitiveType(contentTypeName)) {
                    fieldInfo.append(" 📦🔧 ").append(contentTypeName); // Box + wrench for collection of primitives
                } else {
                    // Content type is known and specific - consider it resolved regardless of
                    // DOClass
                    if (isContentTypeFullyResolved(contentTypeName)) {
                        fieldInfo.append(" 📦✅ ").append(contentTypeName); // Box + check for collection of resolved
                                                                           // types
                    } else {
                        fieldInfo.append(" 📦❌ ").append(contentTypeName); // Box + X for collection of unresolved types

                        // Only collect truly unresolved collection fields (java.lang.Object)
                        unresolvedCollectionFields.add(new UnresolvedCollectionField(
                                className,
                                field.getName(),
                                typeName,
                                contentTypeName));
                    }
                }
            } else if (contentTypeName != null) {
                // Content type is same as collection type (e.g., String[] -> String)
                if (TypeUtil.isPrimitiveType(contentTypeName)) {
                    fieldInfo.append(" 📦🔧 ").append(contentTypeName);
                } else if (isContentTypeFullyResolved(contentTypeName)) {
                    fieldInfo.append(" 📦✅ ").append(contentTypeName); // Resolved content type
                } else {
                    fieldInfo.append(" 📦❌ ").append(contentTypeName); // Unresolved content type

                    // Only collect truly unresolved collection fields (java.lang.Object)
                    unresolvedCollectionFields.add(new UnresolvedCollectionField(
                            className,
                            field.getName(),
                            typeName,
                            contentTypeName));
                }
            } else {
                fieldInfo.append(" 📦❓ Unknown"); // Box + question for unknown content type

                // Unknown content type is definitely unresolved
                unresolvedCollectionFields.add(new UnresolvedCollectionField(
                        className,
                        field.getName(),
                        typeName,
                        "Unknown"));
            }
        }

        System.out.println(indent + fieldInfo.toString());
    }

    private void printReference(DOReference reference, String indent) {
        if (reference == null) {
            System.out.println(indent + "Reference is null");
            return;
        }

        DOClass referencedClass = reference.getReferencedClass();
        DOField referencedField = reference.getReferencedField();

        if (referencedClass != null && referencedField != null) {
            System.out.println(indent + "✅ " + referencedClass.getAbsoluteName() +
                    " -> field: " + referencedField.getName() +
                    " (type: " + referencedField.getTypeName() + ")");
        } else {
            System.out.println(indent + "❌ Incomplete reference - Class: " +
                    (referencedClass != null ? referencedClass.getAbsoluteName() : "null") + ", Field: "
                    + (referencedField != null ? referencedField.getName() : "null"));
        }
    }

    private void printSchemaClassInfo(DOSchemaClass schemaClass, String indent) {
        if (schemaClass == null) {
            return;
        }

        System.out.println(indent + "Schema Info:");
        System.out.println(indent + "  Export Name: " + schemaClass.getExportName());

        // Check if database class is resolved
        dataobjects.impl.models.database.DODatabaseClass databaseClass = schemaClass.getDatabaseClass();
        if (databaseClass != null) {
            System.out.println(indent + "  ✅ Database Class Resolved: " + databaseClass.getAbsoluteName());
            System.out.println(indent + "    Database Objects: " + databaseClass.getTotalObjectCount());
            System.out.println(indent + "    Migrated Objects: " + databaseClass.getMigratedObjectCount());
        } else {
            System.out.println(indent + "  ❌ Database Class: Not found in database");
        }
    }

    /**
     * Prints a summary of all collection fields whose content type was not
     * resolved.
     */
    private void printUnresolvedCollectionFieldsSummary() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("UNRESOLVED COLLECTION FIELDS SUMMARY");
        System.out.println("=".repeat(60));

        if (unresolvedCollectionFields.isEmpty()) {
            System.out.println("✅ All collection field content types were successfully resolved!");
        } else {
            System.out.println("❌ Found " + unresolvedCollectionFields.size()
                    + " collection fields with unresolved content types:");
            System.out.println();

            for (UnresolvedCollectionField field : unresolvedCollectionFields) {
                System.out.println("  " + field.className + "." + field.fieldName +
                        " : " + field.collectionType +
                        " <" + field.contentTypeName + ">");
            }
        }
        System.out.println();
    }

    /**
     * Determines if a content type name is considered fully resolved.
     * A content type is considered resolved if:
     * - It's not java.lang.Object (which indicates unknown/generic type)
     * - It's not null or empty
     * - It's not "Unknown"
     * 
     * @param contentTypeName The content type name to check
     * @return true if the content type is considered resolved
     */
    private boolean isContentTypeFullyResolved(String contentTypeName) {
        if (contentTypeName == null || contentTypeName.isEmpty()) {
            return false;
        }

        // java.lang.Object indicates an unresolved/generic type
        if (contentTypeName.equals("java.lang.Object")) {
            return false;
        }

        // "Unknown" indicates we couldn't determine the type
        if (contentTypeName.equals("Unknown")) {
            return false;
        }

        // Any other specific class name is considered resolved
        return true;
    }
}
