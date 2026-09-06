plugins {
    `java-library`
    `maven-publish`
}

group = "io.libcni"
version = "0.1.0"

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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("libcni-java")
                description.set("A Java port of libcni, the library container runtimes use to invoke CNI plugins")
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
