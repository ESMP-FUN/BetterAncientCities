plugins {
    kotlin("jvm") version "2.3.21"
    id("com.gradleup.shadow") version "9.0.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "com.esmpfun"
version = "2.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://jitpack.io")
    maven("https://repo.faststats.dev/releases") {
        name = "faststatsReleases"
    }
}

dependencies {
    // Paper API. Ancient City discovery uses World.getStructures(...) /
    // GeneratedStructure / StructurePiece (Structure.ANCIENT_CITY), all present
    // in 1.21.1. api-version stays '1.21'.
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")

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

    // PluginPulse — update checking + verified install staging.
    implementation("com.github.darkstarworks.PluginPulse:pluginpulse-core:v0.8.0")

    // Anonymous usage metrics (relocated below to avoid clashing with other
    // plugins shading a different SDK version)
    implementation("dev.faststats.metrics:bukkit:0.28.0")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
}

tasks {
    runServer {
        minecraftVersion("1.21.1")
    }
}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        // Do NOT relocate Kotlin stdlib / kotlinx-coroutines (Bukkit must find them).
        // Do NOT relocate org.sqlite or com.mysql (JDBC drivers load by class name
        // + ServiceLoader; relocation would break driverClassName / META-INF/services).
        relocate("com.zaxxer.hikari", "io.github.darkstarworks.acp.hikari")
        relocate("io.github.darkstarworks.pluginpulse", "io.github.darkstarworks.acp.pluginpulse")
        relocate("dev.faststats", "io.github.darkstarworks.acp.faststats")

        // Paper's plugin remapper rejects a jar containing duplicate entries.
        // Concatenate the JDBC ServiceLoader registrations (sqlite-jdbc and
        // mysql-connector-j each ship META-INF/services/java.sql.Driver) —
        // deduping instead of merging would drop one of the two drivers.
        mergeServiceFiles()
        // The faststats bukkit/config/core modules each ship an identical
        // META-INF/faststats.properties (version=0.28.0); keep the first.
        // Scoped to that path only — a task-wide strategy would override
        // mergeServiceFiles() above and silently drop the MySQL driver.
        filesMatching("META-INF/faststats.properties") {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }

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
