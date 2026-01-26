package migration4o.models.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Data class representing a migration module with nested structure.
 * A module can contain classes and child modules.
 */
public class MigrationModule {
    private final String name;
    private final String id;
    private final List<String> classNames;
    private final List<MigrationModule> childModules;

    public MigrationModule(String name, String id, List<String> classNames) {
        this(name, id, classNames, new ArrayList<>());
    }

    public MigrationModule(String name, String id, List<String> classNames, List<MigrationModule> childModules) {
        this.name = name;
        this.id = id;
        this.classNames = classNames;
        this.childModules = childModules;
    }

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }

    public List<String> getClassNames() {
        return classNames;
    }

    public List<MigrationModule> getChildModules() {
        return childModules;
    }
}
