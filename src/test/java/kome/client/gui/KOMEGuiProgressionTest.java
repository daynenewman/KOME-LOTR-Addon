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
}
