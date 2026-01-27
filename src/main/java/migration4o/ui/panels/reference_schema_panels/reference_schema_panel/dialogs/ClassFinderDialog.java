package migration4o.ui.panels.reference_schema_panels.reference_schema_panel.dialogs;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.ui.common.dialogs.FilteredListDialog;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog for finding and selecting class names (primitives or schema classes).
 * Extends FilteredListDialog to provide search and selection functionality.
 */
public class ClassFinderDialog extends FilteredListDialog<String> {

    private final DOSchema schema;

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
        super(owner, "Class Finder", initialValue, "Type to search classes...");
        this.schema = schema;
    }

    @Override
    protected List<String> getAllItems() {
        List<String> allItems = new ArrayList<>();

        // Add all primitive types
        for (String primitive : PRIMITIVES) {
            allItems.add(primitive);
        }

        // Add all schema classes
        if (schema != null && schema.getClasses() != null) {
            for (DOSchemaClass cls : schema.getClasses()) {
                allItems.add(cls.source);
            }
        }

        return allItems;
    }

    @Override
    protected List<String> filterItems(String pattern) {
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

        return matches;
    }

    @Override
    protected boolean supportsNullSelection() {
        return true; // ClassFinder supports clearing the selection
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

        // Return empty string if cleared, otherwise return selected value or null
        if (dialog.wasCleared()) {
            return "";
        }
        return dialog.getSelectedValue();
    }
}
