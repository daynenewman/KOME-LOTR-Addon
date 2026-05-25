package kome.common.data;

import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketQuotaLedger;
import lotr.common.item.LOTRItemMug;
import lotr.common.item.LOTRItemMug.Vessel;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class KOMEProgressionQuotas {
    private static final String[] QUOTA_IDS = new String[] {
        "serf.food_quota_1", "serf.food_quota_2", "serf.drink_quota", "knight.drop_quota_1", "knight.drop_quota_2"
    };

    public static int processDeposits(KOMEPlayerProgression progression) {
        int accepted = 0;
        for (int slot = 0; slot < progression.getOfferingSlots(); slot++) {
            ItemStack stack = progression.getOffering(slot);
            if (stack == null) {
                continue;
            }
            int remaining = stack.stackSize;
            for (int i = 0; i < QUOTA_IDS.length && remaining > 0; i++) {
                String id = QUOTA_IDS[i];
                Quota quota = parseQuota(progression.getAssignment(id));
                if (quota == null || !matches(stack, quota)) {
                    continue;
                }
                int needed = quota.requiredUnits - progression.getQuotaDelivered(id);
                if (needed <= 0) {
                    continue;
                }
                int taken = Math.min(remaining, needed);
                progression.addQuotaDelivered(id, taken);
                remaining -= taken;
                accepted += taken;
            }
            if (remaining <= 0) {
                progression.setOffering(slot, null);
            } else if (remaining != stack.stackSize) {
                stack.stackSize = remaining;
                progression.setOffering(slot, stack);
            }
        }
        if (accepted > 0) {
            applyCompletedQuotas(progression);
        }
        return accepted;
    }

    public static int applyCompletedQuotas(KOMEPlayerProgression progression) {
        int changed = 0;
        changed += completeIfMet(progression, "serf.food_quota_1");
        changed += completeIfMet(progression, "serf.food_quota_2");
        changed += completeIfMet(progression, "serf.drink_quota");
        changed += completeIfMet(progression, "knight.drop_quota_1");
        changed += completeIfMet(progression, "knight.drop_quota_2");

        if (isComplete(progression, "serf.food_quota_1") && isComplete(progression, "serf.food_quota_2") && isComplete(progression, "serf.drink_quota")) {
            changed += progression.grant("serf.deliver_quota") ? 1 : 0;
        }
        if (isComplete(progression, "knight.drop_quota_1") && isComplete(progression, "knight.drop_quota_2")) {
            changed += progression.grant("knight.deliver_drops") ? 1 : 0;
        }
        return changed;
    }

    public static void sendQuotaLedger(EntityPlayerMP player, KOMEPlayerProgression progression) {
        KOMEPacketHandler.network.sendTo(new KOMEPacketQuotaLedger(getQuotaStatusLines(progression)), player);
    }

    public static List getQuotaStatusLines(KOMEPlayerProgression progression) {
        List lines = new ArrayList();
        addQuotaLine(lines, progression, "serf.food_quota_1");
        addQuotaLine(lines, progression, "serf.food_quota_2");
        addQuotaLine(lines, progression, "serf.drink_quota");
        addQuotaLine(lines, progression, "knight.drop_quota_1");
        addQuotaLine(lines, progression, "knight.drop_quota_2");
        return lines;
    }

    private static int completeIfMet(KOMEPlayerProgression progression, String id) {
        Quota quota = parseQuota(progression.getAssignment(id));
        if (quota == null) {
            return 0;
        }
        return progression.getQuotaDelivered(id) >= quota.requiredUnits && progression.grant(id) ? 1 : 0;
    }

    private static void addQuotaLine(List lines, KOMEPlayerProgression progression, String id) {
        Quota quota = parseQuota(progression.getAssignment(id));
        if (quota == null) {
            return;
        }
        KOMEProgressionAchievement achievement = KOMEProgressionAchievement.forID(id);
        int deposited = progression.getQuotaDelivered(id);
        int shownDeposited = quota.stacks ? deposited / 64 : deposited;
        int shownRequired = quota.stacks ? quota.requiredUnits / 64 : quota.requiredUnits;
        String unit = quota.stacks ? "stacks" : "units";
        String title = achievement == null ? id : achievement.title;
        String done = deposited >= quota.requiredUnits ? " complete" : "";
        if (quota.drink) {
            String detail = title + ": " + quota.item;
            if (!isBlank(quota.vessel)) {
                detail += " / " + quota.vessel;
            }
            if (!isBlank(quota.toxicity)) {
                detail += " / " + quota.toxicity;
            }
            lines.add(detail);
            lines.add("  Delivered: " + shownDeposited + "/" + shownRequired + " " + unit + done);
        } else {
            lines.add(title + ": " + quota.item);
            lines.add("  Delivered: " + shownDeposited + "/" + shownRequired + " " + unit + done);
        }
    }

    private static boolean matches(ItemStack stack, Quota quota) {
        String wanted = normalize(quota.item);
        String display = normalize(stack.getDisplayName());
        String unlocalized = normalize(stack.getUnlocalizedName());
        if (!display.contains(wanted) && !unlocalized.contains(wanted)) {
            return false;
        }
        if (!quota.drink) {
            return true;
        }
        if (stack.getItem() instanceof LOTRItemMug) {
            return matchesDrinkVessel(stack, quota) && matchesDrinkToxicity(stack, quota);
        }
        String combined = display + unlocalized;
        return combined.contains(normalize(quota.vessel)) && combined.contains(normalize(quota.toxicity));
    }

    private static Quota parseQuota(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("Collect ")) {
            return parseCollectQuota(trimmed);
        }
        if (trimmed.startsWith("Bring ")) {
            return parseBringQuota(trimmed);
        }
        return null;
    }

    private static Quota parseCollectQuota(String text) {
        String rest = text.substring("Collect ".length());
        int firstSpace = rest.indexOf(' ');
        if (firstSpace <= 0) {
            return null;
        }
        int amount = parseInt(rest.substring(0, firstSpace));
        String afterAmount = rest.substring(firstSpace + 1);
        int ofIndex = afterAmount.indexOf(" of ");
        if (amount <= 0 || ofIndex <= 0) {
            return null;
        }
        String unit = afterAmount.substring(0, ofIndex).trim();
        String item = afterAmount.substring(ofIndex + 4).trim();
        boolean stacks = "stacks".equalsIgnoreCase(unit);
        return new Quota(item, stacks ? amount * 64 : amount, stacks);
    }

    private static Quota parseBringQuota(String text) {
        String rest = text.substring("Bring ".length());
        int firstSpace = rest.indexOf(' ');
        if (firstSpace <= 0) {
            return null;
        }
        int amount = parseInt(rest.substring(0, firstSpace));
        int ofIndex = rest.indexOf(" of ");
        if (amount <= 0 || ofIndex <= 0) {
            return null;
        }
        String item = rest.substring(ofIndex + 4).trim();
        String vessel = "";
        String toxicity = "";
        int inIndex = item.indexOf(" in a ");
        if (inIndex > 0) {
            vessel = item.substring(inIndex + 6).trim();
            item = item.substring(0, inIndex).trim();
        }
        int toxicityIndex = vessel.indexOf(" (toxicity:");
        if (toxicityIndex > 0) {
            toxicity = vessel.substring(toxicityIndex + " (toxicity:".length()).replace(")", "").trim();
            vessel = vessel.substring(0, toxicityIndex).trim();
        }
        return new Quota(item, amount, false, true, vessel, toxicity);
    }

    private static boolean matchesDrinkVessel(ItemStack stack, Quota quota) {
        Vessel required = getRequiredVessel(quota.vessel);
        return required != null && LOTRItemMug.getVessel(stack) == required;
    }

    private static boolean matchesDrinkToxicity(ItemStack stack, Quota quota) {
        String subtitle = LOTRItemMug.getStrengthSubtitle(stack);
        return !isBlank(quota.toxicity) && normalize(subtitle).equals(normalize(quota.toxicity));
    }

    private static Vessel getRequiredVessel(String vessel) {
        String value = normalize(vessel);
        if ("mug".equals(value)) {
            return Vessel.MUG;
        }
        if ("ceramicmug".equals(value)) {
            return Vessel.MUG_CLAY;
        }
        if ("goldgoblet".equals(value) || "goldengoblet".equals(value)) {
            return Vessel.GOBLET_GOLD;
        }
        if ("silvergoblet".equals(value)) {
            return Vessel.GOBLET_SILVER;
        }
        if ("coppergoblet".equals(value)) {
            return Vessel.GOBLET_COPPER;
        }
        if ("woodengoblet".equals(value) || "woodencup".equals(value)) {
            return Vessel.GOBLET_WOOD;
        }
        if ("skullcup".equals(value)) {
            return Vessel.SKULL;
        }
        if ("wineglass".equals(value)) {
            return Vessel.GLASS;
        }
        if ("glassbottle".equals(value)) {
            return Vessel.BOTTLE;
        }
        if ("waterskin".equals(value)) {
            return Vessel.SKIN;
        }
        if ("alehorn".equals(value)) {
            return Vessel.HORN;
        }
        if ("goldenalehorn".equals(value) || "goldalehorn".equals(value)) {
            return Vessel.HORN_GOLD;
        }
        return null;
    }

    private static boolean isComplete(KOMEPlayerProgression progression, String id) {
        return progression.isCompleted(KOMEProgressionAchievement.forID(id));
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static class Quota {
        private final String item;
        private final int requiredUnits;
        private final boolean stacks;
        private final boolean drink;
        private final String vessel;
        private final String toxicity;

        private Quota(String item, int requiredUnits, boolean stacks) {
            this(item, requiredUnits, stacks, false, "", "");
        }

        private Quota(String item, int requiredUnits, boolean stacks, boolean drink, String vessel, String toxicity) {
            this.item = item;
            this.requiredUnits = requiredUnits;
            this.stacks = stacks;
            this.drink = drink;
            this.vessel = vessel;
            this.toxicity = toxicity;
        }
    }
}
