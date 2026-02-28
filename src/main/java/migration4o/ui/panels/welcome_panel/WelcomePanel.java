package migration4o.ui.panels.welcome_panel;

import javax.swing.*;
import java.awt.*;
import java.net.URL;

import java.util.function.Consumer;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Welcome screen panel with logo and database open/close functionality.
 */
public class WelcomePanel extends JPanel {

    private JButton openButton;
    private JButton compareSelectedButton;
    private JLabel loadingMessage;
    private JPanel openDatabasesPanel;
    private Runnable onOpenDatabase;
    private Consumer<String> onCloseDatabase;
    private Consumer<List<String>> onCompareSelected;

    public WelcomePanel() {
        setLayout(new GridBagLayout());
        setBackground(Color.WHITE);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(40, 20, 20, 20);
        gbc.anchor = GridBagConstraints.CENTER;

        // Load and display PNG logo
        try {
            URL logoUrl = getClass().getResource("/assets/logo.png");
            if (logoUrl != null) {
                ImageIcon logoIcon = new ImageIcon(logoUrl);
                // Scale the logo to a reasonable size (adjust height as needed)
                Image scaledImage = logoIcon.getImage().getScaledInstance(-1, 120, Image.SCALE_SMOOTH);
                JLabel logoLabel = new JLabel(new ImageIcon(scaledImage));
                logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                add(logoLabel, gbc);
            } else {
                // Fallback to text logo if PNG not found
                addTextLogo(gbc);
            }
        } catch (Exception e) {
            // Fallback to text logo on error
            e.printStackTrace();
            addTextLogo(gbc);
        }

        // Add subtitle
        // gbc.gridy = 1;
        // gbc.insets = new Insets(10, 20, 30, 20);
        // JLabel subtitleLabel = new JLabel("Database Migration Tool");
        // subtitleLabel.setFont(new Font("Arial", Font.PLAIN, 18));
        // subtitleLabel.setForeground(Color.DARK_GRAY);
        // add(subtitleLabel, gbc);

        // Add database button
        gbc.gridy = 2;
        gbc.insets = new Insets(30, 20, 10, 20);
        openButton = new JButton("Open database...");
        openButton.setFont(new Font("Arial", Font.BOLD, 16));
        openButton.setPreferredSize(new Dimension(200, 50));
        openButton.setBackground(new Color(37, 99, 235));
        openButton.setForeground(Color.WHITE);
        openButton.setFocusPainted(false);
        openButton.setOpaque(true);
        openButton.setBorderPainted(false);
        openButton.addActionListener(e -> {
            if (onOpenDatabase != null) {
                onOpenDatabase.run();
            }
        });
        add(openButton, gbc);

        gbc.gridy = 3;
        gbc.insets = new Insets(10, 20, 10, 20);
        compareSelectedButton = new JButton("Compare selected");
        compareSelectedButton.setFont(new Font("Arial", Font.PLAIN, 14));
        compareSelectedButton.setEnabled(false);
        compareSelectedButton.addActionListener(e -> {
            if (onCompareSelected != null) {
                onCompareSelected.accept(getSelectedDatabasePaths());
            }
        });
        add(compareSelectedButton, gbc);

        // Panel to hold the list of open databases
        gbc.gridy = 4;
        gbc.insets = new Insets(10, 20, 10, 20);
        openDatabasesPanel = new JPanel();
        openDatabasesPanel.setLayout(new BoxLayout(openDatabasesPanel, BoxLayout.Y_AXIS));
        openDatabasesPanel.setBackground(Color.WHITE);
        add(openDatabasesPanel, gbc);

        // Add loading message (initially hidden)
        gbc.gridy = 5;
        gbc.insets = new Insets(20, 20, 20, 20);
        loadingMessage = new JLabel("");
        loadingMessage.setFont(new Font("Arial", Font.PLAIN, 12));
        loadingMessage.setForeground(new Color(100, 116, 139));
        loadingMessage.setVisible(false);
        add(loadingMessage, gbc);
    }

    /**
     * Fallback method to create text-based logo if PNG fails to load
     */
    private void addTextLogo(GridBagConstraints gbc) {
        JPanel logoContainer = new JPanel();
        logoContainer.setLayout(new BoxLayout(logoContainer, BoxLayout.Y_AXIS));
        logoContainer.setBackground(Color.WHITE);

        // Database icon using Unicode
        JLabel iconLabel = new JLabel("🗄️");
        iconLabel.setFont(new Font("SansSerif", Font.PLAIN, 72));
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        logoContainer.add(iconLabel);

        logoContainer.add(Box.createVerticalStrut(15));

        // Logo text
        JPanel logoTextPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        logoTextPanel.setBackground(Color.WHITE);

        JLabel logoLabel = new JLabel("Migration");
        logoLabel.setFont(new Font("SansSerif", Font.BOLD, 56));
        logoLabel.setForeground(new Color(11, 18, 32));

        JLabel logo4o = new JLabel("4o");
        logo4o.setFont(new Font("SansSerif", Font.BOLD, 56));
        logo4o.setForeground(new Color(37, 99, 235));

        logoTextPanel.add(logoLabel);
        logoTextPanel.add(logo4o);

        logoContainer.add(logoTextPanel);
        add(logoContainer, gbc);
    }

    public void setOnOpenDatabase(Runnable callback) {
        this.onOpenDatabase = callback;
    }

    public void setOnCloseDatabase(Consumer<String> callback) {
        this.onCloseDatabase = callback;
    }

    public void setOnCompareSelected(Consumer<List<String>> callback) {
        this.onCompareSelected = callback;
    }

    public void addOpenDatabase(String databasePath) {
        File dbFile = new File(databasePath);
        String name = dbFile.getParentFile() != null ? dbFile.getParentFile().getName() : dbFile.getName();

        JPanel dbRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        dbRow.setBackground(Color.WHITE);
        dbRow.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Checkbox for future multi-database comparison feature
        JCheckBox compareCheckBox = new JCheckBox();
        compareCheckBox.setBackground(Color.WHITE);
        compareCheckBox.setToolTipText("Select for comparison");
        compareCheckBox.addActionListener(e -> refreshCompareButtonState());

        JLabel nameLabel = new JLabel(name + " (" + dbFile.getName() + ")");
        nameLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        nameLabel.setPreferredSize(new Dimension(200, 25));

        JButton closeBtn = new JButton("Close");
        closeBtn.setBackground(new Color(220, 38, 38));
        closeBtn.setForeground(Color.WHITE);
        closeBtn.setFocusPainted(false);
        closeBtn.setOpaque(true);
        closeBtn.setBorderPainted(false);
        closeBtn.addActionListener(e -> {
            if (onCloseDatabase != null) {
                onCloseDatabase.accept(databasePath);
            }
        });

        dbRow.add(compareCheckBox);
        dbRow.add(nameLabel);
        dbRow.add(closeBtn);

        // Store the path as client property so we can find and remove it later
        dbRow.putClientProperty("databasePath", databasePath);
        dbRow.putClientProperty("compareCheckbox", compareCheckBox);

        openDatabasesPanel.add(dbRow);
        openDatabasesPanel.revalidate();
        openDatabasesPanel.repaint();
        refreshCompareButtonState();
    }

    public void removeOpenDatabase(String databasePath) {
        for (Component comp : openDatabasesPanel.getComponents()) {
            if (comp instanceof JPanel) {
                JPanel row = (JPanel) comp;
                if (databasePath.equals(row.getClientProperty("databasePath"))) {
                    openDatabasesPanel.remove(row);
                    break;
                }
            }
        }
        openDatabasesPanel.revalidate();
        openDatabasesPanel.repaint();
        refreshCompareButtonState();
    }

    public List<String> getSelectedDatabasePaths() {
        List<String> selected = new ArrayList<>();
        for (Component comp : openDatabasesPanel.getComponents()) {
            if (!(comp instanceof JPanel)) {
                continue;
            }
            JPanel row = (JPanel) comp;
            Object checkboxObj = row.getClientProperty("compareCheckbox");
            Object pathObj = row.getClientProperty("databasePath");
            if (checkboxObj instanceof JCheckBox && pathObj instanceof String) {
                JCheckBox checkbox = (JCheckBox) checkboxObj;
                if (checkbox.isSelected()) {
                    selected.add((String) pathObj);
                }
            }
        }
        return selected;
    }

    private void refreshCompareButtonState() {
        if (compareSelectedButton == null) {
            return;
        }
        compareSelectedButton.setEnabled(getSelectedDatabasePaths().size() >= 2);
    }

    // Keep the old signature but adapt or remove it eventually
    public void setDatabaseOpen(boolean isOpen) {
        // Obsolete: We now handle multiple databases via addOpenDatabase / removeOpenDatabase
    }

    /**
     * Show loading state with greyed button and message
     */
    public void showLoading(String filePath) {
        openButton.setEnabled(false);
        openButton.setBackground(new Color(156, 163, 175)); // Grey
        openButton.repaint();

        loadingMessage.setText("Opening database " + filePath);
        loadingMessage.setVisible(true);
    }

    /**
     * Hide loading state
     */
    public void hideLoading() {
        loadingMessage.setVisible(false);
        openButton.setEnabled(true);
        openButton.setBackground(new Color(37, 99, 235));
        openButton.repaint();
    }
}
