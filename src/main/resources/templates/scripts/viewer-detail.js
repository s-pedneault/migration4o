    function openDetailOverlay() {
        detailOverlay.classList.add('open');
        detailOverlay.setAttribute('aria-hidden', 'false');
    }

    function closeDetailOverlay() {
        detailOverlay.classList.remove('open');
        detailOverlay.setAttribute('aria-hidden', 'true');
    }

    function selectRecord(rec) {
        selectedRecordKey = rec.key;
        renderResults();
        renderDetail(rec);
        updateDetailNav();
        openDetailOverlay();
    }

    function findCurrentRecordIndex() {
        if (!selectedRecordKey) return -1;
        return filteredRecords.findIndex((r) => r.key === selectedRecordKey);
    }

    function updateDetailNav() {
        const idx = findCurrentRecordIndex();
        const total = filteredRecords.length;
        if (detailNavPos) detailNavPos.textContent = (idx >= 0 ? (idx + 1) : '?') + ' / ' + total;
        if (detailPrevBtn) detailPrevBtn.disabled = idx <= 0;
        if (detailNextBtn) detailNextBtn.disabled = idx < 0 || idx >= total - 1;
    }

    function navigateDetail(delta) {
        const idx = findCurrentRecordIndex();
        if (idx < 0) return;
        const newIdx = idx + delta;
        if (newIdx < 0 || newIdx >= filteredRecords.length) return;
        selectRecord(filteredRecords[newIdx]);
    }

    function renderDetail(rec) {
        if (!rec) {
            detailContainer.innerHTML = `<div class="results-empty"><div class="icon">\ud83d\udccb</div><p>${esc(t('detail'))}</p></div>`;
            return;
        }

        collectionViewState = {};
        collectionIdCounter = 1;

        let html = '';

        if (typeof DETAIL_LAYOUT !== 'undefined' && DETAIL_LAYOUT) {
            // Layout-driven mode
            var _headerTitle = rec.summary || (typeof entityName !== 'undefined' && entityName ? entityName : rec.entity);
            html += `<div class="detail-header"><h1 class="detail-header-title">${esc(_headerTitle)}</h1><div class="detail-subtitle">${esc(rec.entity)} \u2022 ID: ${esc(rec.id || '\u2014')}</div></div>`;
            html += '<div class="detail-scroll">';
            var _heroPreview = renderPreview(rec.preview, { title: rec.summary || rec.entity });
            if (_heroPreview) html += _heroPreview;
            html += renderLayoutDetail(rec.raw, DETAIL_LAYOUT);
            html += renderBackRefSection(rec);
            html += '</div>';
        } else {
            // Auto-discovery mode: hero header + key info + smart sections

            // 1) Build hero header with promoted subtitle fields
            const subtitleFields = [];
            if (rec.fields) {
                const subtitleCandidates = ['nom', 'name', 'prenom', 'titre', 'title', 'description', 'libelle', 'label', 'raisonSociale'];
                subtitleCandidates.forEach((c) => {
                    if (subtitleFields.length >= 3) return;
                    const entry = Object.entries(rec.fields).find(([k]) => {
                        const lo = k.toLowerCase();
                        return lo === c || lo.endsWith('.' + c);
                    });
                    if (entry && entry[1] && String(entry[1]).trim().length > 0) {
                        const label = displayFieldLabel(entry[0]);
                        const val = String(entry[1]).trim();
                        if (val !== rec.summary && val !== rec.id) {
                            subtitleFields.push({ label, value: val });
                        }
                    }
                });
            }

            html += '<div class="detail-hero">';
            html += '<div class="detail-hero-left">';
            html += '<div class="detail-hero-entity">' + esc(typeof entityName !== 'undefined' && entityName ? entityName : rec.entity) + '</div>';
            html += '<div class="detail-hero-title">' + esc(rec.summary || rec.entity) + '</div>';
            if (subtitleFields.length > 0) {
                html += '<div class="detail-hero-subtitle">';
                html += subtitleFields.map((f) => '<strong>' + esc(f.label) + ':</strong> ' + esc(f.value)).join(' &nbsp;\u2022&nbsp; ');
                html += '</div>';
            }
            html += '</div>';
            html += '<div class="detail-hero-right">';
            if (rec.id) html += '<div class="detail-hero-meta"><strong>ID</strong>&nbsp;<span class="badge badge-id">' + esc(rec.id) + '</span></div>';
            html += '<div class="detail-hero-meta">' + esc(rec.entity) + '</div>';
            html += renderPreview(rec.preview, { title: rec.summary || rec.entity });
            html += '</div>';
            html += '</div>';

            // 2) Extract key info fields from raw data
            html += '<div class="detail-scroll">';
            const rawData = rec.raw;
            const keyInfoEntries = [];
            const promotedKeys = new Set();

            if (rawData && typeof rawData === 'object') {
                const allEntries = getObjectEntries(rawData);
                allEntries.forEach((entry) => {
                    if (entry.type !== 'primitive') return;
                    const lo = String(entry.key || '').toLowerCase();
                    const lastPart = lo.split('.').pop() || lo;
                    if (lastPart === '_summary' || lastPart === '_preview') return;
                    const isKeyField = KEY_INFO_PATTERNS.some((p) => lastPart === p || lastPart.includes(p));
                    const isDateField = lastPart.includes('date') || /^\d{4}-\d{2}-\d{2}/.test(String(entry.value ?? ''));
                    if (isKeyField || isDateField) {
                        keyInfoEntries.push(entry);
                        promotedKeys.add(entry.key);
                    }
                });
            }

            if (keyInfoEntries.length > 0) {
                const sortedKeyInfo = sortPrimitiveEntries(keyInfoEntries);
                html += '<div class="detail-key-info">';
                html += '<div class="detail-key-info-title">' + esc(t('keyInfo')) + '</div>';
                html += renderPrimitiveGroup(sortedKeyInfo);
                html += '</div>';
            }

            // 3) Render remaining data (with promoted keys removed from root primitives)
            if (rawData && typeof rawData === 'object' && promotedKeys.size > 0) {
                const entries = getObjectEntries(rawData);
                const filteredPrimitives = sortPrimitiveEntries(entries.filter((e) => {
                    if (e.type !== 'primitive') return false;
                    if (promotedKeys.has(e.key)) return false;
                    const lastPart = String(e.key || '').toLowerCase().split('.').pop() || '';
                    if (lastPart === 'id') return false;
                    return true;
                }));
                const referenceEntries = entries.filter((e) => e.type === 'reference');
                const objectEntries = entries.filter((e) => e.type === 'object').sort((a, b) => String(a.key || '').localeCompare(String(b.key || '')));
                const collectionEntries = entries.filter((e) => e.type === 'collection').sort((a, b) => String(a.key || '').localeCompare(String(b.key || '')));

                if (filteredPrimitives.length > 0 || referenceEntries.length > 0) {
                    let sectionTitle = t('details');
                    html += `<details class="detail-section" open><summary><span class="summary-title">${esc(sectionTitle)}</span></summary><div class="section-body">`;
                    html += renderPrimitiveGroup(filteredPrimitives);
                    referenceEntries.forEach((entry) => {
                        html += renderReferenceRow(entry.key, entry.value);
                    });
                    html += '</div></details>';
                }

                const topInnerSections =
                    objectEntries.map((entry) => ({
                        label: formatSectionTitle(entry.key),
                        content: renderValue(entry.key, entry.value, 'embedded')
                    })).concat(
                        collectionEntries.map((entry) => ({
                            label: formatSectionTitle(entry.key),
                            content: renderCollectionSection(entry.key, entry.value, 'detail')
                        }))
                    ).filter((sec) => sec.content.trim() !== '');

                if (topInnerSections.length > 1) {
                    const autoTabId = 'at' + (collectionIdCounter++);
                    html += '<div class="layout-tabs"><div class="tab-bar" data-tabgroup="' + autoTabId + '">';
                    topInnerSections.forEach((sec, idx) => {
                        html += '<button type="button" data-tab-target="' + autoTabId + '-' + idx + '"'
                            + (idx === 0 ? ' class="active"' : '') + '>' + esc(sec.label) + '</button>';
                    });
                    html += '</div>';
                    topInnerSections.forEach((sec, idx) => {
                        html += '<div class="tab-panel' + (idx === 0 ? ' active' : '') + '" data-tab-id="' + autoTabId + '-' + idx + '">'
                            + sec.content + '</div>';
                    });
                    html += '</div>';
                } else {
                    topInnerSections.forEach((sec) => { html += sec.content; });
                }
            } else {
                html += renderObjectSection(rec.entity, rawData, 'detail');
            }

            html += renderBackRefSection(rec);
            html += '</div>';
        }

        detailContainer.innerHTML = html;
        bindTabEvents();
    }

    /* ── Back-reference section ──────────────────────────────────── */

    /**
     * Renders a "Références" section at the bottom of the detail view, showing
     * all records from other entity types that reference the current record.
     * Uses the global CROSS_REFS index written by the Java export engine.
     *
     * Grouping rules:
     *  - One group per source entity type.
     *  - If exactly 1 entity type with 1 record → compact <details> section.
     *  - Otherwise → tabbed section, one tab per entity type.
     */
    function renderBackRefSection(rec) {
        if (!window.CROSS_REFS || !rec || !rec.id) return '';
        var refs = window.CROSS_REFS[rec.id];
        if (!refs || refs.length === 0) return '';

        // Group by source entity destination name
        var groups = {};
        var groupOrder = [];
        refs.forEach(function (ref) {
            if (!ref.entity) return;
            if (!groups[ref.entity]) {
                groups[ref.entity] = { label: ref.label || ref.entity, href: ref.href || null, entries: [] };
                groupOrder.push(ref.entity);
            }
            groups[ref.entity].entries.push(ref);
        });
        if (groupOrder.length === 0) return '';

        // Single entity type with exactly one record — compact <details>
        if (groupOrder.length === 1 && groups[groupOrder[0]].entries.length === 1) {
            var sg = groups[groupOrder[0]];
            var se = sg.entries[0];
            return '<details class="detail-section back-ref-section" open><summary>'
                + '<span class="summary-title">' + esc(t('backRefs')) + '</span>'
                + '<span class="summary-meta back-ref-entity-badge">' + esc(sg.label) + '</span>'
                + '</summary><div class="section-body back-ref-list">'
                + renderBackRefRow(se, sg.href)
                + '</div></details>';
        }

        // Multiple groups or multiple records — tabbed section
        var tabGroupId = 'br' + (collectionIdCounter++);
        var html = '<div class="back-ref-section">';
        html += '<div class="back-ref-header">' + esc(t('backRefs')) + '</div>';
        html += '<div class="layout-tabs"><div class="tab-bar" data-tabgroup="' + tabGroupId + '">';
        groupOrder.forEach(function (key, idx) {
            var g = groups[key];
            html += '<button type="button" data-tab-target="' + tabGroupId + '-' + idx + '"'
                + (idx === 0 ? ' class="active"' : '') + '>'
                + esc(g.label)
                + ' <span class="tab-count">(' + g.entries.length + ')</span>'
                + '</button>';
        });
        html += '</div>';
        groupOrder.forEach(function (key, idx) {
            var g = groups[key];
            html += '<div class="tab-panel' + (idx === 0 ? ' active' : '') + '" data-tab-id="' + tabGroupId + '-' + idx + '">';
            html += '<div class="back-ref-list">';
            g.entries.forEach(function (entry) { html += renderBackRefRow(entry, g.href); });
            if (g.entries.length >= 25) {
                html += '<div class="back-ref-capped">' + esc(t('backRefsCapped')) + '</div>';
            }
            html += '</div></div>';
        });
        html += '</div></div>';
        return html;
    }

    /**
     * Renders one row in a back-reference list: a clickable ID link and a
     * summary.
     */
    function renderBackRefRow(entry, groupHref) {
        var href = entry.href || groupHref;
        var linkUrl = href ? href + '?open=' + encodeURIComponent(entry.id) : null;
        var idHtml = linkUrl
            ? refLinkBtn(linkUrl, entry.id)
            : '<span>' + esc(entry.id) + '</span>';
        var summaryHtml = (entry.summary && entry.summary.trim())
            ? esc(entry.summary)
            : '<span class="back-ref-no-summary">—</span>';
        return '<div class="back-ref-row">'
            + '<div class="back-ref-id">' + idHtml + '</div>'
            + '<div class="back-ref-summary">' + summaryHtml + '</div>'
            + '</div>';
    }
