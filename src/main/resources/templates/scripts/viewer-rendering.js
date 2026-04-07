function renderFieldRow(entry) {
    var label = displayFieldLabel(entry.key);
    var destName = String(entry.key || '').split('.').pop() || '';
    var titleAttr = destName && destName !== label ? ' title="' + esc(destName) + '"' : '';
    // If this field is a direct IDEntite reference (embedContents=false), its value IS the ID —
    // render it as a clickable link to the target entity page.
    var _ptDestName = pointsToByPath[normalizeSchemaPath(entry.key || '')];
    var _ptHref = _ptDestName ? navHrefByDestName[_ptDestName] : null;
    var _idVal = _ptHref ? String(entry.value ?? '').trim() : null;
    if (_idVal === '0' || _idVal === '-1' || _idVal === '') _idVal = null;
    var valueHtml = (_ptHref && _idVal)
        ? refLinkBtn(_ptHref + '?open=' + encodeURIComponent(_idVal), _idVal)
        : fmtValue(entry.value, entry.key);
    return '<div class="field-row"><div class="field-label"' + titleAttr + '>' + esc(label) + '</div><div class="field-value">' + valueHtml + '</div></div>';
}

function renderColumnGroup(entries, cols, subtitle) {
    if (!entries || entries.length === 0) return '';
    let html = '<div class="field-group">';
    if (subtitle) html += '<div class="field-group-subtitle">' + esc(subtitle) + '</div>';
    if (entries.length >= cols) {
        html += '<div class="field-columns-' + cols + '">';
        entries.forEach((e) => { html += renderFieldRow(e); });
        html += '</div>';
    } else {
        entries.forEach((e) => { html += renderFieldRow(e); });
    }
    html += '</div>';
    return html;
}

function renderPrimitiveGroup(entries) {
    if (!entries || entries.length === 0) return '';

    const sorted = sortPrimitiveEntries(entries);

    const buckets = { bool: [], id: [], date: [], number: [], short: [], long: [], empty: [] };
    sorted.forEach((entry) => {
        const bucket = classifyPrimitiveBucket(entry.key, entry.value);
        buckets[bucket].push(entry);
    });

    let html = '';

    // Booleans: render in 3-column grid if 3+, 2-column if 2
    if (buckets.bool.length >= 3) {
        html += renderColumnGroup(buckets.bool, 3, t('flags'));
    } else if (buckets.bool.length === 2) {
        html += renderColumnGroup(buckets.bool, 2, t('flags'));
    }

    // Dates: render in 2-column grid if 2+
    if (buckets.date.length >= 2) {
        html += renderColumnGroup(buckets.date, 2, t('dates'));
    }

    // IDs: render in 2-column grid if 2+
    if (buckets.id.length >= 2) {
        html += renderColumnGroup(buckets.id, 2, t('identifiers'));
    }

    // Numbers: render in 3-column grid if 3+, otherwise mix with remaining
    if (buckets.number.length >= 3) {
        html += renderColumnGroup(buckets.number, 3, t('numbers'));
    }

    // Remaining: collect singletons from above + all short/long text
    const remaining = [];
    if (buckets.bool.length === 1) remaining.push(...buckets.bool);
    if (buckets.date.length === 1) remaining.push(...buckets.date);
    if (buckets.id.length === 1) remaining.push(...buckets.id);
    if (buckets.number.length < 3) remaining.push(...buckets.number);
    remaining.push(...buckets.short);
    remaining.push(...buckets.long);

    // Try to sub-group remaining short fields by prefix
    if (remaining.length > 0) {
        const shortRemaining = remaining.filter((e) => {
            const text = String(e.value ?? '').trim();
            return text.length <= 30;
        });
        const longRemaining = remaining.filter((e) => {
            const text = String(e.value ?? '').trim();
            return text.length > 30;
        });

        const prefixGroups = groupByPrefix(shortRemaining);
        const rendered = new Set();
        let groupHtml = '';

        // Render prefix groups with 2+ entries as column groups
        Object.entries(prefixGroups).forEach(([prefix, group]) => {
            if (prefix !== '__ungrouped' && group.length >= 2) {
                const subtitle = schemaTitleForPath(prefix) || humanizeFieldName(prefix);
                groupHtml += renderColumnGroup(group, 2, subtitle);
                group.forEach((e) => rendered.add(e));
            }
        });

        // Collect ungrouped short + prefix singletons
        const ungrouped = shortRemaining.filter((e) => !rendered.has(e));

        // Render ungrouped using the old pair logic for narrow fields
        if (ungrouped.length > 0 || longRemaining.length > 0) {
            groupHtml += '<div class="field-group">';
            let idx = 0;
            while (idx < ungrouped.length) {
                const current = ungrouped[idx];
                const next = ungrouped[idx + 1];
                const currentNarrow = isNarrowField(current.key, current.value);
                const nextNarrow = next ? isNarrowField(next.key, next.value) : false;

                if (next && currentNarrow && nextNarrow) {
                    groupHtml += '<div class="field-pair">';
                    groupHtml += renderFieldRow(current);
                    groupHtml += renderFieldRow(next);
                    groupHtml += '</div>';
                    idx += 2;
                    continue;
                }
                groupHtml += renderFieldRow(current);
                idx += 1;
            }
            longRemaining.forEach((e) => { groupHtml += renderFieldRow(e); });
            groupHtml += '</div>';
        }

        html += groupHtml;
    }

    // Empty fields: compact 3-column group at the bottom
    if (buckets.empty.length > 0) {
        html += '<div class="field-empty-group">';
        html += '<div class="field-group-subtitle">' + esc(t('emptyFields')) + ' (' + buckets.empty.length + ')</div>';
        if (buckets.empty.length >= 3) {
            html += '<div class="field-columns-3">';
            buckets.empty.forEach((e) => { html += renderFieldRow(e); });
            html += '</div>';
        } else {
            buckets.empty.forEach((e) => { html += renderFieldRow(e); });
        }
        html += '</div>';
    }

    return html;
}

function appendRowValue(out, path, value) {
    const normalized = normalizeFieldPath(path || 'value');
    const text = String(value ?? '').trim();
    if (!normalized) return;
    if (!text) {
        if (out[normalized] === undefined) out[normalized] = '';
        return;
    }
    if (out[normalized]) {
        if (!out[normalized].includes(text)) out[normalized] += ' | ' + text;
    } else {
        out[normalized] = text;
    }
}

function flattenRow(value, path, out) {
    if (value === null || value === undefined) return;
    if (Array.isArray(value)) {
        if (value.length === 0) {
            appendRowValue(out, path || 'value', '');
            return;
        }
        const allPrimitive = value.every((item) => item === null || item === undefined || typeof item !== 'object');
        if (allPrimitive) {
            appendRowValue(out, path || 'value', value.map((item) => String(item ?? '')).filter((item) => item.length > 0).join(' | '));
            return;
        }
        value.forEach((item) => {
            flattenRow(item, path, out);
        });
        return;
    }
    if (typeof value !== 'object') {
        appendRowValue(out, path || 'value', value);
        return;
    }

    // Unwrap class-name wrapper from embedded object export
    value = unwrapClassWrapper(value);
    // Promote reserved properties as labeled fields
    if (value._id !== undefined) appendRowValue(out, path ? `${path}._id` : '_id', value._id);
    if (value._summary !== undefined) appendRowValue(out, path ? `${path}._summary` : '_summary', value._summary);
    if (value._preview !== undefined) appendRowValue(out, path ? `${path}._preview` : '_preview', value._preview);
    if (value._label !== undefined) appendRowValue(out, path || 'value', value._label);

    Object.entries(value).forEach(([k, v]) => {
        if (k.startsWith('_')) return;
        flattenRow(v, path ? `${path}.${k}` : k, out);
    });
}

function computeCollectionTable(items) {
    const rows = [];
    const columnsSet = new Set();

    items.forEach((item) => {
        if (item && typeof item === 'object') {
            const row = {};
            flattenRow(item, '', row);
            Object.keys(row).forEach((col) => columnsSet.add(col));
            rows.push(row);
        } else {
            const row = { value: String(item ?? '') };
            columnsSet.add('value');
            rows.push(row);
        }
    });

    const columns = Array.from(columnsSet).sort((a, b) => a.localeCompare(b));
    return { columns, rows };
}

function renderCollectionTableBody(view) {
    const start = (view.page - 1) * view.pageSize;
    const pageRows = view.rows.slice(start, start + view.pageSize);
    if (pageRows.length === 0) {
        return `<tr><td colspan="${Math.max(1, view.columns.length)}">${esc(t('noItems'))}</td></tr>`;
    }

    let html = '';
    pageRows.forEach((row) => {
        html += '<tr>';
        view.columns.forEach((col) => {
            html += '<td>' + fmtValue(row[col] ?? '', col) + '</td>';
        });
        html += '</tr>';
    });
    return html;
}

/**
 * Recursively collect all {ref, label} field entries from a layout JSON node array.
 * Layout nodes serialise properties under a "props" sub-object (see LayoutNode.appendJson).
 */
function extractLayoutFields(nodes) {
    var result = [];
    (nodes || []).forEach(function (n) {
        var p = n.props || {};
        if ((n.type === 'field' || n.type === 'table') && p.ref) {
            result.push({ ref: p.ref, label: p.label || displayFieldLabel(p.ref) });
        } else if (n.children && n.children.length > 0) {
            extractLayoutFields(n.children).forEach(function (f) { result.push(f); });
        }
    });
    return result;
}

// ── Cell popup: registry + render + stacked open/close ───────────────────
// _cellPopupRegistry holds arrays encountered during render, referenced by
// integer index from inline onclick handlers. Never reset — indices are stable.
var _cellPopupRegistry = [];
var _cellPopupStack = [];   // stack of live dialog elements

function _registerPopup(items) {
    var idx = _cellPopupRegistry.length;
    _cellPopupRegistry.push(items);
    return idx;
}

// SVG: two overlapping rectangles — standard "open in new window/popup" icon.
var _POPUP_ICON = '<svg width="11" height="11" viewBox="0 0 14 14" fill="currentColor" aria-hidden="true" style="margin-left:4px;vertical-align:-1px">'
    + '<rect x="3" y="3" width="10" height="10" rx="1.5" ry="1.5" fill="none" stroke="currentColor" stroke-width="1.5"/>'
    + '<rect x="0" y="0" width="8" height="8" rx="1.5" ry="1.5" fill="currentColor" opacity="0.35"/>'
    + '<rect x="1" y="1" width="6" height="6" rx="1" ry="1" fill="var(--c-surface,#fff)"/>'
    + '</svg>';

// Green trigger button placed in the table cell.
function _cellPopupBtn(idx, label, count) {
    var safe = String(label).replace(/\\/g, '\\\\').replace(/'/g, "\\'");
    return '<button type="button" class="cell-popup-btn" onclick="window.openCellPopup(' + idx + ',\'' + safe + '\')">'
        + count + _POPUP_ICON + '</button>';
}

// Renders items as a <table> inside the popup. Array cells get their own
// popup buttons — this is how nesting is handled recursively.
function renderPopupTable(items) {
    if (!items || items.length === 0) {
        return '<p class="popup-empty">\u2014</p>';
    }
    var firstItem = unwrapClassWrapper(items[0]);
    var _itemClass = firstItem && firstItem._class ? firstItem._class : null;
    var _layout = (_itemClass && typeof CLASS_LAYOUTS !== 'undefined' && CLASS_LAYOUTS) ? CLASS_LAYOUTS[_itemClass] : null;
    var cols;
    if (_layout) {
        cols = extractLayoutFields(_layout).map(function (f) { return { key: f.ref, label: f.label }; });
    } else {
        cols = [];
        Object.keys(firstItem || {}).forEach(function (k) {
            if (k.charAt(0) !== '_') cols.push({ key: k, label: displayFieldLabel(k) });
        });
    }
    if (cols.length === 0) return '<p class="popup-empty">\u2014</p>';
    // If any item carries a _preview, append a dedicated preview column.
    var _hasPreview = items.some(function (ri) { var it = unwrapClassWrapper(ri); return it && it._preview; });
    if (_hasPreview) cols.push({ key: '_preview', label: 'Aperçu' });

    // Shared cell renderer — returns HTML string or null (skip row/cell).
    function _renderCellValue(v, col) {
        if (v === null || v === undefined) return null;
        if (col.key === '_preview') {
            var _prevSrc = extractPreviewSrc(v);
            if (!_prevSrc) return null;
            var _CLIP = '<svg width="12" height="12" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">'
                + '<path d="M4.5 3a2.5 2.5 0 0 1 5 0v9a1.5 1.5 0 0 1-3 0V5a.5.5 0 0 1 1 0v7a.5.5 0 0 0 1 0V3a1.5 1.5 0 1 0-3 0v9a2.5 2.5 0 0 0 5 0V5a.5.5 0 0 1 1 0v7a3.5 3.5 0 1 1-7 0z"/>'
                + '</svg>';
            var _ss = _prevSrc.replace(/\\/g, '\\\\').replace(/'/g, "\\'");
            return '<button type="button" class="preview-reveal-btn" onclick="window.openPreviewPopup(\'' + _ss + '\',\'Fichier\')">' + _CLIP + 'Fichier</button>';
        }
        if (Array.isArray(v)) {
            if (v.length === 0) return null;
            var _pi = _registerPopup(v);
            return _cellPopupBtn(_pi, col.label, v.length);
        }
        if (v && typeof v === 'object') {
            if (v._id !== undefined) {
                var _rId = String(v._id ?? '').trim();
                var _rTxt = String(v._label || v._summary || _rId || '').trim();
                if (!_rTxt || _rTxt === '0' || _rTxt === '-1') return null;
                var _rDN = pointsToByPath[normalizeSchemaPath(col.key)] || (v._class ? v._class : null);
                var _rHr = _rDN ? navHrefByDestName[_rDN] : null;
                var _rLk = (_rHr && _rId && _rId !== '0' && _rId !== '-1') ? _rHr + '?open=' + encodeURIComponent(_rId) : null;
                return _rLk ? refLinkBtn(_rLk, _rTxt) : esc(_rTxt);
            }
            if (v._label !== undefined) return fmtValue(String(v._label || '').trim(), col.key);
            if (v._summary) return fmtValue(String(v._summary || '').trim(), col.key);
            var _oi = _registerPopup([v]);
            return _cellPopupBtn(_oi, col.label, '\u25a6');
        }
        return fmtValueWithFormat(v, null, col.key);
    }

    // Single item: 2-column key/value table.
    if (items.length === 1) {
        var singleItem = unwrapClassWrapper(items[0]);
        var html = '<table class="popup-kv-table">';
        cols.forEach(function (col) {
            var v = _layout ? resolveFieldValue(singleItem, col.key) : (singleItem ? singleItem[col.key] : undefined);
            var cell = _renderCellValue(unwrapClassWrapper(v), col);
            if (cell !== null) html += '<tr><th>' + esc(col.label) + '</th><td>' + cell + '</td></tr>';
        });
        html += '</table>';
        return html;
    }

    // Multiple items: horizontal data table.
    var html = '<div style="overflow-x:auto"><table class="collection-table"><thead><tr>';
    cols.forEach(function (col) { html += '<th>' + esc(col.label) + '</th>'; });
    html += '</tr></thead><tbody>';
    items.forEach(function (rawItem) {
        var item = unwrapClassWrapper(rawItem);
        html += '<tr>';
        cols.forEach(function (col) {
            var v = _layout ? resolveFieldValue(item, col.key) : (item ? item[col.key] : undefined);
            var cell = _renderCellValue(unwrapClassWrapper(v), col);
            html += cell !== null ? '<td>' + cell + '</td>' : '<td></td>';
        });
        html += '</tr>';
    });
    html += '</tbody></table></div>';
    return html;
}

// Each openCellPopup call creates a fresh full-screen overlay that contains
// the dialog. The overlay for level N covers — and thus dims and blocks —
// everything at level N-1 and below. Closing pops only the topmost overlay.
function openCellPopup(idx, title) {
    var depth = _cellPopupStack.length;
    var overlay = document.createElement('div');
    overlay.className = 'cell-popup-overlay open';
    overlay.style.zIndex = String(9000 + depth * 20);
    // Clicking the dim area (not the dialog) closes this popup only.
    overlay.addEventListener('click', function (e) {
        if (e.target === overlay) window.closeCellPopup();
    });
    var offsetPx = depth * 20;
    overlay.innerHTML = '<div class="cell-popup-dialog" style="margin-top:' + offsetPx + 'px;margin-left:' + offsetPx + 'px">'
        + '<div class="cell-popup-header">'
        + '<span class="cell-popup-title">' + esc(title) + '</span>'
        + '<button type="button" class="cell-popup-close" onclick="window.closeCellPopup()">&#x2715;</button>'
        + '</div>'
        + '<div class="cell-popup-body">' + renderPopupTable(_cellPopupRegistry[idx]) + '</div>'
        + '</div>';
    document.body.appendChild(overlay);
    _cellPopupStack.push(overlay);
}
window.openCellPopup = openCellPopup;

// Opens an image attachment in a stacked popup — same stack as openCellPopup.
function openPreviewPopup(src, title) {
    var depth = _cellPopupStack.length;
    var overlay = document.createElement('div');
    overlay.className = 'cell-popup-overlay open';
    overlay.style.zIndex = String(9000 + depth * 20);
    overlay.addEventListener('click', function (e) {
        if (e.target === overlay) window.closeCellPopup();
    });
    var offsetPx = depth * 20;
    overlay.innerHTML = '<div class="cell-popup-dialog" style="margin-top:' + offsetPx + 'px;margin-left:' + offsetPx + 'px">'
        + '<div class="cell-popup-header">'
        + '<span class="cell-popup-title">' + esc(title) + '</span>'
        + '<button type="button" class="cell-popup-close" onclick="window.closeCellPopup()">&#x2715;</button>'
        + '</div>'
        + '<div class="cell-popup-body" style="text-align:center;padding:16px">'
        + '<a href="' + esc(src) + '" target="_blank"><img src="' + esc(src) + '" style="max-width:100%;max-height:70vh;border-radius:6px" /></a>'
        + '</div>'
        + '</div>';
    document.body.appendChild(overlay);
    _cellPopupStack.push(overlay);
}
window.openPreviewPopup = openPreviewPopup;

function closeCellPopup() {
    var overlay = _cellPopupStack.pop();
    if (overlay) overlay.remove();
}
window.closeCellPopup = closeCellPopup;

// HTML popup: for pre-rendered layout sections (collapsible section with ref).
var _htmlPopupRegistry = [];
function _registerHtmlPopup(html) {
    var idx = _htmlPopupRegistry.length;
    _htmlPopupRegistry.push(html);
    return idx;
}
function _htmlPopupBtn(idx, title, displayText) {
    var safe = String(title).replace(/\\/g, '\\\\').replace(/'/g, "\\'");
    return '<button type="button" class="cell-popup-btn" onclick="window.openHtmlPopup(' + idx + ',\'' + safe + '\')">' + esc(String(displayText || title)) + _POPUP_ICON + '</button>';
}
function openHtmlPopup(idx, title) {
    var depth = _cellPopupStack.length;
    var overlay = document.createElement('div');
    overlay.className = 'cell-popup-overlay open';
    overlay.style.zIndex = String(9000 + depth * 20);
    overlay.addEventListener('click', function (e) { if (e.target === overlay) window.closeCellPopup(); });
    var offsetPx = depth * 20;
    overlay.innerHTML = '<div class="cell-popup-dialog" style="margin-top:' + offsetPx + 'px;margin-left:' + offsetPx + 'px">'
        + '<div class="cell-popup-header">'
        + '<span class="cell-popup-title">' + esc(title) + '</span>'
        + '<button type="button" class="cell-popup-close" onclick="window.closeCellPopup()">&#x2715;</button>'
        + '</div>'
        + '<div class="cell-popup-body">' + _htmlPopupRegistry[idx] + '</div>'
        + '</div>';
    document.body.appendChild(overlay);
    _cellPopupStack.push(overlay);
}
window.openHtmlPopup = openHtmlPopup;

function renderCollectionSection(label, items, ctx) {
    // If items have a _class with a known layout, render as a layout-driven table
    var _firstItem = items.length > 0 ? unwrapClassWrapper(items[0]) : null;
    var _itemClass = _firstItem && _firstItem._class ? _firstItem._class : null;
    var _itemLayout = (_itemClass && typeof CLASS_LAYOUTS !== 'undefined' && CLASS_LAYOUTS) ? CLASS_LAYOUTS[_itemClass] : null;
    if (_itemLayout) {
        var layoutFields = extractLayoutFields(_itemLayout);
        var tableHtml = '<div style="padding:8px 12px;overflow-x:auto;"><table class="collection-table"><thead><tr>';
        layoutFields.forEach(function (f) {
            tableHtml += '<th>' + esc(f.label) + '</th>';
        });
        tableHtml += '</tr></thead><tbody>';
        items.forEach(function (item) {
            var itemData = unwrapClassWrapper(item);
            tableHtml += '<tr>';
            layoutFields.forEach(function (f) {
                var col = f.ref;
                var cellVal = resolveFieldValue(itemData, col);
                cellVal = unwrapClassWrapper(cellVal);
                if (cellVal === null || cellVal === undefined) { tableHtml += '<td></td>'; return; }
                if (cellVal && typeof cellVal === 'object' && !Array.isArray(cellVal)) {
                    if (cellVal._label !== undefined) {
                        var _lbText = String(cellVal._label || '').trim();
                        tableHtml += '<td>' + (_lbText ? fmtValue(_lbText, col) : '') + '</td>';
                    } else if (cellVal._id !== undefined) {
                        var _refId = String(cellVal._id ?? '').trim();
                        var _refText = String(cellVal._label || cellVal._summary || _refId || '').trim();
                        if (!_refText || _refText === '0' || _refText === '-1') { tableHtml += '<td></td>'; return; }
                        var _refPtDN = pointsToByPath[normalizeSchemaPath(col)];
                        var _refPtHr = _refPtDN ? navHrefByDestName[_refPtDN] : null;
                        var _refLk = (_refPtHr && _refId && _refId !== '0' && _refId !== '-1')
                            ? _refPtHr + '?open=' + encodeURIComponent(_refId) : null;
                        tableHtml += '<td>' + (_refLk ? refLinkBtn(_refLk, _refText) : esc(_refText)) + '</td>';
                    } else if (cellVal._summary) {
                        tableHtml += '<td>' + fmtValue(String(cellVal._summary || '').trim(), col) + '</td>';
                    } else {
                        tableHtml += '<td>' + esc(JSON.stringify(cellVal)) + '</td>';
                    }
                } else if (Array.isArray(cellVal)) {
                    if (cellVal.length === 0) { tableHtml += '<td></td>'; }
                    else { var _pidx = _registerPopup(cellVal); tableHtml += '<td>' + _cellPopupBtn(_pidx, f.label, cellVal.length) + '</td>'; }
                } else {
                    tableHtml += '<td>' + fmtValueWithFormat(cellVal, null, col) + '</td>';
                }
            });
            tableHtml += '</tr>';
        });
        tableHtml += '</tbody></table></div>';
        // In tab (detail) context the section wrapper is provided by the nav - render the table directly.
        // In embedded context, wrap in a collapsible section as usual.
        if (ctx === 'detail') {
            return tableHtml;
        }
        var lhtml = '<details class="detail-section"><summary>'
            + '<span class="summary-title"' + sectionTitleAttr(label) + '>' + esc(formatSectionTitle(label)) + '</span>'
            + '<span class="summary-meta">' + items.length + ' ' + esc(t('elements')) + '</span>'
            + '</summary><div class="section-body">' + tableHtml + '</div></details>';
        return lhtml;
    }

    const collectionId = `c${collectionIdCounter++}`;
    const table = computeCollectionTable(items);
    const pageSize = 25;
    const totalPages = Math.max(1, Math.ceil(table.rows.length / pageSize));

    collectionViewState[collectionId] = {
        columns: table.columns,
        rows: table.rows,
        page: 1,
        pageSize
    };

    const openAttr = ctx === 'detail' ? ' open' : '';
    let html = `<details class="detail-section"${openAttr}><summary><span class="summary-title"${sectionTitleAttr(label)}>${esc(formatSectionTitle(label))}</span><span class="summary-meta">${items.length} ${esc(t('elements'))}</span></summary><div class="section-body">`;

    html += `<div class="collection-toolbar"><span>${items.length} ${esc(t('elements'))}</span><span class="collection-pager">`
        + `<button type="button" data-collection-action="prev" data-collection-id="${collectionId}" disabled>${esc(t('prev'))}</button>`
        + `<span id="collection-page-${collectionId}">1 / ${totalPages}</span>`
        + `<button type="button" data-collection-action="next" data-collection-id="${collectionId}" ${totalPages <= 1 ? 'disabled' : ''}>${esc(t('next'))}</button>`
        + `</span></div>`;

    html += '<div style="padding:8px 12px;overflow-x:auto;">';
    html += `<table class="collection-table"><thead><tr>`;
    if (table.columns.length === 0) {
        html += `<th>${esc(t('collection'))}</th>`;
    } else {
        table.columns.forEach((col) => {
            var colLabel = displayFieldLabel(col);
            var colDest = String(col || '').split('.').pop() || '';
            var colTitle = colDest && colDest !== colLabel ? ` title="${esc(colDest)}"` : '';
            html += `<th data-collection-id="${collectionId}" data-sort-col="${esc(col)}"${colTitle}>${esc(colLabel)}<span class="sort-indicator"></span></th>`;
        });
    }
    html += `</tr></thead><tbody id="collection-body-${collectionId}">${renderCollectionTableBody(collectionViewState[collectionId])}</tbody></table>`;
    html += '</div>';

    html += '</div></details>';
    return html;
}

/**
 * Single dispatch entry point for rendering any value.
 * Routes to the appropriate renderer based on value type and rendering context.
 * @param {string} label - display label / field path
 * @param {*} value - the data value
 * @param {string} ctx - rendering context: 'detail', 'embedded', or 'tabular'
 */
function renderValue(label, value, ctx) {
    if (value === null || value === undefined) return '';
    if (typeof value !== 'object') {
        return renderFieldRow({ key: label, value: value, type: 'primitive' });
    }
    if (Array.isArray(value)) {
        return renderCollectionSection(label, value, ctx);
    }
    // Unwrap class-name wrapper from embedded object export
    value = unwrapClassWrapper(value);
    if (isReferenceOnly(value)) {
        return renderReferenceRow(label, value);
    }
    return renderObjectSection(label, value, ctx);
}

/**
 * Renders a reference-only object ({_id, _label, _preview?}) as a field row
 * with an optional cross-page link and thumbnail.
 */
function renderReferenceRow(label, value) {
    var _refId = String(value._id ?? '').trim();
    var _refText = String(value._label || value._summary || _refId || '').trim();
    if (!_refText || _refText === '0' || _refText === '-1') return '';
    var _refPtDestName = pointsToByPath[normalizeSchemaPath(label)];
    var _refPtHref = _refPtDestName ? navHrefByDestName[_refPtDestName] : null;
    var _refLink = (_refPtHref && _refId && _refId !== '0' && _refId !== '-1')
        ? _refPtHref + '?open=' + encodeURIComponent(_refId)
        : null;
    var _refLabel = displayFieldLabel(label);
    var _refValueHtml = _refLink
        ? refLinkBtn(_refLink, _refText)
        : esc(_refText);
    var _refPreviewHtml = renderPreview(value._preview);
    return '<div class="field-group"><div class="field-row">'
        + '<div class="field-label">' + esc(_refLabel) + '</div>'
        + '<div class="field-value">' + _refValueHtml + '</div>'
        + '</div>' + _refPreviewHtml + '</div>';
}

/**
 * Universal object renderer — replaces renderNodeSection and renderInlineIdEntiteSection.
 * Renders the same object differently depending on context:
 *  - 'detail':   Full field grid, hero _preview, collapsible nested sections, tabs
 *  - 'embedded': Compact <details> with thumbnail _preview + _summary header, fields inside
 *  - 'tabular':  Summary label as link + mini thumbnail only; no fields, no recursion
 */
function renderObjectSection(label, value, ctx) {
    ctx = ctx || 'detail';
    if (!value || typeof value !== 'object' || Array.isArray(value)) return '';

    // Extract reserved properties
    var objId = value._id ? String(value._id).trim() : null;
    var objSummary = value._summary ? String(value._summary).trim() : null;
    var objPreview = value._preview || null;
    var objClass = value._class || null;

    // Resolve cross-page link
    var _ptDestName = pointsToByPath[normalizeSchemaPath(label)];
    var _ptHref = _ptDestName ? navHrefByDestName[_ptDestName] : null;
    if (objId === '0' || objId === '-1' || objId === '') objId = null;
    var linkHref = (_ptHref && objId) ? _ptHref + '?open=' + encodeURIComponent(objId) : null;

    // ── Tabular context: summary + mini thumbnail only ──
    if (ctx === 'tabular') {
        var tabText = objSummary || objId || '';
        if (!tabText) return '';
        var tabHtml = linkHref ? refLinkBtn(linkHref, tabText) : esc(tabText);
        tabHtml += ' ' + renderPreview(objPreview, { size: 'thumb-sm' });
        return tabHtml;
    }

    // Classify entries
    var entries = getObjectEntries(value);
    var primitiveEntries = sortPrimitiveEntries(entries.filter(function (e) { return e.type === 'primitive'; }));
    var referenceEntries = entries.filter(function (e) { return e.type === 'reference'; });
    var objectEntries = entries.filter(function (e) { return e.type === 'object'; }).sort(function (a, b) { return String(a.key || '').localeCompare(String(b.key || '')); });
    var collectionEntries = entries.filter(function (e) { return e.type === 'collection'; }).sort(function (a, b) { return String(a.key || '').localeCompare(String(b.key || '')); });

    // If effectively empty (only a zero-valued ID), skip
    if (primitiveEntries.length === 0 && referenceEntries.length === 0 && objectEntries.length === 0 && collectionEntries.length === 0) {
        if (!objSummary && !objId) return '';
    }
    if (primitiveEntries.length <= 1 && referenceEntries.length === 0 && objectEntries.length === 0 && collectionEntries.length === 0 && !objSummary) {
        var single = primitiveEntries[0];
        if (single) {
            var lo = String(single.key || '').toLowerCase().split('.').pop() || '';
            var valStr = String(single.value ?? '').trim();
            if ((lo === 'mid' || lo === 'id' || lo.startsWith('id')) && (!valStr || valStr === '0' || valStr === '-1')) {
                return '';
            }
        }
    }

    // ── Embedded context: compact <details> with thumbnail header ──
    if (ctx === 'embedded') {
        // Header: label + summary + optional link + thumbnail
        var embHeader = '';
        var embLabel = displayFieldLabel(label);
        var embSummaryHtml = '';
        if (objSummary) {
            embSummaryHtml = linkHref ? refLinkBtn(linkHref, objSummary) : esc(objSummary);
        } else if (objId && linkHref) {
            embSummaryHtml = refLinkBtn(linkHref, objId);
        }
        var embPreviewHtml = renderPreview(objPreview, { size: 'thumb-md' });

        embHeader = '<div class="field-group-subtitle' + (linkHref ? ' ref-subtitle' : '') + '"' + sectionTitleAttr(label) + '>'
            + '<span>' + esc(embLabel) + '</span>';
        if (embSummaryHtml) embHeader += ' <span style="font-weight:normal">' + embSummaryHtml + '</span>';
        if (embPreviewHtml) embHeader += embPreviewHtml;
        if (linkHref) embHeader += '<a class="ref-link" href="' + esc(linkHref) + '" title="' + esc(t('openLinkedRecord')) + '">' + REF_ARROW_SVG + '</a>';
        embHeader += '</div>';

        var embHtml = '<div class="field-group">' + embHeader;
        // If this object's class has a custom layout, use it
        var _classLayout = (value._class && typeof CLASS_LAYOUTS !== 'undefined' && CLASS_LAYOUTS) ? CLASS_LAYOUTS[value._class] : null;
        if (_classLayout) {
            embHtml += renderLayoutChildren(value, _classLayout);
        } else {
            if (primitiveEntries.length >= 2) {
                embHtml += '<div class="field-columns-2">';
                primitiveEntries.forEach(function (e) { embHtml += renderFieldRow(e.key, e.value); });
                embHtml += '</div>';
            } else {
                primitiveEntries.forEach(function (e) { embHtml += renderFieldRow(e.key, e.value); });
            }
            referenceEntries.forEach(function (e) { embHtml += renderReferenceRow(e.key, e.value); });
            objectEntries.forEach(function (e) { embHtml += renderValue(e.key, e.value, 'embedded'); });
            collectionEntries.forEach(function (e) { embHtml += renderValue(e.key, e.value, 'embedded'); });
        }
        embHtml += '</div>';
        return embHtml;
    }

    // ── Detail context: full collapsible section ──
    var openAttr = ' open';
    var html = '<details class="detail-section"' + openAttr + '><summary><span class="summary-title"' + sectionTitleAttr(label) + '>' + esc(formatSectionTitle(label)) + '</span>'
        + '<span class="summary-meta">' + ((objectEntries.length + referenceEntries.length + collectionEntries.length) > 0 ? esc(t('object')) : '') + '</span></summary><div class="section-body">';

    // Hero _preview at top for detail context
    html += renderPreview(objPreview);

    html += renderPrimitiveGroup(primitiveEntries);

    // Render references inline
    referenceEntries.forEach(function (entry) {
        html += renderReferenceRow(entry.key, entry.value);
    });

    // Build inner section list; use tabbed view when more than one sub-section
    var innerSectionEntries =
        objectEntries.map(function (entry) {
            return {
                label: formatSectionTitle(entry.key),
                content: renderValue(entry.key, entry.value, 'embedded')
            };
        }).concat(
            collectionEntries.map(function (entry) {
                return {
                    label: formatSectionTitle(entry.key),
                    content: renderCollectionSection(entry.key, entry.value, 'embedded')
                };
            })
        ).filter(function (sec) { return sec.content.trim() !== ''; });

    if (innerSectionEntries.length > 1) {
        var autoTabId = 'at' + (collectionIdCounter++);
        html += '<div class="layout-tabs"><div class="tab-bar" data-tabgroup="' + autoTabId + '">';
        innerSectionEntries.forEach(function (sec, idx) {
            html += '<button type="button" data-tab-target="' + autoTabId + '-' + idx + '"'
                + (idx === 0 ? ' class="active"' : '') + '>' + esc(sec.label) + '</button>';
        });
        html += '</div>';
        innerSectionEntries.forEach(function (sec, idx) {
            html += '<div class="tab-panel' + (idx === 0 ? ' active' : '') + '" data-tab-id="' + autoTabId + '-' + idx + '">'
                + sec.content + '</div>';
        });
        html += '</div>';
    } else {
        innerSectionEntries.forEach(function (sec) { html += sec.content; });
    }

    html += '</div></details>';
    return html;
}

