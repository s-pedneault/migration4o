package dataobjects.api.engine.resolvers;

import dataobjects.api.engine.DOEngine;

public interface DOFieldResolver {

    /**
     * Resolves field type names to actual DOClass objects for all fields in the
     * engine.
     * This includes both direct field types and array content types.
     */
    public void resolveFieldTypes(DOEngine engine);

}
