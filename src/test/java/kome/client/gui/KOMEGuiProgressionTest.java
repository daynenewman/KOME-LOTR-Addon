package kome.client.gui;

import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

public class KOMEGuiProgressionTest {
    @Test
    public void finalProgressionKeepsInternalKeyButDisplaysPrince() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get("src/main/java/kome/client/gui/KOMEGuiProgression.java")), Charset.forName("UTF-8"));
        assertEquals(true, source.contains("\"prince_king\""));
        assertEquals(true, source.contains("\"Prince\""));
        assertEquals(false, source.contains("\"Prince / King\""));
    }

    @Test
    public void relationshipDepartureRequiresNativeConfirmationBeforeSending() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get("src/main/java/kome/client/gui/KOMEGuiProgression.java")), Charset.forName("UTF-8"));
        assertEquals(true, source.contains("implements GuiYesNoCallback"));
        assertEquals(true, source.contains("new GuiYesNo(this, title, warning, \"Leave\", \"Cancel\", action)"));
        assertEquals(true, source.contains("if (result && (id == KOMEPacketProgressionRelationshipAction.LEAVE_MASTER || id == KOMEPacketProgressionRelationshipAction.LEAVE_LIEGE))"));
        assertEquals(true, source.contains("Serfdom duties and liege, Trial, and gift progress will be lost"));
        assertEquals(true, source.contains("Trial progress for this liege will be lost"));
    }
}
