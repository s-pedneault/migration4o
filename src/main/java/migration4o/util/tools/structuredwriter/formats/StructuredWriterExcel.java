package migration4o.util.tools.structuredwriter.formats;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import migration4o.util.tools.structuredwriter.StructuredWriter;
import migration4o.util.tools.structuredwriter.StructuredWriterAPI;
import migration4o.util.tools.structuredwriter.StructuredWriterElementWithContent;
import migration4o.util.tools.structuredwriter.StructuredWriterElementWithStructure;
import migration4o.util.tools.structuredwriter.StructuredWriterElementWithoutContent;

public class StructuredWriterExcel implements StructuredWriterAPI {

    private static final String ID_COLUMN = "Id";
    private static final String PARENT_COLUMN = "Parent";

    private final Deque<NodeContext> contextStack = new ArrayDeque<>();
    private final Map<String, SheetState> sheetsByObjectType = new LinkedHashMap<>();
    private final Map<String, Integer> sheetNameCounters = new LinkedHashMap<>();
    private final List<String> objectTypeOrder = new ArrayList<>();
    private String mainObjectType;
    private String mainFormSheetName;
    private XSSFWorkbook workbook;

    @Override
    public String getName() {
        return "EXCEL";
    }

    @Override
    public boolean includeCollectionSizeMetadata() {
        return false;
    }

    @Override
    public void initialize(StructuredWriter writer) throws IOException {
        workbook = new XSSFWorkbook();
        contextStack.clear();
        sheetsByObjectType.clear();
        sheetNameCounters.clear();
        objectTypeOrder.clear();
        mainObjectType = null;
        mainFormSheetName = null;
    }

    @Override
    public void add(StructuredWriterElementWithoutContent element) throws IOException {
        Map<String, String> currentRow = getCurrentRowValues();
        if (currentRow == null) {
            return;
        }

        String baseColumn = toSafeBusinessColumnName(buildColumnName(element.name));
        if (baseColumn == null || baseColumn.isBlank()) {
            return;
        }

        currentRow.put(baseColumn, "");

        if (element.attributes == null || element.attributes.isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> entry : element.attributes.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            currentRow.put(baseColumn + "@" + entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void addContent(StructuredWriterElementWithContent element, String content) throws IOException {
        Map<String, String> currentRow = getCurrentRowValues();
        if (currentRow == null) {
            return;
        }

        String column = toSafeBusinessColumnName(buildColumnName(element.name));
        if (column == null || column.isBlank()) {
            return;
        }

        currentRow.put(column, content == null ? "" : content);

        if (element.attributes == null || element.attributes.isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> entry : element.attributes.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            currentRow.put(column + "@" + entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void openStructure(StructuredWriterElementWithStructure element) throws IOException {
        boolean objectNode = isObjectNode(element);
        Long objectId = parseObjectId(element.attributes);
        Long parentId = objectNode ? findNearestParentObjectId() : null;

        NodeContext context = new NodeContext(element.name, element.attributes, objectNode, objectId, parentId);
        contextStack.addLast(context);
    }

    @Override
    public void closeStructure(StructuredWriterElementWithStructure element) throws IOException {
        NodeContext context = contextStack.pollLast();
        if (context == null) {
            return;
        }

        if (!context.objectNode) {
            return;
        }

        if (context.parentId == null && mainObjectType == null) {
            mainObjectType = context.name;
        }

        SheetState sheetState = getOrCreateSheet(context.name);
        ensureMainSheetFirst();
        writeObjectRow(sheetState, context);
    }

    @Override
    public void onDocumentComplete(StructuredWriter writer) throws IOException {
        if (workbook == null) {
            return;
        }

        Path outputPath = writer.outputPath;
        if (outputPath == null) {
            workbook.close();
            workbook = null;
            throw new IOException("StructuredWriterExcel requires StructuredWriter outputPath to write workbook");
        }

        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }

        try (OutputStream output = Files.newOutputStream(outputPath)) {
            createMainObjectFormSheet();
            workbook.write(output);
        } finally {
            workbook.close();
            workbook = null;
            contextStack.clear();
        }
    }

    private void writeObjectRow(SheetState sheetState, NodeContext context) {
        for (String column : context.rowValues.keySet()) {
            sheetState.ensureColumn(column);
        }

        Row row = sheetState.sheet.createRow(sheetState.nextRowIndex++);
        for (Map.Entry<String, String> entry : context.rowValues.entrySet()) {
            Integer columnIndex = sheetState.columnIndexes.get(entry.getKey());
            if (columnIndex == null) {
                continue;
            }
            Cell cell = row.createCell(columnIndex);
            cell.setCellValue(entry.getValue() == null ? "" : entry.getValue());
        }
    }

    private SheetState getOrCreateSheet(String objectType) {
        SheetState state = sheetsByObjectType.get(objectType);
        if (state != null) {
            return state;
        }

        String baseSheetName = sanitizeSheetName(objectType);
        String uniqueSheetName = buildUniqueSheetName(baseSheetName);
        Sheet sheet = workbook.createSheet(uniqueSheetName);

        state = new SheetState(sheet);
        state.ensureColumn(ID_COLUMN);
        state.ensureColumn(PARENT_COLUMN);
        sheetsByObjectType.put(objectType, state);
        objectTypeOrder.add(objectType);
        applyDeterministicSheetOrder();
        return state;
    }

    private void ensureMainSheetFirst() {
        applyDeterministicSheetOrder();
    }

    private void applyDeterministicSheetOrder() {
        if (workbook == null || mainObjectType == null) {
            return;
        }

        SheetState mainState = sheetsByObjectType.get(mainObjectType);
        if (mainState == null) {
            return;
        }

        List<String> orderedTypes = new ArrayList<>();
        orderedTypes.add(mainObjectType);
        for (String objectType : objectTypeOrder) {
            if (!mainObjectType.equals(objectType)) {
                orderedTypes.add(objectType);
            }
        }

        int sheetIndex = 0;
        for (String objectType : orderedTypes) {
            SheetState state = sheetsByObjectType.get(objectType);
            if (state == null) {
                continue;
            }

            String sheetName = state.sheet.getSheetName();
            int currentIndex = workbook.getSheetIndex(sheetName);
            if (currentIndex >= 0 && currentIndex != sheetIndex) {
                workbook.setSheetOrder(sheetName, sheetIndex);
            }
            sheetIndex++;
        }

        if (mainFormSheetName != null) {
            int formIndex = workbook.getSheetIndex(mainFormSheetName);
            if (formIndex > 1) {
                workbook.setSheetOrder(mainFormSheetName, 1);
            }
        }
    }

    private void createMainObjectFormSheet() {
        if (workbook == null || mainObjectType == null) {
            return;
        }

        SheetState mainState = sheetsByObjectType.get(mainObjectType);
        if (mainState == null) {
            return;
        }

        String formSheetName = buildUniqueSheetName(sanitizeSheetName(mainObjectType + "_Form"));
        Sheet formSheet = workbook.createSheet(formSheetName);
        mainFormSheetName = formSheetName;

        formSheet.setColumnWidth(0, 4200);
        formSheet.setColumnWidth(1, 5600);
        formSheet.setColumnWidth(2, 4200);
        formSheet.setColumnWidth(3, 5600);
        formSheet.setColumnWidth(4, 4200);
        formSheet.setColumnWidth(5, 5600);
        formSheet.setColumnWidth(6, 4200);
        formSheet.setColumnWidth(7, 5600);
        formSheet.setDisplayGridlines(false);
        formSheet.setPrintGridlines(false);
        formSheet.setDisplayRowColHeadings(false);
        formSheet.setZoom(120);
        formSheet.createFreezePane(0, 6);

        CellStyle headerStyle = createHeaderStyle();
        CellStyle sectionStyle = createSectionStyle();
        CellStyle labelStyle = createLabelStyle();
        CellStyle valueStyle = createValueStyle();
        CellStyle infoStyle = createInfoStyle();
        CellStyle blockTitleStyle = createBlockTitleStyle();
        CellStyle relatedHeaderStyle = createRelatedHeaderStyle();

        Row titleRow = formSheet.createRow(0);
        titleRow.setHeightInPoints(30f);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(mainObjectType + " - Entry Form");
        titleCell.setCellStyle(headerStyle);
        for (int col = 1; col <= 7; col++) {
            titleRow.createCell(col).setCellStyle(headerStyle);
        }
        mergeAndStyle(formSheet, 0, 0, 0, 7, headerStyle);

        Row summaryHeader = formSheet.createRow(2);
        Cell summaryHeaderCell = summaryHeader.createCell(0);
        summaryHeaderCell.setCellValue("Main Object Direct Data");
        summaryHeaderCell.setCellStyle(sectionStyle);
        for (int col = 1; col <= 7; col++) {
            summaryHeader.createCell(col).setCellStyle(sectionStyle);
        }
        mergeAndStyle(formSheet, 2, 2, 0, 7, sectionStyle);

        Row selectionRow = formSheet.createRow(3);
        Cell idLabelCell = selectionRow.createCell(0);
        idLabelCell.setCellValue("Selected Internal DB Id");
        idLabelCell.setCellStyle(labelStyle);
        Cell idValueCell = selectionRow.createCell(1);
        idValueCell.setCellValue("");
        idValueCell.setCellStyle(valueStyle);
        Cell selectionHintCell = selectionRow.createCell(2);
        selectionHintCell.setCellValue("Choose an Id to refresh all sections");
        selectionHintCell.setCellStyle(infoStyle);
        for (int col = 3; col <= 7; col++) {
            selectionRow.createCell(col).setCellStyle(infoStyle);
        }
        mergeAndStyle(formSheet, 3, 3, 2, 7, infoStyle);

        createSelectedIdNamedRange(formSheet);
        addIdValidation(formSheet, quoteSheetName(mainState.sheet.getSheetName()));

        int rowIndex = 5;
        int fieldIndex = 0;
        String mainSheetQuoted = quoteSheetName(mainState.sheet.getSheetName());
        for (Map.Entry<String, Integer> columnEntry : mainState.columnIndexes.entrySet()) {
            String columnName = columnEntry.getKey();
            if (ID_COLUMN.equals(columnName) || PARENT_COLUMN.equals(columnName)) {
                continue;
            }

            int targetRowIndex = rowIndex + (fieldIndex / 4);
            int pairOffset = (fieldIndex % 4) * 2;
            int sheetColumn = columnEntry.getValue();
            int lookupColumnIndex = sheetColumn + 1;
            Row row = getOrCreateRow(formSheet, targetRowIndex);

            Cell fieldLabelCell = row.createCell(pairOffset);
            fieldLabelCell.setCellValue(columnName);
            fieldLabelCell.setCellStyle(labelStyle);

            String formula = "IFERROR(VLOOKUP($B$4+0,'" + mainSheetQuoted + "'!$A:$ZZ," + lookupColumnIndex + ",FALSE),IFERROR(VLOOKUP($B$4&\"\",'" + mainSheetQuoted + "'!$A:$ZZ," + lookupColumnIndex + ",FALSE),\"\"))";
            Cell fieldValueCell = row.createCell(pairOffset + 1);
            fieldValueCell.setCellFormula(formula);
            fieldValueCell.setCellStyle(valueStyle);
            fieldIndex++;
        }

        rowIndex = rowIndex + ((fieldIndex + 3) / 4) + 1;
        Row relatedHeader = formSheet.createRow(rowIndex++);
        Cell relatedHeaderCell = relatedHeader.createCell(0);
        relatedHeaderCell.setCellValue("Related Data");
        relatedHeaderCell.setCellStyle(sectionStyle);
        for (int col = 1; col <= 7; col++) {
            relatedHeader.createCell(col).setCellStyle(sectionStyle);
        }
        mergeAndStyle(formSheet, relatedHeader.getRowNum(), relatedHeader.getRowNum(), 0, 7, sectionStyle);

        rowIndex = addRelatedDataPreviewBlocks(formSheet, rowIndex, blockTitleStyle, relatedHeaderStyle, labelStyle, valueStyle);

        applyDeterministicSheetOrder();
    }

    private int addRelatedDataPreviewBlocks(Sheet formSheet, int startRow, CellStyle blockTitleStyle, CellStyle relatedHeaderStyle, CellStyle labelStyle, CellStyle valueStyle) {
        int rowIndex = startRow;

        for (String objectType : objectTypeOrder) {
            if (objectType.equals(mainObjectType)) {
                continue;
            }

            SheetState relatedState = sheetsByObjectType.get(objectType);
            if (relatedState == null) {
                continue;
            }

            String relatedSheetQuoted = quoteSheetName(relatedState.sheet.getSheetName());
            int parentColumnIndex = 1;
            if (relatedState.columnIndexes.size() <= parentColumnIndex) {
                continue;
            }

            Row blockTitle = formSheet.createRow(rowIndex++);
            Cell blockTitleCell = blockTitle.createCell(0);
            blockTitleCell.setCellValue(objectType + " (Parent = selected Id)");
            blockTitleCell.setCellStyle(blockTitleStyle);
            for (int col = 1; col <= 7; col++) {
                blockTitle.createCell(col).setCellStyle(blockTitleStyle);
            }
            mergeAndStyle(formSheet, blockTitle.getRowNum(), blockTitle.getRowNum(), 0, 7, blockTitleStyle);

            Row countLabelRow = formSheet.createRow(rowIndex++);
            Cell countLabelCell = countLabelRow.createCell(0);
            countLabelCell.setCellValue("Related rows count");
            countLabelCell.setCellStyle(labelStyle);

            String parentColumnLetter = excelColumnLetter(parentColumnIndex);
            String countFormula = "SUMPRODUCT(--('" + relatedSheetQuoted + "'!$" + parentColumnLetter + "$2:$" + parentColumnLetter + "$1048576&\"\"=$B$4&\"\"))";
            Cell countValueCell = countLabelRow.createCell(1);
            countValueCell.setCellFormula(countFormula);
            countValueCell.setCellStyle(valueStyle);

            List<Map.Entry<String, Integer>> displayColumns = selectDisplayColumns(relatedState);
            Row headerRow = formSheet.createRow(rowIndex++);
            for (int col = 0; col < displayColumns.size() && col <= 7; col++) {
                Cell headerCell = headerRow.createCell(col);
                headerCell.setCellValue(displayColumns.get(col).getKey());
                headerCell.setCellStyle(relatedHeaderStyle);
            }

            int firstDataRow = rowIndex;
            for (int previewIndex = 0; previewIndex < 12; previewIndex++) {
                Row dataRow = formSheet.createRow(rowIndex);
                for (int col = 0; col < displayColumns.size() && col <= 7; col++) {
                    int sourceColumnIndex = displayColumns.get(col).getValue() + 1;
                    String rankExpr = "ROWS($A$" + (firstDataRow + 1) + ":$A$" + (rowIndex + 1) + ")";
                    String matchExpr = "AGGREGATE(15,6,ROW('" + relatedSheetQuoted + "'!$" + parentColumnLetter + "$2:$" + parentColumnLetter + "$1048576)/(('" + relatedSheetQuoted + "'!$" + parentColumnLetter + "$2:$" + parentColumnLetter + "$1048576&\"\"=$B$4&\"\"))," + rankExpr + ")";
                    Cell dataCell = dataRow.createCell(col);
                    dataCell.setCellFormula("IFERROR(INDEX('" + relatedSheetQuoted + "'!$A:$ZZ," + matchExpr + "," + sourceColumnIndex + "),\"\")");
                    dataCell.setCellStyle(valueStyle);
                }
                rowIndex++;
            }

            rowIndex += 1;
        }

        return rowIndex;
    }

    private List<Map.Entry<String, Integer>> selectDisplayColumns(SheetState relatedState) {
        List<Map.Entry<String, Integer>> result = new ArrayList<>();
        Map.Entry<String, Integer> idEntry = getColumnEntryByIndex(relatedState, 0);
        Map.Entry<String, Integer> parentEntry = getColumnEntryByIndex(relatedState, 1);

        if (idEntry != null) {
            result.add(idEntry);
        }
        if (parentEntry != null) {
            result.add(parentEntry);
        }

        for (Map.Entry<String, Integer> entry : relatedState.columnIndexes.entrySet()) {
            if (result.size() >= 8) {
                break;
            }
            if (ID_COLUMN.equals(entry.getKey()) || PARENT_COLUMN.equals(entry.getKey())) {
                continue;
            }
            result.add(entry);
        }

        return result;
    }

    private Map.Entry<String, Integer> getColumnEntryByIndex(SheetState relatedState, int targetIndex) {
        for (Map.Entry<String, Integer> entry : relatedState.columnIndexes.entrySet()) {
            if (entry.getValue() != null && entry.getValue() == targetIndex) {
                return entry;
            }
        }
        return null;
    }

    private String toSafeBusinessColumnName(String columnName) {
        if (columnName == null) {
            return null;
        }

        if (ID_COLUMN.equalsIgnoreCase(columnName) || PARENT_COLUMN.equalsIgnoreCase(columnName)) {
            return "Field." + columnName;
        }

        return columnName;
    }

    private void addIdValidation(Sheet formSheet, String mainSheetQuoted) {
        DataValidationHelper helper = formSheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createFormulaListConstraint("'" + mainSheetQuoted + "'!$A$2:$A$1048576");
        CellRangeAddressList addressList = new CellRangeAddressList(3, 3, 1, 1);
        DataValidation validation = helper.createValidation(constraint, addressList);
        validation.setSuppressDropDownArrow(false);
        formSheet.addValidationData(validation);
    }

    private void createSelectedIdNamedRange(Sheet formSheet) {
        Name selectedIdName = workbook.getName("SelectedId");
        if (selectedIdName == null) {
            selectedIdName = workbook.createName();
            selectedIdName.setNameName("SelectedId");
        }
        String quotedFormSheet = quoteSheetName(formSheet.getSheetName());
        selectedIdName.setRefersToFormula("'" + quotedFormSheet + "'!$B$4");
    }

    private CellStyle createHeaderStyle() {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 15);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createSectionStyle() {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        addThinBorders(style);
        return style;
    }

    private CellStyle createLabelStyle() {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        addThinBorders(style);
        return style;
    }

    private CellStyle createValueStyle() {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        addThinBorders(style);
        return style;
    }

    private CellStyle createInfoStyle() {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setItalic(true);
        font.setColor(IndexedColors.GREY_80_PERCENT.getIndex());
        style.setFont(font);
        style.setWrapText(true);
        return style;
    }

    private CellStyle createBlockTitleStyle() {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        addThinBorders(style);
        return style;
    }

    private CellStyle createRelatedHeaderStyle() {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.TEAL.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        addThinBorders(style);
        return style;
    }

    private void addThinBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setBottomBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setLeftBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setRightBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
    }

    private String quoteSheetName(String sheetName) {
        return sheetName == null ? "" : sheetName.replace("'", "''");
    }

    private String excelColumnLetter(int columnIndex) {
        int index = columnIndex;
        StringBuilder letter = new StringBuilder();
        while (index >= 0) {
            letter.insert(0, (char) ('A' + (index % 26)));
            index = (index / 26) - 1;
        }
        return letter.toString();
    }

    private Row getOrCreateRow(Sheet sheet, int rowIndex) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }
        return row;
    }

    private void mergeAndStyle(Sheet sheet, int firstRow, int lastRow, int firstColumn, int lastColumn, CellStyle style) {
        if (sheet == null) {
            return;
        }

        CellRangeAddress region = new CellRangeAddress(firstRow, lastRow, firstColumn, lastColumn);
        sheet.addMergedRegion(region);

        for (int rowIndex = firstRow; rowIndex <= lastRow; rowIndex++) {
            Row row = getOrCreateRow(sheet, rowIndex);
            for (int colIndex = firstColumn; colIndex <= lastColumn; colIndex++) {
                Cell cell = row.getCell(colIndex);
                if (cell == null) {
                    cell = row.createCell(colIndex);
                }
                cell.setCellStyle(style);
            }
        }
    }

    private String buildUniqueSheetName(String baseSheetName) {
        Integer count = sheetNameCounters.get(baseSheetName);
        if (count == null) {
            sheetNameCounters.put(baseSheetName, 1);
            return baseSheetName;
        }

        int current = count + 1;
        sheetNameCounters.put(baseSheetName, current);
        String suffix = "_" + current;
        int maxBaseLength = Math.max(1, 31 - suffix.length());
        String trimmed = baseSheetName.length() > maxBaseLength ? baseSheetName.substring(0, maxBaseLength) : baseSheetName;
        return trimmed + suffix;
    }

    private String sanitizeSheetName(String name) {
        String value = (name == null || name.isBlank()) ? "Sheet" : name;
        value = value.replaceAll("[\\\\/*?:\\[\\]]", "_");
        value = value.trim();
        if (value.isEmpty()) {
            value = "Sheet";
        }
        if (value.length() > 31) {
            value = value.substring(0, 31);
        }
        return value;
    }

    private boolean isObjectNode(StructuredWriterElementWithStructure element) {
        if (element.attributes != null && element.attributes.get("id") != null) {
            return true;
        }

        if (contextStack.isEmpty()) {
            return false;
        }

        NodeContext parent = contextStack.peekLast();
        return "objects".equals(parent.name);
    }

    private Long parseObjectId(Map<String, String> attributes) {
        if (attributes == null) {
            return null;
        }

        String id = attributes.get("id");
        if (id == null || id.isBlank()) {
            return null;
        }

        try {
            return Long.parseLong(id);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long findNearestParentObjectId() {
        List<NodeContext> contexts = new ArrayList<>(contextStack);
        for (int index = contexts.size() - 1; index >= 0; index--) {
            NodeContext context = contexts.get(index);
            if (!context.objectNode) {
                continue;
            }
            return context.objectId;
        }
        return null;
    }

    private Map<String, String> getCurrentRowValues() {
        List<NodeContext> contexts = new ArrayList<>(contextStack);
        for (int index = contexts.size() - 1; index >= 0; index--) {
            NodeContext context = contexts.get(index);
            if (context.objectNode) {
                return context.rowValues;
            }
        }
        return null;
    }

    private String buildColumnName(String fieldName) {
        if (contextStack.isEmpty()) {
            return fieldName;
        }

        List<NodeContext> contexts = new ArrayList<>(contextStack);
        int objectIndex = -1;
        for (int index = contexts.size() - 1; index >= 0; index--) {
            if (contexts.get(index).objectNode) {
                objectIndex = index;
                break;
            }
        }

        if (objectIndex < 0) {
            return fieldName;
        }

        StringBuilder column = new StringBuilder();
        for (int index = objectIndex + 1; index < contexts.size(); index++) {
            NodeContext context = contexts.get(index);
            if (context.objectNode) {
                continue;
            }
            if (column.length() > 0) {
                column.append('.');
            }
            column.append(context.name);
        }

        if (column.length() > 0) {
            column.append('.');
        }
        column.append(fieldName);
        return column.toString();
    }

    private static final class NodeContext {
        private final String name;
        private final boolean objectNode;
        private final Long objectId;
        private final Long parentId;
        private final Map<String, String> rowValues;

        private NodeContext(String name, Map<String, String> attributes, boolean objectNode, Long objectId, Long parentId) {
            this.name = name;
            this.objectNode = objectNode;
            this.objectId = objectId;
            this.parentId = parentId;
            this.rowValues = new LinkedHashMap<>();

            if (objectNode) {
                this.rowValues.put(ID_COLUMN, objectId != null ? String.valueOf(objectId) : "");
                this.rowValues.put(PARENT_COLUMN, parentId != null ? String.valueOf(parentId) : "");

                if (attributes != null) {
                    for (Map.Entry<String, String> entry : attributes.entrySet()) {
                        if (entry.getKey() == null || entry.getValue() == null || "id".equals(entry.getKey())) {
                            continue;
                        }
                        this.rowValues.put("@" + entry.getKey(), entry.getValue());
                    }
                }
            }
        }
    }

    private static final class SheetState {
        private final Sheet sheet;
        private final Map<String, Integer> columnIndexes = new LinkedHashMap<>();
        private int nextRowIndex = 1;

        private SheetState(Sheet sheet) {
            this.sheet = sheet;
            this.sheet.createRow(0);
        }

        private void ensureColumn(String name) {
            if (columnIndexes.containsKey(name)) {
                return;
            }

            int columnIndex = columnIndexes.size();
            columnIndexes.put(name, columnIndex);
            Row header = sheet.getRow(0);
            Cell cell = header.createCell(columnIndex);
            cell.setCellValue(name);
        }
    }
}
