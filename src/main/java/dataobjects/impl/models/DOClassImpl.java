package dataobjects.impl.models;

import dataobjects.api.models.DOClass;
import dataobjects.api.models.DOField;
import dataobjects.api.models.DOReference;
import java.util.ArrayList;
import java.util.List;

public class DOClassImpl implements DOClass {

    private final String absoluteName;
    private final String shortName;
    private final String description;
    private final String title;
    private final String superClassAbsoluteName;
    private final DOField[] fields;
    private final List<DOReference> referenceList;

    public DOClassImpl(String absoluteName, String shortName, String description, String title,
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

    @Override
    public String getAbsoluteName() {
        return absoluteName;
    }

    @Override
    public String getShortName() {
        return shortName;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public String getSuperClassAbsoluteName() {
        return superClassAbsoluteName;
    }

    @Override
    public DOField[] getFields() {
        return fields;
    }

    @Override
    public DOReference[] getReferences() {
        return referenceList.toArray(new DOReference[0]);
    }

    @Override
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

    @Override
    public void addReference(DOReference reference) {
        if (reference != null) {
            referenceList.add(reference);
        }
    }
}
