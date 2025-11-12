package dataobjects.api.engine.resolvers;

import dataobjects.api.models.DOClass;
import dataobjects.api.models.database.DODatabase;
import dataobjects.api.models.database.DODatabaseClass;
import dataobjects.api.engine.DOEngine;
import dataobjects.api.models.schema.DOSchema;
import dataobjects.api.models.schema.DOSchemaClass;
import com.db4o.reflect.generic.*;

public interface DOGenericObjectResolver {

    DOSchemaClass resolveClass(GenericObject genericObject, DOSchema schema);

    DODatabaseClass resolveClass(GenericObject genericObject, DODatabase database);

    DOClass resolveClass(GenericObject genericObject, DOEngine engine);

}
