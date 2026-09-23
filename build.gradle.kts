plugins {
    kotlin("jvm") version "2.3.21"
    id("com.gradleup.shadow") version "9.0.0"
    // 3.x uses Paper's current downloads API; 2.3.1 used the retired v2 API.
    id("xyz.jpenilla.run-paper") version "3.0.2"
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
    // Paper API 1.21.1, the oldest version this build supports (api-version '1.21').
    // The -mc26 and -mc263 builds live on their own branches.
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Database: SQLite by default, MySQL optional, both through HikariCP.
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
    // Paper ships slf4j, so HikariCP's copy is left out.
    implementation("com.zaxxer:HikariCP:5.1.0") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    // protobuf-java is only for the X DevAPI (jdbc:mysqlx), which is never used. Saves ~1.7 MB.
    implementation("com.mysql:mysql-connector-j:8.4.0") {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }

    // PluginPulse: update checks and checksum-verified downloads.
    implementation("com.github.ESMP-FUN.PluginPulse:pluginpulse-core:v0.9.0")

    // Anonymous usage statistics, relocated below so other plugins' copies cannot clash.
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
        // Kotlin, coroutines, org.sqlite and com.mysql stay where they are: the JDBC
        // drivers are loaded by class name and ServiceLoader.
        relocate("com.zaxxer.hikari", "io.github.darkstarworks.acp.hikari")
        relocate("io.github.darkstarworks.pluginpulse", "io.github.darkstarworks.acp.pluginpulse")
        relocate("dev.faststats", "io.github.darkstarworks.acp.faststats")

        // Paper refuses a jar with duplicate entries. Both drivers register in
        // META-INF/services/java.sql.Driver, so the files are merged, not deduplicated.
        mergeServiceFiles()
        // Each FastStats module ships the same faststats.properties; keep one. Scoped to
        // this path, because a task-wide strategy would override mergeServiceFiles().
        filesMatching("META-INF/faststats.properties") {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }

        // The MySQL connector is signed; its signature files must go or the jar will not load.
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")

        // sqlite-jdbc pulls in slf4j-api too; Paper already has it.
        exclude("org/slf4j/**")

        // Keep only the SQLite natives servers run on: Linux x86_64, Linux aarch64 and
        // Windows x86_64. A host on anything else fails to start with "No native library".
        listOf(
            "Linux/arm", "Linux/armv6", "Linux/armv7", "Linux/ppc64", "Linux/x86",
            "Windows/aarch64", "Windows/armv7", "Windows/x86",
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
        // MockK attaches an agent to the test JVM, which JDK 24 and newer refuse unless asked.
        jvmArgs("-XX:+EnableDynamicAgentLoading", "-Djdk.attach.allowAttachSelf=true")
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
