plugins {
    java
    application
    id("com.google.cloud.tools.jib") version "3.4.3"
}

group = "gazelle.mifos.io"
version = "2.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

application {
    mainClass.set("com.paymenthub.OperatorMain")
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.maven.apache.org/maven2/") }
    maven { url = uri("https://mifos.jfrog.io/artifactory/phee-gradle-local") }
    maven { url = uri("https://mifos.jfrog.io/artifactory/mifosx-gradle-local") }
}

dependencies {
    // Mifos Platform BOM pins Spring Boot 3.4, Camel 4, Zeebe 8, Jakarta EE 10
    // and every other shared library version. Do NOT hardcode managed versions.
    // This is a deployable application (leaf node), so use enforcedPlatform().
    implementation(enforcedPlatform("org.mifos:paymenthub-ee-bom:2.0.0-SNAPSHOT"))

    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")

    implementation("javax.inject:javax.inject:1")
    implementation("io.fabric8:kubernetes-client:6.13.1")
    implementation("io.javaoperatorsdk:operator-framework-core:4.9.2")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation("org.slf4j:slf4j-api")
    runtimeOnly("org.slf4j:slf4j-simple")
    runtimeOnly("org.bouncycastle:bcpkix-jdk18on:1.78.1")

    testImplementation("io.fabric8:kubernetes-server-mock:6.13.1")
    testImplementation("io.fabric8:mockwebserver:6.13.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:3.12.12")
    testImplementation("io.javaoperatorsdk:operator-framework-junit-5:4.9.2")
    testImplementation("io.cucumber:cucumber-java:7.18.0")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:7.18.0")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.platform:junit-platform-suite")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.register<Jar>("bootJar") {
    group = "build"
    description = "Assembles a self-contained executable JAR with all runtime dependencies."
    archiveClassifier.set("boot")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes("Main-Class" to application.mainClass.get())
    }
    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    // Exclude signature files from signed dependency JARs (e.g. BouncyCastle). When
    // their contents are repacked into a fat JAR the digests no longer match and the
    // JVM throws SecurityException: Invalid signature file digest for Manifest.
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC")
}

tasks.jar {
    // Add Main-Class and Class-Path to the manifest so `java -jar` works without a fat JAR.
    // Dependencies are copied to build/libs/ alongside the main JAR during assembly.
    manifest {
        attributes(
            "Main-Class" to application.mainClass.get(),
            "Class-Path" to provider {
                configurations.runtimeClasspath.get().joinToString(" ") { it.name }
            }
        )
    }
    doFirst {
        copy {
            from(configurations.runtimeClasspath)
            into(layout.buildDirectory.dir("libs"))
        }
    }
}

tasks.test {
    useJUnitPlatform { includeTags("unit") }
    testLogging { events("passed", "skipped", "failed") }
}

tasks.register<Test>("integrationTest") {
    useJUnitPlatform { includeTags("integration") }
    testLogging { events("passed", "skipped", "failed") }
    systemProperty(
        "kubeconfig",
        System.getenv("KUBECONFIG") ?: "${System.getProperty("user.home")}/.kube/config"
    )
}

jib {
    from {
        image = "eclipse-temurin:21-jre-jammy"
        platforms {
            platform { os = "linux"; architecture = "amd64" }
            platform { os = "linux"; architecture = "arm64" }
        }
    }
    to {
        image = "openmf/paymenthub-operator:${project.version}"
    }
    container {
        jvmFlags = listOf("-Xms64m", "-Xmx256m")
    }
}
