package migration4o.engine.report;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import migration4o.engine.DOEngine;
import migration4o.models.DOClass;
import migration4o.models.DOField;
import migration4o.models.DOReference;
import migration4o.models.database.DOCollectionReference;
import migration4o.models.database.DODatabase;
import migration4o.models.database.DODatabaseClass;
import migration4o.models.database.DODatabaseObject;
import migration4o.models.database.DOObjectReference;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaModule;
import migration4o.util.HtmlBuilder;

/**
 * Implementation of DOStructureReportGenerator that creates comprehensive HTML
 * reports
 * showing database structure, schema information, inheritance hierarchies, and
 * object data.
 */
public class DOStructureReportGenerator {

    private static final String DEFAULT_OUTPUT_DIR = "output";
    private static final String DEFAULT_FILENAME = "Database contents.html";

    public void generateReport(DOEngine engine, String outputPath) throws IOException {
        DOSchema schema = engine.getSchema();
        DODatabase database = engine.getDatabase();

        StringBuilder html = new StringBuilder();

        // Generate HTML structure
        generateHtmlHeader(html);
        generateDatabaseOverview(html, database);
        generateSchemaModules(html, schema);
        generateClassHierarchy(html, schema);
        generateDetailedClassAnalysis(html, schema);
        generateObjectInstanceAnalysis(html, schema, database);
        generateOrphanObjectsAnalysis(html, schema, database);
        generateHtmlFooter(html);

        // Write to file
        Files.createDirectories(Paths.get(outputPath).getParent());
        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.write(html.toString());
        }

        System.out.println("Database structure report generated: " + outputPath);
    }

    public void generateDefaultReport(DOEngine engine) throws IOException {
        String outputPath = DEFAULT_OUTPUT_DIR + File.separator + DEFAULT_FILENAME;
        generateReport(engine, outputPath);
    }

    private void generateHtmlHeader(StringBuilder html) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        html.append("<!DOCTYPE html>\n")
                .append("<html lang=\"en\">\n")
                .append("<head>\n")
                .append("    <meta charset=\"UTF-8\">\n")
                .append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
                .append("    <title>Database Structure Report</title>\n")
                .append("    <style>\n")
                .append(generateCSS())
                .append("    </style>\n")
                .append("</head>\n")
                .append("<body>\n")
                .append("    <div class=\"container\">\n")
                .append("        <header>\n")
                .append("            <h1>Database Structure Report</h1>\n")
                .append("            <p class=\"timestamp\">Generated on: ").append(timestamp).append("</p>\n")
                .append("        </header>\n")
                .append("        <nav class=\"toc\">\n")
                .append("            <h2>Table of Contents</h2>\n")
                .append("            <ul>\n")
                .append("                <li><a href=\"#overview\">Database Overview</a></li>\n")
                .append("                <li><a href=\"#modules\">Schema Modules</a></li>\n")
                .append("                <li><a href=\"#hierarchy\">Class Hierarchy</a></li>\n")
                .append("                <li><a href=\"#classes\">Detailed Class Analysis</a></li>\n")
                .append("                <li><a href=\"#objects\">Object Instance Analysis</a></li>\n")
                .append("                <li><a href=\"#orphans\">Orphan Objects Analysis</a></li>\n")
                .append("            </ul>\n")
                .append("        </nav>\n");
    }

    private void generateHtmlFooter(StringBuilder html) {
        html.append("    </div>\n")
                .append("    <script>\n")
                .append(generateJavaScript())
                .append("    </script>\n")
                .append("</body>\n")
                .append("</html>\n");
    }

    private String generateCSS() {
        StringBuilder css = new StringBuilder();
        css.append("body {\n")
                .append("    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;\n")
                .append("    line-height: 1.6;\n")
                .append("    margin: 0;\n")
                .append("    padding: 0;\n")
                .append("    background-color: #f5f5f5;\n")
                .append("    color: #333;\n")
                .append("}\n")
                .append(".container {\n")
                .append("    max-width: 1200px;\n")
                .append("    margin: 0 auto;\n")
                .append("    padding: 20px;\n")
                .append("    background: white;\n")
                .append("    box-shadow: 0 0 20px rgba(0,0,0,0.1);\n")
                .append("}\n")
                .append("header {\n")
                .append("    text-align: center;\n")
                .append("    margin-bottom: 30px;\n")
                .append("    padding-bottom: 20px;\n")
                .append("    border-bottom: 2px solid #007acc;\n")
                .append("}\n")
                .append("header h1 {\n")
                .append("    color: #007acc;\n")
                .append("    margin-bottom: 10px;\n")
                .append("}\n")
                .append(".timestamp {\n")
                .append("    color: #666;\n")
                .append("    font-style: italic;\n")
                .append("}\n")
                .append(".toc {\n")
                .append("    background: #f8f9fa;\n")
                .append("    padding: 20px;\n")
                .append("    border-radius: 8px;\n")
                .append("    margin-bottom: 30px;\n")
                .append("}\n")
                .append(".toc ul {\n")
                .append("    list-style-type: none;\n")
                .append("    padding-left: 0;\n")
                .append("}\n")
                .append(".toc li {\n")
                .append("    margin-bottom: 8px;\n")
                .append("}\n")
                .append(".toc a {\n")
                .append("    color: #007acc;\n")
                .append("    text-decoration: none;\n")
                .append("    font-weight: 500;\n")
                .append("}\n")
                .append(".toc a:hover {\n")
                .append("    text-decoration: underline;\n")
                .append("}\n")
                .append(".section {\n")
                .append("    margin-bottom: 40px;\n")
                .append("    padding: 20px;\n")
                .append("    border: 1px solid #ddd;\n")
                .append("    border-radius: 8px;\n")
                .append("    background: #fafafa;\n")
                .append("}\n")
                .append(".section h2 {\n")
                .append("    color: #007acc;\n")
                .append("    border-bottom: 2px solid #007acc;\n")
                .append("    padding-bottom: 10px;\n")
                .append("    margin-top: 0;\n")
                .append("}\n")
                .append(".section h3 {\n")
                .append("    color: #5a6c7d;\n")
                .append("    margin-top: 25px;\n")
                .append("}\n")
                .append(".stats-grid {\n")
                .append("    display: grid;\n")
                .append("    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));\n")
                .append("    gap: 15px;\n")
                .append("    margin: 20px 0;\n")
                .append("}\n")
                .append(".stat-box {\n")
                .append("    background: white;\n")
                .append("    padding: 15px;\n")
                .append("    border-radius: 6px;\n")
                .append("    border-left: 4px solid #007acc;\n")
                .append("    box-shadow: 0 2px 4px rgba(0,0,0,0.1);\n")
                .append("}\n")
                .append(".stat-label {\n")
                .append("    font-weight: 600;\n")
                .append("    color: #333;\n")
                .append("    display: block;\n")
                .append("    margin-bottom: 5px;\n")
                .append("}\n")
                .append(".stat-value {\n")
                .append("    font-size: 1.5em;\n")
                .append("    font-weight: bold;\n")
                .append("    color: #007acc;\n")
                .append("}\n")
                .append(".class-card {\n")
                .append("    background: white;\n")
                .append("    border: 1px solid #ddd;\n")
                .append("    border-radius: 8px;\n")
                .append("    margin-bottom: 20px;\n")
                .append("    overflow: hidden;\n")
                .append("}\n")
                .append(".class-header {\n")
                .append("    background: #007acc;\n")
                .append("    color: white;\n")
                .append("    padding: 15px 20px;\n")
                .append("    font-weight: 600;\n")
                .append("    cursor: pointer;\n")
                .append("    display: flex;\n")
                .append("    justify-content: space-between;\n")
                .append("    align-items: center;\n")
                .append("}\n")
                .append(".class-header:hover {\n")
                .append("    background: #005fa3;\n")
                .append("}\n")
                .append(".class-content {\n")
                .append("    padding: 20px;\n")
                .append("    display: none;\n")
                .append("}\n")
                .append(".class-content.expanded {\n")
                .append("    display: block;\n")
                .append("}\n")
                .append(".expand-icon {\n")
                .append("    transition: transform 0.3s ease;\n")
                .append("}\n")
                .append(".expand-icon.rotated {\n")
                .append("    transform: rotate(180deg);\n")
                .append("}\n")
                .append(".inheritance-info {\n")
                .append("    background: #e3f2fd;\n")
                .append("    padding: 10px 15px;\n")
                .append("    border-radius: 6px;\n")
                .append("    margin-bottom: 15px;\n")
                .append("}\n")
                .append(".field-table {\n")
                .append("    width: 100%;\n")
                .append("    border-collapse: collapse;\n")
                .append("    margin: 15px 0;\n")
                .append("}\n")
                .append(".field-table th,\n")
                .append(".field-table td {\n")
                .append("    border: 1px solid #ddd;\n")
                .append("    padding: 8px 12px;\n")
                .append("    text-align: left;\n")
                .append("}\n")
                .append(".field-table th {\n")
                .append("    background: #f5f5f5;\n")
                .append("    font-weight: 600;\n")
                .append("}\n")
                .append(".field-table tr:nth-child(even) {\n")
                .append("    background: #f9f9f9;\n")
                .append("}\n")
                .append(".object-list {\n")
                .append("    max-height: 300px;\n")
                .append("    overflow-y: auto;\n")
                .append("    border: 1px solid #ddd;\n")
                .append("    border-radius: 4px;\n")
                .append("    background: white;\n")
                .append("}\n")
                .append(".object-item {\n")
                .append("    padding: 8px 12px;\n")
                .append("    border-bottom: 1px solid #eee;\n")
                .append("    display: flex;\n")
                .append("    justify-content: space-between;\n")
                .append("    align-items: center;\n")
                .append("}\n")
                .append(".object-item:last-child {\n")
                .append("    border-bottom: none;\n")
                .append("}\n")
                .append(".object-item:hover {\n")
                .append("    background: #f0f8ff;\n")
                .append("}\n")
                .append(".object-id {\n")
                .append("    font-family: monospace;\n")
                .append("    color: #666;\n")
                .append("}\n")
                .append(".collection-details {\n")
                .append("    background: #fff3cd;\n")
                .append("    border: 1px solid #ffeaa7;\n")
                .append("    border-radius: 4px;\n")
                .append("    padding: 10px;\n")
                .append("    margin: 5px 0;\n")
                .append("}\n")
                .append(".reference-details {\n")
                .append("    background: #d1ecf1;\n")
                .append("    border: 1px solid #bee5eb;\n")
                .append("    border-radius: 4px;\n")
                .append("    padding: 10px;\n")
                .append("    margin: 5px 0;\n")
                .append("}\n")
                .append(".primitive-type {\n")
                .append("    color: #6c757d;\n")
                .append("    font-weight: 600;\n")
                .append("}\n")
                .append(".schema-known-type {\n")
                .append("    color: #28a745;\n")
                .append("    font-weight: 600;\n")
                .append("}\n")
                .append(".schema-unknown-type {\n")
                .append("    color: #dc3545;\n")
                .append("    font-weight: 600;\n")
                .append("}\n")
                .append(".collection-type {\n")
                .append("    color: #fd7e14;\n")
                .append("    font-weight: 600;\n")
                .append("}\n")
                .append(".object-type {\n")
                .append("    color: #007acc;\n")
                .append("    font-weight: 600;\n")
                .append("}\n")
                .append(".expandable-section {\n")
                .append("    margin: 10px 0;\n")
                .append("}\n")
                .append(".expandable-header {\n")
                .append("    background: #e9ecef;\n")
                .append("    padding: 8px 12px;\n")
                .append("    border-radius: 4px;\n")
                .append("    cursor: pointer;\n")
                .append("    font-weight: 500;\n")
                .append("    display: flex;\n")
                .append("    justify-content: space-between;\n")
                .append("    align-items: center;\n")
                .append("}\n")
                .append(".expandable-header:hover {\n")
                .append("    background: #dee2e6;\n")
                .append("}\n")
                .append(".expandable-content {\n")
                .append("    display: none;\n")
                .append("    padding: 10px;\n")
                .append("    border: 1px solid #dee2e6;\n")
                .append("    border-top: none;\n")
                .append("    border-radius: 0 0 4px 4px;\n")
                .append("}\n")
                .append(".expandable-content.expanded {\n")
                .append("    display: block;\n")
                .append("}\n")
                .append(".class-object-card {\n")
                .append("    background: white;\n")
                .append("    border: 1px solid #ddd;\n")
                .append("    border-radius: 8px;\n")
                .append("    margin-bottom: 20px;\n")
                .append("    overflow: hidden;\n")
                .append("}\n")
                .append(".class-object-header {\n")
                .append("    background: #28a745;\n")
                .append("    color: white;\n")
                .append("    padding: 15px 20px;\n")
                .append("    font-weight: 600;\n")
                .append("    cursor: pointer;\n")
                .append("    display: flex;\n")
                .append("    justify-content: space-between;\n")
                .append("    align-items: center;\n")
                .append("}\n")
                .append(".class-object-header:hover {\n")
                .append("    background: #218838;\n")
                .append("}\n")
                .append(".class-object-content {\n")
                .append("    padding: 20px;\n")
                .append("    display: none;\n")
                .append("}\n")
                .append(".class-object-content.expanded {\n")
                .append("    display: block;\n")
                .append("}\n")
                .append(".migration-readiness {\n")
                .append("    background: #f8f9fa;\n")
                .append("    padding: 15px;\n")
                .append("    border-radius: 6px;\n")
                .append("    margin-top: 15px;\n")
                .append("}\n")
                .append(".readiness-score {\n")
                .append("    display: flex;\n")
                .append("    align-items: center;\n")
                .append("    gap: 10px;\n")
                .append("    margin-bottom: 10px;\n")
                .append("}\n")
                .append(".readiness-label {\n")
                .append("    font-weight: 600;\n")
                .append("}\n")
                .append(".stat-value-warning {\n")
                .append("    font-size: 1.5em;\n")
                .append("    font-weight: bold;\n")
                .append("    color: #fd7e14;\n")
                .append("}\n")
                .append(".stat-value-error {\n")
                .append("    font-size: 1.5em;\n")
                .append("    font-weight: bold;\n")
                .append("    color: #dc3545;\n")
                .append("}\n")
                .append(".stat-value-success {\n")
                .append("    color: #28a745;\n")
                .append("    font-weight: 600;\n")
                .append("}\n")
                .append(".readiness-warning {\n")
                .append("    background: #fff3cd;\n")
                .append("    border: 1px solid #ffeaa7;\n")
                .append("    border-radius: 4px;\n")
                .append("    padding: 10px;\n")
                .append("    color: #856404;\n")
                .append("}\n")
                .append(".readiness-success {\n")
                .append("    background: #d4edda;\n")
                .append("    border: 1px solid #c3e6cb;\n")
                .append("    border-radius: 4px;\n")
                .append("    padding: 10px;\n")
                .append("    color: #155724;\n")
                .append("}\n")
                .append(".object-stats {\n")
                .append("    display: flex;\n")
                .append("    gap: 20px;\n")
                .append("    flex-wrap: wrap;\n")
                .append("    margin-bottom: 15px;\n")
                .append("}\n")
                .append(".object-stat {\n")
                .append("    display: flex;\n")
                .append("    align-items: center;\n")
                .append("    gap: 5px;\n")
                .append("}\n")
                .append(".object-instances-table {\n")
                .append("    border: 1px solid #ddd;\n")
                .append("    border-radius: 6px;\n")
                .append("    max-height: 600px;\n")
                .append("    overflow-y: auto;\n")
                .append("}\n")
                .append(".object-instance {\n")
                .append("    border-bottom: 1px solid #eee;\n")
                .append("    padding: 15px;\n")
                .append("}\n")
                .append(".object-instance:last-child {\n")
                .append("    border-bottom: none;\n")
                .append("}\n")
                .append(".object-instance-header {\n")
                .append("    display: flex;\n")
                .append("    justify-content: space-between;\n")
                .append("    align-items: center;\n")
                .append("    margin-bottom: 10px;\n")
                .append("}\n")
                .append(".reachability-badge {\n")
                .append("    padding: 4px 8px;\n")
                .append("    border-radius: 4px;\n")
                .append("    font-size: 0.85em;\n")
                .append("    font-weight: 600;\n")
                .append("}\n")
                .append(".reachability-badge.reachable {\n")
                .append("    background: #d4edda;\n")
                .append("    color: #155724;\n")
                .append("}\n")
                .append(".reachability-badge.orphaned {\n")
                .append("    background: #fff3cd;\n")
                .append("    color: #856404;\n")
                .append("}\n")
                .append(".object-references {\n")
                .append("    background: #f8f9fa;\n")
                .append("    padding: 10px;\n")
                .append("    border-radius: 4px;\n")
                .append("}\n")
                .append(".reference-section {\n")
                .append("    margin-bottom: 15px;\n")
                .append("}\n")
                .append(".reference-section:last-child {\n")
                .append("    margin-bottom: 0;\n")
                .append("}\n")
                .append(".reference-list, .collection-list {\n")
                .append("    margin-top: 8px;\n")
                .append("}\n")
                .append(".reference-item {\n")
                .append("    padding: 5px 0;\n")
                .append("    font-size: 0.9em;\n")
                .append("}\n")
                .append(".field-name {\n")
                .append("    font-weight: 600;\n")
                .append("    color: #495057;\n")
                .append("}\n")
                .append(".reference-type {\n")
                .append("    font-style: italic;\n")
                .append("    color: #6c757d;\n")
                .append("    font-size: 0.85em;\n")
                .append("}\n")
                .append(".collection-item {\n")
                .append("    background: white;\n")
                .append("    border: 1px solid #dee2e6;\n")
                .append("    border-radius: 4px;\n")
                .append("    padding: 10px;\n")
                .append("    margin-bottom: 8px;\n")
                .append("}\n")
                .append(".collection-header {\n")
                .append("    display: flex;\n")
                .append("    justify-content: space-between;\n")
                .append("    align-items: center;\n")
                .append("    margin-bottom: 8px;\n")
                .append("}\n")
                .append(".collection-info {\n")
                .append("    font-size: 0.9em;\n")
                .append("    color: #6c757d;\n")
                .append("}\n")
                .append(".object-id-list {\n")
                .append("    display: flex;\n")
                .append("    flex-wrap: wrap;\n")
                .append("    gap: 5px;\n")
                .append("    margin-top: 5px;\n")
                .append("}\n")
                .append(".object-id-chip {\n")
                .append("    background: #e9ecef;\n")
                .append("    padding: 2px 6px;\n")
                .append("    border-radius: 3px;\n")
                .append("    font-family: monospace;\n")
                .append("    font-size: 0.8em;\n")
                .append("    color: #495057;\n")
                .append("}\n")
                .append(".object-id-more {\n")
                .append("    background: #6c757d;\n")
                .append("    color: white;\n")
                .append("    padding: 2px 6px;\n")
                .append("    border-radius: 3px;\n")
                .append("    font-size: 0.8em;\n")
                .append("}\n")
                .append(".collection-empty, .no-references {\n")
                .append("    color: #6c757d;\n")
                .append("    font-style: italic;\n")
                .append("    padding: 10px;\n")
                .append("}\n")
                .append(".object-more-indicator {\n")
                .append("    text-align: center;\n")
                .append("    padding: 15px;\n")
                .append("    color: #6c757d;\n")
                .append("    border-top: 1px solid #eee;\n")
                .append("}\n");
        return css.toString();
    }

    private String generateJavaScript() {
        StringBuilder js = new StringBuilder();
        js.append("// Toggle class details\n")
                .append("document.addEventListener('DOMContentLoaded', function() {\n")
                .append("    // Class card toggles\n")
                .append("    document.querySelectorAll('.class-header').forEach(header => {\n")
                .append("        header.addEventListener('click', function() {\n")
                .append("            const content = this.nextElementSibling;\n")
                .append("            const icon = this.querySelector('.expand-icon');\n")
                .append("            \n")
                .append("            content.classList.toggle('expanded');\n")
                .append("            icon.classList.toggle('rotated');\n")
                .append("        });\n")
                .append("    });\n")
                .append("    \n")
                .append("    // Object class card toggles\n")
                .append("    document.querySelectorAll('.class-object-header').forEach(header => {\n")
                .append("        header.addEventListener('click', function() {\n")
                .append("            const content = this.nextElementSibling;\n")
                .append("            const icon = this.querySelector('.expand-icon');\n")
                .append("            \n")
                .append("            content.classList.toggle('expanded');\n")
                .append("            icon.classList.toggle('rotated');\n")
                .append("        });\n")
                .append("    });\n")
                .append("    \n")
                .append("    // Expandable section toggles\n")
                .append("    document.querySelectorAll('.expandable-header').forEach(header => {\n")
                .append("        header.addEventListener('click', function() {\n")
                .append("            const content = this.nextElementSibling;\n")
                .append("            const icon = this.querySelector('.expand-icon');\n")
                .append("            \n")
                .append("            content.classList.toggle('expanded');\n")
                .append("            if (icon) icon.classList.toggle('rotated');\n")
                .append("        });\n")
                .append("    });\n")
                .append("});\n")
                .append("\n")
                .append("// Smooth scrolling for navigation links\n")
                .append("document.querySelectorAll('a[href^=\"#\"]').forEach(anchor => {\n")
                .append("    anchor.addEventListener('click', function (e) {\n")
                .append("        e.preventDefault();\n")
                .append("        const target = document.querySelector(this.getAttribute('href'));\n")
                .append("        if (target) {\n")
                .append("            target.scrollIntoView({\n")
                .append("                behavior: 'smooth',\n")
                .append("                block: 'start'\n")
                .append("            });\n")
                .append("        }\n")
                .append("    });\n")
                .append("});\n");
        return js.toString();
    }

    private void generateDatabaseOverview(StringBuilder html, DODatabase database) {
        html.append("        <section id=\"overview\" class=\"section\">\n")
                .append("            <h2>Database Overview</h2>\n")
                .append("            <div class=\"stats-grid\">\n")
                .append("                <div class=\"stat-box\">\n")
                .append("                    <span class=\"stat-label\">Total Classes</span>\n")
                .append("                    <span class=\"stat-value\">").append(database.getTotalClasses())
                .append("</span>\n")
                .append("                </div>\n")
                .append("                <div class=\"stat-box\">\n")
                .append("                    <span class=\"stat-label\">Total Objects</span>\n")
                .append("                    <span class=\"stat-value\">").append(database.getTotalObjects())
                .append("</span>\n")
                .append("                </div>\n")
                .append("                <div class=\"stat-box\">\n")
                .append("                    <span class=\"stat-label\">Database Size</span>\n")
                .append("                    <span class=\"stat-value\">").append(database.getDatabaseSize())
                .append("</span>\n")
                .append("                </div>\n");

        // Add encoding information if available
        if (database.getEncoding() != null) {
            html.append("                <div class=\"stat-box\">\n")
                    .append("                    <span class=\"stat-label\">Encoding</span>\n")
                    .append("                    <span class=\"stat-value\">").append(database.getEncoding().toString())
                    .append("</span>\n")
                    .append("                </div>\n");
        }

        html.append("            </div>\n")
                .append("        </section>\n");
    }

    private void generateSchemaModules(StringBuilder html, DOSchema schema) {
        html.append("        <section id=\"modules\" class=\"section\">\n")
                .append("            <h2>Schema Modules</h2>\n")
                .append("            <p>The schema is organized into modules that group related classes by domain or functionality.</p>\n");

        DOSchemaModule[] modules = schema.getModules();
        if (modules != null && modules.length > 0) {
            html.append("            <div class=\"stats-grid\">\n");
            for (DOSchemaModule module : modules) {
                DOSchemaClass[] moduleClasses = module.getClasses();
                int classCount = moduleClasses != null ? moduleClasses.length : 0;

                // Count objects in this module
                int totalObjects = 0;
                for (DOSchemaClass clazz : moduleClasses != null ? moduleClasses : new DOSchemaClass[0]) {
                    if (clazz.getDatabaseClass() != null) {
                        totalObjects += clazz.getDatabaseClass().getTotalObjectCount();
                    }
                }

                html.append("                <div class=\"stat-box\">\n")
                        .append("                    <span class=\"stat-label\">").append(escapeHtml(module.getName()))
                        .append("</span>\n")
                        .append("                    <span class=\"stat-value\">").append(classCount)
                        .append(" classes</span>\n")
                        .append("                    <div style=\"font-size: 0.8em; color: #666; margin-top: 5px;\">")
                        .append(totalObjects).append(" objects</div>\n")
                        .append("                </div>\n");
            }
            html.append("            </div>\n");
        } else {
            html.append("            <p>No modules defined in schema.</p>\n");
        }

        html.append("        </section>\n");
    }

    private void generateClassHierarchy(StringBuilder html, DOSchema schema) {
        html.append("        <section id=\"hierarchy\" class=\"section\">\n")
                .append("            <h2>Class Hierarchy</h2>\n");

        // Build inheritance tree
        Map<String, List<DOSchemaClass>> inheritanceMap = new HashMap<>();
        Set<String> allClasses = new HashSet<>();

        for (DOSchemaClass clazz : schema.getClasses()) {
            allClasses.add(clazz.getAbsoluteName());
            String superClass = clazz.getSuperClassAbsoluteName();
            if (superClass != null && !superClass.isEmpty()) {
                inheritanceMap.computeIfAbsent(superClass, k -> new ArrayList<>()).add(clazz);
            }
        }

        // Find root classes (classes with no superclass or superclass not in our
        // schema)
        List<DOSchemaClass> rootClasses = Arrays.stream(schema.getClasses())
                .filter(clazz -> {
                    String superClass = clazz.getSuperClassAbsoluteName();
                    return superClass == null || superClass.isEmpty() || !allClasses.contains(superClass);
                })
                .collect(Collectors.toList());

        html.append("            <div class=\"expandable-section\">\n")
                .append("                <div class=\"expandable-header\">\n")
                .append("                    <span>Class Inheritance Tree</span>\n")
                .append("                    <span class=\"expand-icon\">▼</span>\n")
                .append("                </div>\n")
                .append("                <div class=\"expandable-content\">\n")
                .append("                    <ul>\n");

        for (DOSchemaClass rootClass : rootClasses) {
            generateClassHierarchyNode(html, rootClass, inheritanceMap, 0);
        }

        html.append("                    </ul>\n")
                .append("                </div>\n")
                .append("            </div>\n")
                .append("        </section>\n");
    }

    private void generateClassHierarchyNode(StringBuilder html, DOSchemaClass clazz,
            Map<String, List<DOSchemaClass>> inheritanceMap, int depth) {
        String indent = "    ".repeat(depth);
        html.append(indent).append("                        <li>\n")
                .append(indent).append("                            <strong>").append(escapeHtml(clazz.getShortName()))
                .append("</strong>\n")
                .append(indent).append("                            <span class=\"object-id\">(")
                .append(escapeHtml(clazz.getAbsoluteName())).append(")</span>\n");

        if (clazz.getDatabaseClass() != null) {
            html.append(indent).append("                            - <em>")
                    .append(clazz.getDatabaseClass().getTotalObjectCount()).append(" objects</em>\n");
        }

        List<DOSchemaClass> subClasses = inheritanceMap.get(clazz.getAbsoluteName());
        if (subClasses != null && !subClasses.isEmpty()) {
            html.append(indent).append("                            <ul>\n");
            for (DOSchemaClass subClass : subClasses) {
                generateClassHierarchyNode(html, subClass, inheritanceMap, depth + 1);
            }
            html.append(indent).append("                            </ul>\n");
        }

        html.append(indent).append("                        </li>\n");
    }

    private void generateDetailedClassAnalysis(StringBuilder html, DOSchema schema) {
        html.append("        <section id=\"classes\" class=\"section\">\n")
                .append("            <h2>Detailed Class Analysis</h2>\n")
                .append("            <p>Classes are organized by schema modules in their defined order.</p>\n");

        DOSchemaModule[] modules = schema.getModules();
        if (modules != null && modules.length > 0) {
            // Group classes by modules
            for (DOSchemaModule module : modules) {
                generateModuleClassSection(html, module, schema);
            }
        } else {
            // Fallback: show all classes without module grouping
            html.append("            <h3>All Classes (No Module Grouping)</h3>\n");
            for (DOSchemaClass clazz : schema.getClasses()) {
                generateClassCard(html, clazz, schema);
            }
        }

        html.append("        </section>\n");
    }

    private void generateModuleClassSection(StringBuilder html, DOSchemaModule module, DOSchema schema) {
        DOSchemaClass[] moduleClasses = module.getClasses();
        if (moduleClasses == null || moduleClasses.length == 0) {
            return; // Skip empty modules
        }

        html.append("            <div class=\"expandable-section\">\n")
                .append("                <div class=\"expandable-header\">\n")
                .append("                    <span><strong>Module: ").append(escapeHtml(module.getName()))
                .append("</strong> (").append(moduleClasses.length).append(" classes)</span>\n")
                .append("                    <span class=\"expand-icon\">▼</span>\n")
                .append("                </div>\n")
                .append("                <div class=\"expandable-content\">\n");

        // Generate class cards for this module
        for (DOSchemaClass clazz : moduleClasses) {
            generateClassCard(html, clazz, schema);
        }

        html.append("                </div>\n")
                .append("            </div>\n");
    }

    private void generateClassCard(StringBuilder html, DOSchemaClass clazz, DOSchema schema) {
        html.append("            <div class=\"class-card\">\n")
                .append("                <div class=\"class-header\">\n")
                .append("                    <span>").append(escapeHtml(clazz.getShortName())).append("</span>\n")
                .append("                    <span class=\"expand-icon\">▼</span>\n")
                .append("                </div>\n")
                .append("                <div class=\"class-content\">\n");

        // Basic class information
        html.append("                    <div class=\"inheritance-info\">\n")
                .append("                        <strong>Full Name:</strong> ")
                .append(escapeHtml(clazz.getAbsoluteName())).append("<br>\n");

        if (clazz.getDescription() != null && !clazz.getDescription().isEmpty()) {
            html.append("                        <strong>Description:</strong> ")
                    .append(escapeHtml(clazz.getDescription())).append("<br>\n");
        }

        if (clazz.getTitle() != null && !clazz.getTitle().isEmpty()) {
            html.append("                        <strong>Title:</strong> ").append(escapeHtml(clazz.getTitle()))
                    .append("<br>\n");
        }

        if (clazz.getSuperClassAbsoluteName() != null && !clazz.getSuperClassAbsoluteName().isEmpty()) {
            html.append("                        <strong>Extends:</strong> ")
                    .append(escapeHtml(clazz.getSuperClassAbsoluteName())).append("<br>\n");
        }

        if (clazz.getExportName() != null && !clazz.getExportName().isEmpty()) {
            html.append("                        <strong>Export Name:</strong> ")
                    .append(escapeHtml(clazz.getExportName())).append("<br>\n");
        }

        html.append("                    </div>\n");

        // Fields information
        generateFieldsTable(html, clazz, schema);

        // References information
        generateReferencesTable(html, clazz);

        // Database class information (objects)
        if (clazz.getDatabaseClass() != null) {
            generateDatabaseClassInfo(html, clazz.getDatabaseClass());
        }

        html.append("                </div>\n")
                .append("            </div>\n");
    }

    private void generateFieldsTable(StringBuilder html, DOClass clazz, DOSchema schema) {
        DOField[] fields = clazz.getFields();
        if (fields == null || fields.length == 0) {
            html.append("                    <h3>Fields</h3>\n")
                    .append("                    <p>No fields defined.</p>\n");
            return;
        }

        html.append("                    <h3>Fields</h3>\n")
                .append("                    <table class=\"field-table\">\n")
                .append("                        <thead>\n")
                .append("                            <tr>\n")
                .append("                                <th>Name</th>\n")
                .append("                                <th>Type</th>\n")
                .append("                                <th>Description</th>\n")
                .append("                                <th>Details</th>\n")
                .append("                            </tr>\n")
                .append("                        </thead>\n")
                .append("                        <tbody>\n");

        for (DOField field : fields) {
            html.append("                            <tr>\n")
                    .append("                                <td><strong>").append(escapeHtml(field.getName()))
                    .append("</strong></td>\n")
                    .append("                                <td>");

            // Type information with styling
            if (field.isPrimitive()) {
                html.append("<span class=\"primitive-type\">").append(escapeHtml(field.getTypeName()))
                        .append("</span>");
            } else if (field.isArray()) {
                html.append("<span class=\"collection-type\">").append(escapeHtml(field.getTypeName()))
                        .append("</span>");
                if (field.getContentTypeName() != null) {
                    String contentTypeClass = getClassTypeStyleClass(field.getContentTypeName(), schema);
                    html.append("<br>Contains: <span class=\"").append(contentTypeClass).append("\">")
                            .append(escapeHtml(field.getContentTypeName())).append("</span>");
                }
            } else {
                String typeClass = getClassTypeStyleClass(field.getTypeName(), schema);
                html.append("<span class=\"").append(typeClass).append("\">").append(escapeHtml(field.getTypeName()))
                        .append("</span>");
            }

            html.append("</td>\n")
                    .append("                                <td>")
                    .append(escapeHtml(field.getDescription() != null ? field.getDescription() : "")).append("</td>\n")
                    .append("                                <td>");

            // Field details
            List<String> details = new ArrayList<>();
            if (field.isPrimitive())
                details.add("Primitive");
            if (field.isArray())
                details.add("Collection");
            html.append(String.join(", ", details));

            html.append("</td>\n")
                    .append("                            </tr>\n");
        }

        html.append("                        </tbody>\n")
                .append("                    </table>\n");
    }

    private void generateReferencesTable(StringBuilder html, DOClass clazz) {
        DOReference[] references = clazz.getReferences();
        if (references == null || references.length == 0) {
            return; // Don't show empty references section
        }

        html.append("                    <h3>References</h3>\n")
                .append("                    <div class=\"reference-details\">\n")
                .append("                        <p>This class has ").append(references.length)
                .append(" reference(s) from other classes.</p>\n")
                .append("                    </div>\n");
    }

    private void generateDatabaseClassInfo(StringBuilder html, DODatabaseClass dbClass) {
        html.append("                    <h3>Database Objects</h3>\n")
                .append("                    <div class=\"stats-grid\">\n")
                .append("                        <div class=\"stat-box\">\n")
                .append("                            <span class=\"stat-label\">Total Objects</span>\n")
                .append("                            <span class=\"stat-value\">").append(dbClass.getTotalObjectCount())
                .append("</span>\n")
                .append("                        </div>\n")
                .append("                        <div class=\"stat-box\">\n")
                .append("                            <span class=\"stat-label\">Migrated Objects</span>\n")
                .append("                            <span class=\"stat-value\">")
                .append(dbClass.getMigratedObjectCount()).append("</span>\n")
                .append("                        </div>\n");

        if (dbClass.isLeafClass()) {
            html.append("                        <div class=\"stat-box\">\n")
                    .append("                            <span class=\"stat-label\">Leaf Class</span>\n")
                    .append("                            <span class=\"stat-value\">Yes</span>\n")
                    .append("                        </div>\n");
        }

        html.append("                    </div>\n");

        // Resolved objects information
        generateResolvedObjectsInfo(html, dbClass);
    }

    private void generateResolvedObjectsInfo(StringBuilder html, DODatabaseClass dbClass) {
        DODatabaseObject[] resolvedObjects = dbClass.getResolvedObjects();
        if (resolvedObjects == null || resolvedObjects.length == 0) {
            html.append("                    <p>No resolved objects available.</p>\n");
            return;
        }

        // Separate reachable and orphaned objects
        DODatabaseObject[] reachableObjects = dbClass.getReachableObjects();
        DODatabaseObject[] orphanedObjects = dbClass.getOrphanedObjects();

        html.append("                    <div class=\"expandable-section\">\n")
                .append("                        <div class=\"expandable-header\">\n")
                .append("                            <span>Object Details (").append(resolvedObjects.length)
                .append(" total)</span>\n")
                .append("                            <span class=\"expand-icon\">▼</span>\n")
                .append("                        </div>\n")
                .append("                        <div class=\"expandable-content\">\n");

        if (reachableObjects != null && reachableObjects.length > 0) {
            html.append("                            <h4>Reachable Objects (").append(reachableObjects.length)
                    .append(")</h4>\n");
            generateObjectList(html, reachableObjects);
        }

        if (orphanedObjects != null && orphanedObjects.length > 0) {
            html.append("                            <h4>Orphaned Objects (").append(orphanedObjects.length)
                    .append(")</h4>\n");
            generateObjectList(html, orphanedObjects);
        }

        html.append("                        </div>\n")
                .append("                    </div>\n");
    }

    private void generateObjectList(StringBuilder html, DODatabaseObject[] objects) {
        html.append("                            <div class=\"object-list\">\n");

        for (int i = 0; i < Math.min(objects.length, 100); i++) { // Limit to first 100 objects for performance
            DODatabaseObject obj = objects[i];
            html.append("                                <div class=\"object-item\">\n")
                    .append("                                    <span class=\"object-id\">ID: ")
                    .append(obj.getObjectId()).append("</span>\n");

            // Show references count
            int directRefs = obj.getReferences() != null ? obj.getReferences().length : 0;
            int collectionRefs = obj.getCollections() != null ? obj.getCollections().length : 0;

            if (directRefs > 0 || collectionRefs > 0) {
                html.append("                                    <span>Refs: ").append(directRefs)
                        .append(" direct, ").append(collectionRefs).append(" collections</span>\n");
            }

            html.append("                                </div>\n");
        }

        if (objects.length > 100) {
            html.append("                                <div class=\"object-item\">\n")
                    .append("                                    <span><em>... and ").append(objects.length - 100)
                    .append(" more objects</em></span>\n")
                    .append("                                </div>\n");
        }

        html.append("                            </div>\n");
    }

    private void generateObjectInstanceAnalysis(StringBuilder html, DOSchema schema, DODatabase database) {
        html.append("        <section id=\"objects\" class=\"section\">\n")
                .append("            <h2>Object Instance Analysis</h2>\n")
                .append("            <p>This section shows the actual objects stored in the database, their collections, and migration readiness. Objects are organized by schema modules.</p>\n");

        // Overall migration readiness summary
        generateMigrationReadinessSummary(html, schema);

        DOSchemaModule[] modules = schema.getModules();
        if (modules != null && modules.length > 0) {
            // Group object analysis by modules
            for (DOSchemaModule module : modules) {
                HtmlBuilder moduleBuilder = new HtmlBuilder(html);
                generateModuleObjectSection(moduleBuilder, module);
            }
        } else {
            // Fallback: show all classes without module grouping
            html.append("            <h3>All Object Classes (No Module Grouping)</h3>\n");
            for (DOSchemaClass clazz : schema.getClasses()) {
                if (clazz.getDatabaseClass() != null && clazz.getDatabaseClass().getResolvedObjects() != null) {
                    HtmlBuilder classBuilder = new HtmlBuilder(html);
                    generateClassObjectAnalysis(classBuilder, clazz);
                }
            }
        }

        html.append("        </section>\n");
    }

    private void generateModuleObjectSection(HtmlBuilder html, DOSchemaModule module) {
        DOSchemaClass[] moduleClasses = module.getClasses();
        if (moduleClasses == null || moduleClasses.length == 0) {
            return; // Skip empty modules
        }

        // Count classes with objects in this module
        int classesWithObjects = 0;
        int totalObjectsInModule = 0;
        for (DOSchemaClass clazz : moduleClasses) {
            if (clazz.getDatabaseClass() != null && clazz.getDatabaseClass().getResolvedObjects() != null
                    && clazz.getDatabaseClass().getResolvedObjects().length > 0) {
                classesWithObjects++;
                totalObjectsInModule += clazz.getDatabaseClass().getResolvedObjects().length;
            }
        }

        if (classesWithObjects == 0) {
            return; // Skip modules with no objects
        }

        html.openTag("div", "class", "expandable-section");
        html.openTag("div", "class", "expandable-header");
        html.element("span", "<strong>Module: " + escapeHtml(module.getName()) +
                "</strong> (" + classesWithObjects + " classes with " +
                totalObjectsInModule + " objects)");
        html.element("span", "▼", "class", "expand-icon");
        html.closeTag("div"); // expandable-header

        html.openTag("div", "class", "expandable-content");

        // Generate object analysis for classes in this module that have objects
        for (DOSchemaClass clazz : moduleClasses) {
            if (clazz.getDatabaseClass() != null && clazz.getDatabaseClass().getResolvedObjects() != null) {
                generateClassObjectAnalysis(html, clazz);
            }
        }

        html.closeTag("div"); // expandable-content
        html.closeTag("div"); // expandable-section
    }

    private void generateMigrationReadinessSummary(StringBuilder html, DOSchema schema) {
        int totalObjects = 0;
        int reachableObjects = 0;
        int orphanedObjects = 0;
        int classesWithObjects = 0;
        int classesWithCollections = 0;
        int unresolvedCollections = 0;

        for (DOSchemaClass clazz : schema.getClasses()) {
            if (clazz.getDatabaseClass() != null) {
                DODatabaseObject[] resolved = clazz.getDatabaseClass().getResolvedObjects();
                if (resolved != null && resolved.length > 0) {
                    classesWithObjects++;
                    totalObjects += resolved.length;

                    DODatabaseObject[] reachable = clazz.getDatabaseClass().getReachableObjects();
                    DODatabaseObject[] orphaned = clazz.getDatabaseClass().getOrphanedObjects();

                    if (reachable != null)
                        reachableObjects += reachable.length;
                    if (orphaned != null)
                        orphanedObjects += orphaned.length;
                }

                // Check for collection fields
                for (DOField field : clazz.getFields()) {
                    if (field.isArray()) {
                        classesWithCollections++;
                        if (field.getContentTypeName() == null
                                || field.getContentTypeName().equals("java.lang.Object")) {
                            unresolvedCollections++;
                        }
                        break; // Count class only once
                    }
                }
            }
        }

        html.append("            <div class=\"expandable-section\">\n")
                .append("                <div class=\"expandable-header\">\n")
                .append("                    <span>Migration Readiness Summary</span>\n")
                .append("                    <span class=\"expand-icon\">▼</span>\n")
                .append("                </div>\n")
                .append("                <div class=\"expandable-content\">\n")
                .append("                    <div class=\"stats-grid\">\n")
                .append("                        <div class=\"stat-box\">\n")
                .append("                            <span class=\"stat-label\">Total Objects</span>\n")
                .append("                            <span class=\"stat-value\">").append(totalObjects)
                .append("</span>\n")
                .append("                        </div>\n")
                .append("                        <div class=\"stat-box\">\n")
                .append("                            <span class=\"stat-label\">Reachable Objects</span>\n")
                .append("                            <span class=\"stat-value\">").append(reachableObjects)
                .append("</span>\n")
                .append("                        </div>\n")
                .append("                        <div class=\"stat-box\">\n")
                .append("                            <span class=\"stat-label\">Orphaned Objects</span>\n")
                .append("                            <span class=\"stat-value\">").append(orphanedObjects)
                .append("</span>\n")
                .append("                        </div>\n")
                .append("                        <div class=\"stat-box\">\n")
                .append("                            <span class=\"stat-label\">Classes with Objects</span>\n")
                .append("                            <span class=\"stat-value\">").append(classesWithObjects)
                .append("</span>\n")
                .append("                        </div>\n")
                .append("                        <div class=\"stat-box\">\n")
                .append("                            <span class=\"stat-label\">Classes with Collections</span>\n")
                .append("                            <span class=\"stat-value\">").append(classesWithCollections)
                .append("</span>\n")
                .append("                        </div>\n")
                .append("                        <div class=\"stat-box\">\n")
                .append("                            <span class=\"stat-label\">Unresolved Collections</span>\n")
                .append("                            <span class=\"stat-value\">").append(unresolvedCollections)
                .append("</span>\n")
                .append("                        </div>\n")
                .append("                    </div>\n");

        // Migration readiness assessment
        double migrationReadiness = unresolvedCollections == 0 ? 100.0
                : (1.0 - (double) unresolvedCollections / classesWithCollections) * 100.0;

        String readinessClass = migrationReadiness >= 95 ? "stat-value"
                : migrationReadiness >= 80 ? "stat-value-warning" : "stat-value-error";

        html.append("                    <div class=\"migration-readiness\">\n")
                .append("                        <h3>Migration Readiness Assessment</h3>\n")
                .append("                        <div class=\"readiness-score\">\n")
                .append("                            <span class=\"readiness-label\">Overall Readiness Score:</span>\n")
                .append("                            <span class=\"").append(readinessClass).append("\">")
                .append(String.format("%.1f%%", migrationReadiness)).append("</span>\n")
                .append("                        </div>\n");

        if (unresolvedCollections > 0) {
            html.append("                        <div class=\"readiness-warning\">\n")
                    .append("                            <p><strong>⚠️ Warning:</strong> ")
                    .append(unresolvedCollections)
                    .append(" collection fields have unresolved content types that may affect migration accuracy.</p>\n")
                    .append("                        </div>\n");
        } else {
            html.append("                        <div class=\"readiness-success\">\n")
                    .append("                            <p><strong>✅ Excellent:</strong> All collection types are resolved and ready for migration.</p>\n")
                    .append("                        </div>\n");
        }

        html.append("                    </div>\n")
                .append("                </div>\n")
                .append("            </div>\n");
    }

    private void generateClassObjectAnalysis(HtmlBuilder html, DOSchemaClass clazz) {
        DODatabaseObject[] objects = clazz.getDatabaseClass().getResolvedObjects();
        if (objects == null || objects.length == 0) {
            return; // Skip classes with no objects
        }

        html.openTag("div", "class", "class-object-card");
        html.openTag("div", "class", "class-object-header");
        html.element("span", escapeHtml(clazz.getShortName()) + " (" + objects.length + " objects)");
        html.element("span", "▼", "class", "expand-icon");
        html.closeTag("div"); // class-object-header

        html.openTag("div", "class", "class-object-content");

        // Object reachability stats
        DODatabaseObject[] reachableObjects = clazz.getDatabaseClass().getReachableObjects();
        DODatabaseObject[] orphanedObjects = clazz.getDatabaseClass().getOrphanedObjects();

        html.openTag("div", "class", "object-stats");
        html.openTag("div", "class", "object-stat");
        html.element("span", "Total Objects:", "class", "stat-label");
        html.element("span", String.valueOf(objects.length), "class", "stat-value");
        html.closeTag("div"); // object-stat

        if (reachableObjects != null) {
            html.openTag("div", "class", "object-stat");
            html.element("span", "Reachable:", "class", "stat-label");
            html.element("span", String.valueOf(reachableObjects.length), "class", "stat-value-success");
            html.closeTag("div"); // object-stat
        }

        if (orphanedObjects != null && orphanedObjects.length > 0) {
            html.openTag("div", "class", "object-stat");
            html.element("span", "Orphaned:", "class", "stat-label");
            html.element("span", String.valueOf(orphanedObjects.length), "class", "stat-value-warning");
            html.closeTag("div"); // object-stat
        }

        html.closeTag("div"); // object-stats

        // Show object instances with their collections
        generateObjectInstanceTable(html, objects, clazz);

        html.closeTag("div"); // class-object-content
        html.closeTag("div"); // class-object-card
    }

    private void generateObjectInstanceTable(HtmlBuilder html, DODatabaseObject[] objects, DOSchemaClass clazz) {
        // Limit the number of objects shown for performance
        int maxObjectsToShow = Math.min(objects.length, 50);

        html.openTag("div", "class", "expandable-section");
        html.openTag("div", "class", "expandable-header");
        html.element("span", "Object Instances (showing " + maxObjectsToShow + " of " + objects.length + ")");
        html.element("span", "▼", "class", "expand-icon");
        html.closeTag("div"); // expandable-header

        html.openTag("div", "class", "expandable-content");
        html.openTag("div", "class", "object-instances-table");

        for (int i = 0; i < maxObjectsToShow; i++) {
            DODatabaseObject obj = objects[i];
            generateSingleObjectAnalysis(html, obj, clazz);
        }

        if (objects.length > maxObjectsToShow) {
            html.openTag("div", "class", "object-more-indicator");
            html.text("... and " + (objects.length - maxObjectsToShow) + " more objects");
            html.closeTag("div"); // object-more-indicator
        }

        html.closeTag("div"); // object-instances-table
        html.closeTag("div"); // expandable-content
        html.closeTag("div"); // expandable-section
    }

    private void generateSingleObjectAnalysis(HtmlBuilder html, DODatabaseObject obj, DOSchemaClass clazz) {
        html.openTag("div", "class", "object-instance");
        html.openTag("div", "class", "object-instance-header");
        html.element("span", "Object ID: " + obj.getObjectId(), "class", "object-id");

        // Reachability indicator
        if (obj.isReachable()) {
            html.element("span", "✅ Reachable", "class", "reachability-badge reachable");
        } else {
            html.element("span", "⚠️ Orphaned", "class", "reachability-badge orphaned");
        }

        html.closeTag("div"); // object-instance-header

        // Object references and collections
        DOObjectReference[] directRefs = obj.getReferences();
        DOCollectionReference[] collectionRefs = obj.getCollections();

        if ((directRefs != null && directRefs.length > 0) || (collectionRefs != null && collectionRefs.length > 0)) {
            html.openTag("div", "class", "object-references");

            // Direct references
            if (directRefs != null && directRefs.length > 0) {
                html.openTag("div", "class", "reference-section");
                html.element("strong", "Direct References (" + directRefs.length + "):");
                html.openTag("div", "class", "reference-list");

                for (int i = 0; i < Math.min(directRefs.length, 10); i++) {
                    DOObjectReference ref = directRefs[i];
                    html.openTag("div", "class", "reference-item");
                    html.element("span", escapeHtml(ref.getField().getName()), "class", "field-name");
                    html.inlineText(" → ");
                    html.element("span", String.valueOf(ref.getTargetObjectId()), "class", "object-id");
                    html.inlineText(" ");
                    html.element("span", "(" + ref.getReferenceType() + ")", "class", "reference-type");
                    html.closeTag("div"); // reference-item
                }

                if (directRefs.length > 10) {
                    html.openTag("div", "class", "reference-more");
                    html.text("... and " + (directRefs.length - 10) + " more references");
                    html.closeTag("div"); // reference-more
                }

                html.closeTag("div"); // reference-list
                html.closeTag("div"); // reference-section
            }

            // Collection references
            if (collectionRefs != null && collectionRefs.length > 0) {
                html.openTag("div", "class", "reference-section");
                html.element("strong", "Collection References (" + collectionRefs.length + "):");
                html.openTag("div", "class", "collection-list");

                for (DOCollectionReference collRef : collectionRefs) {
                    generateCollectionReferenceDetails(html, collRef);
                }

                html.closeTag("div"); // collection-list
                html.closeTag("div"); // reference-section
            }

            html.closeTag("div"); // object-references
        } else {
            html.openTag("div", "class", "no-references");
            html.element("em", "No references to other objects");
            html.closeTag("div"); // no-references
        }

        html.closeTag("div"); // object-instance
    }

    private void generateCollectionReferenceDetails(HtmlBuilder html, DOCollectionReference collRef) {
        Long[] containedIds = collRef.getContainedObjectIds();

        html.openTag("div", "class", "collection-item");
        html.openTag("div", "class", "collection-header");
        html.element("span", escapeHtml(collRef.getField().getName()), "class", "field-name");
        html.openTag("span", "class", "collection-info");
        html.inlineText("Size: " + collRef.getSize() + ", Type: ");
        html.element("span", escapeHtml(collRef.getResolvedContentType()), "class", "collection-type");
        html.closeTag("span"); // collection-info
        html.closeTag("div"); // collection-header

        if (containedIds != null && containedIds.length > 0) {
            html.openTag("div", "class", "collection-contents");
            html.element("strong", "Contains Object IDs:");
            html.openTag("div", "class", "object-id-list");

            // Show first 20 object IDs
            for (int i = 0; i < Math.min(containedIds.length, 20); i++) {
                html.element("span", String.valueOf(containedIds[i]), "class", "object-id-chip");
            }

            if (containedIds.length > 20) {
                html.element("span", "... +" + (containedIds.length - 20) + " more", "class", "object-id-more");
            }

            html.closeTag("div"); // object-id-list
            html.closeTag("div"); // collection-contents
        } else {
            html.openTag("div", "class", "collection-empty");
            html.element("em", "Collection is empty or contains only primitives");
            html.closeTag("div"); // collection-empty
        }

        html.closeTag("div"); // collection-item
    }

    private void generateOrphanObjectsAnalysis(StringBuilder html, DOSchema schema, DODatabase database) {
        HtmlBuilder builder = new HtmlBuilder(html);

        // Start orphans section
        builder.openTag("section", "id", "orphans", "class", "section")
                .element("h2", "Orphan Objects Analysis")
                .element("p",
                        "Objects that exist in the database but are not reachable through the normal reference graph.");

        // Collect all orphaned classes from both schema classes and database-only
        // classes
        List<DOClass> orphanClasses = new ArrayList<>();
        int totalOrphanObjects = 0;

        // System.out.println("DEBUG: Starting orphan analysis...");
        // System.out.println("DEBUG: Schema has " + schema.getClasses().length + "
        // classes");
        // System.out.println("DEBUG: Database has " + database.getClasses().length + "
        // classes");

        // First, check schema classes
        for (DOSchemaClass clazz : schema.getClasses()) {
            if (clazz.getDatabaseClass() != null) {
                DODatabaseObject[] orphanedObjects = clazz.getDatabaseClass().getOrphanedObjects();
                if (orphanedObjects != null && orphanedObjects.length > 0) {
                    // System.out.println("DEBUG: Found " + orphanedObjects.length + " orphaned
                    // objects in schema class: "
                    // + clazz.getShortName());
                    orphanClasses.add(clazz.getDatabaseClass());
                    totalOrphanObjects += orphanedObjects.length;
                }
            }
        }

        // Also check database-only classes that don't have schema mappings
        if (database != null) {
            for (DODatabaseClass dbClass : database.getClasses()) {
                // Check if this database class already has a schema mapping
                boolean hasSchemaMapping = false;
                for (DOSchemaClass schemaClass : schema.getClasses()) {
                    if (schemaClass.getDatabaseClass() == dbClass) {
                        hasSchemaMapping = true;
                        break;
                    }
                }

                // If no schema mapping and has orphaned objects, include it
                if (!hasSchemaMapping) {
                    DODatabaseObject[] orphanedObjects = dbClass.getOrphanedObjects();
                    if (orphanedObjects != null && orphanedObjects.length > 0) {
                        // System.out.println("DEBUG: Found " + orphanedObjects.length
                        // + " orphaned objects in database-only class: " + dbClass.getShortName());
                        orphanClasses.add(dbClass);
                        totalOrphanObjects += orphanedObjects.length;
                    }
                }
            }
        }

        // System.out.println("DEBUG: Total orphan classes found: " +
        // orphanClasses.size());
        // System.out.println("DEBUG: Total orphan objects found: " +
        // totalOrphanObjects);

        if (orphanClasses.isEmpty()) {
            // System.out.println(
            // "DEBUG: No orphan classes found, but this seems wrong - let me check
            // individual classes...");

            // Debug: Check each schema class individually
            for (DOSchemaClass clazz : schema.getClasses()) {
                DODatabaseClass dbClass = clazz.getDatabaseClass();
                if (dbClass != null) {
                    // DODatabaseObject[] resolved = dbClass.getResolvedObjects();
                    // DODatabaseObject[] orphaned = dbClass.getOrphanedObjects();
                    // System.out.println("DEBUG: Schema class " + clazz.getShortName() + " -
                    // resolved: " +
                    // (resolved != null ? resolved.length : "null") + ", orphaned: " +
                    // (orphaned != null ? orphaned.length : "null"));
                }
            }

            // Debug: Check each database class individually
            // for (DODatabaseClass dbClass : database.getClasses()) {
            // DODatabaseObject[] resolved = dbClass.getResolvedObjects();
            // DODatabaseObject[] orphaned = dbClass.getOrphanedObjects();
            // System.out.println("DEBUG: Database class " + dbClass.getShortName() + " -
            // resolved: " +
            // (resolved != null ? resolved.length : "null") + ", orphaned: " +
            // (orphaned != null ? orphaned.length : "null"));
            // }

            // System.out.println("DEBUG: No orphan classes found, skipping orphan analysis
            // section");
            builder.element("p", null)
                    .inlineRawHtml(
                            "<strong>✅ No orphaned objects found!</strong> All objects in the database are reachable through the normal reference graph.")
                    .closeTag("section");
            return; // Skip if no orphan objects
        }

        // Sort orphan classes by full name
        orphanClasses.sort((a, b) -> a.getAbsoluteName().compareTo(b.getAbsoluteName()));

        // Create expandable section for orphan analysis
        builder.openTag("div", "class", "expandable-section", "id", "orphans")
                .openTag("div", "class", "expandable-header")
                .openTag("span")
                .inlineRawHtml("<strong>🔍 Orphan Objects Analysis</strong> (")
                .inlineText(String.valueOf(orphanClasses.size()))
                .inlineText(" classes with ")
                .inlineText(String.valueOf(totalOrphanObjects))
                .inlineText(" orphaned objects)")
                .closeTag("span")
                .span("▼", "expand-icon")
                .closeTag("div")
                .openTag("div", "class", "expandable-content")
                .element("p", null)
                .inlineRawHtml(
                        "<strong>Orphan objects</strong> are objects that exist in the database but are not reachable through the normal object graph traversal. ")
                .inlineText(
                        "This could indicate issues with schema mapping, missing references, or data integrity problems.");

        // Generate each orphan class section
        for (DOClass clazz : orphanClasses) {
            if (clazz instanceof DODatabaseClass) {
                generateOrphanDatabaseClassSection(builder, (DODatabaseClass) clazz, schema);
            } else {
                // This is a schema class, get its database class
                DOSchemaClass schemaClass = findSchemaClassForDatabaseClass(schema, clazz);
                if (schemaClass != null) {
                    generateOrphanClassSection(builder, schemaClass, schema);
                }
            }
        }

        // Close all sections
        builder.closeTag("div") // expandable-content
                .closeTag("div") // expandable-section
                .closeTag("section"); // orphans section
    }

    /**
     * Find the schema class that corresponds to a database class
     */
    private DOSchemaClass findSchemaClassForDatabaseClass(DOSchema schema, DOClass dbClass) {
        for (DOSchemaClass schemaClass : schema.getClasses()) {
            if (schemaClass.getDatabaseClass() == dbClass) {
                return schemaClass;
            }
        }
        return null;
    }

    /**
     * Generate orphan section for database-only classes (no schema mapping)
     */
    private void generateOrphanDatabaseClassSection(HtmlBuilder builder, DODatabaseClass dbClass, DOSchema schema) {
        DODatabaseObject[] orphanedObjects = dbClass.getOrphanedObjects();
        if (orphanedObjects == null || orphanedObjects.length == 0) {
            return;
        }

        // Determine if this is a collection class using the database class name
        boolean isCollectionClass = isCollectionClass(dbClass);
        String headerColor = isCollectionClass ? "#fd7e14" : "#dc3545";
        String collectionIndicator = isCollectionClass ? " 📦" : "";

        builder.openTag("div", "class", "class-object-card")
                .openTag("div", "class", "class-object-header", "style", "background-color: " + headerColor + ";")
                .openTag("span")
                .inlineRawHtml("<strong>")
                .inlineText(dbClass.getAbsoluteName())
                .inlineRawHtml("</strong>")
                .inlineText(collectionIndicator)
                .inlineRawHtml(" <span class=\"readiness-warning\">⚠️ Database-Only Class</span> (")
                .inlineText(String.valueOf(orphanedObjects.length))
                .inlineText(" orphaned objects)")
                .closeTag("span")
                .span("▼", "expand-icon")
                .closeTag("div")
                .openTag("div", "class", "class-object-content")
                .openTag("div", "class", "migration-readiness")
                .openTag("div", "class", "readiness-warning")
                .element("p", null)
                .inlineRawHtml(
                        "<strong>⚠️ No Schema Mapping:</strong> This class exists in the database but has no corresponding schema definition. ")
                .inlineText("Objects from this class may be lost during migration unless a schema mapping is created.")
                .closeTag("div")
                .closeTag("div");

        if (isCollectionClass) {
            generateCollectionOrphanObjectsList(builder.getStringBuilder(), orphanedObjects, dbClass, schema);
        } else {
            generateStandardOrphanObjectsList(builder, orphanedObjects, dbClass, schema);
        }

        builder.closeTag("div") // class-object-content
                .closeTag("div"); // class-object-card
    }

    /**
     * Generate standard orphan objects list for non-collection classes
     */
    private void generateStandardOrphanObjectsList(HtmlBuilder builder, DODatabaseObject[] objects, DOClass clazz,
            DOSchema schema) {
        builder.openTag("div", "class", "object-instances-table");

        int displayLimit = Math.min(objects.length, 10);

        for (int i = 0; i < displayLimit; i++) {
            DODatabaseObject obj = objects[i];
            builder.openTag("div", "class", "object-instance")
                    .openTag("div", "class", "object-instance-header")
                    .openTag("span", "class", "object-id")
                    .inlineText("Object ID: " + obj.getObjectId())
                    .closeTag("span")
                    .span("🔴 Orphaned", "reachability-badge orphaned")
                    .closeTag("div");

            // Show direct references if any
            DOObjectReference[] directRefs = obj.getReferences();
            if (directRefs != null && directRefs.length > 0) {
                builder.openTag("div", "class", "object-references")
                        .openTag("div", "class", "reference-section")
                        .openTag("strong")
                        .inlineText("Direct References (" + directRefs.length + "):")
                        .closeTag("strong")
                        .openTag("div", "class", "reference-list");

                for (DOObjectReference ref : directRefs) {
                    DOField field = ref.getField();
                    String fieldName = field != null ? field.getName() : "unknown";
                    String fieldType = field != null ? field.getTypeName() : "unknown";
                    String fieldTypeStyle = getClassTypeStyleClass(fieldType, schema);

                    builder.openTag("div", "class", "reference-item")
                            .span(fieldName, "field-name")
                            .inlineText(" → ")
                            .openTag("span", "class", "object-id")
                            .inlineText("ID: " + ref.getTargetObjectId())
                            .closeTag("span")
                            .inlineText(" (type: ")
                            .span(fieldType, fieldTypeStyle)
                            .inlineText(")")
                            .closeTag("div");
                }

                builder.closeTag("div") // reference-list
                        .closeTag("div") // reference-section
                        .closeTag("div"); // object-references
            } else {
                builder.openTag("div", "class", "no-references")
                        .element("em", "No outgoing references found")
                        .closeTag("div");
            }

            builder.closeTag("div"); // object-instance
        }

        if (objects.length > displayLimit) {
            builder.openTag("div", "class", "object-more-indicator")
                    .openTag("em")
                    .inlineText("... and " + (objects.length - displayLimit) + " more orphaned objects")
                    .closeTag("em")
                    .closeTag("div");
        }

        builder.closeTag("div"); // object-instances-table
    }

    private void generateOrphanClassSection(HtmlBuilder builder, DOSchemaClass clazz, DOSchema schema) {
        DODatabaseObject[] orphanedObjects = clazz.getDatabaseClass().getOrphanedObjects();
        if (orphanedObjects == null || orphanedObjects.length == 0) {
            return;
        }

        // Determine if this is a collection class
        boolean isCollectionClass = isCollectionClass(clazz);
        String headerColor = isCollectionClass ? "#fd7e14" : "#dc3545"; // Orange for collections, red for others
        String collectionIndicator = isCollectionClass ? " 📦" : "";

        StringBuilder html = builder.getStringBuilder();
        html.append("                    <div class=\"class-object-card\">\n")
                .append("                        <div class=\"class-object-header\" style=\"background: ")
                .append(headerColor).append(";\">\n")
                .append("                            <span>").append(escapeHtml(clazz.getShortName()))
                .append(collectionIndicator)
                .append(" (").append(orphanedObjects.length).append(" orphaned objects)</span>\n")
                .append("                            <span class=\"expand-icon\">▼</span>\n")
                .append("                        </div>\n")
                .append("                        <div class=\"class-object-content\">\n");

        // Class basic info
        html.append("                            <div class=\"inheritance-info\">\n")
                .append("                                <strong>Full Class Name:</strong> ")
                .append(escapeHtml(clazz.getAbsoluteName())).append("<br>\n");

        if (clazz.getSuperClassAbsoluteName() != null && !clazz.getSuperClassAbsoluteName().isEmpty()) {
            String superClassStyle = getClassTypeStyleClass(clazz.getSuperClassAbsoluteName(), schema);
            html.append("                                <strong>Extends:</strong> ")
                    .append("<span class=\"").append(superClassStyle).append("\">")
                    .append(escapeHtml(clazz.getSuperClassAbsoluteName())).append("</span><br>\n");
        }

        html.append("                            </div>\n");

        // Show references from other classes
        generateIncomingReferencesInfo(html, clazz, schema);

        // Show orphaned objects details with collection-specific analysis
        html.append("                            <h4>Orphaned Objects (").append(orphanedObjects.length)
                .append(")</h4>\n");

        if (isCollectionClass) {
            html.append("                            <div class=\"readiness-warning\">\n")
                    .append("                                <p><strong>📦 Collection Class Analysis:</strong> This appears to be a collection class. ")
                    .append("Orphaned collection objects often indicate missing references or problems with collection content type resolution.</p>\n")
                    .append("                            </div>\n");
            generateCollectionOrphanObjectsList(builder.getStringBuilder(), orphanedObjects, clazz, schema);
        } else {
            generateOrphanObjectsList(builder.getStringBuilder(), orphanedObjects);
        }

        html.append("                        </div>\n")
                .append("                    </div>\n");
    }

    private void generateIncomingReferencesInfo(StringBuilder html, DOSchemaClass clazz, DOSchema schema) {
        DOReference[] references = clazz.getReferences();

        if (references != null && references.length > 0) {
            html.append("                            <h4>Known References from Other Classes</h4>\n")
                    .append("                            <div class=\"reference-details\">\n");

            for (DOReference reference : references) {
                DOClass referencedClass = reference.getReferencedClass();
                DOField referencedField = reference.getReferencedField();

                if (referencedClass != null && referencedField != null) {
                    String referencingClassStyle = getClassTypeStyleClass(referencedClass.getAbsoluteName(), schema);
                    String fieldTypeStyle = getClassTypeStyleClass(referencedField.getTypeName(), schema);

                    html.append("                                <div class=\"reference-item\">\n")
                            .append("                                    <span class=\"").append(referencingClassStyle)
                            .append("\">")
                            .append(escapeHtml(referencedClass.getAbsoluteName())).append("</span> → ")
                            .append("field: <span class=\"field-name\">").append(escapeHtml(referencedField.getName()))
                            .append("</span> ")
                            .append("(type: <span class=\"").append(fieldTypeStyle).append("\">")
                            .append(escapeHtml(referencedField.getTypeName())).append("</span>)\n")
                            .append("                                </div>\n");
                }
            }

            html.append("                            </div>\n");
        } else {
            html.append("                            <div class=\"readiness-warning\">\n")
                    .append("                                <p><strong>⚠️ No Known References:</strong> This class has no known references from other classes, ")
                    .append("which may explain why its objects are orphaned.</p>\n")
                    .append("                            </div>\n");
        }
    }

    private void generateOrphanObjectsList(StringBuilder html, DODatabaseObject[] objects) {
        html.append("                            <div class=\"object-instances-table\">\n");

        int displayLimit = Math.min(objects.length, 50); // Limit display for performance

        for (int i = 0; i < displayLimit; i++) {
            DODatabaseObject obj = objects[i];
            html.append("                                <div class=\"object-instance\">\n")
                    .append("                                    <div class=\"object-instance-header\">\n")
                    .append("                                        <span class=\"object-id\">Object ID: ")
                    .append(obj.getObjectId()).append("</span>\n")
                    .append("                                        <span class=\"reachability-badge orphaned\">Orphaned</span>\n")
                    .append("                                    </div>\n");

            // Show any references this object might have
            DOObjectReference[] directRefs = obj.getReferences();
            DOCollectionReference[] collectionRefs = obj.getCollections();

            if ((directRefs != null && directRefs.length > 0)
                    || (collectionRefs != null && collectionRefs.length > 0)) {
                html.append("                                    <div class=\"object-references\">\n");

                if (directRefs != null && directRefs.length > 0) {
                    html.append("                                        <div class=\"reference-section\">\n")
                            .append("                                            <strong>Direct References (")
                            .append(directRefs.length).append("):</strong>\n")
                            .append("                                            <div class=\"reference-list\">\n");

                    for (DOObjectReference ref : directRefs) {
                        DOField field = ref.getField();
                        String fieldName = field != null ? field.getName() : "unknown";

                        html.append("                                                <div class=\"reference-item\">\n")
                                .append("                                                    <span class=\"field-name\">")
                                .append(escapeHtml(fieldName)).append("</span> → ")
                                .append("<span class=\"object-id\">ID: ").append(ref.getTargetObjectId())
                                .append("</span>\n")
                                .append("                                                </div>\n");
                    }

                    html.append("                                            </div>\n")
                            .append("                                        </div>\n");
                }

                if (collectionRefs != null && collectionRefs.length > 0) {
                    html.append("                                        <div class=\"reference-section\">\n")
                            .append("                                            <strong>Collection References (")
                            .append(collectionRefs.length).append("):</strong>\n")
                            .append("                                            <div class=\"collection-list\">\n");

                    for (DOCollectionReference collRef : collectionRefs) {
                        Long[] targetIds = collRef.getContainedObjectIds();
                        int targetCount = targetIds != null ? targetIds.length : 0;
                        DOField field = collRef.getField();
                        String fieldName = field != null ? field.getName() : "unknown";

                        html.append("                                                <div class=\"collection-item\">\n")
                                .append("                                                    <div class=\"collection-header\">\n")
                                .append("                                                        <span class=\"field-name\">")
                                .append(escapeHtml(fieldName)).append("</span>\n")
                                .append("                                                        <span class=\"collection-info\">")
                                .append(targetCount).append(" items</span>\n")
                                .append("                                                    </div>\n");

                        if (targetCount > 0) {
                            html.append(
                                    "                                                    <div class=\"object-id-list\">\n");
                            int displayMax = Math.min(targetCount, 10);
                            for (int j = 0; j < displayMax; j++) {
                                html.append(
                                        "                                                        <span class=\"object-id-chip\">")
                                        .append(targetIds[j]).append("</span>\n");
                            }
                            if (targetCount > displayMax) {
                                html.append(
                                        "                                                        <span class=\"object-id-more\">+")
                                        .append(targetCount - displayMax).append(" more</span>\n");
                            }
                            html.append("                                                    </div>\n");
                        }

                        html.append("                                                </div>\n");
                    }

                    html.append("                                            </div>\n")
                            .append("                                        </div>\n");
                }

                html.append("                                    </div>\n");
            } else {
                html.append(
                        "                                    <div class=\"no-references\">No outgoing references found</div>\n");
            }

            html.append("                                </div>\n");
        }

        if (objects.length > displayLimit) {
            html.append("                                <div class=\"object-more-indicator\">\n")
                    .append("                                    <em>... and ").append(objects.length - displayLimit)
                    .append(" more orphaned objects</em>\n")
                    .append("                                </div>\n");
        }

        html.append("                            </div>\n");
    }

    /**
     * Helper method to determine if a class represents a collection type
     */
    private boolean isCollectionClass(DOSchemaClass clazz) {
        if (clazz == null) {
            return false;
        }

        String className = clazz.getAbsoluteName();
        String simpleName = clazz.getShortName();

        // Check class name patterns
        if (className != null && (className.contains("Vector") || className.contains("Collection") ||
                className.contains("List") || className.contains("Set") ||
                className.contains("Map") || className.contains("Array") ||
                className.contains("VectRech"))) {
            return true;
        }

        if (simpleName != null && (simpleName.contains("Vector") || simpleName.contains("Collection") ||
                simpleName.contains("List") || simpleName.contains("Set") ||
                simpleName.contains("Map") || simpleName.contains("Array") ||
                simpleName.contains("VectRech"))) {
            return true;
        }

        // Check if the class has collection fields
        DOField[] fields = clazz.getFields();
        if (fields != null) {
            for (DOField field : fields) {
                if (field.isArray() || (field.getTypeName() != null &&
                        (field.getTypeName().contains("Collection") || field.getTypeName().contains("Vector") ||
                                field.getTypeName().contains("List") || field.getTypeName().contains("Set") ||
                                field.getTypeName().contains("Map")))) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Helper method to determine if a database class represents a collection type
     */
    private boolean isCollectionClass(DOClass clazz) {
        if (clazz == null) {
            return false;
        }

        String className = clazz.getAbsoluteName();
        String simpleName = clazz.getShortName();

        // Check class name patterns
        if (className != null && (className.contains("Vector") || className.contains("Collection") ||
                className.contains("List") || className.contains("Set") ||
                className.contains("Map") || className.contains("Array") ||
                className.contains("VectRech"))) {
            return true;
        }

        if (simpleName != null && (simpleName.contains("Vector") || simpleName.contains("Collection") ||
                simpleName.contains("List") || simpleName.contains("Set") ||
                simpleName.contains("Map") || simpleName.contains("Array") ||
                simpleName.contains("VectRech"))) {
            return true;
        }

        // Check if the class has collection fields
        DOField[] fields = clazz.getFields();
        if (fields != null) {
            for (DOField field : fields) {
                if (field.isArray() || (field.getTypeName() != null &&
                        (field.getTypeName().contains("Collection") || field.getTypeName().contains("Vector") ||
                                field.getTypeName().contains("List") || field.getTypeName().contains("Set") ||
                                field.getTypeName().contains("Map")))) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Generate enhanced orphan objects list for collection classes with detailed
     * content analysis
     */
    private void generateCollectionOrphanObjectsList(StringBuilder html, DODatabaseObject[] objects,
            DOSchemaClass clazz, DOSchema schema) {
        html.append("                            <div class=\"object-instances-table\">\n");

        int displayLimit = Math.min(objects.length, 20); // Show fewer for collection analysis

        for (int i = 0; i < displayLimit; i++) {
            DODatabaseObject obj = objects[i];
            html.append("                                <div class=\"object-instance\">\n")
                    .append("                                    <div class=\"object-instance-header\">\n")
                    .append("                                        <span class=\"object-id\">Object ID: ")
                    .append(obj.getObjectId()).append("</span>\n")
                    .append("                                        <span class=\"reachability-badge orphaned\">📦 Orphaned Collection</span>\n")
                    .append("                                    </div>\n");

            // Analyze collection contents in detail
            DOCollectionReference[] collectionRefs = obj.getCollections();

            if (collectionRefs != null && collectionRefs.length > 0) {
                html.append("                                    <div class=\"object-references\">\n")
                        .append("                                        <div class=\"reference-section\">\n")
                        .append("                                            <strong>📦 Collection Contents Analysis (")
                        .append(collectionRefs.length).append(" collections):</strong>\n")
                        .append("                                            <div class=\"collection-list\">\n");

                for (DOCollectionReference collRef : collectionRefs) {
                    generateDetailedCollectionAnalysis(html, collRef, schema);
                }

                html.append("                                            </div>\n")
                        .append("                                        </div>\n")
                        .append("                                    </div>\n");
            } else {
                html.append("                                    <div class=\"readiness-warning\">\n")
                        .append("                                        <p><strong>⚠️ No Collection References:</strong> This collection object has no detected collection references, ")
                        .append("which may indicate a problem with collection field resolution or the object may be corrupted.</p>\n")
                        .append("                                    </div>\n");
            }

            // Also show direct references if any
            DOObjectReference[] directRefs = obj.getReferences();
            if (directRefs != null && directRefs.length > 0) {
                html.append("                                    <div class=\"object-references\">\n")
                        .append("                                        <div class=\"reference-section\">\n")
                        .append("                                            <strong>Direct References (")
                        .append(directRefs.length).append("):</strong>\n")
                        .append("                                            <div class=\"reference-list\">\n");

                for (DOObjectReference ref : directRefs) {
                    DOField field = ref.getField();
                    String fieldName = field != null ? field.getName() : "unknown";
                    String fieldType = field != null ? field.getTypeName() : "unknown";
                    String fieldTypeStyle = getClassTypeStyleClass(fieldType, schema);

                    html.append("                                                <div class=\"reference-item\">\n")
                            .append("                                                    <span class=\"field-name\">")
                            .append(escapeHtml(fieldName)).append("</span> → ")
                            .append("<span class=\"object-id\">ID: ").append(ref.getTargetObjectId())
                            .append("</span> (type: <span class=\"").append(fieldTypeStyle).append("\">")
                            .append(escapeHtml(fieldType)).append("</span>)\n")
                            .append("                                                </div>\n");
                }

                html.append("                                            </div>\n")
                        .append("                                        </div>\n")
                        .append("                                    </div>\n");
            }

            html.append("                                </div>\n");
        }

        if (objects.length > displayLimit) {
            html.append("                                <div class=\"object-more-indicator\">\n")
                    .append("                                    <em>... and ").append(objects.length - displayLimit)
                    .append(" more orphaned collection objects</em>\n")
                    .append("                                </div>\n");
        }

        html.append("                            </div>\n");
    }

    /**
     * Generate detailed analysis for a single collection reference
     */
    private void generateDetailedCollectionAnalysis(StringBuilder html, DOCollectionReference collRef,
            DOSchema schema) {
        Long[] containedIds = collRef.getContainedObjectIds();
        DOField field = collRef.getField();
        String fieldName = field != null ? field.getName() : "unknown";
        String fieldType = field != null ? field.getTypeName() : "unknown";
        String contentType = collRef.getResolvedContentType();
        String fieldTypeStyle = getClassTypeStyleClass(fieldType, schema);
        String contentTypeStyle = getClassTypeStyleClass(contentType, schema);

        html.append("                                                <div class=\"collection-item\">\n")
                .append("                                                    <div class=\"collection-header\">\n")
                .append("                                                        <span class=\"field-name\">")
                .append(escapeHtml(fieldName)).append("</span>\n")
                .append("                                                        <span class=\"collection-info\">\n")
                .append("                                                            Size: ")
                .append(collRef.getSize()).append(", ")
                .append("Collection Type: <span class=\"").append(fieldTypeStyle).append("\">")
                .append(escapeHtml(fieldType)).append("</span>\n")
                .append("                                                        </span>\n")
                .append("                                                    </div>\n");

        // Content type analysis
        html.append("                                                    <div class=\"collection-contents\">\n")
                .append("                                                        <strong>📋 Content Type Analysis:</strong><br>\n")
                .append("                                                        Expected Content: <span class=\"")
                .append(contentTypeStyle).append("\">").append(escapeHtml(contentType)).append("</span>");

        // Check if content type is properly resolved
        if (contentType == null || contentType.equals("java.lang.Object") || contentType.equals("Unknown")) {
            html.append(" <span class=\"readiness-warning\">⚠️ Unresolved</span>");
        } else if (isClassKnownInSchema(contentType, schema)) {
            html.append(" <span class=\"stat-value-success\">✅ Schema Known</span>");
        } else {
            html.append(" <span class=\"readiness-warning\">❓ Not in Schema</span>");
        }

        html.append("<br>\n");

        if (containedIds != null && containedIds.length > 0) {
            html.append("                                                        <strong>📦 Contained Object IDs (")
                    .append(containedIds.length).append(" objects):</strong>\n")
                    .append("                                                        <div class=\"object-id-list\">\n");

            // Show first 15 object IDs with more detail
            for (int i = 0; i < Math.min(containedIds.length, 15); i++) {
                html.append(
                        "                                                            <span class=\"object-id-chip\">")
                        .append(containedIds[i]).append("</span>\n");
            }

            if (containedIds.length > 15) {
                html.append(
                        "                                                            <span class=\"object-id-more\">... +")
                        .append(containedIds.length - 15).append(" more</span>\n");
            }

            html.append("                                                        </div>\n");

            // Add analysis hints
            if (containedIds.length == 0) {
                html.append("                                                        <div class=\"readiness-warning\">")
                        .append("⚠️ Empty collection - may indicate missing data or resolution issues</div>\n");
            } else if (contentType.equals("java.lang.Object")) {
                html.append("                                                        <div class=\"readiness-warning\">")
                        .append("⚠️ Untyped collection content - consider adding content type mapping</div>\n");
            }
        } else {
            html.append("                                                        <div class=\"collection-empty\">\n")
                    .append("                                                            <em>❌ No contained objects detected</em><br>\n")
                    .append("                                                            This could indicate:\n")
                    .append("                                                            <ul style=\"margin: 5px 0; padding-left: 20px; font-size: 0.9em;\">\n")
                    .append("                                                                <li>Collection is genuinely empty</li>\n")
                    .append("                                                                <li>Collection contains only primitive values</li>\n")
                    .append("                                                                <li>Collection reference resolution failed</li>\n")
                    .append("                                                                <li>Database corruption or activation issues</li>\n")
                    .append("                                                            </ul>\n")
                    .append("                                                        </div>\n");
        }

        html.append("                                                    </div>\n")
                .append("                                                </div>\n");
    }

    /**
     * Generate enhanced orphan objects list for collection classes (overload for
     * DOClass)
     */
    private void generateCollectionOrphanObjectsList(StringBuilder html, DODatabaseObject[] objects, DOClass clazz,
            DOSchema schema) {
        html.append("                            <div class=\"object-instances-table\">\n");

        int displayLimit = Math.min(objects.length, 20); // Show fewer for collection analysis

        for (int i = 0; i < displayLimit; i++) {
            DODatabaseObject obj = objects[i];
            html.append("                                <div class=\"object-instance\">\n")
                    .append("                                    <div class=\"object-instance-header\">\n")
                    .append("                                        <span class=\"object-id\">Object ID: ")
                    .append(obj.getObjectId()).append("</span>\n")
                    .append("                                        <span class=\"reachability-badge orphaned\">📦 Orphaned Collection</span>\n")
                    .append("                                    </div>\n");

            // Analyze collection contents in detail
            DOCollectionReference[] collectionRefs = obj.getCollections();

            if (collectionRefs != null && collectionRefs.length > 0) {
                html.append("                                    <div class=\"object-references\">\n")
                        .append("                                        <div class=\"reference-section\">\n")
                        .append("                                            <strong>📦 Collection Contents Analysis (")
                        .append(collectionRefs.length).append(" collections):</strong>\n")
                        .append("                                            <div class=\"collection-list\">\n");

                for (DOCollectionReference collRef : collectionRefs) {
                    generateDetailedCollectionAnalysis(html, collRef, schema);
                }

                html.append("                                            </div>\n")
                        .append("                                        </div>\n")
                        .append("                                    </div>\n");
            } else {
                html.append("                                    <div class=\"readiness-warning\">\n")
                        .append("                                        <p><strong>⚠️ No Collection References:</strong> This collection object has no detected collection references, ")
                        .append("which may indicate a problem with collection field resolution or the object may be corrupted.</p>\n")
                        .append("                                    </div>\n");
            }

            // Also show direct references if any
            DOObjectReference[] directRefs = obj.getReferences();
            if (directRefs != null && directRefs.length > 0) {
                html.append("                                    <div class=\"object-references\">\n")
                        .append("                                        <div class=\"reference-section\">\n")
                        .append("                                            <strong>Direct References (")
                        .append(directRefs.length).append("):</strong>\n")
                        .append("                                            <div class=\"reference-list\">\n");

                for (DOObjectReference ref : directRefs) {
                    DOField field = ref.getField();
                    String fieldName = field != null ? field.getName() : "unknown";
                    String fieldType = field != null ? field.getTypeName() : "unknown";
                    String fieldTypeStyle = getClassTypeStyleClass(fieldType, schema);

                    html.append("                                                <div class=\"reference-item\">\n")
                            .append("                                                    <span class=\"field-name\">")
                            .append(escapeHtml(fieldName)).append("</span> → ")
                            .append("<span class=\"object-id\">ID: ").append(ref.getTargetObjectId())
                            .append("</span> (type: <span class=\"").append(fieldTypeStyle).append("\">")
                            .append(escapeHtml(fieldType)).append("</span>)\n")
                            .append("                                                </div>\n");
                }

                html.append("                                            </div>\n")
                        .append("                                        </div>\n")
                        .append("                                    </div>\n");
            }

            html.append("                                </div>\n");
        }

        if (objects.length > displayLimit) {
            html.append("                                <div class=\"object-more-indicator\">\n")
                    .append("                                    <em>... and ").append(objects.length - displayLimit)
                    .append(" more orphaned collection objects</em>\n")
                    .append("                                </div>\n");
        }

        html.append("                            </div>\n");
    }

    private boolean isClassKnownInSchema(String className, DOSchema schema) {
        if (className == null || className.isEmpty()) {
            return false;
        }

        // Check if the class exists in the schema
        for (DOSchemaClass schemaClass : schema.getClasses()) {
            // Only match by absolute name for reliable resolution
            if (className.equals(schemaClass.getAbsoluteName())) {
                return true;
            }
        }

        return false;
    }

    private String getClassTypeStyleClass(String className, DOSchema schema) {
        if (className == null || className.isEmpty()) {
            return "object-type"; // Default fallback
        }

        // Check if it's a primitive type
        if (isPrimitiveType(className)) {
            return "primitive-type";
        }

        // Check if the class is known in the schema
        if (isClassKnownInSchema(className, schema)) {
            return "schema-known-type";
        } else {
            return "schema-unknown-type";
        }
    }

    private boolean isPrimitiveType(String typeName) {
        if (typeName == null)
            return false;

        // Java primitive types and their wrapper classes
        return typeName.equals("boolean") || typeName.equals("java.lang.Boolean") ||
                typeName.equals("byte") || typeName.equals("java.lang.Byte") ||
                typeName.equals("short") || typeName.equals("java.lang.Short") ||
                typeName.equals("int") || typeName.equals("java.lang.Integer") ||
                typeName.equals("long") || typeName.equals("java.lang.Long") ||
                typeName.equals("float") || typeName.equals("java.lang.Float") ||
                typeName.equals("double") || typeName.equals("java.lang.Double") ||
                typeName.equals("char") || typeName.equals("java.lang.Character") ||
                typeName.equals("java.lang.String") ||
                typeName.equals("java.util.Date");
    }

    private String escapeHtml(String text) {
        if (text == null)
            return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
