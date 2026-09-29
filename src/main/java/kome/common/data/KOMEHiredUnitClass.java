package kome.common.data;

/** Durable strategic classification for a population-tracked hired unit. */
public enum KOMEHiredUnitClass {
    ORDINARY,
    CAMPAIGN;

    static KOMEHiredUnitClass fromPersistedValue(String value) {
        if (CAMPAIGN.name().equals(value)) {
            return CAMPAIGN;
        }
        return ORDINARY;
    }
}
