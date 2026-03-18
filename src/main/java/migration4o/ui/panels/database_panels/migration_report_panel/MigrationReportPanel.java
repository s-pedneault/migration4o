package migration4o.ui.panels.database_panels.migration_report_panel;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.SwingConstants;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * Panel that displays real-time export progress with detailed status updates. Replaces ExportProgressDialog and is embedded in the Database tab. Implements DOExportMonitor to receive callbacks from export engine.
 */
public class MigrationReportPanel extends JPanel implements DOExportMonitor {

    // UI Components
    private JPanel northContainer;
    private JLabel successLabel;
    private JLabel titleLabel;
    private JProgressBar overallProgressBar;
    private JLabel overallStatsLabel;
    private JPanel formatRowsPanel;
    private int formatRowsNextRow = 0;
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

    // Per-format progress tracking
    private final Map<String, FormatRow> formatRows = new LinkedHashMap<>();
    private final List<String> seenFormatList = new ArrayList<>();

    /** Holds UI widgets for one export format's compact progress row. */
    private static class FormatRow {
        final JLabel infoLabel;
        final JProgressBar progressBar;

        FormatRow(JLabel info, JProgressBar bar) {
            this.infoLabel = info;
            this.progressBar = bar;
        }
    }

    public MigrationReportPanel() {
        initComponents();
        showEmptyState();
    }

    /**
     * Shows a message when no export has been run yet.
     */
    private void showEmptyState() {
        logArea.setText("Migration Report\n\nNo export has been run yet.\nUse the Migration structure tab in the Schema section to configure and run an export.\n\nThis panel will show real-time progress when an export is running.");
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

        // Per-format progress rows (compact two-column table: info | bar)
        formatRowsPanel = new JPanel(new GridBagLayout());
        formatRowsPanel.setBorder(BorderFactory.createTitledBorder("Class Progress"));
        gbc.gridy = 3;
        gbc.insets = new Insets(4, 0, 2, 0);
        topPanel.add(formatRowsPanel, gbc);

        // Stats panel
        JPanel statsPanel = new JPanel();
        warningsLabel = new JLabel("Exported: 0 | Warnings: 0 | Errors: 0");
        statsPanel.add(warningsLabel);
        gbc.gridy = 4;
        gbc.insets = new Insets(5, 0, 0, 0);
        topPanel.add(statsPanel, gbc);

        JPanel successPanel = createSuccessPanel();
        northContainer = new JPanel(new CardLayout());
        northContainer.add(topPanel, "progress");
        northContainer.add(successPanel, "success");
        add(northContainer, BorderLayout.NORTH);

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

    private JPanel createSuccessPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(198, 239, 206));
        panel.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));

        successLabel = new JLabel("Export successful!", SwingConstants.CENTER);
        successLabel.setFont(successLabel.getFont().deriveFont(Font.BOLD, 22f));
        successLabel.setForeground(new Color(0, 97, 0));
        panel.add(successLabel, BorderLayout.CENTER);

        return panel;
    }

    private void clearLog() {
        logArea.setText("");
        showEmptyState();
    }

    /**
     * Creates and registers a new format progress row. Must be called on the EDT.
     */
    private FormatRow createFormatRow(String formatName) {
        JLabel infoLabel = new JLabel();
        infoLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));

        JProgressBar bar = new JProgressBar(0, 1);
        bar.setStringPainted(true);
        bar.setPreferredSize(new Dimension(0, 18));

        GridBagConstraints rowGbc = new GridBagConstraints();
        rowGbc.gridy = formatRowsNextRow++;
        rowGbc.insets = new Insets(2, 4, 2, 4);
        rowGbc.anchor = GridBagConstraints.WEST;
        rowGbc.fill = GridBagConstraints.HORIZONTAL;

        rowGbc.gridx = 0;
        rowGbc.weightx = 0.5;
        formatRowsPanel.add(infoLabel, rowGbc);

        rowGbc.gridx = 1;
        rowGbc.weightx = 0.5;
        formatRowsPanel.add(bar, rowGbc);

        formatRowsPanel.revalidate();
        formatRowsPanel.repaint();

        FormatRow row = new FormatRow(infoLabel, bar);
        formatRows.put(formatName, row);
        return row;
    }

    private static String buildInfoText(String formatName, String simpleName, int current, int total) {
        String badge = formatName.isEmpty() ? "[---]" : String.format("[%-4s]", formatName);
        return String.format("%s %s    %,d / %,d", badge, simpleName, current, total);
    }

    /**
     * Resets only the row for the format that is starting a new class. Other format rows keep their last state until that format itself starts a new class — otherwise the XML row wipes the HTML row's in-progress display before the timer can paint it.
     */
    private void resetFormatRow(String formatName) {
        FormatRow row = formatRows.get(formatName);
        if (row != null) {
            row.progressBar.setValue(0);
            String badge = formatName.isEmpty() ? "[---]" : String.format("[%-4s]", formatName);
            row.infoLabel.setText(badge + " —");
        }
    }

    private void appendLog(String message) {
        logArea.append(message);
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void updateStats() {
        warningsLabel.setText(String.format("Exported: %,d | Warnings: %,d | Errors: %,d", exportedObjects, warningCount, errorCount));
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

        logArea.setText("");
        titleLabel.setText("Ready to export");
        overallStatsLabel.setText("Overall Progress: 0 / 0 classes");
        overallProgressBar.setValue(0);
        seenFormatList.clear();
        formatRows.clear();
        formatRowsNextRow = 0;
        formatRowsPanel.removeAll();
        formatRowsPanel.revalidate();
        formatRowsPanel.repaint();
        updateStats();
        ((CardLayout) northContainer.getLayout()).show(northContainer, "progress");
    }

    // ========== DOExportMonitor Implementation ==========

    @Override
    public void onExportStart(String exportName, int totalClasses) {
        this.totalClasses = totalClasses;
        this.completedClasses = 0;
        this.exportedObjects = 0;
        this.warningCount = 0;
        this.errorCount = 0;

        titleLabel.setText("Exporting: " + exportName);
        overallStatsLabel.setText(String.format("Overall Progress: 0 / %,d classes", totalClasses));
        overallProgressBar.setValue(0);
        overallProgressBar.setMaximum(totalClasses);
        seenFormatList.clear();
        formatRows.clear();
        formatRowsNextRow = 0;
        formatRowsPanel.removeAll();
        formatRowsPanel.revalidate();
        formatRowsPanel.repaint();
        updateStats();

        ((CardLayout) northContainer.getLayout()).show(northContainer, "progress");
        appendLog(String.format("=== EXPORT STARTED: %s ===\n", exportName));
        appendLog(String.format("Total classes to export: %,d\n\n", totalClasses));
    }

    @Override
    public void onExportComplete(String exportName, int objectsExported, int warnings) {
        appendLog(String.format("\n=== EXPORT COMPLETED ===\n"));
        appendLog(String.format("Total objects exported: %,d\n", objectsExported));
        appendLog(String.format("Total warnings: %,d\n", warnings));
        appendLog(String.format("Total errors: %,d\n", errorCount));

        if (errorCount == 0) {
            String statsText = String.format("%,d objects exported", objectsExported);
            if (warnings > 0) {
                statsText += String.format(", %,d warning%s", warnings, warnings == 1 ? "" : "s");
            }
            successLabel.setText(String.format("<html><center><b>Export successful!</b><br><span style='font-size:10px'>%s</span></center></html>", statsText));
            ((CardLayout) northContainer.getLayout()).show(northContainer, "success");
        } else {
            titleLabel.setText("Export complete with errors: " + exportName);
        }
    }

    @Override
    public void onExportError(String exportName, String error) {
        errorCount++;
        updateStats();
        titleLabel.setText("Export Failed: " + exportName);

        appendLog(String.format("\n!!! EXPORT FAILED !!!\n"));
        appendLog(String.format("Error: %s\n", error));
    }

    @Override
    public void onModuleStart(String moduleName, int classCount, int depth) {
        String indent = "  ".repeat(depth);
        appendLog(String.format("%s▶ Module: %s (%,d classes)\n", indent, moduleName, classCount));
    }

    @Override
    public void onModuleComplete(String moduleName) {
        // Optional: track module completion
    }

    @Override
    public void onClassStart(String className, String simpleName, int objectCount, String formatName) {
        SwingUtilities.invokeLater(() -> {
            if (!formatName.isEmpty() && !seenFormatList.contains(formatName)) {
                seenFormatList.add(formatName);
                overallProgressBar.setMaximum(totalClasses * seenFormatList.size());
            }
            FormatRow row = formatRows.get(formatName);
            if (row == null) {
                row = createFormatRow(formatName);
            }
            row.infoLabel.setText(buildInfoText(formatName, simpleName, 0, objectCount));
            row.progressBar.setValue(0);
            row.progressBar.setMaximum(Math.max(objectCount, 1));
        });
        appendLog(String.format("  → [%s] %s (%,d objects)... ", formatName, simpleName, objectCount));
    }

    @Override
    public void onClassComplete(String className, int objectsExported, String formatName) {
        completedClasses++;
        final int cc = completedClasses;
        SwingUtilities.invokeLater(() -> {
            int numFormats = Math.max(1, seenFormatList.size());
            int total = totalClasses * numFormats;
            int clamped = Math.min(cc, total);
            overallStatsLabel.setText(String.format("Overall Progress: %,d / %,d classes", clamped, total));
            overallProgressBar.setValue(clamped);
            resetFormatRow(formatName);
        });

        appendLog(String.format("✓ (%,d objects)\n", objectsExported));
    }

    @Override
    public void onObjectProgress(String className, String simpleName, int current, int total, String formatName) {
        // simpleName is passed directly from the exporter at call time — never stale.
        // invokeLater ensures this runs AFTER onClassStart's invokeLater (same EDT queue, FIFO),
        // so the row is guaranteed to exist when we look it up.
        SwingUtilities.invokeLater(() -> {
            FormatRow row = formatRows.get(formatName);
            if (row != null) {
                row.infoLabel.setText(buildInfoText(formatName, simpleName, current, total));
                row.progressBar.setMaximum(Math.max(total, 1));
                row.progressBar.setValue(Math.min(current, total));
            }
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
        appendLog(String.format("\nGenerating XSD schema: %s\n", schemaPath));
    }

    @Override
    public void onXSDGenerationComplete(String schemaPath) {
        appendLog("  ✓ XSD schema generated\n");
    }

    @Override
    public void onStatusMessage(String message) {
        appendLog(message + "\n");
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }
}
