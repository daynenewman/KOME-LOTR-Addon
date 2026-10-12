package kome.integration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;
import static org.junit.Assert.*;

/** Development launch contract only; no production identity workaround. */
public class KOMEDevOfflineIdentityConfigurationTest {
    private String buildScript() throws Exception {
        return new String(Files.readAllBytes(Paths.get("build.gradle.kts")), StandardCharsets.UTF_8);
    }
    @Test public void defaultLaunchKeepsRfgIdentityAndOverrideIsClientOptInOnly() throws Exception {
        String source=buildScript();
        assertTrue(source.contains(".map(String::toBoolean).orElse(false)"));
        assertTrue(source.contains("if (name.startsWith(\"runClient\") && devOfflineIdentity.get())"));
        assertTrue(source.contains("userUUID.set(username.map(::offlineDevUuid))"));
        assertEquals("Only the explicitly opted-in client block overrides RFG UUID",1,
            source.split("userUUID\\.set",-1).length-1);
        assertFalse(source.contains("31c4910d-9b69-4725-8969-9ed53ac8a7dc"));
        assertFalse(source.contains("a01a21f5-c117-3c1f-b5d0-e055b927f733"));
    }
    @Test public void offlineIdentityRetainsUtf8DerivationVerifierAndCredentialGuards() throws Exception {
        String source=buildScript();
        assertTrue(source.contains("UUID.nameUUIDFromBytes("));
        assertTrue(source.contains("(\"OfflinePlayer:\" + username).toByteArray(StandardCharsets.UTF_8)"));
        assertTrue(source.contains("tasks.register(\"verifyDevOfflineIdentity\")"));
        assertTrue(source.contains("check(accessToken.get() == \"0\" || accessToken.get() == \"FML\")"));
        assertTrue(source.contains("check(\"--password\" !in calculateArgs())"));
        assertTrue(source.contains("check(value(\"--accessToken\") in listOf(\"0\", \"FML\"))"));
    }
}
