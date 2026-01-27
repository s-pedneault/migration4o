package migration4o.ui.main;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

import migration4o.database.DODatabaseOpener;
import migration4o.database.DODatabaseReader;
import migration4o.models.database.DODatabase;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.ui.ComparisonTabInfo;
import migration4o.models.ui.SchemaTabInfo;
import migration4o.schema.DODatabaseSchemaInferrer;
import migration4o.ui.panels.database_panels.conformity_analysis_panel.SchemaComparison;
import migration4o.ui.panels.database_panels.conformity_analysis_panel.SchemaComparisonPanel;
import migration4o.ui.panels.database_panels.migration_coverage_panel.MigrationCoveragePanel;
import migration4o.ui.panels.reference_schema_panels.reference_schema_panel.SchemaEditorPanel;
import migration4o.ui.panels.welcome_panel.WelcomePanel;

/**
 * Main application window with tabbed interface for migration tools.
 */
public class MainWindow extends JFrame {

    private JTabbedPane tabbedPane;
    private WelcomePanel welcomePanel;
    private Map<Component, SchemaTabInfo> schemaTabs = new HashMap<>();
    private Map<Component, ComparisonTabInfo> comparisonTabs = new HashMap<>();

    private Runnable repeatExportCallback;
    private boolean pendingRepeatExport = false;

    // Track database-related tabs for closing
    private Component databaseSchemaTab = null;
    private Component conformityAnalysisTab = null;
    private Component migrationCoverageTab = null;
    private DOSchema currentDatabaseSchema = null;
    private String currentDatabasePath = null;

    public MainWindow() {
        initializeUI();
    }

    private void initializeUI() {
        setTitle("Migration4o - Database Migration Tool");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1400, 900);
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

        // Add tabs (for now just placeholder, will add schema editor next)
        addTabs();

        // Add to frame
        add(tabbedPane, BorderLayout.CENTER);
    }

    private void addTabs() {
        // Create and add welcome panel as first tab
        welcomePanel = new WelcomePanel();
        welcomePanel.setOnOpenDatabase(() -> openDatabaseFile());
        welcomePanel.setOnCloseDatabase(() -> closeDatabase());
        tabbedPane.addTab("Welcome", welcomePanel);
    }

    public void openDatabaseFile() {
        // Don't open if a database is already open
        if (currentDatabaseSchema != null) {
            JOptionPane.showMessageDialog(this,
                    "Please close the current database before opening a new one.",
                    "Database Already Open",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Open DB4O Database");
        fileChooser.setFileFilter(new FileNameExtensionFilter("DB4O Database Files (*.dat, *.bak)", "dat", "bak"));
        fileChooser.setCurrentDirectory(new File("local"));

        int result = fileChooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File selectedFile = fileChooser.getSelectedFile();
        openDatabaseFile(selectedFile.getAbsolutePath());
    }

    public void openDatabaseFile(String databasePath) {
        // Don't open if a database is already open
        if (currentDatabaseSchema != null) {
            JOptionPane.showMessageDialog(this,
                    "Please close the current database before opening a new one.",
                    "Database Already Open",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        File selectedFile = new File(databasePath);
        if (!selectedFile.exists()) {
            JOptionPane.showMessageDialog(this,
                    "Database file does not exist: " + databasePath,
                    "File Not Found",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        currentDatabasePath = selectedFile.getAbsolutePath();

        // Show loading state on welcome panel
        welcomePanel.showLoading(selectedFile.getAbsolutePath());

        // Process in background thread
        SwingWorker<DOSchema, Void> worker = new SwingWorker<>() {
            private String errorMessage = null;

            @Override
            protected DOSchema doInBackground() {
                try {
                    // Open database
                    DODatabaseOpener opener = new DODatabaseOpener();
                    var objectContainer = opener.openDatabase(selectedFile.getAbsolutePath());

                    // Get encoding used to open database
                    var encoding = opener.getSuccessfulEncoding();

                    // Read database structure (pass null for schema since we're inferring it)
                    DODatabaseReader reader = new DODatabaseReader();
                    DODatabase database = reader.readDatabaseMeta(objectContainer, encoding,
                            selectedFile.length() + " bytes", null);

                    // Close database
                    objectContainer.close();

                    // Infer schema
                    DODatabaseSchemaInferrer inferrer = new DODatabaseSchemaInferrer();
                    return inferrer.inferSchemaFromDatabase(database);

                } catch (Exception e) {
                    e.printStackTrace();
                    errorMessage = e.getMessage();
                    return null;
                }
            }

            @Override
            protected void done() {
                // Hide loading state
                welcomePanel.hideLoading();

                try {
                    DOSchema inferredSchema = get();

                    if (inferredSchema == null || errorMessage != null) {
                        // Create detailed error message
                        String detailedError = errorMessage != null ? errorMessage : "Unknown error";

                        // Check for common error patterns
                        String helpText = "";
                        if (detailedError.contains("InvalidIDException")) {
                            helpText = "\n\nThis error typically indicates:\n" +
                                    "• The database file is corrupted\n" +
                                    "• The file is not a valid DB4O database\n" +
                                    "• The database was created with an incompatible DB4O version";
                        } else if (detailedError.contains("InaccessibleObjectException")) {
                            helpText = "\n\nThis error indicates Java module access issues.\n" +
                                    "Try restarting the application - module access flags should now be enabled.";
                        } else if (detailedError.contains("locked") || detailedError.contains("in use")) {
                            helpText = "\n\nThe database file is locked by another process.\n" +
                                    "Please close any other applications using this file.";
                        }

                        JTextArea textArea = new JTextArea(detailedError + helpText);
                        textArea.setEditable(false);
                        textArea.setWrapStyleWord(true);
                        textArea.setLineWrap(true);
                        textArea.setCaretPosition(0);

                        JScrollPane scrollPane = new JScrollPane(textArea);
                        scrollPane.setPreferredSize(new Dimension(600, 300));

                        JOptionPane.showMessageDialog(MainWindow.this,
                                scrollPane,
                                "Database Error",
                                JOptionPane.ERROR_MESSAGE);
                        return;
                    }

                    // Store the database schema
                    currentDatabaseSchema = inferredSchema;

                    // Create schema editor panel with inferred schema
                    SchemaEditorPanel schemaEditor = new SchemaEditorPanel(inferredSchema, selectedFile.getName());
                    schemaEditor.setOnCompareRequested(() -> openDatabaseFile());

                    // Add database structure tab
                    databaseSchemaTab = schemaEditor;
                    addSchemaTab("Database structure", schemaEditor, inferredSchema, false);

                    // Automatically create comparison with reference schema
                    createComparisonWithReference(inferredSchema);

                    // Create migration coverage tab
                    createMigrationCoverageTab(inferredSchema);

                    // Notify all tabs that a database has been opened
                    notifyTabsDatabaseOpened(currentDatabasePath, inferredSchema);

                    // Update welcome panel state
                    welcomePanel.setDatabaseOpen(true);

                    // Switch to the database structure tab
                    tabbedPane.setSelectedComponent(databaseSchemaTab);

                    // Trigger pending repeat export if requested
                    if (pendingRepeatExport) {
                        pendingRepeatExport = false;
                        SwingUtilities.invokeLater(() -> triggerRepeatExport());
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(MainWindow.this,
                            "Error processing database:\n" + e.getMessage(),
                            "Processing Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
    }

    /**
     * Automatically creates a comparison between the reference schema and a newly
     * loaded database schema.
     */
    private void createComparisonWithReference(DOSchema databaseSchema) {
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
        SchemaComparison comparison = new SchemaComparison(
                referenceTab.editorPanel.getSchema(), referenceTab.label,
                databaseSchema, "Database");

        // Create comparison panel with callbacks to add missing elements
        SchemaComparisonPanel comparisonPanel = new SchemaComparisonPanel(
                comparison,
                (className, sourceClass) -> addClassToReference(finalReferenceTab.editorPanel, className, sourceClass),
                (parentClass, field) -> addFieldToReference(finalReferenceTab.editorPanel, parentClass, field));

        // Set callback to mark editor as modified when field is edited from comparison
        comparisonPanel.setOnSchemaModified(() -> {
            finalReferenceTab.editorPanel.markModified();
        });

        // Store and add conformity analysis tab
        conformityAnalysisTab = comparisonPanel;
        addTab("Conformity analysis", comparisonPanel);
    }

    /**
     * Creates the migration coverage tab.
     */
    private void createMigrationCoverageTab(DOSchema databaseSchema) {
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
        MigrationCoveragePanel coveragePanel = new MigrationCoveragePanel(
                referenceTab.editorPanel.getSchema(),
                databaseSchema,
                currentDatabasePath);

        // Store and add migration coverage tab
        migrationCoverageTab = coveragePanel;
        addTab("Migration coverage", coveragePanel);
    }

    /**
     * Closes the database and removes all database-related tabs.
     */
    private void closeDatabase() {
        // Remove database-related tabs
        if (databaseSchemaTab != null) {
            tabbedPane.remove(databaseSchemaTab);
            schemaTabs.remove(databaseSchemaTab);
            databaseSchemaTab = null;
        }

        if (conformityAnalysisTab != null) {
            tabbedPane.remove(conformityAnalysisTab);
            comparisonTabs.remove(conformityAnalysisTab);
            conformityAnalysisTab = null;
        }

        if (migrationCoverageTab != null) {
            tabbedPane.remove(migrationCoverageTab);
            migrationCoverageTab = null;
        }

        currentDatabaseSchema = null;
        currentDatabasePath = null;

        // Update welcome panel state
        welcomePanel.setDatabaseOpen(false);

        // Switch to welcome tab
        tabbedPane.setSelectedIndex(0);
    }

    /**
     * Notifies all tabs that a database has been opened
     */
    private void notifyTabsDatabaseOpened(String databasePath, DOSchema inferredSchema) {
        // Iterate through all tabs and notify those that need to know
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            Component component = tabbedPane.getComponentAt(i);
            if (component instanceof migration4o.ui.panels.reference_schema_panels.migration_structure_panel.MigrationStructurePanel) {
                migration4o.ui.panels.reference_schema_panels.migration_structure_panel.MigrationStructurePanel migrationPanel = (migration4o.ui.panels.reference_schema_panels.migration_structure_panel.MigrationStructurePanel) component;
                migrationPanel.setDatabasePath(databasePath);
                migrationPanel.setDatabaseSchema(inferredSchema);
            }
        }
    }

    /**
     * Notify the migration coverage panel about exported objects.
     * 
     * @param exportedClasses Map of class name to number of exported objects
     */
    public void notifyExportCompleted(Map<String, Integer> exportedClasses) {
        if (migrationCoverageTab instanceof migration4o.ui.panels.database_panels.migration_coverage_panel.MigrationCoveragePanel) {
            migration4o.ui.panels.database_panels.migration_coverage_panel.MigrationCoveragePanel coveragePanel = (migration4o.ui.panels.database_panels.migration_coverage_panel.MigrationCoveragePanel) migrationCoverageTab;
            coveragePanel.updateExportedCounts(exportedClasses);
        }
    }

    /**
     * Add a tab to the tabbed pane.
     */
    public void addTab(String title, Component component) {
        tabbedPane.addTab(title, component);
    }

    /**
     * Add a schema tab and track it for comparison.
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
        if (tabIndex < 0 || tabIndex >= tabbedPane.getTabCount()) {
            return;
        }

        String title = tabbedPane.getTitleAt(tabIndex);
        Component component = tabbedPane.getComponentAt(tabIndex);

        // Don't allow tearing off the Welcome tab
        if (component == welcomePanel) {
            JOptionPane.showMessageDialog(this,
                    "The Welcome tab cannot be detached.",
                    "Cannot Detach Tab",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Remove from main window
        tabbedPane.removeTabAt(tabIndex);

        // Create new window
        JFrame detachedWindow = new JFrame(title + " - Migration4o");
        detachedWindow.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        detachedWindow.setSize(1200, 800);
        detachedWindow.setLocationRelativeTo(this);

        // Add component to new window
        detachedWindow.add(component, BorderLayout.CENTER);

        // Add toolbar with "Reattach" button
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        JButton reattachButton = new JButton("Reattach to Main Window");
        reattachButton.addActionListener(e -> {
            detachedWindow.dispose();
            tabbedPane.addTab(title, component);
            tabbedPane.setSelectedComponent(component);
        });
        toolbar.add(reattachButton);
        detachedWindow.add(toolbar, BorderLayout.NORTH);

        // Show the new window
        detachedWindow.setVisible(true);
    }

    private void compareSchemas() {
        List<SchemaTabInfo> availableSchemas = new ArrayList<>(schemaTabs.values());

        if (availableSchemas.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No schemas available for comparison.\nPlease open at least one schema or database.",
                    "No Schemas", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        if (availableSchemas.size() < 2) {
            JOptionPane.showMessageDialog(this,
                    "At least two schemas are required for comparison.\nPlease open another schema or database.",
                    "Insufficient Schemas", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Show dialog to select schemas to compare
        SchemaTabInfo reference = (SchemaTabInfo) JOptionPane.showInputDialog(this,
                "Select reference schema (base):",
                "Compare Schemas",
                JOptionPane.QUESTION_MESSAGE,
                null,
                availableSchemas.toArray(),
                availableSchemas.get(0));

        if (reference == null)
            return;

        // Filter out the selected reference
        List<SchemaTabInfo> remaining = new ArrayList<>(availableSchemas);
        remaining.remove(reference);

        SchemaTabInfo compared = (SchemaTabInfo) JOptionPane.showInputDialog(this,
                "Select schema to compare with:",
                "Compare Schemas",
                JOptionPane.QUESTION_MESSAGE,
                null,
                remaining.toArray(),
                remaining.get(0));

        if (compared == null)
            return;

        // Perform comparison - use live schema from editors in case they were reloaded
        SchemaComparison comparison = new SchemaComparison(
                reference.editorPanel.getSchema(), reference.label,
                compared.editorPanel.getSchema(), compared.label);

        // Create comparison panel with callbacks to add missing elements
        SchemaComparisonPanel comparisonPanel = new SchemaComparisonPanel(
                comparison,
                (className, sourceClass) -> addClassToReference(reference.editorPanel, className, sourceClass),
                (parentClass, field) -> addFieldToReference(reference.editorPanel, parentClass, field));

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
        for (Map.Entry<Component, SchemaTabInfo> entry : schemaTabs.entrySet()) {
            if (entry.getValue().editorPanel == editor) {
                tabbedPane.setSelectedComponent(entry.getKey());
                break;
            }
        }
    }

    private void addFieldToReference(SchemaEditorPanel editor, DOSchemaClass parentClass, DOSchemaField field) {
        // Call the editor's addFieldFromComparison method
        editor.addFieldFromComparison(parentClass, field);

        // Switch to the reference schema tab to show the added field
        for (Map.Entry<Component, SchemaTabInfo> entry : schemaTabs.entrySet()) {
            if (entry.getValue().editorPanel == editor) {
                tabbedPane.setSelectedComponent(entry.getKey());
                break;
            }
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
                SchemaComparison comparison = new SchemaComparison(
                        info.referenceTab.editorPanel.getSchema(), info.referenceTab.label,
                        info.comparedTab.editorPanel.getSchema(), info.comparedTab.label);

                // Update the panel with new comparison
                info.panel.updateComparison(comparison);
            }
        }
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
     * Request repeat export to be triggered after database loads.
     * If database is already open, triggers immediately. Otherwise, sets flag to
     * trigger after next database open.
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
