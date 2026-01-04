package migration4o.engine.migration.formats.xml;

import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import migration4o.engine.DOEngine;
import migration4o.engine.resolvers.DOObjectReachabilityTracker;
import migration4o.models.DOField;
import migration4o.models.database.DODatabase;
import migration4o.models.database.DODatabaseClass;
import migration4o.models.database.DODatabaseObject;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaModule;

/**
 * Generates a comprehensive migration report in XML format.
 * Includes database structure, statistics, and reachability information.
 */
public class XMLReportGenerator {

    private static final String REPORT_VERSION = "1.0";
    private final DOEngine engine;

    public XMLReportGenerator(DOEngine engine) {
        this.engine = engine;
    }

    /**
     * Generate the migration report.
     */
    public void generateReport(String outputPath) throws IOException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();

        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            XMLStreamWriter writer = factory.createXMLStreamWriter(fos, "UTF-8");

            writer.writeStartDocument("UTF-8", "1.0");
            writer.writeCharacters("\n");

            // Root element
            writer.writeStartElement("migrationReport");
            writer.writeAttribute("version", REPORT_VERSION);
            writer.writeAttribute("timestamp", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()));
            writer.writeCharacters("\n\n");

            // Metadata section
            writeMigrationMetadata(writer);

            // Database structure section
            writeDatabaseStructure(writer);

            // Statistics section
            writeStatistics(writer);

            // Reachability information
            writeReachabilityInfo(writer);

            // Module organization
            writeModuleOrganization(writer);

            writer.writeCharacters("\n");
            writer.writeEndElement(); // migrationReport
            writer.writeCharacters("\n");
            writer.writeEndDocument();
            writer.flush();
            writer.close();

        } catch (XMLStreamException e) {
            throw new IOException("Error generating migration report", e);
        }
    }

    /**
     * Write migration metadata.
     */
    private void writeMigrationMetadata(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeCharacters("  ");
        writer.writeStartElement("metadata");
        writer.writeCharacters("\n");

        writeElement(writer, "engineVersion", REPORT_VERSION, 4);
        writeElement(writer, "exportDate", new SimpleDateFormat("yyyy-MM-dd").format(new Date()), 4);
        writeElement(writer, "exportTime", new SimpleDateFormat("HH:mm:ss").format(new Date()), 4);

        DODatabase database = engine.getDatabase();
        if (database != null) {
            writeElement(writer, "databaseSize", database.getDatabaseSize(), 4);
            writeElement(writer, "totalClasses", String.valueOf(database.getTotalClasses()), 4);
            writeElement(writer, "totalObjects", String.valueOf(database.getTotalObjects()), 4);
        }

        writer.writeCharacters("  ");
        writer.writeEndElement(); // metadata
        writer.writeCharacters("\n\n");
    }

    /**
     * Write complete database structure.
     */
    private void writeDatabaseStructure(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeCharacters("  ");
        writer.writeStartElement("databaseStructure");
        writer.writeCharacters("\n");

        DOSchema schema = engine.getSchema();
        if (schema != null && schema.getModules() != null) {
            for (DOSchemaModule module : schema.getModules()) {
                writeModule(writer, module);
            }
        }

        writer.writeCharacters("  ");
        writer.writeEndElement(); // databaseStructure
        writer.writeCharacters("\n\n");
    }

    /**
     * Write a module structure.
     */
    private void writeModule(XMLStreamWriter writer, DOSchemaModule module) throws XMLStreamException {
        writer.writeCharacters("    ");
        writer.writeStartElement("module");
        writer.writeAttribute("name", module.getName());
        writer.writeCharacters("\n");

        if (module.getClasses() != null) {
            for (DOSchemaClass schemaClass : module.getClasses()) {
                writeClass(writer, schemaClass);
            }
        }

        writer.writeCharacters("    ");
        writer.writeEndElement(); // module
        writer.writeCharacters("\n");
    }

    /**
     * Write a class structure.
     */
    private void writeClass(XMLStreamWriter writer, DOSchemaClass schemaClass) throws XMLStreamException {
        writer.writeCharacters("      ");
        writer.writeStartElement("class");
        writer.writeAttribute("name", schemaClass.getShortName());
        writer.writeAttribute("absoluteName", schemaClass.getAbsoluteName());

        if (schemaClass.getExportName() != null && !schemaClass.getExportName().isEmpty()) {
            writer.writeAttribute("exportName", schemaClass.getExportName());
        }

        String superClass = schemaClass.getSuperClassAbsoluteName();
        if (superClass != null && !superClass.equals("java.lang.Object")) {
            writer.writeAttribute("extends", superClass);
        }

        writer.writeCharacters("\n");

        // Write fields
        DOField[] fields = schemaClass.getFields();
        if (fields != null && fields.length > 0) {
            writer.writeCharacters("        ");
            writer.writeStartElement("fields");
            writer.writeCharacters("\n");

            for (DOField field : fields) {
                writeField(writer, field);
            }

            writer.writeCharacters("        ");
            writer.writeEndElement(); // fields
            writer.writeCharacters("\n");
        }

        writer.writeCharacters("      ");
        writer.writeEndElement(); // class
        writer.writeCharacters("\n");
    }

    /**
     * Write a field definition.
     */
    private void writeField(XMLStreamWriter writer, DOField field) throws XMLStreamException {
        writer.writeCharacters("          ");
        writer.writeStartElement("field");
        writer.writeAttribute("name", field.getName());
        writer.writeAttribute("type", field.getTypeName());

        if (field.isArray()) {
            writer.writeAttribute("array", "true");
            if (field.getContentTypeName() != null) {
                writer.writeAttribute("contentType", field.getContentTypeName());
            }
        }

        if (field.isPrimitive()) {
            writer.writeAttribute("primitive", "true");
        }

        if (field.getDescription() != null && !field.getDescription().isEmpty()) {
            writer.writeAttribute("description", field.getDescription());
        }

        writer.writeEndElement(); // field
        writer.writeCharacters("\n");
    }

    /**
     * Write migration statistics.
     */
    private void writeStatistics(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeCharacters("  ");
        writer.writeStartElement("statistics");
        writer.writeCharacters("\n");

        DODatabase database = engine.getDatabase();
        if (database != null && database.getClasses() != null) {
            for (DODatabaseClass dbClass : database.getClasses()) {
                writeClassStatistics(writer, dbClass);
            }
        }

        writer.writeCharacters("  ");
        writer.writeEndElement(); // statistics
        writer.writeCharacters("\n\n");
    }

    /**
     * Write statistics for a single class.
     */
    private void writeClassStatistics(XMLStreamWriter writer, DODatabaseClass dbClass) throws XMLStreamException {
        writer.writeCharacters("    ");
        writer.writeStartElement("classStatistics");
        writer.writeAttribute("name", dbClass.getShortName());
        writer.writeAttribute("absoluteName", dbClass.getAbsoluteName());
        writer.writeCharacters("\n");

        DOObjectReachabilityTracker tracker = engine.getReachabilityTracker();

        long totalCount = tracker.getObjectCountByClass(dbClass);
        long reachedCount = tracker.getReachedObjectCountByClass(dbClass);
        long unreachedCount = tracker.getUnreachedObjectCountByClass(dbClass);

        writeElement(writer, "totalObjects", String.valueOf(totalCount), 6);
        writeElement(writer, "reachedObjects", String.valueOf(reachedCount), 6);
        writeElement(writer, "unreachedObjects", String.valueOf(unreachedCount), 6);

        if (totalCount > 0) {
            double reachabilityPercentage = (reachedCount * 100.0) / totalCount;
            writeElement(writer, "reachabilityPercentage", String.format("%.2f", reachabilityPercentage), 6);
        }

        writer.writeCharacters("    ");
        writer.writeEndElement(); // classStatistics
        writer.writeCharacters("\n");
    }

    /**
     * Write overall reachability information.
     */
    private void writeReachabilityInfo(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeCharacters("  ");
        writer.writeStartElement("reachabilityInfo");
        writer.writeCharacters("\n");

        DOObjectReachabilityTracker tracker = engine.getReachabilityTracker();

        // Note: The tracker counts objects across all inheritance chains,
        // so the same object may be counted multiple times
        int totalObjects = tracker.getTotalObjectCount();
        int reachedObjects = tracker.getReachedObjectCount();
        int unreachedObjects = tracker.getUnreachedObjectCount();

        // Calculate unique object counts from resolved objects
        Set<Long> uniqueObjectIds = new HashSet<>();
        Set<Long> uniqueReachableIds = new HashSet<>();
        Set<Long> uniqueUnreachableIds = new HashSet<>();

        DODatabase database = engine.getDatabase();
        if (database != null && database.getClasses() != null) {
            for (DODatabaseClass dbClass : database.getClasses()) {
                DODatabaseObject[] objects = dbClass.getResolvedObjects();
                if (objects != null) {
                    for (DODatabaseObject obj : objects) {
                        Long id = obj.getObjectId();
                        uniqueObjectIds.add(id);
                        if (obj.isReachable()) {
                            uniqueReachableIds.add(id);
                        } else {
                            uniqueUnreachableIds.add(id);
                        }
                    }
                }
            }
        }

        // Write tracker counts (with inheritance duplicates)
        writer.writeCharacters("    ");
        writer.writeComment(" Note: tracker counts include inheritance duplicates ");
        writer.writeCharacters("\n");
        writeElement(writer, "totalObjectsWithInheritance", String.valueOf(totalObjects), 4);
        writeElement(writer, "reachedObjectsWithInheritance", String.valueOf(reachedObjects), 4);
        writeElement(writer, "unreachedObjectsWithInheritance", String.valueOf(unreachedObjects), 4);

        // Write unique counts
        writer.writeCharacters("\n");
        writer.writeCharacters("    ");
        writer.writeComment(" Unique object counts (each object counted once) ");
        writer.writeCharacters("\n");
        writeElement(writer, "uniqueTotalObjects", String.valueOf(uniqueObjectIds.size()), 4);
        writeElement(writer, "uniqueReachedObjects", String.valueOf(uniqueReachableIds.size()), 4);
        writeElement(writer, "uniqueUnreachedObjects", String.valueOf(uniqueUnreachableIds.size()), 4);

        if (uniqueObjectIds.size() > 0) {
            double overallReachability = (uniqueReachableIds.size() * 100.0) / uniqueObjectIds.size();
            writeElement(writer, "overallReachabilityPercentage", String.format("%.2f", overallReachability), 4);
        }

        writer.writeCharacters("  ");
        writer.writeEndElement(); // reachabilityInfo
        writer.writeCharacters("\n\n");
    }

    /**
     * Write module organization information.
     */
    private void writeModuleOrganization(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeCharacters("  ");
        writer.writeStartElement("moduleOrganization");
        writer.writeCharacters("\n");

        DOSchema schema = engine.getSchema();
        if (schema != null && schema.getModules() != null) {
            for (DOSchemaModule module : schema.getModules()) {
                writer.writeCharacters("    ");
                writer.writeStartElement("module");
                writer.writeAttribute("name", module.getName());
                writer.writeCharacters("\n");

                if (module.getClasses() != null) {
                    writeElement(writer, "classCount", String.valueOf(module.getClasses().length), 6);

                    // Calculate total objects in this module
                    int totalModuleObjects = 0;
                    for (DOSchemaClass schemaClass : module.getClasses()) {
                        DODatabaseClass dbClass = schemaClass.getDatabaseClass();
                        if (dbClass != null) {
                            totalModuleObjects += engine.getReachabilityTracker().getObjectCountByClass(dbClass);
                        }
                    }
                    writeElement(writer, "totalObjects", String.valueOf(totalModuleObjects), 6);
                }

                writer.writeCharacters("    ");
                writer.writeEndElement(); // module
                writer.writeCharacters("\n");
            }
        }

        writer.writeCharacters("  ");
        writer.writeEndElement(); // moduleOrganization
        writer.writeCharacters("\n");
    }

    /**
     * Helper method to write a simple element with text content.
     */
    private void writeElement(XMLStreamWriter writer, String name, String value, int indent)
            throws XMLStreamException {

        String indentStr = " ".repeat(indent);
        writer.writeCharacters(indentStr);
        writer.writeStartElement(name);
        writer.writeCharacters(value);
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }
}
