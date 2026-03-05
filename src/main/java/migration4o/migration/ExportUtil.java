package migration4o.migration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import migration4o.migration.monitoring.ExportStatistics;
import migration4o.migration.monitoring.ExportWarning;
import migration4o.migration.monitoring.ReferencedClassTracker;
import migration4o.models.ui.ClassExportConfig;
import migration4o.models.ui.MigrationModule;
import migration4o.schema.modules.DOModuleService;

public class ExportUtil {

    private static final int MAX_OBJECT_DECISION_OBJECTS = Integer.getInteger("migration4o.export.maxObjectDecisionObjects", 200_000);
    private static final int MAX_RELATIONSHIP_EDGES = Integer.getInteger("migration4o.export.maxRelationshipEdges", 500_000);
    private static final int MAX_NOTES_PER_RELATIONSHIP_EDGE = Integer.getInteger("migration4o.export.maxNotesPerRelationshipEdge", 8);

    public static ExportStatistics combineResults(List<ExportStatistics> results, String outputPath) {
        List<ExportStatistics.ExportError> allErrors = new ArrayList<>();
        List<ExportWarning> allWarnings = new ArrayList<>();
        Map<String, Set<Long>> allObjectIdsSet = new HashMap<>();
        int totalObjectsAttempted = 0;
        int totalObjectsSucceeded = 0;

        for (ExportStatistics result : results) {
            allErrors.addAll(result.errors);
            allWarnings.addAll(result.schemaWarnings);

            if (result.exportedObjectIds != null) {
                for (Map.Entry<String, List<Long>> entry : result.exportedObjectIds.entrySet()) {
                    allObjectIdsSet.computeIfAbsent(entry.getKey(), k -> new HashSet<>()).addAll(entry.getValue());
                }
            }

            totalObjectsAttempted += result.objectsAttempted;
            totalObjectsSucceeded += result.objectsSucceeded;
        }

        Map<String, List<Long>> allObjectIds = new HashMap<>();
        Map<String, Integer> allClassCounts = new HashMap<>();
        for (Map.Entry<String, Set<Long>> entry : allObjectIdsSet.entrySet()) {
            List<Long> uniqueList = new ArrayList<>(entry.getValue());
            allObjectIds.put(entry.getKey(), uniqueList);
            allClassCounts.put(entry.getKey(), uniqueList.size());
        }

        ExportStatistics result = new ExportStatistics();
        result.exportName = "Bulk Export";
        result.outputPath = outputPath;
        result.objectsAttempted = totalObjectsAttempted;
        result.objectsSucceeded = totalObjectsSucceeded;
        result.objectsFiltered = 0;
        result.errors.addAll(allErrors);
        result.schemaWarnings.addAll(allWarnings);
        result.exportedClassCounts.putAll(allClassCounts);
        result.exportedObjectIds.putAll(allObjectIds);

        boolean objectDecisionNotesTruncated = false;
        boolean exportedRelationshipNotesTruncated = false;
        boolean skippedRelationshipNotesTruncated = false;

        for (ExportStatistics moduleResult : results) {
            for (Map.Entry<Long, Set<String>> entry : moduleResult.objectDecisionNotes.entrySet()) {
                if (!result.objectDecisionNotes.containsKey(entry.getKey()) && result.objectDecisionNotes.size() >= MAX_OBJECT_DECISION_OBJECTS) {
                    objectDecisionNotesTruncated = true;
                    continue;
                }

                Set<String> targetNotes = result.objectDecisionNotes.computeIfAbsent(entry.getKey(), key -> new java.util.LinkedHashSet<>());
                for (String note : entry.getValue()) {
                    if (note == null) {
                        continue;
                    }
                    targetNotes.add(note);
                }
            }

            for (Map.Entry<String, Set<String>> entry : moduleResult.exportedRelationshipNotes.entrySet()) {
                if (!result.exportedRelationshipNotes.containsKey(entry.getKey()) && result.exportedRelationshipNotes.size() >= MAX_RELATIONSHIP_EDGES) {
                    exportedRelationshipNotesTruncated = true;
                    continue;
                }

                Set<String> targetNotes = result.exportedRelationshipNotes.computeIfAbsent(entry.getKey(), key -> new java.util.LinkedHashSet<>());
                for (String note : entry.getValue()) {
                    if (note == null) {
                        continue;
                    }
                    if (targetNotes.size() >= MAX_NOTES_PER_RELATIONSHIP_EDGE) {
                        exportedRelationshipNotesTruncated = true;
                        break;
                    }
                    targetNotes.add(note);
                }
            }

            for (Map.Entry<String, Set<String>> entry : moduleResult.skippedRelationshipNotes.entrySet()) {
                if (!result.skippedRelationshipNotes.containsKey(entry.getKey()) && result.skippedRelationshipNotes.size() >= MAX_RELATIONSHIP_EDGES) {
                    skippedRelationshipNotesTruncated = true;
                    continue;
                }

                Set<String> targetNotes = result.skippedRelationshipNotes.computeIfAbsent(entry.getKey(), key -> new java.util.LinkedHashSet<>());
                for (String note : entry.getValue()) {
                    if (note == null) {
                        continue;
                    }
                    if (targetNotes.size() >= MAX_NOTES_PER_RELATIONSHIP_EDGE) {
                        skippedRelationshipNotesTruncated = true;
                        break;
                    }
                    targetNotes.add(note);
                }
            }
        }

        if (objectDecisionNotesTruncated) {
            System.out.println("WARN: Export diagnostics truncated (objectDecisionNotes cap=" + MAX_OBJECT_DECISION_OBJECTS + ")");
        }
        if (exportedRelationshipNotesTruncated) {
            System.out.println("WARN: Export diagnostics truncated (exportedRelationshipNotes edge cap=" + MAX_RELATIONSHIP_EDGES + ", per-edge cap=" + MAX_NOTES_PER_RELATIONSHIP_EDGE + ")");
        }
        if (skippedRelationshipNotesTruncated) {
            System.out.println("WARN: Export diagnostics truncated (skippedRelationshipNotes edge cap=" + MAX_RELATIONSHIP_EDGES + ", per-edge cap=" + MAX_NOTES_PER_RELATIONSHIP_EDGE + ")");
        }

        return result;
    }

    public static void registerAllModuleClasses(MigrationModule module, ReferencedClassTracker tracker) {
        Set<String> classNames = new HashSet<>(module.getClassNames());
        tracker.registerModule(module.getName(), classNames);

        for (MigrationModule childModule : module.getChildModules()) {
            registerAllModuleClasses(childModule, tracker);
        }
    }

    public static ClassExportConfig findClassConfig(String className) {
        List<MigrationModule> modules = DOModuleService.getInstance().getModules();
        for (MigrationModule module : modules) {
            ClassExportConfig config = findClassConfigInModule(module, className);
            if (config != null) {
                return config;
            }
        }
        return null;
    }

    public static ClassExportConfig findClassConfigInModule(MigrationModule module, String className) {
        for (ClassExportConfig config : module.getClassConfigs()) {
            if (config.getClassName().equals(className)) {
                return config;
            }
        }

        for (MigrationModule childModule : module.getChildModules()) {
            ClassExportConfig config = findClassConfigInModule(childModule, className);
            if (config != null) {
                return config;
            }
        }

        return null;
    }

    public static MigrationModule findModuleByName(String moduleName) throws Exception {
        List<MigrationModule> modules = DOModuleService.getInstance().loadModuleStructure("schema/migration-format.xml");
        return findModuleRecursive(modules, moduleName);
    }

    public static MigrationModule findModuleRecursive(List<MigrationModule> modules, String moduleName) {
        for (MigrationModule module : modules) {
            if (module.getName().equals(moduleName)) {
                return module;
            }
            MigrationModule found = findModuleRecursive(module.getChildModules(), moduleName);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Finds the full hierarchical path for a module by name.
     * For example, if "Intervention" is under "Activités", returns "Activités/Intervention"
     * 
     * @param moduleName the name of the module to find
     * @return the full path from root to this module, or just the module name if not found in hierarchy
     * @throws Exception if module structure cannot be loaded
     */
    public static String findModulePathByName(String moduleName) throws Exception {
        List<MigrationModule> modules = DOModuleService.getInstance().loadModuleStructure("schema/migration-format.xml");
        String path = findModulePathRecursive(modules, moduleName, "");
        return path != null ? path : moduleName; // Fallback to module name if not found
    }

    /**
     * Recursively searches for a module and builds its full path.
     * 
     * @param modules the list of modules to search
     * @param moduleName the name of the module to find
     * @param parentPath the path accumulated from parent modules
     * @return the full path if found, null otherwise
     */
    private static String findModulePathRecursive(List<MigrationModule> modules, String moduleName, String parentPath) {
        for (MigrationModule module : modules) {
            // Use module ID for folder name (same logic as ExportEngine.moduleId())
            String folderName = (module.getId() != null && !module.getId().isBlank()) ? module.getId() : module.getName();
            String currentPath = parentPath.isEmpty() ? folderName : parentPath + "/" + folderName;

            if (module.getName().equals(moduleName)) {
                return currentPath;
            }

            String found = findModulePathRecursive(module.getChildModules(), moduleName, currentPath);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
