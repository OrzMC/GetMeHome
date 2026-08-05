import org.gradle.kotlin.dsl.support.serviceOf
import org.yaml.snakeyaml.Yaml
import java.io.ByteArrayOutputStream

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.yaml:snakeyaml:2.6")
    }
}

val pluginYaml = Yaml().load(File("src/main/resources/plugin.yml").inputStream()) as Map<String, Any>
group = (pluginYaml["main"] as String).split('.').dropLast(1).joinToString(".")
version = pluginYaml["version"] as String
description = pluginYaml["description"] as String

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(property("plugin_jdk_min_version") as String))
}

repositories {
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven("https://repo.codemc.org/repository/maven-public/")
}

dependencies {
    // 26.x 起不再用 {api-version}-R0.1-SNAPSHOT 推导，改用显式稳定坐标（对齐 OrzMC）
    compileOnly("io.papermc.paper:paper-api:${property("paper_api_version")}")
    implementation("org.bstats:bstats-bukkit:3.0.2")
}

plugins {
    `java-library`
    // 打包含 bstats 的 fat jar，作为唯一发布产物
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper") version "3.0.2"
    // 自动发布版本配置文档：https://docs.papermc.io/misc/hangar-publishing/
    id("io.papermc.hangar-publish-plugin") version "0.1.4"
}

// 版本发布相关
fun executeGitCommand(vararg command: String): String {
    val byteOut = ByteArrayOutputStream()
    serviceOf<ExecOperations>().exec {
        commandLine = listOf("git", *command)
        standardOutput = byteOut
    }
    return byteOut.toString(Charsets.UTF_8.name()).trim()
}

fun latestCommitMessage(): String {
    return runCatching { executeGitCommand("log", "-1", "--pretty=%B") }
        .getOrElse { "GetMeHome ${project.version} build" }
}

val githubRunNumber: String? = System.getenv("GITHUB_RUN_NUMBER")
val githubRefType: String? = System.getenv("GITHUB_REF_TYPE")
val githubRef: String? = System.getenv("GITHUB_REF")
val versionString: String = version as String

// tag 名（仅 tag push 时有值）
val tagName: String? = if (githubRefType == "tag") githubRef?.removePrefix("refs/tags/") else null
val isTag: Boolean = tagName != null
// 纯 SemVer tag（不含 -）→ Release；其余（分支 push / 预发布 tag / 本地）→ beta
val isReleaseTag: Boolean = isTag && tagName != null && !tagName.contains("-")

val shadowJarVersion: String = when {
    // tag → 正式版版本号以 tag 为准（纯 tag 驱动）；CI 门禁保证 tag == plugin.yml version
    isTag && tagName != null -> tagName
    // CI main push → {base}-dev.{run}（workflow 仅对 main 分支触发）
    githubRunNumber != null -> "${versionString}-dev.${githubRunNumber}"
    // 本地开发 → {base}-dev
    else -> "${versionString}-dev"
}

// Use the commit description for the changelog
val changelogContent: String = latestCommitMessage()

// 统一通道名（小写）。注意：Hangar 通道需在项目页预先创建且大小写敏感。
val platformChannel: String = if (isReleaseTag) "release" else "beta"

val debugServerVesion = property("plugin_debug_server_version") as String
val bytecodeTarget = (property("plugin_bytecode_target") as String).toInt()
tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        // paper-api 26.x 要求 JVM 25，字节码目标与 toolchain 一致（对齐 OrzMC）
        options.release.set(bytecodeTarget)
        // 启用弃用警告
        options.compilerArgs.add("-Xlint:deprecation")
        // 同时启用未检查的类型转换警告
        options.compilerArgs.add("-Xlint:unchecked")
    }
    withType<Javadoc> {
        options.encoding = "UTF-8"
    }
    // 普通 jar 不再产出：发布与本地调试一律使用 shadowJar
    jar {
        enabled = false
    }
    shadowJar {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        archiveClassifier.set(null as String?)
        archiveVersion.set(shadowJarVersion)
        relocate("org.bstats", "com.simonorj.mc.getmehome.shade.org.bstats")
    }
    build {
        dependsOn("shadowJar")
    }
    // 配置工程内直接调试服务端插件
    // gradle-plugin: https://github.com/jpenilla/run-task#basic-usage
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion(debugServerVesion)
    }
}

hangarPublish {
    publications.register("plugin") {
        version = shadowJarVersion
        channel = platformChannel
        changelog = changelogContent
        id = pluginYaml["name"] as String
        apiKey = System.getenv("HANGAR_API_TOKEN")
        platforms {
            paper {
                jar = tasks.shadowJar.flatMap { it.archiveFile }
                platformVersions = (property("plugin_support_paper_versions") as String).split(",").map { it.trim() }
            }
        }

        // 同步 README.md 到 Hangar 项目主页
        pages.resourcePage(project.file("README.md").readText())
    }
}
