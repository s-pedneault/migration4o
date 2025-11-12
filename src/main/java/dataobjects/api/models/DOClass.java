package dataobjects.api.models;

public interface DOClass {

    // The class's full name, including packages (ex: java.lang.String)
    public String getAbsoluteName();

    // The class's short name (ex: String)
    public String getShortName();

    // The class's description
    public String getDescription();

    // The class's title
    public String getTitle();

    // The class's superclass's full class name, including packages.
    public String getSuperClassAbsoluteName();

    // The list of fields
    public DOField[] getFields();

    public DOReference[] getReferences();

    // Set the references array (used after all classes are loaded)
    public void setReferences(DOReference[] references);

    // Add a single reference (used after all classes are loaded)
    public void addReference(DOReference reference);

}
