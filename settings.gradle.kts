// 国内用阿里云镜像：直连 Google / Maven Central 常常只有几十 KB/s，
// 换镜像后首次拉 Compose 依赖从十几分钟降到一两分钟。
//
// 但 GitHub Actions 的 runner 在境外，访问阿里云镜像反而会 502（实测：
// kotlinx-coroutines-core-jvm 的 .pom 直接 Bad Gateway，该仓库随即被 Gradle
// 标记为 disabled，连带 j2objc-annotations 一起解析失败，整个构建挂掉）。
// 所以 CI 里只用官方仓库 —— Actions 默认注入 CI=true，用它判断即可。
//
// 注意：判断必须写在每个 block 内部。pluginManagement 是最先求值的，
// 顶层 val 在它执行时还不存在（会报 Unresolved reference）。

pluginManagement {
    val useAliyun = System.getenv("CI").isNullOrBlank()
    repositories {
        if (useAliyun) {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/central") }
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    val useAliyun = System.getenv("CI").isNullOrBlank()
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (useAliyun) {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/central") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "CheckinNative"
include(":app")