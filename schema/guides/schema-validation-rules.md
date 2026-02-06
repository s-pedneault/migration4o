# Reference Schema Validation Rules

## Types of anomalies reported:

All types of anomalies reported by this system extend `DOSchemaEmbeddingAnomaly`.

* `DOSchemaSharedNotExportedAnomaly`: 
* `DOSchemaSharedEmbeddedAnomaly`: 
* `DOSchemaShouldBeEmbeddedAnomaly`: 
* `DOSchemaShouldNotBeExportedAnomaly`: 


## Processing the entire schema, once fully loaded, to detect anomalies

1. For each class defined in the *schema*:
    1. For each field of the class whose `type` is defined in the *schema*:
        * If the field's `type` is a kind of `IDEntite` ➡ refer to **PROCEDURE 1**
        * If the field's `type` is a kind of `Entite` ➡ refer to **PROCEDURE 2**
        * If the field's `collection` attribute is `true`: 
            1. If the field's `childrenType` is a kind of `IDEntite` ➡ refer to **PROCEDURE 1**
            1. Else if the field's `childrenType` is a kind of `Entite` ➡ refer to **PROCEDURE 2**

### 📋 PROCEDURE 1: Processing a type that is a descendant of `IDEntite`
1. Lookup the IDEntite-type definition in the *schema*.
2. Lookup the concrete class mentionned in its `pointsTo` attribute
3. Process that concrete class with **PROCEDURE 2**

### 📋 PROCEDURE 2: Processing a type that is a descendant of `Entite`
* If that concrete class has MORE than one (1) `reference`: 
    * ⚠️ generate a `DOSchemaSharedEmbeddedAnomaly` if the original field has `embedContents` to `true`
    * ⚠️ generate a `DOSchemaSharedNotExportedAnomaly` if the concrete class is NOT listed in any *module*
* If that concrete class has exactly one (1) `reference`:
    * ⚠️ generate a `DOSchemaShouldBeEmbeddedAnomaly` if the original field has `embedContents` to `false`
    * ⚠️ generate a `DOSchemaShouldNotBeExportedAnomaly` if the concrete class IS listed in any *module*
