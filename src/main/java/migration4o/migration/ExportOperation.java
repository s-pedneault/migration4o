package migration4o.migration;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.db4o.ext.ExtObjectContainer;

import migration4o.migration.monitoring.ExportStatistics;
import migration4o.migration.monitoring.ReferencedClassTracker;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.ui.ClassExportConfig;
import migration4o.models.ui.SeedQuery;
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
    /** Explicit output branch folder name (e.g. "all", "max50", "custom").
     *  When set, overrides the computed {@link #getMaxObjectsFolder()} value. */
    public String outputBranch;
    public DOExportMonitor monitor;
    public String outputFormat = "XML";
    public List<String> outputOptions = new ArrayList<>(List.of("XML + XSD"));
    public boolean generateHtmlViewer = false;

    // Export limits and options
    public Integer maxObjectsPerClass;
    public boolean exportNativeIds = false;
    /**
     * Pre-computed object-ID selections per class (keyed by source class name).
     * When non-null, {@code ObjectExportLoop} iterates these IDs instead of
     * {@code dbSchemaClass.objectIds}. Populated by {@code ExportSelectionAdvisor}
     * before the export loop when a cap is active.
     */
    public java.util.Map<String, long[]> preselectedObjectIds;
    /**
     * Number of "required" (closure-driven) objects at the front of each
     * preselected array.  These are exported unconditionally — the cap check
     * is skipped for them.  Objects after this index are optional fill.
     * Populated alongside {@code preselectedObjectIds} by
     * {@code ExportSelectionAdvisor}.
     */
    public java.util.Map<String, Integer> preselectedRequiredCounts;
    public boolean applyUserSelectedFieldExclusions = true;
    public boolean applySkipWhenConditions = true;
    public boolean applyExportCriteriaFilters = true;
    public boolean skipObjectsWithoutExportableFields = true;

    // Module/Class targets (use lists for consistency)
    // public List<MigrationModule> modules;
    public List<String> classNames;

    // Modules being exported — set by MigrationExportService so format handlers
    // (e.g. HtmlFormatHandler) can build the nav tree in their init() hook.
    public List<DOSchemaModule> exportModules;
    public List<String> exportModulePaths;

    // Export configuration flags
    public boolean saveToHistory = true;
    public boolean useSharedTracking = false;
    /** When {@code false}, expensive per-object ID sets and diagnostic
     * relationship notes are not collected, speeding up large exports. */
    public boolean fullTracking = true;

    // Shared export state (across multiple ObjectExporter instances)
    public Set<Long> exportedObjectIds = new HashSet<>();
    public Set<Long> allowedObjectIds;
    public StructuredWriter xmlWriter;
    public XSDBuilder xsdBuilder;
    public ExportStatistics statistics;
    public ClassExportConfig exportConfig;
    public ReferencedClassTracker referencedClassTracker;
    public ObjectExporter objectExporter; // Set after ObjectExporter
                                          // construction

    public ArrayList<DOSchemaField> availableSkipUserOptions;
    public ArrayList<DOSchemaField> selectedSkipUserOptions;

    /** Seed queries for seed-based selection mode. When non-empty, the export
     *  engine uses seed-based selection instead of cap-based selection. */
    public List<SeedQuery> seedQueries = new ArrayList<>();

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

    // ── Welcome page stats (set by NavTreeBuilder.build()) ────────────────────
    /** Total number of exported modules (including nested sub-modules). */
    public int htmlWelcomeModuleCount;
    /** Total number of exported class data files. */
    public int htmlWelcomeClassCount;

    // ── Format helpers
    // ─────────────────────────────────────────────────────────

    public String getOutputFileExtension() {
        if ("EXCEL".equalsIgnoreCase(outputFormat))
            return ".xlsx";
        if ("JS".equalsIgnoreCase(outputFormat))
            return ".js";
        if ("JSON".equalsIgnoreCase(outputFormat))
            return ".json";
        return ".xml";
    }

    public boolean isXMLFormat() {
        return "XML".equalsIgnoreCase(outputFormat);
    }

    public boolean shouldExportNativeIdsForCurrentFormat() {
        return "EXCEL".equalsIgnoreCase(outputFormat) || exportNativeIds;
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
     * Returns the max-objects sub-folder name for the current export:
     * {@code "all"} when no limit is set, {@code "maxN"} otherwise.
     */
    public String getMaxObjectsFolder() {
        return maxObjectsPerClass != null ? "max" + maxObjectsPerClass : "all";
    }

    /**
     * Gets the base output directory for the current database, including the
     * max-objects sub-folder. Returns:
     * output/&lt;database-folder&gt;/&lt;max-objects-folder&gt;/
     */
    public Path getBaseOutputPath(String baseOutputDir) {
        String branch = (outputBranch != null && !outputBranch.isBlank()) ? outputBranch : getMaxObjectsFolder();
        return Paths.get(baseOutputDir).resolve(getDatabaseFolderName()).resolve(branch);
    }

}
