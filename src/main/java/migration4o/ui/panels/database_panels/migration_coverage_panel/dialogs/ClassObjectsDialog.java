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
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

import com.db4o.ext.ExtObjectContainer;

import migration4o.database.DODatabaseService;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.util.CollectionUtil;
import migration4o.util.ObjectResolverUtil;

/**
 * Frame to display all objects of a specific class from the database.
 * Provides pagination and detailed field inspection.
 */
public class ClassObjectsDialog extends JFrame {
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
    private JCheckBox showUnreachedCheckbox;
    private int currentPage = 0;
    private long[] objectIds;
    private boolean showOnlyUnique = true;
    private boolean showOnlyUnreached = false;

    public ClassObjectsDialog(java.awt.Frame parent, String className, DOSchemaClass schemaClass,
            DOSchema schema, String databasePath) {
        super("Objects: " + className);
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
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // Top panel with status label and checkboxes
        JPanel topPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("Loading...");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        topPanel.add(statusLabel, BorderLayout.CENTER);

        // Right panel for checkboxes
        JPanel checkboxPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        showUniqueCheckbox = new JCheckBox("Show only unique objects", true);
        showUniqueCheckbox.addActionListener(e -> {
            showOnlyUnique = showUniqueCheckbox.isSelected();
            loadObjectIds();
            loadPage(0);
        });
        checkboxPanel.add(showUniqueCheckbox);

        showUnreachedCheckbox = new JCheckBox("Show only unreached objects", false);
        showUnreachedCheckbox.addActionListener(e -> {
            showOnlyUnreached = showUnreachedCheckbox.isSelected();
            loadObjectIds();
            loadPage(0);
        });
        checkboxPanel.add(showUnreachedCheckbox);

        topPanel.add(checkboxPanel, BorderLayout.EAST);

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

        // Add double-click listener to find references to selected object
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
        // Start with the base set of IDs based on unique filter
        long[] baseIds;
        if (showOnlyUnique) {
            baseIds = schemaClass.uniqueObjectIds != null ? schemaClass.uniqueObjectIds : new long[0];
        } else {
            baseIds = schemaClass.objectIds != null ? schemaClass.objectIds : new long[0];
        }

        // Apply unreached filter if enabled
        if (showOnlyUnreached) {
            // Get the set of reached object IDs
            Set<Long> reachedIds = new HashSet<>();
            if (schemaClass.reachedObjectIds != null) {
                for (long id : schemaClass.reachedObjectIds) {
                    reachedIds.add(id);
                }
            }

            // Filter out reached objects
            List<Long> unreachedList = new ArrayList<>();
            for (long id : baseIds) {
                if (!reachedIds.contains(id)) {
                    unreachedList.add(id);
                }
            }

            // Convert back to array
            objectIds = new long[unreachedList.size()];
            for (int i = 0; i < unreachedList.size(); i++) {
                objectIds[i] = unreachedList.get(i);
            }
        } else {
            objectIds = baseIds;
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
        try {
            // Get the shared in-memory database container
            ExtObjectContainer container = DODatabaseService.getInstance().getContainer();

            if (container == null || container.ext().isClosed()) {
                throw new IllegalStateException("No database is currently open.");
            }

            // Get all fields including inherited ones
            List<FieldInfo> fields = collectAllFields(schemaClass, schema);

            // Build column names
            String[] columnNames = new String[fields.size() + 2];
            columnNames[0] = "Class";
            columnNames[1] = "Object ID";
            for (int i = 0; i < fields.size(); i++) {
                columnNames[i + 2] = fields.get(i).displayName;
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
                        // Activate to depth 1 - enough to populate field references
                        // Don't go deeper to avoid TCollection translator errors
                        try {
                            container.activate(obj, 1);
                        } catch (Exception e) {
                            // Ignore activation errors
                        }

                        Object[] rowData = new Object[fields.size() + 2];

                        // Get actual class name from object
                        String actualClassName = obj instanceof com.db4o.reflect.generic.GenericObject
                                ? ((com.db4o.reflect.generic.GenericObject) obj).getGenericClass().getName()
                                : obj.getClass().getName();
                        String shortClassName = actualClassName.contains(".")
                                ? actualClassName.substring(actualClassName.lastIndexOf('.') + 1)
                                : actualClassName;

                        rowData[0] = shortClassName;
                        rowData[1] = objectId;

                        // Extract field values
                        if (obj instanceof com.db4o.reflect.generic.GenericObject) {
                            com.db4o.reflect.generic.GenericObject genericObj = (com.db4o.reflect.generic.GenericObject) obj;

                            for (int fieldIdx = 0; fieldIdx < fields.size(); fieldIdx++) {
                                FieldInfo fieldInfo = fields.get(fieldIdx);
                                Object value = getFieldValue(container, genericObj, fieldInfo.fieldName);
                                rowData[fieldIdx + 2] = formatValue(container, value);
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
                    "Error loading objects: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE));
        }
        // Note: We do NOT close the container here as it's a shared resource managed by
        // DODatabaseService
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
                            // Activate collections to populate their contents
                            if (value instanceof java.util.Collection) {
                                try {
                                    container.activate(value, 1);
                                } catch (Exception e) {
                                    // Ignore
                                }
                            }
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

        // Check if it's already been formatted as a string
        if (value instanceof String) {
            String strValue = (String) value;
            if (strValue.equals("[Object]")) {
                // This shouldn't happen - means value was pre-formatted
                System.err.println("WARNING: Received pre-formatted [Object] string");
                return strValue;
            }
            return strValue;
        }

        // Handle collections (using unified CollectionUtil to handle both Collection
        // and GenericObject)
        java.util.Collection<?> collection = CollectionUtil.extractCollectionItems(container, value);
        // System.err.println("DEBUG formatValue: value class = " +
        // value.getClass().getName());
        // System.err.println("DEBUG formatValue: extracted collection = " +
        // collection);
        // System.err.println("DEBUG formatValue: collection size = " + (collection !=
        // null ? collection.size() : "null"));
        if (collection != null) {
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
                } else {
                    // Collection of non-IDEntite GenericObjects - show meaningful summaries
                    StringBuilder sb = new StringBuilder("[");
                    boolean first = true;
                    int count = 0;
                    int maxItems = 3; // Limit to 3 items to avoid huge strings
                    for (Object item : collection) {
                        if (item instanceof com.db4o.reflect.generic.GenericObject) {
                            if (count >= maxItems) {
                                sb.append(", ..."); // More items
                                break;
                            }
                            if (!first) {
                                sb.append(", ");
                            }
                            sb.append(formatGenericObjectSummary(container,
                                    (com.db4o.reflect.generic.GenericObject) item));
                            first = false;
                            count++;
                        }
                    }
                    sb.append("]");
                    return sb.toString();
                }
            }

            // Non-GenericObject collection - show size
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

            // For other objects, show type and meaningful field summary
            return formatGenericObjectSummary(container, genericObj);
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
     * Format a GenericObject into a meaningful summary showing type and key fields.
     */
    private String formatGenericObjectSummary(ExtObjectContainer container,
            com.db4o.reflect.generic.GenericObject obj) {
        try {
            // Get the class name
            com.db4o.ext.StoredClass storedClass = container.ext().storedClass(obj);
            String className = storedClass != null ? storedClass.getName() : "Unknown";

            // System.out.println("DEBUG: Formatting GenericObject of type: " + className);

            // Simplify class name (remove package)
            String simpleClassName = className;
            if (className.contains(".")) {
                simpleClassName = className.substring(className.lastIndexOf('.') + 1);
            }

            // Try to activate the object to load its fields, but don't fail if activation
            // fails
            try {
                container.activate(obj, 2);
            } catch (Exception activateEx) {
                // Activation failed - continue anyway, we'll try to get fields without full
                // activation
                System.out.println("DEBUG: Could not activate object, continuing without activation");
            }

            // Extract key fields for summary (prioritize common identifier fields)
            StringBuilder summary = new StringBuilder();
            summary.append(simpleClassName).append("(");

            boolean foundField = false;
            String[] priorityFields = { "mNom", "iNom", "mLibelle", "iSommaire", "mCode", "iCode", "mID", "iIdentCol" };

            // Try priority fields first
            for (String fieldName : priorityFields) {
                Object fieldValue = getFieldValueFromObject(container, obj, fieldName);
                if (fieldValue != null && !isEmptyValue(fieldValue)) {
                    if (foundField) {
                        summary.append(", ");
                    }
                    summary.append(formatSimpleValue(fieldValue));
                    foundField = true;
                    // System.out.println("DEBUG: Found priority field " + fieldName + " = " +
                    // fieldValue);
                    break; // Only show first meaningful field
                }
            }

            // If no priority fields found, try to get any meaningful primitive field
            if (!foundField) {
                com.db4o.ext.StoredField[] fields = storedClass.getStoredFields();
                if (fields != null) {
                    for (com.db4o.ext.StoredField field : fields) {
                        try {
                            Object fieldValue = field.get(obj);
                            // System.out.println(
                            // "DEBUG: Checking field " + field.getName() + " = " + fieldValue + " (type: "
                            // + (fieldValue != null ? fieldValue.getClass().getName() : "null") + ")");
                            if (fieldValue != null && isPrimitiveOrString(fieldValue) && !isEmptyValue(fieldValue)) {
                                summary.append(formatSimpleValue(fieldValue));
                                foundField = true;
                                break;
                            }
                        } catch (Exception fieldEx) {
                            // Skip this field if we can't get its value
                            System.out.println(
                                    "DEBUG: Could not get field " + field.getName() + ": " + fieldEx.getMessage());
                        }
                    }
                }
            }

            summary.append(")");
            String result = summary.toString();
            // System.out.println("DEBUG: Formatted summary: " + result);
            return result;

        } catch (Exception e) {
            System.err.println("ERROR: Exception formatting GenericObject: " + e.getMessage());
            e.printStackTrace();
            return "[Object]";
        }
    }

    /**
     * Get a specific field value from a GenericObject.
     */
    private Object getFieldValueFromObject(ExtObjectContainer container, com.db4o.reflect.generic.GenericObject obj,
            String fieldName) {
        try {
            com.db4o.ext.StoredClass storedClass = container.ext().storedClass(obj);
            while (storedClass != null) {
                com.db4o.ext.StoredField[] fields = storedClass.getStoredFields();
                if (fields != null) {
                    for (com.db4o.ext.StoredField field : fields) {
                        if (field.getName().equals(fieldName)) {
                            return field.get(obj);
                        }
                    }
                }
                storedClass = storedClass.getParentStoredClass();
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    /**
     * Check if a value is a primitive type or String.
     */
    private boolean isPrimitiveOrString(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Character;
    }

    /**
     * Check if a value is considered empty.
     */
    private boolean isEmptyValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String) {
            return ((String) value).trim().isEmpty();
        }
        if (value instanceof Number) {
            return ((Number) value).longValue() == 0 || ((Number) value).longValue() == -1;
        }
        return false;
    }

    /**
     * Format a simple value for display in summary.
     */
    private String formatSimpleValue(Object value) {
        if (value instanceof String) {
            String str = (String) value;
            // Truncate long strings
            if (str.length() > 30) {
                return str.substring(0, 27) + "...";
            }
            return str;
        }
        return value.toString();
    }

    /**
     * Handles double-click on a table row to find references to the selected
     * object.
     */
    private void handleRowDoubleClick(int row) {
        // Get the object ID from the clicked row (now in column 1, not 0)
        Object objectIdValue = objectsTableModel.getValueAt(row, 1);
        if (objectIdValue == null) {
            return;
        }

        long selectedObjectId;
        try {
            selectedObjectId = Long.parseLong(objectIdValue.toString());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,
                    "Invalid object ID",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Open ID Tracer with automatic search for this object
        IDTracerDialog tracerDialog = new IDTracerDialog();
        tracerDialog.setSearchId(selectedObjectId);
        tracerDialog.setVisible(true);
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
