package migration4o.models.schema;

/**
 * Represents a criteria for virtual field queries.
 * Used to match objects in the database based on field values.
 */
public class DOFieldCriteria {
    public String match; // Field reference in parent object (e.g., "this.mID")
    public String with; // Field name in target class (e.g., "mIDIntervention")
    public String operator; // Comparison operator: equals (default), notEquals, greaterThan, lessThan,
                            // greaterOrEqual, lessOrEqual

    public DOFieldCriteria() {
        this.operator = "equals"; // Default to equality check
    }

    public DOFieldCriteria(String match, String with) {
        this.match = match;
        this.with = with;
        this.operator = "equals";
    }

    public DOFieldCriteria(String match, String with, String operator) {
        this.match = match;
        this.with = with;
        this.operator = operator != null ? operator : "equals";
    }
}
