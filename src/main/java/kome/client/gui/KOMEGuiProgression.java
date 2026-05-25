package kome.client.gui;

import kome.client.KOMEMinecraftClient;
import kome.common.data.KOMEProgressionAchievement;
import kome.common.data.KOMEProgressionTaskGenerator;
import lotr.client.gui.LOTRGuiAchievements;
import lotr.client.gui.LOTRGuiMenuBase;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.RenderHelper;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class KOMEGuiProgression extends LOTRGuiMenuBase {
    private static final String[] GROUPS = new String[] {"baseline", "wanderer", "serf", "knight", "lord", "prince_king"};
    private static final String[] GROUP_NAMES = new String[] {"Permissions", "Wanderer", "Serf", "Knight", "Lord", "Prince / King"};
    private static String playerName = "";
    private static Set completed = new HashSet();
    private static Map assignments = new HashMap();

    private GuiButton buttonCategoryPrev;
    private GuiButton buttonCategoryNext;
    private int currentGroup;
    private int scroll;
    private boolean isScrolling;
    private boolean wasMouseDown;

    public static void updateProgressionData(String name, List completedIds) {
        updateProgressionData(name, completedIds, new HashMap());
    }

    public static void updateProgressionData(String name, List completedIds, Map assignmentMap) {
        playerName = name;
        completed = new HashSet(completedIds);
        assignments = assignmentMap == null ? new HashMap() : new HashMap(assignmentMap);
    }

    @Override
    public void actionPerformed(GuiButton button) {
        if (button.enabled) {
            if (button == buttonCategoryPrev) {
                prevGroup();
            } else if (button == buttonCategoryNext) {
                nextGroup();
            } else {
                super.actionPerformed(button);
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        updateScrollbarDrag(mouseX, mouseY);
        drawDefaultBackground();
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        mc.getTextureManager().bindTexture(LOTRGuiAchievements.pageTexture);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);

        List groupAchievements = getGroupAchievements();
        int complete = getCompleteCount(groupAchievements);
        int totalComplete = getCompleteCount(getVisibleAchievements());
        drawCenteredString("KOME Progression", guiLeft + xSize / 2, guiTop - 30, 16777215);
        String owner = playerName == null || playerName.length() == 0 ? "Loading..." : playerName;
        drawCenteredString(owner + " - " + totalComplete + "/" + KOMEProgressionAchievement.ALL.size(), guiLeft + xSize / 2, guiTop - 18, 12632256);
        drawCenteredString(GROUP_NAMES[currentGroup] + " (" + complete + "/" + groupAchievements.size() + ")", guiLeft + xSize / 2, guiTop + 28, 8019267);

        super.drawScreen(mouseX, mouseY, partialTicks);

        drawCategoryBar();
        drawAchievements(groupAchievements);
        drawScrollbar(groupAchievements.size());
        drawAchievementTooltip(mouseX, mouseY, groupAchievements);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int maxScroll = Math.max(0, getGroupAchievements().size() - getVisibleRows());
        if (wheel > 0) {
            scroll = Math.max(0, scroll - 1);
        } else {
            scroll = Math.min(maxScroll, scroll + 1);
        }
    }

    @Override
    public void initGui() {
        xSize = 220;
        ySize = 256;
        super.initGui();
        buttonCategoryPrev = new GuiButton(0, guiLeft + 13, guiTop + 9, 20, 20, "<");
        buttonList.add(buttonCategoryPrev);
        buttonCategoryNext = new GuiButton(1, guiLeft + 187, guiTop + 9, 20, 20, ">");
        buttonList.add(buttonCategoryNext);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        if (button != 0) {
            return;
        }
        List groupAchievements = getGroupAchievements();
        for (int i = 0; i < getVisibleRows() && scroll + i < groupAchievements.size(); i++) {
            KOMEProgressionAchievement achievement = (KOMEProgressionAchievement) groupAchievements.get(scroll + i);
            int offset = 47 + getRowHeight() * i;
            int x0 = guiLeft + 174;
            int y0 = guiTop + offset + 27;
            if (mouseX >= x0 && mouseX < x0 + 24 && mouseY >= y0 && mouseY < y0 + 16 && canUseActionButton(achievement)) {
                if (isComplete(achievement)) {
                    KOMEMinecraftClient.sendChat("/progression uncomplete " + achievement.id);
                } else if (needsRoll(achievement)) {
                    KOMEMinecraftClient.sendChat("/progression roll " + achievement.id);
                } else {
                    KOMEMinecraftClient.sendChat("/progression complete " + achievement.id);
                }
                return;
            }
        }
    }

    private void drawAchievements(List groupAchievements) {
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glDisable(2896);
        GL11.glEnable(32826);
        GL11.glEnable(2903);
        int rows = getVisibleRows();
        if (groupAchievements.isEmpty()) {
            mc.fontRenderer.drawString("No progression steps.", guiLeft + 12, guiTop + 55, 5652783);
            return;
        }
        for (int i = 0; i < rows && scroll + i < groupAchievements.size(); i++) {
            KOMEProgressionAchievement achievement = (KOMEProgressionAchievement) groupAchievements.get(scroll + i);
            boolean done = isComplete(achievement);
            int offset = 47 + getRowHeight() * i;
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            mc.getTextureManager().bindTexture(LOTRGuiAchievements.iconsTexture);
            drawTexturedModalRect(guiLeft + 9, guiTop + offset, 0, done ? 0 : 50, 190, 50);
            drawProgressionIcon(guiLeft + 12, guiTop + offset + 3, done);
            int color = done ? 8019267 : 5652783;
            String title = trimToWidth(achievement.title, 139);
            mc.fontRenderer.drawString(title, guiLeft + 33, guiTop + offset + 5, color);
            String requirement = getRequirementText(achievement);
            drawLimitedSplitString(requirement, guiLeft + 12, guiTop + offset + 23, 160, 2, color);
            drawManualButton(achievement, guiLeft + 174, guiTop + offset + 27, done);
            if (done) {
                mc.getTextureManager().bindTexture(LOTRGuiAchievements.iconsTexture);
                drawTexturedModalRect(guiLeft + 179, guiTop + offset + 2, 190, 17, 16, 16);
            }
        }
        GL11.glDisable(2929);
        GL11.glEnable(3042);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void drawProgressionIcon(int x, int y, boolean done) {
        int fill = done ? 0xFFBFA45B : 0xFF5C5342;
        int edge = done ? 0xFF3B2A11 : 0xFF201C16;
        Gui.drawRect(x, y, x + 16, y + 16, edge);
        Gui.drawRect(x + 1, y + 1, x + 15, y + 15, fill);
        String mark = done ? "\u2713" : "?";
        int color = done ? 0xFF1F2B12 : 0xFFE4D7B0;
        mc.fontRenderer.drawString(mark, x + 5, y + 4, color);
        if (!done) {
            Gui.drawRect(x, y, x + 16, y + 16, 0x55000000);
        }
    }

    private void drawCategoryBar() {
        int catScrollCentre = guiLeft + xSize / 2;
        int catScrollX = catScrollCentre - 76;
        int catScrollY = guiTop + 13;
        int catScrollX1 = catScrollX + 152;
        mc.getTextureManager().bindTexture(LOTRGuiAchievements.iconsTexture);
        drawTexturedModalRect(catScrollX, catScrollY, 0, 100, 152, 10);

        int catWidth = 16;
        int catCentreWidth = 50;
        int catsEitherSide = 152 / catWidth + 1;
        for (int i = -catsEitherSide; i <= catsEitherSide; i++) {
            int index = currentGroup + i;
            while (index < 0) {
                index += GROUPS.length;
            }
            index %= GROUPS.length;
            int width = i == 0 ? catCentreWidth : catWidth;
            int x = catScrollCentre;
            if (i != 0) {
                int signum = Integer.signum(i);
                x += (catCentreWidth + catWidth) / 2 * signum;
                x += (Math.abs(i) - 1) * signum * catWidth;
            }
            int x0 = x - width / 2;
            int x1 = x + width / 2;
            if (x0 < catScrollX) {
                x0 = catScrollX;
            }
            if (x1 > catScrollX1) {
                x1 = catScrollX1;
            }
            if (x1 <= x0) {
                continue;
            }
            float[] color = getGroupColor(index);
            GL11.glColor4f(color[0], color[1], color[2], 1.0f);
            drawTexturedModalRect(x0, catScrollY, x0 - catScrollX, 100, x1 - x0, 10);
        }
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        mc.getTextureManager().bindTexture(LOTRGuiAchievements.iconsTexture);
        drawTexturedModalRect(catScrollX, catScrollY, 0, 110, 152, 10);
    }

    private void drawScrollbar(int size) {
        int scrollBarX0 = guiLeft + 201;
        int scrollBarY0 = guiTop + 48;
        mc.getTextureManager().bindTexture(LOTRGuiAchievements.iconsTexture);
        if (size > getVisibleRows()) {
            int maxScroll = Math.max(1, size - getVisibleRows());
            int offset = (int) (scroll / (float) maxScroll * 181.0f);
            drawTexturedModalRect(scrollBarX0, scrollBarY0 + offset, 190, 0, 10, 17);
        } else {
            drawTexturedModalRect(scrollBarX0, scrollBarY0, 200, 0, 10, 17);
        }
    }

    private void drawManualButton(KOMEProgressionAchievement achievement, int x, int y, boolean done) {
        if (!canUseActionButton(achievement)) {
            return;
        }
        int fill = done ? 0xFF7B4B3D : needsRoll(achievement) ? 0xFF6E4F24 : 0xFF5E713D;
        Gui.drawRect(x, y, x + 24, y + 16, 0xFF2B2117);
        Gui.drawRect(x + 1, y + 1, x + 23, y + 15, fill);
        String text = done ? "X" : needsRoll(achievement) ? "Roll" : "+";
        int textX = x + (24 - mc.fontRenderer.getStringWidth(text)) / 2;
        mc.fontRenderer.drawString(text, textX, y + 4, 0xFFE8D9AA);
    }

    private boolean canUseActionButton(KOMEProgressionAchievement achievement) {
        return canUseManualButton(achievement) || needsRoll(achievement);
    }

    private boolean canUseManualButton(KOMEProgressionAchievement achievement) {
        return achievement != null && !"baseline".equals(achievement.group) && !achievement.defaultUnlocked && !KOMEProgressionAchievement.isAutoManaged(achievement.id);
    }

    private boolean needsRoll(KOMEProgressionAchievement achievement) {
        return KOMEProgressionTaskGenerator.canRoll(achievement.id) && getAssignment(achievement).length() == 0;
    }

    private String getRequirementText(KOMEProgressionAchievement achievement) {
        String assignment = getAssignment(achievement);
        return assignment.length() == 0 ? achievement.requirement : assignment;
    }

    private String getAssignment(KOMEProgressionAchievement achievement) {
        Object value = assignments.get(achievement.id);
        return value == null ? "" : String.valueOf(value);
    }

    private void drawLimitedSplitString(String text, int x, int y, int width, int maxLines, int color) {
        List lines = mc.fontRenderer.listFormattedStringToWidth(text, width);
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            String line = String.valueOf(lines.get(i));
            if (i == maxLines - 1 && lines.size() > maxLines) {
                line = trimToWidth(line, width - mc.fontRenderer.getStringWidth("...")) + "...";
            }
            mc.fontRenderer.drawString(line, x, y + i * 10, color);
        }
    }

    private String trimToWidth(String text, int width) {
        if (mc.fontRenderer.getStringWidth(text) <= width) {
            return text;
        }
        String suffix = "...";
        while (text.length() > 0 && mc.fontRenderer.getStringWidth(text + suffix) > width) {
            text = text.substring(0, text.length() - 1);
        }
        return text + suffix;
    }

    private void drawAchievementTooltip(int mouseX, int mouseY, List groupAchievements) {
        for (int i = 0; i < getVisibleRows() && scroll + i < groupAchievements.size(); i++) {
            KOMEProgressionAchievement achievement = (KOMEProgressionAchievement) groupAchievements.get(scroll + i);
            int offset = 47 + getRowHeight() * i;
            int x0 = guiLeft + 9;
            int y0 = guiTop + offset;
            if (mouseX >= x0 && mouseX < x0 + 190 && mouseY >= y0 && mouseY < y0 + 50) {
                List lines = new ArrayList();
                lines.add(achievement.title);
                lines.addAll(mc.fontRenderer.listFormattedStringToWidth(getRequirementText(achievement), 220));
                if (isComplete(achievement) && canUseManualButton(achievement)) {
                    lines.add("Click X to remove completion.");
                } else if (needsRoll(achievement)) {
                    lines.add("Click Roll to generate this assignment.");
                } else if (canUseManualButton(achievement)) {
                    lines.add("Click + to mark complete.");
                }
                func_146283_a(lines, mouseX, mouseY);
                return;
            }
        }
    }

    private void updateScrollbarDrag(int mouseX, int mouseY) {
        boolean isMouseDown = Mouse.isButtonDown(0);
        int size = getGroupAchievements().size();
        int maxScroll = Math.max(0, size - getVisibleRows());
        int scrollBarX0 = guiLeft + 201;
        int scrollBarX1 = scrollBarX0 + 12;
        int scrollBarY0 = guiTop + 48;
        int scrollBarY1 = scrollBarY0 + 200;
        if (!wasMouseDown && isMouseDown && maxScroll > 0 && mouseX >= scrollBarX0 && mouseX < scrollBarX1 && mouseY >= scrollBarY0 && mouseY < scrollBarY1) {
            isScrolling = true;
        }
        if (!isMouseDown) {
            isScrolling = false;
        }
        wasMouseDown = isMouseDown;
        if (isScrolling) {
            float currentScroll = (mouseY - scrollBarY0 - 8.5f) / (scrollBarY1 - scrollBarY0 - 17.0f);
            currentScroll = Math.max(0.0f, Math.min(1.0f, currentScroll));
            scroll = Math.round(currentScroll * maxScroll);
        }
    }

    private float[] getGroupColor(int index) {
        switch (index) {
            case 0:
                return new float[] {0.74f, 0.62f, 0.34f};
            case 1:
                return new float[] {0.42f, 0.66f, 0.36f};
            case 2:
                return new float[] {0.68f, 0.50f, 0.32f};
            case 3:
                return new float[] {0.58f, 0.62f, 0.72f};
            case 4:
                return new float[] {0.55f, 0.42f, 0.70f};
            case 5:
                return new float[] {0.78f, 0.60f, 0.24f};
            default:
                return new float[] {0.36f, 0.62f, 0.70f};
        }
    }

    private List getGroupAchievements() {
        return KOMEProgressionAchievement.forGroup(GROUPS[currentGroup]);
    }

    private List getVisibleAchievements() {
        List list = new ArrayList();
        for (String group : GROUPS) {
            list.addAll(KOMEProgressionAchievement.forGroup(group));
        }
        return list;
    }

    private int getVisibleRows() {
        return 4;
    }

    private int getRowHeight() {
        return 50;
    }

    private boolean isComplete(KOMEProgressionAchievement achievement) {
        return achievement.defaultUnlocked || completed.contains(achievement.id);
    }

    private int getCompleteCount(List achievements) {
        int count = 0;
        for (Object object : achievements) {
            if (isComplete((KOMEProgressionAchievement) object)) {
                count++;
            }
        }
        return count;
    }

    private void nextGroup() {
        currentGroup++;
        if (currentGroup >= GROUPS.length) {
            currentGroup = 0;
        }
        scroll = 0;
    }

    private void prevGroup() {
        currentGroup--;
        if (currentGroup < 0) {
            currentGroup = GROUPS.length - 1;
        }
        scroll = 0;
    }
}
