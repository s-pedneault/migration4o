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

