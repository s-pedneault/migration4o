package migration4o.migration;

import migration4o.database.DODatabaseDelegate;
import migration4o.models.schema.DOSchemaClass;

/**
 * Filters database objects during export based on their {@code mIDSSI} value
 * and the user's organization selection.
 * <p>
 * Uses {@link DOSchemaClass#isMultiOrganization()} (which walks the ancestor
 * chain) to determine whether a class carries an organization identifier.
 * Only root objects are filtered; embedded objects are always exported as
 * part of their owning root.
 */
public class OrganizationFilter {

    private final OrganizationExportConfig config;

    public OrganizationFilter(OrganizationExportConfig config) {
        this.config = config;
    }

    /**
     * Returns {@code true} if the object should be included in the export.
     *
     * @param schemaClass the reference schema class for the object (may be null)
     * @param delegate    the active database delegate
     * @param obj         the database object instance
     * @param isRootObject {@code true} for top-level objects; embedded objects always pass
     */
    public boolean shouldExport(DOSchemaClass schemaClass, DODatabaseDelegate delegate, Object obj, boolean isRootObject) {
        if (!isRootObject || config == null || schemaClass == null) {
            return true;
        }

        if (schemaClass.isMultiOrganization()) {
            Object idValue = delegate.getStoredFieldValue(obj, "mIDSSI");
            if (!(idValue instanceof Number)) {
                return true;
            }
            int idSSI = ((Number) idValue).intValue();
            if (idSSI <= 0) {
                // No valid org assignment — treat as general data, always include
                return true;
            }
            return config.getSelectedIdSSIs().contains(idSSI);
        } else {
            // Class has no mIDSSI in its hierarchy — it is general data
            return config.isIncludeGeneralData();
        }
    }
}
