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
            openLinkedRecord: 'Ouvrir l\'enregistrement lié',
            emptyTab: 'Aucune donnée',
            contentPreviewUnavailable: 'Aperçu non disponible pour ce type de fichier',
            demoNoticeTitle: '🔑 Export démo',
            demoMissingRecord: 'Cet élément n\u2019a pas été inclus dans cet export partiel. Dans un export complet, ce lien mènerait aux détails de cet élément.'
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
            openLinkedRecord: 'Open linked record',
            emptyTab: 'No data',
            contentPreviewUnavailable: 'Preview not available for this file type',
            demoNoticeTitle: 'Limited Preview',
            demoMissingRecord: 'This record was not included in this partial preview. In a complete export, this link would lead to the corresponding record.'
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

