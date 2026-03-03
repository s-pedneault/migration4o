#!/bin/bash

# Migration4o UI Launcher
# 
# Usage:
#   ./run-ui.sh [database_path] [--repeat-export]
#
# Arguments:
#   database_path    - Optional: Path to DB4O database file to open automatically on startup
#   --repeat-export  - Optional: Automatically repeat the last export operation after database loads
#
# Examples:
#   ./run-ui.sh                                    # Start UI without auto-opening database
#   ./run-ui.sh local/54060/mydb.dat               # Start UI and auto-open specified database
#   ./run-ui.sh local/54060/mydb.dat --repeat-export  # Auto-open database and repeat last export
#
# Export History:
#   Export operations are saved to local/.export-history.properties.
#   Use --repeat-export to re-run the last successful export operation.
#   You will be prompted to confirm before the export executes.

# Set working directory to project root
cd "$(dirname "$0")"

echo "=== Migration4o UI ==="
echo ""

# Memory settings (override with environment variables)
# Examples:
#   MIGRATION4O_XMX=12g ./run-ui.sh
#   MIGRATION4O_XMS=2g MIGRATION4O_XMX=16g ./run-ui.sh local/54060/BackupManuel.zip.nozip
JAVA_XMS="${MIGRATION4O_XMS:-1g}"
JAVA_XMX="${MIGRATION4O_XMX:-8g}"

# Check if classes exist
if [ ! -d "classes" ]; then
    echo "Error: Project not built. Please run ./build.sh first."
    exit 1
fi

# Run the UI application with module access flags for DB4O compatibility
# These flags allow DB4O to use reflection on internal Java classes
java --add-opens java.base/java.util=ALL-UNNAMED \
     --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
     --add-opens java.base/java.io=ALL-UNNAMED \
     --add-opens java.base/java.nio=ALL-UNNAMED \
     --add-opens java.base/java.net=ALL-UNNAMED \
     --add-opens java.base/java.text=ALL-UNNAMED \
     --add-opens java.base/java.time=ALL-UNNAMED \
     --add-opens java.base/java.util.concurrent=ALL-UNNAMED \
     --add-opens java.base/java.security=ALL-UNNAMED \
     --add-opens java.desktop/java.awt=ALL-UNNAMED \
     --add-opens java.desktop/java.awt.color=ALL-UNNAMED \
     --add-opens java.desktop/java.awt.font=ALL-UNNAMED \
     --add-opens java.desktop/java.awt.geom=ALL-UNNAMED \
     --add-opens java.desktop/java.awt.image=ALL-UNNAMED \
     --add-opens java.desktop/javax.swing=ALL-UNNAMED \
     --add-opens java.sql/java.sql=ALL-UNNAMED \
    -Xms"$JAVA_XMS" \
    -Xmx"$JAVA_XMX" \
     -cp "classes:lib/*:$HOME/.m2/repository/org/swinglabs/swingx/swingx-all/1.6.5-1/swingx-all-1.6.5-1.jar" migration4o.ui.Migration4oUI "$@"

echo ""
echo "UI closed."
