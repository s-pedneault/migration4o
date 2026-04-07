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
