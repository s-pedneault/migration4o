# Generic Export Engine - Improvement Proposal

## Current Problems

1. **Complex Context Management**: Manual Object context passing is error-prone
2. **Mixed Responsibilities**: Format handlers do both output AND value formatting
3. **Excel-Centric Design**: Interface assumes tabular data, making XML awkward
4. **Duplicate Logic**: Each handler re-implements value formatting
5. **Type Safety**: Object contexts provide no compile-time safety

## Proposed Improvements

### 1. Typed Context Classes

Replace Object contexts with proper typed classes:

```java
public abstract class ModuleExportContext {
    protected final DOSchemaModule module;
    protected final String outputPath;
    // Common module-level state
}

public abstract class ClassExportContext {
    protected final ModuleExportContext moduleContext;
    protected final DOSchemaClass schemaClass;
    protected final DODatabaseClass dbClass;
    protected final List<ExportColumn> columns;
    // Common class-level state
}
```

### 2. Separate Value Processing from Output

Move value formatting out of format handlers into the engine:

```java
public class FormattedValue {
    private final Object rawValue;
    private final ExportColumn column;
    private final String stringValue;
    private final ValueType type;
    
    // Pre-formatted values ready for output
}
```

### 3. Format-Specific Base Classes

Provide base implementations for common patterns:

```java
// For tabular formats (Excel, CSV)
public abstract class TabularFormatHandler implements ExportFormatHandler {
    // Common tabular logic
}

// For hierarchical formats (XML, JSON)
public abstract class HierarchicalFormatHandler implements ExportFormatHandler {
    // Common hierarchical logic
}
```

### 4. Simplified XML Implementation

With these improvements, XML would become:

```java
public class XMLFormatHandler extends HierarchicalFormatHandler {
    @Override
    protected void writeObject(ObjectExportContext ctx, List<FormattedValue> values) {
        // Simple, clean XML writing
        writer.writeStartElement("object");
        writer.writeAttribute("id", String.valueOf(ctx.getObjectId()));
        
        for (FormattedValue value : values) {
            if (!value.isEmpty()) {
                writer.writeStartElement("field");
                writer.writeAttribute("name", value.getColumnName());
                writer.writeAttribute("type", value.getType().toString());
                writer.writeCharacters(value.getStringValue());
                writer.writeEndElement();
            }
        }
        
        writer.writeEndElement();
    }
}
```

### 5. Configuration-Driven Output

Allow format handlers to declare their structure preferences:

```java
public enum OutputStructure {
    TABULAR,        // Excel-style sheets/tables
    HIERARCHICAL,   // XML/JSON tree structure
    FLAT_FILES      // One file per class
}

public interface ExportFormatHandler {
    OutputStructure getPreferredStructure();
    // Engine adapts its calling pattern based on this
}
```

## Benefits

1. **Type Safety**: Compile-time checks for context usage
2. **Simpler Implementation**: Format handlers focus only on output
3. **Less Duplication**: Common logic moved to engine/base classes
4. **Better Separation**: Clear separation between data processing and formatting
5. **Easier Testing**: Each component can be tested independently

## Migration Strategy

1. Keep current interface for backward compatibility
2. Implement new interface alongside
3. Migrate existing handlers to new system
4. Deprecate old interface