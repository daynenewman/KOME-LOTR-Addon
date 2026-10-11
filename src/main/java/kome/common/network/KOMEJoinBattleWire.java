package kome.common.network;

import io.netty.buffer.ByteBuf;
import kome.common.data.KOMEConflictRecord;
import kome.common.data.KOMEJoinBattleService;

final class KOMEJoinBattleWire {
    static final int MAX_TILE_LENGTH = 24;
    static final int MAX_CONFLICT_LENGTH = 32;
    static final int MAX_COMPANY_LENGTH = 64;
    static final int MAX_FACTION_LENGTH = 64;
    static final int MAX_NAME_LENGTH = 128;
    static final int MAX_ACTION_TOKEN_LENGTH = 128;
    static final int MAX_RECEIPT_LENGTH = 32;
    static final int MAX_COMPANIES = 256;
    private KOMEJoinBattleWire() { }

    static String text(ByteBuf buf, int max, String label) {
        String value = KOMEPopulationWire.readText(buf);
        requireId(value, max, label);
        return value;
    }
    static void write(ByteBuf buf, String value, int max, String label) {
        requireId(value, max, label); KOMEPopulationWire.writeText(buf, value);
    }
    static void requireId(String value, int max, String label) {
        if (value == null || value.length() > max) throw new IllegalArgumentException("Invalid Join Battle " + label);
    }
    static KOMEJoinBattleService.Reason reason(String value) {
        try { return KOMEJoinBattleService.Reason.valueOf(value); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("Invalid Join Battle reason", invalid); }
    }
    static KOMEConflictRecord.State state(String value) {
        if (value.length() == 0) return null;
        try { return KOMEConflictRecord.State.valueOf(value); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("Invalid Join Battle conflict state", invalid); }
    }
}
