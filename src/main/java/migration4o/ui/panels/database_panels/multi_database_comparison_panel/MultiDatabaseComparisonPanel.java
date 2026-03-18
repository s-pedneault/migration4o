package migration4o.ui.panels.database_panels.multi_database_comparison_panel;

import java.awt.BorderLayout;
import java.awt.Font;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

import migration4o.database.DODatabaseContext;
import migration4o.migration.monitoring.ExportStatistics;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.migration.recipes.IDEntityHandler;
import migration4o.ui.main.MainWindow;
import migration4o.util.SchemaUtil;

public class MultiDatabaseComparisonPanel extends JPanel {

    private enum ComparisonMode {
        ID_COUNTERS("ID counters (count only)"), ENTITIES("Entities");

        private final String label;

        ComparisonMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final List<DODatabaseContext> contexts;
    private final Map<String, DODatabaseContext> contextByLabel = new LinkedHashMap<>();
    private final DefaultTableModel diffTableModel;
    private final JTable diffTable;
    private final JComboBox<ComparisonMode> modeSelector;
    private final JComboBox<String> leftSelector;
    private final JComboBox<String> rightSelector;
    private final JButton exportBothButton;
    private final JLabel exportStatusLabel;
    private final JTextArea leftOnlyArea;
    private final JTextArea rightOnlyArea;
    private final JLabel summaryLabel;
    private final Map<String, Map<String, Set<Long>>> exportedMidsCacheByDatabasePath = new HashMap<>();
    private final List<String> pendingExportPaths = new ArrayList<>();
    private String awaitingExportPath = null;
    private final MainWindow.ExportCompletionListener exportCompletionListener;

    private static class EntityComparisonData {
        final Map<String, Set<Long>> midsByClass = new HashMap<>();
    }

    public MultiDatabaseComparisonPanel(List<DODatabaseContext> contexts) {
        this.contexts = new ArrayList<>(contexts);
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel header = new JPanel(new BorderLayout(5, 5));
        JLabel titleLabel = new JLabel("Multi-database comparison workspace");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        header.add(titleLabel, BorderLayout.NORTH);

        JPanel topControls = new JPanel(new BorderLayout(10, 10));
        JPanel selectors = new JPanel();

        populateContextLabels();
        modeSelector = new JComboBox<>(ComparisonMode.values());
        modeSelector.setSelectedItem(ComparisonMode.ENTITIES);
        leftSelector = new JComboBox<>(contextByLabel.keySet().toArray(new String[0]));
        rightSelector = new JComboBox<>(contextByLabel.keySet().toArray(new String[0]));
        if (rightSelector.getItemCount() > 1) {
            rightSelector.setSelectedIndex(1);
        }

        selectors.add(new JLabel("Mode:"));
        selectors.add(modeSelector);
        selectors.add(new JLabel("Left:"));
        selectors.add(leftSelector);
        selectors.add(new JLabel("Right:"));
        selectors.add(rightSelector);

        summaryLabel = new JLabel();
        summaryLabel.setFont(new Font("Arial", Font.PLAIN, 12));

        JPanel exportPanel = new JPanel();
        exportBothButton = new JButton("Export selected pair");
        exportStatusLabel = new JLabel(" ");
        exportStatusLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        exportPanel.add(exportBothButton);
        exportPanel.add(exportStatusLabel);

        topControls.add(selectors, BorderLayout.NORTH);
        topControls.add(exportPanel, BorderLayout.CENTER);
        topControls.add(summaryLabel, BorderLayout.SOUTH);
        header.add(topControls, BorderLayout.SOUTH);

        add(header, BorderLayout.NORTH);

        diffTableModel = new DefaultTableModel(new String[] { "Class", "Left Count", "Right Count", "Delta", "Not Exported" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        diffTable = new JTable(diffTableModel);
        diffTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        leftOnlyArea = new JTextArea();
        leftOnlyArea.setEditable(false);
        leftOnlyArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        rightOnlyArea = new JTextArea();
        rightOnlyArea.setEditable(false);
        rightOnlyArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JSplitPane supplementalSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildSupplementalPanel("Only in Left", leftOnlyArea), buildSupplementalPanel("Only in Right", rightOnlyArea));
        supplementalSplit.setResizeWeight(0.5);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(diffTable), supplementalSplit);
        mainSplit.setResizeWeight(0.45);

        add(mainSplit, BorderLayout.CENTER);

        modeSelector.addActionListener(e -> refreshDiffs());
        leftSelector.addActionListener(e -> refreshDiffs());
        rightSelector.addActionListener(e -> refreshDiffs());
        exportBothButton.addActionListener(e -> exportSelectedPair());
        diffTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSupplementalForSelection();
            }
        });

        exportCompletionListener = (databasePath, result) -> SwingUtilities.invokeLater(() -> {
            exportedMidsCacheByDatabasePath.remove(databasePath);
            handleExportProgress(databasePath, result);
            refreshDiffs();
        });
        MainWindow.getInstance().addExportCompletionListener(exportCompletionListener);
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.DISPLAYABILITY_CHANGED) != 0 && !isDisplayable()) {
                MainWindow.getInstance().removeExportCompletionListener(exportCompletionListener);
            }
        });

        refreshDiffs();
    }

    public List<DODatabaseContext> getContexts() {
        return new ArrayList<>(contexts);
    }

    private void populateContextLabels() {
        contextByLabel.clear();
        int index = 1;
        for (DODatabaseContext context : contexts) {
            File file = new File(context.databaseFilePath);
            String parent = file.getParentFile() != null ? file.getParentFile().getName() : "";
            String label = index + " - " + parent + " / " + file.getName();
            contextByLabel.put(label, context);
            index++;
        }
    }

    private JPanel buildSupplementalPanel(String title, JTextArea textArea) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(new JScrollPane(textArea), BorderLayout.CENTER);
        return panel;
    }

    private void refreshDiffs() {
        diffTableModel.setRowCount(0);
        leftOnlyArea.setText("");
        rightOnlyArea.setText("");

        DODatabaseContext leftContext = getSelectedContext(leftSelector);
        DODatabaseContext rightContext = getSelectedContext(rightSelector);

        if (leftContext == null || rightContext == null) {
            summaryLabel.setText("Select two database contexts.");
            return;
        }
        if (leftContext == rightContext) {
            summaryLabel.setText("Select two different database contexts.");
            return;
        }

        updateExportStatusLabel(leftContext, rightContext);

        ComparisonMode mode = (ComparisonMode) modeSelector.getSelectedItem();
        if (mode == ComparisonMode.ID_COUNTERS) {
            Map<String, Integer> leftCounts = mapClassToObjectIdCount(leftContext != null ? leftContext.databaseSchema : null);
            Map<String, Integer> rightCounts = mapClassToObjectIdCount(rightContext != null ? rightContext.databaseSchema : null);

            Set<String> allClasses = new HashSet<>();
            allClasses.addAll(leftCounts.keySet());
            allClasses.addAll(rightCounts.keySet());

            List<String> sortedClasses = allClasses.stream().sorted().collect(Collectors.toList());
            for (String className : sortedClasses) {
                int leftCount = leftCounts.getOrDefault(className, 0);
                int rightCount = rightCounts.getOrDefault(className, 0);
                if (leftCount != rightCount) {
                    diffTableModel.addRow(new Object[] { className, leftCount, rightCount, leftCount - rightCount, "-" });
                }
            }
        } else {
            Map<String, Set<Long>> leftByClass = buildComparisonValues(leftContext, mode);
            Map<String, Set<Long>> rightByClass = buildComparisonValues(rightContext, mode);

            Set<String> allClasses = new HashSet<>();
            allClasses.addAll(leftByClass.keySet());
            allClasses.addAll(rightByClass.keySet());

            List<String> sortedClasses = allClasses.stream().sorted().collect(Collectors.toList());
            for (String className : sortedClasses) {
                int leftCount = leftByClass.containsKey(className) ? leftByClass.get(className).size() : 0;
                int rightCount = rightByClass.containsKey(className) ? rightByClass.get(className).size() : 0;
                if (leftCount != rightCount) {
                    Object notExportedDisplay = "-";
                    Set<Long> leftExported = getExportedMidsForClass(leftContext, className);
                    Set<Long> rightExported = getExportedMidsForClass(rightContext, className);
                    if (leftExported != null && rightExported != null) {
                        Set<Long> leftSet = leftByClass.getOrDefault(className, new LinkedHashSet<>());
                        Set<Long> rightSet = rightByClass.getOrDefault(className, new LinkedHashSet<>());
                        int notExportedCount = 0;
                        for (Long mid : leftSet) {
                            if (!rightSet.contains(mid) && !leftExported.contains(mid)) {
                                notExportedCount++;
                            }
                        }
                        for (Long mid : rightSet) {
                            if (!leftSet.contains(mid) && !rightExported.contains(mid)) {
                                notExportedCount++;
                            }
                        }
                        notExportedDisplay = notExportedCount;
                    } else {
                        notExportedDisplay = "?";
                    }

                    diffTableModel.addRow(new Object[] { className, leftCount, rightCount, leftCount - rightCount, notExportedDisplay });
                }
            }
        }

        summaryLabel.setText(mode + " - classes with different counts: " + diffTableModel.getRowCount());
        if (diffTableModel.getRowCount() > 0) {
            diffTable.setRowSelectionInterval(0, 0);
            showSupplementalForSelection();
        } else {
            leftOnlyArea.setText("No class count differences.");
            rightOnlyArea.setText("No class count differences.");
        }
    }

    private DODatabaseContext getSelectedContext(JComboBox<String> selector) {
        Object key = selector.getSelectedItem();
        if (key == null) {
            return null;
        }
        return contextByLabel.get(String.valueOf(key));
    }

    private Map<String, Set<Long>> buildComparisonValues(DODatabaseContext context, ComparisonMode mode) {
        if (mode == ComparisonMode.ENTITIES) {
            return buildEntityComparisonData(context).midsByClass;
        }
        return new HashMap<>();
    }

    private Map<String, Integer> mapClassToObjectIdCount(DOSchema schema) {
        Map<String, Integer> countsByClass = new HashMap<>();
        if (schema == null || schema.getClasses() == null) {
            return countsByClass;
        }

        for (DOSchemaClass schemaClass : schema.getClasses()) {
            String classKey = getClassKey(schemaClass);
            if (classKey == null) {
                continue;
            }
            int count = schemaClass.uniqueObjectIds != null ? schemaClass.uniqueObjectIds.length : 0;
            countsByClass.put(classKey, count);
        }
        return countsByClass;
    }

    private EntityComparisonData buildEntityComparisonData(DODatabaseContext context) {
        EntityComparisonData data = new EntityComparisonData();
        if (context == null || context.databaseSchema == null || context.databaseSchema.getClasses() == null || context.container == null) {
            return data;
        }

        DOSchema schema = context.databaseSchema;
        for (DOSchemaClass schemaClass : schema.getClasses()) {
            if (!SchemaUtil.isDescendantOf(schemaClass, "gest.gen.EntiteContientID", schema)) {
                continue;
            }

            String classKey = getClassKey(schemaClass);
            if (classKey == null) {
                continue;
            }

            long[] objectIds = schemaClass.uniqueObjectIds != null ? schemaClass.uniqueObjectIds : new long[0];
            Set<Long> mids = new LinkedHashSet<>();
            for (long objectId : objectIds) {
                try {
                    Object obj = context.container.ext().getByID(objectId);
                    if (obj == null) {
                        continue;
                    }
                    Long mid = IDEntityHandler.extractMID(context.container, obj);
                    if (IDEntityHandler.isValidMID(mid)) {
                        mids.add(mid);
                    }
                } catch (Exception e) {
                    // ignore individual lookup failures
                }
            }
            data.midsByClass.put(classKey, mids);
        }
        return data;
    }

    private Map<Long, Set<Long>> buildObjectIdsByMidForClass(DODatabaseContext context, String className) {
        Map<Long, Set<Long>> objectIdsByMid = new HashMap<>();
        if (context == null || context.databaseSchema == null || context.databaseSchema.getClasses() == null || context.container == null || className == null) {
            return objectIdsByMid;
        }

        DOSchemaClass targetClass = null;
        for (DOSchemaClass schemaClass : context.databaseSchema.getClasses()) {
            String classKey = getClassKey(schemaClass);
            if (className.equals(classKey)) {
                targetClass = schemaClass;
                break;
            }
        }

        if (targetClass == null) {
            return objectIdsByMid;
        }

        long[] objectIds = targetClass.uniqueObjectIds != null ? targetClass.uniqueObjectIds : new long[0];
        for (long objectId : objectIds) {
            try {
                Object obj = context.container.ext().getByID(objectId);
                if (obj == null) {
                    continue;
                }
                Long mid = IDEntityHandler.extractMID(context.container, obj);
                if (IDEntityHandler.isValidMID(mid)) {
                    objectIdsByMid.computeIfAbsent(mid, key -> new LinkedHashSet<>()).add(objectId);
                }
            } catch (Exception e) {
                // ignore individual lookup failures
            }
        }

        return objectIdsByMid;
    }

    private String getClassKey(DOSchemaClass schemaClass) {
        if (schemaClass == null) {
            return null;
        }
        if (schemaClass.attributes.source != null && !schemaClass.attributes.source.isBlank()) {
            return schemaClass.attributes.source;
        }
        if (schemaClass.attributes.destinationName != null && !schemaClass.attributes.destinationName.isBlank()) {
            return schemaClass.attributes.destinationName;
        }
        if (schemaClass.attributes.title != null && !schemaClass.attributes.title.isBlank()) {
            return schemaClass.attributes.title;
        }
        return null;
    }

    private void showSupplementalForSelection() {
        int selectedRow = diffTable.getSelectedRow();
        if (selectedRow < 0) {
            return;
        }

        ComparisonMode mode = (ComparisonMode) modeSelector.getSelectedItem();
        if (mode == ComparisonMode.ID_COUNTERS) {
            String msg = "Raw DB4O object IDs are unstable after defragmentation.\n" + "This mode is count-only; use Entities mode for supplemental value diffs based on mID.";
            leftOnlyArea.setText(msg);
            rightOnlyArea.setText(msg);
            leftOnlyArea.setCaretPosition(0);
            rightOnlyArea.setCaretPosition(0);
            return;
        }

        String className = String.valueOf(diffTableModel.getValueAt(selectedRow, 0));
        DODatabaseContext leftContext = getSelectedContext(leftSelector);
        DODatabaseContext rightContext = getSelectedContext(rightSelector);
        if (leftContext == null || rightContext == null) {
            return;
        }

        Map<String, Set<Long>> leftByClass = buildComparisonValues(leftContext, mode);
        Map<String, Set<Long>> rightByClass = buildComparisonValues(rightContext, mode);

        Set<Long> leftSet = leftByClass.getOrDefault(className, new LinkedHashSet<>());
        Set<Long> rightSet = rightByClass.getOrDefault(className, new LinkedHashSet<>());

        List<Long> leftOnly = leftSet.stream().filter(id -> !rightSet.contains(id)).sorted(Comparator.naturalOrder()).collect(Collectors.toList());
        List<Long> rightOnly = rightSet.stream().filter(id -> !leftSet.contains(id)).sorted(Comparator.naturalOrder()).collect(Collectors.toList());

        Set<Long> leftExported = mode == ComparisonMode.ENTITIES ? getExportedMidsForClass(leftContext, className) : null;
        Set<Long> rightExported = mode == ComparisonMode.ENTITIES ? getExportedMidsForClass(rightContext, className) : null;
        Map<Long, Set<Long>> leftObjectIdsByMid = mode == ComparisonMode.ENTITIES ? buildObjectIdsByMidForClass(leftContext, className) : null;
        Map<Long, Set<Long>> rightObjectIdsByMid = mode == ComparisonMode.ENTITIES ? buildObjectIdsByMidForClass(rightContext, className) : null;

        leftOnlyArea.setText(formatValueList(className, leftOnly, mode, leftExported, leftObjectIdsByMid));
        rightOnlyArea.setText(formatValueList(className, rightOnly, mode, rightExported, rightObjectIdsByMid));
        leftOnlyArea.setCaretPosition(0);
        rightOnlyArea.setCaretPosition(0);
    }

    private Set<Long> getExportedMidsForClass(DODatabaseContext context, String className) {
        if (context == null || context.databaseFilePath == null) {
            return null;
        }

        MainWindow mainWindow = MainWindow.getInstance();
        ExportStatistics statistics = mainWindow.getLatestExportStatistics(context.databaseFilePath);
        if (statistics == null) {
            return null;
        }

        Map<String, Set<Long>> cacheForDb = exportedMidsCacheByDatabasePath.computeIfAbsent(context.databaseFilePath, key -> new HashMap<>());
        if (cacheForDb.containsKey(className)) {
            return cacheForDb.get(className);
        }

        List<Long> exportedObjectIds = statistics.exportedObjectIds.getOrDefault(className, new ArrayList<>());
        Set<Long> exportedMids = new LinkedHashSet<>();
        for (Long objectId : exportedObjectIds) {
            if (objectId == null) {
                continue;
            }
            try {
                Object obj = context.container.ext().getByID(objectId);
                if (obj == null) {
                    continue;
                }
                Long mid = IDEntityHandler.extractMID(context.container, obj);
                if (IDEntityHandler.isValidMID(mid)) {
                    exportedMids.add(mid);
                }
            } catch (Exception e) {
                // Ignore individual extraction failures
            }
        }

        cacheForDb.put(className, exportedMids);
        return exportedMids;
    }

    private String formatValueList(String className, List<Long> values, ComparisonMode mode, Set<Long> exportedValues, Map<Long, Set<Long>> objectIdsByMid) {
        StringBuilder sb = new StringBuilder();
        sb.append("Class: ").append(className).append("\n");
        sb.append(mode == ComparisonMode.ENTITIES ? "mID values: " : "Object IDs: ").append(values.size()).append("\n\n");
        for (Long value : values) {
            sb.append(value);
            if (mode == ComparisonMode.ENTITIES) {
                Set<Long> rawObjectIds = objectIdsByMid != null ? objectIdsByMid.get(value) : null;
                if (rawObjectIds != null && !rawObjectIds.isEmpty()) {
                    sb.append("  [dbIds: ").append(rawObjectIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(","))).append("]");
                }
                if (exportedValues == null) {
                    sb.append("  [export: unknown]");
                } else if (exportedValues.contains(value)) {
                    sb.append("  [exported]");
                } else {
                    sb.append("  [not exported]");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private void exportSelectedPair() {
        DODatabaseContext leftContext = getSelectedContext(leftSelector);
        DODatabaseContext rightContext = getSelectedContext(rightSelector);
        if (leftContext == null || rightContext == null || leftContext == rightContext) {
            exportStatusLabel.setText("Select two different databases first.");
            return;
        }

        pendingExportPaths.clear();
        pendingExportPaths.add(leftContext.databaseFilePath);
        pendingExportPaths.add(rightContext.databaseFilePath);
        awaitingExportPath = null;
        triggerNextExportInQueue();
    }

    private void triggerNextExportInQueue() {
        if (pendingExportPaths.isEmpty()) {
            awaitingExportPath = null;
            exportStatusLabel.setText("Both exports completed.");
            refreshDiffs();
            return;
        }

        String nextPath = pendingExportPaths.remove(0);
        DODatabaseContext context = null;
        for (DODatabaseContext candidate : contexts) {
            if (nextPath.equals(candidate.databaseFilePath)) {
                context = candidate;
                break;
            }
        }
        if (context == null) {
            triggerNextExportInQueue();
            return;
        }

        awaitingExportPath = nextPath;
        File file = new File(nextPath);
        exportStatusLabel.setText("Exporting " + file.getName() + "...");
        MainWindow.getInstance().triggerMigrateAllModules(context);
    }

    private void handleExportProgress(String completedPath, ExportStatistics result) {
        if (awaitingExportPath == null || completedPath == null || !completedPath.equals(awaitingExportPath)) {
            return;
        }

        int errorCount = result != null && result.errors != null ? result.errors.size() : 0;
        if (errorCount > 0) {
            exportStatusLabel.setText("Export finished with " + errorCount + " errors for " + new File(completedPath).getName());
        }

        awaitingExportPath = null;
        triggerNextExportInQueue();
    }

    private void updateExportStatusLabel(DODatabaseContext leftContext, DODatabaseContext rightContext) {
        if (leftContext == null || rightContext == null) {
            return;
        }
        MainWindow mainWindow = MainWindow.getInstance();
        boolean leftExported = mainWindow.getLatestExportStatistics(leftContext.databaseFilePath) != null;
        boolean rightExported = mainWindow.getLatestExportStatistics(rightContext.databaseFilePath) != null;

        if (leftExported && rightExported) {
            if (awaitingExportPath == null && pendingExportPaths.isEmpty()) {
                exportStatusLabel.setText("Both selected databases have export diagnostics.");
            }
        } else {
            if (awaitingExportPath == null && pendingExportPaths.isEmpty()) {
                exportStatusLabel.setText("Run 'Export selected pair' to annotate mID diffs with export status.");
            }
        }
    }
}
