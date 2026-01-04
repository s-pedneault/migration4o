package migration4o.engine.migration.formats.excel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import migration4o.engine.migration.engine.ClassExportContext;
import migration4o.engine.migration.engine.FormattedValue;
import migration4o.engine.migration.engine.ModuleExportContext;
import migration4o.engine.migration.engine.ObjectExportContext;
import migration4o.engine.migration.engine.TabularFormatHandler;

/**
 * Excel format handler for the generic export engine.
 * Handles Excel-specific operations using Apache POI.
 */
public class ExcelFormatHandler extends TabularFormatHandler {

    private static final String DEFAULT_OUTPUT_DIR = "output/excel";

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

    public String getDefaultOutputDirectory() {
        return DEFAULT_OUTPUT_DIR;
    }

    public Object beginModule(ModuleExportContext context) throws IOException {
        ModuleContext ctx = new ModuleContext();
        ctx.workbook = new XSSFWorkbook();
        ctx.fileName = context.getSanitizedModuleName() + ".xlsx";
        return ctx;
    }

    public Object beginClass(Object moduleHandle, ClassExportContext context) throws IOException {
        ModuleContext modCtx = (ModuleContext) moduleHandle;
        ClassContext clsCtx = new ClassContext();
        clsCtx.moduleContext = modCtx;

        // Use export name from context
        String exportName = context.getExportName();

        // Create unique sheet name
        String sheetName = getUniqueSheetName(exportName, modCtx.usedSheetNames);
        clsCtx.sheet = modCtx.workbook.createSheet(sheetName);

        // Create header row
        Row headerRow = clsCtx.sheet.createRow(clsCtx.currentRow++);

        CellStyle headerStyle = modCtx.workbook.createCellStyle();
        Font font = modCtx.workbook.createFont();
        font.setBold(true);
        headerStyle.setFont(font);

        // Create headers from the formatted values (which contain column information)
        String[] headers = createHeaders(context);
        for (int colNum = 0; colNum < headers.length; colNum++) {
            Cell cell = headerRow.createCell(colNum);
            cell.setCellValue(headers[colNum]);
            cell.setCellStyle(headerStyle);
        }

        return clsCtx;
    }

    public void exportObject(Object classHandle, ObjectExportContext context, List<FormattedValue> values)
            throws IOException {
        ClassContext ctx = (ClassContext) classHandle;
        Row row = ctx.sheet.createRow(ctx.currentRow++);

        // Convert formatted values to row data
        Object[] rowData = createRowData(values);

        for (int colNum = 0; colNum < rowData.length; colNum++) {
            Cell cell = row.createCell(colNum);
            Object value = rowData[colNum];

            setCellValue(cell, value);
        }
    }

    public void endClass(Object classHandle, ClassExportContext context, int exportedCount) throws IOException {
        ClassContext ctx = (ClassContext) classHandle;

        // Auto-size columns
        Row headerRow = ctx.sheet.getRow(0);
        if (headerRow != null) {
            for (int colNum = 0; colNum < headerRow.getLastCellNum(); colNum++) {
                ctx.sheet.autoSizeColumn(colNum);
            }
        }
    }

    public void endModule(Object moduleHandle, ModuleExportContext context) throws IOException {
        ModuleContext ctx = (ModuleContext) moduleHandle;

        // Write the workbook to file
        File outputFile = new File(outputDirectory, ctx.fileName);
        try (FileOutputStream fileOut = new FileOutputStream(outputFile)) {
            ctx.workbook.write(fileOut);
        }

        ctx.workbook.close();
        System.out.println("  Exported module " + context.getModuleName() + " to " + ctx.fileName);
    }

    public void cleanup() throws IOException {
        // Nothing to clean up for Excel export
    }

    /**
     * Set a value in an Excel cell, handling different data types appropriately.
     */
    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else if (value instanceof Number) {
            Number numValue = (Number) value;
            // Skip -1 values for ID fields (indicates no reference)
            if (numValue.intValue() == -1) {
                cell.setCellValue("");
            } else {
                cell.setCellValue(numValue.doubleValue());
            }
        } else if (value instanceof Date) {
            cell.setCellValue((Date) value);
        } else if (value instanceof String) {
            String strValue = (String) value;
            // Convert French boolean strings to actual booleans (for legacy data)
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
    private String removeAccents(String text) {
        if (text == null) {
            return "";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    /**
     * Remove accents from text by normalizing and removing diacritical marks.
     */
    private String sanitizeSheetName(String name) {
        // First remove accents
        String withoutAccents = removeAccents(name);
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
