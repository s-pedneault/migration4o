package migration4o.models.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for exporting a specific class within a module.
 * Includes destination file name and optional filter criteria.
 * The same class can appear multiple times in a module with different
 * configurations.
 */
public class ClassExportConfig {

    private final String className;
    private final String destinationFileName; // null means use className as default
    private final List<ExportCriteria> criteria;

    /**
     * Creates a simple config with just the class name (backward compatibility).
     */
    public ClassExportConfig(String className) {
        this(className, null, Collections.emptyList());
    }

    /**
     * Creates a config with custom destination file name.
     */
    public ClassExportConfig(String className, String destinationFileName) {
        this(className, destinationFileName, Collections.emptyList());
    }

    /**
     * Creates a full config with class name, destination file, and filter criteria.
     */
    public ClassExportConfig(String className, String destinationFileName, List<ExportCriteria> criteria) {
        this.className = className;
        this.destinationFileName = destinationFileName;
        this.criteria = new ArrayList<>(criteria);
    }

    public String getClassName() {
        return className;
    }

    /**
     * Returns the destination file name, or the class name if none specified.
     */
    public String getDestinationFileName() {
        return destinationFileName != null && !destinationFileName.isEmpty()
                ? destinationFileName
                : className;
    }

    /**
     * Returns true if a custom destination file name is set.
     */
    public boolean hasCustomDestination() {
        return destinationFileName != null && !destinationFileName.isEmpty();
    }

    /**
     * Returns the raw destination file name (may be null).
     */
    public String getRawDestinationFileName() {
        return destinationFileName;
    }

    public List<ExportCriteria> getCriteria() {
        return Collections.unmodifiableList(criteria);
    }

    public boolean hasCriteria() {
        return !criteria.isEmpty();
    }

    /**
     * Adds a new criteria to this configuration.
     */
    public void addCriteria(ExportCriteria criterion) {
        criteria.add(criterion);
    }

    /**
     * Removes a criteria from this configuration.
     */
    public void removeCriteria(ExportCriteria criterion) {
        criteria.remove(criterion);
    }

    /**
     * Evaluates if an object matches all criteria.
     * Returns true if there are no criteria, or if all criteria match.
     */
    public boolean matchesAllCriteria(Object object) {
        if (criteria.isEmpty()) {
            return true; // No criteria means accept all
        }

        // All criteria must match
        for (ExportCriteria criterion : criteria) {
            try {
                // Use reflection to get field value
                java.lang.reflect.Field field = findField(object.getClass(), criterion.getFieldName());
                if (field == null) {
                    System.out.println("DEBUG matchesAllCriteria: Field not found: " + criterion.getFieldName() + " in " + object.getClass().getName());
                    return false; // Field not found
                }

                field.setAccessible(true);
                Object fieldValue = field.get(object);
                
                System.out.println("DEBUG matchesAllCriteria: Field=" + criterion.getFieldName() + 
                                 ", Value=" + fieldValue + " (" + (fieldValue != null ? fieldValue.getClass().getSimpleName() : "null") + ")" +
                                 ", Criterion=" + criterion.getOperator().getSymbol() + " " + criterion.getValue());

                if (!criterion.matches(fieldValue)) {
                    System.out.println("DEBUG matchesAllCriteria: FAILED to match");
                    return false; // Criterion doesn't match
                }
                System.out.println("DEBUG matchesAllCriteria: MATCHED");
            } catch (Exception e) {
                System.out.println("DEBUG matchesAllCriteria: Exception: " + e.getMessage());
                e.printStackTrace();
                return false; // Error accessing field
            }
        }

        return true; // All criteria matched
    }

    /**
     * Finds a field in the class hierarchy (including private fields from parent
     * classes).
     */
    private java.lang.reflect.Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(className);
        if (hasCustomDestination()) {
            sb.append(" → ").append(destinationFileName);
        }
        if (hasCriteria()) {
            sb.append(" [");
            for (int i = 0; i < criteria.size(); i++) {
                if (i > 0)
                    sb.append(" AND ");
                sb.append(criteria.get(i));
            }
            sb.append("]");
        }
        return sb.toString();
    }
}
