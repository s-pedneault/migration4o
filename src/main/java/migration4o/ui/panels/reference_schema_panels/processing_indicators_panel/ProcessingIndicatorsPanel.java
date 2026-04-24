package migration4o.ui.panels.reference_schema_panels.processing_indicators_panel;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.schema.DOSchemaService;
import migration4o.schema.indicators.ProcessingIndicatorService;
import migration4o.util.ClassUtil;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Panel for configuring the ordered list of classes shown in the Processing Costs panel. Lets the user search from all reference-schema classes, add them to an ordered list, and reorder with arrow buttons. Changes are persisted immediately via {@link ProcessingIndicatorService}.
 */
public class ProcessingIndicatorsPanel extends JPanel {

    private final ProcessingIndicatorService indicatorService = ProcessingIndicatorService.getInstance();

    /** All schema classes, sorted by display label. */
    private List<SchemaClassEntry> allEntries = new ArrayList<>();

    // -- Left: available classes --
    private final JTextField searchField = new JTextField();
    private final DefaultListModel<SchemaClassEntry> availableModel = new DefaultListModel<>();
    private final JList<SchemaClassEntry> availableList = new JList<>(availableModel);

    // -- Right: configured indicators --
    private final DefaultListModel<SchemaClassEntry> indicatorModel = new DefaultListModel<>();
    private final JList<SchemaClassEntry> indicatorList = new JList<>(indicatorModel);

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public ProcessingIndicatorsPanel() {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        add(buildHeaderLabel(), BorderLayout.NORTH);
        add(buildMainSplit(), BorderLayout.CENTER);

        loadSchema();
        refreshAvailableList();
        loadIndicators();
    }

    // -------------------------------------------------------------------------
    // UI construction
    // -------------------------------------------------------------------------

    private JLabel buildHeaderLabel() {
        JLabel label = new JLabel("Configure the classes whose object counts are shown in the Processing Costs panel.");
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        return label;
    }

    private JSplitPane buildMainSplit() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildAvailablePanel(), buildIndicatorsPanel());
        split.setResizeWeight(0.5);
        split.setDividerSize(6);
        split.setBorder(null);
        return split;
    }

    private JPanel buildAvailablePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(new TitledBorder("Available classes"));

        // Search bar
        JPanel searchPanel = new JPanel(new BorderLayout(4, 0));
        searchPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        searchPanel.add(new JLabel("Search:"), BorderLayout.WEST);
        searchField.setToolTipText("Filter classes by source name or title");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refreshAvailableList();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshAvailableList();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshAvailableList();
            }
        });
        searchPanel.add(searchField, BorderLayout.CENTER);
        panel.add(searchPanel, BorderLayout.NORTH);

        availableList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        availableList.setCellRenderer(new ClassEntryRenderer());
        availableList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    addSelectedClass();
                }
            }
        });
        panel.add(new JScrollPane(availableList), BorderLayout.CENTER);

        JButton addBtn = new JButton("Add \u25ba");
        addBtn.setToolTipText("Add selected class to indicators (or double-click)");
        addBtn.addActionListener(e -> addSelectedClass());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
        btnPanel.add(addBtn);
        panel.add(btnPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildIndicatorsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(new TitledBorder("Processing indicators (ordered)"));

        indicatorList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        indicatorList.setCellRenderer(new ClassEntryRenderer());
        panel.add(new JScrollPane(indicatorList), BorderLayout.CENTER);

        panel.add(buildIndicatorButtons(), BorderLayout.EAST);
        return panel;
    }

    private JPanel buildIndicatorButtons() {
        JButton upBtn = new JButton("\u25b2");
        upBtn.setToolTipText("Move up");
        upBtn.addActionListener(e -> moveSelectedUp());

        JButton downBtn = new JButton("\u25bc");
        downBtn.setToolTipText("Move down");
        downBtn.addActionListener(e -> moveSelectedDown());

        JButton removeBtn = new JButton("\u2715 Remove");
        removeBtn.setToolTipText("Remove from indicators");
        removeBtn.addActionListener(e -> removeSelectedClass());

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.Y_AXIS));
        buttons.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 4));

        upBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        downBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        removeBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        buttons.add(upBtn);
        buttons.add(Box.createVerticalStrut(4));
        buttons.add(downBtn);
        buttons.add(Box.createVerticalStrut(12));
        buttons.add(removeBtn);

        return buttons;
    }

    // -------------------------------------------------------------------------
    // Data loading
    // -------------------------------------------------------------------------

    private void loadSchema() {
        DOSchema schema = DOSchemaService.getInstance().getReferenceSchema();
        allEntries = new ArrayList<>();
        if (schema != null && schema.getClasses() != null) {
            for (DOSchemaClass cls : schema.getClasses()) {
                allEntries.add(new SchemaClassEntry(cls.attributes.source, cls.attributes.title));
            }
            allEntries.sort(Comparator.comparing(SchemaClassEntry::displayLabel, String.CASE_INSENSITIVE_ORDER));
        }
    }

    private void loadIndicators() {
        indicatorModel.clear();
        DOSchema schema = DOSchemaService.getInstance().getReferenceSchema();
        for (String className : indicatorService.getClassNames()) {
            String title = resolveTitle(schema, className);
            indicatorModel.addElement(new SchemaClassEntry(className, title));
        }
    }

    // -------------------------------------------------------------------------
    // Refresh helpers
    // -------------------------------------------------------------------------

    private void refreshAvailableList() {
        String filter = searchField.getText().trim().toLowerCase();
        List<String> configured = currentIndicatorClassNames();
        availableModel.clear();
        for (SchemaClassEntry entry : allEntries) {
            if (configured.contains(entry.sourceName())) {
                continue; // already in indicator list
            }
            if (!filter.isEmpty()) {
                boolean matches = entry.sourceName().toLowerCase().contains(filter) || (entry.title() != null && entry.title().toLowerCase().contains(filter)) || ClassUtil.getSimpleName(entry.sourceName()).toLowerCase().contains(filter);
                if (!matches)
                    continue;
            }
            availableModel.addElement(entry);
        }
    }

    private List<String> currentIndicatorClassNames() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < indicatorModel.size(); i++) {
            names.add(indicatorModel.getElementAt(i).sourceName());
        }
        return names;
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    private void addSelectedClass() {
        SchemaClassEntry selected = availableList.getSelectedValue();
        if (selected == null)
            return;
        indicatorService.add(selected.sourceName());
        loadIndicators();
        refreshAvailableList();
        selectInIndicatorList(selected.sourceName());
    }

    private void removeSelectedClass() {
        SchemaClassEntry selected = indicatorList.getSelectedValue();
        if (selected == null)
            return;
        int idx = indicatorList.getSelectedIndex();
        indicatorService.remove(selected.sourceName());
        loadIndicators();
        refreshAvailableList();
        int newSize = indicatorModel.size();
        if (newSize > 0) {
            indicatorList.setSelectedIndex(Math.min(idx, newSize - 1));
        }
    }

    private void moveSelectedUp() {
        int idx = indicatorList.getSelectedIndex();
        if (idx <= 0)
            return;
        indicatorService.moveUp(idx);
        loadIndicators();
        indicatorList.setSelectedIndex(idx - 1);
    }

    private void moveSelectedDown() {
        int idx = indicatorList.getSelectedIndex();
        if (idx < 0 || idx >= indicatorModel.size() - 1)
            return;
        indicatorService.moveDown(idx);
        loadIndicators();
        indicatorList.setSelectedIndex(idx + 1);
    }

    private void selectInIndicatorList(String sourceName) {
        for (int i = 0; i < indicatorModel.size(); i++) {
            if (indicatorModel.getElementAt(i).sourceName().equals(sourceName)) {
                indicatorList.setSelectedIndex(i);
                indicatorList.ensureIndexIsVisible(i);
                return;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String resolveTitle(DOSchema schema, String className) {
        if (schema == null)
            return null;
        DOSchemaClass cls = schema.findClassByName(className);
        return cls != null && cls.attributes.title != null && !cls.attributes.title.isBlank() ? cls.attributes.title : null;
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    /**
     * Lightweight value object for a schema class entry in the lists.
     */
    record SchemaClassEntry(String sourceName, String title) {
        /** Label shown in the list: "Title (SimpleName)" or "SimpleName (package)" when no title. */
        String displayLabel() {
            String simple = ClassUtil.getSimpleName(sourceName);
            if (title != null && !title.isBlank()) {
                return title + "  (" + simple + ")";
            }
            return simple + "  [" + ClassUtil.getPackageName(sourceName) + "]";
        }

        @Override
        public String toString() {
            return displayLabel();
        }
    }

    /** Renders each list cell with a two-line display (label + source name). */
    private static class ClassEntryRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof SchemaClassEntry entry) {
                label.setText("<html>" + escapeHtml(entry.displayLabel()) + "<br><font size='-2' color='gray'>" + escapeHtml(entry.sourceName()) + "</font></html>");
                label.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            }
            return label;
        }

        private static String escapeHtml(String s) {
            if (s == null)
                return "";
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}
