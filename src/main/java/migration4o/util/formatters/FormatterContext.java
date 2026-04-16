package migration4o.util.formatters;

import java.nio.file.Path;

import migration4o.migration.FilesDestination;
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

    /**
     * Controls whether attached files should be copied to the {@code file/} folder
     * ({@link FilesDestination#FOLDER}) or embedded inline ({@link FilesDestination#EMBED}).
     * Defaults to {@code FOLDER} so existing callsites that do not set this field are unaffected.
     */
    public FilesDestination filesDestination = FilesDestination.FOLDER;

    public FormatterContext(Path destinationFolder, DOSchemaClass schemaClass, Object currentObject) {
        this(destinationFolder, schemaClass, null, currentObject);
    }

    public FormatterContext(Path destinationFolder, DOSchemaClass schemaClass, DOSchemaField schemaField, Object currentObject) {
        this.destinationFolder = destinationFolder;
        this.schemaClass = schemaClass;
        this.schemaField = schemaField;
        this.currentObject = currentObject;
    }

}
