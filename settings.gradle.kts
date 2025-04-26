pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
        maven("https://maven.aliyun.com/repository/public")
        // Optional if needed
        // maven("https://jcenter.bintray.com/")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.github.com/CarGuo/GSYVideoPlayer") {
            credentials {
                username = "okmthiru04"
                password = "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"
            }
        }
        maven("https://jitpack.io")
    }
}

rootProject.name = "My TV 1"
include(":app")
