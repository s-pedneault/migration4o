package migration4o.migration.xsd;

import java.io.IOException;

import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;

/**
 * Builds XSD (XML Schema) definitions for exported XML files.
 * <p>
 * During the export phase, callers register classes and fields via
 * {@link #addClass}, {@link #addTopLevelObject}, and {@link #addField}. After
 * export completes, {@link #writeXSD} generates the full schema file, including
 * iterative discovery of transitively referenced types.
 * <p>
 * Internal work is delegated to specialised writers in this package:
 * {@link XSDSchemaWriter} (document orchestration), {@link XSDClassWriter}
 * (class definitions), {@link XSDFieldWriter} (field elements), and
 * {@link XSDTypeMapper} (Java→XSD type mapping).
 */
public class XSDBuilder {

    private final XSDContext context = new XSDContext();

    /**
     * Registers a class for potential inclusion in the XSD. Uses the reference
     * schema to resolve the authoritative definition.
     */
    public void addClass(DOSchemaClass schemaClass) {
        context.registerClass(schemaClass);
    }

    /**
     * Registers a class as a top-level XML element in the XSD root choice.
     */
    public void addTopLevelObject(String destName, DOSchemaClass schemaClass) {
        context.registerTopLevelObject(destName, schemaClass);
    }

    /**
     * Registers a field for inclusion under its parent class in the XSD. Only
     * exported fields from the reference schema are recorded.
     */
    public void addField(DOSchemaClass parentClass, DOSchemaField field) {
        context.registerField(parentClass, field);
    }

    /**
     * Generates the complete XSD schema file at the given path.
     *
     * @param xsdPath absolute path for the output {@code .xsd} file
     */
    public void writeXSD(String xsdPath) throws IOException {
        new XSDSchemaWriter(context).write(xsdPath);
    }
}
