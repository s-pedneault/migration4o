package migration4o.ui.main;

import javax.swing.*;
import java.awt.*;

/**
 * Main application window with tabbed interface for migration tools.
 */
public class MainWindow extends JFrame {

    private JTabbedPane tabbedPane;

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

    /**
     * Add a tab to the tabbed pane.
     */
    public void addTab(String title, Component component) {
        tabbedPane.addTab(title, component);
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
