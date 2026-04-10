package migration4o.migration;

/** Controls how a multi-organization database is exported. */
public enum OrganizationExportMode {

    /** All selected organizations are exported together into one output tree. */
    SINGLE_EXPORT,

    /** Each selected organization is exported into its own subfolder. */
    SEPARATE_PER_ORGANIZATION
}
