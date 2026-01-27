package migration4o.models.schema.comparison;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents property differences for a single field between two schemas.
 * Tracks which properties differ and their respective values.
 */
public class FieldPropertyDifference {
    private final Map<String, PropertyDiff> differences = new HashMap<>();

    public void addDifference(String property, Object referenceValue, Object comparedValue) {
        differences.put(property, new PropertyDiff(referenceValue, comparedValue));
    }

    public boolean hasDifferences() {
        return !differences.isEmpty();
    }

    public Map<String, PropertyDiff> getDifferences() {
        return differences;
    }
}
