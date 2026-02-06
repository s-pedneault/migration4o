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
                    allObjectIdsSet.computeIfAbsent(entry.getKey(), k -> new HashSet<>())
                            .addAll(entry.getValue());
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
        List<MigrationModule> modules = DOModuleService.getInstance()
                .loadModuleStructure("schema/migration-format.xml");
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
}
