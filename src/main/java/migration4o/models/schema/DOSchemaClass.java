
package migration4o.models.schema;

import java.util.ArrayList;

import migration4o.util.ClassUtil;
import migration4o.util.SchemaUtil;
import migration4o.util.TypeUtil;
import migration4o.util.tools.structuredwriter.StructuredWriterMetadata;

public class DOSchemaClass {

    public DOSchema schema;
    public DOSchemaClassAttributes attributes = new DOSchemaClassAttributes();
    public DOSchemaField[] fields;
    public DOSchemaReference[] schemaReferences;

    public long[] objectIds; // Object IDs from database
    public long[] uniqueObjectIds; // Unique object IDs after deduplication
    public long[] reachedObjectIds; // Object IDs reached during reach analysis

    public DOSchemaClass(DOSchema schema) {
        this.schema = schema;
    }

    public String getSourcePackage() {
        return ClassUtil.getPackageName(attributes.source);
    }

    public String getSourceName() {
        return ClassUtil.getSimpleName(attributes.source);
    }

    public boolean isIDEntite() {
        return isDescendantOf("gest.gen.IDEntite");
    }

    public boolean isEntite() {
        return isDescendantOf("gest.gen.EntiteContientID");
    }

    public boolean isParam() {
        return isDescendantOf("gest.gen.EntiteParam");
    }

    public boolean isPrimitive() {
        return TypeUtil.isPrimitiveType(attributes.source);
    }

    public DOSchemaField findField(String fieldName) {
        if (fields != null) {
            for (DOSchemaField field : fields) {
                if (field.attributes.destinationName.equals(fieldName)) {
                    return field;
                }
            }
        }
        return null;
    }

    /**
     * Sets the fields array and establishes parent links. Each field will have its parentClass set to this class.
     */
    public void setFields(DOSchemaField[] fields) {
        this.fields = fields;
        if (fields != null) {
            for (DOSchemaField field : fields) {
                if (field != null) {
                    field.parentClass = this;
                }
            }
        }
    }

    public ArrayList<DOSchemaField> getSkipUserOptions() {
        ArrayList<DOSchemaField> list = new ArrayList<>();
        for (DOSchemaField field : fields) {
            if (field.attributes.skipUserOption != null && !field.attributes.skipUserOption.trim().isEmpty()) {
                list.add(field);
            }
        }
        return list;
    }

    /**
     * Checks if the given class is a superclass of other Entite-type classes in the schema.
     * 
     * @param schema the schema to search
     * @param targetClass the class to check
     * @return true if at least one other class in the schema has this class as a parent
     */
    public boolean hasSubclasses() {
        if (schema == null || schema.getClasses() == null) {
            return false;
        }

        // Check if any class has this class as its parent
        for (DOSchemaClass schemaClass : schema.getClasses()) {
            if (schemaClass.attributes.parentClassName != null && schemaClass.attributes.parentClassName.equals(attributes.source)) {
                // Found a direct subclass - now verify it's an Entite type
                if (schemaClass.isDescendantOf("gest.gen.Entite")) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Checks if a schema class is a descendant of a given ancestor class.
     * 
     * @param schemaClass the class to check
     * @param ancestorClassName the name of the ancestor class
     * @param schema the schema containing all classes
     * @return true if schemaClass is a descendant of ancestorClassName
     */
    public boolean isDescendantOf(String ancestorClassName) {
        if (ancestorClassName == null) {
            return false;
        }

        if (attributes.source.equals(ancestorClassName)) {
            return true;
        }

        if (attributes.parentClassName == null || attributes.parentClassName.isEmpty()) {
            return false;
        }

        if (attributes.parentClassName.equals(ancestorClassName)) {
            return true;
        }

        // Look up parent class and recurse
        if (schema != null && schema.getClasses() != null) {
            for (DOSchemaClass candidate : schema.getClasses()) {
                if (candidate.attributes.source.equals(attributes.parentClassName)) {
                    return candidate.isDescendantOf(ancestorClassName);
                }
            }
        }

        return false;
    }

    public StructuredWriterMetadata getMetadata(String module) {
        StructuredWriterMetadata metadata = new StructuredWriterMetadata();
        metadata.generator = "Migration4o";
        metadata.provider = "Gestion Technologies";
        metadata.module = module != null ? module : "";
        metadata.type = attributes.destinationName != null ? attributes.destinationName : getSourceName();
        metadata.objects = objectIds != null ? String.valueOf(objectIds.length) : "0";
        return metadata;
    }

}
