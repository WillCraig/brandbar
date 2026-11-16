import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.intellij.platform.gradle.tasks.BuildPluginTask
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.VerificationReportsFormats
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest
import java.time.Duration
import java.util.zip.ZipFile

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
}

kotlin {
    jvmToolchain(21)
}

val integrationTestSourceSet = sourceSets.create("integrationTest") {
    compileClasspath += sourceSets.main.get().output + configurations.named("intellijPlatformClasspath").get()
    runtimeClasspath += sourceSets.main.get().output + configurations.named("intellijPlatformClasspath").get()
}

configurations.named("integrationTestImplementation") {
    extendsFrom(configurations.named("testImplementation").get())
}
configurations.named("integrationTestRuntimeOnly") {
    extendsFrom(configurations.named("testRuntimeOnly").get())
}

dependencies {
    implementation("com.github.weisj:jsvg:2.2.0")
    testImplementation("junit:junit:4.13.2")
    intellijPlatform {
        intellijIdeaCommunity("2024.3.7.1")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Starter, configurationName = "integrationTestImplementation")
    }
    "integrationTestImplementation"("org.junit.jupiter:junit-jupiter:5.11.4")
    "integrationTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

val verifierChannel = providers.gradleProperty("brandbar.verifierChannel").orElse("stable")

intellijPlatform {
    pluginConfiguration {
        id = "dev.willc.brandbar"
        name = "Brand Bar"
        version = providers.gradleProperty("version")
        ideaVersion {
            sinceBuild = "243"
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ides {
            when (verifierChannel.get()) {
                "eap" -> latest {
                    types.set(
                        listOf(
                            IntelliJPlatformType.IntellijIdeaCommunity,
                            IntelliJPlatformType.IntellijIdeaUltimate,
                        ),
                    )
                    channels.set(listOf(ProductRelease.Channel.EAP))
                    sinceBuild.set("243")
                }
                "current" -> current()
                else -> {
                    current()
                    latest {
                        types.set(
                            listOf(
                                IntelliJPlatformType.IntellijIdeaCommunity,
                                IntelliJPlatformType.IntellijIdeaUltimate,
                                IntelliJPlatformType.PyCharm,
                                IntelliJPlatformType.WebStorm,
                            ),
                        )
                        channels.set(listOf(ProductRelease.Channel.RELEASE))
                        sinceBuild.set("243")
                    }
                }
            }
        }
        verificationReportsFormats.set(
            listOf(
                VerificationReportsFormats.PLAIN,
                VerificationReportsFormats.HTML,
                VerificationReportsFormats.MARKDOWN,
            ),
        )
    }
}

tasks.named<Test>("test") {
    useJUnit()
    systemProperty("java.awt.headless", "true")
    systemProperty(
        "brandbar.screenshots.dir",
        layout.buildDirectory.dir("reports/screenshots").get().asFile.absolutePath,
    )
    systemProperty("brandbar.examples.dir", rootProject.projectDir.resolve("examples/themes").absolutePath)
    outputs.dir(layout.buildDirectory.dir("reports/screenshots"))
}

val renderScreenshots = tasks.register<JavaExec>("renderScreenshots") {
    group = "verification"
    description = "Renders headless Java2D PNG screenshots of Brand Bar across determinate and indeterminate states."
    classpath = files(
        sourceSets.test.get().runtimeClasspath,
        configurations.named("intellijPlatformClasspath"),
    )
    mainClass.set("dev.willc.brandbar.ScreenshotsCli")
    jvmArgs("-Djava.awt.headless=true")
    providers.gradleProperty("brandbar.theme").orNull?.let { systemProperty("brandbar.theme", it) }
    val outputDir = layout.buildDirectory.dir("reports/screenshots")
    outputs.dir(outputDir)
    argumentProviders.add(
        CommandLineArgumentProvider {
            listOf(outputDir.get().asFile.absolutePath)
        },
    )
}

val renderExampleScreenshots = tasks.register<JavaExec>("renderExampleScreenshots") {
    group = "verification"
    description = "Renders every examples/themes/* theme (0/25/50/75/100% + indeterminate) and a gallery overview sheet."
    classpath = files(
        sourceSets.test.get().runtimeClasspath,
        configurations.named("intellijPlatformClasspath"),
    )
    mainClass.set("dev.willc.brandbar.ScreenshotsCli")
    jvmArgs("-Djava.awt.headless=true")
    systemProperty("brandbar.themesRoot", rootProject.projectDir.resolve("examples/themes").absolutePath)
    val outputDir = layout.buildDirectory.dir("reports/example-screenshots")
    outputs.dir(outputDir)
    argumentProviders.add(
        CommandLineArgumentProvider {
            listOf(outputDir.get().asFile.absolutePath)
        },
    )
}

val verifyDistributionExcludesExamples = tasks.register("verifyDistributionExcludesExamples") {
    group = "verification"
    description = "Fails if any examples/ asset (by path, slug, or content hash) ends up in the plugin distribution zip."
    dependsOn(tasks.named<BuildPluginTask>("buildPlugin"))
    inputs.dir(rootProject.projectDir.resolve("examples"))
    val archiveProvider = tasks.named<BuildPluginTask>("buildPlugin").flatMap { it.archiveFile }
    val examplesDir = rootProject.projectDir.resolve("examples")
    doLast {
        val zipPath = archiveProvider.get().asFile
        val exampleFiles = examplesDir.walkTopDown()
            .filter { it.isFile }.toList()
        fun sha256(bytes: ByteArray): String =
            "%064x".format(BigInteger(1, MessageDigest.getInstance("SHA-256").digest(bytes)))
        val exampleHashes = exampleFiles.map { f -> sha256(f.readBytes()) }.toSet()
        val slugs = examplesDir.resolve("themes").listFiles()
            ?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
        val offenders = mutableListOf<String>()
        fun scanZip(file: File, prefix: String) {
            ZipFile(file).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    val name = "$prefix${entry.name}"
                    if (name.contains("examples/") || slugs.any { name.contains(it) }) offenders += name
                    if (entry.name.endsWith(".jar") || entry.name.endsWith(".zip")) {
                        val tmp = File.createTempFile("brandbar-nested", ".zip")
                        try {
                            zip.getInputStream(entry).use { input -> tmp.outputStream().use { input.copyTo(it) } }
                            scanZip(tmp, "$name!/")
                        } finally {
                            tmp.delete()
                        }
                    } else if (!entry.isDirectory && entry.size in 1..2_000_000) {
                        val hash = sha256(zip.getInputStream(entry).readAllBytes())
                        if (hash in exampleHashes) offenders += "$name (content matches an examples/ file)"
                    }
                }
            }
        }
        scanZip(zipPath, "")
        check(offenders.isEmpty()) { "Example assets leaked into ${zipPath.name}: $offenders" }
        logger.lifecycle("Verified ${zipPath.name} contains no examples/ assets ({} slugs checked).", slugs.size)
    }
}

tasks.named("check") {
    dependsOn(verifyDistributionExcludesExamples)
}

intellijPlatformTesting {
    testIdeUi.register("integrationTest") {
        task {
            testClassesDirs = integrationTestSourceSet.output.classesDirs
            classpath = files(
                integrationTestSourceSet.runtimeClasspath,
                configurations.named("intellijPlatformClasspath"),
            )
            useJUnitPlatform()
            jvmArgs("-Xmx2g")
            systemProperty(
                "brandbar.ide.screenshots.dir",
                layout.buildDirectory.dir("reports/ide-screenshots").get().asFile.absolutePath,
            )
            timeout.set(Duration.ofMinutes(25))
            archiveFile.convention(tasks.named<BuildPluginTask>("buildPlugin").flatMap { it.archiveFile })
        }
    }
}

tasks.matching { it.name == "integrationTest" }.configureEach {
    group = "verification"
    description = "Boots a real IDE under Xvfb with Brand Bar installed, asserts ProgressBarUI registration, and captures an in-IDE screenshot."
}

tasks.register("runPluginVerifier") {
    group = "verification"
    description = "Compatibility alias for verifyPlugin."
    dependsOn("verifyPlugin")
}
