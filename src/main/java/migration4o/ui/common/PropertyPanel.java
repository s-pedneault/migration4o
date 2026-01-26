package migration4o.ui.common;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Panel for editing properties of selected schema elements.
 */
public class PropertyPanel extends JPanel {

    private final Map<String, JComponent> propertyFields;
    private final JPanel fieldsPanel;

    public PropertyPanel() {
        this.propertyFields = new HashMap<>();

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createTitledBorder("Properties"));

        // Create scrollable fields panel
        fieldsPanel = new JPanel();
        fieldsPanel.setLayout(new GridBagLayout());

        JScrollPane scrollPane = new JScrollPane(fieldsPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);

        add(scrollPane, BorderLayout.CENTER);

        showEmptyMessage();
    }

    /**
     * Clear all property fields.
     */
    public void clear() {
        propertyFields.clear();
        fieldsPanel.removeAll();
        showEmptyMessage();
        revalidate();
        repaint();
    }

    private void showEmptyMessage() {
        fieldsPanel.setLayout(new BorderLayout());
        JLabel emptyLabel = new JLabel("Select an item to view properties", SwingConstants.CENTER);
        emptyLabel.setForeground(Color.GRAY);
        emptyLabel.setFont(emptyLabel.getFont().deriveFont(Font.ITALIC));
        fieldsPanel.add(emptyLabel, BorderLayout.CENTER);
    }

    /**
     * Add a text property field.
     */
    public JTextField addTextField(String label, String initialValue) {
        removeEmptyMessage();

        JTextField textField = new JTextField(initialValue, 30);
        addField(label, textField);
        propertyFields.put(label, textField);
        return textField;
    }

    /**
     * Add a checkbox property field.
     */
    public JCheckBox addCheckBox(String label, boolean initialValue) {
        removeEmptyMessage();

        JCheckBox checkBox = new JCheckBox();
        checkBox.setSelected(initialValue);
        addField(label, checkBox);
        propertyFields.put(label, checkBox);
        return checkBox;
    }

    /**
     * Add a read-only text field.
     */
    public JTextField addReadOnlyTextField(String label, String value) {
        removeEmptyMessage();

        JTextField textField = new JTextField(value, 30);
        textField.setEditable(false);
        textField.setBackground(new Color(240, 240, 240));
        addField(label, textField);
        propertyFields.put(label, textField);
        return textField;
    }

    /**
     * Add a custom component field.
     */
    public void addCustomField(String label, JComponent component) {
        removeEmptyMessage();
        addField(label, component);
        propertyFields.put(label, component);
    }

    private void removeEmptyMessage() {
        if (fieldsPanel.getLayout() instanceof BorderLayout) {
            fieldsPanel.removeAll();
            fieldsPanel.setLayout(new GridBagLayout());
        }
    }

    private void addField(String label, JComponent component) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = propertyFields.size();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 10, 5, 10);

        JLabel jLabel = new JLabel(label + ":");
        jLabel.setFont(jLabel.getFont().deriveFont(Font.BOLD));
        fieldsPanel.add(jLabel, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        fieldsPanel.add(component, gbc);

        revalidate();
        repaint();
    }

    /**
     * Get a property field by label.
     */
    public JComponent getField(String label) {
        return propertyFields.get(label);
    }
}
