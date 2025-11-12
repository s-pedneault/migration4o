#!/usr/bin/env node

/**
 * DB4O Migration Assistant Launcher for VS Code Debug
 * This script replicates the behavior of test.sh for VS Code launch configurations
 */

const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');

// Base directory (where the script is located)
const baseDir = __dirname;

// Check if classes directory exists
if (!fs.existsSync(path.join(baseDir, 'classes'))) {
    console.error("Error: classes directory not found. Please run build.sh first.");
    process.exit(1);
}

// Check if lib directory exists
if (!fs.existsSync(path.join(baseDir, 'lib'))) {
    console.error("Error: lib directory not found. Please ensure DB4O libraries are in the lib/ directory.");
    process.exit(1);
}

// Allocate 4GB of memory to the Java process
const javaMemeOpts = ["-Xms512m", "-Xmx4g"];

// Java module opens needed for DB4O to work with Java 9+
const jvmArgs = [
    "--add-opens", "java.base/java.util=ALL-UNNAMED",
    "--add-opens", "java.base/java.lang=ALL-UNNAMED",
    "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
    "--add-opens", "java.base/java.io=ALL-UNNAMED",
    "--add-opens", "java.base/java.nio=ALL-UNNAMED",
    "--add-opens", "java.base/java.net=ALL-UNNAMED",
    "--add-opens", "java.base/java.text=ALL-UNNAMED",
    "--add-opens", "java.base/java.time=ALL-UNNAMED",
    "--add-opens", "java.base/java.util.concurrent=ALL-UNNAMED",
    "--add-opens", "java.base/java.security=ALL-UNNAMED",
    "--add-opens", "java.desktop/java.awt=ALL-UNNAMED",
    "--add-opens", "java.desktop/java.awt.color=ALL-UNNAMED",
    "--add-opens", "java.desktop/java.awt.font=ALL-UNNAMED",
    "--add-opens", "java.desktop/java.awt.geom=ALL-UNNAMED",
    "--add-opens", "java.desktop/java.awt.image=ALL-UNNAMED",
    "--add-opens", "java.desktop/javax.swing=ALL-UNNAMED",
    "--add-opens", "java.sql/java.sql=ALL-UNNAMED"
];

console.log("=== DB4O Migration Assistant ===");
console.log(`Working directory: ${baseDir}`);
console.log("");

console.log("🚀 Launching Migration Assistant...");

// Build the classpath
const classpath = `${path.join(baseDir, 'classes')}:${path.join(baseDir, 'lib', '*')}`;

// Build complete Java command arguments
const javaArgs = [
    ...javaMemeOpts,
    ...jvmArgs,
    "-cp", classpath,
    "dataobjects.DOTest",
    ...process.argv.slice(2) // Pass through any additional arguments
];

// Execute Migration Assistant
const javaProcess = spawn('java', javaArgs, {
    cwd: baseDir,
    stdio: 'inherit' // This will pipe the Java process output directly to the terminal
});

// Handle process exit
javaProcess.on('close', (code) => {
    console.log(`\nMigration Assistant exited with code ${code}`);
    process.exit(code);
});

// Handle process errors
javaProcess.on('error', (err) => {
    console.error('Failed to start Migration Assistant:', err);
    process.exit(1);
});