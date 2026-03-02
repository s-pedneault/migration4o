package migration4o.ui.panels.reference_schema_panels.reference_schema_panel.dialogs;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.util.DatabaseUtil;
import migration4o.util.SchemaUtil;
import migration4o.util.TypeUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dialog for editing the summary string of a DOSchemaClass.
 *
 * <p>The summary is a free-form string containing literal text and field references
 * in the form {@code [fieldName]}. For example:
 * <pre>Dossier [adresse.numeroCivique] [adresse.rue], [adresse.ville]</pre>
 *
 * <p>The editor provides:
 * <ul>
 *   <li>A main text area for direct editing of the summary string</li>
 *   <li>A field reference panel listing available fields for one-click insertion</li>
 *   <li>A live preview showing the result</li>
 * </ul>
 */
public class SummaryEditorDialog extends JDialog {

    private static final Pattern FIELD_REF_PATTERN = Pattern.compile("\\[([^\\]]+)\\]");

    private final JTextArea summaryTextArea;
    private final JLabel previewLabel;
    private final DefaultListModel<String> fieldListModel;
    private final JList<String> fieldList;

    private boolean confirmed = false;
    private String result = null;

    /**
     * Creates and shows the dialog.
     *
     * @return the edited summary string if OK was clicked (may be {@code ""} if
     *         the user cleared the summary), or {@code null} if the user cancelled.
     */
    public static String showDialog(Frame owner, DOSchemaClass schemaClass, DOSchema schema) {
        SummaryEditorDialog dialog = new SummaryEditorDialog(owner, schemaClass, schema);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        // null → cancelled; "" or non-empty string → confirmed (OK clicked)
        return dialog.confirmed ? dialog.result : null;
    }

    public SummaryEditorDialog(Frame owner, DOSchemaClass schemaClass, DOSchema schema) {
        super(owner, "Edit Summary — " + schemaClass.destinationName, true);
        setLayout(new BorderLayout(10, 10));
        setPreferredSize(new Dimension(780, 420));

        // ── Preview bar (top) ─────────────────────────────────────────────
        JPanel previewBar = new JPanel(new BorderLayout(6, 0));
        previewBar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY), BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        previewBar.add(new JLabel("Preview: "), BorderLayout.WEST);
        previewLabel = new JLabel();
        previewLabel.setFont(previewLabel.getFont().deriveFont(Font.ITALIC));
        previewLabel.setForeground(new Color(60, 100, 160));
        previewBar.add(previewLabel, BorderLayout.CENTER);
        add(previewBar, BorderLayout.NORTH);

        // ── Centre split: text area + field panel ─────────────────────────
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        split.setResizeWeight(0.65);

        // Left: text area
        JPanel textPanel = new JPanel(new BorderLayout(0, 4));
        textPanel.setBorder(BorderFactory.createTitledBorder("Summary (use [fieldName] for field references)"));

        summaryTextArea = new JTextArea(schemaClass.summary != null ? schemaClass.summary : "", 8, 40);
        summaryTextArea.setLineWrap(true);
        summaryTextArea.setWrapStyleWord(true);
        summaryTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        summaryTextArea.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        JScrollPane textScroll = new JScrollPane(summaryTextArea);
        textPanel.add(textScroll, BorderLayout.CENTER);

        // Helper tip
        JLabel tipLabel = new JLabel("<html><small>Tip: position cursor and double-click a field on the right to insert a reference.</small></html>");
        tipLabel.setForeground(Color.GRAY);
        tipLabel.setBorder(BorderFactory.createEmptyBorder(2, 2, 0, 0));
        textPanel.add(tipLabel, BorderLayout.SOUTH);

        split.setLeftComponent(textPanel);

        // Right: field panel
        JPanel fieldPanel = new JPanel(new BorderLayout(0, 4));
        fieldPanel.setBorder(BorderFactory.createTitledBorder("Available Fields"));

        fieldListModel = new DefaultListModel<>();
        populateFieldList(schemaClass, schema);

        fieldList = new JList<>(fieldListModel);
        fieldList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fieldList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        // Double-click inserts immediately
        fieldList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    insertSelectedField();
                }
            }
        });

        JScrollPane fieldScroll = new JScrollPane(fieldList);
        fieldPanel.add(fieldScroll, BorderLayout.CENTER);

        JButton insertButton = new JButton("Insert →");
        insertButton.setToolTipText("Insert selected field reference at cursor position");
        insertButton.addActionListener(e -> insertSelectedField());
        JPanel insertButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        insertButtonPanel.add(insertButton);
        fieldPanel.add(insertButtonPanel, BorderLayout.SOUTH);

        split.setRightComponent(fieldPanel);

        add(split, BorderLayout.CENTER);

        // ── Bottom buttons ─────────────────────────────────────────────────
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        buttonPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));

        JButton clearButton = new JButton("Clear");
        clearButton.setToolTipText("Remove all summary text");
        clearButton.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(this, "Clear the entire summary?", "Confirm Clear", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                summaryTextArea.setText("");
            }
        });

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> {
            confirmed = false;
            dispose();
        });

        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> {
            confirmed = true;
            String text = summaryTextArea.getText().trim();
            // Return "" for empty (to distinguish from cancel which returns null)
            result = text;
            dispose();
        });
        getRootPane().setDefaultButton(okButton);

        buttonPanel.add(clearButton);
        buttonPanel.add(Box.createHorizontalStrut(20));
        buttonPanel.add(cancelButton);
        buttonPanel.add(okButton);
        add(buttonPanel, BorderLayout.SOUTH);

        // Live preview update
        summaryTextArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updatePreview();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updatePreview();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updatePreview();
            }
        });
        updatePreview();

        pack();
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * Populates the field list with:
     * <ul>
     *   <li>Direct and inherited fields of the class (non-collection)</li>
     *   <li>Dotted fields from non-collection, non-IDEntite embedded object
     *       fields (one level deep), e.g. {@code adresse.rue}</li>
     * </ul>
     * Inherited fields are included at every level via
     * {@link DatabaseUtil#getAllSchemaFieldsIncludingAncestors}.
     */
    private void populateFieldList(DOSchemaClass schemaClass, DOSchema schema) {
        fieldListModel.clear();

        for (DOSchemaField field : DatabaseUtil.getAllSchemaFieldsIncludingAncestors(schemaClass, schema)) {
            if (field.isCollection)
                continue;
            String destName = field.destinationName; // already guaranteed non-null/non-empty by helper

            DOSchemaClass embeddedClass = resolveEmbeddedClass(field, schema);

            if (embeddedClass == null) {
                fieldListModel.addElement(destName);
            } else {
                boolean addedAny = false;
                for (DOSchemaField subField : DatabaseUtil.getAllSchemaFieldsIncludingAncestors(embeddedClass, schema)) {
                    if (subField.isCollection)
                        continue;
                    DOSchemaClass subEmbedded = resolveEmbeddedClass(subField, schema);
                    if (subEmbedded == null) {
                        fieldListModel.addElement(destName + "." + subField.destinationName);
                        addedAny = true;
                    }
                }
                if (!addedAny) {
                    fieldListModel.addElement(destName);
                }
            }
        }
    }

    /**
     * Returns the schema class that {@code field} embeds, or {@code null} if
     * the field itself carries a primitive/leaf value.
     * IDEntite classes are treated as leaf values (they export as an ID).
     */
    private static DOSchemaClass resolveEmbeddedClass(DOSchemaField field, DOSchema schema) {
        if (schema == null)
            return null;
        String type = field.type;
        if (type == null || type.isEmpty())
            return null;
        if (TypeUtil.isPrimitiveType(type))
            return null;
        DOSchemaClass cls = SchemaUtil.findClassByName(type, schema);
        if (cls == null)
            return null;
        // IDEntite classes export as a scalar ID — treat as leaf
        if (cls.isIDEntite(schema))
            return null;
        return cls;
    }

    private void insertSelectedField() {
        String selected = fieldList.getSelectedValue();
        if (selected == null || selected.isEmpty())
            return;
        String ref = "[" + selected + "]";
        int pos = summaryTextArea.getCaretPosition();
        summaryTextArea.insert(ref, pos);
        summaryTextArea.requestFocusInWindow();
        summaryTextArea.setCaretPosition(pos + ref.length());
    }

    /**
     * Renders a preview of the summary. Field references {@code [fieldName]}
     * are highlighted with HTML colour.
     */
    private void updatePreview() {
        String text = summaryTextArea.getText();
        if (text == null || text.trim().isEmpty()) {
            previewLabel.setText("<html><i style='color:gray'>(empty)</i></html>");
            return;
        }
        // Build HTML with field references highlighted
        StringBuilder html = new StringBuilder("<html>");
        Matcher matcher = FIELD_REF_PATTERN.matcher(text);
        int last = 0;
        while (matcher.find()) {
            // Append plain text before this match
            String plain = text.substring(last, matcher.start());
            if (!plain.isEmpty()) {
                html.append(escapeHtml(plain));
            }
            // Append highlighted reference
            html.append("<b style='color:#1a6bb5;background:#e8f0fb'>");
            html.append(escapeHtml(matcher.group()));
            html.append("</b>");
            last = matcher.end();
        }
        // Remaining plain text
        if (last < text.length()) {
            html.append(escapeHtml(text.substring(last)));
        }
        html.append("</html>");
        previewLabel.setText(html.toString());
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
