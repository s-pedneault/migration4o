#!/bin/bash

# Script to simplify the codebase by removing API interfaces and Impl suffixes

set -e

WORKSPACE="/Users/sylvain/Development/migration4o"
cd "$WORKSPACE"

echo "=== Step 1: Renaming Impl classes ==="

# Find all *Impl.java files
find src/main/java/dataobjects/impl -name "*Impl.java" -type f | while read impl_file; do
    # Get the directory and base name
    dir=$(dirname "$impl_file")
    filename=$(basename "$impl_file")
    classname="${filename%.java}"
    newclassname="${classname%Impl}"
    newfilename="${newclassname}.java"
    newfile="${dir}/${newfilename}"
    
    echo "Renaming $impl_file to $newfile"
    
    # Rename the class inside the file
    sed -i '' "s/public class ${classname} implements/public class ${newclassname} {/g" "$impl_file"
    sed -i '' "s/public class ${classname} extends/public class ${newclassname} extends/g" "$impl_file"
    
    # Rename the file
    mv "$impl_file" "$newfile"
done

echo ""
echo "=== Step 2: Update all references in the codebase ==="

# Update imports - from api to impl
find src/main/java -name "*.java" -type f -exec sed -i '' \
    -e 's/import dataobjects\.api\./import dataobjects.impl./g' \
    {} \;

# Remove Impl suffix from class references
find src/main/java -name "*.java" -type f -exec sed -i '' \
    -e 's/DOEngineImpl/DOEngine/g' \
    -e 's/DOEngineMonitoringImpl/DOEngineMonitoring/g' \
    -e 's/DODatabaseBuilderImpl/DODatabaseBuilder/g' \
    -e 's/DODatabaseReaderImpl/DODatabaseReader/g' \
    -e 's/DODatabaseOpenerImpl/DODatabaseOpener/g' \
    -e 's/DODatabaseEncodingImpl/DODatabaseEncoding/g' \
    -e 's/DOObjectResolverImpl/DOObjectResolver/g' \
    -e 's/DOInheritanceResolverImpl/DOInheritanceResolver/g' \
    -e 's/DOModuleReachabilityResolverImpl/DOModuleReachabilityResolver/g' \
    -e 's/DOObjectReachabilityTrackerImpl/DOObjectReachabilityTracker/g' \
    -e 's/DOSchemaToDatabaseClassResolverImpl/DOSchemaToDatabaseClassResolver/g' \
    -e 's/DOGenericObjectResolverImpl/DOGenericObjectResolver/g' \
    -e 's/DOReferenceResolverImpl/DOReferenceResolver/g' \
    -e 's/DOFieldResolverImpl/DOFieldResolver/g' \
    -e 's/DOStructureReportGeneratorImpl/DOStructureReportGenerator/g' \
    -e 's/DOObjectTreeReportGeneratorImpl/DOObjectTreeReportGenerator/g' \
    -e 's/XMLMigrationEngineImpl/XMLMigrationEngine/g' \
    -e 's/ExcelExportEngineImpl/ExcelExportEngine/g' \
    -e 's/DOSchemaImpl/DOSchema/g' \
    -e 's/DOSchemaReaderImpl/DOSchemaReader/g' \
    -e 's/DOSchemaModuleImpl/DOSchemaModule/g' \
    -e 's/DOFieldImpl/DOField/g' \
    -e 's/DOClassImpl/DOClass/g' \
    -e 's/DOReferenceImpl/DOReference/g' \
    -e 's/DODatabaseImpl/DODatabase/g' \
    -e 's/DODatabaseObjectImpl/DODatabaseObject/g' \
    -e 's/DOObjectReferenceImpl/DOObjectReference/g' \
    -e 's/DOCollectionReferenceImpl/DOCollectionReference/g' \
    -e 's/DOSchemaFieldImpl/DOSchemaField/g' \
    -e 's/DOSchemaClassImpl/DOSchemaClass/g' \
    -e 's/DODatabaseClassImpl/DODatabaseClass/g' \
    {} \;

echo ""
echo "=== Step 3: Deleting API package ==="
rm -rf src/main/java/dataobjects/api

echo ""
echo "=== Complete! ==="
echo "API interfaces removed and Impl suffixes cleaned up."
