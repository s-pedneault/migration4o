// ─── Navigation sidebar ───────────────────────────────────────────────────
(function () {
    var navSidebar = document.getElementById('navSidebar');
    var navToggleBtn = document.getElementById('navToggleBtn');
    var navSidebarScroll = document.getElementById('navSidebarScroll');
    if (!navSidebar || !navToggleBtn || !navSidebarScroll) return;

    // ── SVG icon helpers ──────────────────────────────────────────────────
    var SVG_NS = 'http://www.w3.org/2000/svg';

    // Generic Lucide-style outline icon
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

    // Tabler filled icon: first path is always the transparent bounding box
    // (fill="none" stroke="none"); all other paths inherit fill="currentColor".
    function tablerIcon(paths, size) {
        size = size || 16;
        var svg = document.createElementNS(SVG_NS, 'svg');
        svg.setAttribute('width', size);
        svg.setAttribute('height', size);
        svg.setAttribute('viewBox', '0 0 24 24');
        svg.setAttribute('fill', 'currentColor');
        svg.setAttribute('class', 'tabler-icon-filled');
        paths.forEach(function (d, i) {
            var p = document.createElementNS(SVG_NS, 'path');
            p.setAttribute('stroke', 'none');
            p.setAttribute('d', d);
            if (i === 0) p.setAttribute('fill', 'none');
            svg.appendChild(p);
        });
        return svg;
    }

    // Icon definitions
    var ICONS = {
        // Tabler folder (outline)
        folder: function () {
            return tablerIcon([
                'M0 0h24v24H0z',
                'M9 3a1 1 0 0 1 .608 .206l.1 .087l2.706 2.707h6.586a3 3 0 0 1 2.995 2.824l.005 .176v8a3 3 0 0 1 -2.824 2.995l-.176 .005h-14a3 3 0 0 1 -2.995 -2.824l-.005 -.176v-11a3 3 0 0 1 2.824 -2.995l.176 -.005h4z'
            ]);
        },
        // Tabler folder-open (outline)
        folderOpen: function () {
            return tablerIcon([
                'M0 0h24v24H0z',
                'M9 3a1 1 0 0 1 .608 .206l.1 .087l2.706 2.707h6.586a3 3 0 0 1 2.995 2.824l.005 .176v8a3 3 0 0 1 -2.824 2.995l-.176 .005h-14a3 3 0 0 1 -2.995 -2.824l-.005 -.176v-11a3 3 0 0 1 2.824 -2.995l.176 -.005h4z'
            ]);
        },
        // Chevron right
        chevron: function () {
            return svgIcon(['M9 18l6-6-6-6'], 14);
        },
        // Hamburger / sidebar toggle
        menu: function () {
            return svgIcon(['M3 12h18', 'M3 6h18', 'M3 18h18']);
        },
        // Collapse sidebar
        panelLeft: function () {
            return svgIcon(['M3 3h18v18H3z', 'M9 3v18']);
        },
        // Tabler database (filled) — used for leaf class items
        database: function () {
            return tablerIcon([
                'M0 0h24v24H0z',
                'M3 15.731c1.968 1.507 5.234 2.269 9 2.269c3.76 0 7.025 -.76 9 -2.252v2.252c0 2.425 -3.895 3.936 -8.693 3.998l-.307 .002c-4.938 0 -9 -1.523 -9 -4z',
                'M3 9.731c1.968 1.507 5.234 2.269 9 2.269c3.76 0 7.025 -.76 9 -2.252v2.252c0 2.477 -4.062 4 -9 4c-4.798 0 -8.77 -1.438 -8.979 -3.795l-.016 -.101l-.005 -.104z',
                'M12 2c1.041 0 2.044 .068 2.977 .198l.469 .071q .84 .14 1.586 .348l.44 .131l.075 .024a11 11 0 0 1 .805 .3l.199 .086q .535 .242 .967 .53q .165 .11 .313 .225a3.8 3.8 0 0 1 .669 .668l.091 .128q .07 .105 .129 .211l.07 .139q .163 .35 .2 .73l.01 .211c0 2.477 -4.062 4 -9 4c-4.798 0 -8.77 -1.438 -8.979 -3.795a1 1 0 0 1 -.021 -.205l.005 -.104l.016 -.1c.205 -2.306 4.01 -3.733 8.667 -3.794z'
            ]);
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

    function renderNavItem(item, depth, forceOpen) {
        var hasCh = item.children && item.children.length > 0;
        var indent = depth > 0 ? (depth * 14) : 0;

        if (!hasCh) {
            // ── Leaf item (database icon) ─────────────────────────────────
            var isCurrent = isCurrentPage(item.href);
            var a = document.createElement('a');
            a.className = 'nav-item-link' + (isCurrent ? ' nav-current' : '');
            a.href = item.href || '#';
            a.title = item.label || '';

            // Shift background to start at the parent connector line, same as sub-groups
            var leafIndent = (depth - 1) * 19;
            if (leafIndent > 0) a.style.marginLeft = leafIndent + 'px';

            var iconSpan = document.createElement('span');
            iconSpan.className = 'nav-item-icon';
            iconSpan.style.paddingLeft = '6px';
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
            var isOpen = hasCurrent || !!forceOpen;
            // Always auto-cycle the palette class as default; inline styles override below
            var colorIdx = tileColorIndex % 8;
            tileColorIndex++;
            var colorClass = 'nav-tile-c' + colorIdx;

            var tileDiv = document.createElement('div');
            tileDiv.className = 'nav-module-tile ' + colorClass + (isOpen ? ' open' : '');

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
            childDiv.className = 'nav-group-children nav-tile-children' + (isOpen ? ' open' : '');
            (item.children || []).forEach(function (child) {
                childDiv.appendChild(renderNavItem(child, depth + 1, forceOpen));
            });

            tileBtn.tabIndex = -1;
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
        var isOpen = hasCurrent || !!forceOpen;
        var div = document.createElement('div');

        var btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'nav-group-toggle' + (isOpen ? ' open' : '');
        btn.title = item.label || '';

        // Shift the button's left edge to the connector line of its parent level.
        // All nav-group-children share the same absolute left wall as nav-tile-children,
        // so cumulative offset = (depth - 1) * 19px.
        var subIndent = (depth - 1) * 19;
        if (subIndent > 0) btn.style.marginLeft = subIndent + 'px';

        // Set border-radius inline to guarantee no left rounding; open state
        // adds a bottom-right corner for a soft close.
        btn.style.borderRadius = isOpen ? '0 0 6px 0' : '0';

        var iconSpan = document.createElement('span');
        iconSpan.className = 'nav-group-icon';
        iconSpan.style.paddingLeft = '6px';
        iconSpan.appendChild(isOpen ? ICONS.folderOpen() : ICONS.folder());

        var labelSpan = document.createElement('span');
        labelSpan.className = 'nav-group-label';
        labelSpan.textContent = item.label || '';

        btn.appendChild(iconSpan);
        btn.appendChild(labelSpan);

        var childDiv = document.createElement('div');
        childDiv.className = 'nav-group-children' + (isOpen ? ' open' : '');
        (item.children || []).forEach(function (child) {
            childDiv.appendChild(renderNavItem(child, depth + 1, forceOpen));
        });

        btn.tabIndex = -1;
        btn.addEventListener('click', function () {
            var nowOpen = btn.classList.toggle('open');
            childDiv.classList.toggle('open');
            btn.style.borderRadius = nowOpen ? '0 0 6px 0' : '0';
            iconSpan.innerHTML = '';
            iconSpan.appendChild(nowOpen ? ICONS.folderOpen() : ICONS.folder());
        });

        div.appendChild(btn);
        div.appendChild(childDiv);
        return div;
    }

    // ── Filter helpers ────────────────────────────────────────────────────
    function filterTree(items, q) {
        var out = [];
        for (var i = 0; i < items.length; i++) {
            var item = items[i];
            if (!item.children || item.children.length === 0) {
                if (item.label && item.label.toLowerCase().indexOf(q) >= 0) out.push(item);
            } else {
                var fc = filterTree(item.children, q);
                if (fc.length > 0) out.push({ label: item.label, icon: item.icon, tileBg: item.tileBg, tileText: item.tileText, tileIcon: item.tileIcon, tileFontSize: item.tileFontSize, children: fc });
            }
        }
        return out;
    }

    function renderAll(items, forceOpen) {
        tileColorIndex = 0;
        navSidebarScroll.innerHTML = '';
        if (!items || items.length === 0) {
            navSidebarScroll.innerHTML = '<div style="padding:16px;font-size:12px;color:var(--c-text-muted);text-align:center;">No results</div>';
            return;
        }
        items.forEach(function (item) {
            navSidebarScroll.appendChild(renderNavItem(item, 0, forceOpen));
        });
        if (!forceOpen) {
            var cur = navSidebarScroll.querySelector('.nav-current');
            if (cur) setTimeout(function () { cur.scrollIntoView({ block: 'center', behavior: 'smooth' }); }, 100);
        }
    }

    // ── Render navigation ─────────────────────────────────────────────────
    var navItems = (typeof NAV_ITEMS !== 'undefined') ? NAV_ITEMS : [];
    if (navItems && navItems.length > 0) {
        renderAll(navItems, false);
    } else {
        navSidebarScroll.innerHTML = '<div style="padding:16px;font-size:12px;color:var(--c-text-muted);text-align:center;">No modules</div>';
    }

    // ── Search bar ────────────────────────────────────────────────────────
    var searchWrap = document.createElement('div');
    searchWrap.className = 'sidebar-search';
    searchWrap.id = 'navSearchWrap';

    var searchIconSvg = document.createElementNS(SVG_NS, 'svg');
    searchIconSvg.setAttribute('class', 'nav-search-icon');
    searchIconSvg.setAttribute('width', '14'); searchIconSvg.setAttribute('height', '14');
    searchIconSvg.setAttribute('viewBox', '0 0 24 24'); searchIconSvg.setAttribute('fill', 'none');
    searchIconSvg.setAttribute('stroke', 'currentColor'); searchIconSvg.setAttribute('stroke-width', '2.2');
    searchIconSvg.setAttribute('stroke-linecap', 'round'); searchIconSvg.setAttribute('stroke-linejoin', 'round');
    var sc = document.createElementNS(SVG_NS, 'circle');
    sc.setAttribute('cx', '11'); sc.setAttribute('cy', '11'); sc.setAttribute('r', '8');
    var sl = document.createElementNS(SVG_NS, 'path'); sl.setAttribute('d', 'M21 21l-4.35-4.35');
    searchIconSvg.appendChild(sc); searchIconSvg.appendChild(sl);

    var searchInput = document.createElement('input');
    searchInput.type = 'text';
    searchInput.className = 'nav-search-input';
    searchInput.placeholder = 'Filter classes\u2026';
    searchInput.autocomplete = 'off';
    searchInput.setAttribute('spellcheck', 'false');

    var clearBtn = document.createElement('button');
    clearBtn.type = 'button';
    clearBtn.className = 'nav-search-clear';
    clearBtn.title = 'Clear';
    clearBtn.innerHTML = '&#10005;';
    clearBtn.style.opacity = '0';
    clearBtn.style.pointerEvents = 'none';

    searchWrap.appendChild(searchIconSvg);
    searchWrap.appendChild(searchInput);
    searchWrap.appendChild(clearBtn);
    navSidebarScroll.parentNode.insertBefore(searchWrap, navSidebarScroll);

    var NAV_SEARCH_I18N = { fr: 'Rechercher...', en: 'Find...' };
    function updateSearchPlaceholder() {
        var langEl = document.getElementById('languageSelect');
        var lang = (langEl && langEl.value === 'en') ? 'en' : 'fr';
        searchInput.placeholder = NAV_SEARCH_I18N[lang];
    }
    updateSearchPlaceholder();
    var langSelectEl = document.getElementById('languageSelect');
    if (langSelectEl) langSelectEl.addEventListener('change', updateSearchPlaceholder);

    // ── Keyboard navigation helpers ──────────────────────────────────
    function getLeaves() {
        return Array.prototype.slice.call(navSidebarScroll.querySelectorAll('a.nav-item-link'));
    }

    function setKbFocus(el) {
        getLeaves().forEach(function (l) { l.classList.remove('nav-item-kbfocus'); });
        if (el) {
            el.classList.add('nav-item-kbfocus');
            el.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
        }
    }

    function focusLeafAt(idx) {
        var leaves = getLeaves();
        if (!leaves.length) return;
        idx = ((idx % leaves.length) + leaves.length) % leaves.length;
        setKbFocus(leaves[idx]);
    }

    function currentLeafIdx() {
        var leaves = getLeaves();
        for (var i = 0; i < leaves.length; i++) {
            if (leaves[i].classList.contains('nav-item-kbfocus')) return i;
        }
        return -1;
    }

    searchInput.addEventListener('keydown', function (e) {
        var leaves = getLeaves();
        if (!leaves.length) return;
        if (e.key === 'Tab' || e.key === 'ArrowDown') {
            e.preventDefault();
            var idx = currentLeafIdx();
            focusLeafAt(idx + 1 < leaves.length ? idx + 1 : 0);
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            focusLeafAt(leaves.length - 1);
        } else if (e.key === 'Escape') {
            setKbFocus(null);
        } else if (e.key === 'Enter') {
            var idx2 = currentLeafIdx();
            if (idx2 >= 0) { e.preventDefault(); window.location.href = leaves[idx2].href; }
        }
    });

    navSidebarScroll.addEventListener('keydown', function (e) {
        var leaves = getLeaves();
        if (!leaves.length) return;
        var idx = currentLeafIdx();
        if (idx < 0) return;
        if (e.key === 'ArrowDown' || e.key === 'Tab' && !e.shiftKey) {
            e.preventDefault();
            if (idx + 1 < leaves.length) focusLeafAt(idx + 1);
        } else if (e.key === 'ArrowUp' || e.key === 'Tab' && e.shiftKey) {
            e.preventDefault();
            if (idx > 0) { focusLeafAt(idx - 1); }
            else { setKbFocus(null); searchInput.focus(); }
        } else if (e.key === 'Enter') {
            e.preventDefault();
            window.location.href = leaves[idx].href;
        } else if (e.key === 'Escape') {
            setKbFocus(null);
            searchInput.focus();
        }
    });

    var searchTimer = null;
    function applyFilter() {
        var q = searchInput.value.trim().toLowerCase();
        if (q === '') {
            clearBtn.style.opacity = '0';
            clearBtn.style.pointerEvents = 'none';
            renderAll(navItems, false);
        } else {
            clearBtn.style.opacity = '1';
            clearBtn.style.pointerEvents = '';
            renderAll(filterTree(navItems, q), true);
        }
    }
    searchInput.addEventListener('input', function () {
        clearTimeout(searchTimer);
        searchTimer = setTimeout(applyFilter, 80);
    });
    clearBtn.addEventListener('click', function () {
        searchInput.value = '';
        applyFilter();
        searchInput.focus();
    });

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
    let currentLanguage = '__EXPORT_LANGUAGE__';
    let selectedColumns = (typeof DEFAULT_COLUMNS !== 'undefined' && Array.isArray(DEFAULT_COLUMNS) && DEFAULT_COLUMNS.length > 0)
        ? DEFAULT_COLUMNS.slice()
        : ['__summary'];
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
            boolTrue: 'Oui', boolFalse: 'Non',
            logicAnd: 'ET', logicOr: 'OU',
            backRefs: 'Références',
            backRefsCapped: 'Affichage limité aux 25 premières références',
            openLinkedRecord: 'Ouvrir l\'enregistrement lié'
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
            boolTrue: 'Yes', boolFalse: 'No',
            logicAnd: 'AND', logicOr: 'OR',
            backRefs: 'References',
            backRefsCapped: 'Showing first 25 references only',
            openLinkedRecord: 'Open linked record'
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
    function tLogic(op) { return op === 'OR' ? t('logicOr') : t('logicAnd'); }
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

    // Build a map from field path → target entity destination name, for cross-page deep-links
    const pointsToByPath = {};
    (function collectPointsToFields(fields) {
        if (!Array.isArray(fields)) return;
        fields.forEach(function (field) {
            if (field && field.idEntite && field.pointsTo) {
                var p = normalizeSchemaPath(field.path || field.name || '');
                var n = normalizeSchemaPath(field.name || '');
                if (p) pointsToByPath[p] = field.pointsTo;
                if (n && !pointsToByPath[n]) pointsToByPath[n] = field.pointsTo;
            }
            if (field && Array.isArray(field.children)) collectPointsToFields(field.children);
        });
    })(schemaFields);

    // Build a map from entity destination name → nav leaf href, derived from NAV_ITEMS
    const navHrefByDestName = {};
    (function buildNavHrefMap(items) {
        if (!Array.isArray(items)) return;
        items.forEach(function (item) {
            if (item.href) {
                var fname = item.href.replace(/\?.*$/, '').split('/').pop() || '';
                var base = fname.replace(/\.html$/i, '');
                if (base) navHrefByDestName[base] = item.href;
            }
            if (item.children) buildNavHrefMap(item.children);
        });
    })((typeof NAV_ITEMS !== 'undefined') ? NAV_ITEMS : []);

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

        // Reserved properties: promote _id, _summary, _preview, _label as fields
        if (value._id !== undefined) appendField(out, `${path}._id`, value._id);
        if (value._summary !== undefined) appendField(out, `${path}._summary`, value._summary);
        if (value._preview !== undefined) appendField(out, `${path}._preview`, value._preview);
        if (value._label !== undefined) appendField(out, path, value._label);

        Object.entries(value).forEach(([k, v]) => {
            if (k.startsWith('_')) return;
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
        const serverSummary = raw && raw._summary ? raw._summary : null;
        const serverPreview = raw && raw._preview ? raw._preview : null;
        if (raw && typeof raw === 'object') {
            Object.entries(raw).forEach(([k, v]) => {
                if (k.startsWith('_')) return;
                flattenValue(v, k, fields);
            });
        }
        const id = (raw && raw._id) || pickBest(fields, ['id', 'identifiant', 'numero', 'code']) || '';
        return {
            key: `${entity}#${pos}`,
            pos,
            entity,
            id: String(id || ''),
            fields,
            raw,
            summary: serverSummary || '',
            preview: serverPreview || null
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
        if (root.objects && typeof root.objects === 'object') {
            if (Array.isArray(root.objects)) {
                root.objects.forEach((item) => {
                    if (!item || typeof item !== 'object') return;
                    // Direct objects with _class discriminator (clean JS format)
                    if (item._class) {
                        acc.push(buildRecord(item._class, item, acc.length + 1));
                    } else {
                        // Legacy wrapper: {EntityName: value}
                        Object.entries(item).forEach(([entity, value]) => collectFromNamedArray(entity, value, acc));
                    }
                });
            } else {
                Object.entries(root.objects).forEach(([entity, value]) => collectFromNamedArray(entity, value, acc));
            }
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

    // ── Hierarchical field picker ─────────────────────────────────────────

    /**
     * Build a tree from the flat discoveredFields list.
     * Dot-separated paths become nested groups; leaves are selectable fields.
     */
    function _buildTreeLevel(items, depth) {
        const leaves = [];
        const groups = {};
        items.forEach(function (item) {
            if (depth >= item._seg.length - 1) {
                // Use the pre-resolved label from discoveredFields (schema title with accents)
                leaves.push({ path: item.path, label: item._label, type: item.type });
            } else {
                const key = item._seg[depth];
                if (!groups[key]) groups[key] = [];
                groups[key].push(item);
            }
        });
        const nodes = [];
        Object.keys(groups).sort(function (a, b) {
            return (schemaTitleForPath(a) || humanizeFieldName(a)).localeCompare(schemaTitleForPath(b) || humanizeFieldName(b));
        }).forEach(function (key) {
            var children = _buildTreeLevel(groups[key], depth + 1);
            nodes.push({ label: schemaTitleForPath(key) || humanizeFieldName(key), key: key, children: children });
        });
        leaves.sort(function (a, b) { return a.label.localeCompare(b.label); });
        leaves.forEach(function (l) { nodes.push(l); });
        return nodes;
    }

    function buildFieldTree(fields) {
        return _buildTreeLevel(fields.map(function (f) {
            // Resolve the leaf-only label for display: strip parent prefix from the
            // full schema title, or look up the bare segment name.
            var seg = f.path.split('.');
            var leafKey = seg[seg.length - 1];
            var leafLabel = schemaTitleForPath(leafKey) || humanizeFieldName(leafKey);
            return { path: f.path, type: f.type, _seg: seg, _label: leafLabel };
        }), 0);
    }

    function _countLeaves(nodes) {
        var c = 0;
        nodes.forEach(function (n) { c += n.children ? _countLeaves(n.children) : 1; });
        return c;
    }

    function _filterTree(nodes, q) {
        var out = [];
        nodes.forEach(function (n) {
            if (n.children) {
                var filtered = _filterTree(n.children, q);
                if (filtered.length > 0) {
                    out.push({ label: n.label, key: n.key, children: filtered, _open: true });
                } else if (n.label.toLowerCase().indexOf(q) >= 0) {
                    out.push({ label: n.label, key: n.key, children: n.children, _open: true });
                }
            } else {
                if (n.label.toLowerCase().indexOf(q) >= 0 || n.path.toLowerCase().indexOf(q) >= 0) {
                    out.push(n);
                }
            }
        });
        return out;
    }

    /**
     * Create a custom hierarchical field picker that replaces the flat select.
     * Returns { element, getValue, setValue, refresh, destroy }.
     */
    function createFieldPicker(onFieldChange) {
        var currentValue = '__all';
        var isOpen = false;

        var picker = document.createElement('div');
        picker.className = 'field-picker';

        var btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'field-picker-btn';

        var btnText = document.createElement('span');
        btnText.className = 'field-picker-text';
        btnText.textContent = t('allFields');

        var btnChevron = document.createElement('span');
        btnChevron.className = 'field-picker-chevron';
        btnChevron.textContent = '\u25be';

        btn.appendChild(btnText);
        btn.appendChild(btnChevron);

        var popup = document.createElement('div');
        popup.className = 'field-picker-popup';

        var searchWrap = document.createElement('div');
        searchWrap.className = 'fp-search-wrap';
        var searchInput = document.createElement('input');
        searchInput.type = 'text';
        searchInput.className = 'fp-search';
        searchInput.placeholder = t('fieldFilter');
        searchInput.autocomplete = 'off';
        searchInput.setAttribute('spellcheck', 'false');
        searchWrap.appendChild(searchInput);

        var listDiv = document.createElement('div');
        listDiv.className = 'fp-list';

        popup.appendChild(searchWrap);
        popup.appendChild(listDiv);
        picker.appendChild(btn);
        picker.appendChild(popup);

        /** Build display text for a selected field path, showing the full parent chain. */
        function labelForValue(v) {
            if (!v || v === '__all') return t('allFields');
            var seg = v.split('.');
            return seg.map(function (s) { return schemaTitleForPath(s) || humanizeFieldName(s); }).join(' \u203a ');
        }

        function selectField(path) {
            currentValue = path || '__all';
            btnText.textContent = labelForValue(currentValue);
            btn.title = labelForValue(currentValue);
            closePopup();
            if (onFieldChange) onFieldChange(currentValue);
        }

        function renderTree(nodes, container, depth) {
            nodes.forEach(function (node) {
                if (node.children) {
                    var group = document.createElement('div');
                    group.className = 'fpt-group';

                    var header = document.createElement('button');
                    header.type = 'button';
                    header.className = 'fpt-group-header';
                    header.style.paddingLeft = (10 + depth * 16) + 'px';

                    var chevron = document.createElement('span');
                    chevron.className = 'fpt-chevron';
                    chevron.textContent = node._open ? '\u25be' : '\u25b8';

                    var lbl = document.createElement('span');
                    lbl.className = 'fpt-group-label';
                    lbl.textContent = node.label;

                    var cnt = document.createElement('span');
                    cnt.className = 'fpt-group-count';
                    cnt.textContent = _countLeaves(node.children);

                    header.appendChild(chevron);
                    header.appendChild(lbl);
                    header.appendChild(cnt);

                    var body = document.createElement('div');
                    body.className = 'fpt-group-body' + (node._open ? ' open' : '');

                    renderTree(node.children, body, depth + 1);

                    header.addEventListener('click', function () {
                        var wasOpen = body.classList.contains('open');
                        body.classList.toggle('open');
                        chevron.textContent = wasOpen ? '\u25b8' : '\u25be';
                    });

                    group.appendChild(header);
                    group.appendChild(body);
                    container.appendChild(group);
                } else {
                    var item = document.createElement('button');
                    item.type = 'button';
                    item.className = 'fpt-item';
                    item.style.paddingLeft = (26 + depth * 16) + 'px';
                    item.setAttribute('data-path', node.path);
                    item.textContent = node.label;
                    if (node.path === currentValue) item.classList.add('selected');
                    item.addEventListener('click', function () { selectField(node.path); });
                    container.appendChild(item);
                }
            });
        }

        function refreshList(query) {
            listDiv.innerHTML = '';
            // "All fields" always on top
            var allItem = document.createElement('button');
            allItem.type = 'button';
            allItem.className = 'fpt-item fpt-item--all';
            allItem.textContent = t('allFields');
            if (currentValue === '__all') allItem.classList.add('selected');
            allItem.addEventListener('click', function () { selectField('__all'); });
            listDiv.appendChild(allItem);

            var tree = buildFieldTree(discoveredFields);
            var display = query ? _filterTree(tree, query.toLowerCase()) : tree;
            renderTree(display, listDiv, 0);
        }

        function openPopup() {
            isOpen = true;
            popup.classList.add('open');
            refreshList('');
            searchInput.value = '';
            setTimeout(function () { searchInput.focus(); }, 30);
        }

        function closePopup() {
            isOpen = false;
            popup.classList.remove('open');
        }

        btn.addEventListener('click', function (e) {
            e.stopPropagation();
            if (isOpen) closePopup(); else openPopup();
        });
        popup.addEventListener('click', function (e) { e.stopPropagation(); });

        var filterTimer;
        searchInput.addEventListener('input', function () {
            clearTimeout(filterTimer);
            filterTimer = setTimeout(function () { refreshList(searchInput.value.trim()); }, 100);
        });
        searchInput.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') { e.preventDefault(); closePopup(); }
        });

        var closeHandler = function () { if (isOpen) closePopup(); };
        document.addEventListener('click', closeHandler);

        return {
            element: picker,
            getValue: function () { return currentValue; },
            setValue: function (v) {
                currentValue = v || '__all';
                btnText.textContent = labelForValue(currentValue);
            },
            /** Re-translate labels after language change */
            refresh: function () {
                btnText.textContent = labelForValue(currentValue);
                searchInput.placeholder = t('fieldFilter');
            },
            destroy: function () { document.removeEventListener('click', closeHandler); }
        };
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

        const main = document.createElement('div');
        main.className = 'condition-main';

        // Logic label
        const logicLabel = document.createElement('span');
        logicLabel.className = 'logic-label' + (isFirst ? ' first' : '');
        logicLabel.title = t('toggleLogic');
        logicLabel.textContent = tLogic(globalLogicOperator);

        // Operator select
        const opSel = document.createElement('select');
        opSel.className = 'operator-select';
        opSel.innerHTML = buildOperatorOptions('_all');

        // Value input
        const valInput = document.createElement('input');
        valInput.type = 'text';
        valInput.className = 'value-input';
        valInput.placeholder = t('value');

        // Field picker (hierarchical)
        const picker = createFieldPicker(function (fieldPath) {
            opSel.innerHTML = buildOperatorOptions(getFieldType(fieldPath));
            updateValueInput(opSel, valInput);
        });

        // Remove button
        const removeBtn = document.createElement('button');
        removeBtn.type = 'button';
        removeBtn.className = 'remove-btn';
        removeBtn.title = t('remove');
        removeBtn.textContent = '\u00d7';

        main.appendChild(logicLabel);
        main.appendChild(picker.element);
        main.appendChild(opSel);
        main.appendChild(valInput);
        main.appendChild(removeBtn);
        row.appendChild(main);
        conditionsContainer.appendChild(row);

        // Store picker reference on the row for getConditions / applyLanguage
        row._fieldPicker = picker;

        opSel.addEventListener('change', () => updateValueInput(opSel, valInput));
        logicLabel.addEventListener('click', () => {
            if (logicLabel.classList.contains('first')) return;
            globalLogicOperator = globalLogicOperator === 'AND' ? 'OR' : 'AND';
            document.querySelectorAll('.logic-label:not(.first)').forEach((lbl) => lbl.textContent = tLogic(globalLogicOperator));
        });
        removeBtn.addEventListener('click', () => {
            picker.destroy();
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

        updateValueInput(opSel, valInput);
    }

    function getConditions() {
        return Array.from(conditionsContainer.querySelectorAll('.search-condition')).map((row) => ({
            field: row._fieldPicker ? row._fieldPicker.getValue() : '__all',
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
        if (col === 'sommaire') return t('colSummary');
        const f = discoveredFields.find((d) => d.path === col);
        return f ? (f.label || f.path) : col;
    }

    function getColumnValue(rec, col) {
        if (col === '__id') return rec.id || '\u2014';
        if (col === '__summary') return rec.summary || '\u2014';
        if (col === 'sommaire') return rec.summary || '\u2014';
        // For .sommaire suffix on a related object: the IDEntite resolved
        // label is stored at the parent path itself (no sub-fields are kept
        // for IDEntite references in the JS payload).
        if (col.endsWith('.sommaire')) {
            const parentPath = col.slice(0, -'.sommaire'.length);
            return rec.fields[parentPath] || '\u2014';
        }
        // Direct lookup (works for scalars and embedded-struct sub-fields).
        if (rec.fields[col]) return rec.fields[col];
        // Fallback for IDEntite fields: paths like "idDossierAdresse.nom" have
        // no sub-field entry because the IDEntite export only stores a flat
        // resolved label at the parent key. Walk up the path segments.
        if (col.includes('.')) {
            const parts = col.split('.');
            for (let i = parts.length - 1; i >= 1; i--) {
                const prefix = parts.slice(0, i).join('.');
                if (rec.fields[prefix]) return rec.fields[prefix];
            }
        }
        return '\u2014';
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

    /**
     * Returns the inner HTML for a results-table column header.
     * Composite paths (e.g. "adresse.rue") are rendered with each
     * path segment on its own line; all segments except the last use
     * a smaller text style so the leaf name stands out.
     */
    function renderColumnHeader(col) {
        if (col === '__id' || col === '__summary' || col === 'sommaire' || !col.includes('.')) {
            return esc(getColumnLabel(col));
        }
        const parts = col.split('.');
        let html = '<span class="col-hd">';
        for (let i = 0; i < parts.length - 1; i++) {
            const seg = schemaTitleForPath(parts[i]) || humanizeFieldName(parts[i]);
            html += `<span class="col-hd-prefix">${esc(seg)}</span>`;
        }
        const leafKey = parts[parts.length - 1];
        // "sommaire" is a virtual summary segment — label it as the summary column
        const leafLabel = (leafKey === 'sommaire') ? t('colSummary') : (schemaTitleForPath(leafKey) || humanizeFieldName(leafKey));
        html += `<span class="col-hd-leaf">${esc(leafLabel)}</span>`;
        html += '</span>';
        return html;
    }

    function renderResultsHead() {
        const cols = getVisibleColumns();
        let head = `<tr><th style="width:55px">${esc(t('colRow'))}</th>`;
        cols.forEach((c) => head += `<th>${renderColumnHeader(c)}</th>`);
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
                cols.forEach((c) => row += `<td>${fmtValue(getColumnValue(rec, c), c)}</td>`);
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

    /** Maps our language codes to BCP-47 locales for date formatting. */
    var DATE_LOCALES = { fr: 'fr-CA', en: 'en-CA' };

    function fmtDate(val) {
        const m = String(val).match(/^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2}))?)?/);
        if (!m) return null;
        try {
            const d = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]),
                m[4] ? Number(m[4]) : 0, m[5] ? Number(m[5]) : 0, m[6] ? Number(m[6]) : 0);
            if (isNaN(d.getTime())) return null;
            const opts = { year: 'numeric', month: 'long', day: 'numeric' };
            if (m[4]) { opts.hour = '2-digit'; opts.minute = '2-digit'; }
            var locale = DATE_LOCALES[currentLanguage] || 'fr-CA';
            return d.toLocaleDateString(locale, opts);
        } catch (_) { return null; }
    }

    /** Inline SVG checkmark icon for boolean true values. */
    var BOOL_CHECK_SVG = '<svg width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="1.5,5 4,7.5 8.5,2.5"/></svg>';

    /** Renders a boolean value as a styled on/off pill with label. */
    function renderBoolValue(value, trueLabel, falseLabel) {
        var bv = String(value).toLowerCase();
        if (bv === 'true') return '<span class="field-bool on"><span class="bool-icon">' + BOOL_CHECK_SVG + '</span>' + esc(trueLabel) + '</span>';
        if (bv === 'false') return '<span class="field-bool off"><span class="bool-icon"></span>' + esc(falseLabel) + '</span>';
        return null;
    }

    function fmtValue(v, key) {
        const val = String(v ?? '');
        if (!val.trim()) return '<span style="color:var(--c-text-muted)">\u2014</span>';
        const lowerKey = String(key || '').toLowerCase();
        if (val === 'true') return renderBoolValue(val, t('boolTrue'), t('boolFalse'));
        if (val === 'false') return renderBoolValue(val, t('boolTrue'), t('boolFalse'));
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

    /** Check if an object only carries reference metadata (_id, _summary, _preview, _label, _class) and no real fields. */
    function isReferenceOnly(obj) {
        if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return false;
        return Object.keys(obj).every((k) => k.startsWith('_'));
    }

    function classifyFieldEntry(key, value) {
        if (value === null || value === undefined) {
            return { key, type: 'primitive', value: '' };
        }

        if (Array.isArray(value)) {
            if (value.length === 0) {
                return { key, type: 'primitive', value: '' };
            }
            const allPrimitive = value.every((item) => item === null || item === undefined || typeof item !== 'object');
            if (allPrimitive) {
                return { key, type: 'primitive', value: value.map((item) => String(item ?? '')).join(' | ') };
            }
            return { key, type: 'collection', value };
        }

        if (typeof value === 'object') {
            if (isReferenceOnly(value)) {
                return { key, type: 'reference', value };
            }
            return { key, type: 'object', value };
        }

        return { key, type: 'primitive', value };
    }

    function getObjectEntries(value) {
        if (!value || typeof value !== 'object' || Array.isArray(value)) return [];
        const entries = [];

        Object.entries(value).forEach(([k, v]) => {
            if (k.startsWith('_')) return;
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

    /** Returns a title="..." attribute for a section header when the raw field name differs from the display title. */
    function sectionTitleAttr(name) {
        var display = formatSectionTitle(name);
        var raw = String(name || '').split('.').pop() || '';
        return raw && raw !== display ? ' title="' + esc(raw) + '"' : '';
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

    /** Inline SVG arrow icon used in reference link buttons. */
    var REF_ARROW_SVG = '<svg class="ref-btn-icon" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 12L12 4M12 4H5M12 4v7"/></svg>';

    /** Builds an internal reference link button with arrow icon. */
    function refLinkBtn(href, text) {
        return '<a class="ref-id-link" href="' + esc(href) + '">' + esc(text) + REF_ARROW_SVG + '</a>';
    }

    /**
     * Extracts the image src URL from a raw _preview HTML string.
     * @param {string} preview - raw _preview value (contains an <img> tag with src)
     * @returns {string|null} the src URL, or null if not found
     */
    function extractPreviewSrc(preview) {
        if (!preview) return null;
        var m = String(preview).match(/src="([^"]+)"/);
        return m ? m[1] : null;
    }

    /**
     * Renders a preview image from a raw _preview HTML string.
     * All preview rendering (hero blocks, inline thumbnails) goes through here.
     * @param {string} preview - raw _preview value (contains an <img> tag with src)
     * @param {object} [opts] - rendering options
     * @param {string} [opts.size] - 'hero' (default), 'thumb-sm', 'thumb-md', or 'inline'
     * @param {string} [opts.title] - title attribute on the link (hero/inline only)
     * @returns {string} HTML string or empty string if no valid src found
     */
    function renderPreview(preview, opts) {
        var src = extractPreviewSrc(preview);
        if (!src) return '';
        var size = (opts && opts.size) || 'hero';
        var titleAttr = (opts && opts.title) ? ' title="' + esc(opts.title) + '"' : '';
        if (size === 'thumb-sm') {
            return '<img src="' + esc(src) + '" class="preview-thumb preview-thumb-sm" />';
        }
        if (size === 'thumb-md') {
            return '<img src="' + esc(src) + '" class="preview-thumb preview-thumb-md" />';
        }
        if (size === 'inline') {
            return '<div class="preview-inline"><a href="' + esc(src) + '" target="_blank"' + titleAttr + '><img src="' + esc(src) + '" /></a></div>';
        }
        return '<div class="detail-hero-preview"><a href="' + esc(src) + '" target="_blank"' + titleAttr + '><img src="' + esc(src) + '" /></a></div>';
    }

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

    function renderCollectionSection(label, items, ctx) {
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
                var _sBody = renderLayoutChildren(data, children);
                if (p.ref) {
                    var _sData = unwrapClassWrapper(resolveFieldValue(data, p.ref));
                    if (_sData && typeof _sData === 'object' && !Array.isArray(_sData)) {
                        _sBody += renderPreview(_sData._preview);
                    }
                }
                // Nested embedded section (collapsible + has ref): render as
                // a field-row with the title on the left and a pre-collapsed
                // details block of children on the right, so it sits inline
                // with sibling field rows instead of a big blue header.
                if (collapsible && p.ref) {
                    var _nestedLabel = p.title || displayFieldLabel(p.ref);
                    var _nestedSummary = (_sData && _sData._summary) ? String(_sData._summary) : _nestedLabel;
                    return '<div class="field-row"><div class="field-label">' + esc(_nestedLabel)
                        + '</div><div class="field-value"><details class="inline-subsection">'
                        + '<summary>' + esc(_nestedSummary) + '</summary>'
                        + '<div class="inline-subsection-body">' + _sBody + '</div>'
                        + '</details></div></div>';
                }
                if (collapsible) {
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
                    + renderLayoutChildren(data, children) + '</div>';
            }
            case 'column':
                return '<div class="layout-column">' + renderLayoutChildren(data, children) + '</div>';

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
                    // Delegate all complex objects/arrays to the recursive
                    // renderValue pipeline, wrapped in an inline-subsection
                    // so it sits in a field-row like its siblings.
                    var _embEntries = getObjectEntries(scalarVal);
                    var _embBody = '';
                    _embEntries.forEach(function (e) {
                        _embBody += renderValue(e.key, e.value, 'embedded');
                    });
                    if (!_embBody) return '';
                    return '<div class="field-row' + styleCls + '"' + styleAttr + '><div class="field-label"' + fieldTitleAttr + '>' + esc(label)
                        + '</div><div class="field-value"><details class="inline-subsection">'
                        + '<summary>' + esc(label) + '</summary>'
                        + '<div class="inline-subsection-body">' + _embBody + '</div>'
                        + '</details></div></div>';
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
                if (!Array.isArray(items) || items.length === 0) {
                    return '<div class="field-row"><div class="field-label">' + esc(displayFieldLabel(p.ref))
                        + '</div><div class="field-value" style="color:var(--c-text-muted)">\u2014</div></div>';
                }
                var bare = p.bare === 'true';
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
                        // Object cell: extract displayable value
                        if (cellVal && typeof cellVal === 'object' && !Array.isArray(cellVal)) {
                            // Reference or labeled value
                            if (cellVal._label !== undefined) {
                                cellVal = cellVal._label;
                            } else if (cellVal._summary) {
                                // Embedded object with summary: render as expandable toggle
                                var _embSum = String(cellVal._summary || '').trim();
                                var _embId = String(cellVal._id || '').trim();
                                var _refPtDN = pointsToByPath[normalizeSchemaPath(col)];
                                var _refPtHr = _refPtDN ? navHrefByDestName[_refPtDN] : null;
                                var _refLk = (_refPtHr && _embId && _embId !== '0' && _embId !== '-1')
                                    ? _refPtHr + '?open=' + encodeURIComponent(_embId) : null;
                                var _sHtml = _refLk ? refLinkBtn(_refLk, _embSum) : fmtValue(_embSum, col);
                                var _dRows = '';
                                Object.keys(cellVal).forEach(function (dk) {
                                    if (dk.charAt(0) === '_' || dk === 'derniereModification' || dk === 'donnees') return;
                                    var dv = cellVal[dk];
                                    if (dv == null) return;
                                    if (typeof dv === 'object') {
                                        if (dv._label !== undefined) dv = dv._label;
                                        else if (dv._summary) dv = dv._summary;
                                        else {
                                            var _lv = [];
                                            (function _el(n) {
                                                if (n == null) return;
                                                if (Array.isArray(n)) { n.forEach(_el); return; }
                                                if (typeof n !== 'object') { _lv.push(String(n)); return; }
                                                Object.keys(n).forEach(function (nk) { if (nk.charAt(0) !== '_') _el(n[nk]); });
                                            })(dv);
                                            if (_lv.length > 0) dv = _lv.join(', ');
                                            else return;
                                        }
                                    }
                                    var dvStr = String(dv ?? '').trim();
                                    if (!dvStr) return;
                                    _dRows += '<div style="padding:1px 0;font-size:0.85em;">'
                                        + '<span style="color:var(--c-text-muted)">' + esc(displayFieldLabel(dk)) + ':</span> '
                                        + fmtValue(dv, dk) + '</div>';
                                });
                                if (_dRows) {
                                    var _prevHtml = renderPreview(cellVal._preview, { size: 'inline' });
                                    thtml += '<td><div style="cursor:pointer" onclick="var d=this.querySelector(\'.ebd\');'
                                        + 'var a=this.querySelector(\'.eta\');if(d.style.display===\'none\'){'
                                        + 'd.style.display=\'block\';a.textContent=\'\\u25BC\'}else{'
                                        + 'd.style.display=\'none\';a.textContent=\'\\u25B6\'}">'
                                        + '<span class="eta" style="font-size:0.7em;margin-right:3px">&#9654;</span>'
                                        + _sHtml
                                        + '<div class="ebd" style="display:none;margin-top:4px;border-top:1px solid var(--c-border);padding-top:4px">'
                                        + _dRows + _prevHtml + '</div></div></td>';
                                } else {
                                    thtml += '<td>' + _sHtml + '</td>';
                                }
                                return;
                            } else {
                                // Compact inline rendering with labels
                                var segs = [];
                                Object.keys(cellVal).forEach(function (k) {
                                    if (k.charAt(0) === '_') return;
                                    var sv = cellVal[k];
                                    if (sv == null) return;
                                    if (typeof sv === 'object') {
                                        if (sv._label !== undefined) sv = sv._label;
                                        else if (sv._summary) sv = sv._summary;
                                        else {
                                            var leafVals = [];
                                            (function extractLeaves(node) {
                                                if (node == null) return;
                                                if (Array.isArray(node)) { node.forEach(extractLeaves); return; }
                                                if (typeof node !== 'object') { leafVals.push(String(node)); return; }
                                                Object.keys(node).forEach(function (nk) {
                                                    if (nk.charAt(0) !== '_') extractLeaves(node[nk]);
                                                });
                                            })(sv);
                                            if (leafVals.length > 0) sv = leafVals.join(', ');
                                            else return;
                                        }
                                    }
                                    segs.push('<span style="color:var(--c-text-muted);font-size:0.85em">' + esc(displayFieldLabel(k)) + ':</span> ' + fmtValue(sv, k));
                                });
                                thtml += '<td>' + (segs.length ? segs.join(' &middot; ') : '\u2014') + '</td>';
                                return;
                            }
                        }
                        thtml += '<td>' + fmtValue(cellVal, col) + '</td>';
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
            const opSel = row.querySelector('.operator-select');
            const valInput = row.querySelector('.value-input');
            const removeBtn = row.querySelector('.remove-btn');
            const logicLabel = row.querySelector('.logic-label');

            // Refresh the hierarchical field picker labels
            if (row._fieldPicker) row._fieldPicker.refresh();

            if (opSel) {
                const selected = opSel.value;
                const fieldType = getFieldType(row._fieldPicker ? row._fieldPicker.getValue() : '__all');
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
            // Sync language dropdown with export-configured default language
            if (languageSelect) languageSelect.value = currentLanguage;
            applyLanguage();
            renderResults();
            resultsCount.textContent = allRecords.length + ' ' + t('resultsTotal');
            // Auto-open a specific record if ?open=<id> is present in the URL
            var _openId = new URLSearchParams(window.location.search).get('open');
            if (_openId) {
                var _targetRec = allRecords.find(function (r) { return String(r.id) === String(_openId); });
                if (_targetRec) selectRecord(_targetRec);
            }
        } catch (err) {
            resultsCount.textContent = `${t('err')}: ${err.message || 'Unknown'}`;
            resultsBody.innerHTML = `<tr><td colspan="3"><div class="results-empty"><p>${esc(err.message || t('noPayload'))}</p></div></td></tr>`;
        }
    }

    addConditionBtn.addEventListener('click', addCondition);
    clearSearchBtn.addEventListener('click', () => {
        // Destroy picker listeners before clearing
        Array.from(conditionsContainer.querySelectorAll('.search-condition')).forEach((row) => {
            if (row._fieldPicker) row._fieldPicker.destroy();
        });
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
