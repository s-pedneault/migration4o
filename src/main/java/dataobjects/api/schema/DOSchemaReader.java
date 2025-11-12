package dataobjects.api.schema;

import dataobjects.api.models.schema.DOSchema;

public interface DOSchemaReader {

    public DOSchema readSchema(String filePath);

}
