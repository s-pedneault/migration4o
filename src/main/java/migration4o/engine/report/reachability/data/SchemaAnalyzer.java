package migration4o.engine.report.reachability.data;

import migration4o.engine.DOEngine;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaModule;
import migration4o.models.database.DODatabaseClass;
import migration4o.models.database.DODatabaseField;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Analyzes schema structure for reachability report
 */
public class SchemaAnalyzer {

    public static class ModuleInfo {
        public String name;
        public List<ClassInfo> classes = new ArrayList<>();

        public ModuleInfo(String name) {
            this.name = name;
        }
    }

    public static class ClassInfo {
        public String name;
        public String shortName;
        public String description;
        public String superClass;
        public List<FieldInfo> fields = new ArrayList<>();
        public String moduleName; // null for foundation classes

        public ClassInfo(String name) {
            this.name = name;
            this.shortName = name.substring(name.lastIndexOf('.') + 1);
        }
    }

    public static class FieldInfo {
        public String name;
        public String type;
        public boolean isPrimitive;
        public boolean isCollection;
        public boolean isId;
        public boolean isReference;

        public FieldInfo(String name, String type) {
            this.name = name;
            this.type = type;
            analyzeFieldType();
        }

        private void analyzeFieldType() {
            // Determine field characteristics based on type
            isPrimitive = isPrimitiveType(type);
            isCollection = isCollectionType(type);
            isId = isIdType(name, type);
            isReference = !isPrimitive && !isCollection && !isId;
        }

        private boolean isPrimitiveType(String type) {
            return type.equals("int") || type.equals("long") || type.equals("double") ||
                    type.equals("float") || type.equals("boolean") || type.equals("byte") ||
                    type.equals("short") || type.equals("char") ||
                    type.equals("java.lang.String") || type.equals("java.util.Date") ||
                    type.endsWith("[]") && isPrimitiveType(type.substring(0, type.length() - 2));
        }

        private boolean isCollectionType(String type) {
            return type.startsWith("java.util.Vector") || type.startsWith("java.util.List") ||
                    type.startsWith("java.util.Set") || type.startsWith("java.util.Collection") ||
                    type.contains("[]") || type.contains("VectRechID");
        }

        private boolean isIdType(String fieldName, String type) {
            return fieldName.startsWith("mID") || fieldName.equals("mId") ||
                    type.contains(".ID") || fieldName.toLowerCase().contains("id");
        }
    }

    private DOEngine engine;

    public SchemaAnalyzer(DOEngine engine) {
        this.engine = engine;
    }

    public List<ModuleInfo> analyzeModules() {
        List<ModuleInfo> modules = new ArrayList<>();
        DOSchema schema = engine.getSchema();
        DOSchemaModule[] schemaModules = schema.getModules();

        for (DOSchemaModule module : schemaModules) {
            ModuleInfo moduleInfo = new ModuleInfo(module.getName());

            DOSchemaClass[] classes = module.getClasses();
            for (DOSchemaClass schemaClass : classes) {
                ClassInfo classInfo = createClassInfo(schemaClass);
                classInfo.moduleName = module.getName();
                moduleInfo.classes.add(classInfo);
            }

            modules.add(moduleInfo);
        }

        return modules;
    }

    public Map<String, ClassInfo> analyzeAllClasses() {
        Map<String, ClassInfo> allClasses = new HashMap<>();
        DOSchema schema = engine.getSchema();
        DOSchemaClass[] classes = schema.getClasses();

        for (DOSchemaClass schemaClass : classes) {
            ClassInfo classInfo = createClassInfo(schemaClass);

            // Find which module this class belongs to (if any)
            classInfo.moduleName = findModuleForClass(schemaClass.getAbsoluteName());

            allClasses.put(schemaClass.getAbsoluteName(), classInfo);
        }

        return allClasses;
    }

    private ClassInfo createClassInfo(DOSchemaClass schemaClass) {
        ClassInfo classInfo = new ClassInfo(schemaClass.getAbsoluteName());
        classInfo.description = schemaClass.getDescription();
        classInfo.superClass = schemaClass.getSuperClassAbsoluteName();

        // Analyze fields
        DODatabaseClass dbClass = schemaClass.getDatabaseClass();
        DODatabaseField[] fields = dbClass != null ? dbClass.getFields() : null;
        if (fields != null) {
            for (DODatabaseField field : fields) {
                FieldInfo fieldInfo = new FieldInfo(field.getName(), field.getTypeName());
                classInfo.fields.add(fieldInfo);
            }
        }

        return classInfo;
    }

    private String findModuleForClass(String className) {
        DOSchema schema = engine.getSchema();
        DOSchemaModule[] modules = schema.getModules();

        for (DOSchemaModule module : modules) {
            DOSchemaClass[] classes = module.getClasses();
            for (DOSchemaClass schemaClass : classes) {
                if (className.equals(schemaClass.getAbsoluteName())) {
                    return module.getName();
                }
            }
        }
        return null; // This means it's a foundation class
    }
}