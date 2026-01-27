package migration4o.ui.common.dialogs;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Generic base class for filtered list selection dialogs.
 * Provides:
 * - Search field with live filtering
 * - List display with selection
 * - OK/Clear/Cancel buttons
 * - Double-click to select
 * 
 * Subclasses should implement:
 * - getItems() - Return all items to display
 * - filterItems() - Filter items based on search pattern
 * - Optional: supportsNullSelection() - Whether Clear button should be shown
 * 
 * @param <T> The type of items in the list
 */
public abstract class FilteredListDialog<T> extends JDialog {

    protected final JTextField searchField;
    protected final DefaultListModel<T> listModel;
    protected final JList<T> itemList;
    protected T selectedValue = null;
    protected boolean cleared = false;

    /**
     * Creates a new filtered list dialog.
     * 
     * @param owner           The parent frame
     * @param title           The dialog title
     * @param initialFilter   The initial search filter text
     * @param placeholderText Placeholder text for the search field
     */
    public FilteredListDialog(Frame owner, String title, String initialFilter, String placeholderText) {
        super(owner, title, true);

        setLayout(new BorderLayout(10, 10));
        setSize(500, 400);

        // Search field at the top
        JPanel searchPanel = new JPanel(new BorderLayout(5, 5));
        searchPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        searchField = new JTextField(initialFilter != null ? initialFilter : "");
        if (placeholderText != null) {
            searchField.putClientProperty("JTextField.placeholderText", placeholderText);
        }
        searchPanel.add(new JLabel("Search:"), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        add(searchPanel, BorderLayout.NORTH);

        // List of matching items
        listModel = new DefaultListModel<>();
        itemList = new JList<>(listModel);
        itemList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Populate initial list
        updateList(initialFilter != null ? initialFilter : "");

        // Update list as user types
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void changedUpdate(DocumentEvent e) {
                updateList(searchField.getText());
            }

            public void removeUpdate(DocumentEvent e) {
                updateList(searchField.getText());
            }

            public void insertUpdate(DocumentEvent e) {
                updateList(searchField.getText());
            }
        });

        JScrollPane listScroll = new JScrollPane(itemList);
        listScroll.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        add(listScroll, BorderLayout.CENTER);

        // Button panel
        add(createButtonPanel(), BorderLayout.SOUTH);

        // Double-click to select
        itemList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    selectAndClose();
                }
            }
        });
    }

    /**
     * Creates the button panel with OK, Clear (optional), and Cancel buttons.
     * 
     * @return The button panel
     */
    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> selectAndClose());

        // Only show Clear button if subclass supports null selection
        if (supportsNullSelection()) {
            JButton clearButton = new JButton("Clear");
            clearButton.addActionListener(e -> {
                cleared = true;
                selectedValue = null;
                dispose();
            });
            buttonPanel.add(clearButton);
        }

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> {
            cleared = false;
            selectedValue = null;
            dispose();
        });

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        return buttonPanel;
    }

    /**
     * Selects the currently highlighted item and closes the dialog.
     */
    private void selectAndClose() {
        T selected = itemList.getSelectedValue();
        if (selected != null) {
            selectedValue = selected;
            cleared = false;
            dispose();
        }
    }

    /**
     * Updates the list based on the search pattern.
     * 
     * @param pattern The search pattern
     */
    private void updateList(String pattern) {
        listModel.clear();

        List<T> filtered = filterItems(pattern);
        for (T item : filtered) {
            listModel.addElement(item);
        }
    }

    /**
     * Gets all items to be displayed in the list.
     * Subclasses should implement this to provide the full list of items.
     * 
     * @return List of all items
     */
    protected abstract List<T> getAllItems();

    /**
     * Filters items based on the search pattern.
     * Subclasses should implement this to define filtering logic.
     * The filtered list should be sorted as appropriate.
     * 
     * @param pattern The search pattern (may be empty)
     * @return List of filtered and sorted items
     */
    protected abstract List<T> filterItems(String pattern);

    /**
     * Whether this dialog supports null/cleared selection via a Clear button.
     * Default is false. Override to return true if you want a Clear button.
     * 
     * @return true if Clear button should be shown, false otherwise
     */
    protected boolean supportsNullSelection() {
        return false;
    }

    /**
     * Gets the selected value.
     * 
     * @return The selected value, or null if cancelled or cleared
     */
    public T getSelectedValue() {
        return selectedValue;
    }

    /**
     * Checks if the user clicked Clear button.
     * 
     * @return true if Clear was clicked, false otherwise
     */
    public boolean wasCleared() {
        return cleared;
    }

    /**
     * Checks if the user cancelled the dialog.
     * 
     * @return true if cancelled, false if OK or Clear was clicked
     */
    public boolean wasCancelled() {
        return !cleared && selectedValue == null;
    }
}
