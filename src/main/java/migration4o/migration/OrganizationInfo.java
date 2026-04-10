package migration4o.migration;

/**
 * Identifies a partner organization found in the database, derived from a {@code ParamConfigSSI} object.
 * <p>
 * {@code idSSI} is the value stored in the {@code mIDSSI} field;
 * {@code name}  is the city name from {@code mVille.mNom}.
 */
public record OrganizationInfo(int idSSI, String name) {

    public OrganizationInfo {
        if (idSSI <= 0) {
            throw new IllegalArgumentException("Organization idSSI must be positive, got: " + idSSI);
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Organization name must not be null or blank");
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
