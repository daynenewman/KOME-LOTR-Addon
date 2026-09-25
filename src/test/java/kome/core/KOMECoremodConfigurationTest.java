package kome.core;

import com.fuzs.aquaacrobatics.core.AquaAcrobaticsCore;
import com.fuzs.aquaacrobatics.core.asm.AquaEntityPlayerTransformer;
import com.fuzs.aquaacrobatics.entity.player.IPlayerResizeable;
import cpw.mods.fml.common.Mod;
import kome.common.KOMEAddon;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class KOMECoremodConfigurationTest {
    private static final String CORE_PLUGIN_CLASS = "kome.core.KOMECorePlugin";
    private static final String RESIZEABLE_INTERFACE = Type.getInternalName(IPlayerResizeable.class);

    @Test
    public void gtnhGradleOwnsCorePluginMetadataWithoutDuplicateManifestConfiguration() throws Exception {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(projectFile("gradle.properties"))) {
            properties.load(input);
        }

        String modGroup = properties.getProperty("modGroup").trim();
        String coreModClass = properties.getProperty("coreModClass").trim();
        assertEquals(CORE_PLUGIN_CLASS, modGroup + "." + coreModClass);
        assertEquals("false", properties.getProperty("containsMixinsAndOrCoreModOnly").trim());

        String buildScript = new String(
            Files.readAllBytes(projectFile("build.gradle.kts")),
            StandardCharsets.UTF_8);
        assertFalse(buildScript.contains("FMLCorePlugin"));
    }

    @Test
    public void realDevelopmentEntityPlayerReceivesResizeableInterfaceInheritedByClientPlayer() throws Exception {
        new AquaAcrobaticsCore().injectData(
            Collections.<String, Object>singletonMap("runtimeDeobfuscationEnabled", false));

        byte[] transformed = new AquaEntityPlayerTransformer().transform(
            EntityPlayer.class.getName(),
            EntityPlayer.class.getName(),
            classBytes(EntityPlayer.class));
        ClassNode entityPlayer = new ClassNode();
        new ClassReader(transformed).accept(entityPlayer, ClassReader.SKIP_CODE);

        assertEquals(1, Collections.frequency(entityPlayer.interfaces, RESIZEABLE_INTERFACE));
        assertTrue(EntityPlayer.class.isAssignableFrom(EntityClientPlayerMP.class));
    }

    @Test
    public void corePluginAndNormalModEntryPointRemainDiscoverable() {
        assertTrue(cpw.mods.fml.relauncher.IFMLLoadingPlugin.class.isAssignableFrom(KOMECorePlugin.class));
        assertNotNull(KOMEAddon.class.getAnnotation(Mod.class));
    }

    private static byte[] classBytes(Class<?> type) throws IOException {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream input = KOMECoremodConfigurationTest.class.getResourceAsStream(resource)) {
            assertNotNull(resource, input);
            byte[] buffer = new byte[8192];
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static Path projectFile(String name) {
        return Paths.get("").toAbsolutePath().normalize().resolve(name);
    }
}
