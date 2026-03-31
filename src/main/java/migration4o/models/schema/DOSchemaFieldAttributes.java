package migration4o.models.schema;

import java.util.List;

public class DOSchemaFieldAttributes {

    public String source;
    public String destinationName;
    public String type;
    public String format;
    public boolean isExported;
    public String skipWhen; // Comma-separated skip conditions (NULL,ZERO,MINUS_ONE,etc.)
    public String skipUserOption;
    public boolean isCollection;
    public boolean embedContents;
    public String childrenType;
    public String title;
    public String description;
    public String pointsTo;
    public DOSchemaValueMap valueMap; // Maps database values to export values
    public String definitionId; // Shared field definition support. If set, this field references a shared definition
    public String group; // Semantic group for auto-layout (e.g. "identity", "dates", "status", "text")

    public List<DOFieldCriteria> criterias; // Virtual field support (source starts with @). Query criterias for virtual fields
    public String criteriasOperator; // Logical operator for multiple criterias: "AND" or "OR" (default: "AND")

}
