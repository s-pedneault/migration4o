package migration4o.migration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.db4o.ext.ExtObjectContainer;

import migration4o.util.tools.structuredwriter.StructuredWriter;

import migration4o.migration.monitoring.ExportStatistics;
import migration4o.migration.monitoring.ReferencedClassTracker;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaField;
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
    public List<MigrationModule> modules;
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
    public ObjectExporter objectExporter; // Set after ObjectExporter construction

    public ArrayList<DOSchemaField> availableSkipUserOptions;
    public ArrayList<DOSchemaField> selectedSkipUserOptions;
}
