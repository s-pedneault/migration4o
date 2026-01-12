package migration4o.ui.main;

import migration4o.database.DODatabaseOpener;
import migration4o.database.DODatabaseReader;
import migration4o.models.database.DODatabase;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.schema.DODatabaseSchemaInferrer;
import migration4o.schema.DODatabaseSchemaWriter;
import migration4o.ui.comparison.SchemaComparison;
import migration4o.ui.comparison.SchemaComparisonPanel;
import migration4o.ui.editors.schema.SchemaEditorPanel;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main application window with tabbed interface for migration tools.
 */
public class MainWindow extends JFrame {

    private JTabbedPane tabbedPane;
    private Map<Component, SchemaTabInfo> schemaTabs = new HashMap<>();
    private Map<Component, ComparisonTabInfo> comparisonTabs = new HashMap<>();

    private static class ComparisonTabInfo {
        SchemaTabInfo referenceTab;
        SchemaTabInfo comparedTab;
        String title;
        SchemaComparisonPanel panel;

        ComparisonTabInfo(SchemaTabInfo referenceTab, SchemaTabInfo comparedTab, String title,
                SchemaComparisonPanel panel) {
            this.referenceTab = referenceTab;
            this.comparedTab = comparedTab;
            this.title = title;
            this.panel = panel;
        }
    }

    public MainWindow() {
        initializeUI();
    }

    private static class SchemaTabInfo {
        String label;
        DOSchema schema;
        SchemaEditorPanel editorPanel;
        boolean isReference; // true if this is the XML reference schema

        SchemaTabInfo(String label, DOSchema schema, SchemaEditorPanel editorPanel, boolean isReference) {
            this.label = label;
            this.schema = schema;
            this.editorPanel = editorPanel;
            this.isReference = isReference;
        }

        @Override
        public String toString() {
            return label + (isReference ? " (Reference)" : "");
        }
    }

    private void initializeUI() {
        setTitle("Migration4o - Database Migration Tool");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1400, 900);
        setLocationRelativeTo(null);

        // Create tabbed pane
        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Arial", Font.PLAIN, 14));

        // Add tabs (for now just placeholder, will add schema editor next)
        addTabs();

        // Add to frame
        add(tabbedPane, BorderLayout.CENTER);

        // Add menu bar
        setJMenuBar(createMenuBar());
    }

    private void addTabs() {
        // Tabs will be added here
        // First tab will be the schema editor
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // File menu
        JMenu fileMenu = new JMenu("File");

        // Open Database menu item
        JMenuItem openDatabaseItem = new JMenuItem("Open Database...");
        openDatabaseItem.addActionListener(e -> openDatabaseFile());
        fileMenu.add(openDatabaseItem);

        fileMenu.addSeparator();

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);

        // Help menu
        JMenu helpMenu = new JMenu("Help");
        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> showAboutDialog());
        helpMenu.add(aboutItem);
        menuBar.add(helpMenu);

        return menuBar;
    }

    private void showAboutDialog() {
        JOptionPane.showMessageDialog(this,
                "Migration4o - Database Migration Tool\n" +
                        "Version 1.0\n\n" +
                        "A tool for migrating database schemas and data.",
                "About Migration4o",
                JOptionPane.INFORMATION_MESSAGE);
    }

    public void openDatabaseFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Open DB4O Database");
        fileChooser.setFileFilter(new FileNameExtensionFilter("DB4O Database Files (*.dat, *.bak)", "dat", "bak"));
        fileChooser.setCurrentDirectory(new File("local"));

        int result = fileChooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File selectedFile = fileChooser.getSelectedFile();

        // Show progress dialog
        JDialog progressDialog = new JDialog(this, "Loading Database", true);
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setString("Opening database and inferring schema...");
        progressBar.setStringPainted(true);

        JPanel progressPanel = new JPanel(new BorderLayout(10, 10));
        progressPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        progressPanel.add(new JLabel("Please wait..."), BorderLayout.NORTH);
        progressPanel.add(progressBar, BorderLayout.CENTER);

        progressDialog.add(progressPanel);
        progressDialog.pack();
        progressDialog.setLocationRelativeTo(this);

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
                progressDialog.dispose();

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

                    // Create schema editor panel with inferred schema
                    String tabTitle = "DB: " + selectedFile.getName();
                    SchemaEditorPanel schemaEditor = new SchemaEditorPanel(inferredSchema, selectedFile.getName());
                    schemaEditor.setOnCompareRequested(() -> openDatabaseFile());
                    addSchemaTab(tabTitle, schemaEditor, inferredSchema, false);

                    // Switch to the database tab
                    tabbedPane.setSelectedIndex(tabbedPane.getTabCount() - 1);

                    // Automatically create comparison with reference schema
                    createComparisonWithReference(inferredSchema, selectedFile.getName());

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
        progressDialog.setVisible(true);
    }

    /**
     * Automatically creates a comparison between the reference schema and a newly
     * loaded database schema.
     */
    private void createComparisonWithReference(DOSchema databaseSchema, String databaseName) {
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
                databaseSchema, "DB: " + databaseName);

        // Create comparison panel with callbacks to add missing elements
        SchemaComparisonPanel comparisonPanel = new SchemaComparisonPanel(
                comparison,
                (className, sourceClass) -> addClassToReference(finalReferenceTab.editorPanel, className, sourceClass),
                (parentClass, field) -> addFieldToReference(finalReferenceTab.editorPanel, parentClass, field));

        // Add comparison result as a new tab
        String tabTitle = "Compare: " + referenceTab.label + " vs DB: " + databaseName;
        addTab(tabTitle, comparisonPanel);

        // Switch to the comparison tab
        tabbedPane.setSelectedIndex(tabbedPane.getTabCount() - 1);
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
        // This will be handled by the SchemaEditorPanel
        // For now, show a message - will implement field addition in SchemaEditorPanel
        JOptionPane.showMessageDialog(this,
                "Field addition feature will be implemented in SchemaEditorPanel.\n" +
                        "Field: " + field.getSource() + " in class: " + parentClass.getSourceName(),
                "Feature Pending", JOptionPane.INFORMATION_MESSAGE);
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
