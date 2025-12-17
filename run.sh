#!/bin/bash

# DB4O Migration Assistant Launcher Script
# This script handles the complex JVM arguments needed for DB4O to work with Java 9+

# Base directory (where the script is located)
BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Check if classes directory exists
if [ ! -d "$BASE_DIR/classes" ]; then
    echo "Error: classes directory not found. Please run build.sh first."
    exit 1
fi

# Check if lib directory exists
if [ ! -d "$BASE_DIR/lib" ]; then
    echo "Error: lib directory not found. Please ensure DB4O libraries are in the lib/ directory."
    exit 1
fi

# Allocate 4GB of memory to the Java process
JAVA_MEM_OPTS=("-Xms512m" "-Xmx4g")

# Java module opens needed for DB4O to work with Java 9+
JVM_ARGS=(
    "--add-opens" "java.base/java.util=ALL-UNNAMED"
    "--add-opens" "java.base/java.lang=ALL-UNNAMED" 
    "--add-opens" "java.base/java.lang.reflect=ALL-UNNAMED"
    "--add-opens" "java.base/java.io=ALL-UNNAMED"
    "--add-opens" "java.base/java.nio=ALL-UNNAMED"
    "--add-opens" "java.base/java.net=ALL-UNNAMED"
    "--add-opens" "java.base/java.text=ALL-UNNAMED"
    "--add-opens" "java.base/java.time=ALL-UNNAMED"
    "--add-opens" "java.base/java.util.concurrent=ALL-UNNAMED"
    "--add-opens" "java.base/java.security=ALL-UNNAMED"
    "--add-opens" "java.desktop/java.awt=ALL-UNNAMED"
    "--add-opens" "java.desktop/java.awt.color=ALL-UNNAMED"
    "--add-opens" "java.desktop/java.awt.font=ALL-UNNAMED"
    "--add-opens" "java.desktop/java.awt.geom=ALL-UNNAMED"
    "--add-opens" "java.desktop/java.awt.image=ALL-UNNAMED"
    "--add-opens" "java.desktop/javax.swing=ALL-UNNAMED"
    "--add-opens" "java.sql/java.sql=ALL-UNNAMED"
)

# Show usage if help is requested
if [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
    echo "=== DB4O Migration Assistant ==="
    echo ""
    echo "Usage: $0"
    echo ""
    echo "This tool provides a streamlined, step-by-step interface for:"
    echo "  1. Database file selection and configuration"
    echo "  2. Database analysis and schema discovery"  
    echo "  3. Database migration with progress tracking"
    echo ""
    echo "Options:"
    echo "  -h, --help              Show this help message"
    echo ""
    echo "The assistant will guide you through the entire migration process."
    exit 0
fi

echo "=== DB4O Migration Assistant ==="
echo "Java version: $(java -version 2>&1 | head -n 1)"
echo "Working directory: $BASE_DIR"
echo ""

echo "🚀 Launching Migration Assistant..."
# Execute Migration Assistant
exec java "${JAVA_MEM_OPTS[@]}" "${JVM_ARGS[@]}" -cp "$BASE_DIR/classes:$BASE_DIR/lib/*" migration4o.Migration4o "$@"
