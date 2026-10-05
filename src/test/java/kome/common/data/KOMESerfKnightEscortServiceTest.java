package kome.common.data;

import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.*;

public class KOMESerfKnightEscortServiceTest {
    @Test
    public void persistedStringUuidMatchesLoadedEntityUuid() {
        UUID id=UUID.randomUUID();
        assertTrue(KOMESerfKnightEscortService.sameEntityId(id.toString(),id));
        assertFalse(KOMESerfKnightEscortService.sameEntityId(UUID.randomUUID().toString(),id));
        assertFalse(KOMESerfKnightEscortService.sameEntityId(null,id));
        assertFalse(KOMESerfKnightEscortService.sameEntityId(id.toString(),null));
    }
}