package migration4o.ui.panels.reference_schema_panels.migration_structure_panel;

import java.awt.Component;
import java.awt.Frame;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import migration4o.database.DODatabaseContext;
import migration4o.migration.ExportHistory;
import migration4o.migration.ExportOutputOption;
import migration4o.migration.MigrationExportService;
import migration4o.migration.monitoring.ExportStatistics;
import migration4o.migration.monitoring.ValidationResult;
import migration4o.models.schema.DOSchemaModule;
import migration4o.ui.common.DOExportMonitor;
import migration4o.ui.main.MainWindow;
import migration4o.ui.panels.reference_schema_panels.migration_structure_panel.MigrationStructurePanelUtil.ModuleExportInfo;

/**
 * UI adapter that manages async export operations using SwingWorker. Handles
 * progress dialogs and result displays for export operations. All business
 * logic is delegated to MigrationExportService.
 */
public class MigrationServiceCallback {

    private final MigrationExportService exportService;
    private Component parentComponent;
    private ExportStatisticsCallback resultCallback;

    public MigrationServiceCallback(Component parentComponent) {
        this.exportService = new MigrationExportService();
        this.parentComponent = parentComponent;
    }

    /**
     * Sets the callback for export results.
     */
    public void setResultCallback(ExportStatisticsCallback callback) {
        this.resultCallback = callback;
    }

    /**
     * Validates that export prerequisites are met.
     * 
     * @return validation result with error message if invalid
     */
    public ValidationResult validateExportPrerequisites() {
        return exportService.validateExportPrerequisites(MainWindow.getInstance().getCurrentContext());
    }

    // ==================== PUBLIC EXPORT METHODS ====================

    /**
     * Exports multiple modules to XML in the background.
     * 
     * @param modulesToExport the list of modules to export
     * @param options export options selected by user
     */
    public void exportModulesAsync(DODatabaseContext dbContext, List<ModuleExportInfo> modulesToExport, ExportOptions options) {
        // Reset reached values before starting export
        resetReachedValuesInCoveragePanel();

        final boolean fullTracking = options.isFullTracking();

        // Extract modules and paths
        List<DOSchemaModule> modules = new ArrayList<>();
        List<String> modulePaths = new ArrayList<>();
        for (ModuleExportInfo info : modulesToExport) {
            modules.add(info.module);
            modulePaths.add(info.fullPath);
        }

        // Get migration report monitor from main window
        DOExportMonitor monitor = getExportMonitor();
        if (monitor == null) {
            handleExportError(new IllegalStateException("Migration report panel not available. Please ensure a database is loaded."));
            return;
        }

        // Switch to Migration report tab
        showMigrationReportTab();

        // Save export options immediately before starting, so --repeat-export
        // always reflects the most recently launched configuration.
        List<String> moduleNames = new ArrayList<>();
        for (DOSchemaModule module : modules) {
            moduleNames.add(module.name);
        }
        String targetName = moduleNames.size() == 1 ? moduleNames.get(0) : moduleNames.size() + " modules";
        ExportHistory.saveExport(ExportHistory.ExportType.MODULE, targetName, options.getOutputPath(), new ArrayList<>(), moduleNames, options.getMaxObjectsPerClass(), options.isExportNativeIds(), ExportOutputOption.toPersistedOptions(options.getOutputOptions()), options.isApplyUserSelectedFieldExclusions(), options.isApplySkipWhenConditions(), options.isApplyExportCriteriaFilters(), options.isSkipObjectsWithoutExportableFields(), fullTracking);

        // Run export in background
        SwingWorker<ExportStatistics, Void> worker = new SwingWorker<>() {
            @Override
            protected ExportStatistics doInBackground() throws Exception {
                // Use exportModules which handles single or multiple modules
                // automatically
                DODatabaseContext context = dbContext;
                if (context == null)
                    throw new IllegalStateException("No database is open");

                return exportService.exportModules(context, modules, modulePaths, options.getOutputPath(), monitor, options.getMaxObjectsPerClass(), options.isExportNativeIds(), options.getSelectedSkipOptions(), options.getOutputOptions(), options.isApplyUserSelectedFieldExclusions(), options.isApplySkipWhenConditions(), options.isApplyExportCriteriaFilters(), options.isSkipObjectsWithoutExportableFields(), fullTracking);
            }

            @Override
            protected void done() {
                try {
                    ExportStatistics result = get();
                    handleExportCompleted(result, dbContext, fullTracking);
                } catch (Exception e) {
                    handleExportError(e);
                }
            }
        };

        worker.execute();
    }

    /**
     * Repeats the last export operation from history.
     */
    public void repeatLastExportAsync(DODatabaseContext dbContext) {
        // Check if history exists
        ExportHistory.ExportParams params = ExportHistory.loadLastExport();

        if (params == null) {
            handleExportError(new IllegalStateException("No export history found."));
            return;
        }

        // Get migration report monitor from main window
        DOExportMonitor monitor = getExportMonitor();
        if (monitor == null) {
            handleExportError(new IllegalStateException("Migration report panel not available. Please ensure a database is loaded."));
            return;
        }

        // Switch to Migration report tab
        showMigrationReportTab();

        final boolean fullTracking = params.fullTracking;

        // Reset reached values before starting export
        resetReachedValuesInCoveragePanel();

        // Run export in background
        SwingWorker<ExportStatistics, Void> worker = new SwingWorker<>() {
            @Override
            protected ExportStatistics doInBackground() throws Exception {
                return exportService.repeatLastExport(monitor, dbContext);
            }

            @Override
            protected void done() {
                try {
                    ExportStatistics result = get();
                    handleExportCompleted(result, dbContext, fullTracking);
                } catch (Exception e) {
                    handleExportError(e);
                }
            }
        };

        worker.execute();
    }

    // ==================== HELPER METHODS ====================

    /**
     * Resets reached values in the migration coverage panel before starting a
     * new export.
     */
    private void resetReachedValuesInCoveragePanel() {
        if (parentComponent != null) {
            java.awt.Window window = SwingUtilities.getWindowAncestor(parentComponent);
            if (window instanceof MainWindow) {
                MainWindow mainWindow = (MainWindow) window;
                mainWindow.resetCoverageReachedValues();
            }
        }
    }

    /**
     * Handles successful export completion.
     */
    private void handleExportCompleted(ExportStatistics result, migration4o.database.DODatabaseContext dbContext) {
        handleExportCompleted(result, dbContext, true);
    }

    /**
     * Handles successful export completion.
     *
     * @param fullTracking whether full tracking was enabled for this export;
     *        when {@code false} the coverage panel is disabled after completion
     */
    private void handleExportCompleted(ExportStatistics result, migration4o.database.DODatabaseContext dbContext, boolean fullTracking) {
        // Show results in the Migration results tab instead of a dialog
        if (parentComponent != null) {
            java.awt.Window window = SwingUtilities.getWindowAncestor(parentComponent);
            if (window instanceof migration4o.ui.main.MainWindow) {
                migration4o.ui.main.MainWindow mainWindow = (migration4o.ui.main.MainWindow) window;
                mainWindow.showMigrationResults(result);
                // Enable/disable coverage panel depending on tracking mode
                mainWindow.setCoveragePanelEnabled(fullTracking);
            }
        }

        // Notify external callback
        if (resultCallback != null) {
            resultCallback.onExportCompleted(result, dbContext);
        }
    }

    /**
     * Handles export error.
     */
    private void handleExportError(Exception error) {
        // Log and notify external callback
        error.printStackTrace();
        if (resultCallback != null) {
            resultCallback.onExportError(error);
        }
    }

    /**
     * Gets the parent frame for dialogs.
     */
    private Frame getParentFrame() {
        if (parentComponent != null) {
            return (Frame) SwingUtilities.getWindowAncestor(parentComponent);
        }
        return null;
    }

    /**
     * Gets the export monitor from the main window's migration report panel.
     * 
     * @return the export monitor, or null if not available
     */
    private DOExportMonitor getExportMonitor() {
        if (parentComponent != null) {
            java.awt.Window window = SwingUtilities.getWindowAncestor(parentComponent);
            if (window instanceof MainWindow) {
                MainWindow mainWindow = (MainWindow) window;
                return mainWindow.getMigrationReportMonitor();
            }
        }
        return null;
    }

    /**
     * Switches the main window to the Migration report tab in the Database
     * section.
     */
    private void showMigrationReportTab() {
        if (parentComponent != null) {
            java.awt.Window window = SwingUtilities.getWindowAncestor(parentComponent);
            if (window instanceof MainWindow) {
                MainWindow mainWindow = (MainWindow) window;
                mainWindow.showMigrationReportTab();
            }
        }
    }

    /**
     * Callback interface for export results.
     */
    public interface ExportStatisticsCallback {
        /**
         * Called when export completes successfully.
         * 
         * @param result the export result
         */
        void onExportCompleted(ExportStatistics result, migration4o.database.DODatabaseContext dbContext);

        /**
         * Called when export fails with an error.
         * 
         * @param error the exception that occurred
         */
        void onExportError(Exception error);
    }
}
