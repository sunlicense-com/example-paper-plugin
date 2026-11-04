plugins {
    java
    // Puts the SunLicense SDK inside the plugin jar under your own package (see shadowJar below).
    id("com.gradleup.shadow") version "9.6.1"
    // ./gradlew runServer starts a local Paper server with the plugin installed.
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

// Your product's code in SunLicense. Set it (-PsunlicenseProduct=… or ORG_GRADLE_PROJECT_sunlicenseProduct) for
// release builds: see "Core unlock" at the end of this file.
val sunlicenseProduct = (project.findProperty("sunlicenseProduct") ?: "").toString().trim()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Built against 1.21.11 (Java 21), so the jar runs on 1.21.x and on 26.x servers.
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    // The official SunLicense Java SDK (MIT, no dependencies): https://github.com/sunlicense-com/sunlicense-java
    implementation("com.sunlicense:sunlicense:0.0.1")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Paper ships Gson at runtime; the tests need their own copy.
    testImplementation("com.google.code.gson:gson:2.13.2")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    processResources {
        // SunLicense settings from gradle.properties, -P flags or ORG_GRADLE_PROJECT_* environment variables.
        val values = mapOf(
            "version" to project.version.toString(),
            "sunlicenseUrl" to (project.findProperty("sunlicenseUrl") ?: "https://api.sunlicense.com").toString(),
            "sunlicenseApiKey" to (project.findProperty("sunlicenseApiKey") ?: "").toString(),
            "sunlicensePublicKey" to (project.findProperty("sunlicensePublicKey") ?: "").toString(),
            "sunlicenseProduct" to sunlicenseProduct,
        )
        inputs.properties(values)
        filesMatching(listOf("plugin.yml", "sunlicense.properties")) {
            expand(values)
        }
    }

    shadowJar {
        archiveClassifier = ""
        // Relocate the SDK so two plugins with different SDK versions never clash on one server.
        relocate("com.sunlicense", "${project.group}.myplugin.libs.sunlicense")
    }

    jar {
        archiveClassifier = "plain"
    }

    build {
        dependsOn(shadowJar)
    }

    test {
        useJUnitPlatform()
    }

    runServer {
        minecraftVersion("1.21.11")
    }
}

// ---- Core unlock and tamper reports (https://docs.sunlicense.com/guide/protection) ----
// With -PsunlicenseProduct=<product code> and the SunLicense CLI signed in (or SUNLICENSE_TOKEN set),
// `./gradlew build` also writes build/release/<jar>: the same plugin, but the core package is encrypted with
// `sunlicense seal` (only a valid license check unlocks it) and carries the SHA-256 of your other classes, so a
// modified copy is reported. Ship that jar. Without the property, build/libs has a development build with the core
// in plain. Rename both packages here if you rename yours.
val corePackage = "com/example/myplugin/core/"
val ownPackage = "com/example/myplugin/"
val sealDir = layout.buildDirectory.dir("sunlicense")

val unpackJar = tasks.register<Sync>("unpackJar") {
    from(zipTree(tasks.shadowJar.flatMap { it.archiveFile }))
    into(sealDir.map { it.dir("unpacked") })
}

// The release jar's content without the core.
val plainClasses = tasks.register<Sync>("plainClasses") {
    from(unpackJar) { exclude("$corePackage**") }
    into(sealDir.map { it.dir("plain") })
}

// The classes whose digests go into the seal: your own, as they are in the final jar.
val digestClasses = tasks.register<Sync>("digestClasses") {
    from(plainClasses) { include("$ownPackage**/*.class") }
    into(sealDir.map { it.dir("digest") })
}

val coreJar = tasks.register<Zip>("coreJar") {
    from(unpackJar) { include("$corePackage**") }
    destinationDirectory = sealDir
    archiveFileName = "core.jar"
}

val sealCore = tasks.register<Exec>("sealCore") {
    dependsOn(digestClasses, coreJar)
    val product = sunlicenseProduct   // a local copy: the configuration cache can't keep script references
    val cli = (project.findProperty("sunlicenseCli") ?: "sunlicense").toString()   // or a full path to the CLI
    val digest = sealDir.get().dir("digest").asFile
    val core = sealDir.get().file("core.jar").asFile
    val sealed = sealDir.get().file("core.sealed").asFile
    inputs.dir(digest)
    inputs.file(core)
    outputs.file(sealed)
    // Run from the digest folder, so each digest is named like the class's entry in the jar.
    workingDir(digest)
    commandLine(cli, "seal", core.absolutePath, "--product", product, "--digest", "**/*.class")
    doFirst {
        if (product.isEmpty()) {
            throw GradleException("Set -PsunlicenseProduct=<your product code> to seal the core")
        }
        standardOutput = sealed.outputStream()
    }
    doLast {
        standardOutput.close()
        if (!sealed.readText().trim().startsWith("SLS1.")) {
            throw GradleException("`sunlicense seal` didn't print sealed data; run it by hand to see why")
        }
    }
}

val releaseJar = tasks.register<Zip>("releaseJar") {
    dependsOn(sealCore, plainClasses)
    val plain = sealDir.map { it.dir("plain") }
    from(plain) { include("META-INF/MANIFEST.MF") }   // first, like a normal jar
    from(plain) { exclude("META-INF/MANIFEST.MF") }
    from(sealDir.map { it.file("core.sealed") }) { into("sunlicense") }
    destinationDirectory = layout.buildDirectory.dir("release")
    archiveFileName = tasks.shadowJar.flatMap { it.archiveFileName }
}

if (sunlicenseProduct.isNotEmpty()) {
    tasks.build { dependsOn(releaseJar) }
}
