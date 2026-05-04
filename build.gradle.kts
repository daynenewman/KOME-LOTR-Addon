plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

val lotrClassesDir = providers.gradleProperty("kome.lotrClassesDir").orElse("../build/classes/java/main")
val lotrResourcesDir = providers.gradleProperty("kome.lotrResourcesDir").orElse("../build/resources/main")

dependencies {
    compileOnly(files(lotrClassesDir, lotrResourcesDir))
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

extra["modVersion"] = "dev-local"
