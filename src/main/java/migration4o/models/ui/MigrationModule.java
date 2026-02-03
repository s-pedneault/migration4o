package migration4o.models.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a migration module that groups related classes for export.
 * Modules can be hierarchical with child modules.
 */
public class MigrationModule {

    private final String name;
    private final String id;
    private final List<ClassExportConfig> classConfigs;
    private final List<MigrationModule> childModules;

    public MigrationModule(String name, String id, List<ClassExportConfig> classConfigs,
            List<MigrationModule> childModules) {
        this.name = name;
        this.id = id;
        this.classConfigs = new ArrayList<>(classConfigs);
        this.childModules = new ArrayList<>(childModules);
    }

    /**
     * Backward compatibility constructor that takes class names as strings.
     */
    public MigrationModule(String name, String id, List<String> classNames, List<MigrationModule> childModules,
            boolean isStringList) {
        this.name = name;
        this.id = id;
        this.classConfigs = classNames.stream()
                .map(ClassExportConfig::new)
                .collect(Collectors.toList());
        this.childModules = new ArrayList<>(childModules);
    }

    public MigrationModule(String name, String id, List<String> classNames) {
        this(name, id, classNames, new ArrayList<>(), true);
    }

    public MigrationModule(String name, List<ClassExportConfig> classConfigs) {
        this(name, name, classConfigs, Collections.emptyList());
    }

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }

    /**
     * Returns the list of class export configurations.
     */
    public List<ClassExportConfig> getClassConfigs() {
        return Collections.unmodifiableList(classConfigs);
    }

    /**
     * Returns class names only (backward compatibility).
     * Note: If a class appears multiple times with different configs, it will
     * appear multiple times in the list.
     */
    public List<String> getClassNames() {
        return classConfigs.stream()
                .map(ClassExportConfig::getClassName)
                .collect(Collectors.toList());
    }

    public List<MigrationModule> getChildModules() {
        return Collections.unmodifiableList(childModules);
    }

    /**
     * Recursively collects all class names from this module and its children.
     */
    public List<String> getAllClassNames() {
        List<String> allClasses = new ArrayList<>(getClassNames());
        for (MigrationModule child : childModules) {
            allClasses.addAll(child.getAllClassNames());
        }
        return allClasses;
    }

    /**
     * Recursively collects all class configurations from this module and its
     * children.
     */
    public List<ClassExportConfig> getAllClassConfigs() {
        List<ClassExportConfig> allConfigs = new ArrayList<>(classConfigs);
        for (MigrationModule child : childModules) {
            allConfigs.addAll(child.getAllClassConfigs());
        }
        return allConfigs;
    }

    @Override
    public String toString() {
        return name + " (" + classConfigs.size() + " classes, " + childModules.size() + " submodules)";
    }
}
