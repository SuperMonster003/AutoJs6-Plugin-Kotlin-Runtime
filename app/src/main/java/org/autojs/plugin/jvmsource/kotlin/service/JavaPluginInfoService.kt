package org.autojs.plugin.jvmsource.kotlin.service

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import org.autojs.plugin.common.api.IPluginInfoProvider
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.api.JvmSourceLanguage
import org.autojs.plugin.jvmsource.kotlin.BuildConfig
import org.autojs.plugin.jvmsource.kotlin.JavaProviderRuntime
import org.autojs.plugin.jvmsource.kotlin.R

class JavaPluginInfoService : Service() {
    private lateinit var callerVerifier: HostCallerVerifier

    override fun onCreate() {
        super.onCreate()
        callerVerifier = HostCallerVerifier(this)
    }

    // Plugin Center binds by explicit component, for which a null action is valid.
    override fun onBind(intent: Intent?): IBinder = binder

    private val binder = object : IPluginInfoProvider.Stub() {
        override fun getInfo(): PluginInfo {
            callerVerifier.enforceAllowedCaller()
            return PluginInfo(
                name = getString(R.string.plugin_name),
                description = getString(R.string.plugin_description),
                instruction = getString(R.string.plugin_instruction),
                author = getString(R.string.plugin_author),
                collaborators = null,
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE.toLong(),
                versionDate = null,
                id = JavaProviderRuntime.PROVIDER_ID,
                engine = JvmSourceContract.ENGINE_ID,
                variant = VARIANT,
                supportedAbis = null,
                capabilities = Bundle().apply {
                    putLong(
                        PluginCapabilityKeys.REQUIRES_HOST_VERSION,
                        BuildConfig.MIN_HOST_VERSION_CODE,
                    )
                    putInt("jvmSourceProtocolMajor", JvmSourceContract.PROTOCOL_MAJOR)
                    putInt("jvmSourceProtocolMinor", JvmSourceContract.PROTOCOL_MINOR)
                    putInt("jvmSourceEntryApiVersion", JvmSourceContract.ENTRY_API_VERSION)
                    putStringArray("languages", arrayOf(JvmSourceLanguage.KOTLIN.wireName))
                    putString("sourceCompiler", "kotlin-jvm")
                    putString("sourceCompilerVersion", BuildConfig.KOTLIN_COMPILER_VERSION)
                    putString("dexCompiler", "d8")
                    putString("dexCompilerVersion", BuildConfig.D8_VERSION)
                },
            )
        }
    }

    private companion object {
        const val VARIANT = "kotlin-jvm-d8"
    }
}
