package migration4o.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

public final class XmlViewerHtmlGenerator {

    private XmlViewerHtmlGenerator() {
    }

    public static Path writeViewerForXml(Path xmlPath) throws IOException {
        if (xmlPath == null) {
            throw new IllegalArgumentException("xmlPath must not be null");
        }

        String fileName = xmlPath.getFileName() != null ? xmlPath.getFileName().toString() : "export.xml";
        Path htmlPath = resolveHtmlPath(xmlPath);
        String embeddedXmlBase64 = Base64.getEncoder().encodeToString(Files.readAllBytes(xmlPath));
        String html = buildHtml(fileName, embeddedXmlBase64);

        if (htmlPath.getParent() != null) {
            Files.createDirectories(htmlPath.getParent());
        }
        Files.write(htmlPath, html.getBytes(StandardCharsets.UTF_8));
        return htmlPath;
    }

    private static Path resolveHtmlPath(Path xmlPath) {
        String fileName = xmlPath.getFileName() != null ? xmlPath.getFileName().toString() : "export.xml";
        int extensionIndex = fileName.lastIndexOf('.');
        String baseName = extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
        String htmlName = baseName + ".html";
        return xmlPath.resolveSibling(htmlName);
    }

    private static String buildHtml(String xmlFileName, String embeddedXmlBase64) {
        String safeXmlFileName = escapeJsString(xmlFileName);
        String safeXmlBase64 = escapeJsString(embeddedXmlBase64);
        String safeTitle = escapeHtml(xmlFileName);

        String template = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>Migration Data Viewer - __TITLE__</title>
                    <style>
                        :root { color-scheme: light; }
                        * { box-sizing: border-box; }
                        body {
                            margin: 0;
                            font-family: Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Arial, sans-serif;
                            background: #f5f7fa;
                            color: #17212b;
                        }
                        .page {
                            max-width: 1500px;
                            margin: 0 auto;
                            padding: 18px;
                        }
                        .header {
                            margin-bottom: 14px;
                        }
                        .title {
                            margin: 0;
                            font-size: 24px;
                            font-weight: 700;
                        }
                        .subtitle {
                            margin-top: 6px;
                            color: #4f5b67;
                            font-size: 13px;
                        }
                        .card {
                            background: #ffffff;
                            border: 1px solid #dde3ea;
                            border-radius: 10px;
                            box-shadow: 0 1px 2px rgba(17, 24, 39, 0.04);
                        }
                        .filters {
                            display: grid;
                            grid-template-columns: minmax(170px, 1fr) minmax(170px, 1fr) minmax(220px, 2fr) auto;
                            gap: 10px;
                            align-items: end;
                            padding: 14px;
                            margin-bottom: 14px;
                        }
                        .field {
                            display: flex;
                            flex-direction: column;
                            gap: 5px;
                        }
                        .field label {
                            font-size: 12px;
                            font-weight: 600;
                            color: #485666;
                        }
                        .field select,
                        .field input,
                        .field button {
                            height: 36px;
                            padding: 0 10px;
                            border: 1px solid #cfd8e3;
                            border-radius: 8px;
                            font-size: 14px;
                            background: #fff;
                            color: #17212b;
                        }
                        .field select option,
                        .pagination select option {
                            color: #17212b;
                            background: #ffffff;
                        }
                        .field button {
                            background: #f4f7fb;
                            cursor: pointer;
                        }
                        .layout {
                            display: grid;
                            grid-template-columns: 1.2fr 1fr;
                            gap: 14px;
                        }
                        .section-title {
                            margin: 0;
                            padding: 12px 14px;
                            font-size: 15px;
                            font-weight: 700;
                            border-bottom: 1px solid #e8edf3;
                        }
                        .results-body {
                            padding: 0;
                        }
                        .status {
                            padding: 10px 14px;
                            font-size: 13px;
                            color: #4f5b67;
                            border-bottom: 1px solid #eef2f7;
                        }
                        table {
                            width: 100%;
                            border-collapse: collapse;
                        }
                        thead th {
                            text-align: left;
                            font-size: 12px;
                            color: #4f5b67;
                            font-weight: 700;
                            border-bottom: 1px solid #e8edf3;
                            padding: 10px 12px;
                            background: #fafcff;
                        }
                        tbody td {
                            font-size: 13px;
                            border-bottom: 1px solid #eef2f7;
                            padding: 9px 12px;
                            vertical-align: top;
                        }
                        tbody tr:hover {
                            background: #f8fbff;
                        }
                        tbody tr.selected {
                            background: #e9f2ff;
                        }
                        tbody tr {
                            cursor: pointer;
                        }
                        .pagination {
                            padding: 10px 12px;
                            display: flex;
                            align-items: center;
                            justify-content: space-between;
                            gap: 10px;
                        }
                        .pagination-controls {
                            display: flex;
                            gap: 8px;
                            align-items: center;
                        }
                        .pagination button,
                        .pagination select {
                            height: 32px;
                            border: 1px solid #cfd8e3;
                            border-radius: 8px;
                            background: #fff;
                            padding: 0 10px;
                            color: #17212b;
                        }
                        .detail-body {
                            padding: 14px;
                            max-height: 74vh;
                            overflow: auto;
                        }
                        .empty {
                            color: #6b7785;
                            font-size: 13px;
                            padding: 10px 2px;
                        }
                        .record-title {
                            font-size: 18px;
                            margin: 0;
                            font-weight: 700;
                        }
                        .record-subtitle {
                            margin: 5px 0 14px;
                            color: #4f5b67;
                            font-size: 13px;
                        }
                        .form-grid {
                            display: grid;
                            grid-template-columns: minmax(180px, 260px) 1fr;
                            gap: 8px 12px;
                            align-items: start;
                        }
                        .label {
                            font-size: 12px;
                            font-weight: 700;
                            color: #465362;
                            word-break: break-word;
                        }
                        .value {
                            font-size: 13px;
                            color: #1c2733;
                            white-space: pre-wrap;
                            word-break: break-word;
                            background: #f9fbfd;
                            border: 1px solid #e7edf5;
                            border-radius: 8px;
                            padding: 6px 8px;
                        }
                        @media (max-width: 1200px) {
                            .layout { grid-template-columns: 1fr; }
                        }
                        @media (max-width: 760px) {
                            .filters { grid-template-columns: 1fr; }
                            .form-grid { grid-template-columns: 1fr; }
                        }
                    </style>
                </head>
                <body>
                <div class="page">
                    <header class="header">
                        <h1 class="title">Migration Data Viewer</h1>
                        <div class="subtitle">File: __TITLE__</div>
                    </header>

                    <section class="card filters">
                        <div class="field">
                            <label for="entitySelect">Entity</label>
                            <select id="entitySelect"></select>
                        </div>
                        <div class="field">
                            <label for="fieldSelect">Field</label>
                            <select id="fieldSelect"></select>
                        </div>
                        <div class="field">
                            <label for="queryInput">Search text</label>
                            <input id="queryInput" type="search" placeholder="Search values...">
                        </div>
                        <div class="field">
                            <label>&nbsp;</label>
                            <button id="clearBtn" type="button">Clear filters</button>
                        </div>
                    </section>

                    <div class="layout">
                        <section class="card">
                            <h2 class="section-title">Results</h2>
                            <div id="status" class="status">Loading embedded data...</div>
                            <div class="results-body">
                                <table>
                                    <thead>
                                    <tr>
                                        <th style="width:70px;">#</th>
                                        <th style="width:170px;">Entity</th>
                                        <th style="width:160px;">ID</th>
                                        <th>Main info</th>
                                    </tr>
                                    </thead>
                                    <tbody id="resultsBody"></tbody>
                                </table>
                            </div>
                            <div class="pagination">
                                <div class="pagination-controls">
                                    <button id="prevBtn" type="button">Previous</button>
                                    <span id="pageInfo">Page 1 / 1</span>
                                    <button id="nextBtn" type="button">Next</button>
                                </div>
                                <div class="pagination-controls">
                                    <label for="pageSizeSelect">Rows:</label>
                                    <select id="pageSizeSelect">
                                        <option>25</option>
                                        <option selected>50</option>
                                        <option>100</option>
                                    </select>
                                </div>
                            </div>
                        </section>

                        <section class="card">
                            <h2 class="section-title">Record Details</h2>
                            <div id="detailBody" class="detail-body">
                                <div class="empty">Select a result to view its details.</div>
                            </div>
                        </section>
                    </div>
                </div>

                <script>
                    const xmlFileName = '__XML_FILE_NAME__';
                    const embeddedXmlBase64 = '__XML_BASE64__';

                    const entitySelect = document.getElementById('entitySelect');
                    const fieldSelect = document.getElementById('fieldSelect');
                    const queryInput = document.getElementById('queryInput');
                    const clearBtn = document.getElementById('clearBtn');
                    const statusEl = document.getElementById('status');
                    const resultsBody = document.getElementById('resultsBody');
                    const detailBody = document.getElementById('detailBody');
                    const prevBtn = document.getElementById('prevBtn');
                    const nextBtn = document.getElementById('nextBtn');
                    const pageInfo = document.getElementById('pageInfo');
                    const pageSizeSelect = document.getElementById('pageSizeSelect');

                    let allRecords = [];
                    let filteredRecords = [];
                    let currentPage = 1;
                    let selectedRecordKey = null;

                    function decodeBase64Utf8(base64) {
                        const binary = atob(base64);
                        const bytes = new Uint8Array(binary.length);
                        for (let index = 0; index < binary.length; index++) {
                            bytes[index] = binary.charCodeAt(index);
                        }
                        return new TextDecoder('utf-8').decode(bytes);
                    }

                    function appendFieldValue(map, key, value) {
                        if (!key || !value) {
                            return;
                        }
                        if (map[key]) {
                            if (!map[key].includes(value)) {
                                map[key] += ' | ' + value;
                            }
                        } else {
                            map[key] = value;
                        }
                    }

                    function removeIndexSuffix(segment) {
                        return String(segment || '').replace(/\\[\\d+\\]$/, '');
                    }

                    function shouldCollapseSegment(prefix, childTagName) {
                        if (!prefix) {
                            return false;
                        }

                        const parts = prefix.split('.');
                        const lastSegment = removeIndexSuffix(parts[parts.length - 1]);
                        return lastSegment.toLowerCase() === String(childTagName || '').toLowerCase();
                    }

                    function collectFieldsFromElement(element, prefix, map) {
                        const children = Array.from(element.children || []);
                        if (children.length === 0) {
                            const text = (element.textContent || '').trim();
                            if (text) {
                                appendFieldValue(map, prefix || element.tagName, text);
                            }
                            return;
                        }

                        const occurrences = {};
                        children.forEach(child => {
                            occurrences[child.tagName] = (occurrences[child.tagName] || 0) + 1;
                        });

                        const counters = {};
                        children.forEach(child => {
                            counters[child.tagName] = (counters[child.tagName] || 0) + 1;
                            const isRepeated = occurrences[child.tagName] > 1;
                            const segment = isRepeated ? child.tagName + '[' + counters[child.tagName] + ']' : child.tagName;
                            const collapseSegment = shouldCollapseSegment(prefix, child.tagName);
                            const path = prefix
                                ? (collapseSegment ? prefix : prefix + '.' + segment)
                                : segment;

                            if ((child.children || []).length === 0) {
                                const value = (child.textContent || '').trim();
                                appendFieldValue(map, path, value);
                            } else {
                                collectFieldsFromElement(child, path, map);
                            }
                        });
                    }

                    function pickBestValue(fields, candidates) {
                        const loweredEntries = Object.entries(fields).map(([key, value]) => [key.toLowerCase(), value]);
                        for (const candidate of candidates) {
                            const normalized = candidate.toLowerCase();
                            const exact = loweredEntries.find(([key]) => key === normalized);
                            if (exact && exact[1]) {
                                return exact[1];
                            }
                        }
                        for (const candidate of candidates) {
                            const normalized = candidate.toLowerCase();
                            const partial = loweredEntries.find(([key]) => key.includes(normalized));
                            if (partial && partial[1]) {
                                return partial[1];
                            }
                        }
                        return '';
                    }

                    function summarizeRecord(record) {
                        const preferred = pickBestValue(record.fields, ['nom', 'name', 'titre', 'title', 'description', 'libelle', 'label']);
                        if (preferred) {
                            return preferred;
                        }
                        const values = Object.values(record.fields).filter(Boolean);
                        return values.length > 0 ? values.slice(0, 2).join(' • ') : '(no details)';
                    }

                    function buildRecord(element, index) {
                        const fields = {};
                        Array.from(element.attributes || []).forEach(attribute => {
                            appendFieldValue(fields, '@' + attribute.name, attribute.value);
                        });
                        collectFieldsFromElement(element, '', fields);

                        const id = element.getAttribute('id')
                            || pickBestValue(fields, ['id', 'identifiant', 'numero', 'code'])
                            || '';

                        const record = {
                            key: String(index),
                            position: index + 1,
                            entity: element.tagName,
                            id,
                            fields
                        };
                        record.summary = summarizeRecord(record);
                        return record;
                    }

                    function parseEmbeddedXml() {
                        const xmlText = decodeBase64Utf8(embeddedXmlBase64);
                        const parser = new DOMParser();
                        const xmlDocument = parser.parseFromString(xmlText, 'application/xml');
                        const parseError = xmlDocument.querySelector('parsererror');
                        if (parseError) {
                            throw new Error('Invalid XML document');
                        }

                        const objectContainer = xmlDocument.querySelector('export > objects') || xmlDocument.documentElement;
                        const objectNodes = Array.from(objectContainer.children || []);
                        allRecords = objectNodes.map((node, index) => buildRecord(node, index));
                    }

                    function populateEntityOptions() {
                        const entities = Array.from(new Set(allRecords.map(record => record.entity))).sort((a, b) => a.localeCompare(b));
                        entitySelect.innerHTML = '<option value="__all">All entities</option>';
                        entities.forEach(entity => {
                            const option = document.createElement('option');
                            option.value = entity;
                            option.textContent = entity;
                            entitySelect.appendChild(option);
                        });
                    }

                    function populateFieldOptions() {
                        const entity = entitySelect.value;
                        const source = entity === '__all'
                            ? allRecords
                            : allRecords.filter(record => record.entity === entity);

                        const fieldNames = new Set();
                        source.forEach(record => {
                            Object.keys(record.fields).forEach(name => fieldNames.add(name));
                        });

                        const sortedFields = Array.from(fieldNames).sort((a, b) => a.localeCompare(b));
                        const previousValue = fieldSelect.value;
                        fieldSelect.innerHTML = '<option value="__all">All fields</option>';

                        const groups = new Map();
                        sortedFields.forEach(field => {
                            const isAttribute = field.startsWith('@');
                            const rootName = isAttribute ? 'Attributes' : field.split('.')[0];
                            const currentGroup = groups.get(rootName) || [];

                            let label = field;
                            if (!isAttribute && field.includes('.')) {
                                label = field.substring(rootName.length + 1);
                            }
                            const nestingDepth = Math.max(0, (label.match(/\\./g) || []).length);
                            const indent = '\u00A0\u00A0'.repeat(nestingDepth);

                            currentGroup.push({
                                value: field,
                                label: indent + label
                            });
                            groups.set(rootName, currentGroup);
                        });

                        Array.from(groups.keys()).sort((a, b) => a.localeCompare(b)).forEach(groupName => {
                            const options = groups.get(groupName) || [];
                            if (options.length === 0) {
                                return;
                            }

                            if (groupName === 'Attributes') {
                                options.forEach(item => {
                                    const option = document.createElement('option');
                                    option.value = item.value;
                                    option.textContent = item.label;
                                    fieldSelect.appendChild(option);
                                });
                                return;
                            }

                            const optGroup = document.createElement('optgroup');
                            optGroup.label = groupName;
                            options.forEach(item => {
                                const option = document.createElement('option');
                                option.value = item.value;
                                option.textContent = item.label;
                                optGroup.appendChild(option);
                            });
                            fieldSelect.appendChild(optGroup);
                        });

                        if (previousValue && (previousValue === '__all' || sortedFields.includes(previousValue))) {
                            fieldSelect.value = previousValue;
                        }
                    }

                    function getPageSize() {
                        const parsed = Number.parseInt(pageSizeSelect.value, 10);
                        return Number.isFinite(parsed) && parsed > 0 ? parsed : 50;
                    }

                    function matchesSearch(record, query, field) {
                        if (!query) {
                            return true;
                        }
                        if (field === '__all') {
                            const haystack = [record.entity, record.id, record.summary, ...Object.values(record.fields)]
                                .filter(Boolean)
                                .join(' ')
                                .toLowerCase();
                            return haystack.includes(query);
                        }

                        const value = (record.fields[field] || '').toLowerCase();
                        return value.includes(query);
                    }

                    function renderDetail(record) {
                        if (!record) {
                            detailBody.innerHTML = '<div class="empty">Select a result to view its details.</div>';
                            return;
                        }

                        const fields = Object.entries(record.fields).sort((left, right) => left[0].localeCompare(right[0]));
                        if (fields.length === 0) {
                            detailBody.innerHTML = '<h3 class="record-title">' + record.entity + '</h3><div class="empty">No visible fields available for this record.</div>';
                            return;
                        }

                        let rows = '';
                        fields.forEach(([name, value]) => {
                            rows += '<div class="label">' + escapeHtml(name) + '</div>';
                            rows += '<div class="value">' + escapeHtml(value) + '</div>';
                        });

                        detailBody.innerHTML = ''
                            + '<h3 class="record-title">' + escapeHtml(record.entity) + '</h3>'
                            + '<div class="record-subtitle">ID: ' + escapeHtml(record.id || '—') + ' • Record #' + record.position + '</div>'
                            + '<div class="form-grid">' + rows + '</div>';
                    }

                    function renderResults() {
                        const pageSize = getPageSize();
                        const total = filteredRecords.length;
                        const totalPages = Math.max(1, Math.ceil(total / pageSize));

                        if (currentPage > totalPages) {
                            currentPage = totalPages;
                        }

                        const start = (currentPage - 1) * pageSize;
                        const end = Math.min(total, start + pageSize);
                        const pageRows = filteredRecords.slice(start, end);

                        resultsBody.innerHTML = '';
                        if (pageRows.length === 0) {
                            const row = document.createElement('tr');
                            row.innerHTML = '<td colspan="4" class="empty">No records found for current filters.</td>';
                            resultsBody.appendChild(row);
                        } else {
                            pageRows.forEach(record => {
                                const row = document.createElement('tr');
                                if (record.key === selectedRecordKey) {
                                    row.classList.add('selected');
                                }
                                row.innerHTML = ''
                                    + '<td>' + record.position + '</td>'
                                    + '<td>' + escapeHtml(record.entity) + '</td>'
                                    + '<td>' + escapeHtml(record.id || '—') + '</td>'
                                    + '<td>' + escapeHtml(record.summary) + '</td>';

                                row.addEventListener('click', () => {
                                    selectedRecordKey = record.key;
                                    renderResults();
                                    renderDetail(record);
                                });

                                resultsBody.appendChild(row);
                            });
                        }

                        pageInfo.textContent = 'Page ' + currentPage + ' / ' + totalPages;
                        prevBtn.disabled = currentPage <= 1;
                        nextBtn.disabled = currentPage >= totalPages;

                        statusEl.textContent = 'File ' + xmlFileName + ' • ' + total + ' result(s)';

                        if (!filteredRecords.some(record => record.key === selectedRecordKey)) {
                            selectedRecordKey = null;
                            renderDetail(null);
                        }
                    }

                    function applyFilters() {
                        const entity = entitySelect.value;
                        const field = fieldSelect.value;
                        const query = queryInput.value.trim().toLowerCase();

                        filteredRecords = allRecords.filter(record => {
                            if (entity !== '__all' && record.entity !== entity) {
                                return false;
                            }
                            return matchesSearch(record, query, field);
                        });

                        currentPage = 1;
                        renderResults();
                    }

                    function escapeHtml(value) {
                        return String(value)
                            .replace(/&/g, '&amp;')
                            .replace(/</g, '&lt;')
                            .replace(/>/g, '&gt;')
                            .replace(/\"/g, '&quot;')
                            .replace(/'/g, '&#39;');
                    }

                    function initialize() {
                        try {
                            parseEmbeddedXml();
                            populateEntityOptions();
                            populateFieldOptions();
                            filteredRecords = allRecords.slice();
                            renderResults();
                        } catch (error) {
                            statusEl.textContent = 'Unable to read embedded data: ' + (error && error.message ? error.message : 'Unknown error');
                            resultsBody.innerHTML = '<tr><td colspan="4" class="empty">Viewer initialization failed.</td></tr>';
                            detailBody.innerHTML = '<div class="empty">No record details available.</div>';
                        }
                    }

                    entitySelect.addEventListener('change', () => {
                        populateFieldOptions();
                        applyFilters();
                    });
                    fieldSelect.addEventListener('change', applyFilters);
                    queryInput.addEventListener('input', applyFilters);
                    pageSizeSelect.addEventListener('change', renderResults);

                    clearBtn.addEventListener('click', () => {
                        entitySelect.value = '__all';
                        queryInput.value = '';
                        populateFieldOptions();
                        fieldSelect.value = '__all';
                        applyFilters();
                    });

                    prevBtn.addEventListener('click', () => {
                        if (currentPage > 1) {
                            currentPage--;
                            renderResults();
                        }
                    });

                    nextBtn.addEventListener('click', () => {
                        const totalPages = Math.max(1, Math.ceil(filteredRecords.length / getPageSize()));
                        if (currentPage < totalPages) {
                            currentPage++;
                            renderResults();
                        }
                    });

                    initialize();
                </script>
                </body>
                </html>
                """;

        return template.replace("__TITLE__", safeTitle).replace("__XML_FILE_NAME__", safeXmlFileName).replace("__XML_BASE64__", safeXmlBase64);
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String escapeJsString(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("'", "\\'").replace("\r", "").replace("\n", "");
    }
}