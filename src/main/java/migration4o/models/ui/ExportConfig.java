package migration4o.models.ui;

import java.util.ArrayList;
import java.util.List;

import migration4o.migration.ExportOutputOption;
import migration4o.migration.FilesDestination;

/**
 * Persistent export configuration for a database. Saved per-database as {@code export-config.json} in the database file's parent folder.
 */
public class ExportConfig {

    public enum ExportMode {
        ALL_OBJECTS, MAX_PER_CLASS, SEED_BASED
    }

    private ExportMode exportMode = ExportMode.ALL_OBJECTS;
    private int maxObjectsPerClass = 50;
    private boolean exportNativeIds = false;
    private boolean fullTracking = true;
    private boolean applyUserSelectedFieldExclusions = true;
    private boolean applySkipWhenConditions = true;
    private boolean applyExportCriteriaFilters = true;
    private boolean skipObjectsWithoutExportableFields = true;
    private List<String> outputOptions = new ArrayList<>(List.of(ExportOutputOption.XML_XSD));
    private List<String> selectedSkipOptionNames = new ArrayList<>();
    private List<SeedQuery> seeds = new ArrayList<>();
    private int seedMaxPerClass = 50;
    private String outputBranch;
    private String exportLanguage = "fr";
    private FilesDestination filesDestination = FilesDestination.FOLDER;

    // Organization export config (persisted so --repeat-export can restore it)
    private String organizationMode; // null = not yet configured; else OrganizationExportMode.name()
    private List<Integer> selectedOrgIds = new ArrayList<>();
    private boolean includeGeneralData = true;

    public ExportConfig() {
    }

    // ── Getters and setters ──────────────────────────────────────────────────

    public ExportMode getExportMode() {
        return exportMode;
    }

    public void setExportMode(ExportMode exportMode) {
        this.exportMode = exportMode;
    }

    public int getMaxObjectsPerClass() {
        return maxObjectsPerClass;
    }

    public void setMaxObjectsPerClass(int maxObjectsPerClass) {
        this.maxObjectsPerClass = maxObjectsPerClass;
    }

    public boolean isExportNativeIds() {
        return exportNativeIds;
    }

    public void setExportNativeIds(boolean exportNativeIds) {
        this.exportNativeIds = exportNativeIds;
    }

    public boolean isFullTracking() {
        return fullTracking;
    }

    public void setFullTracking(boolean fullTracking) {
        this.fullTracking = fullTracking;
    }

    public boolean isApplyUserSelectedFieldExclusions() {
        return applyUserSelectedFieldExclusions;
    }

    public void setApplyUserSelectedFieldExclusions(boolean applyUserSelectedFieldExclusions) {
        this.applyUserSelectedFieldExclusions = applyUserSelectedFieldExclusions;
    }

    public boolean isApplySkipWhenConditions() {
        return applySkipWhenConditions;
    }

    public void setApplySkipWhenConditions(boolean applySkipWhenConditions) {
        this.applySkipWhenConditions = applySkipWhenConditions;
    }

    public boolean isApplyExportCriteriaFilters() {
        return applyExportCriteriaFilters;
    }

    public void setApplyExportCriteriaFilters(boolean applyExportCriteriaFilters) {
        this.applyExportCriteriaFilters = applyExportCriteriaFilters;
    }

    public boolean isSkipObjectsWithoutExportableFields() {
        return skipObjectsWithoutExportableFields;
    }

    public void setSkipObjectsWithoutExportableFields(boolean skipObjectsWithoutExportableFields) {
        this.skipObjectsWithoutExportableFields = skipObjectsWithoutExportableFields;
    }

    public List<String> getOutputOptions() {
        return outputOptions;
    }

    public void setOutputOptions(List<String> outputOptions) {
        this.outputOptions = outputOptions != null ? outputOptions : new ArrayList<>();
    }

    public List<String> getSelectedSkipOptionNames() {
        return selectedSkipOptionNames;
    }

    public void setSelectedSkipOptionNames(List<String> selectedSkipOptionNames) {
        this.selectedSkipOptionNames = selectedSkipOptionNames != null ? selectedSkipOptionNames : new ArrayList<>();
    }

    public List<SeedQuery> getSeeds() {
        return seeds;
    }

    public void setSeeds(List<SeedQuery> seeds) {
        this.seeds = seeds != null ? seeds : new ArrayList<>();
    }

    public int getSeedMaxPerClass() {
        return seedMaxPerClass;
    }

    public void setSeedMaxPerClass(int seedMaxPerClass) {
        this.seedMaxPerClass = seedMaxPerClass;
    }

    public String getOutputBranch() {
        return outputBranch;
    }

    public void setOutputBranch(String outputBranch) {
        this.outputBranch = outputBranch;
    }

    public String getExportLanguage() {
        return exportLanguage;
    }

    public void setExportLanguage(String exportLanguage) {
        this.exportLanguage = (exportLanguage != null && !exportLanguage.isBlank()) ? exportLanguage : "fr";
    }

    public FilesDestination getFilesDestination() {
        return filesDestination;
    }

    public void setFilesDestination(FilesDestination filesDestination) {
        this.filesDestination = filesDestination != null ? filesDestination : FilesDestination.FOLDER;
    }

    /**
     * Returns the default output branch name for the current mode and settings.
     */
    public String getDefaultOutputBranch() {
        switch (exportMode) {
        case MAX_PER_CLASS:
            return "max" + maxObjectsPerClass;
        case SEED_BASED:
            return "custom";
        default:
            return "all";
        }
    }

    public String getOrganizationMode() {
        return organizationMode;
    }

    public void setOrganizationMode(String organizationMode) {
        this.organizationMode = organizationMode;
    }

    public List<Integer> getSelectedOrgIds() {
        return selectedOrgIds;
    }

    public void setSelectedOrgIds(List<Integer> selectedOrgIds) {
        this.selectedOrgIds = selectedOrgIds != null ? selectedOrgIds : new ArrayList<>();
    }

    public boolean isIncludeGeneralData() {
        return includeGeneralData;
    }

    public void setIncludeGeneralData(boolean includeGeneralData) {
        this.includeGeneralData = includeGeneralData;
    }

    // ── Derived helpers ──────────────────────────────────────────────────────

    /**
     * Returns the effective maxObjectsPerClass for the export pipeline: null for ALL_OBJECTS mode, the configured value for MAX_PER_CLASS, null for SEED_BASED (seed mode uses the advisor differently).
     */
    public Integer getEffectiveMaxObjectsPerClass() {
        if (exportMode == ExportMode.MAX_PER_CLASS) {
            return maxObjectsPerClass;
        }
        if (exportMode == ExportMode.SEED_BASED) {
            return seedMaxPerClass;
        }
        return null;
    }
}
