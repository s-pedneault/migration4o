package migration4o.migration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import migration4o.migration.monitoring.ExportStatistics;
import migration4o.migration.monitoring.ReferencedClassTracker;
import migration4o.models.schema.DOSchema;
import migration4o.models.ui.ClassExportConfig;
import migration4o.models.ui.MigrationModule;
import migration4o.ui.common.DOExportMonitor;

/**
 * Encapsulates all context and configuration for an export operation.
 */
public class ExportOperation {

    // Schemas and database context
    public DOSchema referenceSchema;
    public DOSchema databaseSchema;
    public String databasePath;

    // Output configuration
    public String baseOutputPath;
    public DOExportMonitor monitor;

    // Export limits and options
    public Integer maxObjectsPerClass;
    public boolean exportNativeIds = false;

    // Module/Class targets (use lists for consistency)
    public List<MigrationModule> modules;
    public List<String> classNames;

    // Export configuration flags
    public boolean saveToHistory = true;
    public boolean useSharedTracking = false;

    // Shared export state (across multiple ObjectExporter instances)
    public Set<Long> exportedObjectIds = new HashSet<>();
    public ExportStatistics statistics;
    public ClassExportConfig exportConfig;
    public ReferencedClassTracker referencedClassTracker;
    public ObjectExporter objectExporter; // Set after ObjectExporter construction
}
