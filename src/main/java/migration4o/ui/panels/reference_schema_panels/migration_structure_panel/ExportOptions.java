package migration4o.ui.panels.reference_schema_panels.migration_structure_panel;

import java.util.List;

import migration4o.migration.ExportOutputOption;
import migration4o.models.schema.DOSchemaField;

/**
 * Export options selected by the user.
 */
public class ExportOptions {

    private final Integer maxObjectsPerClass;
    private final boolean exportNativeIds;
    private final List<DOSchemaField> selectedSkipOptions;
    private final String outputPath;
    private final List<String> outputOptions;
    private final boolean applyUserSelectedFieldExclusions;
    private final boolean applySkipWhenConditions;
    private final boolean applyExportCriteriaFilters;
    private final boolean skipObjectsWithoutExportableFields;

    public ExportOptions(Integer maxObjectsPerClass, boolean exportNativeIds, List<DOSchemaField> selectedSkipOptions, String outputPath, List<String> outputOptions, boolean applyUserSelectedFieldExclusions, boolean applySkipWhenConditions, boolean applyExportCriteriaFilters, boolean skipObjectsWithoutExportableFields) {
        this.maxObjectsPerClass = maxObjectsPerClass;
        this.exportNativeIds = exportNativeIds;
        this.selectedSkipOptions = selectedSkipOptions;
        this.outputPath = outputPath;
        this.outputOptions = ExportOutputOption.normalize(outputOptions);
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

    public List<String> getOutputOptions() {
        return outputOptions;
    }

    /**
     * Backward-compatible getter for components still expecting a single writer
     * format name.
     */
    public String getOutputFormat() {
        return ExportOutputOption.toWriterFormat(outputOptions.get(0));
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