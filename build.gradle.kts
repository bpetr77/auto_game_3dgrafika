plugins {//#plugins# specify plugin versions here
  kotlin("multiplatform") version "1.9.21" apply false
  kotlin("plugin.serialization") version "1.9.21" apply false
}

allprojects {//#allprojects# setting for all subprojects
  group = "vision.gears"
  version = "1.0"

  repositories {//#repositories# fetch dependencies from here
    mavenCentral()
    maven{
      url = uri("https://repo1.maven.org/maven2")
    }
  }

}

project("client").buildDir = File("../build")