package migration4o.ui.panels.reference_schema_panels.migration_structure_panel;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import migration4o.database.DODatabaseContext;
import migration4o.migration.ExportRequest;
import migration4o.migration.MigrationExportService;
import migration4o.migration.OrganizationExportConfig;
import migration4o.migration.OrganizationExportMode;
import migration4o.migration.OrganizationInfo;
import migration4o.migration.monitoring.ExportStatistics;
import migration4o.migration.monitoring.ValidationResult;
import migration4o.models.schema.DOSchemaModule;
import migration4o.ui.common.DOExportMonitor;
import migration4o.ui.main.MainWindow;
import migration4o.ui.panels.reference_schema_panels.migration_structure_panel.MigrationStructurePanelUtil.ModuleExportInfo;
import migration4o.util.FileUtil;

/**
 * UI adapter that manages async export operations using SwingWorker. Handles progress dialogs and result displays for export operations. All business logic is delegated to MigrationExportService.
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
        // Extract modules
        List<DOSchemaModule> modules = new ArrayList<>();
        for (ModuleExportInfo info : modulesToExport) {
            modules.add(info.module);
        }

        // Get migration report monitor from main window
        DOExportMonitor monitor = getExportMonitor();
        if (monitor == null) {
            handleExportError(new IllegalStateException("Migration report panel not available. Please ensure a database is loaded."));
            return;
        }

        // Switch to Migration report tab
        showMigrationReportTab();

        // Run export in background
        SwingWorker<ExportStatistics, Void> worker = new SwingWorker<>() {
            @Override
            protected ExportStatistics doInBackground() throws Exception {
                DODatabaseContext context = dbContext;
                if (context == null)
                    throw new IllegalStateException("No database is open");

                OrganizationExportConfig orgConfig = options.getOrganizationConfig();

                if (orgConfig != null && orgConfig.getMode() == OrganizationExportMode.SEPARATE_PER_ORGANIZATION) {
                    return exportPerOrganization(context, orgConfig, options, modules, monitor);
                }

                ExportRequest request = options.toExportRequest(context, monitor);
                return exportService.exportModules(request, modules);
            }

            @Override
            protected void done() {
                try {
                    ExportStatistics result = get();
                    handleExportCompleted(result, dbContext);
                } catch (Exception e) {
                    handleExportError(e);
                }
            }
        };

        worker.execute();
    }

    // ==================== HELPER METHODS ====================

    /**
     * Runs one export per organization in SEPARATE_PER_ORGANIZATION mode.
     * Fail-fast: stops immediately on the first error.
     */
    private ExportStatistics exportPerOrganization(DODatabaseContext context, OrganizationExportConfig orgConfig, ExportOptions options, List<DOSchemaModule> modules, DOExportMonitor monitor) throws Exception {

        String baseBranch = options.getOutputBranch();
        if (baseBranch == null || baseBranch.isBlank()) {
            baseBranch = "all";
        }

        ExportStatistics combined = new ExportStatistics();

        for (OrganizationInfo org : orgConfig.getSelectedOrganizations()) {
            String folderName = FileUtil.sanitizeForPath(org.name()) + "_" + org.idSSI();
            String perOrgBranch = baseBranch + "/" + folderName;

            ExportRequest baseRequest = options.toExportRequest(context, monitor);
            ExportRequest orgRequest = baseRequest.withOrganizationScope(org, perOrgBranch, orgConfig.isIncludeGeneralData());

            try {
                ExportStatistics orgStats = exportService.exportModules(orgRequest, modules);
                combined.merge(orgStats);
            } catch (Exception e) {
                throw new RuntimeException("Export failed for organization '" + org.name() + "' (idSSI=" + org.idSSI() + "): " + e.getMessage(), e);
            }
        }

        return combined;
    }

    /**
     * Handles successful export completion.
     */
    private void handleExportCompleted(ExportStatistics result, migration4o.database.DODatabaseContext dbContext) {
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
        void onExportCompleted(ExportStatistics result, migration4o.database.DODatabaseContext dbContext);

        /**
         * Called when export fails with an error.
         * 
         * @param error the exception that occurred
         */
        void onExportError(Exception error);
    }
}
