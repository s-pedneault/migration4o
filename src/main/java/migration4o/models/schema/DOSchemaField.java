
package migration4o.models.schema;

import java.util.ArrayList;

public class DOSchemaField {
    public DOSchema schema;
    public DOSchemaClass parentClass;
    public DOSchemaFieldAttributes attributes = new DOSchemaFieldAttributes();

    public DOSchemaClass childrenSchemaClass;

    public DOSchemaField(DOSchema schema, DOSchemaClass parentClass) {
        this.schema = schema;
        this.parentClass = parentClass;
    }

    /**
     * Returns true if this field is a reference to a shared field definition.
     */
    public boolean isSharedField() {
        return attributes.definitionId != null && !attributes.definitionId.trim().isEmpty();
    }

    /**
     * Returns true if this is a virtual field (source starts with @). Virtual fields query the database for related objects instead of reading actual fields.
     */
    public boolean isVirtualField() {
        return attributes.source != null && attributes.source.startsWith("@");
    }

    /**
     * Gets the actual field name for a virtual field (removes the @ prefix).
     */
    public String getVirtualFieldName() {
        if (isVirtualField()) {
            return attributes.source.substring(1);
        }
        return attributes.source;
    }

    /**
     * Creates a deep copy of this field (used when instantiating shared fields).
     */
    public DOSchemaField copy() {
        DOSchemaField copy = new DOSchemaField(schema, parentClass);
        copy.attributes = new DOSchemaFieldAttributes();

        // Deep copy criterias
        if (this.attributes.criterias != null) {
            copy.attributes.criterias = new ArrayList<>();
            for (DOFieldCriteria criteria : this.attributes.criterias) {
                copy.attributes.criterias.add(new DOFieldCriteria(criteria.match, criteria.with, criteria.operator));
            }
        }
        copy.attributes.criteriasOperator = this.attributes.criteriasOperator;

        // y.source = this.source;
        copy.attributes.destinationName = this.attributes.destinationName;
        copy.attributes.type = this.attributes.type;
        copy.attributes.format = this.attributes.format;
        copy.attributes.isExported = this.attributes.isExported;
        copy.attributes.skipWhen = this.attributes.skipWhen;
        copy.attributes.skipUserOption = this.attributes.skipUserOption;
        copy.attributes.isCollection = this.attributes.isCollection;
        copy.attributes.embedContents = this.attributes.embedContents;
        copy.attributes.childrenType = this.attributes.childrenType;
        copy.attributes.title = this.attributes.title;
        copy.attributes.description = this.attributes.description;
        copy.attributes.pointsTo = this.attributes.pointsTo;
        copy.attributes.definitionId = this.attributes.definitionId;

        // Deep copy value map
        if (this.attributes.valueMap != null) {
            copy.attributes.valueMap = this.attributes.valueMap.copy();
        }

        // Note: childrenSchemaClass and parentClass are not copied as they're
        // set later
        return copy;
    }
}