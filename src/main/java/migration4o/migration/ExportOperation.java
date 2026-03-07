package migration4o.migration;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import migration4o.util.tools.structuredwriter.StructuredWriterAPI;
import migration4o.util.tools.structuredwriter.StructuredWriterProvider;
import migration4o.util.tools.structuredwriter.formats.StructuredWriterXML;

import com.db4o.ext.ExtObjectContainer;

import migration4o.migration.monitoring.ExportStatistics;
import migration4o.migration.monitoring.ReferencedClassTracker;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.ui.ClassExportConfig;
import migration4o.models.schema.DOSchemaModule;
import migration4o.ui.common.DOExportMonitor;
import migration4o.util.tools.structuredwriter.StructuredWriter;

/**
 * Encapsulates all context and configuration for an export operation.
 */
public class ExportOperation {

    // Schemas and database context
    public DOSchema referenceSchema;
    public DOSchema databaseSchema;
    public String databasePath;
    public migration4o.database.DODatabaseContext dbContext;
    public ExtObjectContainer container;

    // Output configuration
    public String baseOutputPath;
    public DOExportMonitor monitor;
    public String outputFormat = "XML";
    public List<String> outputOptions = new ArrayList<>(List.of("XML + XSD"));
    public boolean generateHtmlViewer = false;

    // Export limits and options
    public Integer maxObjectsPerClass;
    public boolean exportNativeIds = false;
    public boolean applyUserSelectedFieldExclusions = true;
    public boolean applySkipWhenConditions = true;
    public boolean applyExportCriteriaFilters = true;
    public boolean skipObjectsWithoutExportableFields = true;

    // Module/Class targets (use lists for consistency)
    // public List<MigrationModule> modules;
    public List<String> classNames;

    // Export configuration flags
    public boolean saveToHistory = true;
    public boolean useSharedTracking = false;

    // Shared export state (across multiple ObjectExporter instances)
    public Set<Long> exportedObjectIds = new HashSet<>();
    public Set<Long> allowedObjectIds;
    public XSDBuilder sharedXSDBuilder;
    public Set<String> exportedXMLFiles;
    public StructuredWriter xmlWriter;
    public XSDBuilder xsdBuilder;
    public ExportStatistics statistics;
    public ClassExportConfig exportConfig;
    public ReferencedClassTracker referencedClassTracker;
    public ObjectExporter objectExporter; // Set after ObjectExporter
                                          // construction

    public ArrayList<DOSchemaField> availableSkipUserOptions;
    public ArrayList<DOSchemaField> selectedSkipUserOptions;

    // ── Module stack tracking the current branch being exported
    // ───────────────
    /**
     * Stack of modules currently being exported. The top of the stack is the
     * innermost active module. Push on entry to exportModuleRecursive, pop on
     * exit. {@code size()} gives the current nesting depth.
     */
    public Deque<DOSchemaModule> moduleStack = new ArrayDeque<>();

    // ── IDEntite label resolution caches (JS export only) ────────────────────
    /**
     * Maps a composite key {@code "<mID>:<expectedType>"} to the resolved
     * target entity's DB4O object ID. Populated lazily; avoids repeating the
     * O(n) mID scan when multiple fields reference the same entity type with
     * the same mID.
     */
    public Map<String, Long> idEntiteTargetCache = new HashMap<>();
    /**
     * Maps a resolved target entity's DB4O object ID to its generated
     * human-readable summary label. Populated lazily; avoids regenerating the
     * same summary when the same entity is referenced from multiple records.
     */
    public Map<Long, String> idEntiteSummaryCache = new HashMap<>();

    // ── Nav tree (built once before HTML viewer export)
    // ───────────────────────
    /** Top-level nav tree — same for all files in this export. */
    public final List<NavNode> navTree = new ArrayList<>();
    /**
     * Nav JSON serialized once from navTree; injected verbatim into every HTML
     * file.
     */
    public String cachedNavJson = "[]";

    // ── Format helpers
    // ─────────────────────────────────────────────────────────

    public StructuredWriterAPI getStructuredWriterAPI() {
        StructuredWriterAPI configured = StructuredWriterProvider.getFormat(outputFormat);
        if (configured != null) {
            return configured;
        }
        return new StructuredWriterXML();
    }

    public String getOutputFileExtension() {
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

    public boolean isXMLFormat() {
        return "XML".equalsIgnoreCase(getStructuredWriterAPI().getName());
    }

    public boolean shouldExportNativeIdsForCurrentFormat() {
        if ("EXCEL".equalsIgnoreCase(getStructuredWriterAPI().getName())) {
            return true;
        }
        return exportNativeIds;
    }

    /**
     * Extracts the database folder name from the database path. For example:
     * "local/54060/BackupManuel.dat" -> "54060"
     */
    public String getDatabaseFolderName() {
        if (databasePath == null) {
            return "default";
        }
        Path path = Paths.get(databasePath);
        Path parent = path.getParent();
        if (parent != null) {
            return parent.getFileName().toString();
        }
        return "default";
    }

    /**
     * Gets the base output directory for the current database. Returns:
     * output/&lt;database-folder&gt;/
     */
    public Path getBaseOutputPath(String baseOutputDir) {
        return Paths.get(baseOutputDir).resolve(getDatabaseFolderName());
    }

    // ── Shared tracking lifecycle
    // ─────────────────────────────────────────────

    /**
     * Initializes shared object tracking and XSD builder for multi-module
     * exports. Call this before exporting multiple modules to ensure objects
     * are only counted once and a single comprehensive XSD is generated.
     */
    public void initializeSharedTracking() {
        exportedObjectIds = new HashSet<>();
        useSharedTracking = true;
        sharedXSDBuilder = new XSDBuilder(dbContext);
        sharedXSDBuilder.startExportRoot();
        exportedXMLFiles = new HashSet<>();
    }

    /**
     * Resets shared object tracking and XSD builder. Call this to clear
     * tracking state between different export sessions.
     */
    public void resetSharedTracking() {
        useSharedTracking = false;
        exportedObjectIds = new HashSet<>();
        sharedXSDBuilder = null;
        exportedXMLFiles = null;
    }
}
