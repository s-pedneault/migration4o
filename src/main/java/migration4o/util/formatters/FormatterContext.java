package migration4o.util.formatters;

import java.nio.file.Path;

import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;

/**
 * Context passed to {@link ValueFormatter} implementations during export. Carries export-time information that formatters may need beyond the raw value and schema field (e.g. where to copy files to, sibling field access).
 */
public class FormatterContext {

    /** Root output directory for the current export run. */
    public final Path destinationFolder;

    /** Schema class of the object currently being exported. */
    public final DOSchemaClass schemaClass;

    /** Schema field being formatted. */
    public final DOSchemaField schemaField;

    /** The raw DB4O object currently being exported (typically a {@code GenericObject}). */
    public final Object currentObject;

    public FormatterContext(Path destinationFolder, DOSchemaClass schemaClass, DOSchemaField schemaField, Object currentObject) {
        this.destinationFolder = destinationFolder;
        this.schemaClass = schemaClass;
        this.schemaField = schemaField;
        this.currentObject = currentObject;
    }

}
