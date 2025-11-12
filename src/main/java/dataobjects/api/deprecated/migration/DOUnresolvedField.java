package dataobjects.api.deprecated.migration;

import dataobjects.api.models.DOClass;
import dataobjects.api.models.DOField;

/**
 * Represents an unresolved field in the migration analysis.
 */
public interface DOUnresolvedField {

    /**
     * Get the class containing this unresolved field.
     * 
     * @return The class
     */
    DOClass getContainingClass();

    /**
     * Get the unresolved field.
     * 
     * @return The field
     */
    DOField getField();

    /**
     * Get the collection type if this is a collection field.
     * 
     * @return Collection type, or null if not a collection
     */
    String getCollectionType();

    /**
     * Get the unresolved content type.
     * 
     * @return Content type
     */
    String getContentType();

    /**
     * Get the reason why this field is unresolved.
     * 
     * @return Reason for being unresolved
     */
    String getUnresolvedReason();
}
