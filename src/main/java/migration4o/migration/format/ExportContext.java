package migration4o.migration.format;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

import migration4o.migration.ExportOperation;
import migration4o.migration.monitoring.ExportStatistics;
import migration4o.migration.monitoring.ReferencedClassTracker;
import migration4o.migration.tasks.ModulePathUtil;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.schema.DOSchemaModule;
import migration4o.models.ui.ClassExportConfig;

/**
 * Mutable state tracking the current position in the export tree
 * (module → class → object → field), plus all format-neutral shared state.
 * <p>
 * All fields are public. Computed methods derive values from the current state
 * without external parameters.
 */
public class ExportContext {

    // ── Immutable configuration ──────────────────────────────────────────────

    /** Immutable operation configuration. Set in constructor. */
    public final ExportOperation operation;

    // ── Set once before export starts ────────────────────────────────────────

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

    // ── Object level ─────────────────────────────────────────────────────────

    /** Object stack: last = current, previous = parent. */
    public final List<ObjectFrame> objectChain = new ArrayList<>();

    /**
     * When non-null, only objects whose IDs are in this set are exported.
     * Used by {@code XmlFormatHandler.done()} for the unreached-objects pass.
     * Null means "allow all".
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

    /**
     * Shared reference tracker. Temporarily set to {@code null} during the
     * referenced-class export pass to prevent infinite recursion.
     */
    public ReferencedClassTracker referencedClassTracker;

    // ── Constructor ──────────────────────────────────────────────────────────

    public ExportContext(ExportOperation operation) {
        this.operation = operation;
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
     * Absolute path to the directory for the current module chain,
     * derived from {@code basePath} and each module's ID bottom-to-top.
     */
    public Path modulePath() {
        Path path = basePath;
        for (DOSchemaModule m : moduleChain) {
            path = path.resolve(ModulePathUtil.moduleId(m));
        }
        return path;
    }

    /**
     * Human-readable display name for the current module chain, e.g.
     * {@code "Parent/Child"}.
     */
    public String moduleDisplayName() {
        StringBuilder sb = new StringBuilder();
        for (DOSchemaModule m : moduleChain) {
            if (sb.length() > 0) sb.append('/');
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
     * Returns the DB4O object ID of the parent object, or {@code null} when
     * the current object is a root object.
     */
    public Long parentObjectId() {
        int size = objectChain.size();
        return size < 2 ? null : objectChain.get(size - 2).objectId;
    }
}
