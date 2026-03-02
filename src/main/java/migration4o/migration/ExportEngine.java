package migration4o.migration;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import migration4o.database.DODatabaseService;
import migration4o.migration.monitoring.ExportStatistics;
import migration4o.migration.monitoring.ReferencedClassTracker;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.ui.ClassExportConfig;
import migration4o.models.ui.MigrationModule;
import migration4o.ui.common.DOExportMonitor;
import migration4o.util.JsViewerHtmlGenerator;
import migration4o.util.SchemaUtil;
import migration4o.util.XmlViewerHtmlGenerator;
import migration4o.util.tools.structuredwriter.StructuredWriter;
import migration4o.util.tools.structuredwriter.StructuredWriterAPI;
import migration4o.util.tools.structuredwriter.StructuredWriterMetadata;
import migration4o.util.tools.structuredwriter.StructuredWriterProvider;
import migration4o.util.tools.structuredwriter.formats.StructuredWriterXML;

/**
 * Orchestrates XML export operations by coordinating specialized components.
 * This class has been refactored to delegate to: - XMLWriter: XML file
 * generation - ExportStatistics: tracking metrics and errors - ObjectExporter:
 * object graph traversal - XSDBuilder: schema generation
 */
public class ExportEngine {
    private final ExportOperation operation;

    // ── Module nav tree (built once before export) ──────────────────────────
    private static final class NavNode {
        final String label;
        /** Root-relative href (e.g. "Activités/Intervention/Intervention.html"); null for groups. */
        final String rootRelativeHref;
        final List<NavNode> children = new ArrayList<>();

        NavNode(String label, String rootRelativeHref) {
            this.label = label;
            this.rootRelativeHref = rootRelativeHref;
        }

        boolean isLeaf() {
            return rootRelativeHref != null;
        }
    }

    /** Top-level nav tree — same for all files in this export. */
    private final List<NavNode> navTree = new ArrayList<>();
    /** Nav JSON serialized once from navTree; injected verbatim into every HTML file. */
    private String cachedNavJson = "[]";

    /**
     * Creates export engine using the shared in-memory database from DODatabaseService.
     * 
     * @param schema         The reference schema
     * @param databaseSchema The database schema
     * @param databasePath   The database file path (for naming output folders)
     * @param dbContext      The database context
     */
    public ExportEngine(DOSchema schema, DOSchema databaseSchema, String databasePath, migration4o.database.DODatabaseContext dbContext) {
        this.operation = new ExportOperation();
        this.operation.referenceSchema = schema;
        this.operation.databaseSchema = databaseSchema;
        this.operation.databasePath = databasePath;
        this.operation.dbContext = dbContext;
        this.operation.maxObjectsPerClass = null;
        this.operation.exportNativeIds = false;
        this.operation.outputFormat = "XML";
        this.operation.applyUserSelectedFieldExclusions = true;
        this.operation.applySkipWhenConditions = true;
        this.operation.applyExportCriteriaFilters = true;
        this.operation.skipObjectsWithoutExportableFields = true;
        this.operation.availableSkipUserOptions = SchemaUtil.collectSkipUserOptions(schema);
        this.operation.selectedSkipUserOptions = new ArrayList<>();
        this.operation.exportedObjectIds = new HashSet<>();
        this.operation.useSharedTracking = false;
        this.operation.sharedXSDBuilder = null;
        this.operation.exportedXMLFiles = null;

        // Get the shared in-memory container from the context
        this.operation.container = dbContext != null ? dbContext.container : null;

        if (operation.container == null) {
            throw new IllegalStateException("No database is open.");
        }
    }

    /**
     * Sets the maximum number of objects to export per class.
     * 
     * @param maxObjectsPerClass Maximum objects to export per class, or null for
     *                           all objects
     */
    public void setMaxObjectsPerClass(Integer maxObjectsPerClass) {
        this.operation.maxObjectsPerClass = maxObjectsPerClass;
    }

    /**
     * Sets whether to export native DB4O object IDs as XML attributes.
     * 
     * @param exportNativeIds true to include id attribute with DB4O object ID
     */
    public void setExportNativeIds(boolean exportNativeIds) {
        this.operation.exportNativeIds = exportNativeIds;
    }

    /**
     * Sets fields selected by the user to be skipped during export.
     * 
     * @param selectedSkipOptions schema fields to skip
     */
    public void setSelectedSkipOptions(List<migration4o.models.schema.DOSchemaField> selectedSkipOptions) {
        if (selectedSkipOptions == null) {
            this.operation.selectedSkipUserOptions = new ArrayList<>();
        } else {
            this.operation.selectedSkipUserOptions = new ArrayList<>(selectedSkipOptions);
        }
    }

    public void setApplyUserSelectedFieldExclusions(boolean applyUserSelectedFieldExclusions) {
        this.operation.applyUserSelectedFieldExclusions = applyUserSelectedFieldExclusions;
    }

    public void setApplySkipWhenConditions(boolean applySkipWhenConditions) {
        this.operation.applySkipWhenConditions = applySkipWhenConditions;
    }

    public void setApplyExportCriteriaFilters(boolean applyExportCriteriaFilters) {
        this.operation.applyExportCriteriaFilters = applyExportCriteriaFilters;
    }

    public void setSkipObjectsWithoutExportableFields(boolean skipObjectsWithoutExportableFields) {
        this.operation.skipObjectsWithoutExportableFields = skipObjectsWithoutExportableFields;
    }

    /**
     * Sets the structured output format used by the writer layer.
     *
     * @param outputFormat format name from StructuredWriterProvider (e.g. XML,
     *                     JSON, EXCEL)
     */
    public void setOutputFormat(String outputFormat) {
        if (outputFormat == null || outputFormat.isBlank()) {
            this.operation.outputFormat = "XML";
        } else {
            this.operation.outputFormat = outputFormat;
        }
    }

    public void setGenerateHtmlViewer(boolean generateHtmlViewer) {
        this.operation.generateHtmlViewer = generateHtmlViewer;
    }

    /**
     * Pre-builds the hierarchical nav tree for the HTML viewer sidebar.
     * Must be called before export starts. The tree mirrors the module structure:
     *   path-prefix groups → module groups → class leaves (recursively).
     */
    public void setModuleNavData(List<MigrationModule> modules, List<String> modulePaths, String baseOutputDir) {
        navTree.clear();
        cachedNavJson = "[]";
        if (modules == null || modules.isEmpty())
            return;
        Path base = getBaseOutputPath(baseOutputDir);

        // Group top-level modules by their first path segment.
        LinkedHashMap<String, NavNode> prefixGroups = new LinkedHashMap<>();

        for (int i = 0; i < modules.size(); i++) {
            MigrationModule m = modules.get(i);
            String mp = (modulePaths != null && i < modulePaths.size()) ? modulePaths.get(i) : m.getName();
            String[] parts = mp.split("/");

            // Compute the module's actual output folder (mirrors exportModuleRecursive logic)
            Path moduleFolderPath = base;
            for (String part : parts) {
                moduleFolderPath = moduleFolderPath.resolve(part);
            }
            Path actualModuleFolder = moduleFolderPath.getParent() != null ? moduleFolderPath.getParent().resolve(m.getName()) : base.resolve(m.getName());

            NavNode moduleNode = new NavNode(m.getName(), null);
            buildModuleNavChildren(moduleNode, m, actualModuleFolder, base);

            if (parts.length > 1) {
                NavNode group = prefixGroups.computeIfAbsent(parts[0], k -> {
                    NavNode g = new NavNode(k, null);
                    navTree.add(g);
                    return g;
                });
                group.children.add(moduleNode);
            } else {
                navTree.add(moduleNode);
            }
        }

        // Serialize once — all files share the same root-relative hrefs
        cachedNavJson = serializeNavTree();
    }

    /**
     * Recursively adds class leaves and child module groups to a nav node,
     * using root-relative href strings.
     */
    private void buildModuleNavChildren(NavNode node, MigrationModule module, Path folderPath, Path base) {
        for (ClassExportConfig config : module.getClassConfigs()) {
            String destName = config.getDestinationFileName();
            String href = base.relativize(folderPath.resolve(destName + ".html")).toString().replace('\\', '/');
            node.children.add(new NavNode(destName, href));
        }
        for (MigrationModule child : module.getChildModules()) {
            NavNode childNode = new NavNode(child.getName(), null);
            buildModuleNavChildren(childNode, child, folderPath.resolve(child.getName()), base);
            node.children.add(childNode);
        }
    }

    private String serializeNavTree() {
        if (navTree.isEmpty())
            return "[]";
        StringBuilder sb = new StringBuilder();
        appendNavNodes(sb, navTree);
        return sb.toString();
    }

    private void appendNavNodes(StringBuilder sb, List<NavNode> nodes) {
        sb.append('[');
        for (int i = 0; i < nodes.size(); i++) {
            if (i > 0)
                sb.append(',');
            appendNavNode(sb, nodes.get(i));
        }
        sb.append(']');
    }

    private void appendNavNode(StringBuilder sb, NavNode node) {
        sb.append("{\"label\":\"").append(escNavJson(node.label)).append('"');
        if (node.isLeaf()) {
            sb.append(",\"href\":\"").append(escNavJson(node.rootRelativeHref)).append('"');
        } else {
            sb.append(",\"children\":");
            appendNavNodes(sb, node.children);
        }
        sb.append('}');
    }

    /**
     * Computes the base href for a given output file — a relative path from that
     * file's directory back to the db export root (e.g. "../../" for depth 2).
     */
    private String computeBaseHref(Path outputPath) {
        try {
            Path dbBase = getBaseOutputPath(operation.baseOutputPath);
            Path fileDir = outputPath.getParent();
            if (fileDir == null)
                return "./";
            Path rel = dbBase.relativize(fileDir);
            int depth = rel.getNameCount();
            if (depth == 0 || (depth == 1 && rel.getName(0).toString().isEmpty()))
                return "./";
            StringBuilder bh = new StringBuilder();
            for (int i = 0; i < depth + 1; i++)
                bh.append("../");
            return bh.toString();
        } catch (Exception e) {
            return "./";
        }
    }

    private static String escNavJson(String v) {
        if (v == null)
            return "";
        return v.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public boolean isXMLFormat() {
        return "XML".equalsIgnoreCase(getStructuredWriterAPI().getName());
    }

    private boolean shouldExportNativeIdsForCurrentFormat() {
        if ("EXCEL".equalsIgnoreCase(getStructuredWriterAPI().getName())) {
            return true;
        }
        return operation.exportNativeIds;
    }

    /**
     * Initializes shared object tracking and XSD builder for multi-module exports.
     * Call this before exporting multiple modules to ensure objects are only
     * counted once and a single comprehensive XSD is generated.
     */
    public void initializeSharedTracking() {
        operation.exportedObjectIds = new HashSet<>();
        operation.useSharedTracking = true;
        operation.sharedXSDBuilder = new XSDBuilder(operation.dbContext);
        operation.sharedXSDBuilder.startExportRoot();
        operation.exportedXMLFiles = new HashSet<>();
    }

    /**
     * Resets shared object tracking and XSD builder. Call this to clear tracking
     * state between different export sessions.
     */
    public void resetSharedTracking() {
        operation.useSharedTracking = false;
        operation.exportedObjectIds = new HashSet<>();
        operation.sharedXSDBuilder = null;
        operation.exportedXMLFiles = null;
    }

    /**
     * Gets the list of exported XML files. Only available when using shared
     * tracking.
     * 
     * @return Set of XML file paths, or null if not using shared tracking
     */
    public Set<String> getExportedXMLFiles() {
        return operation.exportedXMLFiles;
    }

    /**
     * Writes the comprehensive XSD schema file. Should be called after all exports
     * are complete when using shared tracking.
     * 
     * @param baseOutputPath The base output directory
     * @throws IOException if writing fails
     */
    public void writeComprehensiveXSD(String baseOutputPath) throws IOException {
        if (operation.sharedXSDBuilder == null) {
            throw new IllegalStateException("Shared XSD builder not initialized. Call initializeSharedTracking() first.");
        }

        Path xsdPath = getComprehensiveSchemaPath(baseOutputPath);
        Files.createDirectories(xsdPath.getParent());
        operation.sharedXSDBuilder.writeXSD(xsdPath.toString());
    }

    private Path getComprehensiveSchemaPath(String baseOutputPath) {
        Path dbBasePath = getBaseOutputPath(baseOutputPath);
        return dbBasePath.resolve("_Migration").resolve("Schema.xsd");
    }

    private String getSchemaLocationForXml(Path xmlPath) {
        if (!isXMLFormat()) {
            return null;
        }

        Path schemaPath = getComprehensiveSchemaPath(operation.baseOutputPath);
        Path xmlDir = xmlPath.getParent();
        if (xmlDir == null) {
            return "_Migration/Schema.xsd";
        }

        try {
            return xmlDir.relativize(schemaPath).toString().replace('\\', '/');
        } catch (Exception e) {
            return "_Migration/Schema.xsd";
        }
    }

    /**
     * Extracts the database folder name from the database path. For example:
     * "local/54060/BackupManuel.dat" -> "54060"
     */
    private String getDatabaseFolderName() {
        if (operation.databasePath == null) {
            return "default";
        }

        Path path = Paths.get(operation.databasePath);
        Path parent = path.getParent();

        if (parent != null) {
            return parent.getFileName().toString();
        }

        return "default";
    }

    /**
     * Gets the base output directory for the current database. Returns:
     * output/<database-folder>/
     */
    public Path getBaseOutputPath(String baseOutputDir) {
        String dbFolder = getDatabaseFolderName();
        return Paths.get(baseOutputDir).resolve(dbFolder);
    }

    /**
     * Exports all objects in a module to XML file (legacy flat structure).
     * 
     * @deprecated Use exportModuleStructured for folder-based export
     */
    @Deprecated
    public ExportStatistics exportModule(List<String> classNames, String moduleName, String outputPath) throws Exception {
        return exportModule(classNames, moduleName, outputPath, null);
    }

    /**
     * Exports a module with folder structure, using provided hierarchical path.
     * This is used when exporting a child module standalone to preserve the parent
     * folder structure.
     * 
     * @param module         The module to export (including child modules)
     * @param modulePath     The full hierarchical path for this module (e.g.,
     *                       "Activités/Intervention")
     * @param baseOutputPath The base output directory (typically "output")
     * @param monitor        Progress monitor (can be null)
     * @param sharedTracker  Shared reference tracker for bulk exports (can be null)
     * @return ExportStatistics with details about the export operation
     * @throws Exception if export fails
     */
    public ExportStatistics exportModuleStructured(MigrationModule module, String modulePath, String baseOutputPath, DOExportMonitor monitor, ReferencedClassTracker sharedTracker) throws Exception {
        System.out.println("DEBUG exportModuleStructured: module=" + module.getName() + ", modulePath=" + modulePath);

        // Count total classes for progress reporting
        int totalClasses = countTotalClasses(module);

        operation.monitor = monitor;
        operation.baseOutputPath = baseOutputPath;

        if (operation.monitor != null) {
            operation.monitor.onExportStart(module.getName(), totalClasses);
        }

        operation.statistics = new ExportStatistics(operation.monitor);

        // Use shared tracker if provided, otherwise create new one
        operation.referencedClassTracker = sharedTracker != null ? sharedTracker : new ReferencedClassTracker();

        try {
            // Use the in-memory container (already open)

            // Create database-specific output directory: output/<db-folder>/
            Path dbBasePath = getBaseOutputPath(baseOutputPath);
            Files.createDirectories(dbBasePath);

            // Register all modules and their classes with the tracker
            registerModuleClasses(module, operation.referencedClassTracker);

            // Build the full path including parent modules
            // For example, "Activités/Intervention" becomes
            // output/54060/Activités/Intervention
            Path fullModulePath = dbBasePath;
            if (modulePath != null && !modulePath.isEmpty()) {
                String[] pathParts = modulePath.split("/");
                for (String part : pathParts) {
                    fullModulePath = fullModulePath.resolve(part);
                }
            }

            // Create parent directories
            if (fullModulePath.getParent() != null) {
                Files.createDirectories(fullModulePath.getParent());
            }

            // Export module - start from the parent path (not the module
            // itself)
            // The module will create its own folder within this path
            Path moduleBasePath = fullModulePath.getParent();
            if (moduleBasePath == null) {
                moduleBasePath = dbBasePath;
            }

            exportModuleRecursive(module, moduleBasePath, 0);

            // Only export referenced classes if we created the tracker (not
            // shared)
            // For shared trackers, the caller will handle referenced classes
            if (sharedTracker == null) {
                // After exporting requested modules, check for referenced
                // classes
                Set<String> referencedClasses = operation.referencedClassTracker.getReferencedClasses();
                if (!referencedClasses.isEmpty() && operation.monitor != null) {
                    operation.monitor.onStatusMessage("Discovered " + referencedClasses.size() + " referenced classes not in export request");
                }

                // Export referenced classes to a "Referenced" module
                if (!referencedClasses.isEmpty()) {
                    exportReferencedClasses(referencedClasses, dbBasePath);
                }
            }

            // Generate duplicate warnings and set export info
            String fullOutputPath = dbBasePath.toString();
            operation.statistics.schemaWarnings.clear();
            operation.statistics.schemaWarnings.addAll(operation.statistics.duplicationDetector.generateDuplicateWarnings());
            operation.statistics.setExportInfo(module.getName(), fullOutputPath);

            if (operation.monitor != null) {
                operation.monitor.onExportComplete(module.getName(), operation.statistics.objectsSucceeded, operation.statistics.schemaWarnings.size());
            }

            return operation.statistics;

        } catch (Exception e) {
            if (operation.monitor != null) {
                operation.monitor.onExportError(module.getName(), e.getMessage());
            }
            throw e;
        } finally {
            // Don't close container - it's shared and managed by MainWindow
        }
    }

    /**
     * Exports a module with folder structure matching the module hierarchy. Each
     * class is exported to its own XML/XSD file within the module folders. Files
     * are written to output/<db-folder>/
     * 
     * Automatically tracks and exports referenced classes that are not in the
     * module structure. Referenced classes are exported to a virtual "Referenced"
     * module.
     * 
     * @param module         The module to export (including child modules)
     * @param baseOutputPath The base output directory (typically "output")
     * @param monitor        Progress monitor (can be null)
     * @param sharedTracker  Shared reference tracker for bulk exports (can be null)
     * @return ExportStatistics with details about the export operation
     * @throws Exception if export fails
     */
    public ExportStatistics exportModuleStructured(MigrationModule module, String baseOutputPath, DOExportMonitor monitor, ReferencedClassTracker sharedTracker) throws Exception {
        // Use module name as path (backward compatibility - single module at
        // root
        // level)
        return exportModuleStructured(module, module.getName(), baseOutputPath, monitor, sharedTracker);
    }

    /**
     * Exports a module with folder structure (convenience method without shared
     * tracker).
     * 
     * @param module         The module to export (including child modules)
     * @param baseOutputPath The base output directory (typically "output")
     * @param monitor        Progress monitor (can be null)
     * @return ExportStatistics with details about the export operation
     * @throws Exception if export fails
     */
    public ExportStatistics exportModuleStructured(MigrationModule module, String baseOutputPath, DOExportMonitor monitor) throws Exception {
        return exportModuleStructured(module, baseOutputPath, monitor, null);
    }

    /**
     * Exports referenced classes that were discovered during bulk export. Should be
     * called after all modules have been exported with a shared tracker.
     * 
     * @param baseOutputPath The base output directory (typically "output")
     * @param monitor        Progress monitor (can be null)
     * @param tracker        The shared reference tracker from bulk export
     * @throws Exception if export fails
     */
    public ExportStatistics exportReferencedClasses(String baseOutputPath, DOExportMonitor monitor, ReferencedClassTracker tracker) throws Exception {

        operation.baseOutputPath = baseOutputPath;
        operation.monitor = monitor;
        operation.referencedClassTracker = tracker;
        operation.statistics = new ExportStatistics(operation.monitor);

        Set<String> referencedClasses = tracker.getReferencedClasses();
        if (referencedClasses.isEmpty()) {
            operation.statistics.setExportInfo("Referenced", getBaseOutputPath(baseOutputPath).resolve("Referenced").toString());
            return operation.statistics;
        }

        if (operation.monitor != null) {
            operation.monitor.onStatusMessage("Exporting " + referencedClasses.size() + " referenced classes");
        }

        try {
            Path dbBasePath = getBaseOutputPath(baseOutputPath);

            exportReferencedClasses(referencedClasses, dbBasePath);
            operation.statistics.schemaWarnings.clear();
            operation.statistics.schemaWarnings.addAll(operation.statistics.duplicationDetector.generateDuplicateWarnings());
            operation.statistics.setExportInfo("Referenced", dbBasePath.resolve("Referenced").toString());
            return operation.statistics;

        } catch (Exception e) {
            if (operation.monitor != null) {
                operation.monitor.onExportError("Referenced", e.getMessage());
            }
            throw e;
        }
    }

    public ExportStatistics exportUnreachedObjects(String baseOutputPath, Set<Long> unreachedObjectIds, DOExportMonitor monitor) throws Exception {
        operation.baseOutputPath = baseOutputPath;
        operation.monitor = monitor;
        operation.statistics = new ExportStatistics(operation.monitor);

        Path dbBasePath = getBaseOutputPath(baseOutputPath);
        Path migrationPath = dbBasePath.resolve("_Migration");
        Files.createDirectories(migrationPath);

        Path xmlPath = migrationPath.resolve("Extra" + getOutputFileExtension());
        if (isXMLFormat() && operation.exportedXMLFiles != null) {
            operation.exportedXMLFiles.add(xmlPath.toString());
        }

        if (operation.monitor != null) {
            operation.monitor.onModuleStart("_Migration", unreachedObjectIds != null ? unreachedObjectIds.size() : 0, 0);
        }

        Writer outputWriter = null;
        ClassExportConfig previousExportConfig = operation.exportConfig;
        Set<Long> previousAllowedObjectIds = operation.allowedObjectIds;

        try {
            operation.xsdBuilder = operation.sharedXSDBuilder != null ? operation.sharedXSDBuilder : new XSDBuilder(operation.dbContext);
            if (operation.sharedXSDBuilder == null) {
                operation.xsdBuilder.startExportRoot();
            }

            outputWriter = new FileWriter(xmlPath.toFile());
            operation.xmlWriter = new StructuredWriter(getStructuredWriterAPI(), outputWriter, xmlPath);

            operation.exportNativeIds = shouldExportNativeIdsForCurrentFormat();
            if (!operation.useSharedTracking) {
                operation.exportedObjectIds = new HashSet<>();
            }

            operation.exportConfig = null;
            operation.allowedObjectIds = unreachedObjectIds != null ? new HashSet<>(unreachedObjectIds) : Collections.emptySet();

            ObjectExporter objectExporter = new ObjectExporter(operation, operation.xmlWriter, operation.xsdBuilder);
            objectExporter.reset();

            if (isXMLFormat()) {
                operation.xmlWriter.openRootStructure("export", getSchemaLocationForXml(xmlPath));
            } else {
                operation.xmlWriter.openStructure("export");
            }
            operation.xmlWriter.metadata(getMetadata("_Migration", "Extra", unreachedObjectIds != null ? unreachedObjectIds.size() : 0));
            operation.xmlWriter.openStructure("objects");

            if (unreachedObjectIds != null && !unreachedObjectIds.isEmpty()) {
                List<Long> sortedIds = new ArrayList<>(unreachedObjectIds);
                Collections.sort(sortedIds);
                operation.statistics.setCurrentClass("_Migration.Extra", sortedIds.size());

                for (Long objectId : sortedIds) {
                    if (objectId == null || objectId <= 0) {
                        continue;
                    }
                    if (operation.monitor != null && operation.monitor.isCancelled()) {
                        break;
                    }
                    objectExporter.exportObjectRecursively(operation.container, objectId, 2);
                }
            }

            operation.xmlWriter.closeStructure("objects");
            operation.xmlWriter.closeStructure("export");

            if (outputWriter != null) {
                outputWriter.close();
                outputWriter = null;
            }
            generateHtmlViewerIfNeeded(xmlPath, null);

            if (isXMLFormat() && operation.sharedXSDBuilder == null) {
                Path xsdPath = getComprehensiveSchemaPath(baseOutputPath);
                Files.createDirectories(xsdPath.getParent());
                operation.xsdBuilder.writeXSD(xsdPath.toString());
            }

            operation.statistics.schemaWarnings.clear();
            operation.statistics.schemaWarnings.addAll(operation.statistics.duplicationDetector.generateDuplicateWarnings());
            operation.statistics.setExportInfo("_Migration/Extra", xmlPath.toString());

            if (operation.monitor != null) {
                operation.monitor.onModuleComplete("_Migration");
            }

            return operation.statistics;

        } finally {
            operation.exportConfig = previousExportConfig;
            operation.allowedObjectIds = previousAllowedObjectIds;

            if (outputWriter != null) {
                try {
                    outputWriter.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    /**
     * Counts total number of classes in a module and all its children.
     */
    private int countTotalClasses(MigrationModule module) {
        int count = module.getClassNames().size();
        for (MigrationModule child : module.getChildModules()) {
            count += countTotalClasses(child);
        }
        return count;
    }

    /**
     * Recursively exports a module and its children to folder structure.
     */
    private void exportModuleRecursive(MigrationModule module, Path currentBasePath, int depth) throws Exception {

        if (operation.monitor != null) {
            operation.monitor.onModuleStart(module.getName(), module.getClassConfigs().size(), depth);
        }

        // Create folder for this module (use module name to preserve proper
        // casing)
        Path modulePath = currentBasePath.resolve(module.getName());
        Files.createDirectories(modulePath);

        // Export each class configuration in this module
        // System.out.println("DEBUG exportModuleRecursive: Module '" +
        // module.getName()
        // + "' has "
        // + module.getClassConfigs().size() + " class configs");
        for (ClassExportConfig config : module.getClassConfigs()) {
            if (operation.monitor != null && operation.monitor.isCancelled()) {
                break;
            }

            String className = config.getClassName();
            // System.out.println("DEBUG exportModuleRecursive: Processing class
            // " +
            // className + ", hasCriteria="
            // + config.hasCriteria() + ", criteria count=" +
            // config.getCriteria().size());

            // DEBUG: Log if this class has criteria
            if (config.hasCriteria()) {
                System.out.println("DEBUG: Exporting " + className + " with " + config.getCriteria().size() + " criteria: " + config.getCriteria());
            }

            DOSchemaClass schemaClass = operation.referenceSchema.findClassByName(className);
            if (schemaClass == null) {
                continue; // Skip missing classes silently - errors tracked via
                          // monitor
            }

            DOSchemaClass dbSchemaClass = operation.databaseSchema.findClassByName(className);
            if (dbSchemaClass == null) {
                continue; // Skip missing classes silently - errors tracked via
                          // monitor
            }

            // Use the destination file name from config (defaults to class name
            // if not set)
            String fileName = config.getDestinationFileName() + getOutputFileExtension();
            String xsdFileName = config.getDestinationFileName() + ".xsd";
            Path xmlPath = modulePath.resolve(fileName);
            Path xsdPath = isXMLFormat() ? modulePath.resolve(xsdFileName) : null;

            // Track XML file for validation if using shared tracking
            if (isXMLFormat() && operation.exportedXMLFiles != null) {
                operation.exportedXMLFiles.add(xmlPath.toString());
            }

            // Export this class with criteria filtering
            exportClassToFile(schemaClass, dbSchemaClass, xmlPath, xsdPath, config);
        }

        // Recursively export child modules
        for (MigrationModule childModule : module.getChildModules()) {
            if (operation.monitor != null && operation.monitor.isCancelled()) {
                break;
            }
            exportModuleRecursive(childModule, modulePath, depth + 1);
        }

        if (operation.monitor != null) {
            operation.monitor.onModuleComplete(module.getName());
        }
    }

    /**
     * Exports a single class to the specified file paths.
     * 
     * @param config Optional export configuration with criteria filtering. If null,
     *               exports all objects.
     */
    private void exportClassToFile(DOSchemaClass schemaClass, DOSchemaClass dbSchemaClass, Path xmlPath, Path xsdPath, migration4o.models.ui.ClassExportConfig config) throws Exception {

        // Use shared XSD builder if available, otherwise create a new one
        operation.xsdBuilder = operation.sharedXSDBuilder != null ? operation.sharedXSDBuilder : new XSDBuilder(operation.dbContext);
        if (operation.sharedXSDBuilder == null) {
            operation.xsdBuilder.startExportRoot();
        }

        Writer outputWriter = null;

        try {
            // Create structured writer
            outputWriter = new FileWriter(xmlPath.toFile());
            operation.xmlWriter = new StructuredWriter(getStructuredWriterAPI(), outputWriter, xmlPath);

            // Create export operation
            operation.baseOutputPath = xmlPath.getParent().getParent().toString();
            operation.exportNativeIds = shouldExportNativeIdsForCurrentFormat();
            operation.exportConfig = config;
            if (!operation.useSharedTracking) {
                operation.exportedObjectIds = new HashSet<>();
            }

            // Create object exporter
            ObjectExporter objectExporter = new ObjectExporter(operation, operation.xmlWriter, operation.xsdBuilder);
            objectExporter.reset();

            // Copy the tracker state if we have a tracker
            if (operation.referencedClassTracker != null) {
                for (String className : operation.referencedClassTracker.getReferencedClasses()) {
                    operation.referencedClassTracker.registerReferencedClass(className);
                }
            }
            String module = getModuleNameForXml(xmlPath);

            // Write XML header and metadata
            if (isXMLFormat() && operation.sharedXSDBuilder != null) {
                String relativeSchemaPath = getSchemaLocationForXml(xmlPath);
                operation.xmlWriter.openRootStructure("export", relativeSchemaPath);
                operation.xmlWriter.metadata(schemaClass.getMetadata(module));

            } else if (isXMLFormat()) {
                // Individual XSD - no schema reference needed (pass null for
                // schemaLocation)
                operation.xmlWriter.openRootStructure("export", null);
                operation.xmlWriter.metadata(schemaClass.getMetadata(module));
            } else {
                operation.xmlWriter.openStructure("export");
                operation.xmlWriter.metadata(schemaClass.getMetadata(module));
            }
            operation.xmlWriter.openStructure("objects");

            // Export all objects of this class
            operation.xsdBuilder.addTopLevelObject(dbSchemaClass.destinationName, dbSchemaClass);
            long[] objectIds = dbSchemaClass.objectIds;
            int objectCount = (objectIds != null ? objectIds.length : 0);

            // Apply object limit if set
            int actualCount = objectCount;
            if (operation.maxObjectsPerClass != null && objectCount > operation.maxObjectsPerClass) {
                actualCount = operation.maxObjectsPerClass;
            }

            if (operation.monitor != null) {
                operation.monitor.onClassStart(schemaClass.source, schemaClass.destinationName, actualCount);
            }

            if (objectIds != null) {
                operation.statistics.setCurrentClass(schemaClass.source, actualCount);

                // Export up to maxObjectsPerClass objects that match criteria
                int exportedCount = 0;
                for (long objectId : objectIds) {
                    if (operation.monitor != null && operation.monitor.isCancelled()) {
                        break;
                    }

                    if (operation.maxObjectsPerClass != null && exportedCount >= operation.maxObjectsPerClass) {
                        if (operation.statistics != null) {
                            operation.statistics.recordObjectDecision(objectId, schemaClass.source, "not exported due to class limit maxObjectsPerClass=" + operation.maxObjectsPerClass);
                        }
                        break; // Stop at limit
                    }

                    objectExporter.exportObjectRecursively(operation.container, objectId, 2);
                    exportedCount++;
                }
            }

            // Merge discovered references back to main tracker
            if (operation.referencedClassTracker != null) {
                for (String className : objectExporter.getReferencedClassTracker().getReferencedClasses()) {
                    operation.referencedClassTracker.registerReferencedClass(className);
                }
            }

            // Write XML footer
            operation.xmlWriter.closeStructure("objects");
            operation.xmlWriter.closeStructure("export");

            if (outputWriter != null) {
                outputWriter.close();
                outputWriter = null;
            }
            generateHtmlViewerIfNeeded(xmlPath, schemaClass);

            // Only generate individual XSD if not using shared builder
            if (isXMLFormat() && operation.sharedXSDBuilder == null && xsdPath != null) {
                if (operation.monitor != null) {
                    operation.monitor.onXSDGenerationStart(xsdPath.toString());
                }

                // Generate XSD schema
                operation.xsdBuilder.writeXSD(xsdPath.toString());

                if (operation.monitor != null) {
                    operation.monitor.onXSDGenerationComplete(xsdPath.toString());
                }
            }

            if (operation.monitor != null) {
                int exportedCount = operation.statistics.objectsSucceeded;
                operation.monitor.onClassComplete(schemaClass.source, exportedCount);
            }

        } finally {
            if (outputWriter != null) {
                try {
                    outputWriter.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    private StructuredWriterAPI getStructuredWriterAPI() {
        StructuredWriterAPI configured = StructuredWriterProvider.getFormat(operation.outputFormat);
        if (configured != null) {
            return configured;
        }
        return new StructuredWriterXML();
    }

    private String getOutputFileExtension() {
        String formatName = getStructuredWriterAPI().getName();
        if ("EXCEL".equalsIgnoreCase(formatName)) {
            return ".xlsx";
        }
        if ("JS".equalsIgnoreCase(formatName)) {
            return ".js";
        }
        if ("JSON".equalsIgnoreCase(formatName)) {
            return ".json";
        }
        return ".xml";
    }

    private static String sanitizeModuleName(String moduleName) {
        if (moduleName == null) {
            return "";
        }

        String normalized = moduleName.trim().replace('\\', '/');
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isEmpty()) {
            return "";
        }

        boolean looksAbsolutePath = normalized.startsWith("/") || normalized.matches("^[A-Za-z]:/.*");
        if (!looksAbsolutePath) {
            return normalized;
        }

        String marker = "/output/";
        int outputMarkerIndex = normalized.indexOf(marker);
        if (outputMarkerIndex >= 0) {
            String afterOutput = normalized.substring(outputMarkerIndex + marker.length());
            int dbSeparatorIndex = afterOutput.indexOf('/');
            if (dbSeparatorIndex >= 0 && dbSeparatorIndex + 1 < afterOutput.length()) {
                return afterOutput.substring(dbSeparatorIndex + 1);
            }
        }

        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash + 1 < normalized.length()) {
            return normalized.substring(lastSlash + 1);
        }

        return normalized;
    }

    private String getModuleNameForXml(Path xmlPath) {
        if (xmlPath == null) {
            return "";
        }

        Path parent = xmlPath.getParent();
        if (parent == null) {
            return "";
        }

        try {
            if (operation.baseOutputPath != null && !operation.baseOutputPath.isBlank()) {
                Path dbBasePath = getBaseOutputPath(operation.baseOutputPath);
                if (dbBasePath != null && parent.startsWith(dbBasePath)) {
                    String relativeModule = dbBasePath.relativize(parent).toString().replace('\\', '/');
                    if (!relativeModule.isBlank()) {
                        return sanitizeModuleName(relativeModule);
                    }
                }
            }
        } catch (Exception ignored) {
            // Fallback to path sanitization
        }

        return sanitizeModuleName(parent.toString());
    }

    private void generateHtmlViewerIfNeeded(Path outputPath, DOSchemaClass schemaClass) {
        if (!operation.generateHtmlViewer || outputPath == null) {
            return;
        }

        try {
            if ("JS".equalsIgnoreCase(getStructuredWriterAPI().getName())) {
                String baseHref = computeBaseHref(outputPath);
                JsViewerHtmlGenerator.writeViewerForJs(outputPath, schemaClass, cachedNavJson, baseHref);
            } else {
                if (schemaClass != null) {
                    DOSchema refSchema = migration4o.schema.DOSchemaService.getInstance().getReferenceSchema();
                    XmlViewerHtmlGenerator.writeViewerForXml(outputPath, schemaClass, refSchema);
                } else {
                    XmlViewerHtmlGenerator.writeViewerForXml(outputPath);
                }
            }
        } catch (IOException e) {
            if (operation.monitor != null) {
                operation.monitor.onStatusMessage("Warning: Failed to generate HTML viewer for " + outputPath.getFileName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Recursively registers all modules and their classes with the reference
     * tracker.
     */
    private void registerModuleClasses(MigrationModule module, ReferencedClassTracker tracker) {
        Set<String> classNames = new HashSet<>(module.getClassNames());
        tracker.registerModule(module.getName(), classNames);

        for (MigrationModule childModule : module.getChildModules()) {
            registerModuleClasses(childModule, tracker);
        }
    }

    /**
     * Exports referenced classes that were discovered during export but not in the
     * original request. Creates a "Referenced" module to hold these classes.
     */
    private void exportReferencedClasses(Set<String> referencedClasses, Path basePath) throws Exception {

        if (operation.monitor != null) {
            operation.monitor.onModuleStart("Referenced", referencedClasses.size(), 0);
        }

        // Create "Referenced" module folder
        Path referencedPath = basePath.resolve("Referenced");
        Files.createDirectories(referencedPath);

        for (String className : referencedClasses) {
            if (operation.monitor != null && operation.monitor.isCancelled()) {
                break;
            }

            // Skip if already exported as a referenced class
            if (operation.referencedClassTracker.isReferencedClassExported(className)) {
                continue;
            }

            DOSchemaClass schemaClass = operation.referenceSchema.findClassByName(className);
            if (schemaClass == null) {
                if (operation.monitor != null) {
                    operation.monitor.onStatusMessage("Referenced class not found in schema: " + className);
                }
                continue;
            }

            DOSchemaClass dbSchemaClass = operation.databaseSchema.findClassByName(className);
            if (dbSchemaClass == null) {
                if (operation.monitor != null) {
                    operation.monitor.onStatusMessage("Referenced class not found in database: " + className);
                }
                continue;
            }

            // Generate file name from destination class name
            String fileName = schemaClass.destinationName + getOutputFileExtension();
            String xsdFileName = schemaClass.destinationName + ".xsd";
            Path xmlPath = referencedPath.resolve(fileName);
            Path xsdPath = isXMLFormat() ? referencedPath.resolve(xsdFileName) : null;

            // Export this referenced class (without further reference tracking
            // to avoid
            // infinite loops, and without criteria filtering)
            ReferencedClassTracker previousTracker = operation.referencedClassTracker;
            operation.referencedClassTracker = null;
            try {
                exportClassToFile(schemaClass, dbSchemaClass, xmlPath, xsdPath, null);
            } finally {
                operation.referencedClassTracker = previousTracker;
            }

            // Mark as exported
            operation.referencedClassTracker.markReferencedClassAsExported(className);

            if (operation.monitor != null) {
                operation.monitor.onStatusMessage("Exported referenced class: " + schemaClass.destinationName);
            }
        }

        if (operation.monitor != null) {
            operation.monitor.onModuleComplete("Referenced");
        }
    }

    /**
     * Exports all objects in a module to XML file with custom XSD path.
     */
    public ExportStatistics exportModule(List<String> classNames, String moduleName, String outputPath, String xsdOutputPath) throws Exception {

        String safeModuleName = sanitizeModuleName(moduleName);

        // Initialize components
        operation.classNames = classNames;
        operation.monitor = null;
        operation.statistics = new ExportStatistics();
        operation.xsdBuilder = new XSDBuilder(operation.dbContext);
        operation.xsdBuilder.startExportRoot();
        operation.referencedClassTracker = null;

        Writer outputWriter = null;

        try {
            // Use the shared in-memory container (already opened by
            // DODatabaseService)
            // No need to open database here - saves time and memory

            // Create structured writer
            outputWriter = new FileWriter(outputPath);
            operation.xmlWriter = new StructuredWriter(getStructuredWriterAPI(), outputWriter, Paths.get(outputPath));

            // Create export operation
            operation.baseOutputPath = outputPath;
            operation.exportNativeIds = shouldExportNativeIdsForCurrentFormat();
            if (!operation.useSharedTracking) {
                operation.exportedObjectIds = new HashSet<>();
            }

            // Create object exporter
            ObjectExporter objectExporter = new ObjectExporter(operation, operation.xmlWriter, operation.xsdBuilder);
            objectExporter.reset();

            // Write XML header and metadata
            // xmlWriter.writeExportHeader(moduleName, "module",
            // classNames.size(), null);
            if (isXMLFormat()) {
                operation.xmlWriter.openRootStructure("export", null);
            } else {
                operation.xmlWriter.openStructure("export");
            }
            operation.xmlWriter.metadata(getMetadata(safeModuleName, null, classNames.size()));
            operation.xmlWriter.openStructure("objects");

            // Export all classes in the module
            for (String className : classNames) {
                DOSchemaClass dbSchemaClass = operation.databaseSchema.findClassByName(className);
                if (dbSchemaClass != null) {
                    operation.xsdBuilder.addTopLevelObject(dbSchemaClass.destinationName, dbSchemaClass);
                    long[] objectIds = dbSchemaClass.objectIds;

                    if (objectIds != null) {
                        for (long objectId : objectIds) {
                            objectExporter.exportObjectRecursively(operation.container, objectId, 2);
                        }
                    }
                }
            }

            // Write XML footer
            operation.xmlWriter.closeStructure("objects");
            operation.xmlWriter.closeStructure("export");

            if (outputWriter != null) {
                outputWriter.close();
                outputWriter = null;
            }
            generateHtmlViewerIfNeeded(Paths.get(outputPath), null);

            // Generate XSD schema
            if (isXMLFormat()) {
                String xsdPath = xsdOutputPath;
                if (xsdPath == null) {
                    xsdPath = outputPath.replace(".xml", ".xsd");
                }
                operation.xsdBuilder.writeXSD(xsdPath);
                System.out.println("Generated XSD schema: " + xsdPath);
            }

            // Generate duplicate warnings and set export info
            operation.statistics.schemaWarnings.clear();
            operation.statistics.schemaWarnings.addAll(operation.statistics.duplicationDetector.generateDuplicateWarnings());
            operation.statistics.setExportInfo(safeModuleName, outputPath);

            return operation.statistics;

        } finally {
            // Note: We do NOT close the container here as it's a shared
            // resource managed by
            // DODatabaseService
            if (outputWriter != null) {
                try {
                    outputWriter.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    public static StructuredWriterMetadata getMetadata(String module, String type, int objects) {
        StructuredWriterMetadata metadata = new StructuredWriterMetadata();
        metadata.generator = "Migration4o";
        metadata.provider = "Gestion Technologies";
        metadata.module = sanitizeModuleName(module);
        metadata.type = type;
        metadata.objects = objects >= 0 ? String.valueOf(objects) : "";
        metadata.date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        return metadata;
    }

}
