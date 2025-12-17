package dataobjects.impl.models;

import dataobjects.impl.models.DOClass;
import dataobjects.impl.models.DOField;
import dataobjects.impl.models.DOReference;
import java.util.ArrayList;
import java.util.List;

public class DOClass {

    private final String absoluteName;
    private final String shortName;
    private final String description;
    private final String title;
    private final String superClassAbsoluteName;
    private final DOField[] fields;
    private final List<DOReference> referenceList;

    public DOClass(String absoluteName, String shortName, String description, String title,
            String superClassAbsoluteName,
            DOField[] fields) {
        this.absoluteName = absoluteName;
        this.shortName = shortName;
        this.description = description;
        this.title = title;
        this.superClassAbsoluteName = superClassAbsoluteName;
        this.fields = fields != null ? fields : new DOField[0];
        this.referenceList = new ArrayList<>();
    }

    public String getAbsoluteName() {
        return absoluteName;
    }

    public String getShortName() {
        return shortName;
    }

    public String getDescription() {
        return description;
    }

    public String getTitle() {
        return title;
    }

    public String getSuperClassAbsoluteName() {
        return superClassAbsoluteName;
    }

    public DOField[] getFields() {
        return fields;
    }

    public DOReference[] getReferences() {
        return referenceList.toArray(new DOReference[0]);
    }

    public void setReferences(DOReference[] references) {
        referenceList.clear();
        if (references != null) {
            for (DOReference ref : references) {
                if (ref != null) {
                    referenceList.add(ref);
                }
            }
        }
    }

    public void addReference(DOReference reference) {
        if (reference != null) {
            referenceList.add(reference);
        }
    }
}
