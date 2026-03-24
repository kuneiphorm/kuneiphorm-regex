plugins {
    `java-library`
    id("org.kuneiphorm.conventions") version "0.1.0-rc.1"
    id("org.kuneiphorm.spotless") version "0.1.0-rc.1"
    id("org.kuneiphorm.testing") version "0.1.0-rc.1"
    id("org.kuneiphorm.pitest") version "0.1.0-rc.1"
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://maven.pkg.github.com/kuneiphorm/*")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("gpr.token").orNull ?: System.getenv("GITHUB_TOKEN")
        }
    }
    mavenCentral()
}

dependencies {
    api("org.kuneiphorm:kuneiphorm-daedalus:0.1.0-rc.1")
    api("org.kuneiphorm:kuneiphorm-runtime:0.1.0-rc.1")
}
