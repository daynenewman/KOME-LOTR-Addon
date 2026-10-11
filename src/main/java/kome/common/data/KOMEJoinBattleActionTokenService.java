package kome.common.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * Short-lived server challenge for the Join Battle selection UI. A challenge narrows replay and
 * stale-GUI input, but never replaces current eligibility/company validation.
 */
public final class KOMEJoinBattleActionTokenService {
    public static final KOMEJoinBattleActionTokenService INSTANCE =
        new KOMEJoinBattleActionTokenService();
    static final long TOKEN_LIFETIME_MILLIS = 5L * 60L * 1000L;
    private static final int MAX_ACTIVE_TOKENS = 4096;
    private static final SecureRandom RANDOM = new SecureRandom();

    public enum Validation { ACCEPTED, ABSENT_OR_EXPIRED, SCOPE_MISMATCH }

    private final LinkedHashMap<String, Grant> grants = new LinkedHashMap<String, Grant>();

    private KOMEJoinBattleActionTokenService() { }

    public synchronized String issue(UUID playerId, KOMEJoinBattleService.Projection projection) {
        return issue(playerId, projection, System.currentTimeMillis());
    }

    synchronized String issue(UUID playerId, KOMEJoinBattleService.Projection projection, long now) {
        purge(now);
        if (playerId == null || projection == null || !projection.isAllowed()
                || projection.conflictId.length() == 0 || projection.conflictRevision < 1L)
            return "";
        while (grants.size() >= MAX_ACTIVE_TOKENS)
            grants.remove(grants.keySet().iterator().next());
        String token;
        do { token = randomToken(); }
        while (grants.containsKey(token));
        Set<String> companies = new LinkedHashSet<String>();
        for (KOMEJoinBattleService.EligibleCompany company : projection.eligibleCompanies)
            companies.add(company.companyId);
        grants.put(token, new Grant(token, playerId, projection.tileId, projection.conflictId,
            projection.conflictRevision, companies, now + TOKEN_LIFETIME_MILLIS));
        return token;
    }

    public synchronized Validation validate(String token, UUID playerId, String tileId,
            String conflictId, long revision, String companyId) {
        return validate(token, playerId, tileId, conflictId, revision, companyId,
            System.currentTimeMillis());
    }

    synchronized Validation validate(String token, UUID playerId, String tileId,
            String conflictId, long revision, String companyId, long now) {
        purge(now);
        String candidate = clean(token);
        if (!isUsableToken(candidate)) return Validation.ABSENT_OR_EXPIRED;
        Grant grant = grants.get(candidate);
        if (grant == null) return Validation.ABSENT_OR_EXPIRED;
        return grant.matches(playerId, tileId, conflictId, revision, companyId)
            ? Validation.ACCEPTED : Validation.SCOPE_MISMATCH;
    }

    public synchronized void consume(String token) {
        grants.remove(clean(token));
    }

    synchronized void clearForTests() { grants.clear(); }

    /** Canonical wire shape for a transient or receipt-backed Join Battle action token. */
    public static boolean isUsableToken(String token) {
        String candidate = clean(token);
        return candidate.length() == 32 && candidate.matches("[0-9a-f]{32}");
    }

    private void purge(long now) {
        for (String token : new ArrayList<String>(grants.keySet()))
            if (grants.get(token).expiresAtMillis < now) grants.remove(token);
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static String randomToken() {
        byte[] bytes=new byte[16];RANDOM.nextBytes(bytes);
        StringBuilder value=new StringBuilder(32);
        for(byte next:bytes)value.append(String.format("%02x",Integer.valueOf(next&255)));
        return value.toString();
    }

    private static final class Grant {
        final String token;
        final UUID playerId;
        final String tileId;
        final String conflictId;
        final long revision;
        final Set<String> companies;
        final long expiresAtMillis;

        Grant(String token, UUID playerId, String tileId, String conflictId, long revision,
                Set<String> companies, long expiresAtMillis) {
            this.token=token;this.playerId=playerId;this.tileId=tileId;this.conflictId=conflictId;
            this.revision=revision;this.companies=Collections.unmodifiableSet(
                new LinkedHashSet<String>(companies));this.expiresAtMillis=expiresAtMillis;
        }

        boolean matches(UUID player, String tile, String conflict, long expectedRevision,
                String company) {
            return playerId.equals(player) && tileId.equals(KOMEConquestTile.normalizeId(tile))
                && conflictId.equals(clean(conflict)) && revision == expectedRevision
                && companies.contains(clean(company));
        }
    }
}
