package migration4o.ui.panels.reference_schema_panels.migration_structure_panel;

import java.awt.Component;
import java.awt.Frame;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import migration4o.engine.export.ExportHistory;
import migration4o.engine.export.monitoring.ExportResult;
import migration4o.migration.MigrationExportService;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.ui.ClassNode;
import migration4o.models.ui.MigrationModule;
import migration4o.ui.common.ExportProgressDialog;
import migration4o.ui.common.dialogs.ExportResultDialog;
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
    private ExportResultCallback resultCallback;

    public MigrationServiceCallback(Component parentComponent) {
        this.exportService = new MigrationExportService();
        this.parentComponent = parentComponent;
    }

    /**
     * Sets the callback for export results.
     */
    public void setResultCallback(ExportResultCallback callback) {
        this.resultCallback = callback;
    }

    /**
     * Validates that export prerequisites are met.
     * 
     * @return validation result with error message if invalid
     */
    public MigrationExportService.ValidationResult validateExportPrerequisites() {
        return exportService.validateExportPrerequisites();
    }

    // ==================== PUBLIC EXPORT METHODS ====================

    /**
     * Exports a single class to XML in the background.
     * 
     * @param classNode          the class node to export
     * @param maxObjectsPerClass maximum objects per class (null for unlimited)
     * @param outputPath         the output directory path
     */
    public void exportClassAsync(ClassNode classNode, Integer maxObjectsPerClass, String outputPath) {
        // Reset reached values before starting export
        resetReachedValuesInCoveragePanel();

        DOSchemaClass schemaClass = classNode.getSchemaClass();
        String simpleName = schemaClass.getSourceName();

        // Create and show progress dialog
        ExportProgressDialog progressDialog = new ExportProgressDialog(getParentFrame(),
                "Exporting Class: " + simpleName);
        progressDialog.setVisible(true);

        // Run export in background
        SwingWorker<ExportResult, Void> worker = new SwingWorker<>() {
            @Override
            protected ExportResult doInBackground() throws Exception {
                return exportService.exportClass(schemaClass, outputPath, progressDialog, maxObjectsPerClass);
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    ExportResult result = get();
                    handleExportCompleted(result);
                } catch (Exception e) {
                    handleExportError(e);
                }
            }
        };

        worker.execute();
    }

    /**
     * Exports multiple modules to XML in the background.
     * 
     * @param modulesToExport    the list of modules to export
     * @param maxObjectsPerClass maximum objects per class (null for unlimited)
     * @param outputPath         the output directory path
     */
    public void exportModulesAsync(List<ModuleExportInfo> modulesToExport, Integer maxObjectsPerClass,
            String outputPath) {
        // Reset reached values before starting export
        resetReachedValuesInCoveragePanel();

        // Extract modules list
        List<MigrationModule> modules = new ArrayList<>();
        for (ModuleExportInfo info : modulesToExport) {
            modules.add(info.module);
        }

        // Create and show progress dialog
        ExportProgressDialog progressDialog = new ExportProgressDialog(getParentFrame(),
                "Exporting " + modules.size() + " Module(s)");
        progressDialog.setVisible(true);

        // Run export in background
        SwingWorker<ExportResult, Void> worker = new SwingWorker<>() {
            @Override
            protected ExportResult doInBackground() throws Exception {
                // Use bulk export with shared tracker and object limit
                List<ExportResult> results = exportService.exportModulesWithSharedTracker(modules, outputPath,
                        progressDialog, maxObjectsPerClass);

                // Extract module names
                List<String> moduleNames = new ArrayList<>();
                for (MigrationModule module : modules) {
                    moduleNames.add(module.getName());
                }

                // Combine results for summary
                ExportResult combinedResult = exportService.combineExportResults(results, moduleNames, outputPath);

                // Save bulk export to history if any modules succeeded
                int successCount = (int) results.stream().filter(r -> r.errors.isEmpty()).count();
                if (successCount > 0) {
                    exportService.saveModuleExportHistory(
                            moduleNames,
                            new ArrayList<>(combinedResult.exportedClassCounts.keySet()),
                            outputPath,
                            maxObjectsPerClass);
                }

                return combinedResult;
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    ExportResult result = get();
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

        String dialogTitle = params.type == ExportHistory.ExportType.CLASS
                ? "Repeating Class Export"
                : "Repeating Module Export";

        // Create and show progress dialog
        ExportProgressDialog progressDialog = new ExportProgressDialog(getParentFrame(), dialogTitle);
        progressDialog.setVisible(true);

        // Run export in background
        SwingWorker<ExportResult, Void> worker = new SwingWorker<>() {
            @Override
            protected ExportResult doInBackground() throws Exception {
                return exportService.repeatLastExport(progressDialog);
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    ExportResult result = get();
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
    private void handleExportCompleted(ExportResult result) {
        // Show result dialog
        ExportResultDialog dialog = new ExportResultDialog(getParentFrame(), result);
        dialog.setVisible(true);

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
     * Callback interface for export results.
     */
    public interface ExportResultCallback {
        /**
         * Called when export completes successfully.
         * 
         * @param result the export result
         */
        void onExportCompleted(ExportResult result);

        /**
         * Called when export fails with an error.
         * 
         * @param error the exception that occurred
         */
        void onExportError(Exception error);
    }
}
