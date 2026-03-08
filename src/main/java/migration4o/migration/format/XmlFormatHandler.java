package migration4o.migration.format;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import migration4o.migration.ExportFormat;
import migration4o.migration.ObjectExporter;
import migration4o.migration.XSDBuilder;
import migration4o.models.schema.DOSchemaClass;
import migration4o.util.tools.structuredwriter.StructuredWriter;
import migration4o.util.tools.structuredwriter.formats.StructuredWriterXML;

/**
 * Format handler for XML output, with optional XSD schema generation and
 * validation.
 * <p>
 * Own state: {@code xsdBuilder}, {@code exportedXMLFiles}, {@code generateXsd}.
 * Overrides six hooks: {@code init}, {@code observeObject}, {@code observeField},
 * {@code open}, {@code close}, {@code done}.
 */
public class XmlFormatHandler extends FormatHandler {

    private final Set<String> exportedXMLFiles = new HashSet<>();
    private final boolean generateXsd;
    private XSDBuilder liveXsdBuilder;

    public XmlFormatHandler(boolean generateXsd) {
        super(ExportFormat.XML);
        this.generateXsd = generateXsd;
    }

    @Override
    public String extension() { return ".xml"; }

    @Override
    public String displayName() { return "XML"; }

    @Override
    protected StructuredWriter createWriter(Path filePath) throws IOException {
        Files.createDirectories(filePath.getParent());
        return new StructuredWriter(new StructuredWriterXML(), new FileWriter(filePath.toFile()), filePath);
    }

    /** Initialises the shared XSD builder. */
    @Override
    public void init(ExportContext ctx) throws Exception {
        liveXsdBuilder = new XSDBuilder(ctx.operation.dbContext);
        liveXsdBuilder.startExportRoot();
    }

    /**
     * Registers the current class and top-level object in the XSD builder.
     * Called once per object; XSDBuilder operations are idempotent.
     */
    @Override
    public void observeObject(ExportContext ctx) throws Exception {
        if (ctx.schemaClass == null || liveXsdBuilder == null) return;
        liveXsdBuilder.addClass(ctx.schemaClass);
        DOSchemaClass dbSchemaClass = ctx.operation.databaseSchema.findClassByName(ctx.schemaClass.source);
        liveXsdBuilder.addTopLevelObject(ctx.schemaClass.destinationName, dbSchemaClass);
    }

    /** Registers the current field in the XSD builder. */
    @Override
    public void observeField(ExportContext ctx) throws Exception {
        if (ctx.field == null || ctx.schemaClass == null || liveXsdBuilder == null) return;
        liveXsdBuilder.addField(ctx.schemaClass, ctx.field);
    }

    /**
     * Opens the root {@code <export>} element with an XSD schema location
     * attribute, then writes class metadata and opens {@code <objects>}.
     */
    @Override
    public void open(ExportContext ctx) throws Exception {
        String schemaLocation = computeSchemaLocation(ctx);
        writer.openRootStructure("export", schemaLocation);
        if (ctx.schemaClass != null) {
            writer.metadata(ctx.schemaClass.getMetadata(ctx.moduleDisplayName()));
        }
        writer.openStructure("objects");
    }

    /**
     * Closes {@code <objects>} and {@code <export>}, flushes the writer, and
     * records the file path for post-export validation.
     */
    @Override
    public void close(ExportContext ctx) throws Exception {
        writer.closeStructure("objects");
        writer.closeStructure("export");
        writer.writer.flush();
        if (writer.outputPath != null) {
            exportedXMLFiles.add(writer.outputPath.toString());
        }
    }

    /**
     * Final pass: unreached objects → comprehensive XSD → XML validation.
     */
    @Override
    public void done(ExportContext ctx) throws Exception {
        exportUnreachedObjects(ctx);

        if (generateXsd && liveXsdBuilder != null) {
            Path xsdPath = ctx.basePath.resolve("_Migration").resolve("Schema.xsd");
            Files.createDirectories(xsdPath.getParent());
            if (ctx.operation.monitor != null) {
                ctx.operation.monitor.onStatusMessage("Generating comprehensive XSD schema...");
            }
            liveXsdBuilder.writeXSD(xsdPath.toString());
            if (ctx.operation.monitor != null) {
                ctx.operation.monitor.onStatusMessage("Comprehensive XSD schema generated: _Migration/Schema.xsd");
            }
        }

        if (generateXsd && !exportedXMLFiles.isEmpty()) {
            validateXmlFiles(ctx);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String computeSchemaLocation(ExportContext ctx) {
        if (writer == null || writer.outputPath == null) return null;
        Path schemaPath = ctx.basePath.resolve("_Migration").resolve("Schema.xsd");
        Path xmlDir = writer.outputPath.getParent();
        if (xmlDir == null) return "_Migration/Schema.xsd";
        try {
            return xmlDir.relativize(schemaPath).toString().replace('\\', '/');
        } catch (Exception e) {
            return "_Migration/Schema.xsd";
        }
    }

    private void exportUnreachedObjects(ExportContext ctx) throws Exception {
        Set<Long> reachedIds = collectReachedIds(ctx);
        Set<Long> unreachedIds = collectUnreachedIds(ctx, reachedIds);
        if (unreachedIds.isEmpty()) {
            if (ctx.operation.monitor != null) {
                ctx.operation.monitor.onStatusMessage("No unreached objects detected.");
            }
            return;
        }

        if (ctx.operation.monitor != null) {
            ctx.operation.monitor.onStatusMessage(
                "Exporting " + unreachedIds.size() + " unreached objects to _Migration/Extra.xml...");
        }

        Path extraPath = ctx.basePath.resolve("_Migration").resolve("Extra.xml");
        Files.createDirectories(extraPath.getParent());
        exportedXMLFiles.add(extraPath.toString());

        if (ctx.operation.monitor != null) {
            ctx.operation.monitor.onModuleStart("_Migration", unreachedIds.size(), 0);
        }

        this.writer = createWriter(extraPath);
        ctx.allowedObjectIds = new HashSet<>(unreachedIds);
        try {
            // Open with null schemaClass (mixed types, no metadata)
            writer.openRootStructure("export", computeSchemaLocation(ctx));
            writer.openStructure("objects");

            List<Long> sortedIds = new ArrayList<>(unreachedIds);
            Collections.sort(sortedIds);

            ObjectExporter objectExporter = new ObjectExporter(ctx, this);
            for (Long objectId : sortedIds) {
                if (objectId == null || objectId <= 0) continue;
                if (ctx.operation.monitor != null && ctx.operation.monitor.isCancelled()) break;
                objectExporter.exportObject(objectId, false);
            }

            writer.closeStructure("objects");
            writer.closeStructure("export");
            writer.writer.flush();
        } finally {
            ctx.allowedObjectIds = null;
        }

        if (ctx.operation.monitor != null) {
            ctx.operation.monitor.onModuleComplete("_Migration");
        }
    }

    private Set<Long> collectReachedIds(ExportContext ctx) {
        Set<Long> reached = new HashSet<>();
        if (ctx.statistics == null || ctx.statistics.exportedObjectIds == null) return reached;
        for (List<Long> ids : ctx.statistics.exportedObjectIds.values()) {
            if (ids != null) reached.addAll(ids);
        }
        return reached;
    }

    private Set<Long> collectUnreachedIds(ExportContext ctx, Set<Long> reachedIds) {
        Set<Long> all = new HashSet<>();
        if (ctx.operation.databaseSchema == null) return all;
        for (DOSchemaClass sc : ctx.operation.databaseSchema.getClasses()) {
            long[] ids = (sc.uniqueObjectIds != null && sc.uniqueObjectIds.length > 0)
                    ? sc.uniqueObjectIds : sc.objectIds;
            if (ids == null) continue;
            for (long id : ids) {
                if (id > 0) all.add(id);
            }
        }
        all.removeAll(reachedIds);
        return all;
    }

    private void validateXmlFiles(ExportContext ctx) {
        try {
            if (ctx.operation.monitor != null) {
                ctx.operation.monitor.onStatusMessage(
                    "Validating " + exportedXMLFiles.size() + " XML files against schema...");
            }
            Path xsdPath = ctx.basePath.resolve("_Migration").resolve("Schema.xsd");
            migration4o.util.XMLValidator.ValidationResult result =
                    migration4o.util.XMLValidator.validateMultiple(
                            new ArrayList<>(exportedXMLFiles), xsdPath.toString());

            System.out.println();
            if (result.allValid()) {
                System.out.println("=== OVERALL VALIDATION: PASS (" + result.getTotalCount() + " files) ===");
            } else {
                System.out.println("=== OVERALL VALIDATION: FAIL (" + result.successCount
                        + " passed, " + result.failedFiles.size() + " failed) ===");
            }
            System.out.println();

            if (ctx.operation.monitor != null) {
                if (result.allValid()) {
                    ctx.operation.monitor.onStatusMessage(
                        "✓ All " + result.getTotalCount() + " XML files validated successfully");
                } else {
                    ctx.operation.monitor.onStatusMessage("⚠ Validation: " + result.successCount
                        + " passed, " + result.failedFiles.size() + " failed");
                    for (String failed : result.failedFiles) {
                        ctx.operation.monitor.onStatusMessage(
                            "  ✗ " + new java.io.File(failed).getName());
                    }
                }
            }
        } catch (Exception e) {
            if (ctx.operation.monitor != null) {
                ctx.operation.monitor.onStatusMessage(
                    "Warning: XML validation failed: " + e.getMessage());
            }
        }
    }
}
