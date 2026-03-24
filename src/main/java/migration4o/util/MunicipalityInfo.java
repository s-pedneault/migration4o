package migration4o.util;

/**
 * Client municipality information resolved from {@code schema/municipalities.csv}
 * using the database folder name as the {@code mcode} key.
 * <p>
 * All fields are nullable — callers must null-check before use.
 */
public final class MunicipalityInfo {

    /** Municipality code (mcode) — the database folder name, e.g. "54060". */
    public final String code;
    /** Municipality name (munnom). */
    public final String name;
    /** MRC name (mrc). */
    public final String mrc;
    /** Administrative region (regadm). */
    public final String region;
    /** Civic address (madr1 + " " + mcodpos). */
    public final String address;
    /** E-mail (mcourriel). */
    public final String email;
    /** Web site URL (mweb). */
    public final String website;
    /** Population (mpopul). */
    public final String population;

    public MunicipalityInfo(String code, String name, String mrc, String region, String address, String email, String website, String population) {
        this.code = code;
        this.name = name;
        this.mrc = mrc;
        this.region = region;
        this.address = address;
        this.email = email;
        this.website = website;
        this.population = population;
    }
}
