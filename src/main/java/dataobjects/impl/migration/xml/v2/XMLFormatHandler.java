package dataobjects.impl.migration.xml.v2;

import dataobjects.api.migration.generic.v2.*;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Production-ready XML format handler using the v2 modular architecture.
 * Generates XML files with the correct <database><modules> structure and actual
 * data.
 */
public class XMLFormatHandler extends HierarchicalFormatHandler {

    private static final String DEFAULT_OUTPUT_DIR = "output/migration/data";

    /**
     * Module-level context for XML export.
     */
    private static class XMLModuleContext {
        XMLStreamWriter writer;
        FileOutputStream outputStream;
        String fileName;

        void close() throws IOException {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (XMLStreamException e) {
                throw new IOException("Error closing XML writer: " + e.getMessage(), e);
            } finally {
                if (outputStream != null) {
                    outputStream.close();
                }
            }
        }
    }

    /**
     * Class-level context for XML export.
     */
    private static class XMLClassContext {
        XMLModuleContext moduleContext;
        boolean hasWrittenObjects = false;
    }

    @Override
    public String getDefaultOutputDirectory() {
        return DEFAULT_OUTPUT_DIR;
    }

    @Override
    public Object beginModule(ModuleExportContext context) throws IOException {
        XMLModuleContext xmlContext = new XMLModuleContext();

        try {
            // Create output file
            xmlContext.fileName = generateModuleFileName(context, ".xml");
            File outputFile = new File(outputDirectory, xmlContext.fileName);
            xmlContext.outputStream = new FileOutputStream(outputFile);

            // Create XML writer
            XMLOutputFactory factory = XMLOutputFactory.newInstance();
            xmlContext.writer = factory.createXMLStreamWriter(xmlContext.outputStream, "UTF-8");

            // Write XML declaration and root elements
            xmlContext.writer.writeStartDocument("UTF-8", "1.0");
            xmlContext.writer.writeCharacters("\n");

            xmlContext.writer.writeStartElement("database");
            xmlContext.writer.writeCharacters("\n  ");

            xmlContext.writer.writeStartElement("modules");
            xmlContext.writer.writeCharacters("\n    ");

            xmlContext.writer.writeStartElement("module");
            xmlContext.writer.writeAttribute("name", context.getModuleName());
            xmlContext.writer.writeCharacters("\n");

            return xmlContext;

        } catch (Exception e) {
            // Clean up on error
            if (xmlContext.outputStream != null) {
                try {
                    xmlContext.outputStream.close();
                } catch (IOException ignored) {
                }
            }
            throw new IOException("Failed to begin XML module: " + e.getMessage(), e);
        }
    }

    @Override
    public Object beginClass(Object moduleHandle, ClassExportContext context) throws IOException {
        XMLModuleContext moduleCtx = (XMLModuleContext) moduleHandle;
        XMLClassContext classCtx = new XMLClassContext();
        classCtx.moduleContext = moduleCtx;

        try {
            // Only write class element if we have objects to export
            if (context.getTotalObjectCount() > 0) {
                moduleCtx.writer.writeCharacters("      ");
                moduleCtx.writer.writeStartElement("class");
                moduleCtx.writer.writeAttribute("type", context.getExportName());
                moduleCtx.writer.writeAttribute("count", String.valueOf(context.getTotalObjectCount()));
                moduleCtx.writer.writeCharacters("\n");
            }

            return classCtx;

        } catch (XMLStreamException e) {
            throw new IOException("Failed to begin XML class: " + e.getMessage(), e);
        }
    }

    @Override
    public void exportObject(Object classHandle, ObjectExportContext context, List<FormattedValue> values)
            throws IOException {

        XMLClassContext classCtx = (XMLClassContext) classHandle;
        XMLStreamWriter writer = classCtx.moduleContext.writer;

        try {
            // Write object start element
            writer.writeCharacters("        ");
            writer.writeStartElement("object");
            writer.writeAttribute("id", String.valueOf(context.getObjectId()));
            writer.writeCharacters("\n");

            // Write all non-empty field values
            List<FormattedValue> nonEmptyValues = getNonEmptyValues(values);
            for (FormattedValue value : nonEmptyValues) {
                writeFieldElement(writer, value);
            }

            // Write object end element
            writer.writeCharacters("        ");
            writer.writeEndElement(); // object
            writer.writeCharacters("\n");

            classCtx.hasWrittenObjects = true;

        } catch (XMLStreamException e) {
            throw new IOException("Failed to export XML object: " + e.getMessage(), e);
        }
    }

    @Override
    public void endClass(Object classHandle, ClassExportContext context, int exportedCount) throws IOException {
        XMLClassContext classCtx = (XMLClassContext) classHandle;

        try {
            // Only close class element if we opened it and wrote objects
            if (exportedCount > 0 && classCtx.hasWrittenObjects) {
                classCtx.moduleContext.writer.writeCharacters("      ");
                classCtx.moduleContext.writer.writeEndElement(); // class
                classCtx.moduleContext.writer.writeCharacters("\n");
            }

        } catch (XMLStreamException e) {
            throw new IOException("Failed to end XML class: " + e.getMessage(), e);
        }
    }

    @Override
    public void endModule(Object moduleHandle, ModuleExportContext context) throws IOException {
        XMLModuleContext moduleCtx = (XMLModuleContext) moduleHandle;

        try {
            // Close all XML elements
            moduleCtx.writer.writeCharacters("    ");
            moduleCtx.writer.writeEndElement(); // module
            moduleCtx.writer.writeCharacters("\n  ");

            moduleCtx.writer.writeEndElement(); // modules
            moduleCtx.writer.writeCharacters("\n");

            moduleCtx.writer.writeEndElement(); // database
            moduleCtx.writer.writeCharacters("\n");

            moduleCtx.writer.writeEndDocument();
            moduleCtx.writer.flush();

            System.out.println("  Exported module " + context.getModuleName() + " to " + moduleCtx.fileName);

        } catch (XMLStreamException e) {
            throw new IOException("Failed to end XML module: " + e.getMessage(), e);
        } finally {
            moduleCtx.close();
        }
    }

    @Override
    public void cleanup() throws IOException {
        // Nothing to clean up - each module handles its own resources
    }

    /**
     * Write a field element to XML.
     */
    private void writeFieldElement(XMLStreamWriter writer, FormattedValue value) throws XMLStreamException {
        writer.writeCharacters("          ");
        writer.writeStartElement("field");

        // Write attributes
        writer.writeAttribute("name", sanitizeElementName(value.getFieldName()));
        writer.writeAttribute("type", value.getType().toString().toLowerCase());

        // Write value as text content
        String textContent = formatTextContent(value);
        if (!textContent.isEmpty()) {
            writer.writeCharacters(textContent);
        }

        writer.writeEndElement(); // field
        writer.writeCharacters("\n");
    }
}