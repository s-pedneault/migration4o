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

/** Maps image file extensions to their MIME type for inline data-URL fallback. */
var IMAGE_MIME_TYPES = {
    'jpg': 'image/jpeg', 'jpeg': 'image/jpeg',
    'png': 'image/png', 'gif': 'image/gif',
    'bmp': 'image/bmp', 'webp': 'image/webp',
    'svg': 'image/svg+xml'
};

/**
 * Returns the image MIME type for a given filename, or null if not an image extension.
 * @param {string} filename - e.g. "photo.jpg"
 * @returns {string|null}
 */
function imageExtMime(filename) {
    if (!filename) return null;
    var ext = String(filename).split('.').pop().toLowerCase();
    return IMAGE_MIME_TYPES[ext] || null;
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
 * @param {object} [obj] - the full data object; when provided and it has a Base64 `contenu`
 *                         field with an image-typed `nom`, an onerror data-URL fallback is
 *                         added so the image still displays if the file/ copy is missing.
 * @returns {string} HTML string or empty string if no valid src found
 */
function renderPreview(preview, opts, obj) {
    var src = extractPreviewSrc(preview);
    if (!src) return '';
    var size = (opts && opts.size) || 'hero';
    var titleAttr = (opts && opts.title) ? ' title="' + esc(opts.title) + '"' : '';

    // Build an onerror handler that falls back to an inline data-URL when
    // the file/ copy cannot be loaded but the raw bytes are in contenu.
    var fallbackAttr = '';
    if (obj && typeof obj.contenu === 'string' && obj.contenu.length > 0) {
        var _mime = imageExtMime(String(obj.nom || obj._summary || ''));
        if (_mime) {
            // Base64 alphabet is HTML-safe — no escaping needed for contenu.
            fallbackAttr = ' onerror="if(!this._fb){this._fb=1;this.src=\'data:' + _mime + ';base64,' + obj.contenu + '\';}"';
        }
    }

    if (size === 'thumb-sm') {
        return '<img src="' + esc(src) + '" class="preview-thumb preview-thumb-sm"' + fallbackAttr + ' />';
    }
    if (size === 'thumb-md') {
        return '<img src="' + esc(src) + '" class="preview-thumb preview-thumb-md"' + fallbackAttr + ' />';
    }
    if (size === 'inline') {
        return '<div class="preview-inline"><a href="' + esc(src) + '" target="_blank"' + titleAttr + '><img src="' + esc(src) + '"' + fallbackAttr + ' /></a></div>';
    }
    return '<div class="detail-hero-preview"><a href="' + esc(src) + '" target="_blank"' + titleAttr + '><img src="' + esc(src) + '"' + fallbackAttr + ' /></a></div>';
}

