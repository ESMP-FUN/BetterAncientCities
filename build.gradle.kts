import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    kotlin("jvm") version "2.3.20"
    id("com.gradleup.shadow") version "8.3.6"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "io.github.darkstarworks"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
}

dependencies {
    // Paper API — 26.x track (the `-mc26` build). plugin.yml's api-version '26.1'
    // keeps this jar to 26.x servers; the master build targets 1.21.1 + '1.21'.
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Database (SQLite default, MySQL optional — both via HikariCP).
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
    // HikariCP needs slf4j-api at compile time, but Paper ships slf4j at
    // runtime — exclude it so we don't shade a duplicate.
    implementation("com.zaxxer:HikariCP:5.1.0") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    // MySQL classic-protocol JDBC driver. protobuf-java is only used by the X
    // DevAPI (jdbc:mysqlx) which we never touch — excluding it drops ~1.7 MB.
    implementation("com.mysql:mysql-connector-j:8.4.0") {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
}

tasks {
    runServer {
        minecraftVersion("26.1.2")
    }
}

// Use JDK 25 to compile against the Paper 26.x API, but output Java 21 bytecode so
// the Shadow jar packager (older bundled ASM) can process the class files.
// api-version: '26.1' in plugin.yml is what keeps this jar to 26.x servers.
kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_21.toString()
    targetCompatibility = JavaVersion.VERSION_21.toString()
}

// Accept JVM 25 libraries (Paper 26.x requires it) while still outputting JVM 21.
configurations.matching {
    it.name in setOf("compileClasspath", "runtimeClasspath")
}.configureEach {
    attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

tasks {
    shadowJar {
        archiveClassifier.set("mc26")
        // Do NOT relocate Kotlin stdlib / kotlinx-coroutines (Bukkit must find them).
        // Do NOT relocate org.sqlite or com.mysql (JDBC drivers load by class name
        // + ServiceLoader; relocation would break driverClassName / META-INF/services).
        relocate("com.zaxxer.hikari", "io.github.darkstarworks.acp.hikari")

        // Strip signature files from the (signed) MySQL connector jar — shading a
        // signed jar without this throws "Invalid signature file digest" at load.
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")

        // slf4j-api is pulled transitively by sqlite-jdbc, but Paper ships it at
        // runtime — don't shade a duplicate.
        exclude("org/slf4j/**")

        // Keep only the SQLite natives real servers/dev use: Windows x86_64 (dev),
        // Linux x86_64 (most servers), Linux aarch64 (ARM servers). Drop the rest.
        listOf(
            "Linux/arm", "Linux/armv6", "Linux/armv7", "Linux/ppc64", "Linux/x86",
            "Windows/aarch64", "Windows/armv7", "Windows/x86",
            // Defensive: other layouts shipped by some sqlite-jdbc versions.
            "Mac", "FreeBSD", "Linux-Android", "Linux-Musl",
        ).forEach { exclude("org/sqlite/native/$it/**") }
    }

    jar {
        enabled = false
    }

    build {
        dependsOn(shadowJar)
    }

    assemble {
        dependsOn(shadowJar)
    }

    test {
        useJUnitPlatform()
    }
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}
