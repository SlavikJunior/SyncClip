plugins {
    id("convention.kmp.library")
}

/**
 * Firebase-инфраструктура клиента: авторизация и push-токены.
 *
 * Android использует официальный Google Play services SDK, iOS — Swift-мост
 * (`IosFirebaseBridge`), Desktop — заглушку: Firebase не поддерживает JVM.
 */
kotlin {
    sourceSets {
        // api: приложению нужны firebase-messaging (сервис) и firebase-auth
        androidMain.dependencies {
            api(libs.firebase.auth)
            api(libs.firebase.messaging)
        }
    }
}

dependencies {
    // BOM выравнивает версии Firebase-пакетов между модулями (INV-23).
    add("androidMainApi", platform(libs.firebase.bom))
}
