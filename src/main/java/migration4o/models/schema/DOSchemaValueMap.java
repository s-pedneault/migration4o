package migration4o.models.schema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Encapsulates value mapping logic for a schema field. Maps raw database values
 * to desired export values, preserving insertion order.
 *
 * <p>
 * When {@link #bitmask} is {@code true}, the {@code from} keys are treated as
 * powers-of-two flags. A raw value that is a bitwise OR of several flags is
 * decomposed and the corresponding labels are joined with {@code ", "}.
 */
public class DOSchemaValueMap {

    private final Map<String, String> entries;

    /**
     * When {@code true}, the value map uses bitmask (flags) semantics: a
     * database value that is a sum of several power-of-two keys is decomposed
     * into its constituent flags and their labels are joined with {@code ", "}.
     */
    public boolean bitmask;

    public DOSchemaValueMap() {
        this.entries = new LinkedHashMap<>();
    }

    public DOSchemaValueMap(Map<String, String> source) {
        this.entries = source != null ? new LinkedHashMap<>(source) : new LinkedHashMap<>();
    }

    /**
     * Creates a DOSchemaValueMap from a plain map. Returns {@code null} when
     * the source map is null or empty, matching the nullable convention used on
     * {@link DOSchemaField#valueMap}.
     */
    public static DOSchemaValueMap copyOf(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        return new DOSchemaValueMap(map);
    }

    // -------------------------------------------------------------------------
    // Core operations
    // -------------------------------------------------------------------------

    /**
     * Returns the mapped export value for {@code databaseValue}, or
     * {@code databaseValue} itself when no mapping is defined.
     */
    public String getMappedValue(String databaseValue) {
        if (isEmpty() || databaseValue == null) {
            return databaseValue;
        }
        if (bitmask) {
            return getMappedValueBitmask(databaseValue);
        }
        return entries.getOrDefault(databaseValue, databaseValue);
    }

    /**
     * Bitmask resolution: parse the value as an integer, then iterate each
     * power-of-two key in the map. For each key whose bit is set in the value,
     * append the label. If nothing matches (or the value is not a valid
     * integer), fall back to the raw value.
     */
    private String getMappedValueBitmask(String databaseValue) {
        long raw;
        try {
            raw = Long.parseLong(databaseValue.trim());
        } catch (NumberFormatException e) {
            return databaseValue; // not a number — pass through
        }
        if (raw == 0) {
            // Check for an explicit mapping of 0
            return entries.getOrDefault("0", databaseValue);
        }
        List<String> labels = new ArrayList<>();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            long key;
            try {
                key = Long.parseLong(entry.getKey().trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (key != 0 && (raw & key) == key) {
                labels.add(entry.getValue());
            }
        }
        return labels.isEmpty() ? databaseValue : String.join(", ", labels);
    }

    /**
     * Adds or replaces a mapping from {@code fromValue} to {@code toValue}.
     */
    public void add(String fromValue, String toValue) {
        entries.put(fromValue, toValue);
    }

    // -------------------------------------------------------------------------
    // Map-view delegations used by consumers
    // -------------------------------------------------------------------------

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public Collection<String> values() {
        return entries.values();
    }

    public Set<Map.Entry<String, String>> entrySet() {
        return entries.entrySet();
    }

    /**
     * Returns the underlying map for serialisation (schema writer, XSD builder,
     * etc.).
     */
    public Map<String, String> toMap() {
        return entries;
    }

    // -------------------------------------------------------------------------
    // Copy support
    // -------------------------------------------------------------------------

    /** Returns a deep copy of this value map. */
    public DOSchemaValueMap copy() {
        DOSchemaValueMap c = new DOSchemaValueMap(this.entries);
        c.bitmask = this.bitmask;
        return c;
    }
}
