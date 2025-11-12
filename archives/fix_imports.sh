#!/bin/bash

# Fix import statements for DODatabase and DOSchema
find src -name "*.java" -exec sed -i '' 's/import dataobjects\.api\.database\.DODatabase;/import dataobjects.api.models.database.DODatabase;/g' {} \;
find src -name "*.java" -exec sed -i '' 's/import dataobjects\.api\.schema\.DOSchema;/import dataobjects.api.models.schema.DOSchema;/g' {} \;
find src -name "*.java" -exec sed -i '' 's/import dataobjects\.api\.schema\.DOSchemaClass;/import dataobjects.api.models.schema.DOSchemaClass;/g' {} \;

echo "Fixed imports for DODatabase, DOSchema, and DOSchemaClass"