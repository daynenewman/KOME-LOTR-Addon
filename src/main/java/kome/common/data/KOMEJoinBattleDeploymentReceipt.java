package kome.common.data;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * Immutable physical delivery/recovery receipt. ConflictRecord.PlayerParticipation remains the
 * sole participation-history authority; this object grants no faction, company or movement power.
 */
public final class KOMEJoinBattleDeploymentReceipt {
    public static final int MAX_ACTION_TOKEN_LENGTH = 128;
    public static final int MAX_REASON_LENGTH = 256;
    public static final int MAX_MOUNT_NBT_BYTES = 1024 * 1024;

    public enum State { PENDING_ENTRY, DEPLOYED, PENDING_EGRESS, CLOSED }
    public enum ParticipationRecovery {
        PREEXISTING_ACTIVE, REGISTRATION_REQUIRED, REGISTERED_BY_RECEIPT
    }
    public enum ClosureOutcome { ENTRY_CANCELLED, EGRESS_COMPLETED }
    public enum MountProfile { VANILLA_HORSE, LOTR_HORSE_FAMILY, LOTR_WARG }
    public enum MountTransferPhase {
        NOT_STARTED,
        SOURCE_SNAPSHOT_PERSISTED,
        DESTINATION_PUBLICATION_PENDING,
        DEPLOYMENT_COMPLETE,
        EGRESS_TRANSFER_PENDING,
        TERMINAL
    }
    public enum MountDisposition {
        RETURNED_WITH_PLAYER, DEAD, SEPARATED, UNAVAILABLE
    }

    public static final class Pose {
        public final int dimensionId;
        public final double x;
        public final double y;
        public final double z;
        public final float yaw;
        public final float pitch;

        public Pose(int dimensionId, double x, double y, double z, float yaw, float pitch) {
            requireFinite(x, "X"); requireFinite(y, "Y"); requireFinite(z, "Z");
            requireFinite(yaw, "yaw"); requireFinite(pitch, "pitch");
            this.dimensionId = dimensionId;
            this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch;
        }

        private static void requireFinite(double value, String name) {
            if (Double.isNaN(value) || Double.isInfinite(value))
                throw new IllegalArgumentException("Join Battle pose " + name + " must be finite.");
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Pose)) return false;
            Pose pose = (Pose) other;
            return dimensionId == pose.dimensionId
                && Double.doubleToLongBits(x) == Double.doubleToLongBits(pose.x)
                && Double.doubleToLongBits(y) == Double.doubleToLongBits(pose.y)
                && Double.doubleToLongBits(z) == Double.doubleToLongBits(pose.z)
                && Float.floatToIntBits(yaw) == Float.floatToIntBits(pose.yaw)
                && Float.floatToIntBits(pitch) == Float.floatToIntBits(pose.pitch);
        }

        @Override public int hashCode() {
            long value = Double.doubleToLongBits(x);
            int result = dimensionId;
            result = 31 * result + (int) (value ^ value >>> 32);
            value = Double.doubleToLongBits(y); result = 31 * result + (int) (value ^ value >>> 32);
            value = Double.doubleToLongBits(z); result = 31 * result + (int) (value ^ value >>> 32);
            result = 31 * result + Float.floatToIntBits(yaw);
            return 31 * result + Float.floatToIntBits(pitch);
        }
    }

    private final String receiptId;
    private final String actionToken;
    private final UUID playerId;
    private final String conflictId;
    private final String tileId;
    private final long acceptedConflictRevision;
    private final String factionId;
    private final String selectedCompanyId;
    private final long createdAtMillis;
    private final State state;
    private final long updatedAtMillis;
    private final Long deployedAtMillis;
    private final Long egressRequestedAtMillis;
    private final String egressReason;
    private final Long closedAtMillis;
    private final ClosureOutcome closureOutcome;
    private final Pose returnAnchor;
    private final Pose deploymentDestination;
    private final ParticipationRecovery participationRecovery;
    private final boolean enteredMounted;
    private final UUID mountUuid;
    private final String mountEntityType;
    private final MountProfile mountProfile;
    private final Pose mountSourceAnchor;
    private final MountTransferPhase mountTransferPhase;
    private final NBTTagCompound temporaryMountNbt;
    private final MountDisposition mountDisposition;

    private KOMEJoinBattleDeploymentReceipt(Builder value) {
        receiptId = KOMEJoinBattleReceiptIdAllocator.requireIdentity(value.receiptId);
        actionToken = bounded(value.actionToken, "Join Battle action token", MAX_ACTION_TOKEN_LENGTH);
        playerId = required(value.playerId, "Player UUID");
        conflictId = KOMEConflictIdAllocator.requireIdentity(value.conflictId);
        tileId = KOMEConflictContracts.tile(value.tileId);
        if (value.acceptedConflictRevision < 1L)
            throw new IllegalArgumentException("Accepted conflict revision must be positive.");
        acceptedConflictRevision = value.acceptedConflictRevision;
        factionId = KOMEConflictContracts.faction(value.factionId);
        selectedCompanyId = KOMEConflictContracts.companyId(value.selectedCompanyId);
        createdAtMillis = nonnegative(value.createdAtMillis, "Creation timestamp");
        state = required(value.state, "Receipt state");
        updatedAtMillis = nonnegative(value.updatedAtMillis, "Update timestamp");
        if (updatedAtMillis < createdAtMillis)
            throw new IllegalArgumentException("Receipt update precedes creation.");
        deployedAtMillis = timestamp(value.deployedAtMillis, "Deployment timestamp");
        egressRequestedAtMillis = timestamp(value.egressRequestedAtMillis, "Egress timestamp");
        egressReason = optionalBounded(value.egressReason, "Egress reason", MAX_REASON_LENGTH);
        closedAtMillis = timestamp(value.closedAtMillis, "Closure timestamp");
        closureOutcome = value.closureOutcome;
        returnAnchor = required(value.returnAnchor, "Return anchor");
        deploymentDestination = value.deploymentDestination;
        participationRecovery = required(value.participationRecovery, "Participation recovery state");
        enteredMounted = value.enteredMounted;
        mountUuid = value.mountUuid;
        mountEntityType = optionalBounded(value.mountEntityType, "Mount entity type", 128);
        mountProfile = value.mountProfile;
        mountSourceAnchor = value.mountSourceAnchor;
        mountTransferPhase = value.mountTransferPhase;
        temporaryMountNbt = copyAndValidateNbt(value.temporaryMountNbt);
        mountDisposition = value.mountDisposition;
        validateLifecycle();
        validateMount();
    }

    public static Builder builder() { return new Builder(); }
    public static Builder copyOf(KOMEJoinBattleDeploymentReceipt value) { return new Builder(value); }

    public String getReceiptId() { return receiptId; }
    public String getActionToken() { return actionToken; }
    public UUID getPlayerId() { return playerId; }
    public String getConflictId() { return conflictId; }
    public String getTileId() { return tileId; }
    public long getAcceptedConflictRevision() { return acceptedConflictRevision; }
    public String getFactionId() { return factionId; }
    public String getSelectedCompanyId() { return selectedCompanyId; }
    public long getCreatedAtMillis() { return createdAtMillis; }
    public State getState() { return state; }
    public boolean isOpen() { return state != State.CLOSED; }
    public long getUpdatedAtMillis() { return updatedAtMillis; }
    public Long getDeployedAtMillis() { return deployedAtMillis; }
    public Long getEgressRequestedAtMillis() { return egressRequestedAtMillis; }
    public String getEgressReason() { return egressReason; }
    public Long getClosedAtMillis() { return closedAtMillis; }
    public ClosureOutcome getClosureOutcome() { return closureOutcome; }
    public Pose getReturnAnchor() { return returnAnchor; }
    public Pose getDeploymentDestination() { return deploymentDestination; }
    public ParticipationRecovery getParticipationRecovery() { return participationRecovery; }
    public boolean isEnteredMounted() { return enteredMounted; }
    public UUID getMountUuid() { return mountUuid; }
    public String getMountEntityType() { return mountEntityType; }
    public MountProfile getMountProfile() { return mountProfile; }
    public Pose getMountSourceAnchor() { return mountSourceAnchor; }
    public MountTransferPhase getMountTransferPhase() { return mountTransferPhase; }
    public NBTTagCompound getTemporaryMountNbt() {
        return temporaryMountNbt == null ? null : (NBTTagCompound) temporaryMountNbt.copy();
    }
    public MountDisposition getMountDisposition() { return mountDisposition; }

    boolean sameImmutableIdentity(KOMEJoinBattleDeploymentReceipt other) {
        return other != null && receiptId.equals(other.receiptId)
            && actionToken.equals(other.actionToken) && playerId.equals(other.playerId)
            && conflictId.equals(other.conflictId) && tileId.equals(other.tileId)
            && acceptedConflictRevision == other.acceptedConflictRevision
            && factionId.equals(other.factionId) && selectedCompanyId.equals(other.selectedCompanyId)
            && createdAtMillis == other.createdAtMillis && returnAnchor.equals(other.returnAnchor)
            && enteredMounted == other.enteredMounted
            && equalsNullable(mountUuid, other.mountUuid)
            && mountEntityType.equals(other.mountEntityType)
            && mountProfile == other.mountProfile
            && equalsNullable(mountSourceAnchor, other.mountSourceAnchor);
    }

    static boolean isForwardTransition(State from, State to) {
        if (from == to) return from != State.CLOSED;
        return from == State.PENDING_ENTRY && (to == State.DEPLOYED
                || to == State.PENDING_EGRESS || to == State.CLOSED)
            || from == State.DEPLOYED && to == State.PENDING_EGRESS
            || from == State.PENDING_EGRESS && to == State.CLOSED;
    }

    private void validateLifecycle() {
        validateNotAfter(deployedAtMillis, "Deployment");
        validateNotAfter(egressRequestedAtMillis, "Egress");
        validateNotAfter(closedAtMillis, "Closure");
        if (deployedAtMillis != null && deploymentDestination == null)
            throw new IllegalArgumentException("A deployed receipt requires a resolved destination.");
        switch (state) {
            case PENDING_ENTRY:
                requireAbsent(deployedAtMillis, "deployment timestamp");
                requireAbsent(egressRequestedAtMillis, "egress timestamp");
                requireAbsent(closedAtMillis, "closure timestamp");
                if (!egressReason.isEmpty() || closureOutcome != null)
                    throw new IllegalArgumentException("Pending entry cannot contain egress/closure facts.");
                break;
            case DEPLOYED:
                requirePresent(deployedAtMillis, "deployment timestamp");
                requirePresent(deploymentDestination, "deployment destination");
                requireAbsent(egressRequestedAtMillis, "egress timestamp");
                requireAbsent(closedAtMillis, "closure timestamp");
                if (!egressReason.isEmpty() || closureOutcome != null)
                    throw new IllegalArgumentException("Deployed receipt cannot contain egress/closure facts.");
                break;
            case PENDING_EGRESS:
                requirePresent(egressRequestedAtMillis, "egress timestamp");
                if (egressReason.isEmpty())
                    throw new IllegalArgumentException("Pending egress requires a reason.");
                requireAbsent(closedAtMillis, "closure timestamp");
                if (closureOutcome != null)
                    throw new IllegalArgumentException("Pending egress cannot contain a closure outcome.");
                break;
            case CLOSED:
                requirePresent(closedAtMillis, "closure timestamp");
                requirePresent(closureOutcome, "closure outcome");
                if (egressRequestedAtMillis != null && egressReason.isEmpty())
                    throw new IllegalArgumentException("Recorded egress requires a reason.");
                if (egressRequestedAtMillis == null && !egressReason.isEmpty())
                    throw new IllegalArgumentException("Egress reason requires an egress timestamp.");
                if (closureOutcome == ClosureOutcome.ENTRY_CANCELLED && deployedAtMillis != null)
                    throw new IllegalArgumentException("A deployed receipt cannot close as an entry cancellation.");
                if (closureOutcome == ClosureOutcome.EGRESS_COMPLETED
                        && (deployedAtMillis == null || egressRequestedAtMillis == null))
                    throw new IllegalArgumentException("Completed egress requires prior deployment and egress request.");
                break;
            default: throw new IllegalArgumentException("Unsupported receipt state.");
        }
        if (egressRequestedAtMillis != null && deployedAtMillis != null
                && egressRequestedAtMillis < deployedAtMillis)
            throw new IllegalArgumentException("Egress precedes deployment.");
        if (closedAtMillis != null && egressRequestedAtMillis != null
                && closedAtMillis < egressRequestedAtMillis)
            throw new IllegalArgumentException("Closure precedes egress.");
        if (deployedAtMillis != null
                && participationRecovery == ParticipationRecovery.REGISTRATION_REQUIRED)
            throw new IllegalArgumentException("Deployed receipt cannot retain pending participation registration.");
    }

    private void validateMount() {
        if (!enteredMounted) {
            if (mountUuid != null || !mountEntityType.isEmpty() || mountProfile != null
                    || mountSourceAnchor != null || mountTransferPhase != null
                    || temporaryMountNbt != null || mountDisposition != null)
                throw new IllegalArgumentException("Unmounted receipt contains mounted-transfer authority.");
            return;
        }
        requirePresent(mountUuid, "mount UUID");
        bounded(mountEntityType, "Mount entity type", 128);
        if (!mountEntityType.matches("[A-Za-z0-9_.:-]+"))
            throw new IllegalArgumentException("Mount entity type is malformed.");
        requirePresent(mountProfile, "mount profile");
        requirePresent(mountSourceAnchor, "mount source anchor");
        requirePresent(mountTransferPhase, "mount transfer phase");
        if (mountSourceAnchor.dimensionId != returnAnchor.dimensionId)
            throw new IllegalArgumentException("Mounted entry source dimensions disagree.");
        if (deploymentDestination != null
                && deploymentDestination.dimensionId != mountSourceAnchor.dimensionId)
            throw new IllegalArgumentException("Mounted cross-dimension Join Battle is unsupported.");
        boolean pendingNbt = mountTransferPhase == MountTransferPhase.SOURCE_SNAPSHOT_PERSISTED
            || mountTransferPhase == MountTransferPhase.DESTINATION_PUBLICATION_PENDING
            || mountTransferPhase == MountTransferPhase.EGRESS_TRANSFER_PENDING;
        if (pendingNbt != (temporaryMountNbt != null))
            throw new IllegalArgumentException("Temporary mount NBT disagrees with transfer phase.");
        if (state == State.PENDING_ENTRY
                && mountTransferPhase != MountTransferPhase.NOT_STARTED
                && mountTransferPhase != MountTransferPhase.SOURCE_SNAPSHOT_PERSISTED
                && mountTransferPhase != MountTransferPhase.DESTINATION_PUBLICATION_PENDING)
            throw new IllegalArgumentException("Pending mounted entry has an invalid transfer phase.");
        if (state == State.DEPLOYED
                && mountTransferPhase != MountTransferPhase.DEPLOYMENT_COMPLETE)
            throw new IllegalArgumentException("Mounted deployment requires completed entry transfer.");
        if (state == State.PENDING_EGRESS
                && mountTransferPhase != MountTransferPhase.NOT_STARTED
                && mountTransferPhase != MountTransferPhase.DEPLOYMENT_COMPLETE
                && mountTransferPhase != MountTransferPhase.EGRESS_TRANSFER_PENDING)
            throw new IllegalArgumentException("Pending mounted egress has an invalid transfer phase.");
        if (mountTransferPhase == MountTransferPhase.TERMINAL) {
            if (state != State.CLOSED || mountDisposition == null)
                throw new IllegalArgumentException("Terminal mount transfer requires a closed receipt and disposition.");
        } else if (mountDisposition != null) {
            throw new IllegalArgumentException("Mount disposition is only valid for terminal transfer.");
        }
        if (state == State.CLOSED && mountTransferPhase != MountTransferPhase.TERMINAL)
            throw new IllegalArgumentException("Closed mounted receipt requires terminal mount transfer.");
    }

    private void validateNotAfter(Long value, String name) {
        if (value != null && value > updatedAtMillis)
            throw new IllegalArgumentException(name + " timestamp follows last update.");
    }

    private Long timestamp(Long value, String name) {
        if (value == null) return null;
        long checked = nonnegative(value, name);
        if (checked < createdAtMillis)
            throw new IllegalArgumentException(name + " precedes receipt creation.");
        return checked;
    }

    private static NBTTagCompound copyAndValidateNbt(NBTTagCompound source) {
        if (source == null) return null;
        NBTTagCompound copy = (NBTTagCompound) source.copy();
        if (copy.hasNoTags()) throw new IllegalArgumentException("Temporary mount NBT cannot be empty.");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            CompressedStreamTools.write(copy, output);
            output.flush();
            if (bytes.size() > MAX_MOUNT_NBT_BYTES)
                throw new IllegalArgumentException("Temporary mount NBT exceeds " + MAX_MOUNT_NBT_BYTES + " bytes.");
        } catch (IOException invalid) {
            throw new IllegalArgumentException("Temporary mount NBT could not be encoded.", invalid);
        }
        return copy;
    }

    private static String bounded(String value, String name, int maximum) {
        return KOMEConflictContracts.text(value, name, maximum);
    }
    private static String optionalBounded(String value, String name, int maximum) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maximum)
            throw new IllegalArgumentException(name + " must be at most " + maximum + " characters.");
        return normalized;
    }
    private static long nonnegative(long value, String name) {
        return KOMEConflictContracts.nonnegative(value, name);
    }
    private static <T> T required(T value, String name) {
        return KOMEConflictContracts.required(value, name);
    }
    private static void requirePresent(Object value, String name) {
        if (value == null) throw new IllegalArgumentException("Receipt " + name + " is required.");
    }
    private static void requireAbsent(Object value, String name) {
        if (value != null) throw new IllegalArgumentException("Receipt " + name + " must be absent.");
    }
    private static boolean equalsNullable(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }

    public static final class Builder {
        private String receiptId = "";
        private String actionToken = "";
        private UUID playerId;
        private String conflictId = "";
        private String tileId = "";
        private long acceptedConflictRevision;
        private String factionId = "";
        private String selectedCompanyId = "";
        private long createdAtMillis;
        private State state = State.PENDING_ENTRY;
        private long updatedAtMillis;
        private Long deployedAtMillis;
        private Long egressRequestedAtMillis;
        private String egressReason = "";
        private Long closedAtMillis;
        private ClosureOutcome closureOutcome;
        private Pose returnAnchor;
        private Pose deploymentDestination;
        private ParticipationRecovery participationRecovery = ParticipationRecovery.REGISTRATION_REQUIRED;
        private boolean enteredMounted;
        private UUID mountUuid;
        private String mountEntityType = "";
        private MountProfile mountProfile;
        private Pose mountSourceAnchor;
        private MountTransferPhase mountTransferPhase;
        private NBTTagCompound temporaryMountNbt;
        private MountDisposition mountDisposition;

        public Builder() { }
        private Builder(KOMEJoinBattleDeploymentReceipt value) {
            receiptId=value.receiptId; actionToken=value.actionToken; playerId=value.playerId;
            conflictId=value.conflictId; tileId=value.tileId; acceptedConflictRevision=value.acceptedConflictRevision;
            factionId=value.factionId; selectedCompanyId=value.selectedCompanyId;
            createdAtMillis=value.createdAtMillis; state=value.state; updatedAtMillis=value.updatedAtMillis;
            deployedAtMillis=value.deployedAtMillis; egressRequestedAtMillis=value.egressRequestedAtMillis;
            egressReason=value.egressReason; closedAtMillis=value.closedAtMillis; closureOutcome=value.closureOutcome;
            returnAnchor=value.returnAnchor; deploymentDestination=value.deploymentDestination;
            participationRecovery=value.participationRecovery; enteredMounted=value.enteredMounted;
            mountUuid=value.mountUuid; mountEntityType=value.mountEntityType; mountProfile=value.mountProfile;
            mountSourceAnchor=value.mountSourceAnchor; mountTransferPhase=value.mountTransferPhase;
            temporaryMountNbt=value.getTemporaryMountNbt(); mountDisposition=value.mountDisposition;
        }

        public Builder receiptId(String value){receiptId=value;return this;}
        public Builder actionToken(String value){actionToken=value;return this;}
        public Builder playerId(UUID value){playerId=value;return this;}
        public Builder conflictId(String value){conflictId=value;return this;}
        public Builder tileId(String value){tileId=value;return this;}
        public Builder acceptedConflictRevision(long value){acceptedConflictRevision=value;return this;}
        public Builder factionId(String value){factionId=value;return this;}
        public Builder selectedCompanyId(String value){selectedCompanyId=value;return this;}
        public Builder createdAtMillis(long value){createdAtMillis=value;return this;}
        public Builder state(State value){state=value;return this;}
        public Builder updatedAtMillis(long value){updatedAtMillis=value;return this;}
        public Builder deployedAtMillis(Long value){deployedAtMillis=value;return this;}
        public Builder egressRequestedAtMillis(Long value){egressRequestedAtMillis=value;return this;}
        public Builder egressReason(String value){egressReason=value;return this;}
        public Builder closedAtMillis(Long value){closedAtMillis=value;return this;}
        public Builder closureOutcome(ClosureOutcome value){closureOutcome=value;return this;}
        public Builder returnAnchor(Pose value){returnAnchor=value;return this;}
        public Builder deploymentDestination(Pose value){deploymentDestination=value;return this;}
        public Builder participationRecovery(ParticipationRecovery value){participationRecovery=value;return this;}
        public Builder enteredMounted(boolean value){enteredMounted=value;return this;}
        public Builder mountUuid(UUID value){mountUuid=value;return this;}
        public Builder mountEntityType(String value){mountEntityType=value;return this;}
        public Builder mountProfile(MountProfile value){mountProfile=value;return this;}
        public Builder mountSourceAnchor(Pose value){mountSourceAnchor=value;return this;}
        public Builder mountTransferPhase(MountTransferPhase value){mountTransferPhase=value;return this;}
        public Builder temporaryMountNbt(NBTTagCompound value){temporaryMountNbt=value==null?null:(NBTTagCompound)value.copy();return this;}
        public Builder mountDisposition(MountDisposition value){mountDisposition=value;return this;}
        public KOMEJoinBattleDeploymentReceipt build(){return new KOMEJoinBattleDeploymentReceipt(this);}
    }
}
