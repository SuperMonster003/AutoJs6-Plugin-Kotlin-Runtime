package org.autojs.plugin.jvmsource.kotlin.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import org.autojs.plugin.jvmsource.kotlin.BuildConfig

internal class HostCallerVerifier(context: Context) {
    private val packageManager = context.applicationContext.packageManager
    private val providerPackageName = context.applicationContext.packageName

    fun enforceAllowedCaller(): Int = Binder.getCallingUid().also(::enforceAllowedUid)

    fun enforceSessionOwner(expectedUid: Int) {
        val actualUid = Binder.getCallingUid()
        if (actualUid != expectedUid) throw SecurityException("Session UID differs from its owner")
        enforceAllowedUid(actualUid)
    }

    @Suppress("DEPRECATION")
    private fun enforceAllowedUid(uid: Int) {
        val hostPackage = BuildConfig.HOST_PACKAGE_NAME
        val uidPackages = packageManager.getPackagesForUid(uid)?.toSet().orEmpty()
        if (hostPackage !in uidPackages) throw SecurityException("Caller is not the configured host package")
        val hostUid = try {
            packageManager.getApplicationInfo(hostPackage, 0).uid
        } catch (error: PackageManager.NameNotFoundException) {
            throw SecurityException("Configured host package is not installed", error)
        }
        if (hostUid != uid) throw SecurityException("Caller UID does not own the configured host package")
        if (packageManager.checkSignatures(providerPackageName, hostPackage) != PackageManager.SIGNATURE_MATCH) {
            throw SecurityException("Provider and host signatures differ")
        }
    }
}
