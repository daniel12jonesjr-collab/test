plugins {
    `java-library`
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 11
    options.encoding = "UTF-8"
}

// ASM wird in die Jar gepackt, damit der Agent nicht vom Client-Jar abhaengt.
val bundled: Configuration by configurations.creating {
    isTransitive = false
}

dependencies {
    bundled(libs.asm.core)
    bundled(libs.asm.tree)

    compileOnly(libs.asm.core)
    compileOnly(libs.asm.tree)

    testImplementation(libs.asm.core)
    testImplementation(libs.asm.tree)
    testImplementation(libs.asm.commons)
    testImplementation(libs.junit)
    testImplementation("net.runelite:injected-client:${providers.gradleProperty("runelite.injected-client.version").get()}")
}

val historicalClients: Configuration by configurations.creating {
    isTransitive = false
}

dependencies {
    file("historical-clients.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() }.forEach {
        historicalClients("net.runelite:injected-client:$it")
    }
}

tasks.test {
    inputs.files(historicalClients)
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-Dhwid.historicalClients=" + historicalClients.files.joinToString(File.pathSeparator))
    })
}

tasks.jar {
    manifest {
        attributes["Premain-Class"] = "local.hwidguard.HwidAgent"
        attributes["Can-Redefine-Classes"] = "true"
        attributes["Can-Retransform-Classes"] = "true"
    }
    from(bundled.map { zipTree(it) }) {
        exclude("META-INF/**", "module-info.class")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveBaseName = "hwid-guard"
    archiveVersion = ""
}
