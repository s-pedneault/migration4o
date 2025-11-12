#!/bin/bash

# Fix package declarations in files in models/schema and models/database folders
echo "Fixing package declarations..."

# Fix DOSchema and related files in models/schema
find src/dataobjects/api/models/schema -name "*.java" -exec sed -i '' 's/package dataobjects\.api\.schema;/package dataobjects.api.models.schema;/g' {} \;

# Fix DODatabase and related files in models/database  
find src/dataobjects/api/models/database -name "*.java" -exec sed -i '' 's/package dataobjects\.api\.database;/package dataobjects.api.models.database;/g' {} \;

# Fix DOClass in models/
find src/dataobjects/api/models -name "DOClass.java" -exec sed -i '' 's/package dataobjects\.api\.common;/package dataobjects.api.models;/g' {} \;
find src/dataobjects/api/models -name "DOField.java" -exec sed -i '' 's/package dataobjects\.api\.common;/package dataobjects.api.models;/g' {} \;
find src/dataobjects/api/models -name "DOReference.java" -exec sed -i '' 's/package dataobjects\.api\.common;/package dataobjects.api.models;/g' {} \;

echo "Fixed package declarations"