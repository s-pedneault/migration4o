package migration4o.models.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;
import com.db4o.reflect.generic.GenericObject;

import migration4o.database.DODatabaseDelegate;

import migration4o.models.ui.layout.DetailLayout;
import migration4o.util.ClassUtil;

/**
 * Configuration for exporting a specific class within a module.
 * Includes destination file name, optional filter criteria, description, and
 * pricing.
 * The same class can appear multiple times in a module with different
 * configurations.
 */
public class ClassExportConfig {

    private final String className;
    private final String destinationFileName; // null means use className as default
    private final List<ExportCriteria> criteria;
    private final String description;
    private final Map<String, Float> unitCosts; // Price list: key -> unit cost
    private DetailLayout layout; // Optional detail view layout
    private String title; // Optional display title override (from classRef title="..."), overrides schema class title
    private List<String> defaultColumns; // Optional ordered list of column field paths shown by default in HTML viewer search table

    /**
     * Creates a simple config with just the class name (backward compatibility).
     */
    public ClassExportConfig(String className) {
        this(className, null, Collections.emptyList(), null, Collections.emptyMap());
    }

    /**
     * Creates a config with custom destination file name.
     */
    public ClassExportConfig(String className, String destinationFileName) {
        this(className, destinationFileName, Collections.emptyList(), null, Collections.emptyMap());
    }

    /**
     * Creates a full config with class name, destination file, and filter criteria.
     */
    public ClassExportConfig(String className, String destinationFileName, List<ExportCriteria> criteria) {
        this(className, destinationFileName, criteria, null, Collections.emptyMap());
    }

    /**
     * Creates a complete config with all properties.
     */
    public ClassExportConfig(String className, String destinationFileName, List<ExportCriteria> criteria, String description, Map<String, Float> unitCosts) {
        this.className = className;
        this.destinationFileName = destinationFileName;
        this.criteria = new ArrayList<>(criteria);
        this.description = description;
        this.unitCosts = new HashMap<>(unitCosts != null ? unitCosts : Collections.emptyMap());
    }

    public String getClassName() {
        return className;
    }

    /**
     * Returns the destination file name, or the class name if none specified.
     */
    public String getDestinationFileName() {
        return destinationFileName != null && !destinationFileName.isEmpty() ? destinationFileName : ClassUtil.getSimpleName(className);
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

    public String getDescription() {
        return description;
    }

    public Map<String, Float> getUnitCosts() {
        return Collections.unmodifiableMap(unitCosts);
    }

    /**
     * Get unit cost for a specific price list key.
     * Returns 0.0 if the key doesn't exist.
     */
    public float getUnitCost(String priceListKey) {
        return unitCosts.getOrDefault(priceListKey, 0.0f);
    }

    public List<ExportCriteria> getCriteria() {
        return Collections.unmodifiableList(criteria);
    }

    public boolean hasCriteria() {
        return !criteria.isEmpty();
    }

    public DetailLayout getLayout() {
        return layout;
    }

    public void setLayout(DetailLayout layout) {
        this.layout = layout;
    }

    public boolean hasLayout() {
        return layout != null && !layout.isEmpty();
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = (title != null && !title.isBlank()) ? title : null;
    }

    public boolean hasTitle() {
        return title != null && !title.isBlank();
    }

    public List<String> getDefaultColumns() {
        return defaultColumns != null ? Collections.unmodifiableList(defaultColumns) : Collections.emptyList();
    }

    public void setDefaultColumns(List<String> cols) {
        this.defaultColumns = (cols != null && !cols.isEmpty()) ? new ArrayList<>(cols) : null;
    }

    public boolean hasDefaultColumns() {
        return defaultColumns != null && !defaultColumns.isEmpty();
    }

    /**
     * Returns default columns as a JSON array string for HTML embedding, e.g. ["col1","col2"].
     * Returns "null" when no default columns are configured.
     */
    public String getDefaultColumnsJson() {
        if (!hasDefaultColumns())
            return "null";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < defaultColumns.size(); i++) {
            if (i > 0)
                sb.append(',');
            sb.append('"').append(defaultColumns.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        sb.append(']');
        return sb.toString();
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
     * @param delegate      The database delegate for accessing stored class info
     * @param genericObject The GenericObject from db4o to check against criteria
     * @return true if all criteria match or no criteria exist
     */
    public boolean matchesAllCriteria(DODatabaseDelegate delegate, GenericObject genericObject) {
        if (criteria.isEmpty()) {
            // System.out.println("DEBUG no criteria applied to " + getClassName());
            return true; // No criteria means accept all
        }

        // System.out.println("DEBUG checking criteria for " + getClassName() + " with "
        // + criteria.size() + " criteria");

        // Get stored class info to access fields
        StoredClass storedClass = delegate.storedClass(genericObject);
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
                System.out.println("DEBUG matchesAllCriteria: Exception accessing field " + criterion.getFieldName() + ": " + e.getMessage());
                e.printStackTrace();
                return false; // Error accessing field
            }
        }

        return true; // All criteria matched
    }

    /**
     * Counts how many objects in the given array match all criteria.
     * 
     * @param delegate Database delegate for accessing object data
     * @param objectIds Array of object IDs to check
     * @return Count of objects that match all criteria
     */
    public int countMatchingObjects(DODatabaseDelegate delegate, long[] objectIds) {
        if (objectIds == null || objectIds.length == 0) {
            return 0;
        }

        // If no criteria, all objects match
        if (!hasCriteria()) {
            return objectIds.length;
        }

        int matchCount = 0;
        for (long objectId : objectIds) {
            try {
                Object obj = delegate.getByID(objectId);
                if (obj instanceof GenericObject) {
                    if (matchesAllCriteria(delegate, (GenericObject) obj)) {
                        matchCount++;
                    }
                }
            } catch (Exception e) {
                // Skip objects that can't be loaded
            }
        }

        return matchCount;
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
