import com.gtnewhorizons.retrofuturagradle.minecraft.RunMinecraftTask
import java.util.UUID
import java.nio.charset.StandardCharsets

plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

dependencies {
    add(
        "devOnlyNonPublishable",
        rfg.deobf(project.files("libs/geckolib-unofficial-1.7.10-1.0.4.jar"))
    )
    add(
        "devOnlyNonPublishable",
        rfg.deobf(project.files("libs/LOTRMod v36.15.jar"))
    )
    add(
        "testCompileOnly",
        rfg.deobf(project.files("libs/LOTRMod v36.15.jar"))
    )
    add(
        "testRuntimeOnly",
        rfg.deobf(project.files("libs/LOTRMod v36.15.jar"))
    )
    // The actual ram impact permission test links the existing production animation interface.
    add("testCompileOnly", rfg.deobf(project.files("libs/geckolib-unofficial-1.7.10-1.0.4.jar")))
    add("testRuntimeOnly", rfg.deobf(project.files("libs/geckolib-unofficial-1.7.10-1.0.4.jar")))

    testImplementation("junit:junit:4.13.2")
}

// Exercise the real legacy GuiScreen event path without adding a graphics dependency version.
configurations.named("testRuntimeOnly") {
    extendsFrom(configurations.getByName("lwjgl2Classpath"))
}

sourceSets.named("main") {
    output.setResourcesDir(java.classesDirectory.get().asFile)
}

tasks.withType<JavaCompile>().configureEach {
    doFirst {
        options.compilerArgs.removeAll { it.contains("jabel", ignoreCase = true) }
        options.annotationProcessorPath = files()
        classpath = classpath.filter {
            !it.name.contains("jabel", ignoreCase = true) && !it.name.contains("byte-buddy", ignoreCase = true)
        }
    }
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("KOME-LOTR-Addon")
}

extra["modVersion"] = "1.0.9-integration-g13"

// Explicitly opt in for local dedicated online-mode=false testing. RFG's default UUID is a
// cached Mojang profile even with Gradle --offline; it is NOT Minecraft's offline player UUID.
// This affects development client tasks and regenerated IDE runs only, never a packaged mod.
val devOfflineIdentity = providers.gradleProperty("kome.devOfflineIdentity")
    .map(String::toBoolean).orElse(false)
fun offlineDevUuid(username: String): String = UUID.nameUUIDFromBytes(
    ("OfflinePlayer:" + username).toByteArray(StandardCharsets.UTF_8)
).toString()

tasks.withType<RunMinecraftTask>().configureEach {
    if (name.startsWith("runClient") && devOfflineIdentity.get()) {
        userUUID.set(username.map(::offlineDevUuid))
        doFirst {
            check(accessToken.get() == "0" || accessToken.get() == "FML") {
                "Offline development identity cannot be used with an authenticated access token; omit -Pkome.devOfflineIdentity=true."
            }
            check("--password" !in calculateArgs()) {
                "Offline development identity cannot be used with a login password; omit -Pkome.devOfflineIdentity=true."
            }
            logger.lifecycle("[KOME DEV IDENTITY] task=" + name + " username=" + username.get() + " uuid=" + userUUID.get() + " mode=offline-development")
        }
    }
}

// Inspect the actual RFG launch arguments without starting a client/server or touching a world.
tasks.register("verifyDevOfflineIdentity") {
    group = "verification"
    description = "Verify offline development client identity (use -Pkome.devOfflineIdentity=true)."
    doLast {
        check(devOfflineIdentity.get()) { "Use -Pkome.devOfflineIdentity=true for this check." }
        val clients = tasks.withType<RunMinecraftTask>().filter { it.name.startsWith("runClient") }
        check(clients.isNotEmpty()) { "No development client tasks found." }
        clients.forEach { client ->
            val launchArgs = client.calculateArgs()
            check("--password" !in launchArgs) { client.name + ": login password in offline development mode." }
            fun value(flag: String): String {
                check(launchArgs.count { it == flag } == 1) { client.name + ": expected exactly one " + flag }
                return launchArgs[launchArgs.indexOf(flag) + 1]
            }
            val launchName = value("--username")
            val launchUuid = value("--uuid")
            check(launchName == client.username.get() && launchUuid == offlineDevUuid(launchName)) {
                client.name + ": client identity does not match dedicated offline-server derivation."
            }
            check(value("--accessToken") in listOf("0", "FML")) { client.name + ": authenticated token in offline development mode." }
            logger.lifecycle("[KOME DEV IDENTITY] verified task=" + client.name + " username=" + launchName + " uuid=" + launchUuid)
        }
    }
}
