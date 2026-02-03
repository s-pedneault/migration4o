package migration4o.models.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;
import com.db4o.reflect.generic.GenericObject;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.schema.DOSchemaService;
import migration4o.util.ClassUtil;
import migration4o.util.ObjectResolverUtil;

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
                : ClassUtil.getSimpleName(className);
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
     * 
     * @param container     The database container for accessing stored class info
     * @param genericObject The GenericObject from db4o to check against criteria
     * @return true if all criteria match or no criteria exist
     */
    public boolean matchesAllCriteria(ExtObjectContainer container, GenericObject genericObject) {
        if (criteria.isEmpty()) {
            // System.out.println("DEBUG no criteria applied to " + getClassName());
            return true; // No criteria means accept all
        }

        // System.out.println("DEBUG checking criteria for " + getClassName() + " with "
        // + criteria.size() + " criteria");

        // Get stored class info to access fields
        StoredClass storedClass = container.ext().storedClass(genericObject);
        if (storedClass == null) {
            // System.out.println("DEBUG matchesAllCriteria: No StoredClass found");
            return false;
        }

        // All criteria must match
        for (ExportCriteria criterion : criteria) {
            try {
                // Find the field by name
                StoredField storedField = null;
                for (StoredField field : storedClass.getStoredFields()) {
                    if (field.getName().equals(criterion.getFieldName())) {
                        storedField = field;
                        break;
                    }
                }

                if (storedField == null) {
                    // System.out.println("DEBUG matchesAllCriteria: Field not found: " +
                    // criterion.getFieldName());
                    return false; // Field not found
                }

                // Get field value using StoredField.get()
                Object fieldValue = storedField.get(genericObject);

                // System.out.println("DEBUG matchesAllCriteria: Field=" +
                // criterion.getFieldName() +
                // ", Value=" + fieldValue + " ("
                // + (fieldValue != null ? fieldValue.getClass().getSimpleName() : "null") + ")"
                // +
                // ", Criterion=" + criterion.getOperator().getSymbol() + " " +
                // criterion.getValue());

                if (!criterion.matches(fieldValue)) {
                    // System.out.println("DEBUG matchesAllCriteria: FAILED to match");
                    return false; // Criterion doesn't match
                }
                // System.out.println("DEBUG matchesAllCriteria: MATCHED");
            } catch (Exception e) {
                System.out.println("DEBUG matchesAllCriteria: Exception accessing field " + criterion.getFieldName()
                        + ": " + e.getMessage());
                e.printStackTrace();
                return false; // Error accessing field
            }
        }

        return true; // All criteria matched
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
