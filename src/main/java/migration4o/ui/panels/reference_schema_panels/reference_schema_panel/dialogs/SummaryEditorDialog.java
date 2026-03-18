package migration4o.ui.panels.reference_schema_panels.reference_schema_panel.dialogs;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.ui.common.FieldSelectorPanel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dialog for editing the summary string of a DOSchemaClass.
 *
 * <p>
 * The summary is a free-form string containing literal text and field
 * references in the form {@code [fieldName]}. For example:
 * 
 * <pre>
 * Dossier [adresse.numeroCivique] [adresse.rue], [adresse.ville]
 * </pre>
 *
 * <p>
 * The editor provides:
 * <ul>
 * <li>A main text area for direct editing of the summary string</li>
 * <li>A field reference panel listing available fields for one-click
 * insertion</li>
 * <li>A live preview showing the result</li>
 * </ul>
 */
public class SummaryEditorDialog extends JDialog {

    private static final Pattern FIELD_REF_PATTERN = Pattern.compile("\\[([^\\]]+)\\]");

    private final JTextArea summaryTextArea;
    private final JLabel previewLabel;
    private final FieldSelectorPanel fieldSelector;

    private boolean confirmed = false;
    private String result = null;

    /**
     * Creates and shows the dialog.
     *
     * @return the edited summary string if OK was clicked (may be {@code ""} if
     * the user cleared the summary), or {@code null} if the user cancelled.
     */
    public static String showDialog(Frame owner, DOSchemaClass schemaClass, DOSchema schema) {
        SummaryEditorDialog dialog = new SummaryEditorDialog(owner, schemaClass, schema);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        // null → cancelled; "" or non-empty string → confirmed (OK clicked)
        return dialog.confirmed ? dialog.result : null;
    }

    public SummaryEditorDialog(Frame owner, DOSchemaClass schemaClass, DOSchema schema) {
        super(owner, "Edit Summary — " + schemaClass.attributes.destinationName, true);
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

        summaryTextArea = new JTextArea(schemaClass.attributes.summary != null ? schemaClass.attributes.summary : "", 8, 40);
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

        // Right: field selector tree panel
        // Collect currently-referenced field paths so the tree can highlight
        // them
        Set<String> referencedPaths = extractReferencedPaths(schemaClass.attributes.summary);

        fieldSelector = new FieldSelectorPanel(schemaClass, referencedPaths, (fieldPath, fieldLabel) -> insertField(fieldPath));

        JPanel fieldPanel = new JPanel(new BorderLayout(0, 4));
        fieldPanel.setBorder(BorderFactory.createTitledBorder("Available Fields"));
        fieldPanel.add(fieldSelector, BorderLayout.CENTER);

        JButton insertButton = new JButton("Insert →");
        insertButton.setToolTipText("Insert selected field reference at cursor position");
        insertButton.addActionListener(e -> {
            String path = fieldSelector.getSelectedFieldPath();
            if (path != null) {
                insertField(path);
            }
        });
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
            // Return "" for empty (to distinguish from cancel which returns
            // null)
            result = text;
            dispose();
        });
        getRootPane().setDefaultButton(okButton);

        buttonPanel.add(clearButton);
        buttonPanel.add(Box.createHorizontalStrut(20));
        buttonPanel.add(cancelButton);
        buttonPanel.add(okButton);
        add(buttonPanel, BorderLayout.SOUTH);

        // Live preview update + field-tree highlighting
        summaryTextArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updatePreview();
                refreshSelectedPaths();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updatePreview();
                refreshSelectedPaths();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updatePreview();
                refreshSelectedPaths();
            }
        });
        updatePreview();

        pack();
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * Extracts field paths referenced as {@code [fieldName]} tokens from a
     * summary string.
     */
    private static Set<String> extractReferencedPaths(String summary) {
        Set<String> paths = new LinkedHashSet<>();
        if (summary == null || summary.isEmpty())
            return paths;
        Matcher m = FIELD_REF_PATTERN.matcher(summary);
        while (m.find()) {
            paths.add(m.group(1).trim());
        }
        return paths;
    }

    /**
     * Updates the selected-path highlighting in the field tree based on the
     * current summary text.
     */
    private void refreshSelectedPaths() {
        fieldSelector.setSelectedPaths(extractReferencedPaths(summaryTextArea.getText()));
    }

    private void insertField(String fieldPath) {
        if (fieldPath == null || fieldPath.isEmpty())
            return;
        String ref = "[" + fieldPath + "]";
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
