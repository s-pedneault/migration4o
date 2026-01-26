package migration4o.models.ui;

/**
 * Model class representing a module node in the migration structure.
 * Contains module metadata (name and ID).
 */
public class ModuleNode {
    private String name;
    private String id;

    public ModuleNode(String name, String id) {
        this.name = name;
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return name + " [" + id + "]";
    }
}
