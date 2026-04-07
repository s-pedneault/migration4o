
/* ── Layout-driven detail renderer ──────────────────────────── */

/**
 * Unwraps a single-key class-name wrapper produced by the export engine
 * when FieldExporter wraps an embedded object in a field-name structure
 * and ObjectExporter adds a class-name structure inside it.
 * E.g. {"Fichier": {_id: "456", nom: "photo.jpg"}} → {_id: "456", nom: "photo.jpg"}
 */
function unwrapClassWrapper(obj) {
    if (obj == null || typeof obj !== 'object' || Array.isArray(obj)) return obj;
    var nonUKeys = Object.keys(obj).filter(function (k) { return k.charAt(0) !== '_'; });
    if (nonUKeys.length === 1 && typeof obj[nonUKeys[0]] === 'object' && obj[nonUKeys[0]] !== null && !Array.isArray(obj[nonUKeys[0]])) {
        return obj[nonUKeys[0]];
    }
    return obj;
}

function resolveFieldValue(data, ref) {
    if (!data || !ref) return null;
    return ref.split('.').reduce(function (obj, key) {
        if (obj == null) return null;
        if (typeof obj !== 'object' || obj === null) return null;
        // Direct lookup
        if (obj[key] !== undefined) return obj[key];
        // Transparent class-name wrapper navigation: if the object has
        // exactly one non-_ key whose value is an object, look inside
        // that inner object for the requested key.
        var wk = Object.keys(obj).filter(function (k) { return k.charAt(0) !== '_'; });
        if (wk.length === 1 && typeof obj[wk[0]] === 'object' && obj[wk[0]] !== null) {
            var inner = obj[wk[0]];
            if (!Array.isArray(inner) && inner[key] !== undefined) return inner[key];
        }
        return null;
    }, data);
}

function formatDatePattern(date, pattern) {
    if (!(date instanceof Date) || isNaN(date.getTime())) return '';
    var months = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
    var monthsShort = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    var pad = function (n) { return n < 10 ? '0' + n : '' + n; };
    return pattern
        .replace('yyyy', date.getFullYear())
        .replace('MM', pad(date.getMonth() + 1))
        .replace('dd', pad(date.getDate()))
        .replace('HH', pad(date.getHours()))
        .replace('mm', pad(date.getMinutes()))
        .replace('ss', pad(date.getSeconds()))
        .replace('MMMM', months[date.getMonth()])
        .replace('MMM', monthsShort[date.getMonth()]);
}

function fmtValueWithFormat(value, formatSpec, key) {
    if (value === null || value === undefined) return '<span style="color:var(--c-text-muted)">\u2014</span>';
    if (!formatSpec) return fmtValue(value, key);

    var colonIdx = formatSpec.indexOf(':');
    if (colonIdx < 0) return fmtValue(value, key);
    var fmtType = formatSpec.substring(0, colonIdx);
    var fmtSpec = formatSpec.substring(colonIdx + 1);

    if (fmtType === 'date') {
        try { return esc(formatDatePattern(new Date(value), fmtSpec)); } catch (e) { return esc(String(value)); }
    }
    if (fmtType === 'longdate') {
        try { return esc(formatDatePattern(new Date(Number(value)), fmtSpec)); } catch (e) { return esc(String(value)); }
    }
    if (fmtType === 'bool') {
        var parts = fmtSpec.split(',');
        var tv = parts[0] || 'True', fv = parts[1] || 'False';
        var bv = String(value).toLowerCase();
        var boolHtml = renderBoolValue(bv, tv, fv);
        if (boolHtml) return boolHtml;
        return esc(String(value));
    }
    if (fmtType === 'num') {
        var num = Number(value);
        if (isNaN(num)) return esc(String(value));
        // Extract suffix text after pattern: "num:#,##0.0 Km" -> suffix=" Km"
        var match = fmtSpec.match(/^([#0,.]+)\s*(.*)$/);
        if (!match) return '<span class="badge badge-number">' + esc(String(num)) + '</span>';
        var decMatch = match[1].match(/\.([0#]+)$/);
        var decimals = decMatch ? decMatch[1].length : 0;
        var formatted = num.toLocaleString(undefined, { minimumFractionDigits: decimals, maximumFractionDigits: decimals });
        return '<span class="badge badge-number">' + esc(formatted + (match[2] ? ' ' + match[2] : '')) + '</span>';
    }
    return fmtValue(value, key);
}

function renderLayoutDetail(data, layout) {
    var html = '';
    for (var i = 0; i < layout.length; i++) {
        html += renderLayoutNode(data, layout[i], 'detail');
    }
    return html;
}

function renderLayoutNode(data, node, ctx) {
    var p = node.props || {};
    var children = node.children || [];
    var styleCls = p.style ? ' field-' + p.style : '';
    var inlineStyle = '';
    if (p.color) inlineStyle += 'color:' + esc(p.color) + ';';
    if (p.hilite) inlineStyle += 'background-color:' + esc(p.hilite) + ';';
    var styleAttr = inlineStyle ? ' style="' + inlineStyle + '"' : '';

    switch (node.type) {
        case 'section': {
            var collapsible = p.collapsible === 'true';
            var titleInline = '';
            var tc = p.color || p.titleColor;
            if (tc) titleInline += 'color:' + esc(tc) + ';';
            if (p.hilite) titleInline += 'background-color:' + esc(p.hilite) + ';';
            if (p.style) titleInline += layoutStyleFont(p.style);
            var titleAttr = titleInline ? ' style="' + titleInline + '"' : '';
            var _sBody = renderLayoutChildren(data, children, ctx);
            if (p.ref) {
                var _sData = unwrapClassWrapper(resolveFieldValue(data, p.ref));
                if (_sData && typeof _sData === 'object' && !Array.isArray(_sData)) {
                    _sBody += renderPreview(_sData._preview);
                }
            }
            // Nothing to show — suppress the entire section wrapper
            if (!_sBody.trim()) return '';
            // Nested embedded section (collapsible + has ref): render as
            // a field-row with the title on the left and a pre-collapsed
            // details block of children on the right, so it sits inline
            // with sibling field rows instead of a big blue header.
            if (collapsible && p.ref) {
                var _nestedLabel = p.title || displayFieldLabel(p.ref);
                var _nestedSummary = (_sData && _sData._summary) ? String(_sData._summary) : null;
                var _hpIdx = _registerHtmlPopup(_sBody);
                return '<div class="field-row"><div class="field-label">' + esc(_nestedLabel)
                    + '</div><div class="field-value">' + _htmlPopupBtn(_hpIdx, _nestedLabel, _nestedSummary || _nestedLabel) + '</div></div>';
            }
            if (collapsible && ctx !== 'detail') {
                return '<details class="detail-section" open><summary><span class="layout-section-title"' + titleAttr + '>'
                    + esc(p.title || '') + '</span></summary><div class="section-body">'
                    + _sBody + '</div></details>';
            }
            return '<div class="detail-section"><div class="section-body">'
                + (p.title ? '<div class="layout-section-title" style="padding:8px 12px;' + titleInline + '">' + esc(p.title) + '</div>' : '')
                + _sBody + '</div></div>';
        }
        case 'columns': {
            var sizes = (p.sizes || '').split(',').map(function (s) { return s.trim() + '%'; });
            var gridCols = sizes.join(' ');
            return '<div class="layout-columns" style="grid-template-columns:' + gridCols + '">'
                + renderLayoutChildren(data, children, ctx) + '</div>';
        }
        case 'column':
            return '<div class="layout-column">' + renderLayoutChildren(data, children, ctx) + '</div>';

        case 'field': {
            var val = resolveFieldValue(data, p.ref);
            if (val === null || val === undefined) return '';
            var scalarVal = val;
            if (scalarVal === null || scalarVal === undefined || String(scalarVal).trim() === '') return '';
            var label = p.label || displayFieldLabel(p.ref);
            var fieldDestName = String(p.ref || '').split('.').pop() || '';
            var fieldTitleAttr = fieldDestName && fieldDestName !== label ? ' title="' + esc(fieldDestName) + '"' : '';
            // IDEntite object resolution: extract summary text and optional deep-link
            if (typeof scalarVal === 'object' && scalarVal !== null && !Array.isArray(scalarVal)) {
                // Unwrap class-name wrapper from embedded object export
                scalarVal = unwrapClassWrapper(scalarVal);
                // Reference with _id: render as linked label or summary
                if (scalarVal._id !== undefined) {
                    var _refId = String(scalarVal._id ?? '').trim();
                    var _refText = String(scalarVal._label || scalarVal._summary || _refId || '').trim();
                    if (!_refText || _refText === '0' || _refText === '-1') return '';
                    var _refPtDestName = pointsToByPath[normalizeSchemaPath(p.ref)];
                    var _refPtHref = _refPtDestName ? navHrefByDestName[_refPtDestName] : null;
                    var _refLink = (_refPtHref && _refId && _refId !== '0' && _refId !== '-1')
                        ? _refPtHref + '?open=' + encodeURIComponent(_refId)
                        : null;
                    var _refValueHtml = _refLink ? refLinkBtn(_refLink, _refText) : esc(_refText);
                    return '<div class="field-row' + styleCls + '"' + styleAttr + '><div class="field-label"' + fieldTitleAttr + '>' + esc(label)
                        + '</div><div class="field-value">' + _refValueHtml + '</div></div>';
                }
                // Complex embedded object: open in a popup.
                var _embIdx = _registerPopup([scalarVal]);
                return '<div class="field-row' + styleCls + '"' + styleAttr + '><div class="field-label"' + fieldTitleAttr + '>' + esc(label)
                    + '</div><div class="field-value">' + _cellPopupBtn(_embIdx, label, '\u25a6') + '</div></div>';
            }
            if (Array.isArray(scalarVal)) {
                return renderValue(label, scalarVal, 'embedded');
            }
            var formatted = fmtValueWithFormat(scalarVal, p.format, p.ref);
            return '<div class="field-row' + styleCls + '"' + styleAttr + '><div class="field-label"' + fieldTitleAttr + '>' + esc(label)
                + '</div><div class="field-value">' + formatted + '</div></div>';
        }
        case 'divider': {
            var divStyle = '';
            if (p.color) divStyle += 'border-top-color:' + esc(p.color) + ';';
            if (p.style === 'h1') divStyle += 'border-top-width:4px;margin:20px 0;';
            else if (p.style === 'h2') divStyle += 'border-top-width:3px;margin:16px 0;';
            else if (p.style === 'small') divStyle += 'border-top-width:1px;margin:8px 0;';
            return '<hr class="layout-divider"' + (divStyle ? ' style="' + divStyle + '"' : '') + '/>';
        }
        case 'table': {
            var items = resolveFieldValue(data, p.ref);
            if (!Array.isArray(items) || items.length === 0) return '';
            // If items have a custom class layout, delegate to layout-driven card rendering
            var _tblFirst = unwrapClassWrapper(items[0]);
            var _tblClass = _tblFirst && _tblFirst._class ? _tblFirst._class : null;
            var _tblLayout = (_tblClass && typeof CLASS_LAYOUTS !== 'undefined' && CLASS_LAYOUTS) ? CLASS_LAYOUTS[_tblClass] : null;
            if (_tblLayout) return renderCollectionSection(p.ref, items, ctx || 'embedded');

            var bare = p.bare === 'true' || ctx === 'detail';
            var cols = (p.columns || '').split(',').map(function (s) { return s.trim(); }).filter(Boolean);
            var widths = (p.widths || '').split(',').map(function (s) { return s.trim(); });
            var colTitles = (p.columnTitles || '').split(',').map(function (s) { return s.trim(); });
            if (cols.length === 0) {
                var first = items[0];
                if (first && typeof first === 'object') {
                    Object.keys(first).forEach(function (k) {
                        if (k.charAt(0) === '_') return;
                        var sample = unwrapClassWrapper(first[k]);
                        // Flatten embedded objects into dotted sub-columns
                        if (sample && typeof sample === 'object' && !Array.isArray(sample)
                            && !sample._label
                            && !sample._summary) {
                            Object.keys(sample).forEach(function (sk) {
                                if (sk.charAt(0) !== '_') cols.push(k + '.' + sk);
                            });
                        } else {
                            cols.push(k);
                        }
                    });
                }
            }
            var headerStyle = '';
            if (p.color) headerStyle += 'color:' + esc(p.color) + ';';
            if (p.hilite) headerStyle += 'background-color:' + esc(p.hilite) + ';';
            var thtml = bare
                ? '<div style="padding:8px 12px;overflow-x:auto;"><span class="summary-meta" style="display:block;text-align:right;margin-bottom:4px;">' + items.length + ' ' + esc(t('elements')) + '</span><table class="collection-table"><thead><tr>'
                : '<details class="detail-section" open><summary' + (headerStyle ? ' style="' + headerStyle + '"' : '') + '><span class="summary-title">'
                + esc(displayFieldLabel(p.ref)) + '</span><span class="summary-meta">' + items.length + ' ' + esc(t('elements'))
                + '</span></summary><div class="section-body"><div style="padding:8px 12px;overflow-x:auto;"><table class="collection-table"><thead><tr>';
            cols.forEach(function (col, idx) {
                var w = widths[idx] ? ' style="width:' + esc(widths[idx]) + '%"' : '';
                var thText = (colTitles[idx] && colTitles[idx].length > 0) ? colTitles[idx] : displayFieldLabel(col);
                thtml += '<th' + w + '>' + esc(thText) + '</th>';
            });
            thtml += '</tr></thead><tbody>';
            items.forEach(function (item) {
                thtml += '<tr>';
                cols.forEach(function (col) {
                    var cellVal = col.indexOf('.') >= 0 ? resolveFieldValue(item, col)
                        : (item && typeof item === 'object') ? item[col] : item;
                    // Unwrap class-name wrapper from embedded object export
                    cellVal = unwrapClassWrapper(cellVal);
                    // Object cell: labeled scalars render inline; all other objects open as popup.
                    if (cellVal && typeof cellVal === 'object' && !Array.isArray(cellVal)) {
                        if (cellVal._label !== undefined) {
                            cellVal = cellVal._label; // fall through to scalar render below
                        } else {
                            var _oidx = _registerPopup([cellVal]);
                            thtml += '<td>' + _cellPopupBtn(_oidx, displayFieldLabel(col), '\u25a6') + '</td>';
                            return;
                        }
                    }
                    if (Array.isArray(cellVal)) {
                        if (cellVal.length === 0) { thtml += '<td></td>'; }
                        else { var _pidx = _registerPopup(cellVal); thtml += '<td>' + _cellPopupBtn(_pidx, displayFieldLabel(col), cellVal.length) + '</td>'; }
                    } else {
                        thtml += '<td>' + fmtValue(cellVal, col) + '</td>';
                    }
                });
                thtml += '</tr>';
            });
            thtml += '</tbody></table>' + (bare ? '</div>' : '</div></div></details>');
            return thtml;
        }
        case 'tabs': {
            var tabId = 'lt' + (collectionIdCounter++);
            var html = '<div class="layout-tabs">';
            if (p.title) html += '<div class="layout-tabs-header">' + esc(p.title) + '</div>';
            html += '<div class="tab-bar" data-tabgroup="' + tabId + '">';
            children.forEach(function (child, idx) {
                var cp = child.props || {};
                html += '<button type="button" data-tab-target="' + tabId + '-' + idx + '"'
                    + (idx === 0 ? ' class="active"' : '') + '>' + esc(cp.title || 'Tab ' + (idx + 1)) + '</button>';
            });
            html += '</div>';
            children.forEach(function (child, idx) {
                var panelContent = renderLayoutChildren(data, child.children || [], ctx);
                if (!panelContent.trim()) panelContent = '<div class="tab-empty">' + esc(t('emptyTab')) + '</div>';
                html += '<div class="tab-panel' + (idx === 0 ? ' active' : '') + '" data-tab-id="' + tabId + '-' + idx + '">' + panelContent + '</div>';
            });
            html += '</div>';
            return html;
        }
        case 'tab':
            return renderLayoutChildren(data, children, ctx);

        default:
            return '';
    }
}

function layoutStyleFont(style) {
    switch (style) {
        case 'h1': return 'font-size:1.5rem;font-weight:700;';
        case 'h2': return 'font-size:1.25rem;font-weight:600;';
        case 'h3': return 'font-size:1.1rem;font-weight:600;';
        case 'h4': return 'font-size:0.95rem;font-weight:600;';
        case 'small': return 'font-size:0.75rem;';
        case 'caption': return 'font-size:0.75rem;font-style:italic;';
        default: return '';
    }
}

function renderLayoutChildren(data, children, ctx) {
    var html = '';
    for (var i = 0; i < children.length; i++) {
        html += renderLayoutNode(data, children[i], ctx);
    }
    return html;
}

function bindTabEvents() {
    document.querySelectorAll('.tab-bar button[data-tab-target]').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var target = btn.getAttribute('data-tab-target');
            var bar = btn.parentElement;
            bar.querySelectorAll('button').forEach(function (b) { b.classList.remove('active'); });
            btn.classList.add('active');
            var container = bar.parentElement;
            container.querySelectorAll('.tab-panel').forEach(function (p) { p.classList.remove('active'); });
            var panel = container.querySelector('[data-tab-id="' + target + '"]');
            if (panel) panel.classList.add('active');
        });
    });
}

