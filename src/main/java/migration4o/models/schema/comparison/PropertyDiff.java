package migration4o.models.schema.comparison;

/**
 * Represents a single property difference between two schema elements.
 * Holds the reference value and compared value for a specific property.
 */
public class PropertyDiff {
    private final Object referenceValue;
    private final Object comparedValue;

    public PropertyDiff(Object referenceValue, Object comparedValue) {
        this.referenceValue = referenceValue;
        this.comparedValue = comparedValue;
    }

    public Object getReferenceValue() {
        return referenceValue;
    }

    public Object getComparedValue() {
        return comparedValue;
    }
}
