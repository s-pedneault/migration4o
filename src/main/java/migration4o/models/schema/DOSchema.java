
package migration4o.models.schema;

import migration4o.util.SchemaUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DOSchema {
    private final DOSchemaClass[] classes;
    private final DOSchemaClass[] foundationClasses;
    public final List<DOSchemaAnomaly> anomalies = new ArrayList<>();
    private final Map<String, DOSchemaField> sharedFields = new LinkedHashMap<>();

    public DOSchema(DOSchemaClass[] classes) {
        this(classes, new DOSchemaClass[0]);
    }

    public DOSchema(DOSchemaClass[] classes, DOSchemaClass[] foundationClasses) {
        this.classes = classes != null ? classes : new DOSchemaClass[0];
        this.foundationClasses = foundationClasses != null ? foundationClasses : new DOSchemaClass[0];
    }

    public DOSchemaClass[] getClasses() {
        return classes;
    }

    public DOSchemaClass[] getFoundationClasses() {
        return foundationClasses;
    }

    public boolean isDescendant(DOSchemaClass schemaClass, String ancestorClassName) {
        return SchemaUtil.isDescendantOf(schemaClass, ancestorClassName, this);
    }

    public DOSchemaClass findClassByName(String className) {
        return SchemaUtil.findClassByName(className, this);
    }

    /**
     * Get all shared field definitions.
     */
    public Map<String, DOSchemaField> getSharedFields() {
        return sharedFields;
    }

    /**
     * Add or update a shared field definition.
     */
    public void addSharedField(String definitionId, DOSchemaField field) {
        sharedFields.put(definitionId, field);
    }

    /**
     * Remove a shared field definition.
     */
    public void removeSharedField(String definitionId) {
        sharedFields.remove(definitionId);
    }

    /**
     * Get a shared field definition by ID.
     */
    public DOSchemaField getSharedField(String definitionId) {
        return sharedFields.get(definitionId);
    }
}