package migration4o.ui.panels.database_panels.migration_coverage_panel.dialogs;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import migration4o.database.DODatabaseService;
import migration4o.migration.monitoring.ExportStatistics;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;

/**
 * Singleton service for managing object ID data used by the ID Tracer.
 * Loads the all-object-ids.txt file once and keeps it in memory for fast
 * access.
 */
public class IDTracerDataService {
    private static IDTracerDataService instance;

    // Maps object ID -> list of IDs it contains
    private final Map<Long, List<Long>> objectContents = new HashMap<>();
    // Maps object ID -> ALL class names (for inheritance hierarchy)
    private final Map<Long, Set<String>> objectAllClasses = new HashMap<>();
    // Reverse index: class name -> object IDs seen as that class layer
    private final Map<String, Set<Long>> classToObjectIds = new HashMap<>();
    // Track which IDs exist in the file
    private final Set<Long> allObjectIds = new HashSet<>();
    // Track IDs currently marked as reached by coverage/export tracking
    private final Set<Long> reachedObjectIds = new HashSet<>();
    // Diagnostics from latest export run (best effort, in-memory)
    private final Map<Long, Set<String>> objectDecisionNotes = new HashMap<>();
    private final Map<String, Set<String>> exportedRelationshipNotes = new HashMap<>();
    private final Map<String, Set<String>> skippedRelationshipNotes = new HashMap<>();

    private boolean isLoaded = false;
    private boolean isLoading = false;
    private String loadedForDatabase = null;
    private final List<Runnable> loadCompletionCallbacks = new ArrayList<>();

    private IDTracerDataService() {
        // Private constructor for singleton
    }

    public static synchronized IDTracerDataService getInstance() {
        if (instance == null) {
            instance = new IDTracerDataService();
        }
        return instance;
    }

    /**
     * Loads or reloads the object IDs file if needed.
     * Returns true if data is ready, false if loading is in progress.
     */
    public synchronized boolean ensureDataLoaded(Runnable onLoadComplete, migration4o.database.DODatabaseContext dbContext) {
        String currentDatabase = dbContext != null ? dbContext.databaseFilePath : null;

        // Check if we need to reload (different database or not loaded yet)
        if (isLoaded && Objects.equals(currentDatabase, loadedForDatabase)) {
            // Data already loaded for this database
            if (onLoadComplete != null) {
                onLoadComplete.run();
            }
            return true;
        }

        // If already loading, just add callback
        if (isLoading) {
            if (onLoadComplete != null) {
                loadCompletionCallbacks.add(onLoadComplete);
            }
            return false;
        }

        // Need to load data
        isLoading = true;
        isLoaded = false;
        if (onLoadComplete != null) {
            loadCompletionCallbacks.add(onLoadComplete);
        }

        // Start loading in background thread
        new Thread(() -> {
            try {
                loadDataSync(dbContext);
                synchronized (this) {
                    isLoaded = true;
                    isLoading = false;
                    loadedForDatabase = currentDatabase;

                    // Execute all callbacks
                    List<Runnable> callbacks = new ArrayList<>(loadCompletionCallbacks);
                    loadCompletionCallbacks.clear();

                    for (Runnable callback : callbacks) {
                        try {
                            callback.run();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (Exception e) {
                synchronized (this) {
                    isLoading = false;
                    loadCompletionCallbacks.clear();
                }
                e.printStackTrace();
            }
        }).start();

        return false;
    }

    private void loadDataSync(migration4o.database.DODatabaseContext dbContext) {
        // Clear previous data
        synchronized (this) {
            objectContents.clear();
            objectAllClasses.clear();
            classToObjectIds.clear();
            allObjectIds.clear();
            reachedObjectIds.clear();
        }

        // Determine database folder name from database path
        String dbFolder = "default";
        String databasePath = dbContext != null ? dbContext.databaseFilePath : null;
        if (databasePath != null) {
            Path dbPath = Paths.get(databasePath);
            Path parent = dbPath.getParent();
            if (parent != null) {
                dbFolder = parent.getFileName().toString();
            }
        }

        Path idsFile = Paths.get("output").resolve(dbFolder).resolve("all-object-ids.txt");

        if (!Files.exists(idsFile)) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(idsFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split("\t");
                if (parts.length < 2) {
                    continue;
                }

                String className = parts[0];

                // Check if this is a class listing (format: ClassName\tid1\tid2\tid3...)
                // or a collection/field listing (format: ClassName[objectId]\tid1\tid2...)
                if (className.contains("[")) {
                    // Collection or field object listing
                    int bracketStart = className.indexOf('[');
                    int bracketEnd = className.indexOf(']');
                    if (bracketStart > 0 && bracketEnd > bracketStart) {
                        String actualClassName = className.substring(0, bracketStart);
                        String objectIdStr = className.substring(bracketStart + 1, bracketEnd);

                        try {
                            long containerId = Long.parseLong(objectIdStr);
                            List<Long> containedIds = new ArrayList<>();

                            // Parse all contained IDs
                            for (int i = 1; i < parts.length; i++) {
                                try {
                                    long id = Long.parseLong(parts[i].trim());
                                    containedIds.add(id);
                                    synchronized (this) {
                                        allObjectIds.add(id);
                                    }
                                } catch (NumberFormatException e) {
                                    // Skip invalid IDs
                                }
                            }

                            if (!containedIds.isEmpty()) {
                                synchronized (this) {
                                    List<Long> existing = objectContents.computeIfAbsent(containerId, key -> new ArrayList<>());
                                    Set<Long> merged = new LinkedHashSet<>(existing);
                                    merged.addAll(containedIds);
                                    existing.clear();
                                    existing.addAll(merged);

                                    objectAllClasses.computeIfAbsent(containerId, k -> new HashSet<>()).add(actualClassName);
                                    classToObjectIds.computeIfAbsent(actualClassName, key -> new LinkedHashSet<>()).add(containerId);
                                    allObjectIds.add(containerId);
                                }
                            }

                        } catch (NumberFormatException e) {
                            // Skip invalid container ID
                        }
                    }
                } else {
                    // Regular class listing - just track the IDs as existing
                    for (int i = 1; i < parts.length; i++) {
                        try {
                            long id = Long.parseLong(parts[i].trim());
                            synchronized (this) {
                                allObjectIds.add(id);
                                objectAllClasses.computeIfAbsent(id, k -> new HashSet<>()).add(className);
                                classToObjectIds.computeIfAbsent(className, key -> new LinkedHashSet<>()).add(id);
                            }
                        } catch (NumberFormatException e) {
                            // Skip invalid IDs
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        enrichFromDatabaseSchema(dbContext);
    }

    /**
     * Enriches ID->class mappings from the in-memory database schema.
     * This ensures type resolution is available even when an ID only appears as a
     * contained ID in all-object-ids.txt.
     */
    private void enrichFromDatabaseSchema(migration4o.database.DODatabaseContext dbContext) {
        DOSchema databaseSchema = dbContext != null ? dbContext.databaseSchema : null;
        if (databaseSchema == null || databaseSchema.getClasses() == null) {
            return;
        }

        synchronized (this) {
            for (DOSchemaClass schemaClass : databaseSchema.getClasses()) {
                if (schemaClass == null || schemaClass.source == null) {
                    continue;
                }

                if (schemaClass.objectIds != null) {
                    for (long objectId : schemaClass.objectIds) {
                        allObjectIds.add(objectId);
                        objectAllClasses.computeIfAbsent(objectId, key -> new HashSet<>()).add(schemaClass.source);
                        classToObjectIds.computeIfAbsent(schemaClass.source, key -> new LinkedHashSet<>()).add(objectId);
                    }
                }

                if (schemaClass.reachedObjectIds != null) {
                    for (long reachedId : schemaClass.reachedObjectIds) {
                        reachedObjectIds.add(reachedId);
                    }
                }
            }
        }
    }

    public synchronized boolean isLoaded() {
        return isLoaded;
    }

    public synchronized boolean isLoading() {
        return isLoading;
    }

    public synchronized int getObjectCount() {
        return allObjectIds.size();
    }

    public synchronized boolean containsObjectId(long objectId) {
        return allObjectIds.contains(objectId);
    }

    public synchronized void setLatestExportDiagnostics(ExportStatistics statistics, migration4o.database.DODatabaseContext dbContext) {
        refreshReachedFromSchema(dbContext);

        objectDecisionNotes.clear();
        exportedRelationshipNotes.clear();
        skippedRelationshipNotes.clear();

        if (statistics == null) {
            return;
        }

        for (Map.Entry<Long, Set<String>> entry : statistics.objectDecisionNotes.entrySet()) {
            objectDecisionNotes.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }

        for (Map.Entry<String, Set<String>> entry : statistics.exportedRelationshipNotes.entrySet()) {
            exportedRelationshipNotes.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }

        for (Map.Entry<String, Set<String>> entry : statistics.skippedRelationshipNotes.entrySet()) {
            skippedRelationshipNotes.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }
    }

    private void refreshReachedFromSchema(migration4o.database.DODatabaseContext dbContext) {
        reachedObjectIds.clear();
        DOSchema databaseSchema = dbContext != null ? dbContext.databaseSchema : null;
        if (databaseSchema == null || databaseSchema.getClasses() == null) {
            return;
        }

        for (DOSchemaClass schemaClass : databaseSchema.getClasses()) {
            if (schemaClass == null || schemaClass.reachedObjectIds == null) {
                continue;
            }
            for (long reachedId : schemaClass.reachedObjectIds) {
                reachedObjectIds.add(reachedId);
            }
        }
    }

    public synchronized Map<String, Integer> getSkippedButReachedReasonCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();

        for (Map.Entry<String, Set<String>> entry : skippedRelationshipNotes.entrySet()) {
            Long childId = extractChildId(entry.getKey());
            if (childId == null || !reachedObjectIds.contains(childId)) {
                continue;
            }

            for (String note : entry.getValue()) {
                String reason = extractReason(note);
                counts.merge(reason, 1, Integer::sum);
            }
        }

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        Map<String, Integer> ordered = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : sorted) {
            ordered.put(entry.getKey(), entry.getValue());
        }
        return ordered;
    }

    public synchronized Map<String, String> getSkippedButReachedReasonExamples(int maxExamplesPerReason) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();

        for (Map.Entry<String, Set<String>> entry : skippedRelationshipNotes.entrySet()) {
            Long childId = extractChildId(entry.getKey());
            if (childId == null || !reachedObjectIds.contains(childId)) {
                continue;
            }

            for (String note : entry.getValue()) {
                String reason = extractReason(note);
                grouped.computeIfAbsent(reason, ignored -> new ArrayList<>()).add(entry.getKey() + " | " + note);
            }
        }

        Map<String, String> examples = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
            List<String> values = entry.getValue();
            String joined = values.stream().limit(Math.max(1, maxExamplesPerReason)).reduce((a, b) -> a + " ; " + b).orElse("");
            examples.put(entry.getKey(), joined);
        }
        return examples;
    }

    private Long extractChildId(String edgeKey) {
        if (edgeKey == null) {
            return null;
        }
        int sep = edgeKey.indexOf("->");
        if (sep < 0 || sep + 2 >= edgeKey.length()) {
            return null;
        }
        try {
            return Long.parseLong(edgeKey.substring(sep + 2));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String extractReason(String note) {
        if (note == null || note.isBlank()) {
            return "unknown";
        }
        int arrow = note.indexOf(" → ");
        if (arrow >= 0 && arrow + 3 < note.length()) {
            return note.substring(arrow + 3).trim();
        }
        return note.trim();
    }

    public synchronized boolean isReachedObjectId(long objectId) {
        return reachedObjectIds.contains(objectId);
    }

    public synchronized Set<String> getObjectDecisionNotes(long objectId) {
        Set<String> notes = objectDecisionNotes.get(objectId);
        return notes != null ? new LinkedHashSet<>(notes) : Collections.emptySet();
    }

    public synchronized boolean isRelationshipExported(long parentObjectId, long childObjectId) {
        return exportedRelationshipNotes.containsKey(ExportStatistics.edgeKey(parentObjectId, childObjectId));
    }

    public synchronized Set<String> getExportedRelationshipNotes(long parentObjectId, long childObjectId) {
        Set<String> notes = exportedRelationshipNotes.get(ExportStatistics.edgeKey(parentObjectId, childObjectId));
        return notes != null ? new LinkedHashSet<>(notes) : Collections.emptySet();
    }

    public synchronized Set<String> getSkippedRelationshipNotes(long parentObjectId, long childObjectId) {
        Set<String> notes = skippedRelationshipNotes.get(ExportStatistics.edgeKey(parentObjectId, childObjectId));
        return notes != null ? new LinkedHashSet<>(notes) : Collections.emptySet();
    }

    /**
     * Gets all class names for an object ID (due to inheritance, same ID can appear
     * as multiple types).
     */
    public synchronized Set<String> getAllClassNames(long objectId) {
        Set<String> classes = objectAllClasses.get(objectId);
        return classes != null ? new HashSet<>(classes) : new HashSet<>();
    }

    /**
     * Gets the leaf (most derived) class name for an object ID.
     * Uses the reference schema to determine inheritance hierarchy.
     */
    public synchronized String getLeafClassName(long objectId) {
        Set<String> classes = objectAllClasses.get(objectId);
        if (classes == null || classes.isEmpty()) {
            return "Unknown";
        }
        if (classes.size() == 1) {
            return classes.iterator().next();
        }
        // Multiple classes - find the most derived one using schema
        return migration4o.util.SchemaUtil.findLeafClass(classes);
    }

    public synchronized List<Long> getContainedIds(long containerId) {
        return objectContents.get(containerId);
    }

    public synchronized List<Long> findDirectContainers(long targetId) {
        List<Long> containers = new ArrayList<>();
        for (Map.Entry<Long, List<Long>> entry : objectContents.entrySet()) {
            if (entry.getValue().contains(targetId)) {
                containers.add(entry.getKey());
            }
        }
        return containers;
    }

    public static class ClassContainmentSample {
        public final long parentObjectId;
        public final long childObjectId;

        public ClassContainmentSample(long parentObjectId, long childObjectId) {
            this.parentObjectId = parentObjectId;
            this.childObjectId = childObjectId;
        }
    }

    public synchronized Set<Long> getObjectIdsForClass(String className) {
        Set<Long> ids = classToObjectIds.get(className);
        return ids != null ? new LinkedHashSet<>(ids) : Collections.emptySet();
    }

    public synchronized ClassContainmentSample findContainmentSampleForClasses(String parentClassName, String childClassName) {
        if (parentClassName == null || childClassName == null || parentClassName.isBlank() || childClassName.isBlank()) {
            return null;
        }

        Set<Long> parentIds = classToObjectIds.get(parentClassName);
        if (parentIds == null || parentIds.isEmpty()) {
            return null;
        }

        for (Long parentId : parentIds) {
            List<Long> contained = objectContents.get(parentId);
            if (contained == null || contained.isEmpty()) {
                continue;
            }

            for (Long childId : contained) {
                Set<String> childClasses = objectAllClasses.get(childId);
                if (childClasses != null && childClasses.contains(childClassName)) {
                    return new ClassContainmentSample(parentId, childId);
                }
            }
        }

        return null;
    }
}
