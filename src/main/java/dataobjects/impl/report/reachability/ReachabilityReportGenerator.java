package dataobjects.impl.report.reachability;

import dataobjects.impl.engine.DOEngine;
import dataobjects.impl.report.reachability.ReachabilityReportGenerator;
import dataobjects.impl.models.schema.DOSchema;
import dataobjects.impl.models.schema.DOSchemaModule;
import dataobjects.impl.models.schema.DOSchemaClass;
import dataobjects.impl.models.database.DODatabaseClass;
import dataobjects.impl.models.database.DODatabaseObject;
import dataobjects.impl.models.database.DOObjectReference;
import dataobjects.impl.models.database.DOCollectionReference;
import dataobjects.impl.models.DOField;
import dataobjects.impl.report.reachability.html.HTMLWriter;
import dataobjects.impl.report.reachability.html.CSSStylesWriter;
import dataobjects.impl.report.reachability.html.JavaScriptWriter;
import dataobjects.impl.report.reachability.data.SchemaAnalyzer;
import dataobjects.impl.report.reachability.data.DatabaseAnalyzer;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enhanced reachability report with executive summary and tab-based sections
 * for reached/unreached objects and diagnostics
 */
public class ReachabilityReportGenerator {

    private DOEngine engine;
    private BufferedWriter writer;
    private HTMLWriter htmlWriter;
    private CSSStylesWriter cssWriter;
    private JavaScriptWriter jsWriter;
    private SchemaAnalyzer schemaAnalyzer;
    private DatabaseAnalyzer databaseAnalyzer;

    public void generateDefaultReport(DOEngine engine) throws IOException {
        generateReport(engine, "output/Reachability Analysis.html");
    }

    public void generateReport(DOEngine engine, String filePath) throws IOException {
        this.engine = engine;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            this.writer = writer;

            // Initialize components
            initializeComponents();

            // Generate the report
            generateCompleteReport();

        } catch (IOException e) {
            throw new IOException("Failed to generate reachability report: " + e.getMessage(), e);
        }
    }

    private void initializeComponents() {
        htmlWriter = new HTMLWriter(writer);
        cssWriter = new CSSStylesWriter(writer);
        jsWriter = new JavaScriptWriter(writer);
        schemaAnalyzer = new SchemaAnalyzer(engine);
        databaseAnalyzer = new DatabaseAnalyzer(engine);
    }

    private void generateCompleteReport() throws IOException {
        // HTML document structure
        htmlWriter.writeDocumentStart();
        htmlWriter.writeHead("🔍 Database Reachability Analysis - Interactive Drill-Down");
        htmlWriter.writeBodyStart();

        // Main container
        htmlWriter.openTag("div", "class='container'");

        writeHeader();
        writeExecutiveSummary();
        writeMainContent();
        writeStyles();
        writeJavaScript();

        htmlWriter.closeTag("div"); // container
        htmlWriter.writeBodyEnd();
        htmlWriter.writeDocumentEnd();
    }

    private void writeHeader() throws IOException {
        htmlWriter.openTag("header");
        htmlWriter.writeTag("h1", "🔍 Database Reachability Analysis");
        htmlWriter.writeTag("p",
                "Exact object reachability tracking - no approximations or statistical estimates.");
        htmlWriter.closeTag("header");
    }

    private void writeExecutiveSummary() throws IOException {
        // Calculate totals from database analyzer
        Map<String, DatabaseAnalyzer.DatabaseClassSummary> classSummaries = databaseAnalyzer.analyzeDatabaseContent();

        long totalEntries = 0;
        long totalUniqueObjects = 0;
        long totalReachedObjects = 0;
        long totalUnreachedObjects = 0;

        for (DatabaseAnalyzer.DatabaseClassSummary summary : classSummaries.values()) {
            totalEntries += summary.totalEntryCount;
            totalUniqueObjects += summary.uniqueObjectCount;
            totalReachedObjects += summary.reachedObjectCount;
            totalUnreachedObjects += summary.unreachedObjectCount;
        }

        double inflationFactor = totalUniqueObjects > 0 ? (double) totalEntries / totalUniqueObjects : 0;

        htmlWriter.openTag("section", "class='executive-summary'");
        htmlWriter.writeTag("h2", "Executive Summary");

        htmlWriter.openTag("div", "class='summary-stats'");

        // Total unique objects
        htmlWriter.openTag("div", "class='stat-card'");
        htmlWriter.writeTag("div", "Total Unique Objects", "class='stat-label'");
        htmlWriter.writeTag("div", String.format("%,d", totalUniqueObjects), "class='stat-value'");
        htmlWriter.closeTag("div");

        // Reached objects
        htmlWriter.openTag("div", "class='stat-card reached'");
        htmlWriter.writeTag("div", "✅ Reached Objects", "class='stat-label'");
        htmlWriter.writeTag("div", String.format("%,d", totalReachedObjects), "class='stat-value'");
        htmlWriter.closeTag("div");

        // Unreached objects
        htmlWriter.openTag("div", "class='stat-card unreached'");
        htmlWriter.writeTag("div", "❌ Unreached Objects", "class='stat-label'");
        htmlWriter.writeTag("div", String.format("%,d", totalUnreachedObjects), "class='stat-value'");
        htmlWriter.closeTag("div");

        htmlWriter.closeTag("div"); // summary-stats

        // Storage diagnostics
        htmlWriter.openTag("div", "class='storage-info'");
        htmlWriter.writeTag("h3", "DB4O Storage Information");
        htmlWriter.writeTag("p",
                String.format("Total Database Entries: %,d", totalEntries));
        htmlWriter.writeTag("p",
                String.format("Storage Inflation Factor: %.2fx", inflationFactor));
        htmlWriter.writeTag("p",
                "ℹ️ Note: DB4O stores objects in multiple inheritance tables (exploded storage). " +
                        "The same object appears in multiple class tables, one for each class in its inheritance chain.",
                "class='info-note'");
        htmlWriter.closeTag("div");

        htmlWriter.closeTag("section");
    }

    private void writeMainContent() throws IOException {
        htmlWriter.openTag("div", "id='main-content'");

        // Tabs for different views
        htmlWriter.openTag("div", "class='tabs'");
        htmlWriter.writeTag("button", "✅ Reached Objects", "class='tab-button active' onclick='showTab(\"reached\")'");
        htmlWriter.writeTag("button", "❌ Unreached Objects", "class='tab-button' onclick='showTab(\"unreached\")'");
        htmlWriter.writeTag("button", "📊 Database Diagnostics",
                "class='tab-button' onclick='showTab(\"diagnostics\")'");
        htmlWriter.closeTag("div");

        // Reached Objects Section
        htmlWriter.openTag("div", "id='reached-section' class='tab-content active'");
        writeReachedObjectsContent();
        htmlWriter.closeTag("div");

        // Unreached Objects Section
        htmlWriter.openTag("div", "id='unreached-section' class='tab-content'");
        writeUnreachedObjectsContent();
        htmlWriter.closeTag("div");

        // Diagnostics Section
        htmlWriter.openTag("div", "id='diagnostics-section' class='tab-content'");
        writeDiagnosticsContent();
        htmlWriter.closeTag("div");

        htmlWriter.closeTag("div");
    }

    private void writeReachedObjectsContent() throws IOException {
        htmlWriter.writeTag("h2", "✅ Reached Objects (Will Be Migrated)");
        htmlWriter.writeTag("p",
                "All reachable objects organized by schema module. Objects are grouped by their most specific class to avoid duplicates.");

        // Get reached objects by their most specific class (no duplicates)
        Map<DODatabaseClass, Set<Long>> reachedObjectsByClass = engine.getReachabilityTracker()
                .getReachedObjectsByMostSpecificClass();

        htmlWriter.openTag("div", "id='reached-content-area' class='content-area'");

        if (reachedObjectsByClass.isEmpty()) {
            htmlWriter.writeTag("div", "⚠️ No reached objects found.", "class='warning-message'");
            htmlWriter.closeTag("div");
            return;
        }

        // Build a GLOBAL map of ALL resolved objects from ALL classes for quick lookup
        // CRITICAL: Use ALL database classes, not just reached ones, so we can resolve
        // references to unreached objects
        Map<Long, DODatabaseObject> globalResolvedObjectsMap = new HashMap<>();
        DODatabaseClass[] allDbClasses = engine.getDatabase().getClasses();
        for (DODatabaseClass dbClass : allDbClasses) {
            if (dbClass.getResolvedObjects() != null) {
                for (DODatabaseObject obj : dbClass.getResolvedObjects()) {
                    globalResolvedObjectsMap.put(obj.getObjectId(), obj);
                }
            }
        }
        System.out.println(
                "DEBUG: Built global resolved objects map with " + globalResolvedObjectsMap.size() + " total objects");

        // Organize by schema modules
        htmlWriter.openTag("div", "class='module-tree'");

        DOSchema schema = engine.getSchema();
        DOSchemaModule[] modules = schema.getModules();

        // Process each module
        for (DOSchemaModule module : modules) {
            // Collect reached objects for classes in this module
            Map<DODatabaseClass, Set<Long>> moduleReachedObjects = new HashMap<>();
            int totalModuleObjects = 0;

            for (DOSchemaClass schemaClass : module.getClasses()) {
                String className = schemaClass.getAbsoluteName();

                // Find matching database class
                DODatabaseClass dbClass = null;
                for (Map.Entry<DODatabaseClass, Set<Long>> entry : reachedObjectsByClass.entrySet()) {
                    if (entry.getKey().getAbsoluteName().equals(className)) {
                        dbClass = entry.getKey();
                        Set<Long> reachedIds = entry.getValue();
                        if (reachedIds != null && !reachedIds.isEmpty()) {
                            moduleReachedObjects.put(dbClass, reachedIds);
                            totalModuleObjects += reachedIds.size();
                        }
                        break;
                    }
                }
            }

            // Skip module if no reached objects
            if (moduleReachedObjects.isEmpty()) {
                continue;
            }

            // Module header (expandable)
            htmlWriter.openTag("div", "class='tree-node module-node'");
            htmlWriter.openTag("div", "class='tree-node-header' onclick='toggleNode(this)'");
            htmlWriter.writeTag("span", "▶", "class='expand-icon'");
            htmlWriter.writeTag("span", "📁 " + module.getName(), "class='node-label'");
            htmlWriter.writeTag("span",
                    String.format("%,d objects in %d classes", totalModuleObjects, moduleReachedObjects.size()),
                    "class='node-count'");
            htmlWriter.closeTag("div");

            // Module classes (initially collapsed)
            htmlWriter.openTag("div", "class='tree-node-children collapsed'");

            // Sort classes by object count (descending)
            List<Map.Entry<DODatabaseClass, Set<Long>>> sortedClasses = new ArrayList<>(
                    moduleReachedObjects.entrySet());
            sortedClasses.sort((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()));

            for (Map.Entry<DODatabaseClass, Set<Long>> entry : sortedClasses) {
                DODatabaseClass dbClass = entry.getKey();
                Set<Long> reachedIds = entry.getValue();
                String className = dbClass.getAbsoluteName();

                // Extract short name
                String shortName = className.contains(".")
                        ? className.substring(className.lastIndexOf('.') + 1)
                        : className;

                // Class header (expandable)
                htmlWriter.openTag("div", "class='tree-node class-node'");
                htmlWriter.openTag("div", "class='tree-node-header' onclick='toggleNode(this)'");
                htmlWriter.writeTag("span", "▶", "class='expand-icon'");
                htmlWriter.writeTag("span", "📄 " + shortName, "class='node-label'");
                htmlWriter.writeTag("span", String.format("%,d objects", reachedIds.size()), "class='node-count'");
                htmlWriter.closeTag("div");

                // Object IDs (initially collapsed)
                htmlWriter.openTag("div", "class='tree-node-children collapsed'");
                htmlWriter.writeTag("div", "Full class name: " + className, "class='full-class-name'");

                htmlWriter.openTag("div", "class='object-list'");

                // Sort object IDs for consistent display
                List<Long> sortedIds = new ArrayList<>(reachedIds);
                Collections.sort(sortedIds);

                // Show first 100 objects, then add "show more" if needed
                int displayLimit = 100;
                int displayCount = Math.min(displayLimit, sortedIds.size());

                for (int i = 0; i < displayCount; i++) {
                    Long objectId = sortedIds.get(i);
                    DODatabaseObject resolvedObj = globalResolvedObjectsMap.get(objectId);

                    // Object header (expandable if we have resolved data)
                    if (resolvedObj != null) {
                        htmlWriter.openTag("div", "class='tree-node object-node'");
                        htmlWriter.openTag("div", "class='tree-node-header' onclick='toggleNode(this)'");
                        htmlWriter.writeTag("span", "▶", "class='expand-icon'");
                        htmlWriter.writeTag("span", "🔷 Object ID: " + objectId, "class='node-label'");

                        // Count references
                        int refCount = (resolvedObj.getReferences() != null ? resolvedObj.getReferences().length : 0)
                                + (resolvedObj.getCollections() != null ? resolvedObj.getCollections().length : 0);
                        if (refCount > 0) {
                            htmlWriter.writeTag("span", refCount + " references", "class='node-count'");
                        }
                        htmlWriter.closeTag("div");

                        // Object details (initially collapsed)
                        htmlWriter.openTag("div", "class='tree-node-children collapsed'");
                        writeObjectReferences(resolvedObj, globalResolvedObjectsMap);
                        htmlWriter.closeTag("div"); // tree-node-children
                        htmlWriter.closeTag("div"); // object-node
                    } else {
                        // Simple display if no resolved data
                        htmlWriter.writeTag("div", "🔷 Object ID: " + objectId, "class='object-id-item'");
                    }
                }

                if (sortedIds.size() > displayLimit) {
                    htmlWriter.writeTag("div",
                            String.format("... and %,d more objects (showing first %d)",
                                    sortedIds.size() - displayLimit, displayLimit),
                            "class='object-id-more'");
                }

                htmlWriter.closeTag("div"); // object-list
                htmlWriter.closeTag("div"); // tree-node-children
                htmlWriter.closeTag("div"); // class-node
            }

            htmlWriter.closeTag("div"); // module tree-node-children
            htmlWriter.closeTag("div"); // module-node
        }

        htmlWriter.closeTag("div"); // module-tree
        htmlWriter.closeTag("div"); // reached-content-area
    }

    /**
     * Writes the references (direct and collection) for a given database object.
     */
    private void writeObjectReferences(DODatabaseObject obj, Map<Long, DODatabaseObject> resolvedObjectsMap)
            throws IOException {
        boolean hasReferences = false;

        // Direct references
        DOObjectReference[] references = obj.getReferences();
        if (references != null && references.length > 0) {
            hasReferences = true;
            htmlWriter.writeTag("div", "📎 Direct References:", "class='reference-section-header'");
            htmlWriter.openTag("div", "class='reference-list'");

            for (DOObjectReference ref : references) {
                DOField field = ref.getField();
                Long targetId = ref.getTargetObjectId();

                if (targetId != null) {
                    // Handle both regular references (field != null) and synthetic references
                    // (field == null)
                    String fieldName = (field != null) ? field.getName() : "[Resolved Entity]";
                    String fieldType = (field != null) ? field.getTypeName() : null;
                    String shortType = fieldType != null && fieldType.contains(".")
                            ? fieldType.substring(fieldType.lastIndexOf('.') + 1)
                            : fieldType;

                    // Look up the target object to see if it has references
                    DODatabaseObject targetObj = resolvedObjectsMap.get(targetId);

                    if (targetObj != null) {
                        // For synthetic references (shortType == null), get type from target object
                        if (shortType == null) {
                            shortType = targetObj.getMostSpecificClass().getShortName();
                        }

                        // Check if this is an ID-type object that wraps a primitive Long
                        boolean isIdTypeObj = shortType.startsWith("ID") &&
                                (targetObj.getReferences() == null || targetObj.getReferences().length == 0) &&
                                (targetObj.getCollections() == null || targetObj.getCollections().length == 0);

                        DODatabaseObject actualEntity = null;
                        Long actualEntityId = null;

                        if (isIdTypeObj) {
                            // Try to find the entity this ID points to by extracting mID field
                            actualEntityId = extractMIdValue(targetObj, resolvedObjectsMap);
                            if (actualEntityId != null) {
                                actualEntity = resolvedObjectsMap.get(actualEntityId);
                            }
                        }

                        // If this is an ID-type with a resolved entity, show both the ID wrapper and
                        // the entity
                        if (isIdTypeObj && actualEntity != null) {
                            String entityType = actualEntity.getMostSpecificClass().getShortName();
                            int entityRefCount = (actualEntity.getReferences() != null
                                    ? actualEntity.getReferences().length
                                    : 0)
                                    + (actualEntity.getCollections() != null ? actualEntity.getCollections().length
                                            : 0);

                            htmlWriter.openTag("div", "class='tree-node reference-node'");
                            htmlWriter.openTag("div", "class='tree-node-header' onclick='toggleNode(this)'");
                            htmlWriter.writeTag("span", "▶", "class='expand-icon'");
                            htmlWriter.writeTag("span",
                                    String.format("→ %s: %s → %s (Object ID %d)", fieldName, shortType, entityType,
                                            actualEntityId),
                                    "class='node-label'");

                            if (entityRefCount > 0) {
                                htmlWriter.writeTag("span", entityRefCount + " references", "class='node-count'");
                            }
                            htmlWriter.closeTag("div"); // tree-node-header

                            // Show the actual entity's references
                            htmlWriter.openTag("div", "class='tree-node-children collapsed'");
                            writeObjectReferences(actualEntity, resolvedObjectsMap);
                            htmlWriter.closeTag("div"); // tree-node-children
                            htmlWriter.closeTag("div"); // reference-node
                        } else {
                            // Regular object or ID-type without resolved entity
                            int refCount = (targetObj.getReferences() != null ? targetObj.getReferences().length : 0)
                                    + (targetObj.getCollections() != null ? targetObj.getCollections().length : 0);

                            htmlWriter.openTag("div", "class='tree-node reference-node'");
                            htmlWriter.openTag("div", "class='tree-node-header' onclick='toggleNode(this)'");
                            htmlWriter.writeTag("span", "▶", "class='expand-icon'");
                            htmlWriter.writeTag("span",
                                    String.format("→ %s: %s → Object ID %d", fieldName, shortType, targetId),
                                    "class='node-label'");

                            if (refCount > 0) {
                                htmlWriter.writeTag("span", refCount + " references", "class='node-count'");
                            }
                            htmlWriter.closeTag("div"); // tree-node-header

                            // Recursively write the target object's references (initially collapsed)
                            htmlWriter.openTag("div", "class='tree-node-children collapsed'");
                            writeObjectReferences(targetObj, resolvedObjectsMap);
                            htmlWriter.closeTag("div"); // tree-node-children
                            htmlWriter.closeTag("div"); // reference-node
                        }
                    } else {
                        // Target not resolved, just show as text
                        htmlWriter.writeTag("div",
                                String.format("  → %s: %s → Object ID %d", fieldName, shortType, targetId),
                                "class='reference-item'");
                    }
                }
            }

            htmlWriter.closeTag("div"); // reference-list
        }

        // Collection references
        DOCollectionReference[] collections = obj.getCollections();
        if (collections != null && collections.length > 0) {
            hasReferences = true;
            htmlWriter.writeTag("div", "📦 Collection References:", "class='reference-section-header'");
            htmlWriter.openTag("div", "class='reference-list'");

            for (DOCollectionReference collRef : collections) {
                DOField field = collRef.getField();
                Long[] containedIds = collRef.getContainedObjectIds();

                if (field != null && containedIds != null && containedIds.length > 0) {
                    String fieldName = field.getName();
                    String contentType = collRef.getResolvedContentType();
                    String shortContentType = contentType != null && contentType.contains(".")
                            ? contentType.substring(contentType.lastIndexOf('.') + 1)
                            : contentType;

                    htmlWriter.writeTag("div",
                            String.format("  → %s: Collection<%s> (%d items)",
                                    fieldName, shortContentType, containedIds.length),
                            "class='reference-item collection-ref'");

                    // Show first few items as expandable nodes
                    int showCount = Math.min(5, containedIds.length);
                    htmlWriter.openTag("div", "class='collection-items'");
                    for (int i = 0; i < showCount; i++) {
                        Long itemId = containedIds[i];
                        DODatabaseObject itemObj = resolvedObjectsMap.get(itemId);

                        if (itemObj != null) {
                            // Make collection item expandable
                            int refCount = (itemObj.getReferences() != null ? itemObj.getReferences().length : 0)
                                    + (itemObj.getCollections() != null ? itemObj.getCollections().length : 0);

                            htmlWriter.openTag("div", "class='tree-node collection-item-node'");
                            htmlWriter.openTag("div", "class='tree-node-header' onclick='toggleNode(this)'");
                            htmlWriter.writeTag("span", "▶", "class='expand-icon'");
                            htmlWriter.writeTag("span",
                                    String.format("[%d] → Object ID %d", i, itemId),
                                    "class='node-label'");

                            if (refCount > 0) {
                                htmlWriter.writeTag("span", refCount + " references", "class='node-count'");
                            }
                            htmlWriter.closeTag("div"); // tree-node-header

                            // Recursively write the item's references (initially collapsed)
                            htmlWriter.openTag("div", "class='tree-node-children collapsed'");
                            writeObjectReferences(itemObj, resolvedObjectsMap);
                            htmlWriter.closeTag("div"); // tree-node-children
                            htmlWriter.closeTag("div"); // collection-item-node
                        } else {
                            // Item not resolved, just show as text
                            htmlWriter.writeTag("div",
                                    String.format("     [%d] → Object ID %d", i, itemId),
                                    "class='collection-item'");
                        }
                    }
                    if (containedIds.length > showCount) {
                        htmlWriter.writeTag("div",
                                String.format("     ... and %d more", containedIds.length - showCount),
                                "class='collection-more'");
                    }
                    htmlWriter.closeTag("div"); // collection-items
                }
            }

            htmlWriter.closeTag("div"); // reference-list
        }

        // Primitive fields - display after references
        writePrimitiveFields(obj);

        if (!hasReferences) {
            htmlWriter.writeTag("div", "No references (leaf object)", "class='no-references'");
        }
    }

    /**
     * Write primitive field values for an object
     */
    private void writePrimitiveFields(DODatabaseObject obj) throws IOException {
        try {
            // Get the database container
            com.db4o.ext.ExtObjectContainer container = engine.getDatabase().getContainer();

            // Extract all primitive field values using the utility method
            java.util.Map<String, dataobjects.util.ObjectResolverUtil.PrimitiveFieldValue> fieldValues = dataobjects.util.ObjectResolverUtil
                    .extractPrimitiveFieldValues(
                            container, obj.getObjectId(), obj.getAllClasses());

            // If we have primitive fields, display them
            if (!fieldValues.isEmpty()) {
                htmlWriter.writeTag("div", "📋 Data Fields:", "class='reference-section-header primitive-header'");
                htmlWriter.openTag("div", "class='primitive-list'");

                for (java.util.Map.Entry<String, dataobjects.util.ObjectResolverUtil.PrimitiveFieldValue> entry : fieldValues
                        .entrySet()) {
                    DOField field = entry.getValue().field;
                    Object value = entry.getValue().value;

                    // Format the value for display
                    String displayValue;
                    if (value == null) {
                        displayValue = "<null>";
                    } else if (value instanceof String) {
                        String strValue = (String) value;
                        // Truncate very long strings
                        if (strValue.length() > 100) {
                            displayValue = "\"" + strValue.substring(0, 100) + "...\"";
                        } else {
                            displayValue = "\"" + strValue + "\"";
                        }
                    } else if (value instanceof java.util.Date) {
                        displayValue = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(value);
                    } else {
                        displayValue = value.toString();
                    }

                    // Write the field item
                    htmlWriter.openTag("div", "class='primitive-field-item'");
                    htmlWriter.writeTag("span", field.getName() + ":", "class='primitive-field-name'");
                    htmlWriter.writeTag("span", " " + displayValue, "class='primitive-field-value'");
                    htmlWriter.writeTag("span", " (" + getShortTypeName(field.getTypeName()) + ")",
                            "class='primitive-field-type'");
                    htmlWriter.closeTag("div");
                }

                htmlWriter.closeTag("div"); // primitive-list
            }

        } catch (Exception e) {
            // If we can't extract primitive fields, just skip this section
        }
    }

    /**
     * Get short type name for display
     */
    private String getShortTypeName(String fullTypeName) {
        if (fullTypeName == null) {
            return "?";
        }
        int lastDot = fullTypeName.lastIndexOf('.');
        return lastDot >= 0 ? fullTypeName.substring(lastDot + 1) : fullTypeName;
    }

    private void writeUnreachedObjectsContent() throws IOException {
        htmlWriter.writeTag("h2", "❌ Unreached Objects (Will NOT Be Migrated)");
        htmlWriter.writeTag("p",
                "Unique object IDs that are not reachable from any module, grouped by their most precise class.");

        Map<DODatabaseClass, Set<Long>> unreachedObjectsByClass = engine.getReachabilityTracker()
                .getUnreachedObjectsByClass();
        Map<DODatabaseClass, Set<Long>> reachedObjectsByClass = engine.getReachabilityTracker()
                .getReachedObjectsByClass();

        htmlWriter.openTag("div", "id='unreached-content-area' class='content-area'");

        if (unreachedObjectsByClass.isEmpty()) {
            htmlWriter.writeTag("div", "✅ Excellent! No unreached objects found. All database objects are reachable.",
                    "class='success-message'");
        } else {
            // Group by class and show actual object IDs
            htmlWriter.openTag("div", "class='unreached-classes'");

            // Convert to list and sort by count descending
            List<Map.Entry<DODatabaseClass, Set<Long>>> sortedEntries = new ArrayList<>(
                    unreachedObjectsByClass.entrySet());
            sortedEntries.sort((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()));

            for (Map.Entry<DODatabaseClass, Set<Long>> entry : sortedEntries) {
                DODatabaseClass dbClass = entry.getKey();
                String className = dbClass.getAbsoluteName();
                Set<Long> unreachedIds = entry.getValue();

                // Get reached count for this class
                Set<Long> reachedIds = reachedObjectsByClass.get(dbClass);
                long reachedCount = reachedIds != null ? reachedIds.size() : 0;
                long totalCount = unreachedIds.size() + reachedCount;

                // Extract short name
                String shortName = className.contains(".")
                        ? className.substring(className.lastIndexOf('.') + 1)
                        : className;

                htmlWriter.openTag("div", "class='unreached-class-item'");

                // Class header (expandable)
                htmlWriter.openTag("div", "class='unreached-class-header' onclick='toggleUnreached(this)'");
                htmlWriter.writeTag("span", "▶", "class='expand-icon'");
                htmlWriter.writeTag("div", shortName, "class='class-name'");
                htmlWriter.writeTag("div",
                        String.format("✅ %,d reached | ❌ %,d unreached (total: %,d)",
                                reachedCount, unreachedIds.size(), totalCount),
                        "class='unreached-count'");
                htmlWriter.closeTag("div");

                // Object IDs list (initially collapsed)
                htmlWriter.openTag("div", "class='unreached-object-ids collapsed'");
                htmlWriter.writeTag("div", "Full class name: " + className, "class='full-class-name'");

                htmlWriter.openTag("div", "class='object-id-list'");

                // Sort IDs for consistent display
                List<Long> sortedIds = new ArrayList<>(unreachedIds);
                Collections.sort(sortedIds);

                // Display all IDs (since there are only 15 total unreached objects based on our
                // test)
                // But add pagination logic in case this grows
                int displayLimit = 200;
                int displayCount = Math.min(displayLimit, sortedIds.size());

                for (int i = 0; i < displayCount; i++) {
                    Long objectId = sortedIds.get(i);
                    htmlWriter.writeTag("div", "Object ID: " + objectId, "class='object-id-item unreached'");
                }

                if (sortedIds.size() > displayLimit) {
                    htmlWriter.writeTag("div",
                            String.format("... and %,d more objects (showing first %d)",
                                    sortedIds.size() - displayLimit, displayLimit),
                            "class='object-id-more'");
                }

                htmlWriter.closeTag("div"); // object-id-list
                htmlWriter.closeTag("div"); // unreached-object-ids
                htmlWriter.closeTag("div"); // unreached-class-item
            }

            htmlWriter.closeTag("div"); // unreached-classes
        }

        htmlWriter.closeTag("div"); // unreached-content-area
    }

    private void writeDiagnosticsContent() throws IOException {
        htmlWriter.writeTag("h2", "📊 Database Storage Diagnostics");

        // Calculate diagnostics
        Map<String, DatabaseAnalyzer.DatabaseClassSummary> classSummaries = databaseAnalyzer.analyzeDatabaseContent();

        long totalEntries = 0;
        long totalUnique = 0;
        long totalReached = 0;
        long totalUnreached = 0;

        for (DatabaseAnalyzer.DatabaseClassSummary summary : classSummaries.values()) {
            totalEntries += summary.totalEntryCount;
            totalUnique += summary.uniqueObjectCount;
            totalReached += summary.reachedObjectCount;
            totalUnreached += summary.unreachedObjectCount;
        }

        double inflationFactor = totalUnique > 0 ? (double) totalEntries / totalUnique : 0;
        long overlap = (totalReached + totalUnreached) - totalUnique;

        htmlWriter.openTag("div", "class='diagnostics-panel'");

        // DB4O Storage Model Explanation
        htmlWriter.openTag("div", "class='diagnostic-section'");
        htmlWriter.writeTag("h3", "DB4O 'Exploded' Storage Model");
        htmlWriter.writeTag("p",
                "In DB4O, each object is stored in MULTIPLE tables - one for each class in its inheritance chain.");

        htmlWriter.openTag("div", "class='example-box'");
        htmlWriter.writeTag("div", "Example: LeafClass extends MiddleClass extends BaseClass", "class='example-title'");
        htmlWriter.writeTag("p", "Object #12345 appears in:");
        htmlWriter.openTag("ul");
        htmlWriter.writeTag("li", "✓ LeafClass table");
        htmlWriter.writeTag("li", "✓ MiddleClass table (duplicate)");
        htmlWriter.writeTag("li", "✓ BaseClass table (duplicate)");
        htmlWriter.closeTag("ul");
        htmlWriter.writeTag("p", "Result: 1 object = 3 database entries", "class='result'");
        htmlWriter.closeTag("div");

        htmlWriter.closeTag("div");

        // Storage Statistics
        htmlWriter.openTag("div", "class='diagnostic-section'");
        htmlWriter.writeTag("h3", "Your Database Statistics");
        htmlWriter.openTag("div", "class='stats-grid'");

        htmlWriter.openTag("div", "class='stat-item'");
        htmlWriter.writeTag("div", "Total Entries", "class='stat-label'");
        htmlWriter.writeTag("div", String.format("%,d", totalEntries), "class='stat-number'");
        htmlWriter.closeTag("div");

        htmlWriter.openTag("div", "class='stat-item'");
        htmlWriter.writeTag("div", "Unique Objects", "class='stat-label'");
        htmlWriter.writeTag("div", String.format("%,d", totalUnique), "class='stat-number'");
        htmlWriter.closeTag("div");

        htmlWriter.openTag("div", "class='stat-item'");
        htmlWriter.writeTag("div", "Storage Inflation", "class='stat-label'");
        htmlWriter.writeTag("div", String.format("%.2fx", inflationFactor), "class='stat-number'");
        htmlWriter.closeTag("div");

        htmlWriter.closeTag("div");
        htmlWriter.closeTag("div");

        // Reachability Overlap
        htmlWriter.openTag("div", "class='diagnostic-section'");
        htmlWriter.writeTag("h3", "Reachability Overlap");
        htmlWriter.writeTag("p", String.format("Objects marked as BOTH reached AND unreached: %,d", overlap));
        htmlWriter.writeTag("p",
                "Why? The same object appears in a leaf table (reached) AND parent table (unreached). This is EXPECTED and correct!");
        htmlWriter.closeTag("div");

        // Top Classes with Duplicates
        htmlWriter.openTag("div", "class='diagnostic-section'");
        htmlWriter.writeTag("h3", "Top Classes with Most Duplicates");
        htmlWriter.openTag("table", "class='duplicate-table'");
        htmlWriter.openTag("tr");
        htmlWriter.writeTag("th", "Class Name");
        htmlWriter.writeTag("th", "Total Entries");
        htmlWriter.writeTag("th", "Unique Objects");
        htmlWriter.writeTag("th", "Duplication Factor");
        htmlWriter.closeTag("tr");

        // Sort by total entries and show top 10
        classSummaries.values().stream()
                .sorted((a, b) -> Long.compare(b.totalEntryCount, a.totalEntryCount))
                .limit(10)
                .forEach(summary -> {
                    try {
                        double dupFactor = summary.uniqueObjectCount > 0
                                ? (double) summary.totalEntryCount / summary.uniqueObjectCount
                                : 0;

                        htmlWriter.openTag("tr");
                        htmlWriter.writeTag("td", summary.shortName);
                        htmlWriter.writeTag("td", String.format("%,d", summary.totalEntryCount));
                        htmlWriter.writeTag("td", String.format("%,d", summary.uniqueObjectCount));
                        htmlWriter.writeTag("td", String.format("%.2fx", dupFactor));
                        htmlWriter.closeTag("tr");
                    } catch (IOException e) {
                        // Ignore
                    }
                });

        htmlWriter.closeTag("table");
        htmlWriter.closeTag("div");

        // Algorithm Explanation
        htmlWriter.openTag("div", "class='diagnostic-section'");
        htmlWriter.writeTag("h3", "🔄 Reachability Algorithm");
        htmlWriter.openTag("div", "class='algorithm-steps'");

        htmlWriter.openTag("div", "class='algo-step'");
        htmlWriter.writeTag("div", "Step 1: Start with Leaf Classes", "class='step-title'");
        htmlWriter.writeTag("p",
                "Identify all \"end\" classes (no subclasses). These are the entry points for traversal.");
        htmlWriter.closeTag("div");

        htmlWriter.openTag("div", "class='algo-step'");
        htmlWriter.writeTag("div", "Step 2: Process Each Leaf Object", "class='step-title'");
        htmlWriter.writeTag("p",
                "Load object from database, mark as REACHED for ALL classes in inheritance chain, process all fields recursively.");
        htmlWriter.closeTag("div");

        htmlWriter.openTag("div", "class='algo-step'");
        htmlWriter.writeTag("div", "Step 3: Recursive Field Processing", "class='step-title'");
        htmlWriter.writeTag("p",
                "For direct references: Follow and mark as reached. For collections: Mark each contained object as reached. Continue recursively until all references explored.");
        htmlWriter.closeTag("div");

        htmlWriter.openTag("div", "class='algo-step'");
        htmlWriter.writeTag("div", "Step 4: Result", "class='step-title'");
        htmlWriter.writeTag("p", "✅ Reached: Any object encountered during traversal");
        htmlWriter.writeTag("p", "❌ Unreached: Objects never encountered");
        htmlWriter.writeTag("p", "This is EXACT tracking - not statistical estimation!", "class='highlight'");
        htmlWriter.closeTag("div");

        htmlWriter.closeTag("div");
        htmlWriter.closeTag("div");

        htmlWriter.closeTag("div"); // diagnostics-panel
    }

    private void writeStyles() throws IOException {
        cssWriter.writeStyles();

        // Add additional styles for tabs and new sections
        htmlWriter.openTag("style");

        // Tab styles
        writer.write(".tabs { display: flex; gap: 5px; margin-bottom: 15px; border-bottom: 2px solid #e2e8f0; }\n");
        writer.write(
                ".tab-button { background: #f8fafc; border: none; padding: 12px 20px; cursor: pointer; font-size: 14px; font-weight: 500; border-radius: 6px 6px 0 0; transition: all 0.2s ease; }\n");
        writer.write(".tab-button:hover { background: #f1f5f9; }\n");
        writer.write(".tab-button.active { background: #3b82f6; color: white; }\n");
        writer.write(".tab-content { display: none; }\n");
        writer.write(".tab-content.active { display: block; }\n");

        // Unreached objects styles
        writer.write(".unreached-classes { display: flex; flex-direction: column; gap: 10px; }\n");
        writer.write(
                ".unreached-class-item { background: #fff; border: 1px solid #fca5a5; border-left: 4px solid #ef4444; border-radius: 4px; overflow: hidden; }\n");
        writer.write(
                ".unreached-class-header { background: #fef2f2; padding: 12px 15px; display: flex; justify-content: space-between; align-items: center; cursor: pointer; user-select: none; }\n");
        writer.write(
                ".unreached-class-header:hover { background: #fee2e2; }\n");
        writer.write(
                ".class-name { font-weight: 500; font-family: 'Consolas', 'Monaco', monospace; font-size: 13px; }\n");
        writer.write(".unreached-count { color: #dc2626; font-weight: 600; font-size: 13px; }\n");
        writer.write(
                ".unreached-object-ids { padding: 0px; background: #fff; border-top: 1px solid #fee2e2; max-height: 0; overflow: hidden; transition: max-height 0.3s ease; }\n");
        writer.write(".unreached-object-ids:not(.collapsed) { max-height: 2000px; padding: 12px 15px; }\n");
        writer.write(
                ".full-class-name { color: #64748b; font-size: 11px; margin-bottom: 10px; font-family: 'Consolas', 'Monaco', monospace; }\n");

        // Tree structure styles
        writer.write(".module-tree { display: flex; flex-direction: column; gap: 12px; }\n");
        writer.write(
                ".tree-node { background: #fff; border: 1px solid #e2e8f0; border-radius: 6px; overflow: hidden; }\n");
        writer.write(
                ".tree-node-header { padding: 12px 15px; display: flex; align-items: center; gap: 8px; cursor: pointer; user-select: none; transition: background 0.2s; }\n");
        writer.write(".tree-node-header:hover { background: #f8fafc; }\n");
        writer.write(
                ".expand-icon { font-size: 12px; transition: transform 0.2s; display: inline-block; width: 16px; }\n");
        writer.write(".tree-node-header.expanded .expand-icon { transform: rotate(90deg); }\n");
        writer.write(".node-label { font-weight: 500; flex: 1; }\n");
        writer.write(".node-count { color: #64748b; font-size: 13px; }\n");
        writer.write(
                ".tree-node-children { padding-left: 24px; max-height: 0; overflow: hidden; transition: max-height 0.3s ease; }\n");
        writer.write(".tree-node-children:not(.collapsed) { max-height: 100000px; padding: 12px 0px 12px 24px; }\n");
        writer.write(".module-node { border-left: 4px solid #3b82f6; }\n");
        writer.write(".class-node { border-left: 4px solid #10b981; margin-bottom: 8px; }\n");
        writer.write(
                ".object-id-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 6px; padding: 8px; background: #f8fafc; border-radius: 4px; }\n");
        writer.write(
                ".object-id-item { padding: 6px 10px; background: #fff; border: 1px solid #e2e8f0; border-radius: 3px; font-family: 'Consolas', 'Monaco', monospace; font-size: 12px; color: #475569; }\n");
        writer.write(
                ".object-id-item.unreached { background: #fef2f2; border-color: #fca5a5; color: #dc2626; }\n");
        writer.write(
                ".object-id-more { grid-column: 1 / -1; padding: 8px; text-align: center; color: #64748b; font-style: italic; font-size: 12px; }\n");
        writer.write(
                ".success-message { padding: 20px; background: #f0fdf4; border: 2px solid #86efac; border-radius: 6px; color: #166534; font-weight: 500; text-align: center; }\n");

        // Diagnostics panel styles
        writer.write(".diagnostics-panel { display: flex; flex-direction: column; gap: 20px; }\n");
        writer.write(
                ".diagnostic-section { background: #fff; border: 1px solid #e2e8f0; border-radius: 6px; padding: 15px; }\n");
        writer.write(
                ".diagnostic-section h3 { margin: 0 0 12px 0; color: #1e293b; font-size: 1.1em; border-bottom: 1px solid #e2e8f0; padding-bottom: 8px; }\n");
        writer.write(
                ".example-box { background: #f8fafc; border: 1px solid #cbd5e1; border-radius: 4px; padding: 12px; margin: 10px 0; }\n");
        writer.write(".example-title { font-weight: 600; color: #475569; margin-bottom: 8px; }\n");
        writer.write(".example-box ul { margin: 8px 0; padding-left: 20px; }\n");
        writer.write(".example-box li { margin: 4px 0; color: #64748b; }\n");
        writer.write(".example-box .result { font-weight: 600; color: #1e293b; margin-top: 8px; }\n");
        writer.write(
                ".stats-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 15px; margin: 10px 0; }\n");
        writer.write(
                ".stat-item { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 4px; padding: 12px; text-align: center; }\n");
        writer.write(".stat-label { font-size: 0.85em; color: #64748b; margin-bottom: 6px; }\n");
        writer.write(".stat-number { font-size: 1.8em; font-weight: bold; color: #1e293b; }\n");
        writer.write(".duplicate-table { width: 100%; border-collapse: collapse; margin: 10px 0; }\n");
        writer.write(
                ".duplicate-table th { background: #f1f5f9; padding: 10px; text-align: left; font-weight: 600; color: #475569; border-bottom: 2px solid #cbd5e1; }\n");
        writer.write(".duplicate-table td { padding: 8px 10px; border-bottom: 1px solid #e2e8f0; color: #64748b; }\n");
        writer.write(".duplicate-table tr:hover { background: #f8fafc; }\n");
        writer.write(".algorithm-steps { display: flex; flex-direction: column; gap: 12px; }\n");
        writer.write(
                ".algo-step { background: #f8fafc; border-left: 3px solid #10b981; padding: 12px; border-radius: 4px; }\n");
        writer.write(".step-title { font-weight: 600; color: #1e293b; margin-bottom: 6px; }\n");
        writer.write(".algo-step p { margin: 4px 0; color: #64748b; font-size: 13px; }\n");
        writer.write(
                ".highlight { background: #fef3c7; border-left: 3px solid #f59e0b; padding: 8px; border-radius: 3px; color: #d97706; font-weight: 500; }\n");

        htmlWriter.closeTag("style");
    }

    private void writeJavaScript() throws IOException {
        htmlWriter.openTag("script");

        // Write data structure
        writeDataStructure();

        // Tab switching function
        writer.write("function showTab(tabName) {\n");
        writer.write("    // Hide all tab contents\n");
        writer.write("    document.querySelectorAll('.tab-content').forEach(content => {\n");
        writer.write("        content.classList.remove('active');\n");
        writer.write("    });\n");
        writer.write("    // Remove active from all buttons\n");
        writer.write("    document.querySelectorAll('.tab-button').forEach(button => {\n");
        writer.write("        button.classList.remove('active');\n");
        writer.write("    });\n");
        writer.write("    // Show selected tab\n");
        writer.write("    document.getElementById(tabName + '-section').classList.add('active');\n");
        writer.write("    // Activate button\n");
        writer.write("    event.target.classList.add('active');\n");
        writer.write("}\n\n");

        // Tree node toggle function
        writer.write("function toggleNode(headerElement) {\n");
        writer.write("    const header = headerElement;\n");
        writer.write("    const children = header.nextElementSibling;\n");
        writer.write("    \n");
        writer.write("    if (children && children.classList.contains('tree-node-children')) {\n");
        writer.write("        children.classList.toggle('collapsed');\n");
        writer.write("        header.classList.toggle('expanded');\n");
        writer.write("    }\n");
        writer.write("}\n\n");

        // Unreached toggle function
        writer.write("function toggleUnreached(headerElement) {\n");
        writer.write("    const header = headerElement;\n");
        writer.write("    const children = header.nextElementSibling;\n");
        writer.write("    \n");
        writer.write("    if (children && children.classList.contains('unreached-object-ids')) {\n");
        writer.write("        children.classList.toggle('collapsed');\n");
        writer.write("        header.classList.toggle('expanded');\n");
        writer.write("    }\n");
        writer.write("}\n\n");

        htmlWriter.closeTag("script");

        // Write JavaScript functions in separate script tag
        jsWriter.writeScript();
    }

    private void writeDataStructure() throws IOException {
        writer.write("// Data structure for drill-down functionality\n");
        writer.write("const reachabilityData = {\n");

        // Module information
        writer.write("    modules: {\n");
        writeModulesData();
        writer.write("    },\n");

        // All schema classes
        writer.write("    allSchemaClasses: {\n");
        writeAllSchemaClassesData();
        writer.write("    },\n");

        // Database statistics
        writer.write("    database: {\n");
        writeDatabaseData();
        writer.write("    }\n");

        writer.write("};\n\n");
    }

    private void writeModulesData() throws IOException {
        List<SchemaAnalyzer.ModuleInfo> modules = schemaAnalyzer.analyzeModules();

        for (int i = 0; i < modules.size(); i++) {
            SchemaAnalyzer.ModuleInfo module = modules.get(i);
            writer.write("        \"" + escapeJs(module.name) + "\": {\n");
            writer.write("            name: \"" + escapeJs(module.name) + "\",\n");
            writer.write("            classCount: " + module.classes.size() + ",\n");
            writer.write("            classes: {\n");

            for (int j = 0; j < module.classes.size(); j++) {
                SchemaAnalyzer.ClassInfo classInfo = module.classes.get(j);
                writeClassDataStructure(classInfo);
                if (j < module.classes.size() - 1) {
                    writer.write(",");
                }
                writer.write("\n");
            }

            writer.write("            }\n");
            writer.write("        }");

            if (i < modules.size() - 1) {
                writer.write(",");
            }
            writer.write("\n");
        }
    }

    private void writeAllSchemaClassesData() throws IOException {
        Map<String, SchemaAnalyzer.ClassInfo> allClasses = schemaAnalyzer.analyzeAllClasses();

        int count = 0;
        for (Map.Entry<String, SchemaAnalyzer.ClassInfo> entry : allClasses.entrySet()) {
            SchemaAnalyzer.ClassInfo classInfo = entry.getValue();
            writer.write("        \"" + escapeJs(classInfo.name) + "\": {\n");
            writer.write("            name: \"" + escapeJs(classInfo.name) + "\",\n");
            writer.write("            shortName: \"" + escapeJs(classInfo.shortName) + "\",\n");
            writer.write("            description: \""
                    + escapeJs(classInfo.description != null ? classInfo.description : "") + "\",\n");
            writer.write("            superClass: \""
                    + escapeJs(classInfo.superClass != null ? classInfo.superClass : "") + "\",\n");
            writer.write("            moduleName: "
                    + (classInfo.moduleName != null ? "\"" + escapeJs(classInfo.moduleName) + "\"" : "null") + ",\n");
            writer.write("            isFoundation: " + (classInfo.moduleName == null) + ",\n");
            writer.write("            fieldCount: " + classInfo.fields.size() + ",\n");

            // CRITICAL: Include field information for drill-down functionality
            writer.write("            fields: {\n");
            for (int i = 0; i < classInfo.fields.size(); i++) {
                SchemaAnalyzer.FieldInfo field = classInfo.fields.get(i);
                writer.write("                \"" + escapeJs(field.name) + "\": {\n");
                writer.write("                    name: \"" + escapeJs(field.name) + "\",\n");
                writer.write("                    type: \"" + escapeJs(field.type) + "\",\n");
                writer.write("                    isPrimitive: " + field.isPrimitive + ",\n");
                writer.write("                    isCollection: " + field.isCollection + ",\n");
                writer.write("                    isReference: " + field.isReference + "\n");
                writer.write("                }");

                if (i < classInfo.fields.size() - 1) {
                    writer.write(",");
                }
                writer.write("\n");
            }
            writer.write("            }\n");
            writer.write("        }");

            if (++count < allClasses.size()) {
                writer.write(",");
            }
            writer.write("\n");
        }
    }

    private void writeDatabaseData() throws IOException {
        Map<String, DatabaseAnalyzer.DatabaseClassSummary> classSummaries = databaseAnalyzer.analyzeDatabaseContent();

        writer.write("        totalClasses: " + classSummaries.size() + ",\n");
        writer.write("        classes: {\n");

        int count = 0;
        for (Map.Entry<String, DatabaseAnalyzer.DatabaseClassSummary> entry : classSummaries.entrySet()) {
            DatabaseAnalyzer.DatabaseClassSummary summary = entry.getValue();
            writer.write("            \"" + escapeJs(summary.className) + "\": {\n");
            writer.write("                name: \"" + escapeJs(summary.className) + "\",\n");
            writer.write("                shortName: \"" + escapeJs(summary.shortName) + "\",\n");
            writer.write("                totalEntryCount: " + summary.totalEntryCount + ",\n");
            writer.write("                uniqueObjectCount: " + summary.uniqueObjectCount + ",\n");
            writer.write("                reachedObjectCount: " + summary.reachedObjectCount + ",\n");
            writer.write("                unreachedObjectCount: " + summary.unreachedObjectCount + "\n");
            writer.write("            }");

            if (++count < classSummaries.size()) {
                writer.write(",");
            }
            writer.write("\n");
        }

        writer.write("        }\n");
    }

    private void writeClassDataStructure(SchemaAnalyzer.ClassInfo classInfo) throws IOException {
        writer.write("                \"" + escapeJs(classInfo.name) + "\": {\n");
        writer.write("                    name: \"" + escapeJs(classInfo.name) + "\",\n");
        writer.write("                    shortName: \"" + escapeJs(classInfo.shortName) + "\",\n");
        writer.write("                    description: \""
                + escapeJs(classInfo.description != null ? classInfo.description : "") + "\",\n");
        writer.write("                    superClass: \""
                + escapeJs(classInfo.superClass != null ? classInfo.superClass : "") + "\",\n");
        writer.write("                    fieldCount: " + classInfo.fields.size() + ",\n");

        // Field information for drill-down
        writer.write("                    fields: {\n");
        for (int i = 0; i < classInfo.fields.size(); i++) {
            SchemaAnalyzer.FieldInfo field = classInfo.fields.get(i);
            writer.write("                        \"" + escapeJs(field.name) + "\": {\n");
            writer.write("                            name: \"" + escapeJs(field.name) + "\",\n");
            writer.write("                            type: \"" + escapeJs(field.type) + "\",\n");
            writer.write("                            isPrimitive: " + field.isPrimitive + ",\n");
            writer.write("                            isCollection: " + field.isCollection + ",\n");
            writer.write("                            isReference: " + field.isReference + "\n");
            writer.write("                        }");

            if (i < classInfo.fields.size() - 1) {
                writer.write(",");
            }
            writer.write("\n");
        }
        writer.write("                    }\n");

        writer.write("                }");
    }

    private String escapeJs(String text) {
        if (text == null)
            return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    /**
     * Extract the mID field value from an ID-type object.
     * Returns the Long ID of the entity this ID object references, or null if not
     * found.
     */
    private Long extractMIdValue(DODatabaseObject idObject, Map<Long, DODatabaseObject> resolvedObjectsMap) {
        if (idObject == null) {
            return null;
        }

        // Look for a reference that might be the mID field
        // ID objects typically have one reference to a Long primitive, but since Longs
        // aren't objects,
        // we need to look at what the ID object points to through the reachability
        // system
        DOObjectReference[] refs = idObject.getReferences();
        if (refs != null && refs.length > 0) {
            // If the ID object has a reference, it might be to the target entity
            // (if the resolver added it synthetically)
            for (DOObjectReference ref : refs) {
                Long targetId = ref.getTargetObjectId();
                DODatabaseObject targetObj = resolvedObjectsMap.get(targetId);
                if (targetObj != null) {
                    // Check if the target's class name matches the expected pattern
                    // E.g., IDEmploye should point to Employe
                    String idClassName = idObject.getMostSpecificClass().getShortName();
                    String targetClassName = targetObj.getMostSpecificClass().getShortName();

                    if (idClassName.startsWith("ID") && idClassName.substring(2).equals(targetClassName)) {
                        return targetId;
                    }
                }
            }
        }

        // If no synthetic reference was added, we can't determine the target
        // (This is the current situation - the resolver processes the target but
        // doesn't link it)
        return null;
    }
}
