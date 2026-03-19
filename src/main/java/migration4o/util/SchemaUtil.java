package migration4o.util;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SchemaUtil {

    public static ArrayList<DOSchemaField> collectSkipUserOptions(DOSchema schema) {
        if (schema == null || schema.getClasses() == null) {
            return new ArrayList<>();
        }
        ArrayList<DOSchemaField> list = new ArrayList<>();
        for (DOSchemaClass schemaClass : schema.getClasses()) {
            list.addAll(schemaClass.getSkipUserOptions());
        }
        return list;
    }

    /**
     * Converts a field to use a common field definition reference if a matching common field exists in the schema.
     * 
     * @param field the field to potentially convert
     * @param schema the schema containing common field definitions
     * @return a new field using the common definition if found, or the original field
     */
    public static DOSchemaField convertToCommonFieldIfExists(DOSchemaField field, DOSchema schema) {
        if (field == null || schema == null || schema.sharedFields == null || schema.sharedFields.isEmpty()) {
            return field;
        }

        // Check if a common field definition exists for this field's source
        // name
        DOSchemaField commonField = schema.sharedFields.get(field.attributes.source);
        if (commonField != null) {
            // Create a reference to the common field
            DOSchemaField refField = new DOSchemaField(schema, field.parentClass);
            refField.attributes.source = field.attributes.source;
            refField.attributes.definitionId = field.attributes.source; // Mark as reference
            // Copy properties from common field
            refField.attributes.destinationName = commonField.attributes.destinationName;
            refField.attributes.type = commonField.attributes.type;
            refField.attributes.isExported = commonField.attributes.isExported;
            refField.attributes.skipWhen = commonField.attributes.skipWhen;
            refField.attributes.isCollection = commonField.attributes.isCollection;
            refField.attributes.embedContents = commonField.attributes.embedContents;
            refField.attributes.childrenType = commonField.attributes.childrenType;
            refField.attributes.title = commonField.attributes.title;
            refField.attributes.description = commonField.attributes.description;
            refField.attributes.pointsTo = commonField.attributes.pointsTo;
            return refField;
        }

        return field;
    }

    /**
     * Adds a class to a schema, inserting it alphabetically by source name.
     * 
     * @param schema the schema to add the class to
     * @param newClass the class to add
     */
    public static void addClass(DOSchema schema, DOSchemaClass newClass) {
        if (schema == null || newClass == null) {
            return;
        }

        DOSchemaClass[] existing = schema.classes;
        DOSchemaClass[] newArray = new DOSchemaClass[existing.length + 1];

        // Find insertion point (alphabetically by source)
        int insertIndex = 0;
        for (int i = 0; i < existing.length; i++) {
            if (existing[i].attributes.source.compareTo(newClass.attributes.source) < 0) {
                insertIndex = i + 1;
            }
        }

        // Copy elements before insertion point
        System.arraycopy(existing, 0, newArray, 0, insertIndex);

        // Insert new class
        newArray[insertIndex] = newClass;

        // Copy elements after insertion point
        if (insertIndex < existing.length) {
            System.arraycopy(existing, insertIndex, newArray, insertIndex + 1, existing.length - insertIndex);
        }

        schema.classes = newArray;
    }

    /**
     * Finds a schema class by its absolute name.
     */
    public static DOSchemaClass findSchemaClassByName(DOSchema schema, String className) {
        if (schema == null || schema.getClasses() == null) {
            return null;
        }

        for (DOSchemaClass schemaClass : schema.getClasses()) {
            if (className.equals(schemaClass.attributes.source)) {
                return schemaClass;
            }
        }
        return null;
    }

    /**
     * Finds a schema field by its name within a schema class.
     */
    public static DOSchemaField findSchemaFieldByName(DOSchemaClass schemaClass, String fieldName) {
        if (schemaClass == null || schemaClass.fields == null) {
            return null;
        }

        for (DOSchemaField field : schemaClass.fields) {
            if (fieldName.equals(field.attributes.source)) {
                return field;
            }
        }
        return null;
    }

    /**
     * Strips a leading "id" or "ID" prefix (optionally followed by a space) from a field or element name, lowercasing the new first character if it was uppercase. Examples: "IDTypeChampPerso" → "typeChampPerso", "IDPersonne" → "personne", "id type" → "type".
     */
    public static String stripIdPrefix(String name) {
        if (name == null || name.isEmpty())
            return name;
        String stripped = name.replaceFirst("(?i)^id\\s*", "");
        if (stripped.isEmpty() || stripped.equals(name))
            return name;
        return Character.isUpperCase(stripped.charAt(0)) ? Character.toLowerCase(stripped.charAt(0)) + stripped.substring(1) : stripped;
    }
}
