package kome.common.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ChangeDecision {
    public enum Status { ALLOWED, DEFERRED }

    private final Status status;
    private final List<String> reasons;
    private final List<String> blockingKeys;
    private ChangeDecision(Status status, List<String> reasons, List<String> blockingKeys) {
        this.status = status;
        this.reasons = Collections.unmodifiableList(new ArrayList<String>(reasons));
        this.blockingKeys = Collections.unmodifiableList(new ArrayList<String>(blockingKeys));
    }
    static ChangeDecision allowed() {
        return new ChangeDecision(Status.ALLOWED, Collections.<String>emptyList(),
                Collections.<String>emptyList());
    }

    static ChangeDecision deferred(String reason, List<String> keys) {
        List<String> sorted = new ArrayList<String>(keys);
        Collections.sort(sorted);
        return new ChangeDecision(Status.DEFERRED, Collections.singletonList(reason), sorted);
    }

    public boolean isAllowed() { return status == Status.ALLOWED; }
    public Status getStatus() { return status; }
    public List<String> getReasons() { return reasons; }
    public List<String> getBlockingKeys() { return blockingKeys; }
}
