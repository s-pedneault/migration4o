package migration4o.ui.main;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

import migration4o.database.DODatabaseContext;
import migration4o.database.DODatabaseService;
import migration4o.migration.monitoring.ExportStatistics;
import migration4o.schema.DOSchemaService;
import migration4o.schema.diagram.SchemaDiagramExporter;
import migration4o.schema.modules.DOModuleService;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.ui.MigrationModule;
import migration4o.models.ui.ComparisonTabInfo;
import migration4o.models.ui.SchemaTabInfo;
import migration4o.ui.common.DatabaseProgressMonitor;
import migration4o.ui.panels.database_panels.conformity_analysis_panel.SchemaComparison;
import migration4o.ui.panels.database_panels.conformity_analysis_panel.SchemaComparisonPanel;
import migration4o.ui.panels.database_panels.cost_panel.CostPanel;
import migration4o.ui.panels.database_panels.migration_coverage_panel.MigrationCoveragePanel;
import migration4o.ui.panels.database_panels.migration_coverage_panel.dialogs.IDTracerDataService;
import migration4o.ui.panels.database_panels.multi_database_comparison_panel.MultiDatabaseComparisonPanel;
import migration4o.ui.panels.database_panels.reachability_analysis_panel.ReachabilityAnalysisPanel;
import migration4o.ui.panels.reference_schema_panels.migration_structure_panel.MigrationStructurePanel;
import migration4o.ui.panels.reference_schema_panels.reference_schema_panel.SchemaEditorPanel;
import migration4o.ui.panels.reference_schema_panels.schema_structure_panel.SchemaStructurePanel;
import migration4o.ui.panels.welcome_panel.WelcomePanel;

/**
 * Main application window with tabbed interface for migration tools.
 * Responsible for initializing and coordinating all application tabs.
 */
public class MainWindow extends JFrame {

    private static MainWindow instance;

    private JTabbedPane tabbedPane;
    private JTabbedPane schemaTabPane; // Nested tabs for Schema section
    private JTabbedPane databaseTabPane; // Active nested tabs for selected Database section
    private Component databaseTabContainer; // Active selected Database top-level tab
    private WelcomePanel welcomePanel;
    private Map<Component, SchemaTabInfo> schemaTabs = new HashMap<>();
    private Map<Component, ComparisonTabInfo> comparisonTabs = new HashMap<>();

    private Runnable repeatExportCallback;
    private boolean pendingRepeatExport = false;

    // Track static tabs (always present)
    private SchemaEditorPanel referenceSchemaPanel = null;
    private SchemaStructurePanel schemaStructurePanel = null;
    private MigrationStructurePanel migrationStructurePanel = null;

    // Track database-related tabs (dynamically created)
    private Component databaseSchemaTab = null;
    private Component conformityAnalysisTab = null;
    private Component reachabilityAnalysisTab = null;
    private Component migrationCoverageTab = null;
    private Component costTab = null;
    private migration4o.ui.panels.database_panels.migration_report_panel.MigrationReportPanel migrationReportPanel = null;
    private migration4o.ui.panels.database_panels.migration_results_panel.MigrationResultsPanel migrationResultsPanel = null;
    private DOSchema currentDatabaseSchema = null;
    private DODatabaseContext currentContext = null;
    private final Map<String, DatabaseSession> databaseSessions = new LinkedHashMap<>();
    private final Map<Component, String> databaseTabPathByContainer = new HashMap<>();
    private final Map<String, ExportStatistics> latestExportByDatabasePath = new HashMap<>();
    private final List<ExportCompletionListener> exportCompletionListeners = new ArrayList<>();

    private static class DatabaseSession {
        String databasePath;
        String tabTitle;
        DODatabaseContext context;
        DOSchema databaseSchema;
        JTabbedPane tabPane;
        Component tabContainer;
        Component databaseSchemaTab;
        Component conformityAnalysisTab;
        Component reachabilityAnalysisTab;
        Component migrationCoverageTab;
        Component costTab;
        migration4o.ui.panels.database_panels.migration_report_panel.MigrationReportPanel migrationReportPanel;
        migration4o.ui.panels.database_panels.migration_results_panel.MigrationResultsPanel migrationResultsPanel;
    }

    public interface ExportCompletionListener {
        void onExportCompleted(String databasePath, ExportStatistics result);
    }

    // Services manage the actual database and schema
    private final DODatabaseService databaseService = DODatabaseService.getInstance();
    private final DOSchemaService schemaService = DOSchemaService.getInstance();

    public MainWindow() {
        instance = this;
        initializeUI();
    }

    public static MainWindow getInstance() {
        return instance;
    }

    /**
     * Navigate to the coverage tab and filter by the specified class names.
     * 
     * @param classNames the set of class names to filter by
     */
    public void navigateToCoverageWithFilter(Set<String> classNames) {
        if (classNames == null || classNames.isEmpty()) {
            return;
        }

        // Switch to Database tab
        if (databaseTabPane != null && databaseTabContainer != null) {
            tabbedPane.setSelectedComponent(databaseTabContainer);

            // Switch to Migration Coverage sub-tab
            if (migrationCoverageTab != null) {
                databaseTabPane.setSelectedComponent(migrationCoverageTab);

                // Apply filter
                if (migrationCoverageTab instanceof migration4o.ui.panels.database_panels.migration_coverage_panel.MigrationCoveragePanel) {
                    migration4o.ui.panels.database_panels.migration_coverage_panel.MigrationCoveragePanel coveragePanel = (migration4o.ui.panels.database_panels.migration_coverage_panel.MigrationCoveragePanel) migrationCoverageTab;
                    coveragePanel.filterByClassNames(classNames);
                }
            }
        }
    }

    /**
     * Navigate to a class in the reference schema tab. Switches to the Schema tab
     * and selects the specified class.
     * 
     * @param className the fully qualified class name to navigate to
     */
    public void navigateToReferenceSchemaClass(String className) {
        if (className == null || referenceSchemaPanel == null) {
            return;
        }

        // Switch to the Schema tab
        if (schemaTabPane != null) {
            Component schemaTabComponent = null;
            for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                if (tabbedPane.getComponentAt(i) == schemaTabPane) {
                    tabbedPane.setSelectedIndex(i);
                    break;
                }
            }

            // Switch to the Reference schema sub-tab
            for (int i = 0; i < schemaTabPane.getTabCount(); i++) {
                if (schemaTabPane.getComponentAt(i) == referenceSchemaPanel) {
                    schemaTabPane.setSelectedIndex(i);
                    break;
                }
            }
        }

        // Use reflection to call the private selectClassByName method
        try {
            java.lang.reflect.Method method = SchemaEditorPanel.class.getDeclaredMethod("selectClassByName", String.class);
            method.setAccessible(true);
            method.invoke(referenceSchemaPanel, className);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Detach the Schema tab and navigate to a class in the reference schema.
     * 
     * @param className the fully qualified class name to navigate to
     */
    public void detachAndNavigateToReferenceSchemaClass(String className) {
        if (className == null || referenceSchemaPanel == null) {
            return;
        }

        Component previouslySelectedTab = tabbedPane != null ? tabbedPane.getSelectedComponent() : null;

        // First navigate to the class
        navigateToReferenceSchemaClass(className);

        // Then detach the Schema tab if it's not already detached
        if (schemaTabPane != null && tabbedPane != null) {
            for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                if (tabbedPane.getComponentAt(i) == schemaTabPane) {
                    // Found the Schema tab, detach it
                    tearOffTab(i, previouslySelectedTab, false);
                    break;
                }
            }
        }

        if (previouslySelectedTab != null && tabbedPane != null && tabbedPane.indexOfComponent(previouslySelectedTab) >= 0) {
            tabbedPane.setSelectedComponent(previouslySelectedTab);
        }
    }

    private void initializeUI() {
        setTitle("Migration4o - Database Migration Tool");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1600, 900);
        setLocationRelativeTo(null);

        // Create tabbed pane
        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Arial", Font.PLAIN, 14));

        // Add mouse listener for tab context menu (tear-off)
        tabbedPane.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showTabContextMenu(e);
                }
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showTabContextMenu(e);
                }
            }
        });

        tabbedPane.addChangeListener(e -> syncActiveDatabaseSessionFromSelection());

        // Add tabs (for now just placeholder, will add schema editor next)
        addTabs();

        // Add global top toolbar

        // Add to frame
        add(tabbedPane, BorderLayout.CENTER);
    }

    private void generateReferenceSchemaDiagram() {
        DOSchema referenceSchema = referenceSchemaPanel != null ? referenceSchemaPanel.getSchema() : schemaService.getReferenceSchema();

        if (referenceSchema == null || referenceSchema.getClasses() == null || referenceSchema.getClasses().length == 0) {
            JOptionPane.showMessageDialog(this, "Reference schema is not loaded or empty.", "Schema Diagram", JOptionPane.WARNING_MESSAGE);
            return;
        }

        SwingWorker<List<SchemaDiagramExporter.Result>, Void> moduleWorker = new SwingWorker<>() {
            @Override
            protected List<SchemaDiagramExporter.Result> doInBackground() throws Exception {
                SchemaDiagramExporter exporter = new SchemaDiagramExporter();
                Path outputDir = Paths.get("output", "diagrams");

                DOModuleService moduleService = DOModuleService.getInstance();
                List<MigrationModule> modules = moduleService.getModules();
                if (modules.isEmpty()) {
                    modules = moduleService.loadModuleStructure();
                }

                return exporter.exportPerModule(referenceSchema, modules, outputDir);
            }

            @Override
            protected void done() {
                try {
                    List<SchemaDiagramExporter.Result> results = get();

                    if (results == null || results.isEmpty()) {
                        JOptionPane.showMessageDialog(MainWindow.this, "No module diagrams were generated.\nCheck migration-format.xml module/class definitions.", "Schema Diagrams", JOptionPane.WARNING_MESSAGE);
                        return;
                    }

                    StringBuilder message = new StringBuilder();
                    long svgCount = results.stream().filter(r -> r.svgGenerated).count();
                    message.append("Generated module diagrams: ").append(results.size()).append("\n");
                    message.append("SVG rendered: ").append(svgCount).append("\n\n");
                    message.append("Output folder: ").append(Paths.get("output", "diagrams").toAbsolutePath()).append("\n");

                    JOptionPane.showMessageDialog(MainWindow.this, message.toString(), "Schema Diagrams", JOptionPane.INFORMATION_MESSAGE);

                } catch (Exception e) {
                    JOptionPane.showMessageDialog(MainWindow.this, "Failed to generate module diagrams:\n" + e.getMessage(), "Schema Diagrams Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        moduleWorker.execute();
    }

    public void triggerMigrateAllModules(migration4o.database.DODatabaseContext dbContext) {
        if (migrationStructurePanel == null) {
            JOptionPane.showMessageDialog(this, "Migration structure is not initialized yet.", "Migration Unavailable", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (dbContext != null) {
            selectDatabaseByPath(dbContext.databaseFilePath);
        }

        migrationStructurePanel.triggerExportAllModules(dbContext);
    }

    private void addTabs() {
        // Create and add dashboard panel as first tab
        welcomePanel = new WelcomePanel();
        welcomePanel.setOnOpenDatabase(() -> openDatabaseFile());
        welcomePanel.setOnCloseDatabase(path -> closeDatabase(path));
        welcomePanel.setOnCompareSelected(paths -> compareSelectedDatabases(paths));
        tabbedPane.addTab("Dashboard", welcomePanel);
    }

    /**
     * Initializes all static application tabs. This includes reference schema,
     * schema structure, and migration structure. Called after MainWindow
     * construction and before showing the window.
     */
    public void initialize() {
        try {
            // Create Schema nested tab pane
            schemaTabPane = new JTabbedPane();
            schemaTabPane.setFont(new Font("Arial", Font.PLAIN, 12));
            tabbedPane.addTab("Schema", schemaTabPane);

            // Load and add reference schema tab to Schema section
            referenceSchemaPanel = new SchemaEditorPanel();
            referenceSchemaPanel.setOnCompareRequested(() -> openDatabaseFile());
            DOSchema schema = referenceSchemaPanel.getSchema();

            addSchemaTabToSchemaSection("Reference schema", referenceSchemaPanel, schema, true);

            // Add schema structure tab to Schema section
            schemaStructurePanel = new SchemaStructurePanel(schema);
            schemaTabPane.addTab("Schema structure", schemaStructurePanel);

            // Add migration structure tab to Schema section
            migrationStructurePanel = new MigrationStructurePanel(schema);
            schemaTabPane.addTab("Migration structure", migrationStructurePanel);

            // Set up repeat export callback
            setRepeatExportCallback(() -> migrationStructurePanel.repeatLastExport());

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading default schema: " + e.getMessage(), "Schema Load Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Automatically opens a database file (used for command-line auto-open). Shows
     * appropriate error messages if the file doesn't exist.
     * 
     * @param databasePath the absolute path to the database file
     */
    public void autoOpenDatabase(String databasePath) {
        File dbFile = new File(databasePath);
        if (dbFile.exists() && dbFile.isFile()) {
            System.out.println("Auto-opening database: " + databasePath);
            openDatabaseFile(databasePath);
        } else {
            JOptionPane.showMessageDialog(this, "Database file not found: " + databasePath, "Auto-open Failed", JOptionPane.WARNING_MESSAGE);
        }
    }

    public void openDatabaseFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Open DB4O Database");
        fileChooser.setFileFilter(new FileNameExtensionFilter("DB4O Database Files (*.dat, *.bak, *.nozip)", "dat", "bak", "nozip"));
        fileChooser.setCurrentDirectory(new File("local"));

        int result = fileChooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File selectedFile = fileChooser.getSelectedFile();
        openDatabaseFile(selectedFile.getAbsolutePath());
    }

    public void openDatabaseFile(String databasePath) {
        if (databaseSessions.containsKey(databasePath)) {
            selectDatabaseByPath(databasePath);
            return;
        }

        File selectedFile = new File(databasePath);
        if (!selectedFile.exists()) {
            JOptionPane.showMessageDialog(this, "Database file does not exist: " + databasePath, "File Not Found", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Show loading state on welcome panel
        welcomePanel.showLoading(selectedFile.getAbsolutePath());

        // Create progress monitor for UI feedback
        DatabaseProgressMonitor monitor = new DatabaseProgressMonitor(this, "Opening Database");

        // Process in background thread
        SwingWorker<DODatabaseContext, Void> worker = new SwingWorker<>() {
            private String errorMessage = null;

            @Override
            protected DODatabaseContext doInBackground() {
                try {
                    // Show the progress dialog
                    monitor.show();

                    // Open database and read schema using the central service
                    DODatabaseContext context = new DODatabaseContext(selectedFile.getAbsolutePath(), monitor);
                    databaseService.openDatabase(context);

                    return context;

                } catch (Exception e) {
                    e.printStackTrace();
                    errorMessage = e.getMessage();
                    return null;
                } finally {
                    // Hide the progress dialog
                    monitor.hide();
                }
            }

            @Override
            protected void done() {
                // Hide loading state
                welcomePanel.hideLoading();

                try {
                    DODatabaseContext resultContext = get();

                    if (resultContext == null || resultContext.databaseSchema == null || errorMessage != null) {
                        // Create detailed error message
                        String detailedError = errorMessage != null ? errorMessage : "Unknown error";

                        // Check for common error patterns
                        String helpText = "";
                        if (detailedError.contains("InvalidIDException")) {
                            helpText = "\n\nThis error typically indicates:\n" + "• The database file is corrupted\n" + "• The file is not a valid DB4O database\n" + "• The database was created with an incompatible DB4O version";
                        } else if (detailedError.contains("InaccessibleObjectException")) {
                            helpText = "\n\nThis error indicates Java module access issues.\n" + "Try restarting the application - module access flags should now be enabled.";
                        } else if (detailedError.contains("locked") || detailedError.contains("in use")) {
                            helpText = "\n\nThe database file is locked by another process.\n" + "Please close any other applications using this file.";
                        }

                        JTextArea textArea = new JTextArea(detailedError + helpText);
                        textArea.setEditable(false);
                        textArea.setWrapStyleWord(true);
                        textArea.setLineWrap(true);
                        textArea.setCaretPosition(0);

                        JScrollPane scrollPane = new JScrollPane(textArea);
                        scrollPane.setPreferredSize(new Dimension(600, 300));

                        JOptionPane.showMessageDialog(MainWindow.this, scrollPane, "Database Error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }

                    DOSchema inferredSchema = resultContext.databaseSchema;
                    createDatabaseSession(selectedFile, resultContext, inferredSchema);

                    // Trigger pending repeat export if requested
                    if (pendingRepeatExport) {
                        pendingRepeatExport = false;
                        SwingUtilities.invokeLater(() -> triggerRepeatExport());
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(MainWindow.this, "Error processing database:\n" + e.getMessage(), "Processing Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
    }

    private void createDatabaseSession(File selectedFile, DODatabaseContext context, DOSchema inferredSchema) {
        DatabaseSession session = new DatabaseSession();
        session.databasePath = selectedFile.getAbsolutePath();
        session.context = context;
        session.databaseSchema = inferredSchema;

        session.tabPane = new JTabbedPane();
        session.tabPane.setFont(new Font("Arial", Font.PLAIN, 12));
        session.tabContainer = session.tabPane;
        session.tabTitle = buildDatabaseTabTitle(session.databasePath);

        tabbedPane.addTab(session.tabTitle, session.tabContainer);
        databaseTabPathByContainer.put(session.tabContainer, session.databasePath);

        migration4o.ui.panels.database_panels.database_overview_panel.DatabaseOverviewPanel overviewPanel = new migration4o.ui.panels.database_panels.database_overview_panel.DatabaseOverviewPanel(session.databasePath, session.context);
        session.tabPane.addTab("Overview", overviewPanel);

        SchemaEditorPanel schemaEditor = new SchemaEditorPanel(inferredSchema, selectedFile.getName(), session.context);
        schemaEditor.setOnCompareRequested(() -> openDatabaseFile());
        session.databaseSchemaTab = schemaEditor;
        addSchemaTabToDatabaseSection(session.tabPane, "Database structure", schemaEditor, inferredSchema, false);

        createComparisonWithReference(session);
        createReachabilityAnalysisTab(session);
        createMigrationCoverageTab(session);
        createCostTab(session);
        createMigrationReportTab(session);
        createMigrationResultsTab(session);

        databaseSessions.put(session.databasePath, session);
        setActiveDatabaseSession(session);
        notifyTabsDatabaseOpened(session.context.databaseFilePath, inferredSchema);
        welcomePanel.addOpenDatabase(session.databasePath);
        tabbedPane.setSelectedComponent(session.tabContainer);
    }

    private String buildDatabaseTabTitle(String databasePath) {
        File dbFile = new File(databasePath);
        String base = dbFile.getParentFile() != null ? dbFile.getParentFile().getName() : dbFile.getName();
        String title = base;
        int suffix = 2;
        while (isDatabaseTabTitleInUse(title)) {
            title = base + " (" + suffix + ")";
            suffix++;
        }
        return title;
    }

    private boolean isDatabaseTabTitleInUse(String title) {
        for (DatabaseSession session : databaseSessions.values()) {
            if (title.equals(session.tabTitle)) {
                return true;
            }
        }
        return false;
    }

    private void selectDatabaseByPath(String databasePath) {
        DatabaseSession session = databaseSessions.get(databasePath);
        if (session == null) {
            return;
        }
        setActiveDatabaseSession(session);
        if (tabbedPane.indexOfComponent(session.tabContainer) >= 0) {
            tabbedPane.setSelectedComponent(session.tabContainer);
        }
    }

    private void syncActiveDatabaseSessionFromSelection() {
        Component selected = tabbedPane.getSelectedComponent();
        if (selected == null) {
            return;
        }
        String path = databaseTabPathByContainer.get(selected);
        if (path != null) {
            DatabaseSession session = databaseSessions.get(path);
            if (session != null) {
                setActiveDatabaseSession(session);
            }
        }
    }

    private void setActiveDatabaseSession(DatabaseSession session) {
        databaseTabPane = session.tabPane;
        databaseTabContainer = session.tabContainer;
        currentContext = session.context;
        currentDatabaseSchema = session.databaseSchema;
        databaseSchemaTab = session.databaseSchemaTab;
        conformityAnalysisTab = session.conformityAnalysisTab;
        reachabilityAnalysisTab = session.reachabilityAnalysisTab;
        migrationCoverageTab = session.migrationCoverageTab;
        costTab = session.costTab;
        migrationReportPanel = session.migrationReportPanel;
        migrationResultsPanel = session.migrationResultsPanel;
    }

    private void compareSelectedDatabases(List<String> selectedPaths) {
        if (selectedPaths == null || selectedPaths.size() < 2) {
            JOptionPane.showMessageDialog(this, "Please select at least two databases to compare.", "Compare Databases", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        List<DODatabaseContext> selectedContexts = selectedPaths.stream().map(path -> databaseSessions.get(path)).filter(session -> session != null && session.context != null).map(session -> session.context).collect(Collectors.toList());

        if (selectedContexts.size() < 2) {
            JOptionPane.showMessageDialog(this, "Selected databases are no longer available.", "Compare Databases", JOptionPane.WARNING_MESSAGE);
            return;
        }

        startMultiDatabaseComparison(selectedContexts);
    }

    private void startMultiDatabaseComparison(List<DODatabaseContext> contexts) {
        MultiDatabaseComparisonPanel comparisonPanel = new MultiDatabaseComparisonPanel(contexts);
        String tabTitle = "Database Comparison (" + contexts.size() + ")";
        addTab(tabTitle, comparisonPanel);
        tabbedPane.setSelectedComponent(comparisonPanel);
    }

    /**
     * Automatically creates a comparison between the reference schema and a newly
     * loaded database schema.
     */
    private void createComparisonWithReference(DatabaseSession session) {
        // Find the reference schema
        SchemaTabInfo referenceTab = null;
        for (SchemaTabInfo tabInfo : schemaTabs.values()) {
            if (tabInfo.isReference) {
                referenceTab = tabInfo;
                break;
            }
        }

        if (referenceTab == null) {
            System.out.println("Warning: No reference schema found for automatic comparison");
            return;
        }

        // Make final for lambda
        final SchemaTabInfo finalReferenceTab = referenceTab;

        // Create comparison - use live schema from editor in case it was reloaded
        SchemaComparison comparison = new SchemaComparison(referenceTab.editorPanel.getSchema(), referenceTab.label, session.databaseSchema, "Database");

        // Create comparison panel with callbacks to add missing elements
        SchemaComparisonPanel comparisonPanel = new SchemaComparisonPanel(comparison, (className, sourceClass) -> addClassToReference(finalReferenceTab.editorPanel, className, sourceClass), (parentClass, field) -> addFieldToReference(finalReferenceTab.editorPanel, parentClass, field));

        // Set callback to mark editor as modified when field is edited from comparison
        comparisonPanel.setOnSchemaModified(() -> {
            finalReferenceTab.editorPanel.markModified();
        });

        // Store and add conformity analysis tab to Database section
        session.conformityAnalysisTab = comparisonPanel;
        session.tabPane.addTab("Conformity analysis", comparisonPanel);
    }

    /**
     * Creates the reachability analysis tab.
     */
    private void createReachabilityAnalysisTab(DatabaseSession session) {
        try {
            System.out.println("Creating reachability analysis tab...");

            // Find the reference schema
            SchemaTabInfo referenceTab = null;
            for (SchemaTabInfo tabInfo : schemaTabs.values()) {
                if (tabInfo.isReference) {
                    referenceTab = tabInfo;
                    break;
                }
            }

            if (referenceTab == null) {
                System.out.println("Warning: No reference schema found for reachability analysis");
                return;
            }

            // Create reachability analysis panel
            ReachabilityAnalysisPanel reachabilityPanel = new ReachabilityAnalysisPanel(session.databaseSchema, referenceTab.editorPanel.getSchema());

            // Store and add reachability analysis tab to Database section
            session.reachabilityAnalysisTab = reachabilityPanel;
            session.tabPane.addTab("Reachability", reachabilityPanel);

            System.out.println("Reachability analysis tab created successfully");
        } catch (Exception e) {
            System.err.println("Error creating reachability analysis tab: " + e.getMessage());
            e.printStackTrace();
            // Don't rethrow - allow other tabs to be created
        }
    }

    /**
     * Creates the migration coverage tab.
     */
    private void createMigrationCoverageTab(DatabaseSession session) {
        // Find the reference schema
        SchemaTabInfo referenceTab = null;
        for (SchemaTabInfo tabInfo : schemaTabs.values()) {
            if (tabInfo.isReference) {
                referenceTab = tabInfo;
                break;
            }
        }

        if (referenceTab == null) {
            System.out.println("Warning: No reference schema found for migration coverage");
            return;
        }

        // Create migration coverage panel
        MigrationCoveragePanel coveragePanel = new MigrationCoveragePanel(referenceTab.editorPanel.getSchema(), session.databaseSchema, session.context.databaseFilePath, session.context);

        // Store and add migration coverage tab to Database section
        session.migrationCoverageTab = coveragePanel;
        session.tabPane.addTab("Migration coverage", coveragePanel);
    }

    /**
     * Creates the cost analysis tab.
     */
    private void createCostTab(DatabaseSession session) {
        // Create cost panel
        CostPanel costPanel = new CostPanel(session.databaseSchema);

        // Store and add processing costs tab to Database section
        session.costTab = costPanel;
        session.tabPane.addTab("Processing costs", costPanel);
    }

    /**
     * Creates the migration results tab.
     */
    private void createMigrationResultsTab(DatabaseSession session) {
        // Create migration results panel
        session.migrationResultsPanel = new migration4o.ui.panels.database_panels.migration_results_panel.MigrationResultsPanel();

        // Add to Database section with new name
        session.tabPane.addTab("Warnings & errors", session.migrationResultsPanel);
    }

    /**
     * Creates the migration report tab.
     */
    private void createMigrationReportTab(DatabaseSession session) {
        // Create migration report panel
        session.migrationReportPanel = new migration4o.ui.panels.database_panels.migration_report_panel.MigrationReportPanel();

        // Add to Database section
        session.tabPane.addTab("Migration report", session.migrationReportPanel);
    }

    /**
     * Closes the database and removes all database-related tabs.
     */
    private void closeDatabase() {
        if (currentContext != null) {
            closeDatabase(currentContext.databaseFilePath);
        }
    }

    private void closeDatabase(String databasePath) {
        DatabaseSession session = databaseSessions.remove(databasePath);
        if (session == null) {
            return;
        }

        if (session.context != null) {
            session.context.closeDatabase();
        }

        if (session.databaseSchemaTab != null) {
            schemaTabs.remove(session.databaseSchemaTab);
        }
        if (session.conformityAnalysisTab != null) {
            comparisonTabs.remove(session.conformityAnalysisTab);
        }

        if (session.tabContainer != null) {
            databaseTabPathByContainer.remove(session.tabContainer);
            tabbedPane.remove(session.tabContainer);
        }

        welcomePanel.removeOpenDatabase(databasePath);

        if (databaseSessions.isEmpty()) {
            databaseTabPane = null;
            databaseTabContainer = null;
            databaseSchemaTab = null;
            conformityAnalysisTab = null;
            reachabilityAnalysisTab = null;
            migrationCoverageTab = null;
            costTab = null;
            migrationReportPanel = null;
            migrationResultsPanel = null;
            currentContext = null;
            currentDatabaseSchema = null;
            tabbedPane.setSelectedIndex(0);
            return;
        }

        syncActiveDatabaseSessionFromSelection();
    }

    /**
     * Gets the current in-memory database container from the context. This allows
     * reusing the same in-memory instance across all operations.
     * 
     * @return The database container, or null if no database is open
     */
    public com.db4o.ext.ExtObjectContainer getDatabaseContainer() {
        return currentContext != null ? currentContext.container : null;
    }

    /**
     * Gets the current database context.
     * 
     * @return The current database context, or null if no database is open
     */
    public DODatabaseContext getCurrentContext() {
        return currentContext;
    }

    public DOSchema getCurrentDatabaseSchema() {
        return currentDatabaseSchema;
    }

    /**
     * Notifies all tabs that a database has been opened. Updates migration
     * structure panel that database schema has changed.
     */
    private void notifyTabsDatabaseOpened(String databasePath, DOSchema inferredSchema) {
        if (migrationStructurePanel != null) {
            migrationStructurePanel.setActiveContext(currentContext);
            migrationStructurePanel.onDatabaseSchemaChanged();
        }
    }

    /**
     * Notify the migration coverage panel about exported objects.
     * 
     * @param exportedClasses   Map of class name to number of exported objects
     * @param exportedObjectIds Map of class name to list of actual exported object
     *                          IDs
     */
    public void notifyExportCompleted(Map<String, Integer> exportedClasses, Map<String, List<Long>> exportedObjectIds) {
        if (migrationCoverageTab instanceof migration4o.ui.panels.database_panels.migration_coverage_panel.MigrationCoveragePanel) {
            migration4o.ui.panels.database_panels.migration_coverage_panel.MigrationCoveragePanel coveragePanel = (migration4o.ui.panels.database_panels.migration_coverage_panel.MigrationCoveragePanel) migrationCoverageTab;
            coveragePanel.updateExportedCounts(exportedClasses, exportedObjectIds);
        }
    }

    public void notifyExportCompleted(ExportStatistics result) {
        notifyExportCompleted(result, currentContext);
    }

    public void notifyExportCompleted(ExportStatistics result, DODatabaseContext dbContext) {
        if (result == null) {
            return;
        }

        String databasePath = dbContext != null ? dbContext.databaseFilePath : null;
        if (databasePath != null) {
            DatabaseSession session = databaseSessions.get(databasePath);
            if (session != null) {
                setActiveDatabaseSession(session);
            }
        }

        notifyExportCompleted(result.exportedClassCounts, result.exportedObjectIds);
        IDTracerDataService.getInstance().setLatestExportDiagnostics(result, dbContext);

        if (databasePath != null) {
            latestExportByDatabasePath.put(databasePath, result);
            for (ExportCompletionListener listener : new ArrayList<>(exportCompletionListeners)) {
                listener.onExportCompleted(databasePath, result);
            }
        }
    }

    public ExportStatistics getLatestExportStatistics(String databasePath) {
        return latestExportByDatabasePath.get(databasePath);
    }

    public void addExportCompletionListener(ExportCompletionListener listener) {
        if (listener == null) {
            return;
        }
        if (!exportCompletionListeners.contains(listener)) {
            exportCompletionListeners.add(listener);
        }
    }

    public void removeExportCompletionListener(ExportCompletionListener listener) {
        exportCompletionListeners.remove(listener);
    }

    /**
     * Reset reached values in the migration coverage panel. Should be called before
     * starting a new export.
     */
    public void resetCoverageReachedValues() {
        if (migrationCoverageTab instanceof migration4o.ui.panels.database_panels.migration_coverage_panel.MigrationCoveragePanel) {
            migration4o.ui.panels.database_panels.migration_coverage_panel.MigrationCoveragePanel coveragePanel = (migration4o.ui.panels.database_panels.migration_coverage_panel.MigrationCoveragePanel) migrationCoverageTab;
            coveragePanel.resetReachedValues();
        }
    }

    /**
     * Updates the migration results tab with new export statistics. Also switches
     * to the Database tab and Warnings & errors sub-tab.
     * 
     * @param result The export statistics to display
     */
    public void showMigrationResults(ExportStatistics result) {
        if (migrationResultsPanel != null && databaseTabContainer != null) {
            migrationResultsPanel.updateResults(result);

            // Switch to Database tab
            tabbedPane.setSelectedComponent(databaseTabContainer);

            // Switch to Warnings & errors sub-tab
            databaseTabPane.setSelectedComponent(migrationResultsPanel);
        }
    }

    /**
     * Switches to the Migration report tab in the Database section. This is called
     * when an export operation starts.
     */
    public void showMigrationReportTab() {
        if (migrationReportPanel != null && databaseTabContainer != null) {
            // Switch to Database tab
            tabbedPane.setSelectedComponent(databaseTabContainer);

            // Switch to Migration report sub-tab
            databaseTabPane.setSelectedComponent(migrationReportPanel);
        }
    }

    /**
     * Gets the migration report panel as a DOExportMonitor for real-time progress
     * updates.
     * 
     * @return The migration report monitor, or null if no database is loaded
     */
    public migration4o.ui.common.DOExportMonitor getMigrationReportMonitor() {
        return migrationReportPanel;
    }

    /**
     * Add a tab to the tabbed pane.
     */
    public void addTab(String title, Component component) {
        tabbedPane.addTab(title, component);
    }

    /**
     * Add a schema tab to the Schema nested section and track it for comparison.
     */
    public void addSchemaTabToSchemaSection(String title, SchemaEditorPanel editor, DOSchema schema, boolean isReference) {
        schemaTabPane.addTab(title, editor);
        schemaTabs.put(editor, new SchemaTabInfo(title, schema, editor, isReference));

        // Set up listener to refresh comparisons when this schema is reloaded
        editor.setOnSchemaReloaded(() -> refreshComparisonsForEditor(editor));
    }

    /**
     * Add a schema tab to the Database nested section and track it for comparison.
     */
    public void addSchemaTabToDatabaseSection(JTabbedPane targetPane, String title, SchemaEditorPanel editor, DOSchema schema, boolean isReference) {
        targetPane.addTab(title, editor);
        schemaTabs.put(editor, new SchemaTabInfo(title, schema, editor, isReference));

        // Set up listener to refresh comparisons when this schema is reloaded
        editor.setOnSchemaReloaded(() -> refreshComparisonsForEditor(editor));
    }

    /**
     * Add a schema tab and track it for comparison (legacy method for backward
     * compatibility).
     */
    public void addSchemaTab(String title, SchemaEditorPanel editor, DOSchema schema, boolean isReference) {
        tabbedPane.addTab(title, editor);
        schemaTabs.put(editor, new SchemaTabInfo(title, schema, editor, isReference));

        // Set up listener to refresh comparisons when this schema is reloaded
        editor.setOnSchemaReloaded(() -> refreshComparisonsForEditor(editor));
    }

    private void showTabContextMenu(java.awt.event.MouseEvent e) {
        int tabIndex = tabbedPane.indexAtLocation(e.getX(), e.getY());
        if (tabIndex < 0) {
            return;
        }

        JPopupMenu popup = new JPopupMenu();
        JMenuItem tearOffItem = new JMenuItem("Open in New Window");
        tearOffItem.addActionListener(ev -> tearOffTab(tabIndex));
        popup.add(tearOffItem);
        popup.show(e.getComponent(), e.getX(), e.getY());
    }

    private void tearOffTab(int tabIndex) {
        tearOffTab(tabIndex, null, true);
    }

    private void tearOffTab(int tabIndex, Component preferredSelectionOnReattach, boolean selectDetachedOnReattach) {
        if (tabIndex < 0 || tabIndex >= tabbedPane.getTabCount()) {
            return;
        }

        String title = tabbedPane.getTitleAt(tabIndex);
        Component component = tabbedPane.getComponentAt(tabIndex);

        // Don't allow tearing off the Welcome tab
        if (component == welcomePanel) {
            JOptionPane.showMessageDialog(this, "The Welcome tab cannot be detached.", "Cannot Detach Tab", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Remove from main window
        tabbedPane.removeTabAt(tabIndex);

        // Create new window
        JFrame detachedWindow = new JFrame(title + " - Migration4o");
        detachedWindow.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        detachedWindow.setSize(1200, 800);
        detachedWindow.setLocationRelativeTo(this);

        // Add window listener to reattach when closing
        detachedWindow.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                // Reattach the component to the main window
                tabbedPane.addTab(title, component);
                restoreSelectionAfterReattach(component, preferredSelectionOnReattach, selectDetachedOnReattach);
                detachedWindow.dispose();
            }
        });

        // Add component to new window
        detachedWindow.add(component, BorderLayout.CENTER);

        // Add toolbar with "Reattach" button
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        JButton reattachButton = new JButton("Reattach to Main Window");
        reattachButton.addActionListener(e -> {
            tabbedPane.addTab(title, component);
            restoreSelectionAfterReattach(component, preferredSelectionOnReattach, selectDetachedOnReattach);
            detachedWindow.dispose();
        });
        toolbar.add(reattachButton);
        detachedWindow.add(toolbar, BorderLayout.NORTH);

        // Show the new window
        detachedWindow.setVisible(true);
    }

    private void restoreSelectionAfterReattach(Component reattachedComponent, Component preferredSelectionOnReattach, boolean selectDetachedOnReattach) {
        Component targetSelection = reattachedComponent;

        if (!selectDetachedOnReattach && preferredSelectionOnReattach != null && tabbedPane.indexOfComponent(preferredSelectionOnReattach) >= 0) {
            targetSelection = preferredSelectionOnReattach;
        }

        tabbedPane.setSelectedComponent(targetSelection);
    }

    private void compareSchemas() {
        List<SchemaTabInfo> availableSchemas = new ArrayList<>(schemaTabs.values());

        if (availableSchemas.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No schemas available for comparison.\nPlease open at least one schema or database.", "No Schemas", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        if (availableSchemas.size() < 2) {
            JOptionPane.showMessageDialog(this, "At least two schemas are required for comparison.\nPlease open another schema or database.", "Insufficient Schemas", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Show dialog to select schemas to compare
        SchemaTabInfo reference = (SchemaTabInfo) JOptionPane.showInputDialog(this, "Select reference schema (base):", "Compare Schemas", JOptionPane.QUESTION_MESSAGE, null, availableSchemas.toArray(), availableSchemas.get(0));

        if (reference == null)
            return;

        // Filter out the selected reference
        List<SchemaTabInfo> remaining = new ArrayList<>(availableSchemas);
        remaining.remove(reference);

        SchemaTabInfo compared = (SchemaTabInfo) JOptionPane.showInputDialog(this, "Select schema to compare with:", "Compare Schemas", JOptionPane.QUESTION_MESSAGE, null, remaining.toArray(), remaining.get(0));

        if (compared == null)
            return;

        // Perform comparison - use live schema from editors in case they were reloaded
        SchemaComparison comparison = new SchemaComparison(reference.editorPanel.getSchema(), reference.label, compared.editorPanel.getSchema(), compared.label);

        // Create comparison panel with callbacks to add missing elements
        SchemaComparisonPanel comparisonPanel = new SchemaComparisonPanel(comparison, (className, sourceClass) -> addClassToReference(reference.editorPanel, className, sourceClass), (parentClass, field) -> addFieldToReference(reference.editorPanel, parentClass, field));

        // Set callback to mark reference editor as modified when field is edited from
        // comparison
        comparisonPanel.setOnSchemaModified(() -> {
            reference.editorPanel.markModified();
        });

        // Add comparison result as a new tab
        String tabTitle = "Compare: " + reference.label + " vs " + compared.label;
        addTab(tabTitle, comparisonPanel);
        tabbedPane.setSelectedIndex(tabbedPane.getTabCount() - 1);

        // Track this comparison tab
        comparisonTabs.put(comparisonPanel, new ComparisonTabInfo(reference, compared, tabTitle, comparisonPanel));
    }

    private void addClassToReference(SchemaEditorPanel editor, String className, DOSchemaClass sourceClass) {
        // Call the editor's addClassFromComparison method
        editor.addClassFromComparison(className, sourceClass);

        // Switch to the reference schema tab to show the added class
        focusSchemaEditorTab(editor);
    }

    private void addFieldToReference(SchemaEditorPanel editor, DOSchemaClass parentClass, DOSchemaField field) {
        // Call the editor's addFieldFromComparison method
        editor.addFieldFromComparison(parentClass, field);

        // Switch to the reference schema tab to show the added field
        focusSchemaEditorTab(editor);
    }

    private void focusSchemaEditorTab(SchemaEditorPanel editor) {
        if (editor == null) {
            return;
        }

        // Preferred path: editor lives in Schema nested tabs
        if (schemaTabPane != null && schemaTabPane.indexOfComponent(editor) >= 0) {
            if (tabbedPane.indexOfComponent(schemaTabPane) >= 0) {
                tabbedPane.setSelectedComponent(schemaTabPane);
            }
            schemaTabPane.setSelectedComponent(editor);
            return;
        }

        // Fallback: editor lives in Database nested tabs
        if (databaseTabPane != null && databaseTabPane.indexOfComponent(editor) >= 0) {
            if (databaseTabContainer != null && tabbedPane.indexOfComponent(databaseTabContainer) >= 0) {
                tabbedPane.setSelectedComponent(databaseTabContainer);
            }
            databaseTabPane.setSelectedComponent(editor);
            return;
        }

        // Legacy fallback: editor is directly in top-level tabbed pane
        if (tabbedPane.indexOfComponent(editor) >= 0) {
            tabbedPane.setSelectedComponent(editor);
        }
    }

    /**
     * Refresh all comparison tabs that involve the given editor.
     */
    private void refreshComparisonsForEditor(SchemaEditorPanel editor) {
        List<Component> toRefresh = new ArrayList<>();

        // Find all comparison tabs involving this editor
        for (Map.Entry<Component, ComparisonTabInfo> entry : comparisonTabs.entrySet()) {
            ComparisonTabInfo info = entry.getValue();
            if (info.referenceTab.editorPanel == editor || info.comparedTab.editorPanel == editor) {
                toRefresh.add(entry.getKey());
            }
        }

        // Refresh each comparison
        for (Component comp : toRefresh) {
            ComparisonTabInfo info = comparisonTabs.get(comp);
            if (info != null) {
                // Create new comparison with updated schemas
                SchemaComparison comparison = new SchemaComparison(info.referenceTab.editorPanel.getSchema(), info.referenceTab.label, info.comparedTab.editorPanel.getSchema(), info.comparedTab.label);

                // Update the panel with new comparison
                info.panel.updateComparison(comparison);
            }
        }

        // Refresh the Database -> Conformity analysis tab when either reference or
        // database schema is reloaded, so analysis stays up to date without
        // reopening the database.
        SchemaTabInfo reloadedTabInfo = schemaTabs.get(editor);
        DOSchema liveDatabaseSchema = getLiveDatabaseSchemaForConformity();
        if (reloadedTabInfo != null && conformityAnalysisTab instanceof SchemaComparisonPanel && liveDatabaseSchema != null) {
            boolean reloadedReference = reloadedTabInfo.isReference;
            boolean reloadedDatabase = databaseSchemaTab == editor;
            if (reloadedReference || reloadedDatabase) {
                SchemaTabInfo referenceTabInfo = findReferenceTabInfo();
                if (referenceTabInfo != null) {
                    SchemaComparisonPanel conformityPanel = (SchemaComparisonPanel) conformityAnalysisTab;
                    SchemaComparison updatedComparison = new SchemaComparison(referenceTabInfo.editorPanel.getSchema(), referenceTabInfo.label, liveDatabaseSchema, "Database");
                    conformityPanel.updateComparison(updatedComparison);
                }
            }
        }
    }

    private SchemaTabInfo findReferenceTabInfo() {
        for (SchemaTabInfo tabInfo : schemaTabs.values()) {
            if (tabInfo.isReference) {
                return tabInfo;
            }
        }
        return null;
    }

    private DOSchema getLiveDatabaseSchemaForConformity() {
        if (databaseSchemaTab instanceof SchemaEditorPanel) {
            return ((SchemaEditorPanel) databaseSchemaTab).getSchema();
        }
        return currentDatabaseSchema;
    }

    /**
     * Set callback for repeat export functionality (called from Migration4oUI).
     * 
     * @param callback the callback to execute when repeat export is triggered
     */
    public void setRepeatExportCallback(Runnable callback) {
        this.repeatExportCallback = callback;
    }

    /**
     * Request repeat export to be triggered after database loads. If database is
     * already open, triggers immediately. Otherwise, sets flag to trigger after
     * next database open.
     */
    public void triggerRepeatExport() {
        if (currentDatabaseSchema != null) {
            // Database already open, execute immediately
            if (repeatExportCallback != null) {
                repeatExportCallback.run();
            }
        } else {
            // Database not yet open, set flag to trigger after load
            pendingRepeatExport = true;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Set system look and feel
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }

            MainWindow window = new MainWindow();
            window.setVisible(true);
        });
    }
}
