package dataobjects.api.engine.resolvers;

import dataobjects.api.models.DOClass;
import dataobjects.api.engine.DOEngine;

public interface DOReferenceResolver {

    public void resolveReferences(DOClass cl, DOEngine engine);

}
