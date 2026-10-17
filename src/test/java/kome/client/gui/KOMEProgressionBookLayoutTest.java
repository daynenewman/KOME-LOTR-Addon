package kome.client.gui;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import kome.common.KOMEAccessFixture;
import kome.common.data.KOMEProgressionRankSummary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real screen measurement/hit-testing with a controlled font; no GL or live world. */
public class KOMEProgressionBookLayoutTest {
    private static Field field(String name)throws Exception {Field f=KOMEGuiProgression.class.getDeclaredField(name);f.setAccessible(true);return f;}
    private static int measure(KOMEGuiProgression gui,String name)throws Exception {
        Method m=KOMEGuiProgression.class.getDeclaredMethod(name);m.setAccessible(true);return (Integer)m.invoke(gui);
    }
    private static KOMEProgressionRankSummary summary(String title,String objective) {
        return new KOMEProgressionRankSummary("","Knight","Lord","To earn Lord",
            Arrays.asList(new KOMEProgressionRankSummary.Requirement("Commissions",0,3,false,
                Arrays.asList(new KOMEProgressionRankSummary.Requirement("Escort",0,1,false)))),
            "Current commission",title,objective);
    }
    @Test public void longNamesScrollToEndAndFooterAndExpandedHitsUseTheSameGeometry()throws Exception {
        Map<Field,Object> original=new LinkedHashMap<>();
        for(String name:new String[]{"rankSummary","canonicalSummary","leaveRelationshipLabel","abilities"})original.put(field(name),field(name).get(null));
        try {
            Screen screen=new Screen();Minecraft client=KOMEAccessFixture.allocate(Minecraft.class);
            client.fontRenderer=KOMEAccessFixture.allocate(TestFont.class);screen.prepare(client);
            Field view=field("view");view.set(screen,Enum.valueOf((Class)view.getType(),"RANKS"));
            field("abilities").set(null,Collections.emptyList());field("leaveRelationshipLabel").set(null,"");
            GuiButton show=new GuiButton(23,0,234,70,18,"Show Me");show.visible=true;field("buttonShowMe").set(screen,show);
            field("rankSummary").set(null,summary("Escort","Reach the marked shelter."));
            int shortHeight=measure(screen,"rankContentHeight");
            field("rankSummary").set(null,summary("A very long native NPC name and household extending over several lines",
                "Escort the traveller to the marked shelter and protect the traveller along the road. Return to the issuing Liege when ready. Keep your charge close while crossing the hills, wait for them at narrow paths, and speak with them if they need to follow again."));
            int full=measure(screen,"rankContentHeight");assertTrue(full>shortHeight);
            int max=measure(screen,"maxRankScroll");assertTrue(max>0);
            assertEquals(168,full-max);assertEquals(26,measure(screen,"relationshipFooterHeight"));
            field("canonicalSummary").set(null,"Repeated rank, faction and old objective text that must not add a second block.");
            assertEquals(full,measure(screen,"rankContentHeight"));
            show.visible=false;assertEquals(0,measure(screen,"relationshipFooterHeight"));
            assertTrue(measure(screen,"maxRankScroll")<max);
            // Bring the requirement row into the viewport and click it using actual screen input math.
            KOMEProgressionRankSummary s=(KOMEProgressionRankSummary)field("rankSummary").get(null);
            int activity=27+10*(client.fontRenderer.listFormattedStringToWidth(s.activityTitle,184).size()
                +client.fontRenderer.listFormattedStringToWidth(s.activityObjective,184).size());
            field("rankScroll").setInt(screen,activity+14);
            Method click=KOMEGuiProgression.class.getDeclaredMethod("toggleRankRequirement",int.class,int.class);click.setAccessible(true);
            click.invoke(screen,20,55);assertTrue(field("dutiesExpanded").getBoolean(screen));
            assertEquals(full+20,measure(screen,"rankContentHeight"));
        } finally {for(Map.Entry<Field,Object> e:original.entrySet())e.getKey().set(null,e.getValue());}
    }
    static class Screen extends KOMEGuiProgression {void prepare(Minecraft client){mc=client;ySize=256;guiTop=0;guiLeft=0;}}
    public static class TestFont extends FontRenderer {
        private TestFont(){super(null,null,null,false);}
        @Override public List listFormattedStringToWidth(String text,int width){
            List<String> lines=new ArrayList<>();int chars=Math.max(1,width/6);
            for(int i=0;i<text.length();i+=chars)lines.add(text.substring(i,Math.min(text.length(),i+chars)));
            return lines;
        }
    }
}
