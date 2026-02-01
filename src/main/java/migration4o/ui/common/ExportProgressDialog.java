package migration4o.ui.common;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * Dialog that displays real-time export progress with detailed status updates.
 * Implements DOExportMonitor to receive callbacks from export engine.
 */
public class ExportProgressDialog extends JDialog implements DOExportMonitor {
    private static final long serialVersionUID = 1L;
    
    // UI Components
    private JLabel titleLabel;
    private JLabel currentOperationLabel;
    private JProgressBar overallProgressBar;
    private JProgressBar classProgressBar;
    private JLabel overallStatsLabel;
    private JLabel classStatsLabel;
    private JLabel warningsLabel;
    private JTextArea logArea;
    private JButton cancelButton;
    
    // State
    private boolean cancelled = false;
    private int totalClasses = 0;
    private int completedClasses = 0;
    private int totalObjects = 0;
    private int exportedObjects = 0;
    private int warningCount = 0;
    private int errorCount = 0;
    private String currentClassName = "";
    
    /**
     * Creates an export progress dialog.
     * 
     * @param parent Parent frame
     * @param title Dialog title
     */
    public ExportProgressDialog(Frame parent, String title) {
        super(parent, title, false); // Non-modal to allow interaction
        initComponents();
        setSize(700, 550);
        setLocationRelativeTo(parent);
        
        // Handle window close = cancel
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cancel();
            }
        });
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
        titleLabel = new JLabel("Initializing export...");
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
        
        // Bottom: Cancel button
        JPanel bottomPanel = new JPanel();
        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> cancel());
        bottomPanel.add(cancelButton);
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    private void cancel() {
        cancelled = true;
        cancelButton.setEnabled(false);
        cancelButton.setText("Cancelling...");
        appendLog("CANCEL REQUESTED - Export will stop after current class\n");
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
                exportedObjects, warningCount, errorCount
            ));
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
            cancelButton.setText("Close");
            cancelButton.setEnabled(true);
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
            cancelButton.setText("Close");
            cancelButton.setEnabled(true);
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
        this.currentClassName = simpleName;
        this.totalObjects = objectCount;
        
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
                completedClasses, totalClasses
            ));
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
