package migration4o.migration.format;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import migration4o.database.DODatabaseDelegate;
import migration4o.migration.ExportRequest;
import migration4o.migration.NavNode;
import migration4o.migration.ObjectExporter;
import migration4o.migration.OrganizationFilter;
import migration4o.migration.OrganizationInfo;
import migration4o.util.MunicipalityInfo;
import migration4o.migration.monitoring.ExportStatistics;
import migration4o.migration.tasks.ModulePathUtil;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.schema.DOSchemaModule;
import migration4o.models.ui.ClassExportConfig;

/**
 * Mutable state tracking the current position in the export tree (module → class → object → field), plus all runtime-accumulated shared state (statistics, caches, nav tree, …).
 * <p>
 * Immutable configuration (schemas, output paths, flags) lives on {@link ExportRequest}, accessible as {@link #request}.
 * <p>
 * All fields are public. Computed methods derive values from the current state without external parameters.
 */
public class ExportCurrentState {

    // ── Immutable configuration ──────────────────────────────────────────────

    /** Immutable export configuration. Set in constructor; never null. */
    public final ExportRequest request;

    // ── Set once before export starts ────────────────────────────────────────

    /** Active delegate for the class currently being exported. Set by {@code ObjectExportLoop}. */
    public DODatabaseDelegate delegate;

    /** Root output directory for this database's export. */
    public Path basePath;

    // ── Module level ─────────────────────────────────────────────────────────

    /** Module chain: bottom = outermost, top (last pushed) = current. */
    public final Deque<DOSchemaModule> moduleChain = new ArrayDeque<>();

    // ── Class level ──────────────────────────────────────────────────────────

    /** Schema class currently being exported; null between classes. */
    public DOSchemaClass schemaClass;

    /** Export config for the current class; null for referenced classes. */
    public ClassExportConfig exportConfig;

    /** Number of database objects for the current class (0 when absent from DB). */
    public int classObjectCount;

    // ── Object level ─────────────────────────────────────────────────────────

    /** Object stack: last = current, previous = parent. */
    public final List<ObjectFrame> objectChain = new ArrayList<>();

    /**
     * When non-null, only objects whose IDs are in this set are exported. Used by {@code XmlFormatHandler.done()} for the unreached-objects pass. Null means "allow all".
     */
    public Set<Long> allowedObjectIds;

    // ── Field level ──────────────────────────────────────────────────────────

    /** Schema field currently being exported; null between fields. */
    public DOSchemaField field;

    /** Value of the current field; null between fields. */
    public Object fieldValue;

    // ── Format-neutral shared state ──────────────────────────────────────────

    /** Shared statistics across all format handlers. */
    public ExportStatistics statistics;

    // ── HTML viewer / nav tree
    // ────────────────────────────────────────────────

    /**
     * Modules being exported — set by {@code MigrationExportService} so that {@code HtmlFormatHandler.init()} can build the nav tree.
     */
    public List<DOSchemaModule> exportModules;

    /** Whether the HTML viewer files should be generated for this export. */
    public boolean generateHtmlViewer = false;

    /** Top-level nav tree — same for all files in this export. */
    public final List<NavNode> navTree = new ArrayList<>();

    /**
     * Nav JSON serialized once from {@link #navTree}; injected verbatim into every HTML file.
     */
    public String cachedNavJson = "[]";

    /** Total number of exported modules (including nested sub-modules). */
    public int htmlWelcomeModuleCount;

    /** Total number of exported class data files. */
    public int htmlWelcomeClassCount;

    // ── IDEntite label-resolution caches ─────────────────────────────────────

    /**
     * Maps a composite key {@code "<mID>:<expectedType>"} to the resolved target entity's DB4O object ID. Populated lazily; avoids repeating the O(n) mID scan when multiple fields reference the same entity type with the same mID.
     */
    public Map<String, Long> idEntiteTargetCache = new HashMap<>();

    /**
     * Maps a resolved target entity's DB4O object ID to its generated human-readable summary label. Populated lazily; avoids regenerating the same summary when the same entity is referenced from multiple records.
     */
    public Map<Long, String> idEntiteSummaryCache = new HashMap<>();

    // ── Organization filtering ────────────────────────────────────────────────

    /**
     * Filters root objects by their {@code mIDSSI} value. Null means no
     * organization filtering is active.
     */
    public OrganizationFilter organizationFilter;

    /**
     * The organization currently being exported. Non-null only in a
     * {@link migration4o.migration.OrganizationExportMode#SINGLE_EXPORT}
     * per-organization run; null for combined or unfiltered exports.
     */
    public OrganizationInfo currentOrganization;

    /**
     * Municipality info resolved from {@code municipalities.csv} during HTML
     * format init. Null if no matching entry is found or the CSV is unavailable.
     */
    public MunicipalityInfo municipality;

    // ── Active ObjectExporter
    // ─────────────────────────────────────────────────

    /**
     * The {@link ObjectExporter} instance currently driving the export. Set by {@code ObjectExporter}'s constructor so that {@code FieldExporter} can call back into it for embedded references.
     */
    public ObjectExporter objectExporter;

    public Vector<String> previousWarnings = new Vector<>();

    // ── Constructor ──────────────────────────────────────────────────────────

    public ExportCurrentState(ExportRequest request) {
        this.request = request;
    }

    // ── ObjectFrame ──────────────────────────────────────────────────────────

    /** Captures a single object's db4o instance and its numeric ID. */
    public static final class ObjectFrame {
        public final Object obj;
        public final long objectId;

        public ObjectFrame(Object obj, long objectId) {
            this.obj = obj;
            this.objectId = objectId;
        }
    }

    // ── Push / pop / set / clear ─────────────────────────────────────────────

    public void pushModule(DOSchemaModule module) {
        moduleChain.addLast(module);
    }

    public void popModule() {
        moduleChain.removeLast();
    }

    public void setClass(DOSchemaClass schemaClass, ClassExportConfig config) {
        this.schemaClass = schemaClass;
        this.exportConfig = config;
    }

    public void clearClass() {
        this.schemaClass = null;
        this.exportConfig = null;
        this.classObjectCount = 0;
    }

    public void pushObject(Object obj, long objectId) {
        objectChain.add(new ObjectFrame(obj, objectId));
    }

    public void popObject() {
        objectChain.remove(objectChain.size() - 1);
    }

    public void setField(DOSchemaField field, Object value) {
        this.field = field;
        this.fieldValue = value;
    }

    public void clearField() {
        this.field = null;
        this.fieldValue = null;
    }

    // ── Computed methods ─────────────────────────────────────────────────────

    /**
     * Absolute path to the directory for the current module chain, derived from {@code basePath} and each module's ID bottom-to-top. Does <em>not</em> include any per-format sub-folder — use {@link #modulePath(String)} or {@link #moduleRelativePath()} for format-specific paths.
     */
    public Path modulePath() {
        Path path = basePath;
        for (DOSchemaModule m : moduleChain) {
            path = path.resolve(ModulePathUtil.moduleId(m));
        }
        return path;
    }

    /**
     * Returns the module chain as a relative path (no {@code basePath} prefix). Used by the export engine to build per-format file paths: {@code basePath / formatFolder / moduleRelativePath / fileName}.
     */
    public java.nio.file.Path moduleRelativePath() {
        java.nio.file.Path path = java.nio.file.Paths.get("");
        for (DOSchemaModule m : moduleChain) {
            path = path.resolve(ModulePathUtil.moduleId(m));
        }
        return path;
    }

    /**
     * Human-readable display name for the current module chain, e.g. {@code "Parent/Child"}.
     */
    public String moduleDisplayName() {
        StringBuilder sb = new StringBuilder();
        for (DOSchemaModule m : moduleChain) {
            if (sb.length() > 0)
                sb.append('/');
            sb.append(m.name != null ? m.name : "");
        }
        return sb.toString();
    }

    /** {@code true} when the current object is a top-level (root) object. */
    public boolean isRootObject() {
        return objectChain.size() == 1;
    }

    /** Returns the {@link ObjectFrame} for the innermost (current) object. */
    public ObjectFrame currentObject() {
        return objectChain.get(objectChain.size() - 1);
    }

    /**
     * Returns the DB4O object ID of the parent object, or {@code null} when the current object is a root object.
     */
    public Long parentObjectId() {
        int size = objectChain.size();
        return size < 2 ? null : objectChain.get(size - 2).objectId;
    }
}
