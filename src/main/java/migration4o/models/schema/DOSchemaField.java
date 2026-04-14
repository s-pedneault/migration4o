
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
        return attributes.source != null && attributes.source.startsWith(DOSchemaConstants.VIRTUAL_FIELD_PREFIX);
    }

    /**
     * Returns true if this is a value-alias field: a virtual field (source starts with @) that has a valueMap but no criterias. Instead of querying the database, it reads the real sibling field named by {@link #getVirtualFieldName()}, applies the valueMap, and writes the result under {@link #attributes}.destinationName.
     */
    public boolean isValueAliasField() {
        if (!isVirtualField())
            return false;
        if (attributes.valueMap == null || attributes.valueMap.isEmpty())
            return false;
        return attributes.criterias == null || attributes.criterias.isEmpty();
    }

    /**
     * Returns true if this is a scalar virtual field: a virtual field (source starts with @) with no criterias that reads the real sibling field and writes a transformed scalar value. Covers both value-alias fields (valueMap) and format-only fields (format), as well as fields that combine both.
     */
    public boolean isScalarVirtualField() {
        if (!isVirtualField())
            return false;
        if (attributes.criterias != null && !attributes.criterias.isEmpty())
            return false;
        boolean hasValueMap = attributes.valueMap != null && !attributes.valueMap.isEmpty();
        boolean hasFormat = attributes.format != null && !attributes.format.trim().isEmpty();
        return hasValueMap || hasFormat;
    }

    /**
     * Gets the actual field name for a virtual field (removes the @ prefix).
     */
    public String getVirtualFieldName() {
        if (isVirtualField()) {
            return attributes.source.substring(DOSchemaConstants.VIRTUAL_FIELD_PREFIX.length());
        }
        return attributes.source;
    }

    /**
     * Returns true if this is a method-call field (source ends with "()"). Method-call fields invoke a no-arg method on the object via reflection.
     */
    public boolean isMethodCallField() {
        return attributes.source != null && attributes.source.endsWith(DOSchemaConstants.METHOD_CALL_SUFFIX);
    }

    /**
     * Gets the method name for a method-call field (removes the "()" suffix).
     */
    public String getMethodCallName() {
        if (isMethodCallField()) {
            return attributes.source.substring(0, attributes.source.length() - DOSchemaConstants.METHOD_CALL_SUFFIX.length());
        }
        return null;
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
        copy.attributes.group = this.attributes.group;

        // Deep copy value map
        if (this.attributes.valueMap != null) {
            copy.attributes.valueMap = this.attributes.valueMap.copy();
        }

        // Note: childrenSchemaClass and parentClass are not copied as they're
        // set later
        return copy;
    }
}