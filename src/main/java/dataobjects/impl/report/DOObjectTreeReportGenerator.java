package dataobjects.impl.report;

import dataobjects.impl.report.DOObjectTreeReportGenerator;
import dataobjects.impl.engine.DOEngine;
import dataobjects.impl.models.schema.DOSchema;
import dataobjects.impl.models.schema.DOSchemaClass;
import dataobjects.impl.models.schema.DOSchemaModule;
import dataobjects.impl.models.database.DODatabase;
import dataobjects.impl.models.database.DODatabaseClass;
import dataobjects.impl.models.database.DODatabaseObject;
import dataobjects.impl.models.database.DOObjectReference;
import dataobjects.impl.models.database.DOCollectionReference;
import dataobjects.util.HtmlBuilder;
import dataobjects.impl.models.DOClass;
import dataobjects.impl.models.DOField;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of DOObjectTreeReportGenerator that creates an interactive
 * HTML tree view of actual database objects with module-based starting points
 * and unreachable object analysis.
 */
public class DOObjectTreeReportGenerator {

    private static final String DEFAULT_OUTPUT_DIR = "output";
    private static final String DEFAULT_FILENAME = "Object Tree.html";
    private static final int MAX_TREE_DEPTH = 8;

    public void generateReport(DOEngine engine, String outputPath) throws IOException {
        DOSchema schema = engine.getSchema();
        DODatabase database = engine.getDatabase();

        HtmlBuilder html = new HtmlBuilder();

        // Generate HTML structure
        generateHtmlHeader(html);
        generateTreeOverview(html, schema, database);
        generateModuleTreeSection(html, schema, database);
        generateUnreachableObjectsSection(html, schema, database);
        generateHtmlFooter(html);

        // Write to file
        Files.createDirectories(Paths.get(outputPath).getParent());
        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.write(html.toString());
        }

        System.out.println("Object tree report generated: " + outputPath);
    }

    public void generateDefaultReport(DOEngine engine) throws IOException {
        String outputPath = DEFAULT_OUTPUT_DIR + File.separator + DEFAULT_FILENAME;
        generateReport(engine, outputPath);
    }

    private void generateHtmlHeader(HtmlBuilder html) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        html.rawHtml("<!DOCTYPE html>");
        html.openTag("html", "lang", "en");
        html.openTag("head");
        html.element("meta", null, "charset", "UTF-8");
        html.element("meta", null, "name", "viewport", "content", "width=device-width, initial-scale=1.0");
        html.element("title", "Object Tree Report");
        html.openTag("style");
        html.rawHtml(generateCSS());
        html.closeTag("style");
        html.closeTag("head");
        html.openTag("body");
        html.openTag("div", "class", "container");
        html.openTag("header");
        html.element("h1", "Database Object Tree Report");
        html.element("p", "Generated on: " + timestamp, "class", "timestamp");
        html.closeTag("header");
        html.openTag("nav", "class", "toc");
        html.element("h2", "Navigation");
        html.openTag("ul");
        html.openTag("li");
        html.inlineRawHtml("<a href=\"#overview\">Tree Overview</a>");
        html.closeTag("li");
        html.openTag("li");
        html.inlineRawHtml("<a href=\"#modules\">Module Trees</a>");
        html.closeTag("li");
        html.openTag("li");
        html.inlineRawHtml("<a href=\"#unreachable\">Unreachable Objects</a>");
        html.closeTag("li");
        html.closeTag("ul");
        html.closeTag("nav");
    }

    private void generateHtmlFooter(HtmlBuilder html) {
        html.closeTag("div"); // container
        html.openTag("script");
        html.rawHtml(generateJavaScript());
        html.closeTag("script");
        html.closeTag("body");
        html.closeTag("html");
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
                .append("    max-width: 1400px;\n")
                .append("    margin: 0 auto;\n")
                .append("    padding: 20px;\n")
                .append("    background: white;\n")
                .append("    box-shadow: 0 0 20px rgba(0,0,0,0.1);\n")
                .append("}\n")
                .append("header {\n")
                .append("    text-align: center;\n")
                .append("    margin-bottom: 30px;\n")
                .append("    padding-bottom: 20px;\n")
                .append("    border-bottom: 2px solid #28a745;\n")
                .append("}\n")
                .append("header h1 {\n")
                .append("    color: #28a745;\n")
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
                .append("    color: #28a745;\n")
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
                .append("    color: #28a745;\n")
                .append("    border-bottom: 2px solid #28a745;\n")
                .append("    padding-bottom: 10px;\n")
                .append("    margin-top: 0;\n")
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
                .append("    border-left: 4px solid #28a745;\n")
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
                .append("    color: #28a745;\n")
                .append("}\n")
                .append(".tree-container {\n")
                .append("    background: white;\n")
                .append("    border: 1px solid #ddd;\n")
                .append("    border-radius: 8px;\n")
                .append("    margin: 20px 0;\n")
                .append("    overflow: hidden;\n")
                .append("}\n")
                .append(".tree-node {\n")
                .append("    border-left: 2px solid #e9ecef;\n")
                .append("    margin-left: 20px;\n")
                .append("    position: relative;\n")
                .append("}\n")
                .append(".tree-node:before {\n")
                .append("    content: '';\n")
                .append("    position: absolute;\n")
                .append("    left: -2px;\n")
                .append("    top: 20px;\n")
                .append("    width: 15px;\n")
                .append("    height: 1px;\n")
                .append("    background: #e9ecef;\n")
                .append("}\n")
                .append(".tree-header {\n")
                .append("    padding: 8px 12px;\n")
                .append("    cursor: pointer;\n")
                .append("    display: flex;\n")
                .append("    align-items: center;\n")
                .append("    gap: 8px;\n")
                .append("    border-bottom: 1px solid #eee;\n")
                .append("    transition: background-color 0.2s ease;\n")
                .append("}\n")
                .append(".tree-header:hover {\n")
                .append("    background: #f8f9fa;\n")
                .append("}\n")
                .append(".tree-toggle {\n")
                .append("    min-width: 20px;\n")
                .append("    height: 20px;\n")
                .append("    background: #28a745;\n")
                .append("    color: white;\n")
                .append("    border: none;\n")
                .append("    border-radius: 3px;\n")
                .append("    font-size: 12px;\n")
                .append("    display: inline-flex;\n")
                .append("    align-items: center;\n")
                .append("    justify-content: center;\n")
                .append("    flex-shrink: 0;\n")
                .append("    pointer-events: none;\n")
                .append("}\n")
                .append(".tree-toggle.collapsed {\n")
                .append("    background: #6c757d;\n")
                .append("}\n")
                .append(".tree-content {\n")
                .append("    display: none;\n")
                .append("    padding-left: 20px;\n")
                .append("}\n")
                .append(".tree-content.expanded {\n")
                .append("    display: block;\n")
                .append("}\n")
                .append(".object-id {\n")
                .append("    font-family: monospace;\n")
                .append("    background: #e9ecef;\n")
                .append("    padding: 2px 6px;\n")
                .append("    border-radius: 3px;\n")
                .append("    font-size: 0.9em;\n")
                .append("}\n")
                .append(".class-name {\n")
                .append("    font-weight: 600;\n")
                .append("    color: #495057;\n")
                .append("}\n")
                .append(".field-name {\n")
                .append("    color: #6f42c1;\n")
                .append("    font-weight: 500;\n")
                .append("}\n")
                .append(".collection-info {\n")
                .append("    color: #fd7e14;\n")
                .append("    font-style: italic;\n")
                .append("    font-size: 0.9em;\n")
                .append("}\n")
                .append(".reachability-badge {\n")
                .append("    padding: 2px 6px;\n")
                .append("    border-radius: 3px;\n")
                .append("    font-size: 0.8em;\n")
                .append("    font-weight: 500;\n")
                .append("}\n")
                .append(".reachability-badge.reachable {\n")
                .append("    background: #d4edda;\n")
                .append("    color: #155724;\n")
                .append("}\n")
                .append(".reachability-badge.orphaned {\n")
                .append("    background: #fff3cd;\n")
                .append("    color: #856404;\n")
                .append("}\n")
                .append(".module-section {\n")
                .append("    margin-bottom: 30px;\n")
                .append("    background: white;\n")
                .append("    border: 1px solid #ddd;\n")
                .append("    border-radius: 8px;\n")
                .append("    overflow: hidden;\n")
                .append("}\n")
                .append(".module-header {\n")
                .append("    background: #17a2b8;\n")
                .append("    color: white;\n")
                .append("    padding: 15px 20px;\n")
                .append("    font-weight: 600;\n")
                .append("    cursor: pointer;\n")
                .append("    display: flex;\n")
                .append("    justify-content: space-between;\n")
                .append("    align-items: center;\n")
                .append("}\n")
                .append(".module-header:hover {\n")
                .append("    background: #138496;\n")
                .append("}\n")
                .append(".module-content {\n")
                .append("    padding: 20px;\n")
                .append("    display: none;\n")
                .append("}\n")
                .append(".module-content.expanded {\n")
                .append("    display: block;\n")
                .append("}\n")
                .append(".expand-icon {\n")
                .append("    transition: transform 0.3s ease;\n")
                .append("}\n")
                .append(".expand-icon.rotated {\n")
                .append("    transform: rotate(180deg);\n")
                .append("}\n")
                .append(".depth-warning {\n")
                .append("    background: #fff3cd;\n")
                .append("    border: 1px solid #ffeaa7;\n")
                .append("    color: #856404;\n")
                .append("    padding: 8px 12px;\n")
                .append("    border-radius: 4px;\n")
                .append("    margin: 5px 0;\n")
                .append("    font-size: 0.9em;\n")
                .append("}\n")
                .append(".unreachable-class {\n")
                .append("    background: white;\n")
                .append("    border: 1px solid #ddd;\n")
                .append("    border-radius: 8px;\n")
                .append("    margin-bottom: 20px;\n")
                .append("    overflow: hidden;\n")
                .append("}\n")
                .append(".unreachable-header {\n")
                .append("    background: #dc3545;\n")
                .append("    color: white;\n")
                .append("    padding: 15px 20px;\n")
                .append("    font-weight: 600;\n")
                .append("    cursor: pointer;\n")
                .append("    display: flex;\n")
                .append("    justify-content: space-between;\n")
                .append("    align-items: center;\n")
                .append("}\n")
                .append(".unreachable-header:hover {\n")
                .append("    background: #c82333;\n")
                .append("}\n")
                .append(".unreachable-content {\n")
                .append("    padding: 20px;\n")
                .append("    display: none;\n")
                .append("}\n")
                .append(".unreachable-content.expanded {\n")
                .append("    display: block;\n")
                .append("}\n")
                .append(".object-list {\n")
                .append("    max-height: 400px;\n")
                .append("    overflow-y: auto;\n")
                .append("    border: 1px solid #eee;\n")
                .append("    border-radius: 4px;\n")
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
                .append("    background: #f8f9fa;\n")
                .append("}\n")
                .append(".empty-class {\n")
                .append("    color: #6c757d;\n")
                .append("    font-style: italic;\n")
                .append("}\n")
                .append(".schema-only {\n")
                .append("    color: #dc3545;\n")
                .append("    font-style: italic;\n")
                .append("}\n");
        return css.toString();
    }

    private String generateJavaScript() {
        StringBuilder js = new StringBuilder();
        js.append("document.addEventListener('DOMContentLoaded', function() {\n")
                .append("    // Module section toggles\n")
                .append("    document.querySelectorAll('.module-header').forEach(header => {\n")
                .append("        header.addEventListener('click', function() {\n")
                .append("            const content = this.nextElementSibling;\n")
                .append("            const icon = this.querySelector('.expand-icon');\n")
                .append("            content.classList.toggle('expanded');\n")
                .append("            icon.classList.toggle('rotated');\n")
                .append("        });\n")
                .append("    });\n")
                .append("    \n")
                .append("    // Unreachable class toggles\n")
                .append("    document.querySelectorAll('.unreachable-header').forEach(header => {\n")
                .append("        header.addEventListener('click', function() {\n")
                .append("            const content = this.nextElementSibling;\n")
                .append("            const icon = this.querySelector('.expand-icon');\n")
                .append("            content.classList.toggle('expanded');\n")
                .append("            icon.classList.toggle('rotated');\n")
                .append("        });\n")
                .append("    });\n")
                .append("    \n")
                .append("    // Tree node toggles - make entire header clickable\n")
                .append("    document.querySelectorAll('.tree-header').forEach(header => {\n")
                .append("        header.addEventListener('click', function(e) {\n")
                .append("            const toggle = this.querySelector('.tree-toggle');\n")
                .append("            const content = this.nextElementSibling;\n")
                .append("            if (content && content.classList.contains('tree-content') && toggle) {\n")
                .append("                content.classList.toggle('expanded');\n")
                .append("                toggle.classList.toggle('collapsed');\n")
                .append("                toggle.textContent = content.classList.contains('expanded') ? '−' : '+';\n")
                .append("            }\n")
                .append("        });\n")
                .append("    });\n")
                .append("    \n")
                .append("    // Smooth scrolling for navigation links\n")
                .append("    document.querySelectorAll('a[href^=\"#\"]').forEach(anchor => {\n")
                .append("        anchor.addEventListener('click', function (e) {\n")
                .append("            e.preventDefault();\n")
                .append("            const target = document.querySelector(this.getAttribute('href'));\n")
                .append("            if (target) {\n")
                .append("                target.scrollIntoView({ behavior: 'smooth', block: 'start' });\n")
                .append("            }\n")
                .append("        });\n")
                .append("    });\n")
                .append("});\n");
        return js.toString();
    }

    private void generateTreeOverview(HtmlBuilder html, DOSchema schema, DODatabase database) {
        html.openTag("section", "id", "overview", "class", "section");
        html.element("h2", "Object Tree Overview");

        // Calculate statistics
        int totalModules = schema.getModules() != null ? schema.getModules().length : 0;
        int totalClasses = schema.getClasses().length;
        int totalObjects = 0;
        int reachableObjects = 0;
        int unreachableObjects = 0;

        for (DOSchemaClass clazz : schema.getClasses()) {
            if (clazz.getDatabaseClass() != null) {
                DODatabaseObject[] resolved = clazz.getDatabaseClass().getResolvedObjects();
                if (resolved != null) {
                    totalObjects += resolved.length;
                }

                DODatabaseObject[] reachable = clazz.getDatabaseClass().getReachableObjects();
                if (reachable != null) {
                    reachableObjects += reachable.length;
                }

                DODatabaseObject[] orphaned = clazz.getDatabaseClass().getOrphanedObjects();
                if (orphaned != null) {
                    unreachableObjects += orphaned.length;
                }
            }
        }

        html.openTag("div", "class", "stats-grid");

        html.openTag("div", "class", "stat-box");
        html.element("span", "Schema Modules", "class", "stat-label");
        html.element("span", String.valueOf(totalModules), "class", "stat-value");
        html.closeTag("div");

        html.openTag("div", "class", "stat-box");
        html.element("span", "Schema Classes", "class", "stat-label");
        html.element("span", String.valueOf(totalClasses), "class", "stat-value");
        html.closeTag("div");

        html.openTag("div", "class", "stat-box");
        html.element("span", "Total Objects", "class", "stat-label");
        html.element("span", String.valueOf(totalObjects), "class", "stat-value");
        html.closeTag("div");

        html.openTag("div", "class", "stat-box");
        html.element("span", "Reachable Objects", "class", "stat-label");
        html.element("span", String.valueOf(reachableObjects), "class", "stat-value");
        html.closeTag("div");

        html.openTag("div", "class", "stat-box");
        html.element("span", "Unreachable Objects", "class", "stat-label");
        html.element("span", String.valueOf(unreachableObjects), "class", "stat-value");
        html.closeTag("div");

        html.closeTag("div"); // stats-grid

        html.element("p", "This report shows the actual object tree structure in the database. "
                + "Objects are organized by schema modules, showing the hierarchical relationships "
                + "between objects through their fields and references. "
                + "Use the expandable tree nodes to drill down into object relationships.");
        html.closeTag("section");
    }

    private void generateModuleTreeSection(HtmlBuilder html, DOSchema schema, DODatabase database) {
        html.openTag("section", "id", "modules", "class", "section");
        html.element("h2", "Module Object Trees");

        DOSchemaModule[] modules = schema.getModules();
        if (modules != null && modules.length > 0) {
            for (DOSchemaModule module : modules) {
                generateModuleTree(html, module, schema, new HashSet<>(), 0);
            }
        } else {
            html.element("p", "No modules defined in schema. Cannot generate module-based trees.");
        }

        html.closeTag("section");
    }

    private void generateModuleTree(HtmlBuilder html, DOSchemaModule module, DOSchema schema,
            Set<Long> visitedObjects, int depth) {
        DOSchemaClass[] moduleClasses = module.getClasses();
        if (moduleClasses == null || moduleClasses.length == 0) {
            return;
        }

        // Count objects in this module
        int totalObjectsInModule = 0;
        for (DOSchemaClass clazz : moduleClasses) {
            if (clazz.getDatabaseClass() != null && clazz.getDatabaseClass().getReachableObjects() != null) {
                totalObjectsInModule += clazz.getDatabaseClass().getReachableObjects().length;
            }
        }

        // Show all modules, even those with 0 objects
        html.openTag("div", "class", "module-section");
        html.openTag("div", "class", "module-header");
        html.openTag("span");
        html.inlineText("Module: " + escapeHtml(module.getName()));
        html.inlineText(" (" + totalObjectsInModule + " reachable objects)");
        html.closeTag("span");
        html.span("▼", "expand-icon");
        html.closeTag("div");
        html.openTag("div", "class", "module-content");

        // Generate tree for each class in the module, including those with 0 objects
        for (DOSchemaClass clazz : moduleClasses) {
            if (clazz.getDatabaseClass() != null) {
                DODatabaseObject[] reachableObjects = clazz.getDatabaseClass().getReachableObjects();
                if (reachableObjects != null && reachableObjects.length > 0) {
                    generateClassTree(html, clazz, reachableObjects, schema, visitedObjects, depth);
                } else {
                    // Show classes with 0 objects as well
                    generateEmptyClassTree(html, clazz);
                }
            } else {
                // Show schema-only classes (not found in database)
                generateSchemaOnlyClassTree(html, clazz);
            }
        }

        html.closeTag("div"); // module-content
        html.closeTag("div"); // module-section
    }

    private void generateClassTree(HtmlBuilder html, DOSchemaClass clazz, DODatabaseObject[] objects,
            DOSchema schema, Set<Long> visitedObjects, int depth) {
        html.openTag("div", "class", "tree-node");
        html.openTag("div", "class", "tree-header");
        html.openTag("span", "class", "tree-toggle");
        html.inlineText("+");
        html.closeTag("span");
        html.element("span", escapeHtml(clazz.getShortName()), "class", "class-name");
        html.element("span", " (" + objects.length + " objects)", "class", "object-count");
        html.closeTag("div");

        html.openTag("div", "class", "tree-content");

        // Show individual object instances
        int maxObjects = Math.min(objects.length, 50); // Show up to 50 objects per class
        for (int i = 0; i < maxObjects; i++) {
            generateObjectInstanceNode(html, objects[i], schema, visitedObjects, depth + 1);
        }

        if (objects.length > maxObjects) {
            html.openTag("div", "class", "tree-truncated");
            html.inlineText("... and " + (objects.length - maxObjects) + " more objects in this class");
            html.closeTag("div");
        }

        html.closeTag("div"); // tree-content
        html.closeTag("div"); // tree-node
    }

    private void generateObjectInstanceNode(HtmlBuilder html, DODatabaseObject object, DOSchema schema,
            Set<Long> visitedObjects, int depth) {
        Long objectId;
        try {
            objectId = object.getObjectId();
        } catch (Exception e) {
            // Handle objects with problematic IDs
            objectId = null;
        }

        // Handle null objectId case
        if (objectId == null) {
            html.openTag("div", "class", "tree-node object-node-invalid");
            html.openTag("div", "class", "tree-header");
            html.element("span", "❌", "class", "tree-toggle-disabled");
            html.element("span", "Object with null ID", "class", "object-id-null");
            html.closeTag("div");
            html.closeTag("div");
            return;
        }

        // Prevent infinite recursion
        if (visitedObjects.contains(objectId) || depth > MAX_TREE_DEPTH) {
            html.openTag("div", "class", "tree-node truncated");
            html.openTag("div", "class", "tree-header");
            html.element("span", "🔄", "class", "tree-toggle-disabled");
            html.element("span", "Object " + objectId, "class", "object-id");
            String warning = visitedObjects.contains(objectId) ? " (circular reference)" : " (max depth reached)";
            html.element("span", warning, "class", "depth-warning");
            html.closeTag("div");
            html.closeTag("div");
            return;
        }

        visitedObjects.add(objectId);

        // Determine reachability status
        String reachabilityClass = object.isReachable() ? "reachable" : "orphaned";
        String reachabilityIcon = object.isReachable() ? "✅" : "⚠️";

        html.openTag("div", "class", "tree-node object-node");
        html.openTag("div", "class", "tree-header");
        html.openTag("span", "class", "tree-toggle");
        html.inlineText("+");
        html.closeTag("span");
        html.element("span", "Object " + objectId, "class", "object-id");
        html.element("span", reachabilityIcon, "class", "reachability-icon " + reachabilityClass);
        html.element("span", object.getMostSpecificClass().getShortName(), "class", "object-class");
        html.closeTag("div");

        html.openTag("div", "class", "tree-content");

        // Show object fields
        generateObjectFields(html, object, schema, visitedObjects, depth + 1);

        html.closeTag("div"); // tree-content
        html.closeTag("div"); // tree-node

        visitedObjects.remove(objectId); // Allow the same object to appear in different branches
    }

    private void generateObjectFields(HtmlBuilder html, DODatabaseObject object, DOSchema schema,
            Set<Long> visitedObjects, int depth) {
        // Show direct references (non-collection fields)
        DOObjectReference[] directRefs = object.getReferences();
        if (directRefs != null && directRefs.length > 0) {
            html.openTag("div", "class", "field-section");
            html.element("div", "Direct Field References:", "class", "field-section-header");

            for (DOObjectReference ref : directRefs) {
                Long targetObjectId = null;
                try {
                    targetObjectId = ref.getTargetObjectId();
                } catch (Exception e) {
                    // Handle cases where getTargetObjectId() might fail
                    targetObjectId = null;
                }
                generateFieldNode(html, ref.getField().getName(), targetObjectId,
                        schema, visitedObjects, depth, false);
            }
            html.closeTag("div");
        }

        // Show collection references
        DOCollectionReference[] collectionRefs = object.getCollections();
        if (collectionRefs != null && collectionRefs.length > 0) {
            html.openTag("div", "class", "field-section");
            html.element("div", "Collection Fields:", "class", "field-section-header");

            for (DOCollectionReference collRef : collectionRefs) {
                generateCollectionFieldNode(html, collRef, schema, visitedObjects, depth);
            }
            html.closeTag("div");
        }

        // If no fields, show a message
        if ((directRefs == null || directRefs.length == 0) &&
                (collectionRefs == null || collectionRefs.length == 0)) {
            html.openTag("div", "class", "field-section");
            html.element("div", "No non-primitive fields found", "class", "no-fields");
            html.closeTag("div");
        }
    }

    private void generateFieldNode(HtmlBuilder html, String fieldName, Long targetObjectId,
            DOSchema schema, Set<Long> visitedObjects, int depth, boolean isCollectionItem) {
        if (targetObjectId == null) {
            html.openTag("div", "class", "tree-node field-node-null");
            html.element("span", fieldName + " → <null>", "class", "field-null");
            html.closeTag("div");
            return;
        }

        html.openTag("div", "class", "tree-node field-node");
        html.openTag("div", "class", "tree-header");
        html.openTag("span", "class", "tree-toggle");
        html.inlineText("+");
        html.closeTag("span");
        html.element("span", fieldName, "class", "field-name");
        html.inlineText(" → ");
        html.element("span", "Object " + targetObjectId, "class", "target-object-id");
        html.closeTag("div");

        html.openTag("div", "class", "tree-content");

        // Find the target object and display it
        DODatabaseObject targetObject = findObjectById(targetObjectId, schema);
        if (targetObject != null) {
            generateObjectInstanceNode(html, targetObject, schema, visitedObjects, depth + 1);
        } else {
            html.openTag("div", "class", "object-not-found");
            html.inlineText("Object " + targetObjectId + " not found in current analysis");
            html.closeTag("div");
        }

        html.closeTag("div"); // tree-content
        html.closeTag("div"); // tree-node
    }

    private void generateCollectionFieldNode(HtmlBuilder html, DOCollectionReference collRef,
            DOSchema schema, Set<Long> visitedObjects, int depth) {
        Long[] containedIds = collRef.getContainedObjectIds();
        String fieldName = collRef.getField().getName();

        html.openTag("div", "class", "tree-node collection-node");
        html.openTag("div", "class", "tree-header");
        html.openTag("span", "class", "tree-toggle");
        html.inlineText("+");
        html.closeTag("span");
        html.element("span", fieldName, "class", "field-name");
        html.element("span", " [" + collRef.getSize() + " items]", "class", "collection-size");
        if (collRef.getResolvedContentType() != null) {
            html.element("span", " (" + collRef.getResolvedContentType() + ")", "class", "collection-type");
        }
        html.closeTag("div");

        html.openTag("div", "class", "tree-content");

        if (containedIds != null && containedIds.length > 0) {
            int maxItems = Math.min(containedIds.length, 20); // Show up to 20 collection items
            for (int i = 0; i < maxItems; i++) {
                generateFieldNode(html, "[" + i + "]", containedIds[i], schema, visitedObjects, depth + 1, true);
            }

            if (containedIds.length > maxItems) {
                html.openTag("div", "class", "tree-truncated");
                html.inlineText("... and " + (containedIds.length - maxItems) + " more items");
                html.closeTag("div");
            }
        } else {
            html.openTag("div", "class", "empty-collection");
            html.inlineText("Empty collection or contains only primitive values");
            html.closeTag("div");
        }

        html.closeTag("div"); // tree-content
        html.closeTag("div"); // tree-node
    }

    private DODatabaseObject findObjectById(Long objectId, DOSchema schema) {
        if (objectId == null) {
            return null;
        }

        // Search through all database classes to find the object
        DOSchemaClass[] schemaClasses = schema.getClasses();
        if (schemaClasses != null) {
            for (DOSchemaClass schemaClass : schemaClasses) {
                if (schemaClass.getDatabaseClass() != null) {
                    DODatabaseObject[] resolvedObjects = schemaClass.getDatabaseClass().getResolvedObjects();
                    if (resolvedObjects != null) {
                        for (DODatabaseObject obj : resolvedObjects) {
                            if (obj != null) {
                                try {
                                    Long objId = obj.getObjectId();
                                    if (Objects.equals(objId, objectId)) {
                                        return obj;
                                    }
                                } catch (Exception e) {
                                    // Skip objects with problematic IDs
                                    continue;
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private void generateUnreachableObjectsSection(HtmlBuilder html, DOSchema schema, DODatabase database) {
        html.openTag("section", "id", "unreachable", "class", "section")
                .element("h2", "Unreachable Objects");

        // Collect all unreachable objects by class
        Map<String, List<DODatabaseObject>> unreachableByClass = new HashMap<>();
        int totalUnreachable = 0;

        // Check schema classes
        for (DOSchemaClass clazz : schema.getClasses()) {
            if (clazz.getDatabaseClass() != null) {
                DODatabaseObject[] orphaned = clazz.getDatabaseClass().getOrphanedObjects();
                if (orphaned != null && orphaned.length > 0) {
                    unreachableByClass.put(clazz.getAbsoluteName(), Arrays.asList(orphaned));
                    totalUnreachable += orphaned.length;
                }
            }
        }

        // Check database-only classes
        for (DODatabaseClass dbClass : database.getClasses()) {
            boolean hasSchemaMapping = false;
            for (DOSchemaClass schemaClass : schema.getClasses()) {
                if (schemaClass.getDatabaseClass() == dbClass) {
                    hasSchemaMapping = true;
                    break;
                }
            }

            if (!hasSchemaMapping) {
                DODatabaseObject[] orphaned = dbClass.getOrphanedObjects();
                if (orphaned != null && orphaned.length > 0) {
                    unreachableByClass.put(dbClass.getAbsoluteName() + " (No Schema)", Arrays.asList(orphaned));
                    totalUnreachable += orphaned.length;
                }
            }
        }

        if (unreachableByClass.isEmpty()) {
            html.element("p",
                    "✅ No unreachable objects found! All objects are reachable through the normal object graph.");
        } else {
            html.element("p", "Found " + totalUnreachable + " unreachable objects across " + unreachableByClass.size()
                    + " classes:");

            // Sort classes by name and generate sections
            unreachableByClass.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        String className = entry.getKey();
                        List<DODatabaseObject> objects = entry.getValue();
                        generateUnreachableClassSection(html, className, objects);
                    });
        }

        html.closeTag("section");
    }

    private void generateUnreachableClassSection(HtmlBuilder html, String className, List<DODatabaseObject> objects) {
        html.openTag("div", "class", "tree-node unreachable-class");
        html.openTag("div", "class", "tree-header");
        html.openTag("span", "class", "tree-toggle");
        html.inlineText("+");
        html.closeTag("span");
        html.element("span", escapeHtml(className), "class", "class-name");
        html.element("span", " (" + objects.size() + " unreachable objects)", "class", "object-count");
        html.closeTag("div");

        html.openTag("div", "class", "tree-content");

        // Show individual unreachable object instances
        int maxObjects = Math.min(objects.size(), 50);
        for (int i = 0; i < maxObjects; i++) {
            DODatabaseObject obj = objects.get(i);

            // For unreachable objects, just show basic info without deep traversal
            html.openTag("div", "class", "tree-node object-instance");
            html.openTag("div", "class", "tree-header");

            String objectId = "unknown";
            try {
                Long objId = obj.getObjectId();
                objectId = objId != null ? objId.toString() : "null";
            } catch (Exception e) {
                objectId = "error getting ID";
            }

            html.element("span", "Object " + objectId, "class", "object-id");
            html.closeTag("div");

            // Show basic object type info only (no field traversal for unreachable objects)
            html.openTag("div", "class", "object-basic-info");
            html.element("div", "Unreachable object - not connected through schema references", "class",
                    "unreachable-note");
            html.closeTag("div");

            html.closeTag("div"); // tree-node
        }

        if (objects.size() > maxObjects) {
            html.openTag("div", "class", "tree-truncated");
            html.inlineText("... and " + (objects.size() - maxObjects) + " more unreachable objects");
            html.closeTag("div");
        }

        html.closeTag("div"); // tree-content
        html.closeTag("div"); // tree-node
    }

    private void generateEmptyClassTree(HtmlBuilder html, DOSchemaClass clazz) {
        html.openTag("div", "class", "tree-node");
        html.openTag("div", "class", "tree-header");
        html.openTag("span", "class", "tree-toggle");
        html.inlineText("○"); // Empty circle for empty classes
        html.closeTag("span");
        html.element("span", escapeHtml(clazz.getShortName()) + " (0 objects)", "class", "class-name empty-class");
        html.closeTag("div");
        html.closeTag("div");
    }

    private void generateSchemaOnlyClassTree(HtmlBuilder html, DOSchemaClass clazz) {
        html.openTag("div", "class", "tree-node");
        html.openTag("div", "class", "tree-header");
        html.openTag("span", "class", "tree-toggle");
        html.inlineText("◌"); // Hollow circle for schema-only classes
        html.closeTag("span");
        html.element("span", escapeHtml(clazz.getShortName()) + " (not in database)", "class",
                "class-name schema-only");
        html.closeTag("div");
        html.closeTag("div");
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