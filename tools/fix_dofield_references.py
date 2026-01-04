#!/usr/bin/env python3
import os
import re

WORKSPACE = "/Volumes/Business/GestionTechnologies/Migration4O"

# Files that should use DODatabaseField (database package)
database_files = [
    "src/main/java/migration4o/models/database/DODatabaseClass.java",
    "src/main/java/migration4o/models/database/DOObjectReference.java",
    "src/main/java/migration4o/models/database/DOCollectionReference.java",
]

# Files that should use DOSchemaField (schema package)
schema_files = [
    "src/main/java/migration4o/models/schema/DOSchemaClass.java",
]

# Update database files to use DODatabaseField
for file_path in database_files:
    full_path = os.path.join(WORKSPACE, file_path)
    if not os.path.exists(full_path):
        print(f"Skipping {file_path} - not found")
        continue
    
    with open(full_path, 'r') as f:
        content = f.read()
    
    # Replace import
    content = re.sub(
        r'import migration4o\.models\.DOField;',
        'import migration4o.models.database.DODatabaseField;',
        content
    )
    
    # Replace class references
    content = re.sub(r'\bDOField\b', 'DODatabaseField', content)
    
    with open(full_path, 'w') as f:
        f.write(content)
    
    print(f"Updated {file_path} to use DODatabaseField")

# Update schema files to use DOSchemaField
for file_path in schema_files:
    full_path = os.path.join(WORKSPACE, file_path)
    if not os.path.exists(full_path):
        print(f"Skipping {file_path} - not found")
        continue
    
    with open(full_path, 'r') as f:
        content = f.read()
    
    # Replace import
    content = re.sub(
        r'import migration4o\.models\.DOField;',
        'import migration4o.models.schema.DOSchemaField;',
        content
    )
    
    # Replace class references
    content = re.sub(r'\bDOField\b', 'DOSchemaField', content)
    
    with open(full_path, 'w') as f:
        f.write(content)
    
    print(f"Updated {file_path} to use DOSchemaField")

print("Done!")
