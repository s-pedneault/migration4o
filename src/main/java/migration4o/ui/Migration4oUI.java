package migration4o.ui;

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
            SchemaEditorPanel schemaEditor = new SchemaEditorPanel(DEFAULT_SCHEMA_PATH);
            mainWindow.addTab("Schema Editor", schemaEditor);

            // Show window
            mainWindow.setVisible(true);
        });
    }
}
