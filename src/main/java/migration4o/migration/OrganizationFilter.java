package migration4o.migration;

import migration4o.database.DODatabaseDelegate;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaConstants;

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
    /**
     * When {@code true}, all multi-organization objects with a valid (≥ 0) IDSSI are
     * unconditionally excluded. Used for the combined Extra.xml pass in
     * SEPARATE_PER_ORGANIZATION mode: org-specific objects belong in their org's export,
     * never in Extra.xml regardless of whether they were reached.
     */
    private boolean rejectAllOrgSpecific = false;

    public OrganizationFilter(OrganizationExportConfig config) {
        this.config = config;
    }

    /**
     * Returns a filter that accepts only general data (IDSSI &lt; 0 or non-org classes).
     * Any multi-org object with a valid IDSSI is unconditionally rejected.
     * Used for the Extra.xml pass when exporting separate-per-organization.
     */
    public static OrganizationFilter forExtraXml(OrganizationExportConfig config) {
        OrganizationFilter f = new OrganizationFilter(config);
        f.rejectAllOrgSpecific = true;
        return f;
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
            Object idValue = delegate.getStoredFieldValue(obj, DOSchemaConstants.ORGANIZATION_BUSINESS_ID_FIELD_NAME);
            if (!(idValue instanceof Number)) {
                return true;
            }
            int idSSI = ((Number) idValue).intValue();
            if (idSSI < 0) {
                // No valid org assignment — this is general data
                return config.isIncludeGeneralData();
            }
            if (rejectAllOrgSpecific) {
                // Extra.xml mode: org-specific objects belong in their org's export only
                return false;
            }
            return config.getSelectedIdSSIs().contains(idSSI);
        } else {
            // Class has no mIDSSI in its hierarchy — it is general data
            return config.isIncludeGeneralData();
        }
    }
}
