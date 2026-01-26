package migration4o.ui.panels.welcome_panel;

import javax.swing.*;
import java.awt.*;
import java.net.URL;

/**
 * Welcome screen panel with logo and database open/close functionality.
 */
public class WelcomePanel extends JPanel {

    private JButton databaseButton;
    private JLabel loadingMessage;
    private Runnable onOpenDatabase;
    private Runnable onCloseDatabase;
    private boolean isDatabaseOpen = false;

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
        databaseButton = new JButton("Open database...");
        databaseButton.setFont(new Font("Arial", Font.BOLD, 16));
        databaseButton.setPreferredSize(new Dimension(200, 50));
        databaseButton.setBackground(new Color(37, 99, 235));
        databaseButton.setForeground(Color.WHITE);
        databaseButton.setFocusPainted(false);
        databaseButton.setOpaque(true);
        databaseButton.setBorderPainted(false);
        databaseButton.addActionListener(e -> handleButtonClick());
        add(databaseButton, gbc);

        // Add loading message (initially hidden)
        gbc.gridy = 3;
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

    private void handleButtonClick() {
        if (isDatabaseOpen) {
            if (onCloseDatabase != null) {
                onCloseDatabase.run();
            }
        } else {
            if (onOpenDatabase != null) {
                onOpenDatabase.run();
            }
        }
    }

    public void setOnOpenDatabase(Runnable callback) {
        this.onOpenDatabase = callback;
    }

    public void setOnCloseDatabase(Runnable callback) {
        this.onCloseDatabase = callback;
    }

    public void setDatabaseOpen(boolean isOpen) {
        this.isDatabaseOpen = isOpen;
        if (isOpen) {
            databaseButton.setText("Close Database");
            databaseButton.setBackground(new Color(220, 38, 38));
        } else {
            databaseButton.setText("Open database...");
            databaseButton.setBackground(new Color(37, 99, 235));
        }
        databaseButton.repaint();
    }

    /**
     * Show loading state with greyed button and message
     */
    public void showLoading(String filePath) {
        databaseButton.setEnabled(false);
        databaseButton.setBackground(new Color(156, 163, 175)); // Grey
        databaseButton.repaint();

        loadingMessage.setText("Opening database " + filePath);
        loadingMessage.setVisible(true);
    }

    /**
     * Hide loading state
     */
    public void hideLoading() {
        loadingMessage.setVisible(false);
        databaseButton.setEnabled(true);
    }
}
