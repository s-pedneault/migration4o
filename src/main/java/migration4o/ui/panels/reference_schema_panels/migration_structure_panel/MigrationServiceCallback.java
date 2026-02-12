package migration4o.ui.panels.reference_schema_panels.migration_structure_panel;

import java.awt.Component;
import java.awt.Frame;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import migration4o.migration.ExportHistory;
import migration4o.migration.MigrationExportService;
import migration4o.migration.monitoring.ExportStatistics;
import migration4o.migration.monitoring.ValidationResult;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.ui.ClassNode;
import migration4o.models.ui.MigrationModule;
import migration4o.ui.common.DOExportMonitor;
import migration4o.ui.main.MainWindow;
import migration4o.ui.panels.reference_schema_panels.migration_structure_panel.MigrationStructurePanelUtil.ModuleExportInfo;

/**
 * UI adapter that manages async export operations using SwingWorker.
 * Handles progress dialogs and result displays for export operations.
 * All business logic is delegated to MigrationExportService.
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
        return exportService.validateExportPrerequisites();
    }

    // ==================== PUBLIC EXPORT METHODS ====================

    /**
     * Exports multiple modules to XML in the background.
     * 
     * @param modulesToExport     the list of modules to export
     * @param maxObjectsPerClass  maximum objects per class (null for unlimited)
     * @param exportNativeIds     whether to export DB4O object IDs as XML
     *                            attributes
     * @param selectedSkipOptions list of fields that user has chosen to skip
     * @param outputPath          the output directory path
     */
    public void exportModulesAsync(List<ModuleExportInfo> modulesToExport, Integer maxObjectsPerClass,
            boolean exportNativeIds, java.util.List<migration4o.models.schema.DOSchemaField> selectedSkipOptions,
            String outputPath) {
        // Reset reached values before starting export
        resetReachedValuesInCoveragePanel();

        // Extract modules and paths
        List<MigrationModule> modules = new ArrayList<>();
        List<String> modulePaths = new ArrayList<>();
        for (ModuleExportInfo info : modulesToExport) {
            modules.add(info.module);
            modulePaths.add(info.fullPath);
        }

        // Get migration report monitor from main window
        DOExportMonitor monitor = getExportMonitor();
        if (monitor == null) {
            handleExportError(new IllegalStateException(
                    "Migration report panel not available. Please ensure a database is loaded."));
            return;
        }

        // Switch to Migration report tab
        showMigrationReportTab();

        // Run export in background
        SwingWorker<ExportStatistics, Void> worker = new SwingWorker<>() {
            @Override
            protected ExportStatistics doInBackground() throws Exception {
                // Use exportModules which handles single or multiple modules automatically
                ExportStatistics result = exportService.exportModules(modules, modulePaths, outputPath,
                        monitor, maxObjectsPerClass, exportNativeIds, selectedSkipOptions);

                // Extract module names
                List<String> moduleNames = new ArrayList<>();
                for (MigrationModule module : modules) {
                    moduleNames.add(module.getName());
                }

                // Save to history if successful
                if (result.errors.isEmpty()) {
                    String targetName = moduleNames.size() == 1 ? moduleNames.get(0) : moduleNames.size() + " modules";
                    ExportHistory.saveExport(ExportHistory.ExportType.MODULE, targetName, outputPath,
                            new ArrayList<>(result.exportedClassCounts.keySet()), moduleNames, maxObjectsPerClass,
                            exportNativeIds);
                }

                return result;
            }

            @Override
            protected void done() {
                try {
                    ExportStatistics result = get();
                    handleExportCompleted(result);
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
    public void repeatLastExportAsync() {
        // Check if history exists
        ExportHistory.ExportParams params = ExportHistory.loadLastExport();

        if (params == null) {
            handleExportError(new IllegalStateException("No export history found."));
            return;
        }

        // Get migration report monitor from main window
        DOExportMonitor monitor = getExportMonitor();
        if (monitor == null) {
            handleExportError(new IllegalStateException(
                    "Migration report panel not available. Please ensure a database is loaded."));
            return;
        }

        // Switch to Migration report tab
        showMigrationReportTab();

        // Run export in background
        SwingWorker<ExportStatistics, Void> worker = new SwingWorker<>() {
            @Override
            protected ExportStatistics doInBackground() throws Exception {
                return exportService.repeatLastExport(monitor);
            }

            @Override
            protected void done() {
                try {
                    ExportStatistics result = get();
                    handleExportCompleted(result);
                } catch (Exception e) {
                    handleExportError(e);
                }
            }
        };

        worker.execute();
    }

    // ==================== HELPER METHODS ====================

    /**
     * Resets reached values in the migration coverage panel before starting a new
     * export.
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
    private void handleExportCompleted(ExportStatistics result) {
        // Show results in the Migration results tab instead of a dialog
        if (parentComponent != null) {
            java.awt.Window window = SwingUtilities.getWindowAncestor(parentComponent);
            if (window instanceof migration4o.ui.main.MainWindow) {
                migration4o.ui.main.MainWindow mainWindow = (migration4o.ui.main.MainWindow) window;
                mainWindow.showMigrationResults(result);
            }
        }

        // Notify external callback
        if (resultCallback != null) {
            resultCallback.onExportCompleted(result);
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
     * Switches the main window to the Migration report tab in the Database section.
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
        void onExportCompleted(ExportStatistics result);

        /**
         * Called when export fails with an error.
         * 
         * @param error the exception that occurred
         */
        void onExportError(Exception error);
    }
}
