package migration4o.ui;

import migration4o.models.schema.DOSchema;
import migration4o.schema.DODatabaseSchemaReader;
import migration4o.ui.editors.schema.SchemaEditorPanel;
import migration4o.ui.main.MainWindow;
import migration4o.ui.schema.MigrationStructurePanel;
import migration4o.ui.schema.SchemaStructurePanel;

import javax.swing.*;

/**
 * Main entry point for the Migration4o UI application.
 */
public class Migration4oUI {

    private static final String DEFAULT_SCHEMA_PATH = "schema/database-schema.xml";

    public static void main(String[] args) {
        // Parse command line arguments
        String databasePath = null;
        boolean repeatExport = false;
        
        for (String arg : args) {
            if (arg.equals("--repeat-export")) {
                repeatExport = true;
            } else if (!arg.startsWith("--")) {
                // Assume it's a database path
                databasePath = arg;
            }
        }
        
        final String finalDatabasePath = databasePath;
        final boolean finalRepeatExport = repeatExport;

        // Set look and feel before creating any UI components
        SwingUtilities.invokeLater(() -> {
            try {
                // Use system look and feel for native appearance
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Create and show main window
            MainWindow mainWindow = new MainWindow();

            // Add schema editor tab
            try {
                SchemaEditorPanel schemaEditor = new SchemaEditorPanel(DEFAULT_SCHEMA_PATH);
                schemaEditor.setOnCompareRequested(() -> mainWindow.openDatabaseFile());

                // Get the schema from the editor (already loaded by the constructor)
                DOSchema schema = schemaEditor.getSchema();

                mainWindow.addSchemaTab("Reference schema", schemaEditor, schema, true);

                // Add schema structure tab right after reference schema
                SchemaStructurePanel schemaStructurePanel = new SchemaStructurePanel(schema);
                mainWindow.addTab("Schema structure", schemaStructurePanel);

                // Add migration structure tab
                MigrationStructurePanel migrationStructurePanel = new MigrationStructurePanel(schema);
                mainWindow.addTab("Migration structure", migrationStructurePanel);
                
                // Set up repeat export callback
                mainWindow.setRepeatExportCallback(() -> migrationStructurePanel.repeatLastExport());
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null,
                        "Error loading default schema: " + e.getMessage(),
                        "Schema Load Error",
                        JOptionPane.ERROR_MESSAGE);
            }

            // Show window
            mainWindow.setVisible(true);

            // Auto-open database if path was provided
            if (finalDatabasePath != null) {
                java.io.File dbFile = new java.io.File(finalDatabasePath);
                if (dbFile.exists() && dbFile.isFile()) {
                    System.out.println("Auto-opening database: " + finalDatabasePath);
                    
                    // Set up repeat export before opening database
                    if (finalRepeatExport) {
                        mainWindow.triggerRepeatExport();
                    }
                    
                    // Open database - repeat export will trigger automatically after load completes
                    mainWindow.openDatabaseFile(finalDatabasePath);
                } else {
                    JOptionPane.showMessageDialog(mainWindow,
                            "Database file not found: " + finalDatabasePath,
                            "Auto-open Failed",
                            JOptionPane.WARNING_MESSAGE);
                }
            }
        });
    }
}
