package kome.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client cache of server-authoritative Trial eligibility, keyed by stable NPC identity. */
final class KOMEStandingTrialEligibilityState {
    private static final class Entry {
        final int entityId;
        final boolean eligible;
        final boolean passiveOffer;

        Entry(int entityId, boolean eligible, boolean passiveOffer) {
            this.entityId = entityId;
            this.eligible = eligible;
            this.passiveOffer = passiveOffer;
        }
    }

    private final Map<UUID, Entry> entities = new HashMap<UUID, Entry>();

    void update(int entityId, UUID entityUuid, boolean eligible, boolean passiveOffer) {
        if (entityUuid == null) return;
        if (eligible || passiveOffer) {
            entities.put(entityUuid, new Entry(entityId, eligible, passiveOffer));
            return;
        }
        Entry known = entities.get(entityUuid);
        if (known != null && known.entityId == entityId) {
            entities.remove(entityUuid);
        }
    }

    boolean isEligible(int entityId, UUID entityUuid) {
        Entry known = entityUuid == null ? null : entities.get(entityUuid);
        return known != null && known.entityId == entityId && known.eligible;
    }

    boolean isStandingTrialAvailable(int entityId, UUID entityUuid,
            boolean nativeStandingTrialOffer) {
        Entry known = entityUuid == null ? null : entities.get(entityUuid);
        return known != null && known.entityId == entityId && known.eligible && known.passiveOffer;
    }

    private final Map<UUID,String> reasons=new HashMap<UUID,String>();
    void update(int id,UUID uuid,boolean enabled,boolean passive,boolean relevant,String reason){update(id,uuid,enabled,passive);if(relevant){entities.put(uuid,new Entry(id,enabled,passive));reasons.put(uuid,reason);}else reasons.remove(uuid);}
    boolean isRelevant(int id,UUID uuid){Entry e=entities.get(uuid);return e!=null&&e.entityId==id&&reasons.containsKey(uuid);}
    String reason(int id,UUID uuid){return isRelevant(id,uuid)?reasons.get(uuid):"";}
    void clear() {
        reasons.clear();
        entities.clear();
    }
}
