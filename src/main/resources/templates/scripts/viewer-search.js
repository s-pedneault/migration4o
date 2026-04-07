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

