package migration4o.ui.panels.database_panels.migration_coverage_panel.dialogs;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import migration4o.database.DODatabaseService;

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
    // Track which IDs exist in the file
    private final Set<Long> allObjectIds = new HashSet<>();

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
    public synchronized boolean ensureDataLoaded(Runnable onLoadComplete) {
        String currentDatabase = DODatabaseService.getInstance().getCurrentDatabasePath();

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
                loadDataSync();
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

    private void loadDataSync() {
        // Clear previous data
        synchronized (this) {
            objectContents.clear();
            objectAllClasses.clear();
            allObjectIds.clear();
        }

        // Determine database folder name from database path
        String dbFolder = "default";
        String databasePath = DODatabaseService.getInstance().getCurrentDatabasePath();
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
                                    objectContents.put(containerId, containedIds);
                                    objectAllClasses.computeIfAbsent(containerId, k -> new HashSet<>())
                                            .add(actualClassName);
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
}
