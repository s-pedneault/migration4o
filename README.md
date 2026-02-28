# Migration4O - DB4O Database Migration Assistant

A comprehensive tool for migrating DB4O databases to modern formats. This project provides an interactive assistant that guides you through the entire migration process.

## Overview

Migration4O is a specialized tool designed to help developers migrate from the legacy DB4O database format to modern alternatives. The tool provides:

- **Interactive Migration Assistant**: Step-by-step guidance through the migration process
- **Database Analysis**: Comprehensive schema discovery and data structure analysis
- **Multiple Export Formats**: Support for XML, JSON, and other modern data formats
- **Progress Tracking**: Real-time monitoring of migration progress
- **Error Handling**: Robust error detection and recovery mechanisms

## Prerequisites

- **Java 8 or higher** (Java 11+ recommended)
- **Maven 3.6+** (for building from source)
- **4GB+ RAM** (recommended for large databases)

## Quick Start

### Option 1: Using the Pre-built Scripts (Recommended)

1. **Clone/Download the project**
   ```bash
   git clone <repository-url>
   cd Migration4O
   ```

2. **Build the project**
   ```bash
   ./build.sh
   ```

3. **Run the Migration Assistant**
   ```bash
   ./run.sh
   ```

### Option 2: Using Maven

1. **Build with Maven**
   ```bash
   mvn clean compile
   ```

2. **Run with Maven**
   ```bash
   mvn exec:java -Dexec.mainClass="ui.assistant.AssistantLauncher"
   ```

## Project Structure

```
Migration4O/
├── src/main/java/          # Java source code
│   ├── dataobjects/        # Data model classes
│   ├── service/           # Business logic and migration services
│   └── ui/               # User interface components
├── src/test/java/         # Test files
├── lib/                   # Required JAR dependencies
├── doc/                   # Documentation
├── schema/               # Database schema files
├── output/               # Migration output directory
├── build.sh              # Build script
├── run.sh                # Application launcher
└── pom.xml               # Maven configuration
```

## Features

### Interactive Assistant
- **Database Selection**: Easy file browser for selecting DB4O database files
- **Configuration Wizard**: Guided setup of migration parameters
- **Progress Monitoring**: Real-time progress bars and status updates
- **Error Recovery**: Automatic retry mechanisms and error reporting

### Migration Capabilities
- **Schema Analysis**: Automatic detection of database structure
- **Data Export**: Multiple output formats (XML, JSON, CSV)
- **Incremental Migration**: Support for large databases with chunked processing
- **Validation**: Data integrity checks throughout the process

### Advanced Features
- **Memory Optimization**: Efficient handling of large datasets
- **Logging**: Comprehensive logging with configurable levels
- **Backup Creation**: Automatic backup before migration starts
- **Resume Capability**: Ability to resume interrupted migrations

## Usage Guide

### Basic Migration Process

1. **Launch the Assistant**
   ```bash
   ./run.sh
   ```

2. **Follow the Interactive Prompts**
   - Select your DB4O database file
   - Choose output format and destination
   - Configure migration options
   - Start the migration process

3. **Monitor Progress**
   - Watch real-time progress indicators
   - Review any warnings or errors
   - Verify completion status

### Command Line Options

The migration assistant supports various command-line arguments:

```bash
./run.sh [options]

Options:
  -h, --help              Show help message
  --batch                 Run in batch mode (non-interactive)
  --input <file>          Specify input DB4O file
  --output <dir>          Specify output directory
  --format <format>       Output format (xml, json, csv)
```

## Configuration

### Memory Settings

For large databases, you may need to adjust memory settings in `run.sh`:

```bash
# One-off higher heap (UI launcher)
MIGRATION4O_XMX=12g ./run-ui.sh local/54060/BackupManuel.zip.nozip

# Set initial and max heap for either launcher
MIGRATION4O_XMS=2g MIGRATION4O_XMX=16g ./run-ui.sh
MIGRATION4O_XMS=2g MIGRATION4O_XMX=16g ./run.sh
```

Both launchers default to `-Xms1g -Xmx8g` if these environment variables are not set.

### Logging Configuration

Logging is configured through Log4j. You can adjust log levels by modifying the logging configuration in the source code.

## Troubleshooting

### Common Issues

1. **OutOfMemoryError**
   - Increase heap size in `run.sh`
   - Process database in smaller chunks
   - Close other memory-intensive applications

2. **Module Access Warnings (Java 9+)**
   - These warnings are normal and handled by the JVM arguments in `run.sh`
   - The tool includes all necessary `--add-opens` flags

3. **Database File Not Found**
   - Verify the file path is correct
   - Ensure you have read permissions
   - Check that the file is a valid DB4O database

### Java Version Compatibility

This tool requires specific JVM arguments to work with Java 9+. Always use the provided `run.sh` script rather than running the Java application directly.

## Development

### Building from Source

```bash
# Using Maven
mvn clean compile

# Using the build script
./build.sh
```

### Running Tests

```bash
mvn test
```

### IDE Setup

The project can be imported into any Java IDE that supports Maven:

1. Import as a Maven project
2. Ensure Java 8+ is configured
3. Add the JVM arguments from `run.sh` to your run configuration

## Dependencies

This project uses the following major dependencies:

- **DB4O 7.4.106**: Database access and object retrieval
- **Apache POI 5.2.4**: Excel and Office document processing
- **Apache Commons**: Various utility libraries
- **Log4j 2.20.0**: Logging framework

All dependencies are included in the `lib/` directory and configured as system dependencies in the Maven POM.

## Contributing

When contributing to this project:

1. Follow the existing code style and structure
2. Add appropriate tests for new functionality
3. Update documentation for any API changes
4. Ensure all builds pass before submitting

## License

This project is proprietary software developed by Gestion Technologies.

## Support

For issues, questions, or feature requests, please contact the development team or create an issue in the project repository.

## Version History

- **1.0.0**: Initial standalone release
  - Extracted from the Gouvernance Flutter project
  - Full Maven project structure
  - Comprehensive build and run scripts
  - Enhanced documentation and user guides