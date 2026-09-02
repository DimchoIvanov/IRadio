pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "IRadio"
include(":iradioapp")

//includeBuild("/work/androidx-media/androidx-media") {
//    dependencySubstitution {
//        substitute(module("androidx.media3:media3-exoplayer")).using(project(":lib-exoplayer"))
//        substitute(module("androidx.media3:media3-common")).using(project(":lib-common"))
//        substitute(module("androidx.media3:media3-ui")).using(project(":lib-ui"))
//        substitute(module("androidx.media3:media3-container")).using(project(":lib-container"))
//        substitute(module("androidx.media3:media3-extractor")).using(project(":lib-extractor"))
//        substitute(module("androidx.media3:media3-decoder-ffmpeg")).using(project(":lib-decoder-ffmpeg"))
//        substitute(module("androidx.media3:media3-decoder")).using(project(":lib-decoder"))
//        substitute(module("androidx.media3:media3-datasource")).using(project(":lib-datasource"))
//        substitute(module("androidx.media3:media3-database")).using(project(":lib-database"))
//    }
//}
