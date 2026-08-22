package org.autojs.plugin.jvmsource.kotlin

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

internal data class ProviderInstalledIdentity(
    val packageName: String,
    val componentName: String,
    val uid: Int,
    val signerSha256: List<String>,
    val versionCode: Long,
    val versionName: String,
    val lastUpdateTime: Long,
)

internal object ProviderInstalledIdentityResolver {
    @Suppress("DEPRECATION")
    fun resolve(context: Context, component: ComponentName): ProviderInstalledIdentity {
        val packageManager = context.packageManager
        require(component.packageName == context.packageName) {
            "Compilation cache component must belong to the provider package"
        }
        val service = packageManager.getServiceInfo(component, 0)
        require(service.packageName == context.packageName && service.enabled) {
            "Compilation cache component is not an enabled provider service"
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val info = packageManager.getPackageInfo(context.packageName, flags)
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = requireNotNull(info.signingInfo) { "Provider signing identity is unavailable" }
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.apkContentsSigners
            }
        } else {
            requireNotNull(info.signatures) { "Provider signing identity is unavailable" }
        }
        val signerHashes = signatures.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }.distinct().sorted()
        require(signerHashes.isNotEmpty()) { "Provider has no current signing certificates" }
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
        require(versionCode > 0L && info.lastUpdateTime > 0L) {
            "Provider installed version identity is invalid"
        }
        return ProviderInstalledIdentity(
            packageName = info.packageName,
            componentName = component.flattenToString(),
            uid = requireNotNull(info.applicationInfo).uid,
            signerSha256 = signerHashes,
            versionCode = versionCode,
            versionName = info.versionName.orEmpty(),
            lastUpdateTime = info.lastUpdateTime,
        )
    }
}
