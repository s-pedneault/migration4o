package migration4o.models.schema;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Parsed representation of a postProcessor spec string configured on a {@link DOSchemaClass}.
 *
 * <p>Spec format: {@code processorName} or {@code processorName(key=value, key=value)}.
 * Example: {@code file(path=attachments)}.
 *
 * <p>Instances are created once by {@code DOReferenceSchemaReader} when the schema is loaded
 * and stored on {@link DOSchemaClassAttributes#postProcessorSpec}.
 */
public class DOPostProcessorAttribute {

    /** Original unparsed spec string, preserved for round-trip serialization back to the schema XML. */
    public final String rawSpec;

    /** Name of the registered {@code ValuePostProcessor} to invoke. */
    public final String processorName;

    /** Immutable map of key/value parameters from the spec string. Empty if none were specified. */
    public final Map<String, String> params;

    private DOPostProcessorAttribute(String rawSpec, String processorName, Map<String, String> params) {
        this.rawSpec = rawSpec;
        this.processorName = processorName;
        this.params = params;
    }

    /**
     * Parses a spec string into a {@link DOPostProcessorAttribute}. Returns {@code null} if the spec is
     * null or blank.
     */
    public static DOPostProcessorAttribute parse(String spec) {
        if (spec == null || spec.isBlank()) {
            return null;
        }

        int parenIndex = spec.indexOf('(');
        if (parenIndex <= 0) {
            return new DOPostProcessorAttribute(spec, spec.trim(), Collections.emptyMap());
        }

        String name = spec.substring(0, parenIndex).trim();
        String paramsStr = spec.substring(parenIndex + 1, spec.length() - 1);
        Map<String, String> mutableParams = new HashMap<>();
        for (String param : paramsStr.split(",")) {
            String[] parts = param.trim().split("=", 2);
            if (parts.length == 2) {
                mutableParams.put(parts[0].trim(), parts[1].trim());
            }
        }
        return new DOPostProcessorAttribute(spec, name, Collections.unmodifiableMap(mutableParams));
    }

}
