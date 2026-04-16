package migration4o.migration.processors;

import migration4o.migration.format.ExportCurrentState;
import migration4o.models.schema.DOPostProcessorAttribute;
import migration4o.models.schema.DOSchemaField;

/**
 * Intercepts field values during export, allowing a postprocessor to override the value of any field
 * on a configured schema class. Implement this interface to provide custom value computation.
 *
 * <p>Register implementations in {@link ValuePostProcessors}.
 * Configure on a schema class via the {@code postProcessor} attribute (e.g. {@code file(path=data)}).
 */
public interface ValuePostProcessor {

    /**
     * Optionally overrides the value of a field being exported.
     *
     * @param object      The exported DB4O object. Use {@code ctx.delegate} to read other fields on it.
     * @param fieldValue  The raw value read from the database for this field. May be null.
     * @param schemaField The schema field definition (source name, destination name, type, etc.).
     * @param ctx         Full export context: delegate, schema, base path, statistics, etc.
     * @param attribute   Parsed postProcessor attribute containing the processor name and parameters.
     * @return The value to export. Return {@code fieldValue} unchanged for no-op; return a custom value to override.
     */
    Object processField(Object object, Object fieldValue, DOSchemaField schemaField, ExportCurrentState ctx, DOPostProcessorAttribute attributes);

}
