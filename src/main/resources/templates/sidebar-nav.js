// ─── Navigation sidebar ───────────────────────────────────────────────────
(function () {
    var navSidebar = document.getElementById('navSidebar');
    var navToggleBtn = document.getElementById('navToggleBtn');
    var navSidebarScroll = document.getElementById('navSidebarScroll');
    if (!navSidebar || !navToggleBtn || !navSidebarScroll) return;

    // ── SVG icon helpers ──────────────────────────────────────────────────
    var SVG_NS = 'http://www.w3.org/2000/svg';

    function svgIcon(paths, size) {
        size = size || 18;
        var svg = document.createElementNS(SVG_NS, 'svg');
        svg.setAttribute('width', size);
        svg.setAttribute('height', size);
        svg.setAttribute('viewBox', '0 0 24 24');
        svg.setAttribute('fill', 'none');
        svg.setAttribute('stroke', 'currentColor');
        svg.setAttribute('stroke-width', '1.6');
        svg.setAttribute('stroke-linecap', 'round');
        svg.setAttribute('stroke-linejoin', 'round');
        paths.forEach(function (d) {
            var p = document.createElementNS(SVG_NS, 'path');
            p.setAttribute('d', d);
            svg.appendChild(p);
        });
        return svg;
    }

    // Icon definitions (Lucide-style)
    var ICONS = {
        // Folder closed
        folder: function () {
            return svgIcon([
                'M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z'
            ]);
        },
        // Folder open
        folderOpen: function () {
            return svgIcon([
                'M6 14l1.5-2.9A2 2 0 0 1 9.24 10H20a2 2 0 0 1 1.94 2.5l-1.54 6a2 2 0 0 1-1.95 1.5H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2v3'
            ]);
        },
        // Document / file
        file: function () {
            return svgIcon([
                'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z',
                'M14 2v6h6',
                'M16 13H8',
                'M16 17H8'
            ], 16);
        },
        // Chevron right
        chevron: function () {
            return svgIcon(['M9 18l6-6-6-6'], 14);
        },
        // Hamburger / sidebar toggle
        menu: function () {
            return svgIcon([
                'M3 12h18', 'M3 6h18', 'M3 18h18'
            ]);
        },
        // Collapse sidebar (panel left close)
        panelLeft: function () {
            return svgIcon([
                'M3 3h18v18H3z',
                'M9 3v18'
            ]);
        },
        // Database icon for footer
        database: function () {
            return svgIcon([
                'M12 2C6.48 2 2 4.02 2 6.5v11C2 19.98 6.48 22 12 22s10-2.02 10-4.5v-11C22 4.02 17.52 2 12 2z',
                'M2 6.5C2 8.98 6.48 11 12 11s10-2.02 10-4.5',
                'M2 11.5C2 13.98 6.48 16 12 16s10-2.02 10-4.5',
            ], 16);
        }
    };

    function escHtml(s) {
        return String(s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    function isCurrentPage(href) {
        if (!href) return false;
        try { return new URL(href, document.baseURI).href === location.href; } catch (e) { return false; }
    }

    function hasCurrentDescendant(item) {
        if (item.href && isCurrentPage(item.href)) return true;
        if (item.children) return item.children.some(hasCurrentDescendant);
        return false;
    }

    // Tile color palette: cycles through 8 hues for depth-0 module tiles
    var tileColorIndex = 0;

    function renderNavItem(item, depth) {
        var hasCh = item.children && item.children.length > 0;
        var indent = depth > 0 ? (depth * 14) : 0;

        if (!hasCh) {
            // ── Leaf item (database icon) ─────────────────────────────────
            var isCurrent = isCurrentPage(item.href);
            var a = document.createElement('a');
            a.className = 'nav-item-link' + (isCurrent ? ' nav-current' : '');
            a.href = item.href || '#';
            a.title = item.label || '';

            var iconSpan = document.createElement('span');
            iconSpan.className = 'nav-item-icon';
            if (indent > 0) iconSpan.style.paddingLeft = indent + 'px';
            iconSpan.appendChild(ICONS.database());

            var labelSpan = document.createElement('span');
            labelSpan.className = 'nav-item-label';
            labelSpan.textContent = item.label || '';

            a.appendChild(iconSpan);
            a.appendChild(labelSpan);
            return a;
        }

        if (depth === 0) {
            // ── Top-level module tile ─────────────────────────────────────
            var hasCurrent = hasCurrentDescendant(item);
            // Always auto-cycle the palette class as default; inline styles override below
            var colorIdx = tileColorIndex % 8;
            tileColorIndex++;
            var colorClass = 'nav-tile-c' + colorIdx;

            var tileDiv = document.createElement('div');
            tileDiv.className = 'nav-module-tile ' + colorClass + (hasCurrent ? ' open' : '');

            var tileBtn = document.createElement('button');
            tileBtn.type = 'button';
            tileBtn.className = 'nav-tile-btn';
            tileBtn.title = item.label || '';

            var iconWrap = document.createElement('span');
            iconWrap.className = 'nav-tile-icon';
            if (item.icon) {
                // item.icon is a pre-built inline SVG string from LucideIcons.java
                iconWrap.innerHTML = item.icon;
            } else {
                iconWrap.appendChild(ICONS.folder());
            }

            var labelSpan = document.createElement('span');
            labelSpan.className = 'nav-tile-label';
            labelSpan.textContent = item.label || '';
            if (item.tileFontSize) {
                labelSpan.style.fontSize = item.tileFontSize + 'px';
            }

            // ── Optional inline color overrides ───────────────────────────
            if (item.tileBg) {
                tileDiv.style.setProperty('--tile-bg', item.tileBg);
                tileDiv.style.setProperty('--tile-bg-open', item.tileBg);
            }
            if (item.tileText) {
                labelSpan.style.color = item.tileText;
            }
            if (item.tileIcon) {
                iconWrap.style.color = item.tileIcon;
            }

            tileBtn.appendChild(iconWrap);
            tileBtn.appendChild(labelSpan);

            var childDiv = document.createElement('div');
            childDiv.className = 'nav-group-children nav-tile-children' + (hasCurrent ? ' open' : '');
            (item.children || []).forEach(function (child) {
                childDiv.appendChild(renderNavItem(child, depth + 1));
            });

            tileBtn.addEventListener('click', function () {
                tileDiv.classList.toggle('open');
                childDiv.classList.toggle('open');
            });

            tileDiv.appendChild(tileBtn);
            tileDiv.appendChild(childDiv);
            return tileDiv;
        }

        // ── Sub-group item (depth > 0, has children) ─────────────────────
        var hasCurrent = hasCurrentDescendant(item);
        var div = document.createElement('div');

        var btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'nav-group-toggle' + (hasCurrent ? ' open' : '');
        btn.title = item.label || '';

        var iconSpan = document.createElement('span');
        iconSpan.className = 'nav-group-icon';
        if (indent > 0) iconSpan.style.paddingLeft = indent + 'px';
        iconSpan.appendChild(hasCurrent ? ICONS.folderOpen() : ICONS.folder());

        var labelSpan = document.createElement('span');
        labelSpan.className = 'nav-group-label';
        labelSpan.textContent = item.label || '';

        btn.appendChild(iconSpan);
        btn.appendChild(labelSpan);

        var childDiv = document.createElement('div');
        childDiv.className = 'nav-group-children' + (hasCurrent ? ' open' : '');
        (item.children || []).forEach(function (child) {
            childDiv.appendChild(renderNavItem(child, depth + 1));
        });

        btn.addEventListener('click', function () {
            var isOpen = btn.classList.toggle('open');
            childDiv.classList.toggle('open');
            iconSpan.innerHTML = '';
            iconSpan.appendChild(isOpen ? ICONS.folderOpen() : ICONS.folder());
        });

        div.appendChild(btn);
        div.appendChild(childDiv);
        return div;
    }

    // ── Render navigation ─────────────────────────────────────────────────
    var navItems = (typeof NAV_ITEMS !== 'undefined') ? NAV_ITEMS : [];
    if (navItems && navItems.length > 0) {
        navItems.forEach(function (item) {
            navSidebarScroll.appendChild(renderNavItem(item, 0));
        });
        // Smooth scroll to current page
        var cur = navSidebarScroll.querySelector('.nav-current');
        if (cur) {
            setTimeout(function () {
                cur.scrollIntoView({ block: 'center', behavior: 'smooth' });
            }, 100);
        }
    } else {
        navSidebarScroll.innerHTML = '<div style="padding:16px;font-size:12px;color:var(--c-text-muted);text-align:center;">No modules</div>';
    }

    // ── Collapse state persistence ────────────────────────────────────────
    var stored = null;
    try { stored = localStorage.getItem('m4o_nav_collapsed'); } catch (e) { }
    if (stored === '1') navSidebar.classList.add('icon-only');

    navToggleBtn.addEventListener('click', function () {
        navSidebar.classList.toggle('icon-only');
        try { localStorage.setItem('m4o_nav_collapsed', navSidebar.classList.contains('icon-only') ? '1' : '0'); } catch (e) { }
    });
})();
// ─────────────────────────────────────────────────────────────────────────────

// ─── Data viewer ──────────────────────────────────────────────────────────────
// Only runs on viewer pages (where conditionsContainer exists).
(function () {
    var conditionsContainer = document.getElementById('conditionsContainer');
    if (!conditionsContainer) return;

    // entityName is set globally in the template via a separate script tag
    var _entityName = (typeof entityName !== 'undefined') ? entityName : '';

    let allRecords = [];
    let filteredRecords = [];
    let discoveredFields = [];
    let currentPage = 1;
    let selectedRecordKey = null;
    let searchApplied = false;
    let globalLogicOperator = 'AND';
    let currentLanguage = 'fr';
    let selectedColumns = ['__id', '__summary'];
    let collectionViewState = {};
    let collectionIdCounter = 1;
    const schemaFields = (typeof SCHEMA_FIELDS !== 'undefined' && Array.isArray(SCHEMA_FIELDS)) ? SCHEMA_FIELDS : [];
    const schemaTitleByPath = {};
    const schemaTitleByName = {};

    const addConditionBtn = document.getElementById('addConditionBtn');
    const clearSearchBtn = document.getElementById('clearSearchBtn');
    const searchBtn = document.getElementById('searchBtn');
    const resultsCount = document.getElementById('resultsCount');
    const resultsHead = document.getElementById('resultsHead');
    const resultsBody = document.getElementById('resultsBody');
    const pageInfo = document.getElementById('pageInfo');
    const prevBtn = document.getElementById('prevBtn');
    const nextBtn = document.getElementById('nextBtn');
    const pageSizeSelect = document.getElementById('pageSizeSelect');
    const detailContainer = document.getElementById('detailContainer');
    const detailOverlay = document.getElementById('detailOverlay');
    const detailCloseBtn = document.getElementById('detailCloseBtn');
    const detailPrevBtn = document.getElementById('detailPrevBtn');
    const detailNextBtn = document.getElementById('detailNextBtn');
    const detailNavPos = document.getElementById('detailNavPos');
    const languageSelect = document.getElementById('languageSelect');
    const columnsBtn = document.getElementById('columnsBtn');
    const columnsMenu = document.getElementById('columnsMenu');
    const searchTitle = document.getElementById('searchTitle');
    const rowsLabel = document.getElementById('rowsLabel');

    const I18N = {
        fr: {
            search: 'Recherche', columns: 'Colonnes', addCondition: '+ Critère', clear: 'Effacer', apply: 'Appliquer',
            rows: 'Lignes :', prev: '\u2190 Préc.', next: 'Suiv. \u2192', loading: 'Chargement...',
            noResults: 'Aucun enregistrement ne correspond à vos critères.', noResultsSub: 'Essayez d\u2019ajuster vos critères ou effacez la recherche.',
            browse: 'Parcourir tout', welcome: 'enregistrements', welcomeHint: 'Ajoutez des critères ci-dessus pour filtrer, ou parcourez tous les enregistrements.',
            resultsTotal: 'enregistrements au total', result: 'résultat', results: 'résultats', page: 'Page', record: 'Enregistrement',
            detail: 'Détails', properties: 'Propriétés', close: 'Fermer', remove: 'Supprimer',
            toggleLogic: 'Cliquer pour alterner ET/OU', allFields: 'Tous les champs', value: 'Valeur...', fieldFilter: 'Champ...',
            colRow: '#', colId: 'ID', colSummary: 'Résumé', err: 'Erreur', noPayload: 'Aucune donnée JS trouvée (window.__m4o).',
            object: 'Objet', collection: 'Collection', elements: 'éléments', noItems: 'Aucun élément',
            prev: 'Préc.', next: 'Suiv.',
            keyInfo: 'Informations clés', dates: 'Dates', identifiers: 'Identifiants', flags: 'Indicateurs',
            emptyFields: 'Champs vides', numbers: 'Valeurs numériques', details: 'Détails',
            boolTrue: 'Oui', boolFalse: 'Non'
        },
        en: {
            search: 'Search', columns: 'Columns', addCondition: '+ Condition', clear: 'Clear', apply: 'Apply',
            rows: 'Rows:', prev: '\u2190 Prev', next: 'Next \u2192', loading: 'Loading...',
            noResults: 'No records match your criteria.', noResultsSub: 'Try adjusting your conditions or clear the search.',
            browse: 'Browse all', welcome: 'records', welcomeHint: 'Add conditions above to filter, or browse all records.',
            resultsTotal: 'records total', result: 'result', results: 'results', page: 'Page', record: 'Record',
            detail: 'Details', properties: 'Properties', close: 'Close', remove: 'Remove',
            toggleLogic: 'Click to toggle AND/OR', allFields: 'All fields', value: 'Value...', fieldFilter: 'Field...',
            colRow: '#', colId: 'ID', colSummary: 'Summary', err: 'Error', noPayload: 'No JS payload found (window.__m4o).',
            object: 'Object', collection: 'Collection', elements: 'items', noItems: 'No items',
            prev: 'Prev', next: 'Next',
            keyInfo: 'Key Information', dates: 'Dates', identifiers: 'Identifiers', flags: 'Flags',
            emptyFields: 'Empty Fields', numbers: 'Numeric Values', details: 'Details',
            boolTrue: 'Yes', boolFalse: 'No'
        }
    };

    const OPERATORS = {
        string: [{ v: 'contains' }, { v: 'not_contains' }, { v: 'equals' }, { v: 'not_equals' }, { v: 'empty' }, { v: 'not_empty' }],
        _all: [{ v: 'contains' }, { v: 'not_contains' }, { v: 'empty' }, { v: 'not_empty' }]
    };
    const OPERATOR_LABELS = {
        fr: {
            contains: 'Contient',
            not_contains: 'Ne contient pas',
            equals: '\u00c9gale',
            not_equals: 'Différent de',
            empty: 'Est vide',
            not_empty: 'N\u2019est pas vide'
        },
        en: {
            contains: 'Contains',
            not_contains: 'Does not contain',
            equals: 'Equals',
            not_equals: 'Not equal to',
            empty: 'Is empty',
            not_empty: 'Is not empty'
        }
    };
    const NO_VALUE_OPS = new Set(['empty', 'not_empty']);

    function t(key) { return (I18N[currentLanguage] && I18N[currentLanguage][key]) || key; }
    function esc(s) { return String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }

    function normalizeSchemaPath(path) {
        return String(path || '').replace(/\[\d+\]/g, '').replace(/\.{2,}/g, '.').replace(/^\./, '').replace(/\.$/, '').trim();
    }

    function indexSchemaFields(fields) {
        if (!Array.isArray(fields)) return;
        fields.forEach((field) => {
            if (!field || typeof field !== 'object') return;
            const p = normalizeSchemaPath(field.path || field.name || '');
            const n = normalizeSchemaPath(field.name || '');
            const title = String(field.title || '').trim();
            if (title) {
                if (p && !schemaTitleByPath[p]) schemaTitleByPath[p] = title;
                if (n && !schemaTitleByName[n]) schemaTitleByName[n] = title;
            }
            if (Array.isArray(field.children) && field.children.length > 0) {
                indexSchemaFields(field.children);
            }
        });
    }

    function schemaTitleForPath(path) {
        const normalized = normalizeSchemaPath(path);
        if (!normalized) return '';
        if (schemaTitleByPath[normalized]) return schemaTitleByPath[normalized];
        const parts = normalized.split('.').filter(Boolean);
        const last = parts.length > 0 ? parts[parts.length - 1] : normalized;
        return schemaTitleByName[last] || '';
    }

    indexSchemaFields(schemaFields);

    // Build a set of field names/paths that represent non-embedding IDEntite fields.
    // These will be rendered inline (multicolumn) inside their parent section.
    const idEntiteFieldSet = new Set();
    function collectIdEntiteFields(fields) {
        if (!Array.isArray(fields)) return;
        fields.forEach(function (field) {
            if (field && field.idEntite) {
                var p = normalizeSchemaPath(field.path || field.name || '');
                var n = normalizeSchemaPath(field.name || '');
                if (p) idEntiteFieldSet.add(p);
                if (n) idEntiteFieldSet.add(n);
            }
            if (field && Array.isArray(field.children)) collectIdEntiteFields(field.children);
        });
    }
    collectIdEntiteFields(schemaFields);

    function appendField(map, key, value) {
        if (!key) return;
        const normalizedKey = normalizeFieldPath(key);
        if (!normalizedKey) return;
        const text = String(value ?? '').trim();
        if (text.length === 0) return;
        if (map[normalizedKey]) {
            if (!map[normalizedKey].includes(text)) map[normalizedKey] += ' | ' + text;
        } else {
            map[normalizedKey] = text;
        }
    }

    function normalizeFieldPath(path) {
        let clean = String(path || '').replace(/\[\d+\]/g, '');
        clean = clean.replace(/\.{2,}/g, '.').replace(/^\./, '').replace(/\.$/, '');
        if (!clean) return '';

        const segments = clean.split('.').filter(Boolean).map((segment) => {
            const withoutAt = segment.startsWith('@') ? segment.substring(1) : segment;
            return withoutAt.trim();
        }).filter(Boolean);

        const out = [];
        for (const seg of segments) {
            if (out.length === 0) {
                out.push(seg);
                continue;
            }

            const prev = out[out.length - 1];
            const prevLo = prev.toLowerCase();
            const segLo = seg.toLowerCase();

            if (prevLo === segLo) {
                continue;
            }

            if (prevLo.replace(/^liste/, '') === segLo || segLo.replace(/^liste/, '') === prevLo) {
                continue;
            }

            out.push(seg);
        }

        return out.join('.');
    }

    function flattenValue(value, path, out) {
        if (value === null || value === undefined) return;
        if (Array.isArray(value)) {
            value.forEach((item, index) => flattenValue(item, `${path}[${index + 1}]`, out));
            return;
        }
        if (typeof value !== 'object') {
            appendField(out, path, value);
            return;
        }

        if (value['@attributes'] && typeof value['@attributes'] === 'object') {
            Object.entries(value['@attributes']).forEach(([k, v]) => appendField(out, `${path}.${k}`, v));
        }
        if (value['#text'] !== undefined) {
            appendField(out, path, value['#text']);
        }

        Object.entries(value).forEach(([k, v]) => {
            if (k === '@attributes' || k === '#text') return;
            const nextPath = path ? `${path}.${k}` : k;
            flattenValue(v, nextPath, out);
        });
    }

    function pickBest(fields, candidates) {
        const entries = Object.entries(fields);
        for (const c of candidates) {
            const exact = entries.find(([k]) => k.toLowerCase() === c);
            if (exact && exact[1]) return exact[1];
        }
        for (const c of candidates) {
            const partial = entries.find(([k]) => k.toLowerCase().includes(c));
            if (partial && partial[1]) return partial[1];
        }
        return '';
    }

    function summarize(fields) {
        const primary = pickBest(fields, ['name', 'nom', 'title', 'titre', 'label', 'libelle', 'description']);
        if (primary) return primary;
        const vals = Object.values(fields).filter(Boolean);
        return vals.length > 0 ? vals.slice(0, 2).join(' \u2022 ') : '(no details)';
    }

    function buildRecord(entity, raw, pos) {
        const fields = {};
        const attrs = raw && raw['@attributes'] && typeof raw['@attributes'] === 'object' ? raw['@attributes'] : null;
        const serverSummary = attrs && attrs._summary ? attrs._summary : null;
        if (attrs) Object.entries(attrs).forEach(([k, v]) => { if (k !== '_summary') appendField(fields, `${k}`, v); });
        if (raw && typeof raw === 'object') {
            Object.entries(raw).forEach(([k, v]) => {
                if (k === '@attributes') return;
                flattenValue(v, k, fields);
            });
        }
        const id = (attrs && attrs.id) || pickBest(fields, ['id', 'identifiant', 'numero', 'code']) || '';
        return {
            key: `${entity}#${pos}`,
            pos,
            entity,
            id: String(id || ''),
            fields,
            raw,
            summary: serverSummary || summarize(fields)
        };
    }

    function collectFromNamedArray(entity, value, acc) {
        if (Array.isArray(value)) {
            value.forEach((item) => {
                if (item && typeof item === 'object') {
                    acc.push(buildRecord(entity, item, acc.length + 1));
                }
            });
            return;
        }
        if (value && typeof value === 'object') {
            acc.push(buildRecord(entity, value, acc.length + 1));
        }
    }

    function getRootPayload() {
        const raw = window.__m4o;
        if (!raw || typeof raw !== 'object') return null;
        if (raw.export && typeof raw.export === 'object') return raw.export;
        return raw;
    }

    function parsePayload() {
        const root = getRootPayload();
        if (!root) {
            throw new Error(t('noPayload'));
        }

        const acc = [];
        if (Array.isArray(root.objects)) {
            root.objects.forEach((entry) => {
                if (!entry || typeof entry !== 'object') return;
                Object.entries(entry).forEach(([entity, value]) => collectFromNamedArray(entity, value, acc));
            });
        }

        if (acc.length === 0) {
            Object.entries(root).forEach(([entity, value]) => {
                if (entity === 'metadata' || entity === 'objects') return;
                collectFromNamedArray(entity, value, acc);
            });
        }

        allRecords = acc;
        filteredRecords = allRecords.slice();
    }

    function guessType(path) {
        const lo = String(path || '').toLowerCase();
        if (lo.includes('date')) return 'date';
        if (lo === 'id' || lo.endsWith('id') || lo.startsWith('id')) return 'number';
        return 'string';
    }

    function buildDiscoveredFields() {
        const set = new Set();
        allRecords.forEach((rec) => Object.keys(rec.fields || {}).forEach((f) => set.add(f)));
        discoveredFields = Array.from(set).sort().map((path) => ({
            path,
            label: schemaTitleForPath(path) || humanizeFieldName(path),
            type: guessType(path)
        }));
    }

    function getFieldType(path) {
        if (path === '__all') return '_all';
        const found = discoveredFields.find((f) => f.path === path);
        return found ? found.type : 'string';
    }

    function buildFieldOptions() {
        let html = `<option value="__all">${esc(t('allFields'))}</option>`;
        discoveredFields.forEach((f) => {
            html += `<option value="${esc(f.path)}">${esc(f.label)}</option>`;
        });
        return html;
    }

    function buildOperatorOptions(fieldType) {
        const ops = OPERATORS[fieldType] || OPERATORS.string;
        const labels = OPERATOR_LABELS[currentLanguage] || OPERATOR_LABELS.en;
        return ops.map((o) => `<option value="${o.v}">${esc(labels[o.v] || o.v)}</option>`).join('');
    }

    function updateValueInput(opSel, valInput) {
        if (NO_VALUE_OPS.has(opSel.value)) {
            valInput.style.display = 'none';
            valInput.value = '';
        } else {
            valInput.style.display = '';
            valInput.placeholder = t('value');
        }
    }

    function addCondition() {
        const isFirst = conditionsContainer.children.length === 0;
        const row = document.createElement('div');
        row.className = 'search-condition';
        row.innerHTML =
            `<div class="condition-main">`
            + `<span class="logic-label ${isFirst ? 'first' : ''}" title="${esc(t('toggleLogic'))}">${globalLogicOperator}</span>`
            + `<input class="field-filter" type="text" placeholder="${esc(t('fieldFilter'))}" aria-label="Field filter">`
            + `<select class="field-select">${buildFieldOptions()}</select>`
            + `<select class="operator-select">${buildOperatorOptions('_all')}</select>`
            + `<input class="value-input" type="text" placeholder="${esc(t('value'))}">`
            + `<button class="remove-btn" type="button" title="${esc(t('remove'))}">×</button>`
            + `</div>`;

        conditionsContainer.appendChild(row);

        const fieldSel = row.querySelector('.field-select');
        const fieldFilter = row.querySelector('.field-filter');
        const opSel = row.querySelector('.operator-select');
        const valInput = row.querySelector('.value-input');
        const removeBtn = row.querySelector('.remove-btn');
        const logicLabel = row.querySelector('.logic-label');

        function refreshFieldOptions(filterText, keepValue) {
            const term = String(filterText || '').trim().toLowerCase();
            const pool = term
                ? discoveredFields.filter((f) => (f.label || f.path).toLowerCase().includes(term) || f.path.toLowerCase().includes(term))
                : discoveredFields.slice();

            let html = `<option value="__all">${esc(t('allFields'))}</option>`;
            pool.forEach((f) => {
                html += `<option value="${esc(f.path)}">${esc(f.label)}</option>`;
            });

            const previous = keepValue ? fieldSel.value : null;
            fieldSel.innerHTML = html;

            if (previous && Array.from(fieldSel.options).some((opt) => opt.value === previous)) {
                fieldSel.value = previous;
            } else if (term && pool.length > 0) {
                fieldSel.value = pool[0].path;
            } else {
                fieldSel.value = '__all';
            }

            opSel.innerHTML = buildOperatorOptions(getFieldType(fieldSel.value));
            updateValueInput(opSel, valInput);
        }

        fieldSel.addEventListener('change', () => {
            opSel.innerHTML = buildOperatorOptions(getFieldType(fieldSel.value));
            updateValueInput(opSel, valInput);
        });
        fieldFilter.addEventListener('input', () => refreshFieldOptions(fieldFilter.value, false));
        opSel.addEventListener('change', () => updateValueInput(opSel, valInput));
        logicLabel.addEventListener('click', () => {
            if (logicLabel.classList.contains('first')) return;
            globalLogicOperator = globalLogicOperator === 'AND' ? 'OR' : 'AND';
            document.querySelectorAll('.logic-label:not(.first)').forEach((lbl) => lbl.textContent = globalLogicOperator);
        });
        removeBtn.addEventListener('click', () => {
            row.remove();
            const first = conditionsContainer.querySelector('.search-condition .logic-label');
            if (first) first.classList.add('first');
        });
        valInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                applySearch();
            }
        });

        refreshFieldOptions('', true);
        updateValueInput(opSel, valInput);
    }

    function getConditions() {
        return Array.from(conditionsContainer.querySelectorAll('.search-condition')).map((row) => ({
            field: row.querySelector('.field-select').value,
            operator: row.querySelector('.operator-select').value,
            value: row.querySelector('.value-input').value.trim()
        }));
    }

    function matchCondition(rec, cond) {
        let values;
        if (cond.field === '__all') {
            values = [rec.id, rec.summary].concat(Object.values(rec.fields || {})).filter(Boolean);
        } else {
            values = Object.entries(rec.fields || {})
                .filter(([k]) => k === cond.field)
                .map(([, v]) => v)
                .filter(Boolean);
        }

        const text = values.join(' ').toLowerCase();
        const q = String(cond.value || '').toLowerCase();

        if (cond.operator === 'contains') return text.includes(q);
        if (cond.operator === 'not_contains') return !text.includes(q);
        if (cond.operator === 'equals') return values.some((v) => String(v).toLowerCase() === q);
        if (cond.operator === 'not_equals') return values.every((v) => String(v).toLowerCase() !== q);
        if (cond.operator === 'empty') return values.length === 0 || values.every((v) => !String(v).trim());
        if (cond.operator === 'not_empty') return values.some((v) => String(v).trim().length > 0);
        return true;
    }

    function ensureSearchColumnsVisible(conditions) {
        conditions.forEach((c) => {
            if (!c || !c.field || c.field === '__all') return;
            if (!selectedColumns.includes(c.field)) selectedColumns.push(c.field);
        });
    }

    function applySearch() {
        const conditions = getConditions();
        ensureSearchColumnsVisible(conditions);
        renderColumnsMenu();

        const active = conditions.filter((c) => c.value.trim() || NO_VALUE_OPS.has(c.operator));
        searchApplied = active.length > 0;

        if (!searchApplied) {
            filteredRecords = allRecords.slice();
        } else if (globalLogicOperator === 'AND') {
            filteredRecords = allRecords.filter((rec) => active.every((c) => matchCondition(rec, c)));
        } else {
            filteredRecords = allRecords.filter((rec) => active.some((c) => matchCondition(rec, c)));
        }

        currentPage = 1;
        renderResults();
    }

    function getPageSize() { return parseInt(pageSizeSelect.value, 10) || 50; }

    function getColumnLabel(col) {
        if (col === '__id') return t('colId');
        if (col === '__summary') return t('colSummary');
        const f = discoveredFields.find((d) => d.path === col);
        return f ? (f.label || f.path) : col;
    }

    function getColumnValue(rec, col) {
        if (col === '__id') return rec.id || '\u2014';
        if (col === '__summary') return rec.summary || '\u2014';
        return rec.fields[col] || '\u2014';
    }

    function getVisibleColumns() {
        const seen = new Set();
        const cols = [];
        selectedColumns.forEach((c) => {
            if (!c || seen.has(c)) return;
            seen.add(c);
            cols.push(c);
        });
        return cols;
    }

    function renderColumnsMenu() {
        let html = '';
        html += `<label class="columns-item"><input type="checkbox" data-col="__id" ${selectedColumns.includes('__id') ? 'checked' : ''}> ${esc(t('colId'))}</label>`;
        html += `<label class="columns-item"><input type="checkbox" data-col="__summary" ${selectedColumns.includes('__summary') ? 'checked' : ''}> ${esc(t('colSummary'))}</label>`;
        discoveredFields.forEach((f) => {
            html += `<label class="columns-item"><input type="checkbox" data-col="${esc(f.path)}" ${selectedColumns.includes(f.path) ? 'checked' : ''}> ${esc(f.label || f.path)}</label>`;
        });
        columnsMenu.innerHTML = html;

        columnsMenu.querySelectorAll('input[type="checkbox"]').forEach((chk) => {
            chk.addEventListener('change', () => {
                const col = chk.getAttribute('data-col');
                if (chk.checked) {
                    if (!selectedColumns.includes(col)) selectedColumns.push(col);
                } else {
                    selectedColumns = selectedColumns.filter((c) => c !== col);
                }
                renderResults();
            });
        });
    }

    function renderResultsHead() {
        const cols = getVisibleColumns();
        let head = `<tr><th style="width:55px">${esc(t('colRow'))}</th>`;
        cols.forEach((c) => head += `<th>${esc(getColumnLabel(c))}</th>`);
        head += '</tr>';
        resultsHead.innerHTML = head;
    }

    function renderResults() {
        renderResultsHead();
        const ps = getPageSize();
        const total = filteredRecords.length;
        const totalPages = Math.max(1, Math.ceil(total / ps));
        if (currentPage > totalPages) currentPage = totalPages;

        if (!searchApplied && total > ps) {
            const totalCols = 1 + getVisibleColumns().length;
            resultsBody.innerHTML = `<tr><td colspan="${totalCols}"><div class="welcome"><div class="icon">\ud83d\udd0e</div><p><strong>${total}</strong> ${esc(_entityName)} ${esc(t('welcome'))}</p><div class="sub">${esc(t('welcomeHint'))}</div></div></td></tr>`;
            resultsCount.textContent = total + ' ' + t('resultsTotal');
            pageInfo.textContent = `${t('page')} 1 / ${totalPages}`;
            prevBtn.disabled = true;
            nextBtn.disabled = true;
            return;
        }

        const start = (currentPage - 1) * ps;
        const page = filteredRecords.slice(start, start + ps);
        const cols = getVisibleColumns();
        const totalCols = 1 + cols.length;

        resultsBody.innerHTML = '';

        if (page.length === 0) {
            resultsBody.innerHTML = `<tr><td colspan="${totalCols}"><div class="results-empty"><div class="icon">\ud83d\udeab</div><p>${esc(t('noResults'))}</p><div class="sub">${esc(t('noResultsSub'))}</div></div></td></tr>`;
        } else {
            page.forEach((rec) => {
                const tr = document.createElement('tr');
                if (rec.key === selectedRecordKey) tr.classList.add('active');
                let row = `<td>${rec.pos}</td>`;
                cols.forEach((c) => row += `<td>${esc(getColumnValue(rec, c))}</td>`);
                tr.innerHTML = row;
                tr.addEventListener('click', () => selectRecord(rec));
                resultsBody.appendChild(tr);
            });
        }

        resultsCount.textContent = total + ' ' + (total !== 1 ? t('results') : t('result'));
        pageInfo.textContent = `${t('page')} ${currentPage} / ${totalPages}`;
        prevBtn.disabled = currentPage <= 1;
        nextBtn.disabled = currentPage >= totalPages;
    }

    function fmtDate(val) {
        const m = String(val).match(/^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2}))?)?/);
        if (!m) return null;
        try {
            const d = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]),
                m[4] ? Number(m[4]) : 0, m[5] ? Number(m[5]) : 0, m[6] ? Number(m[6]) : 0);
            if (isNaN(d.getTime())) return null;
            const opts = { year: 'numeric', month: 'long', day: 'numeric' };
            if (m[4]) { opts.hour = '2-digit'; opts.minute = '2-digit'; }
            return d.toLocaleDateString('fr-CA', opts);
        } catch (_) { return null; }
    }

    function fmtValue(v, key) {
        const val = String(v ?? '');
        if (!val.trim()) return '<span style="color:var(--c-text-muted)">\u2014</span>';
        const lowerKey = String(key || '').toLowerCase();
        if (val === 'true') return '<span class="badge badge-true">' + esc(t('boolTrue')) + '</span>';
        if (val === 'false') return '<span class="badge badge-false">' + esc(t('boolFalse')) + '</span>';
        // Date detection (before numeric check so dates aren't caught)
        if (lowerKey.includes('date') || /^\d{4}-\d{2}-\d{2}/.test(val)) {
            const formatted = fmtDate(val);
            if (formatted) return esc(formatted);
        }
        if (lowerKey === 'id' || lowerKey.startsWith('id') || lowerKey.endsWith('id')) return `<span class="badge badge-id">${esc(val)}</span>`;
        if (/^-?\d+(\.\d+)?$/.test(val)) return `<span class="badge badge-number">${esc(val)}</span>`;
        return esc(val);
    }

    function isNarrowField(label, value) {
        const text = String(value ?? '').trim();
        const name = String(label || '').toLowerCase();
        if (!text) return true;
        if (text === 'true' || text === 'false') return true;
        if (/^-?\d+(\.\d+)?$/.test(text)) return true;
        if (name === 'id' || name.endsWith('.id') || name.startsWith('id')) return text.length <= 24;
        if (/^\d{4}-\d{2}-\d{2}/.test(text)) return true;
        return name.length <= 24 && text.length <= 26;
    }

    function mergeAttributes(target, attrs) {
        if (!attrs || typeof attrs !== 'object' || !target || typeof target !== 'object' || Array.isArray(target)) {
            return;
        }
        const next = target['@attributes'] && typeof target['@attributes'] === 'object' ? target['@attributes'] : {};
        Object.entries(attrs).forEach(([k, v]) => {
            if (next[k] === undefined) next[k] = v;
        });
        if (Object.keys(next).length > 0) target['@attributes'] = next;
    }

    function isLikelyCollectionField(fieldName, arrayValue) {
        if (!Array.isArray(arrayValue)) return false;
        if (arrayValue.length > 1) return true;
        const name = String(fieldName || '').toLowerCase();
        return name.startsWith('liste') || name.startsWith('table') || name.startsWith('set') || name.includes('collection');
    }

    function unwrapEmbeddedValue(fieldName, value) {
        let current = value;
        let guard = 0;
        while (current && typeof current === 'object' && !Array.isArray(current) && guard < 8) {
            const keys = Object.keys(current).filter((k) => k !== '@attributes' && k !== '#text');
            if (keys.length !== 1) break;

            const childKey = keys[0];
            const childValue = current[childKey];
            const parentLo = String(fieldName || '').toLowerCase();
            const childLo = String(childKey || '').toLowerCase();
            const parentNoList = parentLo.replace(/^liste/, '');
            const childNoList = childLo.replace(/^liste/, '');
            const looksWrapper = childLo === parentLo
                || childNoList === parentNoList
                || /^[A-Z]/.test(childKey);

            if (!looksWrapper) break;

            const currentAttrs = current['@attributes'];
            if (Array.isArray(childValue) && childValue.length === 1 && childValue[0] && typeof childValue[0] === 'object') {
                current = childValue[0];
                mergeAttributes(current, currentAttrs);
                guard++;
                continue;
            }
            if (childValue && typeof childValue === 'object' && !Array.isArray(childValue)) {
                current = childValue;
                mergeAttributes(current, currentAttrs);
                guard++;
                continue;
            }
            break;
        }
        return current;
    }

    function classifyFieldEntry(key, value) {
        if (value === null || value === undefined) {
            return { key, type: 'primitive', value: '' };
        }

        if (Array.isArray(value)) {
            if (isLikelyCollectionField(key, value)) {
                return { key, type: 'collection', value };
            }

            if (value.length === 0) {
                return { key, type: 'primitive', value: '' };
            }

            if (value.length === 1) {
                const single = value[0];
                if (single === null || single === undefined) {
                    return { key, type: 'primitive', value: '' };
                }
                if (typeof single !== 'object') {
                    return { key, type: 'primitive', value: single };
                }
                return { key, type: 'object', value: unwrapEmbeddedValue(key, single) };
            }

            const allPrimitive = value.every((item) => item === null || item === undefined || typeof item !== 'object');
            if (allPrimitive) {
                return { key, type: 'primitive', value: value.map((item) => String(item ?? '')).join(' | ') };
            }

            return { key, type: 'collection', value };
        }

        if (typeof value === 'object') {
            return { key, type: 'object', value: unwrapEmbeddedValue(key, value) };
        }

        return { key, type: 'primitive', value };
    }

    function getObjectEntries(value) {
        if (!value || typeof value !== 'object' || Array.isArray(value)) return [];
        const entries = [];
        const attrs = value['@attributes'];
        if (attrs && typeof attrs === 'object') {
            Object.entries(attrs).forEach(([k, v]) => {
                entries.push({ key: k, value: v, type: 'primitive' });
            });
        }
        if (value['#text'] !== undefined) {
            entries.push({ key: '#text', value: value['#text'], type: 'primitive' });
        }

        Object.entries(value).forEach(([k, v]) => {
            if (k === '@attributes' || k === '#text') return;
            entries.push(classifyFieldEntry(k, v));
        });

        return entries;
    }

    function humanizeFieldName(name) {
        const raw = String(name || '')
            .replace(/^@/, '')
            .replace(/\[\d+\]/g, '')
            .trim();
        if (!raw) return '';

        return raw
            .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
            .replace(/[_-]+/g, ' ')
            .replace(/\s+/g, ' ')
            .toLowerCase()
            .replace(/^./, (c) => c.toUpperCase());
    }

    function formatSectionTitle(name) {
        return schemaTitleForPath(name) || humanizeFieldName(name);
    }

    function displayFieldLabel(path) {
        const normalized = normalizeFieldPath(path || '');
        if (!normalized) return '';
        const titled = schemaTitleForPath(normalized);
        if (titled) return titled;
        const parts = normalized.split('.').filter(Boolean);
        const last = parts.length > 0 ? parts[parts.length - 1] : normalized;
        return schemaTitleForPath(last) || humanizeFieldName(last);
    }

    /* ── Field classification for auto-columnization ───────────── */

    const IMPORTANT_FIELD_PATTERNS = ['nom', 'name', 'prenom', 'titre', 'title', 'code', 'numero', 'identifiant', 'description', 'libelle', 'label'];
    const KEY_INFO_PATTERNS = ['nom', 'name', 'prenom', 'titre', 'title', 'code', 'numero', 'identifiant', 'type', 'statut', 'status', 'categorie', 'actif', 'active', 'inactif', 'email', 'courriel', 'telephone', 'matricule'];

    function classifyPrimitiveBucket(key, value) {
        const text = String(value ?? '').trim();
        const lo = String(key || '').toLowerCase();
        if (!text) return 'empty';
        if (text === 'true' || text === 'false') return 'bool';
        if (lo === 'id' || lo.endsWith('.id') || (lo.startsWith('id') && lo.length > 2 && lo[2] === lo[2].toUpperCase())) return 'id';
        if (lo.includes('date') || /^\d{4}-\d{2}-\d{2}/.test(text)) return 'date';
        if (/^-?\d+(\.\d+)?$/.test(text) && text.length <= 10) return 'number';
        if (text.length <= 30) return 'short';
        return 'long';
    }

    function extractCamelPrefix(name) {
        const clean = String(name || '').replace(/^@/, '');
        const parts = clean.split('.');
        const last = parts[parts.length - 1] || clean;
        const match = last.match(/^([a-z]+)[A-Z]/);
        return match ? match[1].toLowerCase() : '';
    }

    function groupByPrefix(entries) {
        const groups = {};
        entries.forEach((entry) => {
            const prefix = extractCamelPrefix(entry.key);
            const groupKey = prefix.length >= 3 ? prefix : '__ungrouped';
            if (!groups[groupKey]) groups[groupKey] = [];
            groups[groupKey].push(entry);
        });
        return groups;
    }

    function fieldImportanceScore(key) {
        const lo = String(key || '').toLowerCase();
        const lastPart = lo.split('.').pop() || lo;
        for (let i = 0; i < IMPORTANT_FIELD_PATTERNS.length; i++) {
            if (lastPart === IMPORTANT_FIELD_PATTERNS[i]) return i;
            if (lastPart.includes(IMPORTANT_FIELD_PATTERNS[i])) return i + 100;
        }
        return 999;
    }

    function sortPrimitiveEntries(entries) {
        return entries.slice().sort((a, b) => {
            const aEmpty = !String(a.value ?? '').trim();
            const bEmpty = !String(b.value ?? '').trim();
            if (aEmpty !== bEmpty) return aEmpty ? 1 : -1;
            const aScore = fieldImportanceScore(a.key);
            const bScore = fieldImportanceScore(b.key);
            if (aScore !== bScore) return aScore - bScore;
            return String(a.key || '').localeCompare(String(b.key || ''));
        });
    }

    function renderFieldRow(entry) {
        return '<div class="field-row"><div class="field-label">' + esc(displayFieldLabel(entry.key)) + '</div><div class="field-value">' + fmtValue(entry.value, entry.key) + '</div></div>';
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

        const attrs = value['@attributes'];
        if (attrs && typeof attrs === 'object') {
            Object.entries(attrs).forEach(([k, v]) => {
                appendRowValue(out, path ? `${path}.${k}` : k, v);
            });
        }
        if (value['#text'] !== undefined) {
            appendRowValue(out, path || 'value', value['#text']);
        }

        Object.entries(value).forEach(([k, v]) => {
            if (k === '@attributes' || k === '#text') return;
            flattenRow(v, path ? `${path}.${k}` : k, out);
        });
    }

    function computeCollectionTable(items) {
        const normalizedItems = expandWrapperCollectionItems(items);
        const rows = [];
        const columnsSet = new Set();

        normalizedItems.forEach((item) => {
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

    function expandWrapperCollectionItems(items) {
        if (!Array.isArray(items)) return [];

        const expanded = [];
        items.forEach((item) => {
            if (!item || typeof item !== 'object' || Array.isArray(item)) {
                expanded.push(item);
                return;
            }

            const wrapperKeys = Object.keys(item).filter((k) => k !== '@attributes' && k !== '#text');
            if (wrapperKeys.length !== 1) {
                expanded.push(item);
                return;
            }

            const wrapperKey = wrapperKeys[0];
            const inner = item[wrapperKey];
            if (!Array.isArray(inner) || inner.length === 0) {
                expanded.push(item);
                return;
            }

            const isObjectArray = inner.every((entry) => entry && typeof entry === 'object' && !Array.isArray(entry));
            if (!isObjectArray) {
                expanded.push(item);
                return;
            }

            const wrapperAttrs = item['@attributes'];
            inner.forEach((entry) => {
                if (wrapperAttrs && typeof wrapperAttrs === 'object') {
                    const copy = JSON.parse(JSON.stringify(entry));
                    mergeAttributes(copy, wrapperAttrs);
                    expanded.push(copy);
                } else {
                    expanded.push(entry);
                }
            });
        });

        return expanded;
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

    function renderCollectionSection(label, items, level) {
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

        const openAttr = level <= 1 ? ' open' : '';
        let html = `<details class="detail-section"${openAttr}><summary><span class="summary-title">${esc(formatSectionTitle(label))}</span><span class="summary-meta">${items.length} ${esc(t('elements'))}</span></summary><div class="section-body">`;

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
                html += `<th data-collection-id="${collectionId}" data-sort-col="${esc(col)}">${esc(displayFieldLabel(col))}<span class="sort-indicator"></span></th>`;
            });
        }
        html += `</tr></thead><tbody id="collection-body-${collectionId}">${renderCollectionTableBody(collectionViewState[collectionId])}</tbody></table>`;
        html += '</div>';

        html += '</div></details>';
        return html;
    }

    /**
     * Renders a non-embedding IDEntite sub-object inline inside its parent section.
     * The sub-object's primitive fields are shown in a 2-column grid with the field
     * name as a small subtitle, and no collapsible wrapper of its own.
     */
    function renderInlineIdEntiteSection(key, value) {
        if (!value || typeof value !== 'object' || Array.isArray(value)) return '';
        const entries = getObjectEntries(value);
        const primitiveEntries = sortPrimitiveEntries(entries.filter((e) => e.type === 'primitive'));
        const collectionEntries = entries.filter((e) => e.type === 'collection');
        // If effectively empty (e.g. just a zero ID), skip
        if (primitiveEntries.length === 0 && collectionEntries.length === 0) return '';
        if (primitiveEntries.length <= 1 && collectionEntries.length === 0) {
            const single = primitiveEntries[0];
            if (single) {
                const lo = String(single.key || '').toLowerCase().split('.').pop() || '';
                const valStr = String(single.value ?? '').trim();
                if ((lo === 'mid' || lo === 'id') && (!valStr || valStr === '0' || valStr === '-1')) return '';
            }
        }
        let html = '<div class="field-group">';
        html += '<div class="field-group-subtitle">' + esc(formatSectionTitle(key)) + '</div>';
        if (primitiveEntries.length >= 2) {
            html += '<div class="field-columns-2">';
            primitiveEntries.forEach((e) => { html += renderFieldRow(e); });
            html += '</div>';
        } else {
            primitiveEntries.forEach((e) => { html += renderFieldRow(e); });
        }
        collectionEntries.forEach((e) => { html += renderCollectionSection(e.key, e.value, 2); });
        html += '</div>';
        return html;
    }

    function renderNodeSection(label, value, level) {
        level = level || 0;
        if (value === null || value === undefined) {
            return '';
        }

        if (Array.isArray(value)) {
            return renderCollectionSection(label, value, level);
        }

        if (typeof value !== 'object') {
            return renderPrimitiveGroup([{ key: label, value, type: 'primitive' }]);
        }

        const entries = getObjectEntries(value);
        const primitiveEntries = sortPrimitiveEntries(entries.filter((entry) => entry.type === 'primitive'));
        const allObjectEntries = entries.filter((entry) => entry.type === 'object').sort((a, b) => String(a.key || '').localeCompare(String(b.key || '')));
        const collectionEntries = entries.filter((entry) => entry.type === 'collection').sort((a, b) => String(a.key || '').localeCompare(String(b.key || '')));

        // Separate IDEntite sub-objects (rendered inline) from regular sub-objects
        const idEntiteEntries = allObjectEntries.filter((e) => idEntiteFieldSet.has(normalizeSchemaPath(e.key)));
        const objectEntries = allObjectEntries.filter((e) => !idEntiteFieldSet.has(normalizeSchemaPath(e.key)));

        // If the section only contains a single ID-like field and nothing else, render inline
        if (objectEntries.length === 0 && idEntiteEntries.length === 0 && collectionEntries.length === 0 && primitiveEntries.length <= 1) {
            const single = primitiveEntries[0];
            if (single) {
                const lo = String(single.key || '').toLowerCase().split('.').pop() || '';
                const valStr = String(single.value ?? '').trim();
                if ((lo === 'mid' || lo === 'id' || lo.startsWith('id')) && (!valStr || valStr === '0' || valStr === '-1')) {
                    // Empty ID reference — skip entirely
                    return '';
                }
            }
        }

        const openAttr = level <= 1 ? ' open' : '';
        let html = `<details class="detail-section"${openAttr}><summary><span class="summary-title">${esc(formatSectionTitle(label))}</span><span class="summary-meta">${(objectEntries.length + idEntiteEntries.length + collectionEntries.length) > 0 ? esc(t('object')) : ''}</span></summary><div class="section-body">`;

        html += renderPrimitiveGroup(primitiveEntries);

        // Render IDEntite sub-objects inline (multicolumn, no separate header)
        idEntiteEntries.forEach((entry) => {
            html += renderInlineIdEntiteSection(entry.key, entry.value);
        });

        objectEntries.forEach((entry) => {
            html += renderNodeSection(entry.key, entry.value, level + 1);
        });

        collectionEntries.forEach((entry) => {
            html += renderCollectionSection(entry.key, entry.value, level + 1);
        });

        html += '</div></details>';
        return html;
    }

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
            // Layout-driven mode: keep original header
            html += `<div class="detail-header"><h2>${esc(rec.entity)}</h2><div class="detail-subtitle">ID: ${esc(rec.id || '\u2014')} \u2022 ${esc(t('record'))} #${rec.pos}</div></div>`;
            html += '<div class="detail-scroll">';
            html += renderLayoutDetail(rec.raw, DETAIL_LAYOUT);
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
            html += '<div class="detail-hero-entity">' + esc(typeof entityName !== 'undefined' && entityName ? entityName : rec.entity) + '</div>';
            html += '<div class="detail-hero-title">' + esc(rec.summary || rec.entity) + '</div>';
            if (subtitleFields.length > 0) {
                html += '<div class="detail-hero-subtitle">';
                html += subtitleFields.map((f) => '<strong>' + esc(f.label) + ':</strong> ' + esc(f.value)).join(' &nbsp;\u2022&nbsp; ');
                html += '</div>';
            }
            html += '<div class="detail-hero-meta">';
            if (rec.id) html += '<span class="badge badge-id">ID: ' + esc(rec.id) + '</span>';
            html += '<span>' + esc(t('record')) + ' #' + rec.pos + '</span>';
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
                    if (lastPart === '_summary') return;
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
                const allObjectEntries = entries.filter((e) => e.type === 'object').sort((a, b) => String(a.key || '').localeCompare(String(b.key || '')));
                const collectionEntries = entries.filter((e) => e.type === 'collection').sort((a, b) => String(a.key || '').localeCompare(String(b.key || '')));

                // Split object entries: IDEntite non-embedding fields rendered inline in the
                // details section; all other objects get their own collapsible section.
                const idEntiteObjectEntries = allObjectEntries.filter((e) => idEntiteFieldSet.has(normalizeSchemaPath(e.key)));
                const objectEntries = allObjectEntries.filter((e) => !idEntiteFieldSet.has(normalizeSchemaPath(e.key)));

                if (filteredPrimitives.length > 0 || idEntiteObjectEntries.length > 0) {
                    let sectionTitle = t('details');
                    html += `<details class="detail-section" open><summary><span class="summary-title">${esc(sectionTitle)}</span></summary><div class="section-body">`;
                    html += renderPrimitiveGroup(filteredPrimitives);
                    idEntiteObjectEntries.forEach((entry) => {
                        html += renderInlineIdEntiteSection(entry.key, entry.value);
                    });
                    html += '</div></details>';
                }

                objectEntries.forEach((entry) => {
                    html += renderNodeSection(entry.key, entry.value, 1);
                });
                collectionEntries.forEach((entry) => {
                    html += renderCollectionSection(entry.key, entry.value, 1);
                });
            } else {
                html += renderNodeSection(rec.entity, rawData, 0);
            }

            html += '</div>';
        }

        detailContainer.innerHTML = html;
        bindTabEvents();
    }

    /* ── Layout-driven detail renderer ──────────────────────────── */

    function resolveFieldValue(data, ref) {
        if (!data || !ref) return null;
        return ref.split('.').reduce(function (obj, key) {
            if (obj == null) return null;
            if (Array.isArray(obj) && obj.length === 1) obj = obj[0];
            return (typeof obj === 'object') ? obj[key] : null;
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
            if (bv === 'true') return '<span class="badge badge-true">' + esc(tv) + '</span>';
            if (bv === 'false') return '<span class="badge badge-false">' + esc(fv) + '</span>';
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
            html += renderLayoutNode(data, layout[i]);
        }
        return html;
    }

    function renderLayoutNode(data, node) {
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
                if (collapsible) {
                    return '<details class="detail-section" open><summary><span class="layout-section-title"' + titleAttr + '>'
                        + esc(p.title || '') + '</span></summary><div class="section-body">'
                        + renderLayoutChildren(data, children) + '</div></details>';
                }
                return '<div class="detail-section"><div class="section-body">'
                    + (p.title ? '<div class="layout-section-title" style="padding:8px 12px;' + titleInline + '">' + esc(p.title) + '</div>' : '')
                    + renderLayoutChildren(data, children) + '</div></div>';
            }
            case 'columns': {
                var sizes = (p.sizes || '').split(',').map(function (s) { return s.trim() + '%'; });
                var gridCols = sizes.join(' ');
                return '<div class="layout-columns" style="grid-template-columns:' + gridCols + '">'
                    + renderLayoutChildren(data, children) + '</div>';
            }
            case 'column':
                return '<div class="layout-column">' + renderLayoutChildren(data, children) + '</div>';

            case 'field': {
                var val = resolveFieldValue(data, p.ref);
                var label = p.label || displayFieldLabel(p.ref);
                var formatted = fmtValueWithFormat(val, p.format, p.ref);
                return '<div class="field-row' + styleCls + '"' + styleAttr + '><div class="field-label">' + esc(label)
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
                if (!Array.isArray(items) || items.length === 0) {
                    return '<div class="field-row"><div class="field-label">' + esc(displayFieldLabel(p.ref))
                        + '</div><div class="field-value" style="color:var(--c-text-muted)">\u2014</div></div>';
                }
                var cols = (p.columns || '').split(',').map(function (s) { return s.trim(); }).filter(Boolean);
                var widths = (p.widths || '').split(',').map(function (s) { return s.trim(); });
                var colTitles = (p.columnTitles || '').split(',').map(function (s) { return s.trim(); });
                if (cols.length === 0) {
                    var first = items[0];
                    if (first && typeof first === 'object') cols = Object.keys(first);
                }
                var headerStyle = '';
                if (p.color) headerStyle += 'color:' + esc(p.color) + ';';
                if (p.hilite) headerStyle += 'background-color:' + esc(p.hilite) + ';';
                var thtml = '<details class="detail-section" open><summary' + (headerStyle ? ' style="' + headerStyle + '"' : '') + '><span class="summary-title">'
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
                        var cellVal = (item && typeof item === 'object') ? item[col] : item;
                        thtml += '<td>' + fmtValue(cellVal, col) + '</td>';
                    });
                    thtml += '</tr>';
                });
                thtml += '</tbody></table></div></div></details>';
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
                    html += '<div class="tab-panel' + (idx === 0 ? ' active' : '') + '" data-tab-id="' + tabId + '-' + idx + '">'
                        + renderLayoutChildren(data, child.children || []) + '</div>';
                });
                html += '</div>';
                return html;
            }
            case 'tab':
                return renderLayoutChildren(data, children);

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

    function renderLayoutChildren(data, children) {
        var html = '';
        for (var i = 0; i < children.length; i++) {
            html += renderLayoutNode(data, children[i]);
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

    function applyLanguage() {
        searchTitle.textContent = t('search');
        columnsBtn.textContent = t('columns');
        addConditionBtn.textContent = t('addCondition');
        clearSearchBtn.textContent = t('clear');
        searchBtn.textContent = t('apply');
        rowsLabel.textContent = t('rows');
        prevBtn.textContent = t('prev');
        nextBtn.textContent = t('next');
        detailCloseBtn.setAttribute('aria-label', t('close'));

        document.querySelectorAll('.search-condition').forEach((row) => {
            const fieldSel = row.querySelector('.field-select');
            const fieldFilter = row.querySelector('.field-filter');
            const opSel = row.querySelector('.operator-select');
            const valInput = row.querySelector('.value-input');
            const removeBtn = row.querySelector('.remove-btn');
            const logicLabel = row.querySelector('.logic-label');

            if (fieldFilter) {
                fieldFilter.placeholder = t('fieldFilter');
                const current = fieldSel ? fieldSel.value : '__all';
                const term = fieldFilter.value || '';

                const pool = term.trim()
                    ? discoveredFields.filter((f) => (f.label || f.path).toLowerCase().includes(term.toLowerCase()) || f.path.toLowerCase().includes(term.toLowerCase()))
                    : discoveredFields.slice();

                let html = `<option value="__all">${esc(t('allFields'))}</option>`;
                pool.forEach((f) => {
                    html += `<option value="${esc(f.path)}">${esc(f.label)}</option>`;
                });
                if (fieldSel) {
                    fieldSel.innerHTML = html;
                    if (Array.from(fieldSel.options).some((opt) => opt.value === current)) {
                        fieldSel.value = current;
                    }
                }
            }

            if (opSel) {
                const selected = opSel.value;
                const fieldType = getFieldType(fieldSel ? fieldSel.value : '__all');
                opSel.innerHTML = buildOperatorOptions(fieldType);
                if (selected && Array.from(opSel.options).some((opt) => opt.value === selected)) {
                    opSel.value = selected;
                }
            }

            if (valInput) {
                valInput.placeholder = t('value');
                updateValueInput(opSel, valInput);
            }

            if (logicLabel) logicLabel.title = t('toggleLogic');
            if (removeBtn) removeBtn.title = t('remove');
        });

        renderColumnsMenu();
        renderResults();
    }

    function initialize() {
        try {
            parsePayload();
            buildDiscoveredFields();
            renderColumnsMenu();
            addCondition();
            applyLanguage();
            renderResults();
            resultsCount.textContent = allRecords.length + ' ' + t('resultsTotal');
        } catch (err) {
            resultsCount.textContent = `${t('err')}: ${err.message || 'Unknown'}`;
            resultsBody.innerHTML = `<tr><td colspan="3"><div class="results-empty"><p>${esc(err.message || t('noPayload'))}</p></div></td></tr>`;
        }
    }

    addConditionBtn.addEventListener('click', addCondition);
    clearSearchBtn.addEventListener('click', () => {
        conditionsContainer.innerHTML = '';
        searchApplied = false;
        filteredRecords = allRecords.slice();
        currentPage = 1;
        addCondition();
        renderResults();
        selectedRecordKey = null;
        closeDetailOverlay();
    });
    searchBtn.addEventListener('click', applySearch);
    pageSizeSelect.addEventListener('change', () => { currentPage = 1; renderResults(); });
    prevBtn.addEventListener('click', () => { if (currentPage > 1) { currentPage--; renderResults(); } });
    nextBtn.addEventListener('click', () => {
        const totalPages = Math.max(1, Math.ceil(filteredRecords.length / getPageSize()));
        if (currentPage < totalPages) { currentPage++; renderResults(); }
    });
    languageSelect.addEventListener('change', () => { currentLanguage = languageSelect.value === 'en' ? 'en' : 'fr'; applyLanguage(); });
    columnsBtn.addEventListener('click', () => columnsMenu.classList.toggle('open'));
    document.addEventListener('click', (e) => {
        if (!columnsMenu.contains(e.target) && !columnsBtn.contains(e.target)) {
            columnsMenu.classList.remove('open');
        }
    });
    detailCloseBtn.addEventListener('click', closeDetailOverlay);
    if (detailPrevBtn) detailPrevBtn.addEventListener('click', () => navigateDetail(-1));
    if (detailNextBtn) detailNextBtn.addEventListener('click', () => navigateDetail(1));
    detailOverlay.addEventListener('click', (e) => { if (e.target === detailOverlay) closeDetailOverlay(); });
    detailContainer.addEventListener('click', (e) => {
        // Handle collection table header sorting
        const th = e.target.closest('th[data-sort-col]');
        if (th) {
            const collectionId = th.getAttribute('data-collection-id');
            const sortCol = th.getAttribute('data-sort-col');
            const state = collectionViewState[collectionId];
            if (state) {
                if (state.sortCol === sortCol) {
                    state.sortDir = state.sortDir === 'asc' ? 'desc' : 'asc';
                } else {
                    state.sortCol = sortCol;
                    state.sortDir = 'asc';
                }
                state.rows.sort((a, b) => {
                    const va = String(a[sortCol] ?? '');
                    const vb = String(b[sortCol] ?? '');
                    const na = parseFloat(va), nb = parseFloat(vb);
                    let cmp = (!isNaN(na) && !isNaN(nb)) ? na - nb : va.localeCompare(vb, 'fr', { sensitivity: 'base' });
                    return state.sortDir === 'desc' ? -cmp : cmp;
                });
                state.page = 1;
                const body = detailContainer.querySelector(`#collection-body-${collectionId}`);
                const pgInfo = detailContainer.querySelector(`#collection-page-${collectionId}`);
                const totalPages = Math.max(1, Math.ceil(state.rows.length / state.pageSize));
                if (body) body.innerHTML = renderCollectionTableBody(state);
                if (pgInfo) pgInfo.textContent = `${state.page} / ${totalPages}`;
                // Update sort indicators
                const headerRow = th.closest('tr');
                if (headerRow) {
                    headerRow.querySelectorAll('.sort-indicator').forEach((si) => { si.textContent = ''; si.classList.remove('active'); });
                }
                const indicator = th.querySelector('.sort-indicator');
                if (indicator) { indicator.textContent = state.sortDir === 'asc' ? ' \u25B2' : ' \u25BC'; indicator.classList.add('active'); }
                // Update pager buttons
                const prevButton = detailContainer.querySelector(`button[data-collection-action="prev"][data-collection-id="${collectionId}"]`);
                const nextButton = detailContainer.querySelector(`button[data-collection-action="next"][data-collection-id="${collectionId}"]`);
                if (prevButton) prevButton.disabled = state.page <= 1;
                if (nextButton) nextButton.disabled = state.page >= totalPages;
            }
            return;
        }

        const btn = e.target.closest('button[data-collection-action]');
        if (!btn) return;

        const action = btn.getAttribute('data-collection-action');
        const collectionId = btn.getAttribute('data-collection-id');
        const state = collectionViewState[collectionId];
        if (!state) return;

        const totalPages = Math.max(1, Math.ceil(state.rows.length / state.pageSize));

        if (action === 'prev' && state.page > 1) {
            state.page -= 1;
        }
        if (action === 'next' && state.page < totalPages) {
            state.page += 1;
        }

        const body = detailContainer.querySelector(`#collection-body-${collectionId}`);
        const pgInfo = detailContainer.querySelector(`#collection-page-${collectionId}`);
        const prevButton = detailContainer.querySelector(`button[data-collection-action="prev"][data-collection-id="${collectionId}"]`);
        const nextButton = detailContainer.querySelector(`button[data-collection-action="next"][data-collection-id="${collectionId}"]`);

        if (body) body.innerHTML = renderCollectionTableBody(state);
        if (pgInfo) pgInfo.textContent = `${state.page} / ${totalPages}`;
        if (prevButton) prevButton.disabled = state.page <= 1;
        if (nextButton) nextButton.disabled = state.page >= totalPages;
    });
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') closeDetailOverlay();
        if (detailOverlay.classList.contains('open')) {
            if (e.key === 'ArrowLeft') { navigateDetail(-1); e.preventDefault(); }
            if (e.key === 'ArrowRight') { navigateDetail(1); e.preventDefault(); }
        }
    });

    initialize();
})();
// ─────────────────────────────────────────────────────────────────────────────
