package kome.client;

import java.lang.reflect.Field;
import java.util.List;
import lotr.client.gui.LOTRGuiOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.StatCollector;

/** Client-only bridge used by the transformed LOTR Middle-earth Options screen. */
public final class KOMEFactionTitleOptionsBridge {
    private static final int BUTTON_ID = 26010;

    private KOMEFactionTitleOptionsBridge() {
    }

    /** Called once at the end of LOTRGuiOptions.initGui. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void onOptionsInit(LOTRGuiOptions screen) {
        if (screen == null) {
            return;
        }

        Object buttonsObject = getField(screen, "buttonList", "field_146292_n");
        Object anchorObject = getField(screen, "buttonFeminineRank", null);
        if (!(buttonsObject instanceof List) || !(anchorObject instanceof GuiButton)) {
            return;
        }

        List buttons = (List) buttonsObject;
        if (containsTitleModeButton(buttons)) {
            return;
        }

        GuiButton anchor = (GuiButton) anchorObject;
        buttons.add(new TitleModeButton(
            BUTTON_ID,
            anchor.xPosition,
            anchor.yPosition + 24,
            anchor.width,
            anchor.height));
    }

    /** Safe to continue into vanilla handling because TitleModeButton is not a LOTRGuiButtonOptions. */
    public static void handleButton(GuiButton button) {
        if (!(button instanceof TitleModeButton)) {
            return;
        }

        KOMEClientConfig config = KOMEClientConfig.get();
        if (config != null) {
            config.setUseRankTitles(!config.useRankTitles());
        }
        ((TitleModeButton) button).refresh();
    }

    private static Object getField(Object target, String name, String fallbackName) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            Object value = getDeclaredFieldValue(type, target, name);
            if (value != null) {
                return value;
            }
            if (fallbackName != null) {
                value = getDeclaredFieldValue(type, target, fallbackName);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private static Object getDeclaredFieldValue(Class<?> type, Object target, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @SuppressWarnings("rawtypes")
    private static boolean containsTitleModeButton(List buttons) {
        if (buttons == null) {
            return false;
        }
        for (Object element : buttons) {
            if (element instanceof TitleModeButton) {
                return true;
            }
        }
        return false;
    }

    private static final class TitleModeButton extends GuiButton {
        private TitleModeButton(int id, int x, int y, int width, int height) {
            super(id, x, y, width, height, "");
            refresh();
        }

        private void refresh() {
            String state = StatCollector.translateToLocal(
                KOMEClientConfig.useRankTitlesGlobal()
                    ? "kome.gui.options.factionTitleMode.rank"
                    : "kome.gui.options.factionTitleMode.alignment");
            displayString = StatCollector.translateToLocal("kome.gui.options.factionTitleMode")
                + ": " + state;
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY) {
            refresh();
            super.drawButton(mc, mouseX, mouseY);
        }
    }
}
