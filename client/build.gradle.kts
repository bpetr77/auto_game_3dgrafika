import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension

rootProject.plugins.withType(org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin::class.java) {
    rootProject.the<YarnRootExtension>().yarnLockMismatchReport =
        YarnLockMismatchReport.WARNING // NONE | FAIL
    rootProject.the<YarnRootExtension>().reportNewYarnLock = false // true
    rootProject.the<YarnRootExtension>().yarnLockAutoReplace = false // true
}

plugins {//#plugins# apply plugins; already loaded in master project
  kotlin("multiplatform")
  kotlin("plugin.serialization")
}

kotlin {
  sourceSets {
    val commonMain by getting {
      kotlin.srcDir("src/main/kotlin")
      kotlin.srcDir("../webglmath/src/main/kotlin")
      kotlin.srcDir("../common/src/main/kotlin")
      resources.srcDir("src/main/glsl")
      resources.srcDir("src/main/content")
      dependencies {
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-js:1.6.2")
      }
    }
  }  
  js(IR) {
    binaries.executable()
    browser {
	
    }
  }  
}