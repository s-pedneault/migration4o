package dataobjects.api.models;

public interface DOField {

    // Returns the name of the field, as used in the database
    public String getName();

    // A brief description of what the field contains
    public String getDescription();

    // The absolute class name of the field's value.
    // If this field is an array, this is the type of the array itself;
    // and getContentsType() returns the type of the array's content.
    public String getTypeName();

    // The resolved class of the field's value.
    public DOClass getTypeClass();

    public boolean isArray();

    // Returns true if this field's type is a primitive, either a Java primitive or
    // declared as a primitive in the schema.
    public boolean isPrimitive();

    // The absolute class name of the array's content, if isArray() is true.
    // Returns null if this field is not a collection.
    public String getContentTypeName();

    // The resolved class of the array's content, if isArray() is true.
    public DOClass getContentTypeClass();

}