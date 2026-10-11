package kome.common.data;

/** Resolves independently numbered development save formats before decoding their authority. */
final class KOMEWorldDataLineage {
    final boolean resetRequired;
    final boolean campaignRequired;
    final boolean movementRequired;
    final boolean tacticalRequired;
    final boolean emergencyDefenseEight;
    final boolean emergencyDefenseNine;

    private KOMEWorldDataLineage(boolean reset, boolean campaign, boolean movement,
            boolean tactical, boolean defenseEight, boolean defenseNine) {
        resetRequired = reset;
        campaignRequired = campaign;
        movementRequired = movement;
        tacticalRequired = tactical;
        emergencyDefenseEight = defenseEight;
        emergencyDefenseNine = defenseNine;
    }

    static KOMEWorldDataLineage resolve(int schema, boolean tactical, boolean defense, boolean movement) {
        if (schema < 6 || schema > 12) throw new IllegalArgumentException("Unsupported root schema " + schema);
        boolean campaignOnly = !tactical && !defense && !movement;
        boolean resetEight = schema == 8 && campaignOnly;
        boolean campaignNine = schema == 9 && campaignOnly;
        boolean campaignTen = schema == 10 && campaignOnly;
        boolean tacticalEight = schema == 8 && tactical && !defense && !movement;
        boolean defenseEight = schema == 8 && !tactical && defense && !movement;
        boolean movementNine = schema == 9 && tactical && !defense && movement;
        boolean defenseNine = schema == 9 && !tactical && defense && !movement;
        if (schema <= 7 && defense)
            throw new IllegalArgumentException("Schema-6/7 roots cannot contain Emergency Defense authority.");
        if (schema == 8 && !resetEight && !tacticalEight && !defenseEight)
            throw new IllegalArgumentException("Schema 8 must be the reset, tactical, or Emergency Defense lineage.");
        if (schema == 9 && !campaignNine && !movementNine && !defenseNine)
            throw new IllegalArgumentException("Schema 9 must be the campaign, movement, or Emergency Defense lineage.");
        if (schema == 10 && movement)
            throw new IllegalArgumentException("Schema 10 predates movement authority; mixed movement lineage is ambiguous.");
        boolean devCombined = schema >= 11 || schema == 10 && !campaignTen;
        if (devCombined && (!tactical || !defense))
            throw new IllegalArgumentException("Schema " + schema + " requires TacticalConfiguration and Emergency Defense authority.");
        return new KOMEWorldDataLineage(schema >= 12 || resetEight || campaignTen,
            schema >= 12 || campaignNine || campaignTen, schema >= 11 || movement,
            devCombined || tacticalEight || movementNine, defenseEight, defenseNine);
    }
}
