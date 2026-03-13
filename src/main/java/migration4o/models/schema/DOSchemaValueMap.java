package migration4o.models.schema;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Encapsulates value mapping logic for a schema field. Maps raw database values
 * to desired export values, preserving insertion order.
 */
public class DOSchemaValueMap {

    private final Map<String, String> entries;

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
        return entries.getOrDefault(databaseValue, databaseValue);
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
        return new DOSchemaValueMap(this.entries);
    }
}
