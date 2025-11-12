#!/bin/bash

# Fix imports for DOClass, DOField, DOReference
find src -name "*.java" -exec sed -i '' 's/import dataobjects\.api\.common\.DOClass;/import dataobjects.api.models.DOClass;/g' {} \;
find src -name "*.java" -exec sed -i '' 's/import dataobjects\.api\.common\.DOField;/import dataobjects.api.models.DOField;/g' {} \;
find src -name "*.java" -exec sed -i '' 's/import dataobjects\.api\.common\.DOReference;/import dataobjects.api.models.DOReference;/g' {} \;

# Also fix any wildcard imports
find src -name "*.java" -exec sed -i '' 's/import dataobjects\.api\.common\.\*;/import dataobjects.api.models.*;/g' {} \;

echo "Fixed imports for DOClass, DOField, DOReference"