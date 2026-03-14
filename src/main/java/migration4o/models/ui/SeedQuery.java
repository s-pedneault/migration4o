package migration4o.models.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * A seed query that identifies initial objects for seed-based export.
 * Specifies a class name and a list of conditions that must ALL match (AND logic).
 */
public class SeedQuery {

    private String className;
    private List<SeedCondition> conditions = new ArrayList<>();

    public SeedQuery() {
    }

    public SeedQuery(String className) {
        this.className = className;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public List<SeedCondition> getConditions() {
        return conditions;
    }

    public void setConditions(List<SeedCondition> conditions) {
        this.conditions = conditions != null ? conditions : new ArrayList<>();
    }

    public void addCondition(SeedCondition condition) {
        conditions.add(condition);
    }

    /**
     * Returns a human-readable summary of this seed query.
     */
    public String getSummary() {
        String simpleName = className;
        int dot = className.lastIndexOf('.');
        if (dot >= 0) {
            simpleName = className.substring(dot + 1);
        }
        if (conditions.isEmpty()) {
            return simpleName + " (all objects)";
        }
        StringBuilder sb = new StringBuilder(simpleName);
        sb.append(" WHERE ");
        for (int i = 0; i < conditions.size(); i++) {
            if (i > 0)
                sb.append(" AND ");
            sb.append(conditions.get(i));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return getSummary();
    }
}
