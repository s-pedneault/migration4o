package migration4o.ui.panels.reference_schema_panels.migration_structure_panel;

import java.util.List;

import migration4o.models.schema.DOSchemaField;

/**
 * Export options selected by the user.
 */
public class ExportOptions {

    private final Integer maxObjectsPerClass;
    private final boolean exportNativeIds;
    private final List<DOSchemaField> selectedSkipOptions;
    private final String outputPath;
    private final String outputFormat;
    private final boolean applyUserSelectedFieldExclusions;
    private final boolean applySkipWhenConditions;
    private final boolean applyExportCriteriaFilters;
    private final boolean skipObjectsWithoutExportableFields;

    public ExportOptions(Integer maxObjectsPerClass, boolean exportNativeIds, List<DOSchemaField> selectedSkipOptions, String outputPath, String outputFormat, boolean applyUserSelectedFieldExclusions, boolean applySkipWhenConditions, boolean applyExportCriteriaFilters, boolean skipObjectsWithoutExportableFields) {
        this.maxObjectsPerClass = maxObjectsPerClass;
        this.exportNativeIds = exportNativeIds;
        this.selectedSkipOptions = selectedSkipOptions;
        this.outputPath = outputPath;
        this.outputFormat = outputFormat;
        this.applyUserSelectedFieldExclusions = applyUserSelectedFieldExclusions;
        this.applySkipWhenConditions = applySkipWhenConditions;
        this.applyExportCriteriaFilters = applyExportCriteriaFilters;
        this.skipObjectsWithoutExportableFields = skipObjectsWithoutExportableFields;
    }

    public Integer getMaxObjectsPerClass() {
        return maxObjectsPerClass;
    }

    public boolean isExportNativeIds() {
        return exportNativeIds;
    }

    public List<DOSchemaField> getSelectedSkipOptions() {
        return selectedSkipOptions;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public boolean isApplyUserSelectedFieldExclusions() {
        return applyUserSelectedFieldExclusions;
    }

    public boolean isApplySkipWhenConditions() {
        return applySkipWhenConditions;
    }

    public boolean isApplyExportCriteriaFilters() {
        return applyExportCriteriaFilters;
    }

    public boolean isSkipObjectsWithoutExportableFields() {
        return skipObjectsWithoutExportableFields;
    }
}