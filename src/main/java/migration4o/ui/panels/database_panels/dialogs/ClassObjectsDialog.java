package migration4o.ui.panels.database_panels.dialogs;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;
import com.db4o.reflect.generic.GenericObject;

import migration4o.database.DODatabase;
import migration4o.database.DODatabaseClass;
import migration4o.database.DODatabaseDelegate;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.recipes.RecipeCollectionItems;

/**
 * Frame to display all objects of a specific class from the database. Provides pagination and detailed field inspection.
 */
public class ClassObjectsDialog extends JFrame {
    private static final int PAGE_SIZE = 100;

    private final DODatabaseClass dbClass;
    private final DODatabaseDelegate delegate;
    private final DOSchemaClass schemaClass;
    private final DOSchema schema;

    private JTable objectsTable;
    private DefaultTableModel objectsTableModel;
    private JLabel statusLabel;
    private JButton prevButton;
    private JButton nextButton;
    private JCheckBox showUniqueCheckbox;
    private int currentPage = 0;
    private long[] objectIds;
    private boolean showOnlyUnique = true;

    public ClassObjectsDialog(java.awt.Frame parent, DODatabaseClass dbClass, DOSchema schema) {
        super("Objects: " + dbClass.attributes.source);
        this.dbClass = dbClass;
        this.delegate = dbClass.delegate;
        this.schemaClass = dbClass.schemaClass;
        this.schema = schema;

        initializeUI();
        loadObjectIds();
        loadPage(0);
    }

    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));
        setSize(1000, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // Top panel with status label and checkbox
        JPanel topPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("Loading...");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        topPanel.add(statusLabel, BorderLayout.CENTER);

        JPanel checkboxPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        showUniqueCheckbox = new JCheckBox("Show only unique objects", true);
        showUniqueCheckbox.addActionListener(e -> {
            showOnlyUnique = showUniqueCheckbox.isSelected();
            loadObjectIds();
            loadPage(0);
        });
        checkboxPanel.add(showUniqueCheckbox);
        topPanel.add(checkboxPanel, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        // Table
        objectsTableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        objectsTable = new JTable(objectsTableModel);
        objectsTable.setFont(new Font("Monospaced", Font.PLAIN, 11));
        objectsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        objectsTable.setAutoCreateRowSorter(true);

        // Double-click copies object ID to clipboard
        objectsTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = objectsTable.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        handleRowDoubleClick(row);
                    }
                }
            }
        });

        add(new JScrollPane(objectsTable), BorderLayout.CENTER);

        // Pagination
        JPanel paginationPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        prevButton = new JButton("\u2190 Previous");
        prevButton.addActionListener(e -> loadPage(currentPage - 1));
        paginationPanel.add(prevButton);

        nextButton = new JButton("Next \u2192");
        nextButton.addActionListener(e -> loadPage(currentPage + 1));
        paginationPanel.add(nextButton);

        add(paginationPanel, BorderLayout.SOUTH);
    }

    private void loadObjectIds() {
        if (showOnlyUnique) {
            objectIds = dbClass.objects.uniqueObjectIds != null ? dbClass.objects.uniqueObjectIds : new long[0];
        } else {
            objectIds = dbClass.objects.objectIds != null ? dbClass.objects.objectIds : new long[0];
        }
    }

    private void loadPage(int pageNumber) {
        currentPage = pageNumber;
        int totalPages = Math.max(1, (int) Math.ceil((double) objectIds.length / PAGE_SIZE));
        int startIdx = currentPage * PAGE_SIZE;
        int endIdx = Math.min(startIdx + PAGE_SIZE, objectIds.length);

        statusLabel.setText(String.format("Page %d of %d — Objects %d to %d of %d", currentPage + 1, totalPages, startIdx + 1, endIdx, objectIds.length));
        prevButton.setEnabled(currentPage > 0);
        nextButton.setEnabled(currentPage < totalPages - 1);

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                loadObjectsFromDatabase(startIdx, endIdx);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(ClassObjectsDialog.this, "Error loading objects: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void loadObjectsFromDatabase(int startIdx, int endIdx) {
        if (delegate == null || delegate.isClosed()) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Database is not open.", "Error", JOptionPane.ERROR_MESSAGE));
            return;
        }

        // Collect all fields including inherited ones
        List<FieldInfo> fields = collectAllFields(schemaClass, schema);

        String[] columnNames = new String[fields.size() + 2];
        columnNames[0] = "Class";
        columnNames[1] = "Object ID";
        for (int i = 0; i < fields.size(); i++) {
            columnNames[i + 2] = fields.get(i).displayName;
        }

        SwingUtilities.invokeLater(() -> {
            objectsTableModel.setColumnCount(0);
            objectsTableModel.setRowCount(0);
            for (String col : columnNames) {
                objectsTableModel.addColumn(col);
            }
        });

        for (int i = startIdx; i < endIdx; i++) {
            long objectId = objectIds[i];
            try {
                Object obj = delegate.getByID(objectId);
                if (obj == null)
                    continue;

                try {
                    delegate.activate(obj, 1);
                } catch (Exception ignored) {
                }

                Object[] rowData = new Object[fields.size() + 2];

                String actualClassName = obj instanceof GenericObject ? ((GenericObject) obj).getGenericClass().getName() : obj.getClass().getName();
                String shortClassName = actualClassName.contains(".") ? actualClassName.substring(actualClassName.lastIndexOf('.') + 1) : actualClassName;

                rowData[0] = shortClassName;
                rowData[1] = objectId;

                if (obj instanceof GenericObject) {
                    GenericObject genericObj = (GenericObject) obj;
                    for (int fi = 0; fi < fields.size(); fi++) {
                        Object value = getFieldValue(genericObj, fields.get(fi).fieldName);
                        rowData[fi + 2] = formatValue(value);
                    }
                }

                final Object[] finalRow = rowData;
                SwingUtilities.invokeLater(() -> objectsTableModel.addRow(finalRow));
            } catch (Exception e) {
                System.err.println("Error loading object " + objectId + ": " + e.getMessage());
            }
        }
    }

    // ── Field collection (with inheritance) ─────────────────────────────

    private List<FieldInfo> collectAllFields(DOSchemaClass targetClass, DOSchema schema) {
        List<FieldInfo> allFields = new ArrayList<>();
        Set<String> added = new java.util.HashSet<>();

        DOSchemaClass current = targetClass;
        while (current != null) {
            if (current.fields != null) {
                for (DOSchemaField field : current.fields) {
                    String fieldName = field.attributes.source;
                    if (added.add(fieldName)) {
                        String prefix = current.attributes.source.equals(targetClass.attributes.source) ? "" : current.getSourceName() + ".";
                        allFields.add(new FieldInfo(fieldName, prefix + fieldName));
                    }
                }
            }
            String parentName = current.attributes.parentClassName;
            if (parentName == null || parentName.isEmpty() || parentName.equals("Undetermined"))
                break;
            current = schema != null ? schema.findClassByName(parentName) : null;
        }
        return allFields;
    }

    // ── Field value extraction ──────────────────────────────────────────

    private Object getFieldValue(GenericObject obj, String fieldName) {
        try {
            StoredClass storedClass = delegate.storedClass(obj);
            while (storedClass != null) {
                StoredField[] fields = storedClass.getStoredFields();
                if (fields != null) {
                    for (StoredField field : fields) {
                        if (field.getName().equals(fieldName)) {
                            Object value = field.get(obj);
                            if (value instanceof java.util.Collection) {
                                try {
                                    delegate.activate(value, 1);
                                } catch (Exception ignored) {
                                }
                            }
                            return value;
                        }
                    }
                }
                storedClass = storedClass.getParentStoredClass();
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
        return null;
    }

    // ── Value formatting ────────────────────────────────────────────────

    private String formatValue(Object value) {
        if (value == null)
            return "";
        if (value instanceof String)
            return (String) value;

        // Collections (including GenericObject-backed collections via RecipeCollectionItems)
        Collection<?> collection = RecipeCollectionItems.getItems(delegate, value);
        if (collection != null) {
            if (collection.isEmpty())
                return "[Collection<?>: 0 items]";

            Object firstItem = collection.iterator().next();
            if (firstItem instanceof GenericObject) {
                GenericObject firstGeneric = (GenericObject) firstItem;
                if (isIDEntiteType(firstGeneric)) {
                    return formatIDEntiteCollection(collection);
                } else {
                    return formatGenericObjectCollection(collection);
                }
            }
            return "[Collection<" + inferCollectionItemTypes(collection) + ">: " + collection.size() + " items]";
        }

        // Single GenericObject
        if (value instanceof GenericObject) {
            GenericObject genericObj = (GenericObject) value;
            if (isIDEntiteType(genericObj)) {
                Long mID = extractMID(genericObj);
                long oid = delegate.getID(genericObj);
                if (mID != null && oid > 0)
                    return mID + " (" + oid + ")";
                if (mID != null)
                    return String.valueOf(mID);
            }
            return formatGenericObjectSummary(genericObj);
        }

        return value.toString();
    }

    private String formatIDEntiteCollection(Collection<?> collection) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Object item : collection) {
            if (item instanceof GenericObject) {
                Long mID = extractMID((GenericObject) item);
                if (mID != null) {
                    if (!first)
                        sb.append(", ");
                    sb.append(mID);
                    first = false;
                }
            }
        }
        return sb.append("]").toString();
    }

    private String formatGenericObjectCollection(Collection<?> collection) {
        StringBuilder sb = new StringBuilder("[");
        int count = 0;
        for (Object item : collection) {
            if (item instanceof GenericObject) {
                if (count >= 3) {
                    sb.append(", ...");
                    break;
                }
                if (count > 0)
                    sb.append(", ");
                sb.append(formatGenericObjectSummary((GenericObject) item));
                count++;
            }
        }
        return sb.append("]").toString();
    }

    private String inferCollectionItemTypes(Collection<?> collection) {
        LinkedHashSet<String> typeNames = new LinkedHashSet<>();
        int inspected = 0;
        for (Object item : collection) {
            if (item != null) {
                if (item instanceof GenericObject) {
                    try {
                        StoredClass sc = delegate.storedClass(item);
                        if (sc != null)
                            typeNames.add(sc.getName());
                    } catch (Exception ignored) {
                    }
                } else {
                    typeNames.add(item.getClass().getName());
                }
            }
            if (++inspected >= 100 || typeNames.size() >= 3)
                break;
        }
        if (typeNames.isEmpty())
            return "?";
        return String.join("|", typeNames);
    }

    // ── IDEntite helpers ────────────────────────────────────────────────

    private boolean isIDEntiteType(GenericObject obj) {
        try {
            StoredClass storedClass = delegate.storedClass(obj);
            while (storedClass != null) {
                String className = storedClass.getName();
                DOSchemaClass pkgClass = schema != null ? schema.findClassByName(className) : null;
                if (pkgClass != null && pkgClass.isIDEntite())
                    return true;
                storedClass = storedClass.getParentStoredClass();
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private Long extractMID(GenericObject obj) {
        try {
            delegate.activate(obj, 2);
            StoredClass storedClass = delegate.storedClass(obj);
            while (storedClass != null) {
                StoredField[] fields = storedClass.getStoredFields();
                if (fields != null) {
                    for (StoredField field : fields) {
                        if ("mID".equals(field.getName())) {
                            Object value = field.get(obj);
                            if (value instanceof Long)
                                return (Long) value;
                            if (value instanceof Integer)
                                return ((Integer) value).longValue();
                        }
                    }
                }
                storedClass = storedClass.getParentStoredClass();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // ── GenericObject summary ───────────────────────────────────────────

    private String formatGenericObjectSummary(GenericObject obj) {
        try {
            StoredClass storedClass = delegate.storedClass(obj);
            String className = storedClass != null ? storedClass.getName() : "Unknown";
            String simpleName = className.contains(".") ? className.substring(className.lastIndexOf('.') + 1) : className;

            try {
                delegate.activate(obj, 2);
            } catch (Exception ignored) {
            }

            String[] priorityFields = { "mNom", "iNom", "mLibelle", "iSommaire", "mCode", "iCode", "mID", "iIdentCol" };
            for (String fieldName : priorityFields) {
                Object fieldValue = getFieldValueDirect(obj, fieldName);
                if (fieldValue != null && !isEmptyValue(fieldValue)) {
                    return simpleName + "(" + formatSimpleValue(fieldValue) + ")";
                }
            }

            // Fallback: try any primitive/string field
            if (storedClass != null) {
                StoredField[] fields = storedClass.getStoredFields();
                if (fields != null) {
                    for (StoredField field : fields) {
                        try {
                            Object fv = field.get(obj);
                            if (fv != null && isPrimitiveOrString(fv) && !isEmptyValue(fv)) {
                                return simpleName + "(" + formatSimpleValue(fv) + ")";
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

            return simpleName + "(...)";
        } catch (Exception e) {
            return "[Object]";
        }
    }

    private Object getFieldValueDirect(GenericObject obj, String fieldName) {
        try {
            StoredClass storedClass = delegate.storedClass(obj);
            while (storedClass != null) {
                StoredField[] fields = storedClass.getStoredFields();
                if (fields != null) {
                    for (StoredField field : fields) {
                        if (field.getName().equals(fieldName))
                            return field.get(obj);
                    }
                }
                storedClass = storedClass.getParentStoredClass();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean isPrimitiveOrString(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Character;
    }

    private boolean isEmptyValue(Object value) {
        if (value == null)
            return true;
        if (value instanceof String)
            return ((String) value).trim().isEmpty();
        if (value instanceof Number) {
            long l = ((Number) value).longValue();
            return l == 0 || l == -1;
        }
        return false;
    }

    private String formatSimpleValue(Object value) {
        if (value instanceof String) {
            String str = (String) value;
            return str.length() > 30 ? str.substring(0, 27) + "..." : str;
        }
        return value.toString();
    }

    // ── Row interaction ─────────────────────────────────────────────────

    private void handleRowDoubleClick(int row) {
        Object objectIdValue = objectsTableModel.getValueAt(row, 1);
        if (objectIdValue == null)
            return;

        String idStr = objectIdValue.toString();
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(idStr), null);
        statusLabel.setText("Object ID " + idStr + " copied to clipboard");
    }

    // ── Inner types ─────────────────────────────────────────────────────

    private static class FieldInfo {
        final String fieldName;
        final String displayName;

        FieldInfo(String fieldName, String displayName) {
            this.fieldName = fieldName;
            this.displayName = displayName;
        }
    }
}
