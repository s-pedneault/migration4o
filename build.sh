#!/bin/bash

# Migration4O Build Script
echo "=== Migration4O Build Script ==="

# Create directories if they don't exist
mkdir -p classes target

# Clean previous build
echo "Cleaning previous build..."
rm -rf classes/* target/*

# Option 1: Use Maven (preferred)
if command -v mvn &> /dev/null; then
    echo "Building with Maven..."
    mvn clean compile
    if [ $? -eq 0 ]; then
        echo "✓ Maven compilation successful"
        echo ""
        echo "Build complete! You can now run:"
        echo "  ./run.sh"
        echo "  or"
        echo "  mvn exec:java -Dexec.mainClass=\"ui.assistant.AssistantLauncher\""
        exit 0
    else
        echo "✗ Maven compilation failed, trying fallback..."
    fi
fi

# Option 2: Fallback to direct javac compilation
echo "Building with javac (fallback method)..."
javac -cp ".:lib/*" -d classes $(find src/main/java -name "*.java" -not -path "*/deprecated/*")

if [ $? -eq 0 ]; then
    echo "✓ Direct compilation successful"
    echo ""
    echo "Build complete! You can now run:"
    echo "  ./run.sh"
    echo ""
    echo "Note: Do NOT run this tool directly with java. Use the provided run.sh script instead to get all dependencies."
else
    echo "✗ Compilation failed"
    echo ""
    echo "Please ensure:"
    echo "  - Java 8+ is installed and in your PATH"
    echo "  - All required JAR files are in the lib/ directory"
    exit 1
fi
