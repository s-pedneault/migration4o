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

import migration4o.database.DODatabase;
import migration4o.database.DODatabaseClass;
import migration4o.migration.ExportFormat;
import migration4o.migration.ObjectExporter;
import migration4o.migration.xsd.XSDBuilder;
import migration4o.models.schema.DOSchemaClass;
import migration4o.util.tools.structuredwriter.StructuredWriter;
import migration4o.util.tools.structuredwriter.StructuredWriterMetadata;
import migration4o.util.tools.structuredwriter.formats.StructuredWriterXML;

/**
 * Format handler for XML output, with optional XSD schema generation and
 * validation.
 * <p>
 * Own state: {@code xsdBuilder}, {@code exportedXMLFiles}, {@code generateXsd}.
 * Overrides four hooks: {@code init}, {@code open}, {@code close},
 * {@code done}.
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
    public String extension() {
        return ".xml";
    }

    @Override
    public String displayName() {
        return "XML";
    }

    @Override
    protected StructuredWriter createWriter(Path filePath) throws IOException {
        Files.createDirectories(filePath.getParent());
        return new StructuredWriter(new StructuredWriterXML(), new FileWriter(filePath.toFile()), filePath);
    }

    /** Initialises the shared XSD builder. */
    @Override
    public void init(ExportCurrentState ctx) throws Exception {
        liveXsdBuilder = new XSDBuilder();
    }

    /**
     * Opens the root {@code <export>} element with an XSD schema location
     * attribute, then writes class metadata and opens {@code <objects>}.
     */
    @Override
    public void open(ExportCurrentState ctx) throws Exception {
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
    public void close(ExportCurrentState ctx) throws Exception {
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
    public void done(ExportCurrentState ctx) throws Exception {
        // Extra.xml is only generated in unrestricted ("all") mode — i.e. when
        // no per-class object limit is set. In limited/preview exports the
        // reachability data is incomplete so the Extra file would be
        // misleading.
        if (ctx.request.maxObjectsPerClass == null) {
            exportUnreachedObjects(ctx);
        }

        if (generateXsd && liveXsdBuilder != null) {
            Path xsdPath = formatBasePath(ctx).resolve("_Migration").resolve("Schema.xsd");
            Files.createDirectories(xsdPath.getParent());
            if (ctx.request.monitor != null) {
                ctx.request.monitor.onStatusMessage("Generating comprehensive XSD schema...");
            }
            liveXsdBuilder.writeXSD(xsdPath.toString());
            if (ctx.request.monitor != null) {
                ctx.request.monitor.onStatusMessage("Comprehensive XSD schema generated: _Migration/Schema.xsd");
            }
        }

        if (generateXsd && !exportedXMLFiles.isEmpty()) {
            validateXmlFiles(ctx);
        }
    }

    // ── Private helpers
    // ───────────────────────────────────────────────────────

    private String computeSchemaLocation(ExportCurrentState ctx) {
        if (writer == null || writer.outputPath == null)
            return null;
        Path schemaPath = formatBasePath(ctx).resolve("_Migration").resolve("Schema.xsd");
        Path xmlDir = writer.outputPath.getParent();
        if (xmlDir == null)
            return "_Migration/Schema.xsd";
        try {
            return xmlDir.relativize(schemaPath).toString().replace('\\', '/');
        } catch (Exception e) {
            return "_Migration/Schema.xsd";
        }
    }

    private void exportUnreachedObjects(ExportCurrentState ctx) throws Exception {
        Set<Long> reachedIds = collectReachedIds(ctx);
        Set<Long> unreachedIds = collectUnreachedIds(ctx, reachedIds);
        if (unreachedIds.isEmpty()) {
            if (ctx.request.monitor != null) {
                ctx.request.monitor.onStatusMessage("No unreached objects detected.");
            }
            return;
        }

        if (ctx.request.monitor != null) {
            String limitNote = (ctx.request.maxObjectsPerClass != null) ? " (limited to " + ctx.request.maxObjectsPerClass + " per class)" : "";
            ctx.request.monitor.onStatusMessage("Exporting " + unreachedIds.size() + " unreached objects to _Migration/Extra.xml..." + limitNote);
        }

        Path extraPath = formatBasePath(ctx).resolve("_Migration").resolve("Extra.xml");
        Files.createDirectories(extraPath.getParent());
        exportedXMLFiles.add(extraPath.toString());

        if (ctx.request.monitor != null) {
            ctx.request.monitor.onModuleStart("_Migration", unreachedIds.size(), 0);
        }

        this.writer = createWriter(extraPath);
        ctx.allowedObjectIds = new HashSet<>(unreachedIds);

        int extraCount = unreachedIds.size();
        // Initialise per-class statistics so incrementAttempted() tracks
        // progress against the correct total (not the last regular class).
        if (ctx.statistics != null) {
            ctx.statistics.setCurrentClass("Extra", extraCount);
            ctx.statistics.setCurrentFormatName(displayName());
        }
        if (ctx.request.monitor != null) {
            ctx.request.monitor.onClassStart("Extra", "Extra", extraCount, displayName());
        }

        try {
            // Open with metadata for Extra.xml
            writer.openRootStructure("export", computeSchemaLocation(ctx));
            StructuredWriterMetadata extraMetadata = new StructuredWriterMetadata();
            extraMetadata.generator = "Migration4o";
            extraMetadata.provider = "Gestion Technologies";
            extraMetadata.module = "_Migration";
            extraMetadata.type = "Extra";
            extraMetadata.objects = String.valueOf(unreachedIds.size());
            writer.metadata(extraMetadata);
            writer.openStructure("objects");

            List<Long> sortedIds = new ArrayList<>(unreachedIds);
            Collections.sort(sortedIds);

            ObjectExporter objectExporter = new ObjectExporter(ctx, this);
            int extraExported = 0;
            for (Long objectId : sortedIds) {
                if (objectId == null || objectId <= 0)
                    continue;
                if (ctx.request.monitor != null && ctx.request.monitor.isCancelled())
                    break;
                objectExporter.exportObject(objectId, false);
                extraExported++;
                if (ctx.request.monitor != null && extraExported % 100 == 0 && extraCount > 0) {
                    ctx.request.monitor.onObjectProgress("Extra", "Extra", extraExported, extraCount, displayName());
                }
            }

            writer.closeStructure("objects");
            writer.closeStructure("export");
            writer.writer.flush();
        } finally {
            ctx.allowedObjectIds = null;
        }

        if (ctx.request.monitor != null) {
            int exported = ctx.statistics != null ? ctx.statistics.getUniqueExportedCount() : 0;
            ctx.request.monitor.onClassComplete("Extra", exported, displayName());
            ctx.request.monitor.onModuleComplete("_Migration");
        }
    }

    private Set<Long> collectReachedIds(ExportCurrentState ctx) {
        Set<Long> reached = new HashSet<>();
        if (ctx.statistics == null)
            return reached;
        // exportedObjectIdsSet is always populated regardless of fullTracking.
        // exportedObjectIds is only a copy made when fullTracking=true, so we
        // must not use it here — otherwise the Extra pass would treat every
        // object as unreached when "Full tracking & analysis" is disabled.
        for (java.util.Set<Long> ids : ctx.statistics.exportedObjectIdsSet.values()) {
            if (ids != null)
                reached.addAll(ids);
        }
        return reached;
    }

    private Set<Long> collectUnreachedIds(ExportCurrentState ctx, Set<Long> reachedIds) {
        Set<Long> all = new HashSet<>();
        if (ctx.request.database == null)
            return all;
        Integer limit = ctx.request.maxObjectsPerClass;
        for (DODatabaseClass dbClass : ctx.request.database.getClasses()) {
            long[] ids = (dbClass.objects.uniqueObjectIds != null && dbClass.objects.uniqueObjectIds.length > 0) ? dbClass.objects.uniqueObjectIds : dbClass.objects.objectIds;
            if (ids == null)
                continue;
            int classCount = 0;
            for (long id : ids) {
                if (id <= 0 || reachedIds.contains(id))
                    continue;
                if (limit != null && classCount >= limit)
                    break;
                all.add(id);
                classCount++;
            }
        }
        return all;
    }

    private void validateXmlFiles(ExportCurrentState ctx) {
        try {
            if (ctx.request.monitor != null) {
                ctx.request.monitor.onStatusMessage("Validating " + exportedXMLFiles.size() + " XML files against schema...");
            }
            Path xsdPath = formatBasePath(ctx).resolve("_Migration").resolve("Schema.xsd");
            migration4o.util.XMLValidator.ValidationResult result = migration4o.util.XMLValidator.validateMultiple(new ArrayList<>(exportedXMLFiles), xsdPath.toString());

            System.out.println();
            if (result.allValid()) {
                System.out.println("=== OVERALL VALIDATION: PASS (" + result.getTotalCount() + " files) ===");
            } else {
                System.out.println("=== OVERALL VALIDATION: FAIL (" + result.successCount + " passed, " + result.failedFiles.size() + " failed) ===");
            }
            System.out.println();

            if (ctx.request.monitor != null) {
                if (result.allValid()) {
                    ctx.request.monitor.onStatusMessage("✓ All " + result.getTotalCount() + " XML files validated successfully");
                } else {
                    ctx.request.monitor.onStatusMessage("⚠ Validation: " + result.successCount + " passed, " + result.failedFiles.size() + " failed");
                    for (String failed : result.failedFiles) {
                        ctx.request.monitor.onStatusMessage("  ✗ " + new java.io.File(failed).getName());
                    }
                }
            }
        } catch (Exception e) {
            if (ctx.request.monitor != null) {
                ctx.request.monitor.onStatusMessage("Warning: XML validation failed: " + e.getMessage());
            }
        }
    }
}
