package migration4o.ui;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import migration4o.ui.main.MainWindow;

/**
 * Main entry point for the Migration4o UI application.
 * Handles command-line arguments and application bootstrapping.
 * All UI initialization is delegated to MainWindow.
 */
public class Migration4oUI {

    public static void main(String[] args) {
        // Parse command line arguments
        String databasePath = null;
        boolean repeatExport = false;

        for (String arg : args) {
            if (arg.equals("--repeat-export")) {
                repeatExport = true;
            } else if (!arg.startsWith("--")) {
                databasePath = arg;
            }
        }

        final String finalDatabasePath = databasePath;
        final boolean finalRepeatExport = repeatExport;

        // Set look and feel before creating any UI components
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Create and initialize main window - it handles all tab setup
            MainWindow mainWindow = new MainWindow();
            mainWindow.initialize();
            mainWindow.setVisible(true);

            // Handle command-line database auto-open
            if (finalDatabasePath != null) {
                if (finalRepeatExport) {
                    mainWindow.triggerRepeatExport();
                }
                mainWindow.autoOpenDatabase(finalDatabasePath);
            }
        });
    }
}
