plugins {
    id("convention.kmp.library")
}

/**
 * LAN P2P: обнаружение пиров (mDNS/NSD) и прямой обмен кадрами.
 *
 * Контракты и кадрирование — `commonMain`; сокеты — общий JVM/Android source set
 * (`java.net`) и POSIX для iOS; дискавери — JmDNS (Desktop), `NsdManager`
 * (Android), `NSNetService` (iOS).
 */
kotlin {
    applyDefaultHierarchyTemplate()

    sourceSets {
        // java.net одинаков и на Desktop, и на Android: один код на два таргета.
        val jvmSharedMain by creating { dependsOn(commonMain.get()) }
        jvmMain.get().dependsOn(jvmSharedMain)
        androidMain.get().dependsOn(jvmSharedMain)

        jvmMain.dependencies {
            implementation(libs.jmdns)
        }
    }
}
