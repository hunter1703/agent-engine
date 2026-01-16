plugins {
    java
    application
    id("io.quarkus") version "3.12.0"
    id("com.diffplug.spotless") version "6.25.0"
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation(
        enforcedPlatform(
            "${property("quarkusPlatformGroupId")}:${property("quarkusPlatformArtifactId")}:${property("quarkusPlatformVersion")}")
    )
    implementation(platform("dev.langchain4j:langchain4j-bom:1.10.0"))
    implementation(platform("io.quarkiverse.langchain4j:quarkus-langchain4j-bom:0.26.1"))
    implementation("io.quarkiverse.langchain4j:quarkus-langchain4j-core")
    implementation("io.quarkus:quarkus-picocli")
    implementation("com.hubspot.jinjava:jinjava:2.8.2")
    implementation("com.alibaba.fastjson2:fastjson2:2.0.48")
    implementation("org.yaml:snakeyaml:2.2")
    implementation("dev.langchain4j:langchain4j")
    implementation("dev.langchain4j:langchain4j-open-ai")
    implementation("dev.langchain4j:langchain4j-ollama")

    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.assertj:assertj-core:3.25.3")
}

spotless {
    java {
        googleJavaFormat("1.17.0")
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
    format("misc") {
        target("*.md", ".gitignore", "*.yml", "*.yaml")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.withType<Test>().configureEach {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-preview")
}

tasks.named<JavaExec>("run") {
    jvmArgs("--enable-preview")
}
