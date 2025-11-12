package dataobjects.impl.report.reachability.html;

import java.io.IOException;
import java.io.Writer;

/**
 * Generate write("}\n\n");vaScript for drill-down functionality in the
 * reachability report
 */
public class JavaScriptWriter extends HTMLWriter {

    public JavaScriptWriter(Writer writer) {
        super(writer);
    }

    public void writeScript() throws IOException {
        openTag("script");

        // Main drill-down expansion function
        writeDrillDownFunctions();

        // Helper functions
        writeHelperFunctions();

        closeTag("script");
    }

    private void writeDrillDownFunctions() throws IOException {
        // Page initialization function
        write("function initializePage() {\n");
        write("    const contentArea = document.getElementById('content-area');\n");
        write("    \n");
        write("    let html = '<div class=\"modules-overview\">';\n");
        write("    html += '<h2>📋 Modules Overview</h2>';\n");
        write("    \n");
        write("    for (const moduleName in reachabilityData.modules) {\n");
        write("        const module = reachabilityData.modules[moduleName];\n");
        write("        html += '<div class=\"tree-item\">';\n");
        write("        html += '<div class=\"tree-header\" onclick=\"toggleModule(\\'' + moduleName + '\\')\">'; \n");
        write("        html += '<div>';\n");
        write("        html += '<h3>📁 ' + escapeHtml(moduleName) + '</h3>';\n");
        write("        html += '<p>' + module.classCount + ' classes</p>';\n");
        write("        html += '</div>';\n");
        write("        html += '<div class=\"field-expand\">+</div>';\n");
        write("        html += '</div>';\n");
        write("        html += '<div class=\"tree-level nested\" id=\"module-' + moduleName + '\" style=\"display: none;\">';\n");
        write("        html += '</div>';\n");
        write("        html += '</div>';\n");
        write("    }\n");
        write("    \n");
        write("    html += '</div>';\n");
        write("    contentArea.innerHTML = html;\n");
        write("}\n\n");

        // Toggle module expansion
        write("function toggleModule(moduleName) {\n");
        write("    const moduleContainer = document.getElementById('module-' + moduleName);\n");
        write("    const expandIcon = event.target.closest('.tree-header').querySelector('.field-expand');\n");
        write("    \n");
        write("    if (moduleContainer.style.display === 'none') {\n");
        write("        // Expand the module\n");
        write("        moduleContainer.style.display = 'block';\n");
        write("        expandIcon.textContent = '-';\n");
        write("        \n");
        write("        // Load module classes if not already loaded\n");
        write("        if (moduleContainer.innerHTML === '') {\n");
        write("            loadModuleClasses(moduleContainer, moduleName);\n");
        write("        }\n");
        write("    } else {\n");
        write("        // Collapse the module\n");
        write("        moduleContainer.style.display = 'none';\n");
        write("        expandIcon.textContent = '+';\n");
        write("    }\n");
        write("}\n\n");

        // Load module classes function
        write("function loadModuleClasses(container, moduleName) {\n");
        write("    const module = reachabilityData.modules[moduleName];\n");
        write("    let html = '';\n");
        write("    \n");
        write("    for (const className in module.classes) {\n");
        write("        const classInfo = module.classes[className];\n");
        write("        html += '<div class=\"tree-item\">';\n");
        write("        html += '<div class=\"tree-header\" onclick=\"toggleClass(\\'' + className + '\\')\">'; \n");
        write("        html += '<div>';\n");
        write("        html += '<h4>🏛️ ' + escapeHtml(classInfo.shortName) + '</h4>';\n");
        write("        html += '<p>' + classInfo.fieldCount + ' fields</p>';\n");
        write("        if (classInfo.description) {\n");
        write("            html += '<p class=\"description\">' + escapeHtml(classInfo.description) + '</p>';\n");
        write("        }\n");
        write("        html += '</div>';\n");
        write("        html += '<div class=\"field-expand\">+</div>';\n");
        write("        html += '</div>';\n");
        write("        html += '<div class=\"tree-level nested\" id=\"class-' + className.replace(/[^a-zA-Z0-9]/g, '_') + '\" style=\"display: none;\">';\n");
        write("        html += '</div>';\n");
        write("        html += '</div>';\n");
        write("    }\n");
        write("    \n");
        write("    container.innerHTML = html;\n");
        write("}\n\n");

        // Toggle class expansion
        write("function toggleClass(className) {\n");
        write("    const classContainer = document.getElementById('class-' + className.replace(/[^a-zA-Z0-9]/g, '_'));\n");
        write("    const expandIcon = event.target.closest('.tree-header').querySelector('.field-expand');\n");
        write("    \n");
        write("    if (classContainer.style.display === 'none') {\n");
        write("        // Expand the class\n");
        write("        classContainer.style.display = 'block';\n");
        write("        expandIcon.textContent = '-';\n");
        write("        \n");
        write("        // Load class fields if not already loaded\n");
        write("        if (classContainer.innerHTML === '') {\n");
        write("            loadClassFields(classContainer, className);\n");
        write("        }\n");
        write("    } else {\n");
        write("        // Collapse the class\n");
        write("        classContainer.style.display = 'none';\n");
        write("        expandIcon.textContent = '+';\n");
        write("    }\n");
        write("}\n\n");

        // Enhanced toggle expansion function with deep drill-down
        write("function toggleFieldExpansion(element, fieldType, fieldName, currentDepth) {\n");
        write("    const fieldContent = element.parentElement.querySelector('.field-content');\n");
        write("    const expandIcon = element.querySelector('.field-expand');\n");
        write("    \n");
        write("    if (!fieldContent) return; // No drill-down for primitives\n");
        write("    \n");
        write("    // Get depth from element if not provided\n");
        write("    if (typeof currentDepth === 'undefined') {\n");
        write("        const depthIndicator = element.closest('.nested-class')?.querySelector('.depth-indicator');\n");
        write("        currentDepth = depthIndicator ? parseInt(depthIndicator.textContent.match(/\\d+/)?.[0] || '0') : 0;\n");
        write("    }\n");
        write("    \n");
        write("    if (fieldContent.style.display === 'none') {\n");
        write("        // Expand the field\n");
        write("        fieldContent.style.display = 'block';\n");
        write("        expandIcon.textContent = '🔽';\n");
        write("        \n");
        write("        // Load field content if not already loaded\n");
        write("        if (fieldContent.innerHTML === '') {\n");
        write("            const fieldPath = fieldContent.getAttribute('data-field-path');\n");
        write("            loadFieldContent(fieldContent, fieldType, fieldPath, currentDepth + 1);\n");
        write("        }\n");
        write("    } else {\n");
        write("        // Collapse the field\n");
        write("        fieldContent.style.display = 'none';\n");
        write("        expandIcon.textContent = '🔍';\n");
        write("    }\n");
        write("}\n\n");

        // Enhanced load field content function with recursion tracking
        write("function loadFieldContent(container, fieldType, fieldPath, depth) {\n");
        write("    // Prevent infinite recursion\n");
        write("    if (depth > 5) {\n");
        write("        container.innerHTML = '<div class=\"max-depth\">⚠️ Maximum drill-down depth reached</div>';\n");
        write("        return;\n");
        write("    }\n");
        write("    \n");
        write("    let targetType = fieldType;\n");
        write("    let isArray = false;\n");
        write("    let isGeneric = false;\n");
        write("    let contentHtml = '';\n");
        write("    \n");
        write("    // Handle array types\n");
        write("    if (targetType.includes('[]')) {\n");
        write("        targetType = targetType.replace('[]', '');\n");
        write("        isArray = true;\n");
        write("        contentHtml += '<div class=\"type-info array-info\">📚 Array of ' + escapeHtml(targetType) + ' objects</div>';\n");
        write("    }\n");
        write("    \n");
        write("    // Handle generic types (Vector<T>, List<T>, etc.)\n");
        write("    if (targetType.includes('<') && targetType.includes('>')) {\n");
        write("        const genericMatch = targetType.match(/<(.+)>/);\n");
        write("        if (genericMatch) {\n");
        write("            const genericType = genericMatch[1];\n");
        write("            const containerType = targetType.split('<')[0];\n");
        write("            contentHtml += '<div class=\"type-info generic-info\">🔗 ' + escapeHtml(containerType) + ' containing ' + escapeHtml(genericType) + '</div>';\n");
        write("            targetType = genericType;\n");
        write("            isGeneric = true;\n");
        write("        }\n");
        write("    }\n");
        write("    \n");
        write("    // Try to find the class definition and show its fields recursively\n");
        write("    const classInfo = findClassDefinition(targetType);\n");
        write("    if (classInfo) {\n");
        write("        contentHtml += createExpandableClassView(classInfo, fieldPath, depth + 1);\n");
        write("    } else {\n");
        write("        // Show type information for known primitive/system types\n");
        write("        if (isPrimitiveType(targetType)) {\n");
        write("            contentHtml += '<div class=\"primitive-type\">🔧 Primitive type: ' + escapeHtml(targetType) + '</div>';\n");
        write("        } else {\n");
        write("            contentHtml += '<div class=\"not-found\">⚠️ Type definition not found: ' + escapeHtml(targetType) + '</div>';\n");
        write("            // Add suggestion for similar types\n");
        write("            const suggestions = findSimilarTypes(targetType);\n");
        write("            if (suggestions.length > 0) {\n");
        write("                contentHtml += '<div class=\"suggestions\"><strong>Similar types found:</strong><ul>';\n");
        write("                suggestions.forEach(suggestion => {\n");
        write("                    contentHtml += '<li><a href=\"#\" onclick=\"loadFieldContent(this.closest(\\'.field-content\\'), \\'' + suggestion + '\\', \\'' + fieldPath + '\\', ' + depth + '); return false;\">' + escapeHtml(suggestion) + '</a></li>';\n");
        write("                });\n");
        write("                contentHtml += '</ul></div>';\n");
        write("            }\n");
        write("        }\n");
        write("    }\n");
        write("    \n");
        write("    container.innerHTML = contentHtml;\n");
        write("}\n\n");

        // Find class definition function
        write("function findClassDefinition(typeName) {\n");
        write("    // First check in allSchemaClasses\n");
        write("    if (reachabilityData.allSchemaClasses[typeName]) {\n");
        write("        return reachabilityData.allSchemaClasses[typeName];\n");
        write("    }\n");
        write("    \n");
        write("    // Then check in modules\n");
        write("    for (const moduleName in reachabilityData.modules) {\n");
        write("        const module = reachabilityData.modules[moduleName];\n");
        write("        if (module.classes[typeName]) {\n");
        write("            return module.classes[typeName];\n");
        write("        }\n");
        write("    }\n");
        write("    \n");
        write("    return null;\n");
        write("}\n\n");

        // Create expandable class view for deep drill-down
        write("function createExpandableClassView(classInfo, parentPath, depth) {\n");
        write("    let html = '<div class=\"nested-class\" style=\"border-left: 3px solid #' + getDepthColor(depth) + '; padding-left: 10px; margin-left: 10px;\">';\n");
        write("    html += '<div class=\"class-header\" onclick=\"toggleNestedClass(this)\">';\n");
        write("    html += '<div class=\"class-title\">';\n");
        write("    html += '<span class=\"depth-indicator\">[Level ' + depth + ']</span> ';\n");
        write("    html += '<strong>' + escapeHtml(classInfo.shortName || classInfo.name) + '</strong>';\n");
        write("    if (classInfo.superClass) {\n");
        write("        html += ' <span class=\"extends\">extends ' + escapeHtml(classInfo.superClass) + '</span>';\n");
        write("    }\n");
        write("    html += '<span class=\"field-count-badge\">' + classInfo.fieldCount + ' fields</span>';\n");
        write("    html += '</div>';\n");
        write("    html += '<div class=\"nested-expand\">▶️</div>';\n");
        write("    html += '</div>';\n");
        write("    html += '<div class=\"nested-content\" style=\"display: none;\"></div>';\n");
        write("    html += '</div>';\n");
        write("    return html;\n");
        write("}\n\n");

        // Toggle nested class expansion
        write("function toggleNestedClass(header) {\n");
        write("    const content = header.nextElementSibling;\n");
        write("    const expandIcon = header.querySelector('.nested-expand');\n");
        write("    \n");
        write("    if (content.style.display === 'none') {\n");
        write("        content.style.display = 'block';\n");
        write("        expandIcon.textContent = '🔽';\n");
        write("        \n");
        write("        // Load nested class content if not already loaded\n");
        write("        if (content.innerHTML === '') {\n");
        write("            const classTitle = header.querySelector('.class-title strong').textContent;\n");
        write("            const className = findFullClassName(classTitle);\n");
        write("            if (className) {\n");
        write("                const depth = parseInt(header.querySelector('.depth-indicator').textContent.match(/\\d+/)[0]);\n");
        write("                loadClassFields(content, className, depth);\n");
        write("            }\n");
        write("        }\n");
        write("    } else {\n");
        write("        content.style.display = 'none';\n");
        write("        expandIcon.textContent = '▶️';\n");
        write("    }\n");
        write("}\n\n");

        // Get color based on depth level
        write("function getDepthColor(depth) {\n");
        write("    const colors = ['2196F3', '4CAF50', 'FF9800', '9C27B0', 'F44336', '795548'];\n");
        write("    return colors[depth % colors.length];\n");
        write("}\n\n");

        // Check if type is primitive
        write("function isPrimitiveType(typeName) {\n");
        write("    const primitives = ['int', 'long', 'double', 'float', 'boolean', 'byte', 'char', 'short',\n");
        write("                       'java.lang.String', 'java.lang.Integer', 'java.lang.Long', 'java.lang.Double',\n");
        write("                       'java.lang.Float', 'java.lang.Boolean', 'java.lang.Byte', 'java.lang.Character',\n");
        write("                       'java.lang.Short', 'java.util.Date', 'java.math.BigDecimal', 'byte[]'];\n");
        write("    return primitives.includes(typeName);\n");
        write("}\n\n");

        // Find similar types for suggestions
        write("function findSimilarTypes(targetType) {\n");
        write("    const suggestions = [];\n");
        write("    const targetLower = targetType.toLowerCase();\n");
        write("    \n");
        write("    // Search in allSchemaClasses\n");
        write("    for (const className in reachabilityData.allSchemaClasses) {\n");
        write("        if (className.toLowerCase().includes(targetLower) || \n");
        write("            targetLower.includes(className.toLowerCase()) ||\n");
        write("            className.toLowerCase().endsWith(targetLower.split('.').pop())) {\n");
        write("            suggestions.push(className);\n");
        write("        }\n");
        write("    }\n");
        write("    \n");
        write("    return suggestions.slice(0, 5); // Limit to 5 suggestions\n");
        write("}\n\n");

        // Find full class name from short name
        write("function findFullClassName(shortName) {\n");
        write("    // First check in allSchemaClasses\n");
        write("    for (const className in reachabilityData.allSchemaClasses) {\n");
        write("        const classInfo = reachabilityData.allSchemaClasses[className];\n");
        write("        if (classInfo.shortName === shortName || className.endsWith('.' + shortName)) {\n");
        write("            return className;\n");
        write("        }\n");
        write("    }\n");
        write("    \n");
        write("    // Then check in modules\n");
        write("    for (const moduleName in reachabilityData.modules) {\n");
        write("        const module = reachabilityData.modules[moduleName];\n");
        write("        for (const className in module.classes) {\n");
        write("            const classInfo = module.classes[className];\n");
        write("            if (classInfo.shortName === shortName || className.endsWith('.' + shortName)) {\n");
        write("                return className;\n");
        write("            }\n");
        write("        }\n");
        write("    }\n");
        write("    \n");
        write("    return null;\n");
        write("}\n\n");

        // Enhanced loadClassFields that accepts depth parameter
        write("function loadClassFields(container, className, depth) {\n");
        write("    if (typeof depth === 'undefined') depth = 0;\n");
        write("    \n");
        write("    const classInfo = findClassDefinition(className);\n");
        write("    if (!classInfo) {\n");
        write("        container.innerHTML = '<div class=\"not-found\">Class definition not found</div>';\n");
        write("        return;\n");
        write("    }\n");
        write("    \n");
        write("    // Use existing loadClassFields implementation but with depth awareness\n");
        write("    // This calls the original loadClassFields logic but with enhanced drill-down support\n");
        write("    loadClassFieldsImpl(container, className, classInfo, depth);\n");
        write("}\n\n");

        // Implementation method to avoid infinite recursion
        write("function loadClassFieldsImpl(container, className, classInfo, depth) {\n");
        write("    // Group fields by type\n");
        write("    const fieldGroups = {\n");
        write("        primitive: [],\n");
        write("        reference: [],\n");
        write("        collection: [],\n");
        write("        unknown: []\n");
        write("    };\n");
        write("    \n");
        write("    for (const fieldName in classInfo.fields) {\n");
        write("        const field = classInfo.fields[fieldName];\n");
        write("        if (field.isPrimitive) {\n");
        write("            fieldGroups.primitive.push(field);\n");
        write("        } else if (field.isReference) {\n");
        write("            fieldGroups.reference.push(field);\n");
        write("        } else if (field.isCollection) {\n");
        write("            fieldGroups.collection.push(field);\n");
        write("        } else {\n");
        write("            fieldGroups.unknown.push(field);\n");
        write("        }\n");
        write("    }\n");
        write("    \n");
        write("    let html = '<div class=\"class-summary\">';\n");
        write("    html += '<h4>📊 ' + escapeHtml(classInfo.shortName || classInfo.name) + '</h4>';\n");
        write("    if (classInfo.superClass) {\n");
        write("        html += '<p><strong>Extends:</strong> <a href=\"#\" onclick=\"drillIntoType(this, \\'' + classInfo.superClass + '\\', ' + depth + '); return false;\">' + escapeHtml(classInfo.superClass) + '</a></p>';\n");
        write("    }\n");
        write("    html += '<p><strong>Total Fields:</strong> ' + classInfo.fieldCount + '</p>';\n");
        write("    html += '</div>';\n");
        write("    \n");
        write("    // Add field category sections with enhanced controls\n");
        write("    const categories = [\n");
        write("        { key: 'reference', label: '🔗 Object References', icon: '✅', color: '#4CAF50' },\n");
        write("        { key: 'collection', label: '📦 Collections', icon: '📦', color: '#2196F3' },\n");
        write("        { key: 'primitive', label: '🔧 Primitive Fields', icon: '🔧', color: '#9E9E9E' },\n");
        write("        { key: 'unknown', label: '❓ Unknown Types', icon: '❓', color: '#FF9800' }\n");
        write("    ];\n");
        write("    \n");
        write("    categories.forEach(category => {\n");
        write("        const fields = fieldGroups[category.key];\n");
        write("        if (fields.length > 0) {\n");
        write("            html += '<div class=\"field-category\">';\n");
        write("            html += '<div class=\"category-header\" onclick=\"toggleFieldCategory(this)\">';\n");
        write("            html += '<div class=\"category-title\">';\n");
        write("            html += '<span style=\"color: ' + category.color + ';\">' + category.label + '</span>';\n");
        write("            html += '<span class=\"field-count\">(' + fields.length + ')</span>';\n");
        write("            html += '</div>';\n");
        write("            html += '<div class=\"category-expand\">+</div>';\n");
        write("            html += '</div>';\n");
        write("            html += '<div class=\"category-content\" style=\"display: none;\">';\n");
        write("            \n");
        write("            fields.forEach(field => {\n");
        write("                html += '<div class=\"field-item\">';\n");
        write("                html += '<div class=\"field-main\"';\n");
        write("                if (category.key !== 'primitive') {\n");
        write("                    html += ' onclick=\"toggleFieldExpansion(this, \\'' + field.type + '\\', \\'' + field.name + '\\', ' + depth + ')\">'; \n");
        write("                } else {\n");
        write("                    html += '>';\n");
        write("                }\n");
        write("                html += '<div class=\"field-icon\">' + category.icon + '</div>';\n");
        write("                html += '<div class=\"field-info\">';\n");
        write("                html += '<div class=\"field-name\">' + escapeHtml(field.name) + '</div>';\n");
        write("                html += '<div class=\"field-type\">' + escapeHtml(field.type) + '</div>';\n");
        write("                html += '</div>';\n");
        write("                if (category.key !== 'primitive') {\n");
        write("                    html += '<div class=\"field-expand\">🔍</div>';\n");
        write("                }\n");
        write("                html += '</div>';\n");
        write("                if (category.key !== 'primitive') {\n");
        write("                    html += '<div class=\"field-content\" style=\"display: none;\" data-field-path=\"' + className + '.' + field.name + '\"></div>';\n");
        write("                }\n");
        write("                html += '</div>';\n");
        write("            });\n");
        write("            \n");
        write("            html += '</div>';\n");
        write("            html += '</div>';\n");
        write("        }\n");
        write("    });\n");
        write("    \n");
        write("    container.innerHTML = html;\n");
        write("}\n\n");

        // Drill into type function for hyperlink navigation
        write("function drillIntoType(element, typeName, currentDepth) {\n");
        write("    const container = element.closest('.field-content') || element.closest('.nested-content');\n");
        write("    if (container) {\n");
        write("        loadFieldContent(container, typeName, typeName, currentDepth);\n");
        write("    }\n");
        write("}\n\n");
    }

    private void writeHelperFunctions() throws IOException {
        // Page load initialization
        write("// Initialize the page when DOM is loaded\n");
        write("document.addEventListener('DOMContentLoaded', function() {\n");
        write("    initializePage();\n");
        write("});\n\n");

        // Enhanced toggle field category function
        write("function toggleFieldCategory(header) {\n");
        write("    const content = header.nextElementSibling;\n");
        write("    const expandIcon = header.querySelector('.category-expand');\n");
        write("    \n");
        write("    if (content.style.display === 'none') {\n");
        write("        content.style.display = 'block';\n");
        write("        expandIcon.textContent = '-';\n");
        write("    } else {\n");
        write("        content.style.display = 'none';\n");
        write("        expandIcon.textContent = '+';\n");
        write("    }\n");
        write("}\n\n");

        // Search functionality for finding types
        write("function searchType(query) {\n");
        write("    const results = [];\n");
        write("    const queryLower = query.toLowerCase();\n");
        write("    \n");
        write("    // Search in all schema classes\n");
        write("    for (const className in reachabilityData.allSchemaClasses) {\n");
        write("        const classInfo = reachabilityData.allSchemaClasses[className];\n");
        write("        if (className.toLowerCase().includes(queryLower) || \n");
        write("            (classInfo.shortName && classInfo.shortName.toLowerCase().includes(queryLower))) {\n");
        write("            results.push({\n");
        write("                type: 'class',\n");
        write("                name: className,\n");
        write("                shortName: classInfo.shortName,\n");
        write("                fieldCount: classInfo.fieldCount\n");
        write("            });\n");
        write("        }\n");
        write("    }\n");
        write("    \n");
        write("    return results.slice(0, 10); // Limit results\n");
        write("}\n\n");

        // Function to drill into a type
        write("function drillIntoType(element, typeName, depth) {\n");
        write("    const classInfo = findClassDefinition(typeName);\n");
        write("    if (classInfo) {\n");
        write("        const container = element.closest('.class-summary').parentElement;\n");
        write("        loadClassFields(container, typeName, depth + 1);\n");
        write("    }\n");
        write("}\n\n");

        // Function to toggle field category expansion
        write("function toggleFieldCategory(header) {\n");
        write("    const content = header.nextElementSibling;\n");
        write("    const expandIcon = header.querySelector('.category-expand');\n");
        write("    \n");
        write("    if (content.style.display === 'none') {\n");
        write("        content.style.display = 'block';\n");
        write("        expandIcon.textContent = '-';\n");
        write("    } else {\n");
        write("        content.style.display = 'none';\n");
        write("        expandIcon.textContent = '+';\n");
        write("    }\n");
        write("}\n\n");

        // Navigation breadcrumb functionality
        write("function updateBreadcrumb(path) {\n");
        write("    const breadcrumb = document.querySelector('.breadcrumb');\n");
        write("    if (breadcrumb && path) {\n");
        write("        breadcrumb.innerHTML = '🏠 ' + path.split('.').map(part => {\n");
        write("            return '<span class=\"breadcrumb-item\">' + escapeHtml(part) + '</span>';\n");
        write("        }).join(' <span class=\"breadcrumb-separator\">→</span> ');\n");
        write("    }\n");
        write("}\n\n");

        // Escape HTML function
        write("function escapeHtml(text) {\n");
        write("    if (typeof text !== 'string') {\n");
        write("        text = String(text);\n");
        write("    }\n");
        write("    const div = document.createElement('div');\n");
        write("    div.textContent = text;\n");
        write("    return div.innerHTML;\n");
        write("}\n");
    }
}