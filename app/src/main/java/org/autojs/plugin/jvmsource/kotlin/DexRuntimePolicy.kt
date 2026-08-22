package org.autojs.plugin.jvmsource.kotlin

import org.autojs.plugin.jvmsource.api.JvmDexLoaderKind
import org.autojs.plugin.jvmsource.api.JvmDexRuntimeProfile
import org.autojs.plugin.jvmsource.api.JvmSourceContract

/**
 * Conservative production admission policy for a raw DEX before it reaches ART.
 *
 * Device compatibility and the request's promised minApi are deliberately evaluated separately.
 * A newer device must not make an artifact acceptable when its format has already violated the
 * requested minApi contract.
 */
internal object DexRuntimePolicy {
    fun loaderKind(deviceApi: Int): WorkerDexLoaderKind {
        require(deviceApi >= JvmSourceContract.MIN_ANDROID_API) {
            "The worker device API is below the JVM source minimum"
        }
        return WorkerDexLoaderKind.fromApi(
            checkNotNull(JvmDexRuntimeProfile.loaderKindForApi(deviceApi)),
        )
    }

    fun admittedVersions(api: Int): Set<String> = JvmDexRuntimeProfile.admittedVersions(api)

    fun evaluateVersion(
        deviceApi: Int,
        requestMinApi: Int,
        dexVersion: String,
    ): DexVersionAdmission {
        val reason = when {
            deviceApi < JvmSourceContract.MIN_ANDROID_API -> DexVersionRejection.DEVICE_API_UNSUPPORTED
            requestMinApi < JvmSourceContract.MIN_ANDROID_API -> DexVersionRejection.REQUEST_MIN_API_UNSUPPORTED
            deviceApi < requestMinApi -> DexVersionRejection.DEVICE_BELOW_REQUEST_MIN_API
            dexVersion !in admittedVersions(deviceApi) -> DexVersionRejection.VERSION_UNSUPPORTED_BY_DEVICE
            dexVersion !in admittedVersions(requestMinApi) -> DexVersionRejection.VERSION_VIOLATES_REQUEST_MIN_API
            else -> null
        }
        return DexVersionAdmission(
            deviceApi = deviceApi,
            requestMinApi = requestMinApi,
            dexVersion = dexVersion,
            loaderKind = if (deviceApi >= JvmSourceContract.MIN_ANDROID_API) loaderKind(deviceApi) else null,
            rejection = reason,
        )
    }
}

internal enum class WorkerDexLoaderKind(val apiKind: JvmDexLoaderKind) {
    PRIVATE_DEX_CLASS_LOADER(JvmDexLoaderKind.PRIVATE_DEX_CLASS_LOADER),
    IN_MEMORY_DEX_CLASS_LOADER(JvmDexLoaderKind.IN_MEMORY_DEX_CLASS_LOADER),
    ;

    companion object {
        fun fromApi(value: JvmDexLoaderKind): WorkerDexLoaderKind = when (value) {
            JvmDexLoaderKind.PRIVATE_DEX_CLASS_LOADER -> PRIVATE_DEX_CLASS_LOADER
            JvmDexLoaderKind.IN_MEMORY_DEX_CLASS_LOADER -> IN_MEMORY_DEX_CLASS_LOADER
        }
    }
}

internal enum class DexVersionRejection {
    DEVICE_API_UNSUPPORTED,
    REQUEST_MIN_API_UNSUPPORTED,
    DEVICE_BELOW_REQUEST_MIN_API,
    VERSION_UNSUPPORTED_BY_DEVICE,
    VERSION_VIOLATES_REQUEST_MIN_API,
}

internal data class DexVersionAdmission(
    val deviceApi: Int,
    val requestMinApi: Int,
    val dexVersion: String,
    val loaderKind: WorkerDexLoaderKind?,
    val rejection: DexVersionRejection?,
) {
    val accepted: Boolean
        get() = rejection == null
}
