package kome.client.gui;

import kome.client.KOMEMinecraftClient;
import kome.common.data.KOMEProgressionAchievement;
import kome.common.data.KOMEProgressionPermissionRegistry;
import kome.common.data.KOMEProgressionRankSummary;
import lotr.client.gui.LOTRGuiAchievements;
import lotr.client.gui.LOTRGuiMenuBase;
import net.minecraft.client.Minecraft;
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
    private static final String[] GROUP_NAMES = new String[] {"Permissions", "Wanderer", "Serf", "Knight", "Lord", "Prince"};
    // --- Layout constants (panel-relative: add to guiLeft/guiTop) ---
    private static final int LIST_TOP = 47;
    private static final int ROW_HEIGHT = 50;
    private static final int SCROLLBAR_X = 201;
    private static final int SCROLLBAR_HIT_WIDTH = 12;
    private static final int SCROLLBAR_THUMB_HEIGHT = 17;
    private static final int LIST_SCROLLBAR_Y = 48;
    private static final int LIST_SCROLLBAR_HEIGHT = 200;
    private static final int RANK_CONTENT_TOP = 54;
    private static final int RANK_CONTENT_BOTTOM_MARGIN = 8;
    private static final int SUMMARY_LINE_HEIGHT = 9;
    private static final int SUMMARY_MAX_LINES = 7;
    private static final int SUMMARY_BOTTOM_PADDING = 9;
    private static final int SUMMARY_DIVIDER_GAP_ABOVE = 5;
    private static final int SUMMARY_DIVIDER_GAP_BELOW = 7;

    private static String playerName = "";
    private static Set<String> completed = new HashSet<String>();
    private static Map<String, String> assignments = new HashMap<String, String>();
    private static String canonicalSummary = "";
    private static KOMEProgressionRankSummary rankSummary = KOMEProgressionRankSummary.EMPTY;
    private static int snapshotWorldIdentity;
    private enum View { ADVANCEMENTS, RANKS }
    private static View lastSelectedView=View.ADVANCEMENTS;

    private GuiButton buttonCategoryPrev;
    private GuiButton buttonCategoryNext;
    private KOMEGuiButton buttonAdvancements;
    private KOMEGuiButton buttonRanks;
    private int currentGroup;
    private int scroll;
    private int rankScroll;
    private boolean isScrolling;
    private boolean wasMouseDown;
    private final boolean focusDuty;
    private boolean dutyFocusApplied;
    private View view;

    public KOMEGuiProgression(){this(false);}
    private KOMEGuiProgression(boolean focusDuty){this.focusDuty=focusDuty;view=focusDuty?View.RANKS:lastSelectedView;if(focusDuty)lastSelectedView=View.RANKS;}
    public static KOMEGuiProgression dutyView(){return new KOMEGuiProgression(true);}

    public static void updateProgressionData(String name, List completedIds) {
        updateProgressionData(name, completedIds, new HashMap());
    }

    public static void updateProgressionData(String name, List completedIds, Map assignmentMap) {
        playerName = name == null ? "" : name;
        Minecraft minecraft = Minecraft.getMinecraft();
        snapshotWorldIdentity = minecraft.theWorld == null ? 0 : System.identityHashCode(minecraft.theWorld);

        Set<String> normalizedCompleted = new HashSet<String>();
        if (completedIds != null) {
            for (Object id : completedIds) {
                if (id != null) {
                    normalizedCompleted.add(String.valueOf(id));
                }
            }
        }
        completed = normalizedCompleted;

        Map<String, String> normalizedAssignments = new HashMap<String, String>();
        if (assignmentMap != null) {
            for (Object keyObject : assignmentMap.keySet()) {
                if (keyObject == null) {
                    continue;
                }
                Object valueObject = assignmentMap.get(keyObject);
                normalizedAssignments.put(
                        String.valueOf(keyObject),
                        valueObject == null ? "" : String.valueOf(valueObject)
                );
            }
        }
        assignments = normalizedAssignments;
    }

    /**
     * Compatibility overload retained for existing packet/proxy callers.
     * Find/leave data is no longer owned or rendered by this screen.
     */
    public static void updateProgressionData(
            String name,
            List completedIds,
            Map assignmentMap,
            String summary,
            String find,
            String leaveType,
            String leaveLabel,
            String leaveName
    ) {
        updateProgressionData(name, completedIds, assignmentMap);
        canonicalSummary = summary == null ? "" : summary;
    }

    /**
     * Compatibility overload retained for existing packet/proxy callers.
     */
    public static void updateProgressionData(
            String name,
            List completedIds,
            Map assignmentMap,
            String summary,
            String find,
            String leaveType,
            String leaveLabel,
            String leaveName,
            KOMEProgressionRankSummary ranks
    ) {
        updateProgressionData(name, completedIds, assignmentMap, summary, find, leaveType, leaveLabel, leaveName);
        rankSummary = ranks == null ? KOMEProgressionRankSummary.EMPTY : ranks;
    }

    public static void resetData() {
        playerName = "";
        completed = new HashSet<String>();
        assignments = new HashMap<String, String>();
        canonicalSummary = "";
        rankSummary = KOMEProgressionRankSummary.EMPTY;
        snapshotWorldIdentity = 0;
    }

    @Override
    public void actionPerformed(GuiButton button) {
        if (button.enabled) {
            if (button == buttonCategoryPrev) {
                prevGroup();
            } else if (button == buttonCategoryNext) {
                nextGroup();
            } else if (button == buttonAdvancements) {
                selectView(View.ADVANCEMENTS);
            } else if (button == buttonRanks) {
                selectView(View.RANKS);
            } else {
                super.actionPerformed(button);
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (view == View.ADVANCEMENTS) {
            updateScrollbarDrag(mouseX, mouseY);
        } else {
            updateRankScrollbarDrag(mouseX, mouseY);
        }
        drawDefaultBackground();
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        mc.getTextureManager().bindTexture(LOTRGuiAchievements.pageTexture);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);

        int totalComplete = getCompleteCount(getVisibleAchievements());
        drawCenteredString("KOME Progression", guiLeft + xSize / 2, guiTop - 30, 16777215);
        String owner = playerName == null || playerName.length() == 0 ? "Loading..." : playerName;
        drawCenteredString(view==View.ADVANCEMENTS?owner + " - " + totalComplete + "/" + KOMEProgressionAchievement.ALL.size():owner, guiLeft + xSize / 2, guiTop - 18, 12632256);
        if(view==View.ADVANCEMENTS)drawAdvancements();
        else drawRanks();
        super.drawScreen(mouseX, mouseY, partialTicks);
        if(view==View.ADVANCEMENTS)drawAchievementTooltip(mouseX, mouseY, getGroupAchievements());
    }

    static String displayNameForGroup(String group) {
        for (int i = 0; i < GROUPS.length; i++) {
            if (GROUPS[i].equals(group)) {
                return GROUP_NAMES[i];
            }
        }
        return "";
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        if(view==View.RANKS){int max=maxRankScroll();rankScroll=Math.max(0,Math.min(max,rankScroll+(wheel>0?-12:12)));return;}
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
        clearStaleDataForCurrentPlayer();
        buttonCategoryPrev = new GuiButton(0, guiLeft + 13, guiTop + 9, 20, 20, "<");
        buttonList.add(buttonCategoryPrev);
        buttonCategoryNext = new GuiButton(1, guiLeft + 187, guiTop + 9, 20, 20, ">");
        buttonList.add(buttonCategoryNext);
        buttonAdvancements=(KOMEGuiButton)new KOMEGuiButton(20,guiLeft+11,guiTop+29,97,18,"Advancements").setStyle(KOMEGuiButton.Style.TAB);
        buttonRanks=(KOMEGuiButton)new KOMEGuiButton(21,guiLeft+112,guiTop+29,97,18,"Ranks").setStyle(KOMEGuiButton.Style.TAB);
        buttonList.add(buttonAdvancements);buttonList.add(buttonRanks);
        refreshViewButtons();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        if (button != 0 || view != View.ADVANCEMENTS) {
            return;
        }

        List<KOMEProgressionAchievement> groupAchievements = getGroupAchievements();
        for (int i = 0; i < getVisibleRows() && scroll + i < groupAchievements.size(); i++) {
            KOMEProgressionAchievement achievement = groupAchievements.get(scroll + i);
            int offset = LIST_TOP + getRowHeight() * i;
            int x0 = guiLeft + 174;
            int y0 = guiTop + offset + 27;

            // The production progression GUI never exposes manual complete/uncomplete actions.
            // Only legitimate assignment rolling remains interactive here.
            if (mouseX >= x0 && mouseX < x0 + 24
                    && mouseY >= y0 && mouseY < y0 + 16
                    && needsRoll(achievement)) {
                KOMEMinecraftClient.sendChat("/progression roll " + achievement.id);
                return;
            }
        }
    }

    private void drawAchievements(List<KOMEProgressionAchievement> groupAchievements) {
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
            KOMEProgressionAchievement achievement = groupAchievements.get(scroll + i);
            boolean done = isComplete(achievement);
            int offset = LIST_TOP + getRowHeight() * i;
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            mc.getTextureManager().bindTexture(LOTRGuiAchievements.iconsTexture);
            drawTexturedModalRect(guiLeft + 9, guiTop + offset, 0, done ? 0 : 50, 190, 50);
            drawProgressionIcon(guiLeft + 12, guiTop + offset + 3, done);
            int color = done ? 8019267 : 5652783;
            String title = trimToWidth(achievement.title, 139);
            mc.fontRenderer.drawString(title, guiLeft + 33, guiTop + offset + 5, color);
            String requirement = getRequirementText(achievement);
            drawLimitedSplitString(requirement, guiLeft + 12, guiTop + offset + 23, 160, 2, color);
            drawRollButton(achievement, guiLeft + 174, guiTop + offset + 27);
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

    private void drawAdvancements(){
        List<KOMEProgressionAchievement> groupAchievements=getGroupAchievements();int complete=getCompleteCount(groupAchievements);
        drawCenteredString(displayNameForGroup(GROUPS[currentGroup])+" ("+complete+"/"+groupAchievements.size()+")",guiLeft+xSize/2,guiTop+1,8019267);
        drawCategoryBar();
        drawAchievements(groupAchievements);
        drawScrollbar(groupAchievements.size());
        drawSummary();
    }

    private void drawSummary() {
        int lines = getSummaryLineCount();
        if (lines == 0) {
            return;
        }
        int summaryHeight = getSummaryHeight();
        int summaryY = guiTop + ySize - summaryHeight;
        KOMEGuiTheme.drawDivider(guiLeft + 12, summaryY - SUMMARY_DIVIDER_GAP_BELOW, 196);
        String[] summaryLines = canonicalSummary.split("\\n");
        for (int i = 0; i < lines; i++) {
            mc.fontRenderer.drawString(trimToWidth(summaryLines[i], 196), guiLeft + 12, summaryY + i * SUMMARY_LINE_HEIGHT, 5652783);
        }
    }

    private void drawRanks(){
        drawRankLadder();
        int max=maxRankScroll();if(focusDuty&&!dutyFocusApplied&&rankSummary.hasActivity()){rankScroll=max;dutyFocusApplied=true;}rankScroll=Math.max(0,Math.min(max,rankScroll));
        int contentTop=guiTop+RANK_CONTENT_TOP,contentBottom=guiTop+ySize-RANK_CONTENT_BOTTOM_MARGIN;
        KOMEGuiTheme.enableScissor(mc,guiLeft+7,contentTop,xSize-14,contentBottom-contentTop);
        int y=contentTop-rankScroll;
        mc.fontRenderer.drawString(rankSummary.promotionTitle,guiLeft+13,y,KOMEGuiTheme.COLOR_BORDER_RED);y+=14;
        for(KOMEProgressionRankSummary.Requirement requirement:rankSummary.requirements){drawRankRequirement(requirement,y);y+=26;}
        if(rankSummary.hasActivity()){
            y+=5;KOMEGuiTheme.drawDivider(guiLeft+12,y,196);y+=7;
            mc.fontRenderer.drawString(rankSummary.activityHeading,guiLeft+13,y,KOMEGuiTheme.COLOR_BORDER_RED);y+=13;
            mc.fontRenderer.drawString(rankSummary.activityTitle,guiLeft+17,y,5652783);y+=12;
            List<String> lines=mc.fontRenderer.listFormattedStringToWidth(rankSummary.activityObjective,184);
            for(Object line:lines){mc.fontRenderer.drawString(String.valueOf(line),guiLeft+17,y,8019267);y+=10;}
        }
        KOMEGuiTheme.disableScissor();
        if(max>0)drawRankScrollbar(max);
    }

    private void drawRankLadder(){
        int y=guiTop+10;String[] names=KOMEProgressionRankSummary.LADDER;
        int[] centers={guiLeft+31,guiLeft+83,guiLeft+137,guiLeft+190};
        for(int i=0;i<names.length;i++){
            String name=names[i];int color=name.equals(rankSummary.currentRank)?0xFF7B2024:name.equals(rankSummary.nextRank)?0xFF9A6A20:ladderIndex(name)<ladderIndex(rankSummary.currentRank)?8019267:5652783;
            String label=name.equals(rankSummary.currentRank)?name.toUpperCase():name.equals(rankSummary.nextRank)?name.toUpperCase():name;
            mc.fontRenderer.drawString(label,centers[i]-mc.fontRenderer.getStringWidth(label)/2,y,color);
            if(i<names.length-1)mc.fontRenderer.drawString("\u2192",(centers[i]+centers[i+1])/2-3,y,8019267);
        }
    }

    private void drawRankRequirement(KOMEProgressionRankSummary.Requirement requirement,int y){
        mc.getTextureManager().bindTexture(LOTRGuiAchievements.iconsTexture);
        drawTexturedModalRect(guiLeft+9,y,0,requirement.complete?0:50,190,24);
        if(requirement.complete)drawTexturedModalRect(guiLeft+13,y+4,190,17,16,16);
        else {Gui.drawRect(guiLeft+16,y+7,guiLeft+25,y+16,0xFF5A171A);Gui.drawRect(guiLeft+17,y+8,guiLeft+24,y+15,0x55FFFFFF);}
        int color=requirement.complete?8019267:5652783;
        String quota=requirement.current+" / "+requirement.required;
        int quotaX=guiLeft+193-mc.fontRenderer.getStringWidth(quota);
        mc.fontRenderer.drawString(trimToWidth(requirement.label,Math.max(20,quotaX-guiLeft-37)),guiLeft+32,y+8,color);
        mc.fontRenderer.drawString(quota,quotaX,y+8,color);
    }

    private int rankContentHeight(){
        int height=14+rankSummary.requirements.size()*26;
        if(rankSummary.hasActivity()){int lines=mc.fontRenderer.listFormattedStringToWidth(rankSummary.activityObjective,184).size();height+=37+lines*10;}
        return height;
    }

    private int maxRankScroll(){
        int visibleHeight = ySize - RANK_CONTENT_BOTTOM_MARGIN - RANK_CONTENT_TOP;
        return Math.max(0, rankContentHeight() - visibleHeight);
    }

    private void drawRankScrollbar(int max) {
        int x = guiLeft + SCROLLBAR_X;
        int y = guiTop + RANK_CONTENT_TOP;
        int trackHeight = ySize - RANK_CONTENT_BOTTOM_MARGIN - RANK_CONTENT_TOP;
        int travel = trackHeight - SCROLLBAR_THUMB_HEIGHT;
        mc.getTextureManager().bindTexture(LOTRGuiAchievements.iconsTexture);
        int offset = Math.round(rankScroll / (float) max * travel);
        drawTexturedModalRect(x, y + offset, 190, 0, 10, SCROLLBAR_THUMB_HEIGHT);
    }

    private void updateRankScrollbarDrag(int mouseX, int mouseY) {
        boolean isMouseDown = Mouse.isButtonDown(0);
        int max = maxRankScroll();
        int scrollBarX0 = guiLeft + SCROLLBAR_X;
        int scrollBarX1 = scrollBarX0 + SCROLLBAR_HIT_WIDTH;
        int scrollBarY0 = guiTop + RANK_CONTENT_TOP;
        int trackHeight = ySize - RANK_CONTENT_BOTTOM_MARGIN - RANK_CONTENT_TOP;
        int scrollBarY1 = scrollBarY0 + trackHeight;
        if (!wasMouseDown && isMouseDown && max > 0 && mouseX >= scrollBarX0 && mouseX < scrollBarX1 && mouseY >= scrollBarY0 && mouseY < scrollBarY1) {
            isScrolling = true;
        }
        if (!isMouseDown) {
            isScrolling = false;
        }
        wasMouseDown = isMouseDown;
        if (isScrolling) {
            int travel = trackHeight - SCROLLBAR_THUMB_HEIGHT;
            float fraction = (mouseY - scrollBarY0 - SCROLLBAR_THUMB_HEIGHT / 2.0f) / travel;
            fraction = Math.max(0.0f, Math.min(1.0f, fraction));
            rankScroll = Math.round(fraction * max);
        }
    }

    private static int ladderIndex(String name) {
        for (int i = 0; i < KOMEProgressionRankSummary.LADDER.length; i++) {
            if (KOMEProgressionRankSummary.LADDER[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private void selectView(View selected) {
        view = selected;
        lastSelectedView = selected;
        scroll = 0;
        rankScroll = 0;
        isScrolling = false;
        refreshViewButtons();
    }

    private void refreshViewButtons() {
        boolean advancements = view == View.ADVANCEMENTS;
        buttonCategoryPrev.visible = buttonCategoryPrev.enabled = advancements;
        buttonCategoryNext.visible = buttonCategoryNext.enabled = advancements;
        buttonAdvancements.setSelected(advancements);
        buttonRanks.setSelected(!advancements);
    }

    private void drawScrollbar(int size) {
        int scrollBarX0 = guiLeft + SCROLLBAR_X;
        int scrollBarY0 = guiTop + LIST_SCROLLBAR_Y;
        mc.getTextureManager().bindTexture(LOTRGuiAchievements.iconsTexture);
        int rows = getVisibleRows();
        if (size > rows) {
            int maxScroll = Math.max(1, size - rows);
            int travel = LIST_SCROLLBAR_HEIGHT - SCROLLBAR_THUMB_HEIGHT;
            int offset = Math.round(scroll / (float) maxScroll * travel);
            drawTexturedModalRect(scrollBarX0, scrollBarY0 + offset, 190, 0, 10, SCROLLBAR_THUMB_HEIGHT);
        } else {
            drawTexturedModalRect(scrollBarX0, scrollBarY0, 200, 0, 10, SCROLLBAR_THUMB_HEIGHT);
        }
    }

    private void drawRollButton(KOMEProgressionAchievement achievement, int x, int y) {
        if (!needsRoll(achievement)) {
            return;
        }

        Gui.drawRect(x, y, x + 24, y + 16, 0xFF2B2117);
        Gui.drawRect(x + 1, y + 1, x + 23, y + 15, 0xFF6E4F24);
        String text = "Roll";
        int textX = x + (24 - mc.fontRenderer.getStringWidth(text)) / 2;
        mc.fontRenderer.drawString(text, textX, y + 4, 0xFFE8D9AA);
    }

    private boolean needsRoll(KOMEProgressionAchievement achievement) {
        return canRoll(achievement) && getAssignment(achievement).length() == 0;
    }

    private boolean canRoll(KOMEProgressionAchievement achievement) {
        if (achievement == null || "baseline".equals(achievement.group)) {
            return false;
        }
        String id = achievement.id;
        return achievement.requirement.toLowerCase().contains("random task")
                || id.startsWith("serf.food_quota")
                || "serf.drink_quota".equals(id)
                || id.startsWith("knight.drop_quota")
                || id.startsWith("knight.faction_")
                || "lord.fell_beast".equals(id);
    }

    private String getRequirementText(KOMEProgressionAchievement achievement) {
        String assignment = getAssignment(achievement);
        return assignment.length() == 0 ? KOMEProgressionPermissionRegistry.requirementText(achievement) : assignment + KOMEProgressionPermissionRegistry.prerequisiteText(achievement);
    }

    private String getAssignment(KOMEProgressionAchievement achievement) {
        String value = assignments.get(achievement.id);
        return value == null ? "" : value;
    }

    private void drawLimitedSplitString(String text, int x, int y, int width, int maxLines, int color) {
        List<String> lines = mc.fontRenderer.listFormattedStringToWidth(text, width);
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

    private void drawAchievementTooltip(int mouseX, int mouseY, List<KOMEProgressionAchievement> groupAchievements) {
        for (int i = 0; i < getVisibleRows() && scroll + i < groupAchievements.size(); i++) {
            KOMEProgressionAchievement achievement = groupAchievements.get(scroll + i);
            int offset = LIST_TOP + getRowHeight() * i;
            int x0 = guiLeft + 9;
            int y0 = guiTop + offset;
            if (mouseX >= x0 && mouseX < x0 + 190 && mouseY >= y0 && mouseY < y0 + 50) {
                List<String> lines = new ArrayList<String>();
                lines.add(achievement.title);
                lines.addAll(mc.fontRenderer.listFormattedStringToWidth(getRequirementText(achievement), 220));
                if (needsRoll(achievement)) {
                    lines.add("Click Roll to generate this assignment.");
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
        int scrollBarX0 = guiLeft + SCROLLBAR_X;
        int scrollBarX1 = scrollBarX0 + SCROLLBAR_HIT_WIDTH;
        int scrollBarY0 = guiTop + LIST_SCROLLBAR_Y;
        int scrollBarY1 = scrollBarY0 + LIST_SCROLLBAR_HEIGHT;
        if (!wasMouseDown && isMouseDown && maxScroll > 0 && mouseX >= scrollBarX0 && mouseX < scrollBarX1 && mouseY >= scrollBarY0 && mouseY < scrollBarY1) {
            isScrolling = true;
        }
        if (!isMouseDown) {
            isScrolling = false;
        }
        wasMouseDown = isMouseDown;
        if (isScrolling) {
            int travel = LIST_SCROLLBAR_HEIGHT - SCROLLBAR_THUMB_HEIGHT;
            float currentScroll = (mouseY - scrollBarY0 - SCROLLBAR_THUMB_HEIGHT / 2.0f) / travel;
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

    @SuppressWarnings("unchecked")
    private List<KOMEProgressionAchievement> getGroupAchievements() {
        return KOMEProgressionAchievement.forGroup(GROUPS[currentGroup]);
    }

    @SuppressWarnings("unchecked")
    private List<KOMEProgressionAchievement> getVisibleAchievements() {
        List<KOMEProgressionAchievement> list = new ArrayList<KOMEProgressionAchievement>();
        for (String group : GROUPS) {
            list.addAll(KOMEProgressionAchievement.forGroup(group));
        }
        return list;
    }

    private int getVisibleRows() {
        int lines = getSummaryLineCount();
        int reserved = lines == 0 ? 0 : getSummaryHeight() + SUMMARY_DIVIDER_GAP_ABOVE + SUMMARY_DIVIDER_GAP_BELOW;
        int available = ySize - LIST_TOP - reserved;
        return Math.max(1, available / ROW_HEIGHT);
    }

    private int getSummaryLineCount() {
        if (canonicalSummary == null || canonicalSummary.length() == 0) {
            return 0;
        }
        return Math.min(canonicalSummary.split("\\n").length, SUMMARY_MAX_LINES);
    }

    private int getSummaryHeight() {
        int lines = getSummaryLineCount();
        return lines == 0 ? 0 : lines * SUMMARY_LINE_HEIGHT + SUMMARY_BOTTOM_PADDING;
    }

    private int getRowHeight() {
        return ROW_HEIGHT;
    }

    private boolean isComplete(KOMEProgressionAchievement achievement) {
        return achievement.defaultUnlocked || completed.contains(achievement.id);
    }

    private int getCompleteCount(List<KOMEProgressionAchievement> achievements) {
        int count = 0;
        for (KOMEProgressionAchievement achievement : achievements) {
            if (isComplete(achievement)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Defensive fallback for missed logout/world-change cleanup. The authoritative packet will
     * repopulate the snapshot after opening the GUI.
     */
    private void clearStaleDataForCurrentPlayer() {
        if (mc == null || mc.thePlayer == null) {
            return;
        }

        int currentWorldIdentity = mc.theWorld == null ? 0 : System.identityHashCode(mc.theWorld);
        boolean wrongWorld = snapshotWorldIdentity != 0
                && currentWorldIdentity != 0
                && snapshotWorldIdentity != currentWorldIdentity;

        String currentPlayerName = mc.thePlayer.getCommandSenderName();
        boolean wrongPlayer = playerName != null
                && playerName.length() > 0
                && currentPlayerName != null
                && !currentPlayerName.equals(playerName);

        if (wrongWorld || wrongPlayer) {
            resetData();
        }
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