package migration4o.ui;

import migration4o.models.schema.DOSchema;
import migration4o.schema.DODatabaseSchemaReader;
import migration4o.ui.editors.schema.SchemaEditorPanel;
import migration4o.ui.main.MainWindow;

import javax.swing.*;

/**
 * Main entry point for the Migration4o UI application.
 */
public class Migration4oUI {

    private static final String DEFAULT_SCHEMA_PATH = "schema/database-schema.xml";

    public static void main(String[] args) {
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
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null,
                        "Error loading default schema: " + e.getMessage(),
                        "Schema Load Error",
                        JOptionPane.ERROR_MESSAGE);
            }

            // Show window
            mainWindow.setVisible(true);
        });
    }
}
