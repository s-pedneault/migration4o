package migration4o.demo;

import java.util.Random;

/**
 * Curated lists of believable Quebec/French-Canadian data for demo database generation.
 * All values are real-world plausible but synthetic — no actual personal data.
 */
public class QuebecDataLists {

    // ── Names ────────────────────────────────────────────────────────────────

    static final String[] FIRST_NAMES_MALE = { "Luc", "Marc", "Pierre", "Jean", "François", "Michel", "André", "Daniel", "Sylvain", "Martin", "Stéphane", "Éric", "Patrick", "Alain", "Denis", "Robert", "Claude", "Benoît", "Mathieu", "Philippe", "Sébastien", "Alexandre", "Nicolas", "Yves", "Jacques", "Louis", "Guillaume", "Réal", "Normand", "Gaétan" };

    static final String[] FIRST_NAMES_FEMALE = { "Marie", "Louise", "Diane", "Nathalie", "Sophie", "Julie", "Isabelle", "Manon", "Chantal", "Hélène", "Josée", "Caroline", "Mélanie", "Valérie", "Annie", "Guylaine", "Sylvie", "Lucie", "France", "Jocelyne", "Martine", "Geneviève", "Céline", "Catherine", "Monique", "Claudine", "Johanne", "Sarah", "Émilie", "Karine" };

    static final String[] LAST_NAMES = { "Tremblay", "Gagnon", "Roy", "Côté", "Bouchard", "Gauthier", "Morin", "Lavoie", "Fortin", "Gagné", "Ouellet", "Pelletier", "Bélanger", "Lévesque", "Bergeron", "Leblanc", "Paquette", "Girard", "Simard", "Boucher", "Caron", "Beaulieu", "Cloutier", "Dubé", "Poirier", "Fournier", "Lapointe", "Leclerc", "Laflamme", "Thibault", "Mercier", "Dufour", "Dupuis", "Gosselin", "Martel", "Landry", "Blais", "Paradis", "Nadeau", "Perron" };

    // ── Municipalities ───────────────────────────────────────────────────────

    static final String[] MUNICIPALITIES = { "Montréal", "Québec", "Laval", "Gatineau", "Longueuil", "Sherbrooke", "Saguenay", "Lévis", "Trois-Rivières", "Terrebonne", "Saint-Jean-sur-Richelieu", "Repentigny", "Brossard", "Drummondville", "Saint-Jérôme", "Granby", "Blainville", "Saint-Hyacinthe", "Shawinigan", "Dollard-des-Ormeaux", "Rimouski", "Victoriaville", "Saint-Eustache", "Mascouche", "Châteauguay", "Rouyn-Noranda", "Salaberry-de-Valleyfield", "Mirabel", "Alma", "Val-d'Or" };

    // ── Streets ──────────────────────────────────────────────────────────────

    static final String[] STREET_NAMES = { "Rue Principale", "Boulevard des Laurentides", "Rue Sainte-Catherine", "Avenue du Parc", "Rue Saint-Jean", "Boulevard Saint-Laurent", "Rue de l'Église", "Chemin du Lac", "Boulevard René-Lévesque", "Rue Commerciale", "Avenue Cartier", "Rue des Érables", "Boulevard Industriel", "Chemin de la Rivière", "Rue du Moulin", "Avenue Royale", "Rue des Pins", "Boulevard du Curé-Labelle", "Rue Notre-Dame", "Chemin Sainte-Foy" };

    // ── Phone area codes (Quebec) ────────────────────────────────────────────

    static final String[] AREA_CODES = { "450", "514", "418", "819", "438", "579", "581", "873" };

    // ── Postal code prefixes (Quebec) ────────────────────────────────────────

    static final String[] POSTAL_PREFIXES = { "G", "H", "J" // Quebec-specific first letters
    };

    // ── Fire department specific ─────────────────────────────────────────────

    static final String[] FIRE_GRADES = { "Pompier", "Pompier 1re classe", "Lieutenant", "Capitaine", "Chef aux opérations", "Chef de division", "Directeur adjoint", "Directeur" };

    static final String[] FIRE_CERTIFICATIONS = { "DEP Intervention en sécurité incendie", "AEC Prévention des incendies", "Officier I NFPA 1021", "Officier II NFPA 1021", "Instructeur NFPA 1041", "Inspecteur NFPA 1031", "Technicien en prévention", "Matières dangereuses Niveau 2" };

    static final String[] VEHICLE_TYPES = { "Autopompe", "Camion-citerne", "Échelle aérienne", "Unité de secours", "Véhicule de commandement", "Camion-nacelle", "Fourgon de sauvetage", "Embarcation de sauvetage", "VTT", "Motopompe" };

    static final String[] VEHICLE_MAKES = { "Pierce", "Spartan", "Freightliner", "E-One", "Sutphen", "KME", "Rosenbauer", "Seagrave", "International", "Ford" };

    static final String[] EQUIPMENT_TYPES = { "Appareil respiratoire", "Boyau d'incendie", "Lance à incendie", "Détecteur de gaz", "Caméra thermique", "Scie à chaîne", "Ventilateur", "Civière", "Trousse de premiers soins", "Extincteur", "Échelle portative", "Corde de sauvetage" };

    static final String[] BUILDING_TYPES = { "Résidentiel isolé", "Résidentiel jumelé", "Résidentiel en rangée", "Immeuble d'appartements", "Commercial", "Industriel", "Institutionnel", "Agricole", "Tour d'habitation", "Maison mobile" };

    static final String[] HEATING_TYPES = { "Électricité", "Gaz naturel", "Mazout", "Propane", "Bois / Poêle", "Thermopompe", "Bi-énergie", "Géothermie" };

    static final String[] INTERVENTION_TYPES = { "Incendie de bâtiment", "Incendie de véhicule", "Feu de forêt", "Premiers répondants", "Désincarcération", "Inondation", "Fuite de gaz", "Sauvetage nautique", "Alarme incendie", "Entraide", "Feu de cheminée", "Appel de service" };

    static final String[] PREVENTION_TYPES = { "Inspection résidentielle", "Inspection commerciale", "Inspection industrielle", "Vérification d'avertisseur", "Plan de sécurité incendie", "Exercice d'évacuation", "Visite de prévention", "Enquête sur un incendie" };

    // ── Email domains ────────────────────────────────────────────────────────

    static final String[] EMAIL_DOMAINS = { "ville.example.qc.ca", "ssi-demo.qc.ca", "pompiers-demo.ca", "mun.example.qc.ca", "protection-demo.ca" };

    // ── Common descriptions / notes ──────────────────────────────────────────

    static final String[] SHORT_NOTES = { "Aucune note", "Voir dossier", "À vérifier", "Complété", "En attente", "Suivi requis", "Prioritaire", "Remplacement temporaire", "Évaluation annuelle", "Normal" };

    // ── Data access helpers ──────────────────────────────────────────────────

    public static String pick(String[] array, Random rng) {
        return array[rng.nextInt(array.length)];
    }

    public static String firstName(Random rng) {
        if (rng.nextBoolean()) {
            return pick(FIRST_NAMES_MALE, rng);
        }
        return pick(FIRST_NAMES_FEMALE, rng);
    }

    public static String firstNameMale(Random rng) {
        return pick(FIRST_NAMES_MALE, rng);
    }

    public static String firstNameFemale(Random rng) {
        return pick(FIRST_NAMES_FEMALE, rng);
    }

    public static String lastName(Random rng) {
        return pick(LAST_NAMES, rng);
    }

    public static String fullName(Random rng) {
        return firstName(rng) + " " + lastName(rng);
    }

    public static String municipality(Random rng) {
        return pick(MUNICIPALITIES, rng);
    }

    public static String streetAddress(Random rng) {
        int number = 10 + rng.nextInt(9990);
        return number + ", " + pick(STREET_NAMES, rng);
    }

    public static String phoneNumber(Random rng) {
        String area = pick(AREA_CODES, rng);
        int mid = 200 + rng.nextInt(800);
        int end = 1000 + rng.nextInt(9000);
        return area + "-" + mid + "-" + end;
    }

    public static String postalCode(Random rng) {
        String prefix = pick(POSTAL_PREFIXES, rng);
        char d1 = (char) ('0' + rng.nextInt(10));
        char l1 = (char) ('A' + rng.nextInt(26));
        char d2 = (char) ('0' + rng.nextInt(10));
        char l2 = (char) ('A' + rng.nextInt(26));
        char d3 = (char) ('0' + rng.nextInt(10));
        return prefix + d1 + l1 + " " + d2 + l2 + d3;
    }

    public static String email(Random rng) {
        String first = stripAccents(pick(FIRST_NAMES_MALE, rng).toLowerCase());
        String last = stripAccents(pick(LAST_NAMES, rng).toLowerCase()).replace("'", "");
        return first + "." + last + "@" + pick(EMAIL_DOMAINS, rng);
    }

    /**
     * Strips French diacritics from a string for use in ASCII-only contexts (e.g. email).
     */
    private static String stripAccents(String s) {
        return s.replace("é", "e").replace("è", "e").replace("ê", "e").replace("ë", "e").replace("à", "a").replace("â", "a").replace("î", "i").replace("ï", "i").replace("ô", "o").replace("ù", "u").replace("û", "u").replace("ç", "c");
    }
}
