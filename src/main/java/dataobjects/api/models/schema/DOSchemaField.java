package dataobjects.api.models.schema;

import dataobjects.api.models.*;

public interface DOSchemaField extends DOField {

    // Returns the name of the field to use for exporting data
    public String getExportName();

}
