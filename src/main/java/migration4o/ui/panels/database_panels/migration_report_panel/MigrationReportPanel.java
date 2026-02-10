package migration4o.ui.panels.database_panels.migration_report_panel;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import migration4o.ui.common.DOExportMonitor;

/**
 * Panel that displays real-time export progress with detailed status updates.
 * Replaces ExportProgressDialog and is embedded in the Database tab.
 * Implements DOExportMonitor to receive callbacks from export engine.
 */
public class MigrationReportPanel extends JPanel implements DOExportMonitor {

    // UI Components
    private JLabel titleLabel;
    private JLabel currentOperationLabel;
    private JProgressBar overallProgressBar;
    private JProgressBar classProgressBar;
    private JLabel overallStatsLabel;
    private JLabel classStatsLabel;
    private JLabel warningsLabel;
    private JTextArea logArea;
    private JButton clearButton;

    // State
    private boolean cancelled = false;
    private int totalClasses = 0;
    private int completedClasses = 0;
    private int exportedObjects = 0;
    private int warningCount = 0;
    private int errorCount = 0;

    public MigrationReportPanel() {
        initComponents();
        showEmptyState();
    }

    /**
     * Shows a message when no export has been run yet.
     */
    private void showEmptyState() {
        logArea.setText(
                "Migration Report\n\nNo export has been run yet.\nUse the Migration structure tab in the Schema section to configure and run an export.\n\nThis panel will show real-time progress when an export is running.");
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));

        // Top panel with title and main progress
        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridx = 0;
        gbc.insets = new Insets(2, 0, 2, 0);

        // Title
        titleLabel = new JLabel("Ready to export");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        gbc.gridy = 0;
        topPanel.add(titleLabel, gbc);

        // Overall progress label
        overallStatsLabel = new JLabel("Overall Progress: 0 / 0 classes");
        gbc.gridy = 1;
        topPanel.add(overallStatsLabel, gbc);

        // Overall progress bar
        overallProgressBar = new JProgressBar(0, 100);
        overallProgressBar.setStringPainted(true);
        overallProgressBar.setPreferredSize(new Dimension(600, 25));
        gbc.gridy = 2;
        gbc.insets = new Insets(5, 0, 10, 0);
        topPanel.add(overallProgressBar, gbc);

        // Current operation label
        currentOperationLabel = new JLabel(" ");
        currentOperationLabel.setFont(currentOperationLabel.getFont().deriveFont(Font.ITALIC));
        gbc.gridy = 3;
        gbc.insets = new Insets(5, 0, 2, 0);
        topPanel.add(currentOperationLabel, gbc);

        // Class progress label
        classStatsLabel = new JLabel("Class Progress: 0 / 0 objects");
        gbc.gridy = 4;
        gbc.insets = new Insets(2, 0, 2, 0);
        topPanel.add(classStatsLabel, gbc);

        // Class progress bar
        classProgressBar = new JProgressBar(0, 100);
        classProgressBar.setStringPainted(true);
        classProgressBar.setPreferredSize(new Dimension(600, 20));
        gbc.gridy = 5;
        gbc.insets = new Insets(2, 0, 5, 0);
        topPanel.add(classProgressBar, gbc);

        // Stats panel
        JPanel statsPanel = new JPanel();
        warningsLabel = new JLabel("Exported: 0 | Warnings: 0 | Errors: 0");
        statsPanel.add(warningsLabel);
        gbc.gridy = 6;
        gbc.insets = new Insets(5, 0, 0, 0);
        topPanel.add(statsPanel, gbc);

        add(topPanel, BorderLayout.NORTH);

        // Center: Log area
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Export Log"));
        add(scrollPane, BorderLayout.CENTER);

        // Bottom: Clear button
        JPanel bottomPanel = new JPanel();
        clearButton = new JButton("Clear Log");
        clearButton.addActionListener(e -> clearLog());
        bottomPanel.add(clearButton);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void clearLog() {
        logArea.setText("");
        showEmptyState();
    }

    private void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message);
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void updateStats() {
        SwingUtilities.invokeLater(() -> {
            warningsLabel.setText(String.format(
                    "Exported: %,d | Warnings: %,d | Errors: %,d",
                    exportedObjects, warningCount, errorCount));
        });
    }

    /**
     * Resets the panel state before a new export.
     */
    public void reset() {
        cancelled = false;
        totalClasses = 0;
        completedClasses = 0;
        exportedObjects = 0;
        warningCount = 0;
        errorCount = 0;

        SwingUtilities.invokeLater(() -> {
            logArea.setText("");
            titleLabel.setText("Ready to export");
            currentOperationLabel.setText(" ");
            overallStatsLabel.setText("Overall Progress: 0 / 0 classes");
            classStatsLabel.setText("Class Progress: 0 / 0 objects");
            overallProgressBar.setValue(0);
            classProgressBar.setValue(0);
            updateStats();
        });
    }

    // ========== DOExportMonitor Implementation ==========

    @Override
    public void onExportStart(String exportName, int totalClasses) {
        this.totalClasses = totalClasses;
        this.completedClasses = 0;
        this.exportedObjects = 0;
        this.warningCount = 0;
        this.errorCount = 0;

        SwingUtilities.invokeLater(() -> {
            titleLabel.setText("Exporting: " + exportName);
            overallStatsLabel.setText(String.format("Overall Progress: 0 / %,d classes", totalClasses));
            overallProgressBar.setValue(0);
            overallProgressBar.setMaximum(totalClasses);
            classProgressBar.setValue(0);
            updateStats();
        });

        appendLog(String.format("=== EXPORT STARTED: %s ===\n", exportName));
        appendLog(String.format("Total classes to export: %,d\n\n", totalClasses));
    }

    @Override
    public void onExportComplete(String exportName, int objectsExported, int warnings) {
        SwingUtilities.invokeLater(() -> {
            titleLabel.setText("Export Complete: " + exportName);
            currentOperationLabel.setText("Export finished successfully");
        });

        appendLog(String.format("\n=== EXPORT COMPLETED ===\n"));
        appendLog(String.format("Total objects exported: %,d\n", objectsExported));
        appendLog(String.format("Total warnings: %,d\n", warnings));
        appendLog(String.format("Total errors: %,d\n", errorCount));
    }

    @Override
    public void onExportError(String exportName, String error) {
        errorCount++;
        updateStats();

        SwingUtilities.invokeLater(() -> {
            titleLabel.setText("Export Failed: " + exportName);
            currentOperationLabel.setText("ERROR: " + error);
        });

        appendLog(String.format("\n!!! EXPORT FAILED !!!\n"));
        appendLog(String.format("Error: %s\n", error));
    }

    @Override
    public void onModuleStart(String moduleName, int classCount, int depth) {
        String indent = "  ".repeat(depth);
        appendLog(String.format("%s▶ Module: %s (%,d classes)\n", indent, moduleName, classCount));

        SwingUtilities.invokeLater(() -> {
            currentOperationLabel.setText("Processing module: " + moduleName);
        });
    }

    @Override
    public void onModuleComplete(String moduleName) {
        // Optional: track module completion
    }

    @Override
    public void onClassStart(String className, String simpleName, int objectCount) {
        SwingUtilities.invokeLater(() -> {
            currentOperationLabel.setText("Exporting: " + simpleName);
            classStatsLabel.setText(String.format("Class Progress: 0 / %,d objects", objectCount));
            classProgressBar.setValue(0);
            classProgressBar.setMaximum(Math.max(objectCount, 1));
        });

        appendLog(String.format("  → %s (%,d objects)... ", simpleName, objectCount));
    }

    @Override
    public void onClassComplete(String className, int objectsExported) {
        completedClasses++;

        SwingUtilities.invokeLater(() -> {
            overallStatsLabel.setText(String.format(
                    "Overall Progress: %,d / %,d classes",
                    completedClasses, totalClasses));
            overallProgressBar.setValue(completedClasses);
        });

        appendLog(String.format("✓ (%,d objects)\n", objectsExported));
    }

    @Override
    public void onObjectProgress(String className, int current, int total) {
        SwingUtilities.invokeLater(() -> {
            classStatsLabel.setText(String.format("Class Progress: %,d / %,d objects", current, total));
            classProgressBar.setValue(current);
        });
    }

    @Override
    public void onObjectExported(String className, long objectId) {
        exportedObjects++;

        // Update every 100 objects to avoid UI overhead
        if (exportedObjects % 100 == 0) {
            updateStats();
        }
    }

    @Override
    public void onObjectError(String className, long objectId, String error) {
        errorCount++;
        updateStats();
        appendLog(String.format("    ERROR: Object %d - %s\n", objectId, error));
    }

    @Override
    public void onWarning(String warningType, String className, String message) {
        warningCount++;

        // Update every 100 warnings to avoid UI overhead
        if (warningCount % 100 == 0) {
            updateStats();
        }

        // Only log first few warnings of each type to avoid flooding
        if (warningCount <= 10) {
            appendLog(String.format("    WARNING [%s]: %s\n", warningType, message));
        } else if (warningCount == 11) {
            appendLog("    (further warnings suppressed in log)\n");
        }
    }

    @Override
    public void onXSDGenerationStart(String schemaPath) {
        SwingUtilities.invokeLater(() -> {
            currentOperationLabel.setText("Generating XSD schema...");
        });
        appendLog(String.format("\nGenerating XSD schema: %s\n", schemaPath));
    }

    @Override
    public void onXSDGenerationComplete(String schemaPath) {
        appendLog("  ✓ XSD schema generated\n");
    }

    @Override
    public void onStatusMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            currentOperationLabel.setText(message);
        });
        appendLog(message + "\n");
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }
}
