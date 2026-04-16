package migration4o.migration;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import migration4o.database.DODatabase;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.ui.SeedQuery;
import migration4o.ui.common.DOExportMonitor;

/**
 * Immutable-by-convention configuration bag for an export run.
 * <p>
 * All fields are "set once before the export starts" — they represent what the
 * caller wants to do, not what is happening right now. Runtime-accumulated
 * state (statistics, caches, object-exporter instance, nav tree, …) lives on
 * {@link migration4o.migration.format.ExportCurrentState}.
 */
public class ExportRequest {

    // ── Schemas and database context
    // ──────────────────────────────────────────

    public DOSchema referenceSchema;
    public DODatabase database;
    /** @deprecated Use {@link #database} instead. Kept temporarily for coexistence. */
    @Deprecated
    public DOSchema databaseSchema;
    public String databasePath;
    public migration4o.database.DODatabaseContext dbContext;

    // ── Output configuration
    // ──────────────────────────────────────────────────

    public String baseOutputPath;
    /**
     * Explicit output branch folder name (e.g. "all", "max50", "custom"). When
     * set, overrides the computed {@link #getMaxObjectsFolder()} value.
     */
    public String outputBranch;
    public DOExportMonitor monitor;
    public List<String> outputOptions = new ArrayList<>(List.of("XML + XSD"));

    // ── Export limits and options
    // ─────────────────────────────────────────────

    public Integer maxObjectsPerClass;
    public boolean exportNativeIds = false;

    /**
     * Pre-computed object-ID selections per class (keyed by source class name).
     * When non-null, {@code ObjectExportLoop} iterates these IDs instead of
     * {@code dbSchemaClass.objectIds}. Populated by
     * {@code ExportSelectionAdvisor} before the export loop when a cap is
     * active.
     */
    public Map<String, long[]> preselectedObjectIds;

    /**
     * Number of "required" (closure-driven) objects at the front of each
     * preselected array. These are exported unconditionally — the cap check is
     * skipped for them. Objects after this index are optional fill. Populated
     * alongside {@code preselectedObjectIds} by {@code ExportSelectionAdvisor}.
     */
    public Map<String, Integer> preselectedRequiredCounts;

    public boolean applyUserSelectedFieldExclusions = true;
    public boolean applySkipWhenConditions = true;
    public boolean applyExportCriteriaFilters = true;
    public boolean skipObjectsWithoutExportableFields = true;

    // ── Module / class targets
    // ────────────────────────────────────────────────

    public List<String> classNames;

    // ── Export flags
    // ──────────────────────────────────────────────────────────

    public boolean saveToHistory = true;
    public boolean useSharedTracking = false;
    /**
     * When {@code false}, expensive per-object ID sets and diagnostic
     * relationship notes are not collected, speeding up large exports.
     */
    public boolean fullTracking = true;
    /**
     * {@code true} when this request is one sub-export in a
     * {@link OrganizationExportMode#SEPARATE_PER_ORGANIZATION} run.
     * Used by format handlers to vary presentation (e.g. org tile colour).
     */
    public boolean separatePerOrgSubExport = false;

    /**
     * When {@code true}, {@link migration4o.migration.format.XmlFormatHandler} skips
     * the Extra.xml unreached-objects pass in its {@code done()} hook.
     * Set on per-org requests in SEPARATE_PER_ORGANIZATION mode so that
     * Extra.xml is generated only once — after all org exports — via
     * {@link migration4o.migration.MigrationExportService#exportExtraXml}.
     */
    public boolean skipExtraXml = false;

    // ── Skip options
    // ──────────────────────────────────────────────────────────

    public ArrayList<DOSchemaField> availableSkipUserOptions;
    public ArrayList<DOSchemaField> selectedSkipUserOptions;

    // ── Seed queries
    // ──────────────────────────────────────────────────────────

    /**
     * Seed queries for seed-based selection mode. When non-empty, the export
     * engine uses seed-based selection instead of cap-based selection.
     */
    public List<SeedQuery> seedQueries = new ArrayList<>();

    // ── Export language
    // ──────────────────────────────────────────────────────────

    /**
     * Language code for the export (e.g. {@code "fr"} or {@code "en"}).
     * Controls locale-aware date formatting in summaries and sets the default
     * viewer language.
     */
    public String exportLanguage = "fr";

    // ── Files destination
    // ──────────────────────────────────────────────────────────

    /**
     * Controls where exported data files are placed (folder hierarchy vs. embedded).
     */
    public FilesDestination filesDestination = FilesDestination.FOLDER;

    // ── Path helpers
    // ────────────────────────────────────────────────────────

    /**
     * Extracts the database folder name from the database path. For example:
     * "local/54060/BackupManuel.dat" → "54060"
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
    private String getMaxObjectsFolder() {
        return maxObjectsPerClass != null ? "max" + maxObjectsPerClass : "all";
    }

    // ── Organization export
    // ──────────────────────────────────────────────────

    /**
     * When non-null, restricts the export to the organizations described by this
     * config. Null means "no organization filtering" (export everything).
     */
    public OrganizationExportConfig organizationConfig;

    /**
     * Gets the base output directory for the current database, including the
     * max-objects sub-folder. Returns:
     * output/&lt;database-folder&gt;/&lt;max-objects-folder&gt;/
     */
    public Path getBaseOutputPath(String baseOutputDir) {
        String branch = (outputBranch != null && !outputBranch.isBlank()) ? outputBranch : getMaxObjectsFolder();
        return Paths.get(baseOutputDir).resolve(getDatabaseFolderName()).resolve(branch);
    }

    /**
     * Creates a copy of this request scoped to a single organization, for use
     * during separate-per-organization export.
     * <p>
     * All fields are copied from the original. {@code organizationConfig} is
     * replaced with a single-org {@link OrganizationExportMode#SINGLE_EXPORT}
     * config, and {@code outputBranch} is set to the given value.
     */
    public ExportRequest withOrganizationScope(OrganizationInfo org, String perOrgOutputBranch, boolean includeGeneralData) {
        ExportRequest copy = new ExportRequest();
        copy.referenceSchema = this.referenceSchema;
        copy.database = this.database;
        copy.databaseSchema = this.databaseSchema;
        copy.databasePath = this.databasePath;
        copy.dbContext = this.dbContext;
        copy.baseOutputPath = this.baseOutputPath;
        copy.outputBranch = perOrgOutputBranch;
        copy.monitor = this.monitor;
        copy.outputOptions = new ArrayList<>(this.outputOptions);
        copy.maxObjectsPerClass = this.maxObjectsPerClass;
        copy.exportNativeIds = this.exportNativeIds;
        copy.preselectedObjectIds = this.preselectedObjectIds;
        copy.preselectedRequiredCounts = this.preselectedRequiredCounts;
        copy.applyUserSelectedFieldExclusions = this.applyUserSelectedFieldExclusions;
        copy.applySkipWhenConditions = this.applySkipWhenConditions;
        copy.applyExportCriteriaFilters = this.applyExportCriteriaFilters;
        copy.skipObjectsWithoutExportableFields = this.skipObjectsWithoutExportableFields;
        copy.classNames = this.classNames;
        copy.saveToHistory = this.saveToHistory;
        copy.useSharedTracking = this.useSharedTracking;
        copy.fullTracking = this.fullTracking;
        copy.availableSkipUserOptions = this.availableSkipUserOptions;
        copy.selectedSkipUserOptions = this.selectedSkipUserOptions != null ? new ArrayList<>(this.selectedSkipUserOptions) : null;
        copy.seedQueries = this.seedQueries != null ? new ArrayList<>(this.seedQueries) : new ArrayList<>();
        copy.exportLanguage = this.exportLanguage;
        copy.filesDestination = this.filesDestination;
        copy.organizationConfig = new OrganizationExportConfig(OrganizationExportMode.SINGLE_EXPORT, List.of(org), includeGeneralData);
        copy.separatePerOrgSubExport = true;
        copy.skipExtraXml = this.skipExtraXml;
        return copy;
    }
}
