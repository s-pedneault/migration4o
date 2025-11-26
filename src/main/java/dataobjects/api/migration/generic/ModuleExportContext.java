package dataobjects.api.migration.generic;

import dataobjects.api.engine.DOEngine;
import dataobjects.api.models.schema.DOSchemaModule;

/**
 * Context for module-level export operations.
 * Contains all information needed for exporting a complete module.
 */
public class ModuleExportContext extends ExportContext {
    private final DOEngine engine;
    private final DOSchemaModule module;
    private final String moduleName;
    private final String sanitizedModuleName;

    public ModuleExportContext(String outputDirectory, DOEngine engine, DOSchemaModule module,
            String sanitizedModuleName) {
        super(outputDirectory);
        this.engine = engine;
        this.module = module;
        this.moduleName = module.getName();
        this.sanitizedModuleName = sanitizedModuleName;
    }

    public DOEngine getEngine() {
        return engine;
    }

    public DOSchemaModule getModule() {
        return module;
    }

    public String getModuleName() {
        return moduleName;
    }

    public String getSanitizedModuleName() {
        return sanitizedModuleName;
    }
}