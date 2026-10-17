// 影译（AiVideo）工程配置
// 插件与依赖仓库：google / mavenCentral / gradlePluginPortal，
// 模块内禁止私自声明其他仓库（repositoriesMode = FAIL_ON_PROJECT_REPOS）。

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// 沙箱 MITM 代理：从环境变量 http_proxy / https_proxy 解析，
// 映射为 Gradle 依赖下载使用的代理系统属性。
fun applyProxyFromEnv() {
    val proxies = mapOf(
        "http" to (System.getenv("http_proxy") ?: System.getenv("HTTP_PROXY")),
        "https" to (System.getenv("https_proxy") ?: System.getenv("HTTPS_PROXY")),
    )
    for ((scheme, raw) in proxies) {
        if (raw.isNullOrBlank()) continue
        // 兼容 http://host:port 与 host:port 两种写法
        val noScheme = raw.substringAfter("://")
        val host = noScheme.substringBefore(":")
        val port = noScheme.substringAfter(":", "").substringBefore("/").toIntOrNull() ?: continue
        if (host.isBlank()) continue
        System.setProperty("$scheme.proxyHost", host)
        System.setProperty("$scheme.proxyPort", port.toString())
        println("已从环境变量配置 $scheme 代理：$host:$port")
    }
}
applyProxyFromEnv()

rootProject.name = "aivideo"
include(":app")
