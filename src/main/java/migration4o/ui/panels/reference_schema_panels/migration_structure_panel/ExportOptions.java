package migration4o.ui.panels.reference_schema_panels.migration_structure_panel;

import java.util.ArrayList;
import java.util.List;

import migration4o.migration.ExportOutputOption;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.ui.ExportConfig;
import migration4o.models.ui.SeedQuery;
import migration4o.schema.DOSchemaService;
import migration4o.util.SchemaUtil;

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
    /** When {@code false}, expensive per-object diagnostic tracking and
     * reachability ID collection are skipped, speeding up the export at the
     * cost of disabling the coverage analysis panel. */
    private final boolean fullTracking;
    private final List<SeedQuery> seedQueries;
    private final String outputBranch;

    public ExportOptions(Integer maxObjectsPerClass, boolean exportNativeIds, List<DOSchemaField> selectedSkipOptions, String outputPath, List<String> outputOptions, boolean applyUserSelectedFieldExclusions, boolean applySkipWhenConditions, boolean applyExportCriteriaFilters, boolean skipObjectsWithoutExportableFields) {
        this(maxObjectsPerClass, exportNativeIds, selectedSkipOptions, outputPath, outputOptions, applyUserSelectedFieldExclusions, applySkipWhenConditions, applyExportCriteriaFilters, skipObjectsWithoutExportableFields, true, null, null);
    }

    public ExportOptions(Integer maxObjectsPerClass, boolean exportNativeIds, List<DOSchemaField> selectedSkipOptions, String outputPath, List<String> outputOptions, boolean applyUserSelectedFieldExclusions, boolean applySkipWhenConditions, boolean applyExportCriteriaFilters, boolean skipObjectsWithoutExportableFields, boolean fullTracking) {
        this(maxObjectsPerClass, exportNativeIds, selectedSkipOptions, outputPath, outputOptions, applyUserSelectedFieldExclusions, applySkipWhenConditions, applyExportCriteriaFilters, skipObjectsWithoutExportableFields, fullTracking, null, null);
    }

    public ExportOptions(Integer maxObjectsPerClass, boolean exportNativeIds, List<DOSchemaField> selectedSkipOptions, String outputPath, List<String> outputOptions, boolean applyUserSelectedFieldExclusions, boolean applySkipWhenConditions, boolean applyExportCriteriaFilters, boolean skipObjectsWithoutExportableFields, boolean fullTracking, List<SeedQuery> seedQueries) {
        this(maxObjectsPerClass, exportNativeIds, selectedSkipOptions, outputPath, outputOptions, applyUserSelectedFieldExclusions, applySkipWhenConditions, applyExportCriteriaFilters, skipObjectsWithoutExportableFields, fullTracking, seedQueries, null);
    }

    public ExportOptions(Integer maxObjectsPerClass, boolean exportNativeIds, List<DOSchemaField> selectedSkipOptions, String outputPath, List<String> outputOptions, boolean applyUserSelectedFieldExclusions, boolean applySkipWhenConditions, boolean applyExportCriteriaFilters, boolean skipObjectsWithoutExportableFields, boolean fullTracking, List<SeedQuery> seedQueries, String outputBranch) {
        this.maxObjectsPerClass = maxObjectsPerClass;
        this.exportNativeIds = exportNativeIds;
        this.selectedSkipOptions = selectedSkipOptions;
        this.outputPath = outputPath;
        this.outputOptions = ExportOutputOption.normalize(outputOptions);
        this.applyUserSelectedFieldExclusions = applyUserSelectedFieldExclusions;
        this.applySkipWhenConditions = applySkipWhenConditions;
        this.applyExportCriteriaFilters = applyExportCriteriaFilters;
        this.skipObjectsWithoutExportableFields = skipObjectsWithoutExportableFields;
        this.fullTracking = fullTracking;
        this.seedQueries = seedQueries != null ? seedQueries : new ArrayList<>();
        this.outputBranch = outputBranch;
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

    public boolean isFullTracking() {
        return fullTracking;
    }

    public List<SeedQuery> getSeedQueries() {
        return seedQueries;
    }

    public String getOutputBranch() {
        return outputBranch;
    }

    /**
     * Builds an {@code ExportOptions} from a persisted {@link ExportConfig}.
     * This is the single place that translates stored configuration into
     * runtime export parameters, used by both the UI Export button and
     * {@code --repeat-export}.
     */
    public static ExportOptions fromConfig(ExportConfig config) {
        Integer maxPerClass = config.getEffectiveMaxObjectsPerClass();

        // Resolve skip option names → DOSchemaField objects
        List<DOSchemaField> selectedSkipFields = new ArrayList<>();
        List<DOSchemaField> available = SchemaUtil.collectSkipUserOptions(DOSchemaService.getInstance().getReferenceSchema());
        if (available != null) {
            List<String> savedNames = config.getSelectedSkipOptionNames();
            for (DOSchemaField field : available) {
                if (savedNames.contains(field.skipUserOption)) {
                    selectedSkipFields.add(field);
                }
            }
        }

        // Collect seed queries for seed-based mode
        List<SeedQuery> seeds = null;
        if (config.getExportMode() == ExportConfig.ExportMode.SEED_BASED) {
            seeds = config.getSeeds();
        }

        return new ExportOptions(maxPerClass, config.isExportNativeIds(), selectedSkipFields, "output", config.getOutputOptions(), config.isApplyUserSelectedFieldExclusions(), config.isApplySkipWhenConditions(), config.isApplyExportCriteriaFilters(), config.isSkipObjectsWithoutExportableFields(), config.isFullTracking(), seeds, config.getOutputBranch());
    }
}