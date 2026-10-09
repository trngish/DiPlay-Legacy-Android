plugins {
    alias(libs.plugins.android.application)
}

// Optional local-only input. CI and ordinary source builds contain no accessory identity.
val localAuthenticationAssets = providers.environmentVariable("DIPLAY_AUTH_ASSETS_DIR")
    .orNull?.let { file(it).canonicalFile }

// Likewise for the head-unit identity of the build itself. A package id or ABI set chosen by
// accident cannot be installed over the previous one, and that is only discovered at the car, so
// car builds have to name them explicitly instead of inheriting the source defaults.
val localApplicationId = providers.environmentVariable("DIPLAY_APPLICATION_ID").orNull
val localVersionSuffix = providers.environmentVariable("DIPLAY_VERSION_SUFFIX").getOrElse("")
val localAbis = providers.environmentVariable("DIPLAY_ABIS").orNull
    ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }

android {
    namespace = "com.shilapi.xcertplay"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = localApplicationId ?: "com.shihab.diplay"
        minSdk = 19
        targetSdk = 37
        multiDexEnabled = true
        versionCode = 26
        versionName = "0.2.7" + localVersionSuffix
        // Dependency native libraries arrive with obsolete ABIs (armeabi, mips); without a filter
        // they are packaged as-is, which costs almost 1 MB on every install.
        ndk {
            abiFilters += localAbis ?: listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }

    }


    localAuthenticationAssets?.let { sourceSets.getByName("main").assets.srcDir(it) }

    signingConfigs {
        create("release") {
            storeFile = file(
                providers.environmentVariable("ANDROID_KEYSTORE_PATH")
                    .getOrElse("missing-release-keystore.jks"),
            )
            storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").getOrElse("")
            keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").getOrElse("")
            keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").getOrElse("")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".hudtest"
            versionNameSuffix = "-hud-test"
        }
        release {
            optimization {
                enable = false
            }
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":common"))
    implementation(project(":shared"))
    implementation("androidx.multidex:multidex:2.0.1")
}

// No implicit import. Only the two explicitly selected local runtime assets are allowed.
val credentialAssets = files(android.sourceSets.flatMap { source ->
    source.assets.directories.map { directory ->
        fileTree(directory) {
            include("**/offline-mfi/**", "**/*.pk8", "**/*.p7b", "**/*.key",
                "**/*.pem", "**/*.p12", "**/*.pfx", "**/*.jks", "**/*.keystore")
        }
    }
})
val rejectBundledCredentials by tasks.registering {
    group = "verification"
    description = "Reject unexpected credential files in APK assets."
    val filesToCheck = credentialAssets
    val allowed = localAuthenticationAssets?.let { dir ->
        listOf("identity.pk8", "certificate.p7b").map { dir.resolve("offline-mfi/$it").canonicalFile }.toSet()
    } ?: emptySet()
    inputs.files(filesToCheck)
    doLast {
        check(allowed.all { it.isFile }) { "Explicit local authentication assets are incomplete" }
        val unexpected = filesToCheck.files.filter { it.canonicalFile !in allowed }
        check(unexpected.isEmpty()) { "Unexpected credential files in APK assets" }
    }
}
tasks.named("preBuild") { dependsOn(rejectBundledCredentials) }

// Car-test packages must be standalone. Keep ordinary source/CI builds identity-free.
val verifyStandaloneAuthentication by tasks.registering {
    group = "verification"
    description = "Require the explicit runtime authentication input for a standalone car-test APK."
    val directory = localAuthenticationAssets
    doLast {
        check(directory != null) {
            "Standalone car builds require DIPLAY_AUTH_ASSETS_DIR; assembleDebug alone is source-only."
        }
        check(listOf("identity.pk8", "certificate.p7b").all {
            directory.resolve("offline-mfi/$it").let { file -> file.isFile && file.length() > 0 }
        }) { "Standalone CarPlay authentication files are missing or empty" }
    }
}
tasks.named("preBuild") { mustRunAfter(verifyStandaloneAuthentication) }
tasks.register("assembleStandaloneDebug") {
    group = "build"
    description = "Build a standalone car-test APK with explicitly provisioned authentication."
    dependsOn(verifyStandaloneAuthentication, "assembleDebug")
}

val verifyStandaloneIdentity by tasks.registering {
    group = "verification"
    description = "Require an explicit package identity for a signed car release."
    val applicationId = localApplicationId
    val abis = localAbis
    doLast {
        check(!applicationId.isNullOrBlank()) {
            "Car releases require DIPLAY_APPLICATION_ID; assembleRelease alone keeps the upstream id."
        }
        check(!abis.isNullOrEmpty()) {
            "Car releases require DIPLAY_ABIS so the package only carries what the head unit can load."
        }
    }
}
tasks.named("preBuild") { mustRunAfter(verifyStandaloneIdentity) }
tasks.register("assembleStandaloneRelease") {
    group = "build"
    description = "Build a signed car release with explicitly provisioned authentication and identity."
    dependsOn(verifyStandaloneAuthentication, verifyStandaloneIdentity, "assembleRelease")
}
