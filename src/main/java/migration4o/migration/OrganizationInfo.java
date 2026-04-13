package migration4o.migration;

/**
 * Identifies a partner organization found in the database, derived from a {@code ParamConfigSSI} object.
 * <p>
 * {@code idSSI}   is the value stored in the {@code mIDSSI} field;<br>
 * {@code name}    is the city name from {@code mVille.mNom};<br>
 * {@code codeRao} is the RAO code from {@code mVille.mCodeRAO} (may be {@code null}).
 */
public record OrganizationInfo(int idSSI, String name, String codeRao) {

    public OrganizationInfo {
        if (idSSI < 0) {
            throw new IllegalArgumentException("Organization idSSI must not be negative, got: " + idSSI);
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Organization name must not be null or blank");
        }
        // codeRao is optional — null is valid
    }

    @Override
    public String toString() {
        return name;
    }
}
