package migration4o.models.ui;

import migration4o.models.schema.DOSchemaClass;

import java.util.ArrayList;
import java.util.List;

/**
 * Data class to hold categorized schema classes for tree population.
 * Separates classes into Entities, Params, and Others categories,
 * and further separates them by exported vs available status.
 */
public class CategorizedClasses {
    public final List<DOSchemaClass> availableEntities = new ArrayList<>();
    public final List<DOSchemaClass> availableParams = new ArrayList<>();
    public final List<DOSchemaClass> availableOthers = new ArrayList<>();
    public final List<DOSchemaClass> exportedEntities = new ArrayList<>();
    public final List<DOSchemaClass> exportedParams = new ArrayList<>();
    public final List<DOSchemaClass> exportedOthers = new ArrayList<>();
}
