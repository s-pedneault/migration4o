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
            if (n.type === 'field' && p.ref) {
                result.push({ ref: p.ref, label: p.label || displayFieldLabel(p.ref) });
            } else if (n.children && n.children.length > 0) {
                extractLayoutFields(n.children).forEach(function (f) { result.push(f); });
            }
        });
        return result;
    }

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
                        tableHtml += '<td>' + esc(String(cellVal.length) + ' ' + t('elements')) + '</td>';
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

