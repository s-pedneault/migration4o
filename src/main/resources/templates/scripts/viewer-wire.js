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
