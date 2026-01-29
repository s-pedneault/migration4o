package migration4o.ui.panels.database_panels.migration_coverage_panel.dialogs;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

import com.db4o.ext.ExtObjectContainer;

import migration4o.database.DODatabaseOpener;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.util.ObjectResolverUtil;

/**
 * Dialog to display all objects of a specific class from the database.
 * Provides pagination and detailed field inspection.
 */
public class ClassObjectsDialog extends JDialog {
    private static final int PAGE_SIZE = 100;

    private final DOSchemaClass schemaClass;
    private final DOSchema schema;
    private final String databasePath;

    private JTable objectsTable;
    private DefaultTableModel objectsTableModel;
    private JLabel statusLabel;
    private JButton prevButton;
    private JButton nextButton;
    private JCheckBox showUniqueCheckbox;
    private int currentPage = 0;
    private long[] objectIds;
    private boolean showOnlyUnique = true;

    public ClassObjectsDialog(java.awt.Frame parent, String className, DOSchemaClass schemaClass,
            DOSchema schema, String databasePath) {
        super(parent, "Objects: " + className, true);
        this.schemaClass = schemaClass;
        this.schema = schema;
        this.databasePath = databasePath;

        initializeUI();
        loadObjectIds();
        loadPage(0);
    }

    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));
        setSize(1000, 600);
        setLocationRelativeTo(getParent());

        // Top panel with status label and checkbox
        JPanel topPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("Loading...");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        topPanel.add(statusLabel, BorderLayout.CENTER);

        showUniqueCheckbox = new JCheckBox("Show only unique objects", true);
        showUniqueCheckbox.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        showUniqueCheckbox.addActionListener(e -> {
            showOnlyUnique = showUniqueCheckbox.isSelected();
            loadObjectIds();
            loadPage(0);
        });
        topPanel.add(showUniqueCheckbox, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);

        // Table in center
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

        JScrollPane scrollPane = new JScrollPane(objectsTable);
        add(scrollPane, BorderLayout.CENTER);

        // Pagination panel at bottom
        JPanel paginationPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        prevButton = new JButton("← Previous");
        prevButton.addActionListener(e -> loadPage(currentPage - 1));
        paginationPanel.add(prevButton);

        nextButton = new JButton("Next →");
        nextButton.addActionListener(e -> loadPage(currentPage + 1));
        paginationPanel.add(nextButton);

        add(paginationPanel, BorderLayout.SOUTH);
    }

    private void loadObjectIds() {
        if (showOnlyUnique) {
            objectIds = schemaClass.uniqueObjectIds != null ? schemaClass.uniqueObjectIds : new long[0];
        } else {
            objectIds = schemaClass.objectIds != null ? schemaClass.objectIds : new long[0];
        }
    }

    private void loadPage(int pageNumber) {
        currentPage = pageNumber;

        int totalPages = (int) Math.ceil((double) objectIds.length / PAGE_SIZE);
        if (totalPages == 0)
            totalPages = 1;

        // Update status
        int startIdx = currentPage * PAGE_SIZE;
        int endIdx = Math.min(startIdx + PAGE_SIZE, objectIds.length);
        statusLabel.setText(String.format("Page %d of %d - Showing objects %d to %d of %d total",
                currentPage + 1, totalPages, startIdx + 1, endIdx, objectIds.length));

        // Update buttons
        prevButton.setEnabled(currentPage > 0);
        nextButton.setEnabled(currentPage < totalPages - 1);

        // Load objects for this page
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                loadObjectsFromDatabase(startIdx, endIdx);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(ClassObjectsDialog.this,
                            "Error loading objects: " + ex.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    private void loadObjectsFromDatabase(int startIdx, int endIdx) {
        ExtObjectContainer container = null;
        try {
            // Open database
            DODatabaseOpener opener = new DODatabaseOpener();
            container = opener.openDatabase(databasePath);

            // Get all fields including inherited ones
            List<FieldInfo> fields = collectAllFields(schemaClass, schema);

            // Build column names
            String[] columnNames = new String[fields.size() + 1];
            columnNames[0] = "Object ID";
            for (int i = 0; i < fields.size(); i++) {
                columnNames[i + 1] = fields.get(i).displayName;
            }

            // Clear and set columns
            SwingUtilities.invokeLater(() -> {
                objectsTableModel.setColumnCount(0);
                objectsTableModel.setRowCount(0);
                for (String colName : columnNames) {
                    objectsTableModel.addColumn(colName);
                }
            });

            // Load objects
            for (int i = startIdx; i < endIdx; i++) {
                long objectId = objectIds[i];
                try {
                    Object obj = container.ext().getByID(objectId);
                    if (obj != null) {
                        // Activate the object to ensure all fields are loaded
                        ObjectResolverUtil.activateObject(container, obj, objectId);

                        Object[] rowData = new Object[fields.size() + 1];
                        rowData[0] = objectId;

                        // Extract field values
                        if (obj instanceof com.db4o.reflect.generic.GenericObject) {
                            com.db4o.reflect.generic.GenericObject genericObj = (com.db4o.reflect.generic.GenericObject) obj;

                            for (int fieldIdx = 0; fieldIdx < fields.size(); fieldIdx++) {
                                FieldInfo fieldInfo = fields.get(fieldIdx);
                                Object value = getFieldValue(container, genericObj, fieldInfo.fieldName);
                                rowData[fieldIdx + 1] = formatValue(container, value);
                            }
                        }

                        final Object[] finalRowData = rowData;
                        SwingUtilities.invokeLater(() -> objectsTableModel.addRow(finalRowData));
                    }
                } catch (Exception e) {
                    System.err.println("Error loading object " + objectId + ": " + e.getMessage());
                }
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                    "Error opening database: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE));
        } finally {
            if (container != null) {
                container.close();
            }
        }
    }

    /**
     * Collect all fields including inherited ones from superclasses.
     */
    private List<FieldInfo> collectAllFields(DOSchemaClass targetClass, DOSchema schema) {
        List<FieldInfo> allFields = new ArrayList<>();
        Set<String> addedFields = new HashSet<>();

        // Walk up the inheritance hierarchy
        DOSchemaClass currentClass = targetClass;
        while (currentClass != null) {
            if (currentClass.fields != null) {
                for (DOSchemaField field : currentClass.fields) {
                    String fieldName = field.source;
                    if (!addedFields.contains(fieldName)) {
                        String currentShortName = currentClass.source != null && currentClass.source.contains(".")
                                ? currentClass.source.substring(currentClass.source.lastIndexOf('.') + 1)
                                : currentClass.source;
                        String displayName = currentClass.source.equals(targetClass.source)
                                ? fieldName
                                : currentShortName + "." + fieldName;
                        allFields.add(new FieldInfo(fieldName, displayName));
                        addedFields.add(fieldName);
                    }
                }
            }

            // Move to parent class
            String parentName = currentClass.parentClassName;
            if (parentName == null || parentName.isEmpty() || parentName.equals("Undetermined")) {
                break;
            }

            currentClass = findClassByName(schema, parentName);
        }

        return allFields;
    }

    /**
     * Find a class by name in the schema.
     */
    private DOSchemaClass findClassByName(DOSchema schema, String className) {
        if (schema == null || schema.getClasses() == null) {
            return null;
        }
        for (DOSchemaClass cls : schema.getClasses()) {
            String clsShortName = cls.source != null && cls.source.contains(".")
                    ? cls.source.substring(cls.source.lastIndexOf('.') + 1)
                    : cls.source;
            if (className.equals(clsShortName) || className.equals(cls.source)) {
                return cls;
            }
        }
        return null;
    }

    /**
     * Get field value from a GenericObject.
     * Searches through the entire class hierarchy to find the field.
     */
    private Object getFieldValue(ExtObjectContainer container,
            com.db4o.reflect.generic.GenericObject obj,
            String fieldName) {
        try {
            // Start with the object's immediate class
            com.db4o.ext.StoredClass storedClass = container.ext().storedClass(obj);

            // Search up the hierarchy for the field
            while (storedClass != null) {
                com.db4o.ext.StoredField[] fields = storedClass.getStoredFields();
                if (fields != null) {
                    for (com.db4o.ext.StoredField field : fields) {
                        if (field.getName().equals(fieldName)) {
                            Object value = field.get(obj);
                            return value;
                        }
                    }
                }

                // Move to parent class
                storedClass = storedClass.getParentStoredClass();
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
        return null;
    }

    /**
     * Format a value for display in the table.
     */
    private String formatValue(ExtObjectContainer container, Object value) {
        if (value == null) {
            return "";
        }

        // Handle collections
        if (value instanceof java.util.Collection) {
            java.util.Collection<?> collection = (java.util.Collection<?>) value;

            // Empty collection
            if (collection.isEmpty()) {
                return "[]";
            }

            // Check if it's a collection of IDEntite objects
            Object firstItem = collection.iterator().next();
            if (firstItem instanceof com.db4o.reflect.generic.GenericObject) {
                com.db4o.reflect.generic.GenericObject firstGenericObj = (com.db4o.reflect.generic.GenericObject) firstItem;

                if (isIDEntiteType(container, firstGenericObj)) {
                    // Extract mID values from all IDEntite objects in the collection
                    StringBuilder sb = new StringBuilder("[");
                    boolean first = true;
                    for (Object item : collection) {
                        if (item instanceof com.db4o.reflect.generic.GenericObject) {
                            Long mID = extractMIDFromIDEntite(container,
                                    (com.db4o.reflect.generic.GenericObject) item);
                            if (mID != null) {
                                if (!first) {
                                    sb.append(", ");
                                }
                                sb.append(mID);
                                first = false;
                            }
                        }
                    }
                    sb.append("]");
                    return sb.toString();
                }
            }

            // Non-IDEntite collection - show size
            return "[Collection: " + collection.size() + " items]";
        }

        // Handle GenericObject (potential IDEntite)
        if (value instanceof com.db4o.reflect.generic.GenericObject) {
            com.db4o.reflect.generic.GenericObject genericObj = (com.db4o.reflect.generic.GenericObject) value;

            if (isIDEntiteType(container, genericObj)) {
                // Extract mID value
                Long mID = extractMIDFromIDEntite(container, genericObj);
                // Get object ID
                Long objectId = null;
                try {
                    objectId = container.ext().getID(genericObj);
                } catch (Exception e) {
                    // Could not get object ID
                }

                if (mID != null && objectId != null && objectId > 0) {
                    return mID + " (" + objectId + ")";
                } else if (mID != null) {
                    return String.valueOf(mID);
                }
            }

            return "[Object]";
        }

        return value.toString();
    }

    /**
     * Check if a GenericObject is an IDEntite type by checking its class hierarchy.
     */
    private boolean isIDEntiteType(ExtObjectContainer container, com.db4o.reflect.generic.GenericObject obj) {
        try {
            com.db4o.ext.StoredClass storedClass = container.ext().storedClass(obj);

            while (storedClass != null) {
                String className = storedClass.getName();
                DOSchemaClass pkgClass = findClass(className);
                if (pkgClass != null && pkgClass.isIDEntite(schema)) {
                    return true;
                }
                storedClass = storedClass.getParentStoredClass();
            }
        } catch (Exception e) {
            // Could not determine type
        }
        return false;
    }

    /**
     * Find a class in the schema by name.
     */
    private DOSchemaClass findClass(String className) {
        if (className == null || schema == null || schema.getClasses() == null) {
            return null;
        }
        for (DOSchemaClass cls : schema.getClasses()) {
            if (className.equals(cls.source)) {
                return cls;
            }
        }
        return null;
    }

    /**
     * Extract mID field value from an IDEntite GenericObject.
     */
    private Long extractMIDFromIDEntite(ExtObjectContainer container, com.db4o.reflect.generic.GenericObject obj) {
        try {
            // Activate the object to ensure mID is loaded
            container.activate(obj, 2);

            // Search for mID field in the object's class hierarchy
            com.db4o.ext.StoredClass storedClass = container.ext().storedClass(obj);
            while (storedClass != null) {
                com.db4o.ext.StoredField[] fields = storedClass.getStoredFields();
                if (fields != null) {
                    for (com.db4o.ext.StoredField field : fields) {
                        if ("mID".equals(field.getName())) {
                            Object value = field.get(obj);
                            if (value instanceof Long) {
                                return (Long) value;
                            } else if (value instanceof Integer) {
                                return ((Integer) value).longValue();
                            }
                        }
                    }
                }
                storedClass = storedClass.getParentStoredClass();
            }
        } catch (Exception e) {
            // Could not extract mID
        }
        return null;
    }

    /**
     * Helper class to store field information.
     */
    private static class FieldInfo {
        final String fieldName;
        final String displayName;

        FieldInfo(String fieldName, String displayName) {
            this.fieldName = fieldName;
            this.displayName = displayName;
        }
    }
}
