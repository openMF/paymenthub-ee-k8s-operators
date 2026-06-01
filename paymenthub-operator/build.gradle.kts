plugins {
    java
    application
    id("com.google.cloud.tools.jib") version "3.4.3"
}

group = "gazelle.mifos.io"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

application {
    mainClass.set("com.paymenthub.OperatorMain")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("javax.inject:javax.inject:1")
    implementation("io.fabric8:kubernetes-client:6.13.1")
    implementation("io.javaoperatorsdk:operator-framework-core:4.9.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.2")
    implementation("org.slf4j:slf4j-api:2.0.9")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.9")
    runtimeOnly("org.bouncycastle:bcpkix-jdk18on:1.78.1")

    testImplementation("io.fabric8:kubernetes-server-mock:6.13.1")
    testImplementation("io.fabric8:mockwebserver:6.13.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:3.12.12")
    testImplementation("io.javaoperatorsdk:operator-framework-junit-5:4.9.2")
    testImplementation("io.cucumber:cucumber-java:7.18.0")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:7.18.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("org.junit.platform:junit-platform-suite:1.10.3")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("org.assertj:assertj-core:3.26.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.3")
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
