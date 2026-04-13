package migration4o.migration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import migration4o.database.DODatabase;
import migration4o.database.DODatabaseClass;
import migration4o.database.DODatabaseDelegate;
import migration4o.migration.recipes.ObjectActivator;
import migration4o.models.schema.DOSchemaConstants;
import migration4o.util.ObjectResolverUtil;

/**
 * Detects the partner organizations present in a database by querying all
 * {@code ParamConfigSSI} objects and reading their {@code mIDSSI} and
 * {@code mVille.mNom} fields.
 */
public class OrganizationDetectionService {

    private static final Logger log = LogManager.getLogger(OrganizationDetectionService.class);

    private static final String FIELD_VILLE = "mVille";
    private static final String FIELD_NOM = "mNom";

    /**
     * Queries all {@code ParamConfigSSI} objects from the database and returns one
     * {@link OrganizationInfo} per valid entry, sorted by name.
     * <p>
     * Returns an empty list if the class is absent or has no objects. Invalid
     * entries (missing or non-positive {@code mIDSSI}, blank name) are skipped
     * with a warning.
     */
    public static List<OrganizationInfo> detectOrganizations(DODatabase database) {
        if (database == null) {
            return List.of();
        }

        DODatabaseClass dbClass = database.findClassByName(DOSchemaConstants.ORGANIZATION_CLASS_NAME);
        if (dbClass == null || dbClass.objects == null || dbClass.objects.objectIds == null || dbClass.objects.objectIds.length == 0) {
            return List.of();
        }

        DODatabaseDelegate delegate = dbClass.delegate;
        List<OrganizationInfo> result = new ArrayList<>();

        for (long objectId : dbClass.objects.objectIds) {
            OrganizationInfo org = readOrganization(delegate, objectId);
            if (org != null) {
                result.add(org);
            }
        }

        result.sort(Comparator.comparing(OrganizationInfo::name));
        return result;
    }

    private static OrganizationInfo readOrganization(DODatabaseDelegate delegate, long objectId) {
        ObjectActivator.ActivationResult activation = ObjectActivator.getAndActivate(delegate, objectId);
        if (activation == null) {
            return null;
        }

        try {
            Object obj = activation.object;

            Object idValue = delegate.getStoredFieldValue(obj, DOSchemaConstants.ORGANIZATION_BUSINESS_ID_FIELD_NAME);
            if (!(idValue instanceof Number)) {
                return null;
            }
            int idSSI = ((Number) idValue).intValue();
            if (idSSI <= 0) {
                return null;
            }

            Object villeObj = delegate.getStoredFieldValue(obj, FIELD_VILLE);
            if (villeObj == null) {
                log.warn("ParamConfigSSI id={} has no mVille — skipping", idSSI);
                return null;
            }
            ObjectResolverUtil.activateObjectShallow(delegate, villeObj, null);

            Object nomValue = delegate.getStoredFieldValue(villeObj, FIELD_NOM);
            if (!(nomValue instanceof String) || ((String) nomValue).isBlank()) {
                log.warn("ParamConfigSSI id={} has no mVille.mNom — skipping", idSSI);
                return null;
            }

            return new OrganizationInfo(idSSI, (String) nomValue);

        } catch (Exception e) {
            log.warn("Failed to read ParamConfigSSI object id={}: {}", objectId, e.getMessage());
            return null;
        }
    }
}
