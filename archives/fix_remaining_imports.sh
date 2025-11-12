#!/bin/bash

# Fix all the database and schema related imports
echo "Fixing all database and schema related imports..."

# DODatabaseClass imports
find src -name "*.java" -exec sed -i '' 's/import dataobjects\.api\.database\.DODatabaseClass;/import dataobjects.api.models.database.DODatabaseClass;/g' {} \;

# DODatabaseField imports  
find src -name "*.java" -exec sed -i '' 's/import dataobjects\.api\.database\.DODatabaseField;/import dataobjects.api.models.database.DODatabaseField;/g' {} \;

# DOSchemaField imports
find src -name "*.java" -exec sed -i '' 's/import dataobjects\.api\.schema\.DOSchemaField;/import dataobjects.api.models.schema.DOSchemaField;/g' {} \;

# DOSchemaModule imports
find src -name "*.java" -exec sed -i '' 's/import dataobjects\.api\.schema\.DOSchemaModule;/import dataobjects.api.models.schema.DOSchemaModule;/g' {} \;

echo "Fixed all database and schema related imports"