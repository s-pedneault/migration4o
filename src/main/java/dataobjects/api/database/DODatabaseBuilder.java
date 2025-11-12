package dataobjects.api.database;

import dataobjects.api.models.schema.DOSchema;
import dataobjects.api.models.database.DODatabase;

// Specialized class that builds DODatabase instance.
public interface DODatabaseBuilder {

    public DODatabase buildDatabase(String filePath, DOSchema schema);

}
