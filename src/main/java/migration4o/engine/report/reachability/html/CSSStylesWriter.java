package migration4o.engine.report.reachability.html;

import java.io.IOException;
import java.io.Writer;

/**
 * Generates CSS styles for the reachability report
 */
public class CSSStylesWriter extends HTMLWriter {

    public CSSStylesWriter(Writer writer) {
        super(writer);
    }

    public void writeStyles() throws IOException {
        openTag("style");

        // Base styles - Compact desktop-focused layout
        write("body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 0; background: #f8fafc; font-size: 13px; line-height: 1.4; }\n");
        write(".container { max-width: 1600px; margin: 0 auto; padding: 10px; }\n");
        write("header { background: #ffffff; padding: 12px 20px; border-bottom: 1px solid #e2e8f0; margin-bottom: 10px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }\n");
        write("header h1 { margin: 0; font-size: 1.5em; color: #1e293b; font-weight: 600; }\n");

        // Layout styles - Compact content area
        write(".content-area { background: #ffffff; border: 1px solid #e2e8f0; padding: 15px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); min-height: 600px; }\n");
        write(".breadcrumb { background: #f1f5f9; padding: 8px 12px; border-radius: 4px; margin-bottom: 12px; font-size: 12px; border-left: 3px solid #3b82f6; }\n");

        // Tree styles - Compact tree with better visual hierarchy
        write(".tree-container { margin-top: 10px; }\n");
        write(".tree-level { margin-left: 0; }\n");
        write(".tree-level.nested { margin-left: 20px; border-left: 1px solid #cbd5e1; padding-left: 12px; }\n");
        write(".tree-item { margin-bottom: 2px; }\n");
        write(".tree-header { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 4px; padding: 8px 12px; cursor: pointer; transition: all 0.15s ease; display: flex; align-items: center; justify-content: space-between; }\n");
        write(".tree-header:hover { border-color: #3b82f6; background: #f8fafc; }\n");

        // Field styles - Compact field display
        write(".field-item { margin: 1px 0; border-radius: 3px; overflow: hidden; }\n");
        write(".field-main { display: flex; align-items: center; padding: 4px 8px; background: #f8fafc; border: 1px solid #e2e8f0; cursor: pointer; transition: all 0.1s ease; }\n");
        write(".field-main:hover { background: #f1f5f9; border-color: #3b82f6; }\n");
        write(".field-icon { margin-right: 6px; font-size: 12px; width: 16px; text-align: center; }\n");
        write(".field-info { flex: 1; min-width: 0; }\n");
        write(".field-name { font-weight: 500; color: #1e293b; font-size: 12px; }\n");
        write(".field-type { font-size: 11px; color: #64748b; font-family: 'Consolas', 'Monaco', monospace; margin-left: 8px; }\n");

        // Expandable styles - Compact expand controls
        write(".field-expand { width: 16px; height: 16px; display: flex; align-items: center; justify-content: center; background: #3b82f6; color: white; border-radius: 3px; font-weight: bold; font-size: 10px; margin-left: 6px; }\n");
        write(".field-content { background: #ffffff; border-left: 2px solid #3b82f6; margin-left: 16px; padding: 8px; border-radius: 0 3px 3px 0; }\n");

        // Field category styles - Compact categories
        write(".field-category { margin: 6px 0; border: 1px solid #e2e8f0; border-radius: 4px; overflow: hidden; }\n");
        write(".category-header { background: #f8fafc; padding: 6px 10px; cursor: pointer; display: flex; justify-content: space-between; align-items: center; transition: background 0.1s ease; }\n");
        write(".category-header:hover { background: #f1f5f9; }\n");
        write(".category-title { display: flex; align-items: center; gap: 6px; font-weight: 500; font-size: 12px; }\n");
        write(".field-count { background: #64748b; color: white; padding: 1px 6px; border-radius: 8px; font-size: 10px; }\n");
        write(".category-expand { width: 16px; height: 16px; display: flex; align-items: center; justify-content: center; background: #64748b; color: white; border-radius: 3px; font-weight: bold; font-size: 10px; }\n");
        write(".category-content { padding: 0; }\n");
        write(".category-content .field-item { margin: 0; border-radius: 0; border-bottom: 1px solid #f1f5f9; }\n");
        write(".category-content .field-item:last-child { border-bottom: none; }\n");

        // Class summary styles - Compact summary
        write(".class-summary { background: #f8fafc; padding: 10px; border-radius: 4px; margin-bottom: 10px; border-left: 3px solid #10b981; }\n");
        write(".class-summary h4 { margin: 0 0 6px 0; color: #1e293b; font-size: 13px; }\n");
        write(".class-summary p { margin: 2px 0; color: #64748b; font-size: 11px; }\n");
        write(".class-summary a { color: #3b82f6; text-decoration: none; }\n");
        write(".class-summary a:hover { text-decoration: underline; }\n");

        // Nested class styles for deep drill-down - Compact nesting
        write(".nested-class { margin: 4px 0; border-radius: 4px; overflow: hidden; }\n");
        write(".class-header { background: #ffffff; border: 1px solid #e2e8f0; padding: 6px 10px; cursor: pointer; display: flex; justify-content: space-between; align-items: center; transition: all 0.1s ease; }\n");
        write(".class-header:hover { background: #f8fafc; border-color: #10b981; }\n");
        write(".class-title { display: flex; align-items: center; gap: 6px; }\n");
        write(".depth-indicator { background: #64748b; color: white; padding: 1px 4px; border-radius: 6px; font-size: 10px; font-weight: bold; }\n");
        write(".extends { color: #64748b; font-style: italic; font-size: 11px; }\n");
        write(".field-count-badge { background: #0ea5e9; color: white; padding: 1px 6px; border-radius: 8px; font-size: 10px; margin-left: 6px; }\n");
        write(".nested-expand { width: 16px; height: 16px; display: flex; align-items: center; justify-content: center; background: #10b981; color: white; border-radius: 3px; font-size: 10px; }\n");
        write(".nested-content { background: #fdfdfd; border-top: 1px solid #e2e8f0; }\n");

        // Type information styles - Compact type info
        write(".type-info { padding: 4px 8px; margin: 4px 0; border-radius: 3px; font-weight: 400; display: flex; align-items: center; gap: 4px; font-size: 11px; }\n");
        write(".array-info { background: #dbeafe; color: #1d4ed8; border-left: 2px solid #3b82f6; }\n");
        write(".generic-info { background: #fef3c7; color: #d97706; border-left: 2px solid #f59e0b; }\n");
        write(".primitive-type { background: #dcfce7; color: #16a34a; border-left: 2px solid #22c55e; }\n");
        write(".not-found { background: #fee2e2; color: #dc2626; border-left: 2px solid #ef4444; }\n");
        write(".max-depth { background: #fef9c3; color: #ca8a04; border-left: 2px solid #eab308; text-align: center; font-weight: 500; }\n");

        // Suggestions styles - Compact suggestions
        write(".suggestions { margin-top: 6px; padding: 6px; background: #f8fafc; border-radius: 3px; }\n");
        write(".suggestions ul { margin: 3px 0 0 0; padding-left: 16px; }\n");
        write(".suggestions li { margin: 1px 0; }\n");
        write(".suggestions a { color: #3b82f6; text-decoration: none; font-family: 'Consolas', 'Monaco', monospace; font-size: 11px; }\n");
        write(".suggestions a:hover { text-decoration: underline; }\n");

        write(".collection-info, .generic-info { padding: 6px; border-radius: 3px; margin: 4px 0; font-style: italic; }\n");

        // Additional compact navigation improvements
        write("h2, h3, h4 { margin: 6px 0 4px 0; font-size: 14px; }\n");
        write("h3 { font-size: 13px; }\n");
        write("h4 { font-size: 12px; }\n");
        write(".modules-overview h2 { border-bottom: 1px solid #e2e8f0; padding-bottom: 4px; }\n");
        write(".tree-header h3, .tree-header h4 { margin: 0; }\n");
        write(".tree-header p { margin: 2px 0; font-size: 11px; color: #64748b; }\n");
        write(".description { font-style: italic; color: #64748b !important; }\n");

        // Executive Summary styles
        write(".executive-summary { background: #ffffff; border: 2px solid #3b82f6; border-radius: 8px; padding: 20px; margin-bottom: 20px; box-shadow: 0 2px 8px rgba(59, 130, 246, 0.1); }\n");
        write(".executive-summary h2 { margin: 0 0 15px 0; color: #1e293b; font-size: 1.4em; border-bottom: 2px solid #3b82f6; padding-bottom: 8px; }\n");
        write(".summary-stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 15px; margin-bottom: 20px; }\n");
        write(".stat-card { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; padding: 15px; text-align: center; transition: all 0.2s ease; }\n");
        write(".stat-card:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,0.1); }\n");
        write(".stat-card.reached { border-left: 4px solid #10b981; }\n");
        write(".stat-card.unreached { border-left: 4px solid #ef4444; }\n");
        write(".stat-label { font-size: 0.9em; color: #64748b; margin-bottom: 8px; font-weight: 500; }\n");
        write(".stat-value { font-size: 2em; font-weight: bold; color: #1e293b; }\n");
        write(".stat-card.reached .stat-value { color: #10b981; }\n");
        write(".stat-card.unreached .stat-value { color: #ef4444; }\n");
        write(".storage-info { background: #f1f5f9; border-left: 4px solid #64748b; padding: 15px; border-radius: 4px; }\n");
        write(".storage-info h3 { margin: 0 0 10px 0; color: #1e293b; font-size: 1.1em; }\n");
        write(".storage-info p { margin: 5px 0; color: #475569; font-size: 0.95em; }\n");
        write(".info-note { background: #dbeafe; border-left: 3px solid #3b82f6; padding: 10px; border-radius: 3px; color: #1e40af; font-style: italic; }\n");

        // Scrollbar styling for better desktop experience
        write("::-webkit-scrollbar { width: 8px; height: 8px; }\n");
        write("::-webkit-scrollbar-track { background: #f1f5f9; }\n");
        write("::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 4px; }\n");
        write("::-webkit-scrollbar-thumb:hover { background: #94a3b8; }\n");

        // Primitive field styles - muted appearance to distinguish from references
        write(".primitive-header { color: #64748b; font-weight: normal; margin-top: 12px; }\n");
        write(".primitive-list { margin: 8px 0; padding: 8px; background: #f8fafc; border-left: 2px solid #94a3b8; border-radius: 3px; }\n");
        write(".primitive-field-item { padding: 4px 8px; margin: 2px 0; font-size: 12px; color: #475569; display: flex; align-items: baseline; }\n");
        write(".primitive-field-name { font-weight: 500; color: #64748b; min-width: 150px; }\n");
        write(".primitive-field-value { color: #1e293b; font-family: 'Consolas', 'Monaco', monospace; flex: 1; }\n");
        write(".primitive-field-type { font-size: 10px; color: #94a3b8; font-style: italic; margin-left: 8px; }\n");

        closeTag("style");
    }
}