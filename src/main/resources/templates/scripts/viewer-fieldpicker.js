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

