package migration4o.migration;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Records the user's choices from the {@code OrganizationExportDialog}: which mode to use,
 * which organizations to include, and whether to include general (non-org-specific) data.
 */
public class OrganizationExportConfig {

    private final OrganizationExportMode mode;
    private final List<OrganizationInfo> selectedOrganizations;
    private final boolean includeGeneralData;

    public OrganizationExportConfig(OrganizationExportMode mode, List<OrganizationInfo> selectedOrganizations, boolean includeGeneralData) {
        if (mode == null)
            throw new IllegalArgumentException("mode must not be null");
        if (selectedOrganizations == null || selectedOrganizations.isEmpty()) {
            throw new IllegalArgumentException("At least one organization must be selected");
        }
        this.mode = mode;
        this.selectedOrganizations = Collections.unmodifiableList(selectedOrganizations);
        this.includeGeneralData = includeGeneralData;
    }

    /**
     * Creates a single-organization, single-export config for the no-dialog case
     * (database contains only one organization).
     */
    public static OrganizationExportConfig singleOrganization(OrganizationInfo org) {
        return new OrganizationExportConfig(OrganizationExportMode.SINGLE_EXPORT, List.of(org), true);
    }

    public OrganizationExportMode getMode() {
        return mode;
    }

    public List<OrganizationInfo> getSelectedOrganizations() {
        return selectedOrganizations;
    }

    public boolean isIncludeGeneralData() {
        return includeGeneralData;
    }

    /** Returns the set of selected {@code mIDSSI} values for fast membership tests. */
    public Set<Integer> getSelectedIdSSIs() {
        return selectedOrganizations.stream().map(OrganizationInfo::idSSI).collect(Collectors.toSet());
    }
}
