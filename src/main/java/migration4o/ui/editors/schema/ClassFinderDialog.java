package migration4o.ui.editors.schema;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog for finding and selecting class names (primitives or schema classes).
 */
public class ClassFinderDialog extends JDialog {

    private final DOSchema schema;
    private final JTextField searchField;
    private final DefaultListModel<String> listModel;
    private final JList<String> classList;
    private String selectedValue = null;

    // Primitive types to include in the list
    private static final String[] PRIMITIVES = {
            "boolean", "byte", "char", "short", "int", "long", "float", "double",
            "java.lang.Object", "java.lang.String", "java.lang.Integer", "java.lang.Long",
            "java.lang.Double", "java.lang.Float", "java.lang.Boolean", "java.lang.Character",
            "java.lang.Byte", "java.lang.Short", "java.math.BigDecimal", "java.math.BigInteger",
            "java.util.Date", "java.sql.Date", "java.sql.Time", "java.sql.Timestamp",
            "java.time.LocalDate", "java.time.LocalTime", "java.time.LocalDateTime",
            "java.time.ZonedDateTime", "java.util.UUID"
    };

    public ClassFinderDialog(Frame owner, DOSchema schema, String initialValue) {
        super(owner, "Class Finder", true);
        this.schema = schema;

        setLayout(new BorderLayout(10, 10));
        setSize(500, 400);

        // Search field at the top
        JPanel searchPanel = new JPanel(new BorderLayout(5, 5));
        searchPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        searchField = new JTextField(initialValue != null ? initialValue : "");
        searchField.putClientProperty("JTextField.placeholderText", "Type to search classes...");
        searchPanel.add(new JLabel("Search:"), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        add(searchPanel, BorderLayout.NORTH);

        // List of matching classes
        listModel = new DefaultListModel<>();
        classList = new JList<>(listModel);
        classList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Populate initial list
        updateClassList(initialValue != null ? initialValue : "");

        // Update list as user types
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                updateClassList(searchField.getText());
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                updateClassList(searchField.getText());
            }

            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                updateClassList(searchField.getText());
            }
        });

        JScrollPane listScroll = new JScrollPane(classList);
        listScroll.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        add(listScroll, BorderLayout.CENTER);

        // Button panel
        add(createButtonPanel(), BorderLayout.SOUTH);

        // Double-click to select
        classList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    selectAndClose();
                }
            }
        });
    }

    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> selectAndClose());

        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> {
            selectedValue = "";
            dispose();
        });

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());

        buttonPanel.add(okButton);
        buttonPanel.add(clearButton);
        buttonPanel.add(cancelButton);

        return buttonPanel;
    }

    private void selectAndClose() {
        String selected = classList.getSelectedValue();
        if (selected != null) {
            selectedValue = selected;
            dispose();
        }
    }

    private void updateClassList(String pattern) {
        listModel.clear();

        String lowerPattern = pattern.toLowerCase();
        List<String> matches = new ArrayList<>();

        // Add matching primitive types
        for (String primitive : PRIMITIVES) {
            if (primitive.toLowerCase().contains(lowerPattern)) {
                matches.add(primitive);
            }
        }

        // Add matching schema classes
        if (schema != null && schema.getClasses() != null) {
            for (DOSchemaClass cls : schema.getClasses()) {
                String className = cls.source;
                if (className.toLowerCase().contains(lowerPattern)) {
                    matches.add(className);
                }
            }
        }

        // Sort matches
        matches.sort(String.CASE_INSENSITIVE_ORDER);

        // Add to list
        for (String match : matches) {
            listModel.addElement(match);
        }
    }

    /**
     * Show the dialog and return the selected class name.
     * 
     * @param owner        The parent frame
     * @param schema       The schema containing classes
     * @param initialValue The initial search value
     * @return The selected class name, "" for clear, or null if cancelled
     */
    public static String showDialog(Frame owner, DOSchema schema, String initialValue) {
        ClassFinderDialog dialog = new ClassFinderDialog(owner, schema, initialValue);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return dialog.selectedValue;
    }
}
