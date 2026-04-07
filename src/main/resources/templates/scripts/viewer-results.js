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

