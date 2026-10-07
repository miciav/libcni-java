plugins {
    `java-library`
    `maven-publish`
    signing
    id("com.gradleup.nmcp") version "1.6.2"
    id("com.gradleup.nmcp.aggregation") version "1.6.2"
    // Runs the test suite as a native image. Gson populates these types by reflection, which a
    // native image strips unless told otherwise, and the failure is not a crash: fields come back
    // null and a valid config is rejected as malformed. The suite is the guard.
    id("org.graalvm.buildtools.native") version "1.1.12"
}

group = "io.github.nanofaas"
version = "0.24.0"

val gsonVersion = "2.11.0"
val junitVersion = "5.11.4"
val junitPlatformVersion = "1.11.4"

// Java 21, not higher: this library needs nothing newer, and raising the floor would exclude
// consumers for no gain. containerd-java, which requires 22, consumes it fine.
val javaRelease = 21

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaRelease)
    options.encoding = "UTF-8"
}

dependencies {
    // CNI configs and plugin results are JSON; Gson decodes them. It stays an implementation
    // detail — nothing in the public API exposes a Gson type — so a consumer never has to know.
    implementation("com.google.code.gson:gson:$gsonVersion")

    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:$junitPlatformVersion")
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed", "skipped") }
}

// The metadata under src/main/resources/META-INF/native-image ships in the jar, so a consumer's
// native build works without their running the tracing agent. It was recorded by running this
// suite under the agent, then cut down to this library's own types — what the agent also saw of
// Gradle, JUnit and the test classes has no business in a published library.
graalvmNative {
    binaries {
        named("test") {
            buildArgs.add("--enable-native-access=ALL-UNNAMED")
        }
    }
    agent { enabled.set(false) }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("libcni-java")
                description.set("A Java port of libcni, the library container runtimes use to invoke CNI plugins")
                url.set("https://github.com/Nanofaas/libcni-java")
                scm {
                    connection.set("scm:git:https://github.com/Nanofaas/libcni-java.git")
                    developerConnection.set("scm:git:ssh://git@github.com/Nanofaas/libcni-java.git")
                    url.set("https://github.com/Nanofaas/libcni-java")
                }
                developers { developer { id.set("miciav"); name.set("Michele") } }
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
            }
        }
    }
}

// Ordinary builds and local Maven staging work without a publishing key.
// Central uploads check the credentials before sending the bundle.
signing {
    val key = providers.environmentVariable("SIGNING_KEY").orNull
    isRequired = !key.isNullOrBlank()
    if (!key.isNullOrBlank()) {
        useInMemoryPgpKeys(key, providers.environmentVariable("SIGNING_PASSWORD").orNull)
    }
    sign(publishing.publications)
}

tasks.matching { it.name.endsWith("ToCentralPortal") }.configureEach {
    doFirst {
        val missing = listOf("MAVEN_CENTRAL_USERNAME", "MAVEN_CENTRAL_PASSWORD", "SIGNING_KEY")
            .filter { System.getenv(it).isNullOrBlank() }
        require(missing.isEmpty()) { "Missing publishing credentials: ${missing.joinToString()}" }
    }
}

nmcpAggregation {
    centralPortal {
        username.set(providers.environmentVariable("MAVEN_CENTRAL_USERNAME"))
        password.set(providers.environmentVariable("MAVEN_CENTRAL_PASSWORD"))
        publishingType.set("AUTOMATIC")
    }
}

dependencies {
    add("nmcpAggregation", project(":"))
}
