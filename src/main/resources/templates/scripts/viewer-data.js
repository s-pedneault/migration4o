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

