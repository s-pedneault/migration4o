package migration4o.migration.xsd;

import java.io.IOException;

/**
 * Entry point for XSD (XML Schema) generation.
 * <p>
 * Generates a comprehensive XSD from the full reference schema. All exported
 * classes ({@code migrate=true}) are included — no observation-based
 * registration is needed.
 * <p>
 * Internal work is delegated to specialised writers in this package:
 * {@link XSDSchemaWriter} (document orchestration), {@link XSDClassWriter}
 * (class definitions), {@link XSDFieldWriter} (field elements), and
 * {@link XSDTypeMapper} (Java→XSD type mapping).
 */
public class XSDBuilder {

    private final XSDContext context = new XSDContext();

    /**
     * Generates the complete XSD schema file at the given path.
     *
     * @param xsdPath absolute path for the output {@code .xsd} file
     */
    public void writeXSD(String xsdPath) throws IOException {
        new XSDSchemaWriter(context).write(xsdPath);
    }
}
