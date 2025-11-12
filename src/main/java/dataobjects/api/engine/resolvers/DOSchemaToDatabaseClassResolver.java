package dataobjects.api.engine.resolvers;

import dataobjects.api.models.database.DODatabase;
import dataobjects.api.engine.DOEngine;
import dataobjects.api.models.schema.DOSchemaClass;

public interface DOSchemaToDatabaseClassResolver {

    public void resolveReferences(DOSchemaClass cl, DOEngine engine);

}
