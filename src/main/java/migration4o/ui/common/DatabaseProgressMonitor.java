package migration4o.ui.common;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import migration4o.database.DODatabaseMonitor;

/**
 * Professional UI implementation of DODatabaseMonitor with visual phase
 * indicators and detailed progress tracking.
 */
public class DatabaseProgressMonitor extends DODatabaseMonitor {

    private JDialog dialog;
    private JLabel overallStatusLabel;
    private JProgressBar overallProgressBar;

    // Phase panels
    private PhasePanel encodingPhase;
    private PhasePanel schemaReadPhase;
    private PhasePanel conversionPhase;
    private PhasePanel deduplicationPhase;

    // Statistics
    private JLabel statsLabel;
    private int totalClasses;
    private int processedClasses;
    private int totalLeafClasses;
    private int processedLeafClasses;
    private int warningCount = 0;
    private int errorCount = 0;

    /**
     * Creates a new database progress monitor with a professional visual UI
     */
    public DatabaseProgressMonitor(java.awt.Frame parent, String title) {
        createDialog(parent, title);
    }

    private void createDialog(java.awt.Frame parent, String title) {
        dialog = new JDialog(parent, title, true);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        mainPanel.setBackground(Color.WHITE);

        // Header with overall status
        JPanel headerPanel = createHeaderPanel();
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Center panel with phase indicators
        JPanel phasesPanel = createPhasesPanel();
        mainPanel.add(phasesPanel, BorderLayout.CENTER);

        // Footer with statistics
        JPanel footerPanel = createFooterPanel();
        mainPanel.add(footerPanel, BorderLayout.SOUTH);

        dialog.setContentPane(mainPanel);
        dialog.setPreferredSize(new Dimension(700, 600));
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 0, new Color(220, 220, 220)),
                new EmptyBorder(0, 0, 15, 0)));

        overallStatusLabel = new JLabel("Initializing database reader...");
        overallStatusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        overallStatusLabel.setForeground(new Color(50, 50, 50));
        panel.add(overallStatusLabel, BorderLayout.NORTH);

        overallProgressBar = new JProgressBar();
        overallProgressBar.setIndeterminate(true);
        overallProgressBar.setPreferredSize(new Dimension(600, 30));
        overallProgressBar.setStringPainted(true);
        overallProgressBar.setFont(new Font("Arial", Font.PLAIN, 11));
        panel.add(overallProgressBar, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createPhasesPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Color.WHITE);

        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.setBorder(null);
        scrollPane.setBackground(Color.WHITE);
        scrollPane.getViewport().setBackground(Color.WHITE);

        // Create phase panels
        encodingPhase = new PhasePanel("Phase 1: Database Opening", "Testing encoding configurations...");
        schemaReadPhase = new PhasePanel("Phase 2: Schema Discovery", "Reading class metadata...");
        conversionPhase = new PhasePanel("Phase 3: Class Conversion", "Converting DB4O classes to schema...");
        deduplicationPhase = new PhasePanel("Phase 4: Object Deduplication",
                "Removing duplicate IDs from inheritance hierarchies...");

        panel.add(encodingPhase);
        panel.add(Box.createVerticalStrut(10));
        panel.add(schemaReadPhase);
        panel.add(Box.createVerticalStrut(10));
        panel.add(conversionPhase);
        panel.add(Box.createVerticalStrut(10));
        panel.add(deduplicationPhase);
        panel.add(Box.createVerticalGlue());

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(Color.WHITE);
        wrapper.add(scrollPane, BorderLayout.CENTER);

        return wrapper;
    }

    private JPanel createFooterPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(2, 0, 0, 0, new Color(220, 220, 220)),
                new EmptyBorder(15, 0, 0, 0)));

        statsLabel = new JLabel("Preparing to read database...");
        statsLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        statsLabel.setForeground(new Color(100, 100, 100));
        panel.add(statsLabel);

        return panel;
    }

    /**
     * Shows the progress dialog
     */
    public void show() {
        SwingUtilities.invokeLater(() -> {
            if (!dialog.isVisible()) {
                dialog.setVisible(true);
            }
        });
    }

    /**
     * Hides the progress dialog
     */
    public void hide() {
        SwingUtilities.invokeLater(() -> {
            dialog.setVisible(false);
            dialog.dispose();
        });
    }

    private void updateOverallStatus(String message) {
        SwingUtilities.invokeLater(() -> overallStatusLabel.setText(message));
    }

    private void updateOverallProgress(int current, int total) {
        SwingUtilities.invokeLater(() -> {
            if (total > 0) {
                overallProgressBar.setIndeterminate(false);
                overallProgressBar.setMaximum(total);
                overallProgressBar.setValue(current);
                overallProgressBar.setString(String.format("%d / %d (%.1f%%)", current, total,
                        (current * 100.0 / total)));
            }
        });
    }

    private void updateStats() {
        SwingUtilities.invokeLater(() -> {
            StringBuilder stats = new StringBuilder();
            stats.append("Classes: ").append(processedClasses).append("/").append(totalClasses);
            if (warningCount > 0 || errorCount > 0) {
                stats.append(" │ ");
                if (warningCount > 0)
                    stats.append("⚠ ").append(warningCount).append(" warnings");
                if (warningCount > 0 && errorCount > 0)
                    stats.append(", ");
                if (errorCount > 0)
                    stats.append("✗ ").append(errorCount).append(" errors");
            }
            statsLabel.setText(stats.toString());
        });
    }

    // ===== Database Opening Methods =====

    @Override
    public void onTryingEncoding(String encodingDescription) {
        updateOverallStatus("Trying encoding: " + encodingDescription);
        encodingPhase.setStatus(PhaseStatus.IN_PROGRESS);
        encodingPhase.addDetail("→ Attempting: " + encodingDescription);
    }

    @Override
    public void onEncodingFailed(String encodingDescription, String errorType) {
        encodingPhase.addDetail("  ✗ Failed (" + errorType + ")");
    }

    @Override
    public void onDatabaseOpened(String encodingDescription) {
        updateOverallStatus("Database opened successfully");
        encodingPhase.setStatus(PhaseStatus.COMPLETED);
        encodingPhase.setDetail("✓ Opened with: " + encodingDescription);
    }

    @Override
    public void onDatabaseOpenFailed(String errorMessage) {
        updateOverallStatus("Failed to open database");
        encodingPhase.setStatus(PhaseStatus.ERROR);
        encodingPhase.setDetail("✗ " + errorMessage);
        errorCount++;
        updateStats();
    }

    @Override
    public void onServiceDatabaseOpened(String databasePath) {
        updateOverallStatus("Database ready");
        encodingPhase.addDetail("✓ Service opened: " + databasePath);
    }

    @Override
    public void onServiceDatabaseClosed(String databasePath) {
        encodingPhase.addDetail("✓ Service closed: " + databasePath);
    }

    @Override
    public void onServiceDatabaseCloseFailed(String databasePath, String errorMessage) {
        encodingPhase.addDetail("✗ Close failed for " + databasePath + ": " + errorMessage);
        warningCount++;
        updateStats();
    }

    // ===== Database Reading Methods =====

    @Override
    public void onStartingSchemaRead(int totalClasses) {
        this.totalClasses = totalClasses;
        this.processedClasses = 0;
        updateOverallStatus("Reading database schema (" + totalClasses + " classes)");
        schemaReadPhase.setStatus(PhaseStatus.IN_PROGRESS);
        schemaReadPhase.setDetail("Discovered " + totalClasses + " classes");
        updateStats();
    }

    @Override
    public void onCreatingDatabaseContext() {
        schemaReadPhase.addDetail("→ Creating database context");
    }

    @Override
    public void onConvertingClasses(int totalClasses) {
        this.totalClasses = totalClasses;
        this.processedClasses = 0;
        updateOverallStatus("Converting classes to schema format");
        schemaReadPhase.setStatus(PhaseStatus.COMPLETED);
        conversionPhase.setStatus(PhaseStatus.IN_PROGRESS);
        conversionPhase.setDetail("Converting " + totalClasses + " classes...");
        updateOverallProgress(0, totalClasses);
    }

    @Override
    public void onConvertingClass(String className, int classIndex, int totalClasses) {
        String shortName = className.substring(className.lastIndexOf('.') + 1);
        updateOverallStatus("Converting class " + classIndex + "/" + totalClasses + ": " + shortName);
        conversionPhase.setDetail("Converting: " + shortName + " (" + classIndex + "/" + totalClasses + ")");
        updateOverallProgress(classIndex, totalClasses);
    }

    @Override
    public void onRetrievingObjectIds(String className, int objectCount) {
        String shortName = className.substring(className.lastIndexOf('.') + 1);
        if (objectCount > 10000) {
            conversionPhase.addDetail(
                    "  → Retrieving " + objectCount + " object IDs from " + shortName + " (may take time)...");
        }
    }

    @Override
    public void onObjectIdsRetrieved(String className, int idCount) {
        // Callback complete, no additional UI needed
    }

    @Override
    public void onClassConverted(String className, int fieldCount) {
        this.processedClasses++;
        updateStats();
    }

    @Override
    public void onClassConversionWarning(String className, String errorMessage) {
        warningCount++;
        String shortName = className.substring(className.lastIndexOf('.') + 1);
        conversionPhase.addDetail("⚠ " + shortName + ": " + errorMessage);
        updateStats();
    }

    @Override
    public void onConvertingFields(String className, int fieldCount) {
        // Handled by onClassConverted
    }

    @Override
    public void onFieldConversionWarning(String className, String fieldName, String errorMessage) {
        warningCount++;
        updateStats();
    }

    @Override
    public void onFieldConversionError(String className, String errorMessage) {
        errorCount++;
        String shortName = className.substring(className.lastIndexOf('.') + 1);
        conversionPhase.addDetail("✗ " + shortName + ": " + errorMessage);
        updateStats();
    }

    @Override
    public void onCreatingModules(int moduleCount) {
        conversionPhase.addDetail("→ Creating " + moduleCount + " module(s)");
    }

    @Override
    public void onCreatingSchema(int classCount) {
        conversionPhase.addDetail("→ Assembling schema with " + classCount + " classes");
    }

    @Override
    public void onStartingDeduplication(int totalLeafClasses) {
        this.totalLeafClasses = totalLeafClasses;
        this.processedLeafClasses = 0;
        updateOverallStatus("Deduplicating object IDs");
        conversionPhase.setStatus(PhaseStatus.COMPLETED);
        deduplicationPhase.setStatus(PhaseStatus.IN_PROGRESS);
        deduplicationPhase.setDetail("Processing " + totalLeafClasses + " leaf classes...");
        updateOverallProgress(0, totalLeafClasses);
    }

    @Override
    public void onProcessingLeafClass(String className, int leafIndex, int totalLeafClasses) {
        this.processedLeafClasses = leafIndex;
        String shortName = className.substring(className.lastIndexOf('.') + 1);
        updateOverallStatus("Deduplicating " + leafIndex + "/" + totalLeafClasses + ": " + shortName);
        deduplicationPhase.setDetail("Processing: " + shortName + " (" + leafIndex + "/" + totalLeafClasses + ")");
        updateOverallProgress(leafIndex, totalLeafClasses);
    }

    @Override
    public void onClassDeduplicated(String className, int removedCount, int remainingCount) {
        if (removedCount > 0) {
            String shortName = className.substring(className.lastIndexOf('.') + 1);
            deduplicationPhase
                    .addDetail("→ " + shortName + ": " + removedCount + " duplicates removed");
        }
    }

    @Override
    public void onDeduplicationComplete(int leafClasses, int totalRemoved) {
        deduplicationPhase.setStatus(PhaseStatus.COMPLETED);
        deduplicationPhase.setDetail("✓ Complete: " + totalRemoved + " duplicates removed from " + leafClasses
                + " leaf classes");
    }

    @Override
    public void onSchemaReadComplete(int totalClasses) {
        updateOverallStatus("✓ Schema created successfully!");
        updateOverallProgress(totalClasses, totalClasses);
        deduplicationPhase.setStatus(PhaseStatus.COMPLETED);

        // Final statistics
        StringBuilder finalStats = new StringBuilder();
        finalStats.append("✓ Complete: ").append(totalClasses).append(" classes processed");
        if (warningCount > 0 || errorCount > 0) {
            finalStats.append(" │ ");
            if (warningCount > 0)
                finalStats.append(warningCount).append(" warnings");
            if (warningCount > 0 && errorCount > 0)
                finalStats.append(", ");
            if (errorCount > 0)
                finalStats.append(errorCount).append(" errors");
        }
        statsLabel.setText(finalStats.toString());
    }

    @Override
    public void onSchemaReadError(String errorMessage) {
        updateOverallStatus("✗ Error reading schema");
        deduplicationPhase.setStatus(PhaseStatus.ERROR);
        deduplicationPhase.setDetail("✗ " + errorMessage);
        errorCount++;
        updateStats();
    }

    // ===== Inner Classes =====

    private enum PhaseStatus {
        PENDING, IN_PROGRESS, COMPLETED, ERROR
    }

    /**
     * Visual panel representing a processing phase
     */
    private class PhasePanel extends JPanel {
        private JLabel titleLabel;
        private JLabel statusIconLabel;
        private JLabel detailLabel;
        private JPanel detailsPanel;
        private PhaseStatus status = PhaseStatus.PENDING;

        public PhasePanel(String title, String initialDetail) {
            setLayout(new BorderLayout(10, 5));
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
                    new EmptyBorder(12, 15, 12, 15)));

            // Header with title and status icon
            JPanel headerPanel = new JPanel(new BorderLayout(10, 0));
            headerPanel.setBackground(Color.WHITE);

            titleLabel = new JLabel(title);
            titleLabel.setFont(new Font("Arial", Font.BOLD, 12));
            titleLabel.setForeground(new Color(60, 60, 60));
            headerPanel.add(titleLabel, BorderLayout.CENTER);

            statusIconLabel = new JLabel("⏸");
            statusIconLabel.setFont(new Font("Arial", Font.PLAIN, 16));
            statusIconLabel.setForeground(new Color(180, 180, 180));
            statusIconLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            headerPanel.add(statusIconLabel, BorderLayout.EAST);

            add(headerPanel, BorderLayout.NORTH);

            // Detail label
            detailLabel = new JLabel(initialDetail);
            detailLabel.setFont(new Font("Arial", Font.PLAIN, 11));
            detailLabel.setForeground(new Color(100, 100, 100));
            add(detailLabel, BorderLayout.CENTER);

            // Details panel for additional messages
            detailsPanel = new JPanel();
            detailsPanel.setLayout(new BoxLayout(detailsPanel, BoxLayout.Y_AXIS));
            detailsPanel.setBackground(Color.WHITE);
            detailsPanel.setVisible(false);
            add(detailsPanel, BorderLayout.SOUTH);
        }

        public void setStatus(PhaseStatus newStatus) {
            this.status = newStatus;
            SwingUtilities.invokeLater(() -> {
                switch (status) {
                    case PENDING:
                        statusIconLabel.setText("⏸");
                        statusIconLabel.setForeground(new Color(180, 180, 180));
                        setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
                                new EmptyBorder(12, 15, 12, 15)));
                        break;
                    case IN_PROGRESS:
                        statusIconLabel.setText("⟳");
                        statusIconLabel.setForeground(new Color(33, 150, 243));
                        setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(new Color(33, 150, 243), 2),
                                new EmptyBorder(11, 14, 11, 14)));
                        break;
                    case COMPLETED:
                        statusIconLabel.setText("✓");
                        statusIconLabel.setForeground(new Color(76, 175, 80));
                        setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(new Color(76, 175, 80), 1),
                                new EmptyBorder(12, 15, 12, 15)));
                        break;
                    case ERROR:
                        statusIconLabel.setText("✗");
                        statusIconLabel.setForeground(new Color(244, 67, 54));
                        setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(new Color(244, 67, 54), 2),
                                new EmptyBorder(11, 14, 11, 14)));
                        break;
                }
            });
        }

        public void setDetail(String detail) {
            SwingUtilities.invokeLater(() -> {
                detailLabel.setText(detail);
                // Clear previous details when setting new main detail
                detailsPanel.removeAll();
                detailsPanel.setVisible(false);
            });
        }

        public void addDetail(String detail) {
            SwingUtilities.invokeLater(() -> {
                JLabel detailItemLabel = new JLabel(detail);
                detailItemLabel.setFont(new Font("Monospaced", Font.PLAIN, 10));
                detailItemLabel.setForeground(new Color(120, 120, 120));
                detailItemLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                detailsPanel.add(detailItemLabel);
                detailsPanel.setVisible(true);
                detailsPanel.revalidate();
            });
        }
    }
}
