import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    kotlin("jvm") version "2.4.20"
    id("com.gradleup.shadow") version "9.6.1"
    // 3.x uses Paper's current downloads API; 2.3.1 used the retired v2 API.
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "com.esmpfun"
version = "2.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    // Each extra repository may only serve its own packages, so nothing else can be swapped in.
    maven("https://jitpack.io") {
        name = "jitpack"
        content { includeGroup("com.github.ESMP-FUN.PluginPulse") }
    }
    maven("https://repo.faststats.dev/releases") {
        name = "faststatsReleases"
        content { includeGroupByRegex("dev\\.faststats.*") }
    }
}

dependencies {
    // Paper API 26.1.2, the oldest 26.x release (api-version '26.1'). This is the
    // -mc26 build for 26.0 to 26.2; 1.21.x and 26.3 have their own branches.
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")

    // Kotlin
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    // Database: SQLite by default, MySQL optional, both through HikariCP.
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    // Paper ships slf4j, so HikariCP's copy is left out.
    implementation("com.zaxxer:HikariCP:7.1.0") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    // protobuf-java is only for the X DevAPI (jdbc:mysqlx), which is never used. Saves ~1.7 MB.
    implementation("com.mysql:mysql-connector-j:9.7.0") {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }

    // PluginPulse: update checks and checksum-verified downloads.
    implementation("com.github.ESMP-FUN.PluginPulse:pluginpulse-core:v0.9.0")

    // Anonymous usage statistics, relocated below so other plugins' copies cannot clash.
    implementation("dev.faststats.metrics:bukkit:0.30.1")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
}

tasks {
    runServer {
        minecraftVersion("26.1.2")
    }
}

// JDK 25 compiles against the Paper 26.x API, but the output is Java 21 bytecode so
// Shadow's bundled ASM can read it. api-version '26.1' keeps this jar off 1.21.x.
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
        // Kotlin, coroutines, org.sqlite and com.mysql stay where they are: the JDBC
        // drivers are loaded by class name and ServiceLoader.
        relocate("com.zaxxer.hikari", "io.github.darkstarworks.acp.hikari")
        relocate("io.github.darkstarworks.pluginpulse", "io.github.darkstarworks.acp.pluginpulse")
        relocate("dev.faststats", "io.github.darkstarworks.acp.faststats")

        // Paper refuses a jar with duplicate entries. Both drivers register in
        // META-INF/services/java.sql.Driver, so the files are merged. Shadow only merges
        // duplicates it is allowed to see, hence INCLUDE here; the merge still writes one
        // entry. Without it the MySQL driver silently drops out of the jar.
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        mergeServiceFiles()
        // Each FastStats module ships the same faststats.properties; keep one. Scoped to
        // this path, because a task-wide strategy would override mergeServiceFiles().
        filesMatching("META-INF/faststats.properties") {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }

        // The X DevAPI (jdbc:mysqlx) is never used; only classic jdbc:mysql connections are.
        exclude("com/mysql/cj/x/**")
        exclude("com/mysql/cj/xdevapi/**")
        exclude("META-INF/maven/**")

        // The MySQL connector is signed; its signature files must go or the jar will not load.
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")

        // sqlite-jdbc pulls in slf4j-api too; Paper already has it.
        exclude("org/slf4j/**")

        // Keep only the SQLite natives servers run on: Linux x86_64, Linux aarch64 and
        // Windows x86_64. An allow-list, so platforms added by a newer driver stay out.
        // A host on anything else fails to start with "No native library".
        val keptNatives = listOf("Linux/x86_64/", "Linux/aarch64/", "Windows/x86_64/")
        exclude { e ->
            e.path.startsWith("org/sqlite/native/") && !e.isDirectory &&
                keptNatives.none { e.path.startsWith("org/sqlite/native/$it") }
        }
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
