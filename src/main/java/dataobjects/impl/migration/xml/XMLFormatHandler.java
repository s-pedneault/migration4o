package dataobjects.impl.migration.xml;package dataobjects.impl.migration.xml;package dataobjects.impl.migration.xml;package dataobjects.impl.migration.xml;



import dataobjects.api.migration.generic.ExportFormatHandler;

import dataobjects.api.migration.generic.ExportColumn;

import dataobjects.api.models.schema.DOSchemaModule;import dataobjects.api.migration.generic.ExportFormatHandler;

import dataobjects.api.models.schema.DOSchemaClass;

import dataobjects.api.models.database.DODatabaseClass;import dataobjects.api.migration.generic.ExportColumn;

import dataobjects.api.models.database.DODatabaseObject;

import dataobjects.api.models.schema.DOSchemaModule;import dataobjects.api.migration.generic.ExportFormatHandler;import dataobjects.api.migration.generic.ExportFormatHandler;

import java.io.IOException;

import java.util.List;import dataobjects.api.models.schema.DOSchemaClass;



/**import dataobjects.api.models.database.DODatabaseClass;import dataobjects.api.migration.generic.ExportColumn;import dataobjects.api.migration.generic.ExportColumn;

 * Placeholder XML format handler for the old (v1) system.

 * The real implementation is now in the v2 package.import dataobjects.api.models.database.DODatabaseObject;

 */

public class XMLFormatHandler implements ExportFormatHandler {import dataobjects.impl.migration.generic.GenericExportEngineImpl;import dataobjects.api.models.schema.DOSchemaModule;import dataobjects.api.models.schema.DOSchemaModule;



    @Override

    public void initialize(String outputDirectory) throws IOException {

        throw new IOException("XMLFormatHandler v1 is deprecated. Use exportToXMLV2() instead.");import javax.xml.stream.XMLOutputFactory;import dataobjects.api.models.schema.DOSchemaClass;import dataobjects.api.models.schema.DOSchemaClass;

    }

import javax.xml.stream.XMLStreamWriter;

    @Override

    public Object beginModule(DOSchemaModule module) throws IOException {import java.io.File;import dataobjects.api.models.database.DODatabaseClass;import dataobjects.api.models.database.DODatabaseClass;

        throw new IOException("XMLFormatHandler v1 is deprecated. Use exportToXMLV2() instead.");

    }import java.io.FileOutputStream;



    @Overrideimport java.io.IOException;import dataobjects.api.models.database.DODatabaseObject;import dataobjects.api.models.database.DODatabaseObject;

    public Object beginClass(Object moduleContext, DOSchemaClass schemaClass, DODatabaseClass dbClass,

            List<ExportColumn> columns, int objectCount) throws IOException {import java.util.List;

        throw new IOException("XMLFormatHandler v1 is deprecated. Use exportToXMLV2() instead.");

    }import java.util.Date;import dataobjects.impl.migration.generic.GenericExportEngineImpl;import dataobjects.impl.migration.generic.GenericExportEngineImpl;



    @Overrideimport java.text.SimpleDateFormat;

    public void exportRow(Object classContext, DODatabaseObject obj, List<ExportColumn> columns, int rowIndex,

            List<Object> cellValues) throws IOException {

        throw new IOException("XMLFormatHandler v1 is deprecated. Use exportToXMLV2() instead.");

    }/**



    @Override * XML format handler for the generic export engine.import javax.xml.stream.XMLOutputFactory;import javax.xml.stream.XMLOutputFactory;

    public void endClass(Object classContext, DOSchemaClass schemaClass, int exportedCount) throws IOException {

        throw new IOException("XMLFormatHandler v1 is deprecated. Use exportToXMLV2() instead."); * Exports data in the database/modules format matching the schema requirements.

    }

 */import javax.xml.stream.XMLStreamWriter;import javax.xml.stream.XMLStreamWriter;

    @Override

    public void endModule(Object moduleContext, DOSchemaModule module) throws IOException {public class XMLFormatHandler implements ExportFormatHandler {

        throw new IOException("XMLFormatHandler v1 is deprecated. Use exportToXMLV2() instead.");

    }import java.io.File;import javax.xml.stream.XMLStreamException;



    @Override    private static final String DEFAULT_OUTPUT_DIR = "output/migration/data";

    public void cleanup() throws IOException {

        throw new IOException("XMLFormatHandler v1 is deprecated. Use exportToXMLV2() instead.");    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");import java.io.FileOutputStream;

    }

    

    @Override

    public String getDefaultOutputDirectory() {    private String outputDirectory;import java.io.IOException;import java.io.File;

        return "output/migration/data";

    }

}
    // Context class for moduleimport java.util.*;import java.io.FileOutputStream;

    private static class ModuleContext {

        XMLStreamWriter writer;import java.text.SimpleDateFormat;import java.io.IOException;

        FileOutputStream outputStream;

        String fileName;import java.util.*;

    }

public class XMLFormatHandler implements ExportFormatHandler {import java.text.SimpleDateFormat;

    // Context class for class

    private static class ClassContext {

        ModuleContext moduleContext;

        boolean hasObjects;    private static final String DEFAULT_OUTPUT_DIR = "output/migration/data";/**

    }

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"); * XML format handler for the generic export engine.

    @Override

    public void initialize(String outputDirectory) throws IOException {     * Produces XML with actual data export in database/modules structure.

        this.outputDirectory = outputDirectory;

        File outputDir = new File(outputDirectory);    private String outputDirectory; */

        if (!outputDir.exists()) {

            outputDir.mkdirs();public class XMLFormatHandler implements ExportFormatHandler {

        }

    }    private static class ModuleContext {



    @Override        XMLStreamWriter writer;    private static final String DEFAULT_OUTPUT_DIR = "output/migration/data";

    public Object beginModule(DOSchemaModule module) throws IOException {

        try {        FileOutputStream outputStream;    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

            ModuleContext ctx = new ModuleContext();

            ctx.fileName = outputDirectory + "/" + GenericExportEngineImpl.sanitizeModuleName(module.getName()) + ".xml";        String fileName;



            File file = new File(ctx.fileName);    }    private String outputDirectory;

            ctx.outputStream = new FileOutputStream(file);



            XMLOutputFactory factory = XMLOutputFactory.newInstance();

            ctx.writer = factory.createXMLStreamWriter(ctx.outputStream, "UTF-8");    private static class ClassContext {    // Context class for module (XML file per module)



            // Write XML declaration and start root elements        ModuleContext moduleContext;    private static class ModuleContext {

            ctx.writer.writeStartDocument("UTF-8", "1.0");

            ctx.writer.writeCharacters("\n");    }        XMLStreamWriter writer;

            ctx.writer.writeStartElement("database");

            ctx.writer.writeCharacters("\n");        FileOutputStream outputStream;

            ctx.writer.writeStartElement("modules");

            ctx.writer.writeCharacters("\n");    @Override        String fileName;

            ctx.writer.writeCharacters("  ");

            ctx.writer.writeStartElement("module");    public void initialize(String outputDirectory) throws IOException {        Set<String> usedClassNames = new HashSet<>();

            ctx.writer.writeAttribute("name", module.getName());

            ctx.writer.writeCharacters("\n");        this.outputDirectory = outputDirectory;    }



            return ctx;        File outputDir = new File(outputDirectory);

        } catch (Exception e) {

            throw new IOException("Error starting module XML: " + e.getMessage(), e);        if (!outputDir.exists()) {    // Context class for class (XML element)

        }

    }            outputDir.mkdirs();    private static class ClassContext {



    @Override        }        ModuleContext moduleContext;

    public Object beginClass(Object moduleContext, DOSchemaClass schemaClass, DODatabaseClass dbClass,

            List<ExportColumn> columns, int objectCount) throws IOException {    }        String className;

        try {

            ModuleContext modCtx = (ModuleContext) moduleContext;        List<ExportColumn> columns;

            ClassContext clsCtx = new ClassContext();

            clsCtx.moduleContext = modCtx;    @Override    }

            clsCtx.hasObjects = objectCount > 0;

    public Object beginModule(DOSchemaModule module) throws IOException {

            if (objectCount > 0) {

                String exportName = schemaClass.getExportName();        try {    @Override

                if (exportName == null || exportName.isEmpty()) {

                    exportName = schemaClass.getShortName();            ModuleContext ctx = new ModuleContext();    public void initialize(String outputDirectory) throws IOException {

                }

            ctx.fileName = outputDirectory + "/" + GenericExportEngineImpl.sanitizeModuleName(module.getName()) + ".xml";        this.outputDirectory = outputDirectory;

                modCtx.writer.writeCharacters("    ");

                modCtx.writer.writeStartElement("class");

                modCtx.writer.writeAttribute("type", exportName);

                modCtx.writer.writeAttribute("count", String.valueOf(objectCount));            File file = new File(ctx.fileName);        // Create output directory

                modCtx.writer.writeCharacters("\n");

            }            ctx.outputStream = new FileOutputStream(file);        File outputDir = new File(outputDirectory);



            return clsCtx;        if (!outputDir.exists()) {

        } catch (Exception e) {

            throw new IOException("Error starting class XML: " + e.getMessage(), e);            XMLOutputFactory factory = XMLOutputFactory.newInstance();            outputDir.mkdirs();

        }

    }            ctx.writer = factory.createXMLStreamWriter(ctx.outputStream, "UTF-8");        }



    @Override    }

    public void exportRow(Object classContext, DODatabaseObject obj, List<ExportColumn> columns, int rowIndex,

            List<Object> cellValues) throws IOException {            ctx.writer.writeStartDocument("UTF-8", "1.0");

        try {

            ClassContext ctx = (ClassContext) classContext;            ctx.writer.writeCharacters("\n");    @Override

            XMLStreamWriter writer = ctx.moduleContext.writer;

            ctx.writer.writeStartElement("database");    public Object beginModule(DOSchemaModule module) throws IOException {

            writer.writeCharacters("      ");

            writer.writeStartElement("object");            ctx.writer.writeCharacters("\n");        ModuleContext ctx = new ModuleContext();

            writer.writeAttribute("id", String.valueOf(obj.getObjectId()));

            writer.writeCharacters("\n");            ctx.writer.writeStartElement("modules");        ctx.workbook = new XSSFWorkbook();



            // Export each field value            ctx.writer.writeCharacters("\n");        ctx.fileName = outputDirectory + "/" + GenericExportEngineImpl.sanitizeModuleName(module.getName()) + ".xlsx";

            for (int i = 0; i < columns.size() && i < cellValues.size(); i++) {

                ExportColumn column = columns.get(i);            ctx.writer.writeCharacters("    ");        return ctx;

                Object value = cellValues.get(i);

            ctx.writer.writeStartElement("module");    }

                if (value != null && !isEmptyValue(value, column)) {

                    writer.writeCharacters("        ");            ctx.writer.writeAttribute("name", module.getName());

                    writer.writeStartElement("field");

                    writer.writeAttribute("name", column.columnName);            ctx.writer.writeCharacters("\n");    @Override

                    writer.writeAttribute("type", getFieldType(value));

                    writer.writeCharacters(formatValue(value));    public Object beginClass(Object moduleContext, DOSchemaClass schemaClass, DODatabaseClass dbClass,

                    writer.writeEndElement();

                    writer.writeCharacters("\n");            return ctx;            List<ExportColumn> columns, int objectCount) throws IOException {

                }

            }        } catch (Exception e) {        ModuleContext modCtx = (ModuleContext) moduleContext;



            writer.writeCharacters("      ");            throw new IOException("Error starting module XML: " + e.getMessage(), e);        ClassContext clsCtx = new ClassContext();

            writer.writeEndElement(); // object

            writer.writeCharacters("\n");        }        clsCtx.moduleContext = modCtx;



        } catch (Exception e) {    }

            throw new IOException("Error exporting row: " + e.getMessage(), e);

        }        // Use export name if available, otherwise fall back to short name

    }

    @Override        String exportName = schemaClass.getExportName();

    @Override

    public void endClass(Object classContext, DOSchemaClass schemaClass, int exportedCount) throws IOException {    public Object beginClass(Object moduleContext, DOSchemaClass schemaClass, DODatabaseClass dbClass,        if (exportName == null || exportName.isEmpty()) {

        try {

            ClassContext ctx = (ClassContext) classContext;            List<ExportColumn> columns, int objectCount) throws IOException {            exportName = schemaClass.getShortName();

            if (ctx.hasObjects && exportedCount > 0) {

                ctx.moduleContext.writer.writeCharacters("    ");        try {        }

                ctx.moduleContext.writer.writeEndElement(); // class

                ctx.moduleContext.writer.writeCharacters("\n");            ModuleContext modCtx = (ModuleContext) moduleContext;

            }

        } catch (Exception e) {            ClassContext clsCtx = new ClassContext();        // Create unique sheet name

            throw new IOException("Error ending class: " + e.getMessage(), e);

        }            clsCtx.moduleContext = modCtx;        String sheetName = getUniqueSheetName(exportName, modCtx.usedSheetNames);

    }

        clsCtx.sheet = modCtx.workbook.createSheet(sheetName);

    @Override

    public void endModule(Object moduleContext, DOSchemaModule module) throws IOException {            String exportName = schemaClass.getExportName();

        try {

            ModuleContext ctx = (ModuleContext) moduleContext;            if (exportName == null || exportName.isEmpty()) {        // Create header row



            ctx.writer.writeCharacters("  ");                exportName = schemaClass.getShortName();        Row headerRow = clsCtx.sheet.createRow(clsCtx.currentRow++);

            ctx.writer.writeEndElement(); // module

            ctx.writer.writeCharacters("\n");            }

            ctx.writer.writeEndElement(); // modules

            ctx.writer.writeCharacters("\n");        CellStyle headerStyle = modCtx.workbook.createCellStyle();

            ctx.writer.writeEndElement(); // database

            ctx.writer.writeCharacters("\n");            if (objectCount > 0) {        Font font = modCtx.workbook.createFont();



            ctx.writer.writeEndDocument();                modCtx.writer.writeCharacters("        ");        font.setBold(true);

            ctx.writer.flush();

            ctx.writer.close();                modCtx.writer.writeStartElement("class");        headerStyle.setFont(font);

            ctx.outputStream.close();

                modCtx.writer.writeAttribute("type", exportName);

            System.out.println("  Exported module " + module.getName() + " to " + ctx.fileName);

        } catch (Exception e) {                modCtx.writer.writeAttribute("count", String.valueOf(objectCount));        for (int colNum = 0; colNum < columns.size(); colNum++) {

            throw new IOException("Error ending module: " + e.getMessage(), e);

        }                modCtx.writer.writeCharacters("\n");            Cell cell = headerRow.createCell(colNum);

    }

            }            cell.setCellValue(columns.get(colNum).columnName);

    @Override

    public void cleanup() throws IOException {            cell.setCellStyle(headerStyle);

        // Nothing to cleanup

    }            return clsCtx;        }



    @Override        } catch (Exception e) {

    public String getDefaultOutputDirectory() {

        return DEFAULT_OUTPUT_DIR;            throw new IOException("Error starting class XML: " + e.getMessage(), e);        return clsCtx;

    }

        }    }

    /**

     * Check if a value should be considered empty and excluded from output.    }

     */

    private boolean isEmptyValue(Object value, ExportColumn column) {    @Override

        if (value == null) return true;

        if (value instanceof String && ((String) value).trim().isEmpty()) return true;    @Override    public void exportRow(Object classContext, DODatabaseObject obj, List<ExportColumn> columns, int rowIndex,

        

        // For ID fields, -1 typically means "no reference"    public void exportRow(Object classContext, DODatabaseObject obj, List<ExportColumn> columns, int rowIndex,            List<Object> cellValues) throws IOException {

        if (value instanceof Number && isIDTypeField(column) && ((Number) value).intValue() == -1) {

            return true;            List<Object> cellValues) throws IOException {        ClassContext ctx = (ClassContext) classContext;

        }

                try {        Row row = ctx.sheet.createRow(ctx.currentRow++);

        return false;

    }            ClassContext ctx = (ClassContext) classContext;



    /**            XMLStreamWriter writer = ctx.moduleContext.writer;        for (int colNum = 0; colNum < cellValues.size(); colNum++) {

     * Check if a column represents an ID-type field.

     */            Cell cell = row.createCell(colNum);

    private boolean isIDTypeField(ExportColumn column) {

        if (column.isFlattened) {            writer.writeCharacters("          ");            Object value = cellValues.get(colNum);

            return false; // Flattened fields are not ID fields

        }            writer.writeStartElement("object");            ExportColumn column = columns.get(colNum);

        String typeName = column.field != null ? column.field.getTypeName() : null;

        return typeName != null && (typeName.startsWith("gen.util.ID") || typeName.contains(".ID"));            writer.writeAttribute("id", String.valueOf(obj.getObjectId()));

    }

            writer.writeCharacters("\n");            setCellValue(cell, value, isIDTypeField(column));

    /**

     * Determine the XML type attribute for a value.        }

     */

    private String getFieldType(Object value) {            for (int i = 0; i < columns.size() && i < cellValues.size(); i++) {    }

        if (value == null) return "string";

        if (value instanceof Integer || value instanceof Long) return "integer";                ExportColumn column = columns.get(i);

        if (value instanceof Double || value instanceof Float) return "double";

        if (value instanceof Boolean) return "boolean";                Object value = cellValues.get(i);    @Override

        if (value instanceof Date) return "date";

        return "string";    public void endClass(Object classContext, DOSchemaClass schemaClass, int exportedCount) throws IOException {

    }

                if (value != null && !isEmptyValue(value, column)) {        ClassContext ctx = (ClassContext) classContext;

    /**

     * Format a value for XML content, handling special cases.                    writer.writeCharacters("            ");

     */

    private String formatValue(Object value) {                    writer.writeStartElement("field");        // Auto-size columns

        if (value == null) return "";

                            writer.writeAttribute("type", getFieldType(value));        Row headerRow = ctx.sheet.getRow(0);

        if (value instanceof Date) {

            return dateFormat.format((Date) value);                    writer.writeAttribute("name", column.columnName);        if (headerRow != null) {

        }

                            writer.writeCharacters(formatValue(value));            for (int colNum = 0; colNum < headerRow.getLastCellNum(); colNum++) {

        if (value instanceof String) {

            String strValue = (String) value;                    writer.writeEndElement();                ctx.sheet.autoSizeColumn(colNum);

            // Convert French boolean strings to standard format

            if ("VRAI".equalsIgnoreCase(strValue)) return "true";                    writer.writeCharacters("\n");            }

            if ("FAUX".equalsIgnoreCase(strValue)) return "false";

                            }        }

            // Escape XML special characters

            return strValue            }    }

                .replace("&", "&amp;")

                .replace("<", "&lt;")

                .replace(">", "&gt;")

                .replace("\"", "&quot;")            writer.writeCharacters("          ");    @Override

                .replace("'", "&apos;");

        }            writer.writeEndElement();    public void endModule(Object moduleContext, DOSchemaModule module) throws IOException {

        

        return String.valueOf(value);            writer.writeCharacters("\n");        ModuleContext ctx = (ModuleContext) moduleContext;

    }

}

        } catch (Exception e) {        // Write the workbook to file

            throw new IOException("Error exporting row: " + e.getMessage(), e);        try (FileOutputStream fileOut = new FileOutputStream(ctx.fileName)) {

        }            ctx.workbook.write(fileOut);

    }        }



    @Override        ctx.workbook.close();

    public void endClass(Object classContext, DOSchemaClass schemaClass, int exportedCount) throws IOException {    }

        try {

            ClassContext ctx = (ClassContext) classContext;    @Override

            if (exportedCount > 0) {    public void cleanup() throws IOException {

                ctx.moduleContext.writer.writeCharacters("        ");        // Nothing to clean up for Excel export

                ctx.moduleContext.writer.writeEndElement();    }

                ctx.moduleContext.writer.writeCharacters("\n");

            }    @Override

        } catch (Exception e) {    public String getDefaultOutputDirectory() {

            throw new IOException("Error ending class: " + e.getMessage(), e);        return DEFAULT_OUTPUT_DIR;

        }    }

    }

    private boolean isIDTypeField(ExportColumn column) {

    @Override        if (column.isFlattened) {

    public void endModule(Object moduleContext, DOSchemaModule module) throws IOException {            return false; // Flattened fields are not ID fields

        try {        }

            ModuleContext ctx = (ModuleContext) moduleContext;        String typeName = column.field.getTypeName();

        return typeName != null && (typeName.startsWith("gen.util.ID") || typeName.contains(".ID"));

            ctx.writer.writeCharacters("    ");    }

            ctx.writer.writeEndElement(); // module

            ctx.writer.writeCharacters("\n");    private void setCellValue(Cell cell, Object value, boolean isIDField) {

            ctx.writer.writeEndElement(); // modules        if (value == null) {

            ctx.writer.writeCharacters("\n");            cell.setCellValue("");

            ctx.writer.writeEndElement(); // database        } else if (value instanceof Boolean) {

            ctx.writer.writeCharacters("\n");            // Handle real Boolean objects first - no conversion needed

            cell.setCellValue((Boolean) value);

            ctx.writer.writeEndDocument();        } else if (value instanceof Number) {

            ctx.writer.flush();            Number numValue = (Number) value;

            ctx.writer.close();            // Only skip -1 values for ID fields (indicates no reference)

            ctx.outputStream.close();            if (isIDField && numValue.intValue() == -1) {

                cell.setCellValue("");

            System.out.println("  Exported schema for " + module.getName() + " to " + ctx.fileName);            } else {

        } catch (Exception e) {                cell.setCellValue(numValue.doubleValue());

            throw new IOException("Error ending module: " + e.getMessage(), e);            }

        }        } else if (value instanceof Date) {

    }            cell.setCellValue((Date) value);

        } else if (value instanceof String) {

    @Override            String strValue = (String) value;

    public void cleanup() throws IOException {            // Convert French boolean strings to actual booleans (for legacy data if it

        // Nothing to cleanup            // exists)

    }            if ("VRAI".equalsIgnoreCase(strValue) || "true".equalsIgnoreCase(strValue)) {

                cell.setCellValue(true);

    @Override            } else if ("FAUX".equalsIgnoreCase(strValue) || "false".equalsIgnoreCase(strValue)) {

    public String getDefaultOutputDirectory() {                cell.setCellValue(false);

        return DEFAULT_OUTPUT_DIR;            } else {

    }                cell.setCellValue(strValue);

            }

    private boolean isEmptyValue(Object value, ExportColumn column) {        } else {

        if (value == null) return true;            // Fallback for any other type

        if (value instanceof String && ((String) value).trim().isEmpty()) return true;            cell.setCellValue(value.toString());

        if (value instanceof Number && isIDTypeField(column) && ((Number) value).intValue() == -1) return true;        }

        return false;    }

    }

    /**

    private boolean isIDTypeField(ExportColumn column) {     * Remove accents from text by normalizing and removing diacritical marks.

        if (column.isFlattened) return false;     */

        String typeName = column.field.getTypeName();    private String sanitizeSheetName(String name) {

        return typeName != null && (typeName.startsWith("gen.util.ID") || typeName.contains(".ID"));        // First remove accents

    }        String withoutAccents = GenericExportEngineImpl.removeAccents(name);

        // Excel sheet names have restrictions: no \/:*?[]

    private String getFieldType(Object value) {        String sanitized = withoutAccents.replaceAll("[\\\\/:*?\\[\\]]", "_");

        if (value == null) return "string";        // Max length is 31 characters

        if (value instanceof Integer || value instanceof Long) return "integer";        if (sanitized.length() > 31) {

        if (value instanceof Double || value instanceof Float) return "double";            sanitized = sanitized.substring(0, 31);

        if (value instanceof Boolean) return "boolean";        }

        if (value instanceof Date) return "date";        return sanitized;

        return "string";    }

    }

    /**

    private String formatValue(Object value) {     * Get a unique sheet name by appending a counter if needed.

        if (value == null) return "";     * Excel doesn't allow duplicate sheet names in the same workbook.

        if (value instanceof Date) return dateFormat.format((Date) value);     */

        if (value instanceof String) {    private String getUniqueSheetName(String baseName, Set<String> usedNames) {

            String strValue = (String) value;        String sanitized = sanitizeSheetName(baseName);

            if ("VRAI".equalsIgnoreCase(strValue)) return "true";

            if ("FAUX".equalsIgnoreCase(strValue)) return "false";        // If the name is unique, use it as-is

        }        if (!usedNames.contains(sanitized)) {

        return String.valueOf(value);            usedNames.add(sanitized);

    }            return sanitized;

}        }

        // Otherwise, append a counter
        int counter = 2;
        String uniqueName;
        while (true) {
            // Make sure we stay within 31 character limit
            String suffix = "_" + counter;
            int maxBaseLength = 31 - suffix.length();
            String truncatedBase = sanitized.length() > maxBaseLength
                    ? sanitized.substring(0, maxBaseLength)
                    : sanitized;
            uniqueName = truncatedBase + suffix;

            if (!usedNames.contains(uniqueName)) {
                usedNames.add(uniqueName);
                return uniqueName;
            }
            counter++;
        }
    }
}
