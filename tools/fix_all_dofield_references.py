#!/usr/bin/env python3
import os
import re

WORKSPACE = "/Volumes/Business/GestionTechnologies/Migration4O"

# Map of file patterns to their field type
updates = {
    # DOReference works with database fields (references are resolved at db level)
    "src/main/java/migration4o/models/DOReference.java": "DODatabaseField",
    
    # Utility classes work with database at runtime
    "src/main/java/migration4o/util/TypeUtil.java": "DODatabaseField",
    "src/main/java/migration4o/util/ObjectResolverUtil.java": "DODatabaseField",
    "src/main/java/migration4o/util/DatabaseUtil.java": "DODatabaseField",
    "src/main/java/migration4o/util/CollectionTypeUtil.java": "DODatabaseField",
    
    # Engine and resolvers work with database
    "src/main/java/migration4o/engine/DOEngine.java": "DODatabaseField",
    "src/main/java/migration4o/engine/resolvers/DOFieldResolver.java": "DODatabaseField",
    "src/main/java/migration4o/engine/resolvers/DOObjectResolver.java": "DODatabaseField",
    "src/main/java/migration4o/engine/resolvers/DOReferenceResolver.java": "DODatabaseField",
    
    # Report generators work with database
    "src/main/java/migration4o/engine/report/DOEnginePrintout.java": "DODatabaseField",
    "src/main/java/migration4o/engine/report/DOStructureReportGenerator.java": "DODatabaseField",
    "src/main/java/migration4o/engine/report/reachability/ReachabilityReportGenerator.java": "DODatabaseField",
    "src/main/java/migration4o/engine/report/reachability/data/SchemaAnalyzer.java": "DODatabaseField",
    "src/main/java/migration4o/engine/report/reachability/data/DatabaseAnalyzer.java": "DODatabaseField",
    
    # Migration formats work with database
    "src/main/java/migration4o/engine/migration/formats/xml/XMLSchemaGenerator.java": "DODatabaseField",
    "src/main/java/migration4o/engine/migration/formats/xml/XMLReportGenerator.java": "DODatabaseField",
    "src/main/java/migration4o/engine/migration/formats/xml/XMLFormatHandler.java": "DODatabaseField",
    "src/main/java/migration4o/engine/migration/formats/xml/XMLDataExporter.java": "DODatabaseField",
    "src/main/java/migration4o/engine/migration/formats/excel/ExcelExportEngine.java": "DODatabaseField",
    
    # Schema reader works with schema fields
    "src/main/java/migration4o/schema/DOSchemaReader.java": "DOSchemaField",
}

for file_path, field_type in updates.items():
    full_path = os.path.join(WORKSPACE, file_path)
    if not os.path.exists(full_path):
        print(f"Skipping {file_path} - not found")
        continue
    
    with open(full_path, 'r') as f:
        content = f.read()
    
    # Replace import
    if field_type == "DODatabaseField":
        content = re.sub(
            r'import migration4o\.models\.DOField;',
            'import migration4o.models.database.DODatabaseField;',
            content
        )
    else:  # DOSchemaField
        content = re.sub(
            r'import migration4o\.models\.DOField;',
            'import migration4o.models.schema.DOSchemaField;',
            content
        )
    
    # Replace class references
    content = re.sub(r'\bDOField\b', field_type, content)
    
    with open(full_path, 'w') as f:
        f.write(content)
    
    print(f"Updated {file_path} to use {field_type}")

print("Done!")
