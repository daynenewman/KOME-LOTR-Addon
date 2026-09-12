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

    testImplementation("junit:junit:4.13.2")
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
    manifest {
        attributes[
            "FMLCorePlugin"
        ] = "kome.core.KOMECorePlugin"
        attributes["FMLCorePluginContainsFMLMod"] = "true"
    }
}

extra["modVersion"] = "1.0.8"
