package dataobjects.impl.migration.xml;

import dataobjects.api.migration.generic.ExportFormatHandler;
import dataobjects.api.migration.generic.ExportColumn;
import dataobjects.api.models.schema.DOSchemaModule;
import dataobjects.api.models.schema.DOSchemaClass;
import dataobjects.api.models.database.DODatabaseClass;
import dataobjects.api.models.database.DODatabaseObject;
import dataobjects.impl.migration.generic.GenericExportEngineImpl;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Excel format handler for the generic export engine.
 * Handles Excel-specific operations using Apache POI.
 */
public class ExcelFormatHandler implements ExportFormatHandler {

    private static final String DEFAULT_OUTPUT_DIR = "output/excel";

    private String outputDirectory;

    // Context class for module (workbook)
    private static class ModuleContext {
        Workbook workbook;
        String fileName;
        Set<String> usedSheetNames = new HashSet<>();
    }

    // Context class for class (sheet)
    private static class ClassContext {
        Sheet sheet;
        ModuleContext moduleContext;
        int currentRow = 0;
    }

    @Override
    public void initialize(String outputDirectory) throws IOException {
        this.outputDirectory = outputDirectory;

        // Create output directory
        File outputDir = new File(outputDirectory);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
    }

    @Override
    public Object beginModule(DOSchemaModule module) throws IOException {
        ModuleContext ctx = new ModuleContext();
        ctx.workbook = new XSSFWorkbook();
        ctx.fileName = outputDirectory + "/" + GenericExportEngineImpl.sanitizeModuleName(module.getName()) + ".xlsx";
        return ctx;
    }

    @Override
    public Object beginClass(Object moduleContext, DOSchemaClass schemaClass, DODatabaseClass dbClass,
            List<ExportColumn> columns, int objectCount) throws IOException {
        ModuleContext modCtx = (ModuleContext) moduleContext;
        ClassContext clsCtx = new ClassContext();
        clsCtx.moduleContext = modCtx;

        // Use export name if available, otherwise fall back to short name
        String exportName = schemaClass.getExportName();
        if (exportName == null || exportName.isEmpty()) {
            exportName = schemaClass.getShortName();
        }

        // Create unique sheet name
        String sheetName = getUniqueSheetName(exportName, modCtx.usedSheetNames);
        clsCtx.sheet = modCtx.workbook.createSheet(sheetName);

        // Create header row
        Row headerRow = clsCtx.sheet.createRow(clsCtx.currentRow++);

        CellStyle headerStyle = modCtx.workbook.createCellStyle();
        Font font = modCtx.workbook.createFont();
        font.setBold(true);
        headerStyle.setFont(font);

        for (int colNum = 0; colNum < columns.size(); colNum++) {
            Cell cell = headerRow.createCell(colNum);
            cell.setCellValue(columns.get(colNum).columnName);
            cell.setCellStyle(headerStyle);
        }

        return clsCtx;
    }

    @Override
    public void exportRow(Object classContext, DODatabaseObject obj, List<ExportColumn> columns, int rowIndex,
            List<Object> cellValues) throws IOException {
        ClassContext ctx = (ClassContext) classContext;
        Row row = ctx.sheet.createRow(ctx.currentRow++);

        for (int colNum = 0; colNum < cellValues.size(); colNum++) {
            Cell cell = row.createCell(colNum);
            Object value = cellValues.get(colNum);
            ExportColumn column = columns.get(colNum);

            setCellValue(cell, value, isIDTypeField(column));
        }
    }

    @Override
    public void endClass(Object classContext, DOSchemaClass schemaClass, int exportedCount) throws IOException {
        ClassContext ctx = (ClassContext) classContext;

        // Auto-size columns
        Row headerRow = ctx.sheet.getRow(0);
        if (headerRow != null) {
            for (int colNum = 0; colNum < headerRow.getLastCellNum(); colNum++) {
                ctx.sheet.autoSizeColumn(colNum);
            }
        }
    }

    @Override
    public void endModule(Object moduleContext, DOSchemaModule module) throws IOException {
        ModuleContext ctx = (ModuleContext) moduleContext;

        // Write the workbook to file
        try (FileOutputStream fileOut = new FileOutputStream(ctx.fileName)) {
            ctx.workbook.write(fileOut);
        }

        ctx.workbook.close();
    }

    @Override
    public void cleanup() throws IOException {
        // Nothing to clean up for Excel export
    }

    @Override
    public String getDefaultOutputDirectory() {
        return DEFAULT_OUTPUT_DIR;
    }

    private boolean isIDTypeField(ExportColumn column) {
        if (column.isFlattened) {
            return false; // Flattened fields are not ID fields
        }
        String typeName = column.field.getTypeName();
        return typeName != null && (typeName.startsWith("gen.util.ID") || typeName.contains(".ID"));
    }

    private void setCellValue(Cell cell, Object value, boolean isIDField) {
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Boolean) {
            // Handle real Boolean objects first - no conversion needed
            cell.setCellValue((Boolean) value);
        } else if (value instanceof Number) {
            Number numValue = (Number) value;
            // Only skip -1 values for ID fields (indicates no reference)
            if (isIDField && numValue.intValue() == -1) {
                cell.setCellValue("");
            } else {
                cell.setCellValue(numValue.doubleValue());
            }
        } else if (value instanceof Date) {
            cell.setCellValue((Date) value);
        } else if (value instanceof String) {
            String strValue = (String) value;
            // Convert French boolean strings to actual booleans (for legacy data if it
            // exists)
            if ("VRAI".equalsIgnoreCase(strValue) || "true".equalsIgnoreCase(strValue)) {
                cell.setCellValue(true);
            } else if ("FAUX".equalsIgnoreCase(strValue) || "false".equalsIgnoreCase(strValue)) {
                cell.setCellValue(false);
            } else {
                cell.setCellValue(strValue);
            }
        } else {
            // Fallback for any other type
            cell.setCellValue(value.toString());
        }
    }

    /**
     * Remove accents from text by normalizing and removing diacritical marks.
     */
    private String sanitizeSheetName(String name) {
        // First remove accents
        String withoutAccents = GenericExportEngineImpl.removeAccents(name);
        // Excel sheet names have restrictions: no \/:*?[]
        String sanitized = withoutAccents.replaceAll("[\\\\/:*?\\[\\]]", "_");
        // Max length is 31 characters
        if (sanitized.length() > 31) {
            sanitized = sanitized.substring(0, 31);
        }
        return sanitized;
    }

    /**
     * Get a unique sheet name by appending a counter if needed.
     * Excel doesn't allow duplicate sheet names in the same workbook.
     */
    private String getUniqueSheetName(String baseName, Set<String> usedNames) {
        String sanitized = sanitizeSheetName(baseName);

        // If the name is unique, use it as-is
        if (!usedNames.contains(sanitized)) {
            usedNames.add(sanitized);
            return sanitized;
        }

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
