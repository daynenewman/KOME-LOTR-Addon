package kome.common.command;

import kome.common.data.KOMEHiredUnitRecord;

import java.util.UUID;

class SpawnTarget {
    boolean valid;
    int dimensionId;
    double x;
    double y;
    double z;
    String label = "";
    String failureReason = "";
    int chunkX;
    int chunkZ;
    boolean chunkLoadedBeforeAttempt;
    boolean chunkLoadedForSpawn;
}

class SpawnAttempt {
    KOMEHiredUnitRecord record;
    int orderIndex = -1;
    UUID oldId;
    UUID newId;
    double attemptX;
    double attemptY;
    double attemptZ;
    int dimensionId;
    double spawnX;
    double spawnY;
    double spawnZ;
    String targetLabel = "";
    String failureReason = "";
}
