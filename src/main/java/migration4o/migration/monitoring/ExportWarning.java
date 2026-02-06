package migration4o.migration.monitoring;

import java.util.List;
import java.util.ArrayList;

/**
 * Represents a warning about duplicate object exports.
 * Analyzes multiple ObjectReferences to determine warning type and severity.
 */
public class ExportWarning {
    public enum WarningType {
        DUPLICATE_EMBEDDED_REFERENCE, // Same object referenced from multiple fields
        MISSING_EMBED_CONTENTS // Object exported as both embedded AND standalone
    }

    public final WarningType type;
    public final long objectId;
    public final String className;
    public final List<ObjectReference> references;

    /**
     * Creates a warning by analyzing all references to a duplicate object.
     */
    public ExportWarning(long objectId, String className, List<ObjectReference> references) {
        this.objectId = objectId;
        this.className = className;
        this.references = new ArrayList<>(references);
        this.type = determineWarningType(references);
    }

    /**
     * Determines warning type by analyzing reference patterns.
     */
    private static WarningType determineWarningType(List<ObjectReference> refs) {
        boolean hasEmbedded = false;
        boolean hasModule = false;

        for (ObjectReference ref : refs) {
            if (ref.isModuleExport()) {
                hasModule = true;
            }
            if (ref.isEmbeddedField()) {
                hasEmbedded = true;
            }
        }

        // Embedded + Module = schema configuration issue
        if (hasEmbedded && hasModule) {
            return WarningType.MISSING_EMBED_CONTENTS;
        }

        return WarningType.DUPLICATE_EMBEDDED_REFERENCE;
    }

    public int getReferenceCount() {
        return references.size();
    }

    public String getMessage() {
        if (type == WarningType.MISSING_EMBED_CONTENTS) {
            return String.format(
                    "SCHEMA CONFIG: Object (ID %d, class %s) exported %d times. " +
                            "Add embedContents=\"true\" to prevent duplicate exports.",
                    objectId, className, references.size());
        } else {
            return String.format(
                    "Object (ID %d, class %s) referenced %d times from different fields.",
                    objectId, className, references.size());
        }
    }

    /**
     * Returns formatted display strings for all references.
     */
    public List<String> getReferenceDisplayStrings() {
        List<String> displays = new ArrayList<>();
        for (ObjectReference ref : references) {
            displays.add(formatReferenceDisplay(ref));
        }
        return displays;
    }

    private String formatReferenceDisplay(ObjectReference ref) {
        if (ref.isModuleExport()) {
            return className + " [MODULE EXPORT]";
        } else if (ref.parentObjectId != null) {
            return String.format("%s[%d].%s",
                    ref.sourceContainingClass != null ? ref.sourceContainingClass : "?",
                    ref.parentObjectId,
                    ref.sourceFieldName != null ? ref.sourceFieldName : "?");
        } else {
            return className + " [UNKNOWN]";
        }
    }
}
