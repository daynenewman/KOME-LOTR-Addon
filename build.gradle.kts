plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

val lotrClassesDir = providers.gradleProperty("kome.lotrClassesDir").orElse("../build/classes/java/main")
val lotrResourcesDir = providers.gradleProperty("kome.lotrResourcesDir").orElse("../build/resources/main")
val lotrRuntimeJar = providers.gradleProperty("kome.lotrRuntimeJar").orElse("../build/libs/lotr-dev-local-dev.jar")

dependencies {
    compileOnly(files(lotrClassesDir, lotrResourcesDir))
    runtimeOnly(files(lotrRuntimeJar))
    testImplementation("junit:junit:4.13.2")
    testCompileOnly(files(lotrClassesDir, lotrResourcesDir))
    testRuntimeOnly(files(lotrClassesDir, lotrResourcesDir))
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
    manifest {
        attributes[
            "FMLCorePlugin"
        ] = "kome.core.KOMECorePlugin"
        attributes["FMLCorePluginContainsFMLMod"] = "true"
    }
}

extra["modVersion"] = "1.0.7"
