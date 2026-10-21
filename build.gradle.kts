plugins {
    java
    // Puts the SunLicense SDK inside the plugin jar under your own package (see shadowJar below).
    id("com.gradleup.shadow") version "9.6.1"
    // ./gradlew runServer starts a local Paper server with the plugin installed.
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

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
