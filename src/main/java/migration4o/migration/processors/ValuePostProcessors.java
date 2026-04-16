package migration4o.migration.processors;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import migration4o.migration.format.ExportCurrentState;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.schema.DOPostProcessorAttribute;

/**
 * Registry and dispatcher for {@link ValuePostProcessor} implementations.
 * Postprocessors are keyed by name and configured on schema classes via the {@code postProcessor} attribute.
 */
public class ValuePostProcessors {

    private static final Map<String, ValuePostProcessor> processors;

    static {
        Map<String, ValuePostProcessor> map = new HashMap<>();
        map.put("file", new ValuePostProcessorFileContents());
        processors = Collections.unmodifiableMap(map);
    }

    /**
     * Applies the configured value postprocessor for the current schema class, if any.
     * Returns the original {@code fieldValue} immediately when no postprocessor is configured.
     *
     * @param object      The exported DB4O object.
     * @param fieldValue  The raw database value for the field.
     * @param schemaField The schema field definition.
     * @param ctx         Full export context.
     * @return The (possibly overridden) field value.
     */
    public static Object processField(Object object, Object fieldValue, DOSchemaField schemaField, ExportCurrentState ctx) {
        if (ctx.schemaClass == null || ctx.schemaClass.attributes.postProcessor == null) {
            return fieldValue;
        }

        DOPostProcessorAttribute attributes = ctx.schemaClass.attributes.postProcessor;
        ValuePostProcessor processor = processors.get(attributes.processorName);
        if (processor == null) {
            return fieldValue;
        }

        try {
            return processor.processField(object, fieldValue, schemaField, ctx, attributes);
        } catch (Exception e) {
            System.err.println("[WARN] ValuePostProcessor '" + attributes.processorName + "' threw on field '" + (schemaField != null ? schemaField.attributes.destinationName : "?") + "': " + e.getMessage());
            return fieldValue;
        }
    }

}
