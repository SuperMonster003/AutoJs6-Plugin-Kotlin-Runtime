import groovy.json.JsonSlurper
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Properties
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.ow2.asm:asm:9.9")
        classpath("org.ow2.asm:asm-tree:9.9")
        classpath("org.ow2.asm:asm-commons:9.9")
    }
}

plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.application")
}

val kotlinCompilerVersion = "2.3.21"
val kotlinCompilerReflectVersion = "1.6.10"
val kotlinCompilerCoroutinesVersion = "1.8.0"
val kotlinCompilerTroveVersion = "1.0.20200330"
val d8Version = "8.13.17"
val desugarArtifact = "desugar_jdk_libs_nio"
val desugarVersion = "2.1.5"
val globalApplicationId = "io.github.supermonster003.autojs6.plugin.kotlin.runtime"
val providerNamespace = "org.autojs.plugin.jvmsource.kotlin"
val compilerStubApi = 24
val protocolLockFile = rootProject.file("protocol/protocol-artifacts.lock.json")
val expectedProtocolModules = linkedMapOf(
    "common-plugin-api.aar" to ":plugin-api:common-plugin-api",
    "protocol-wire-api.aar" to ":plugin-api:protocol-wire-api",
    "jvm-source-api.aar" to ":plugin-api:jvm-source-api",
)
val protocolArtifacts = expectedProtocolModules.keys.map { rootProject.file("protocol/$it") }
val jvmSourceApiAar = rootProject.file("protocol/jvm-source-api.aar")
val generatedCompilerClasspathAssets = layout.buildDirectory.dir("generated/assets/compilerClasspath")
val kotlinCompilerLibraries = configurations.create("kotlinCompilerLibraries") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}
val kotlinCompilerEmbeddable = configurations.create("kotlinCompilerEmbeddable") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}
val kotlinCompilerTrove = configurations.create("kotlinCompilerTrove") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}
val entryApiClassEntries = linkedSetOf(
    "org/autojs/plugin/jvmsource/api/AutoJsJvmEntry.class",
    "org/autojs/plugin/jvmsource/api/JvmScriptContext.class",
    "org/autojs/plugin/jvmsource/api/JvmScriptContext\$DefaultImpls.class",
    "org/autojs/plugin/jvmsource/api/JvmAppApi.class",
    "org/autojs/plugin/jvmsource/api/JvmConsoleApi.class",
    "org/autojs/plugin/jvmsource/api/JvmCancellation.class",
    "org/autojs/plugin/jvmsource/api/JvmCancellationException.class",
)

data class HostAlignedSigningMaterial(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

fun requiredSigningValue(properties: Properties, key: String): String =
    properties.getProperty(key)?.trim()?.takeIf(String::isNotEmpty)
        ?: throw GradleException("sign.properties is missing '$key'")

fun loadHostAlignedSigningMaterial(): HostAlignedSigningMaterial {
    val propertiesFile = rootProject.file("sign.properties")
    if (!propertiesFile.isFile) {
        throw GradleException("Host-aligned signing requires ${propertiesFile.absolutePath}")
    }
    val properties = Properties().apply { propertiesFile.inputStream().use(::load) }
    val storePath = requiredSigningValue(properties, "storeFile")
    val storeFile = project.file(storePath).canonicalFile
    if (!storeFile.isFile) {
        throw GradleException("Host-aligned signing store is missing: $storeFile")
    }
    return HostAlignedSigningMaterial(
        storeFile = storeFile,
        storePassword = requiredSigningValue(properties, "storePassword"),
        keyAlias = requiredSigningValue(properties, "keyAlias"),
        keyPassword = requiredSigningValue(properties, "keyPassword"),
    )
}

val hostAlignedSigning = loadHostAlignedSigningMaterial()

android {
    namespace = providerNamespace
    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        applicationId = globalApplicationId
        minSdk = versions.sdkVersionMin
        targetSdk = versions.sdkVersionTarget
        versionCode = versions.appVersionCode
        versionName = versions.appVersionName
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "HOST_PACKAGE_NAME", "\"org.autojs.autojs6\"")
        buildConfigField(
            "long",
            "MIN_HOST_VERSION_CODE",
            "${versions["REQUIRED_HOST_VERSION_CODE"]}L",
        )
        buildConfigField("String", "KOTLIN_COMPILER_VERSION", "\"$kotlinCompilerVersion\"")
        buildConfigField("String", "D8_VERSION", "\"$d8Version\"")
        // Literal schema-v2 release metadata is intentionally kept parser-friendly for
        // AutoJs6-Official-Plugins-Index. Keep the host version in sync with version.properties.
        resValue("string", "plugin_id", "kotlin-jvm")
        resValue("string", "plugin_engine", "jvm-source")
        resValue("string", "plugin_variant", "kotlin-jvm-d8")
        resValue("string", "plugin_requires_host_version", "5276")
        resValue(
            "string",
            "plugin_runtime_component",
            "io.github.supermonster003.autojs6.plugin.kotlin.runtime/org.autojs.plugin.jvmsource.kotlin.service.JavaSourceCompilerService"
        )
        resValue("string", "plugin_protocol_api_min", "1.1")
        resValue("string", "plugin_protocol_api_max", "1.1")
        resValue("string", "plugin_backend", "kotlin-jvm-d8")
        resValue("string", "plugin_task", "jvm-source")
    }

    buildFeatures {
        aidl = true
        buildConfig = true
        resValues = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    signingConfigs {
        create("hostAligned") {
            storeFile = hostAlignedSigning.storeFile
            storePassword = hostAlignedSigning.storePassword
            keyAlias = hostAlignedSigning.keyAlias
            keyPassword = hostAlignedSigning.keyPassword
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("hostAligned")
        }
        release {
            // The pinned compiler jar is shape-checked and bytecode-patched for Android. R8 cannot
            // safely shrink its service/extension graph or its intentionally unreachable JVM-only
            // branches, so release preserves the same audited runtime graph as debug.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("hostAligned")
        }
    }

    sourceSets.named("main") {
        assets.directories.add(generatedCompilerClasspathAssets.get().asFile.absolutePath)
    }

    packaging {
        resources.pickFirsts += setOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE",
            "META-INF/LICENSE.*",
            "META-INF/NOTICE",
            "META-INF/NOTICE.*",
            // The compiler and stdlib copies are byte-identical in the pinned 2.3.21 artifacts.
            "kotlin/annotation/annotation.kotlin_builtins",
            "kotlin/collections/collections.kotlin_builtins",
            "kotlin/concurrent/atomics/atomics.kotlin_builtins",
            "kotlin/coroutines/coroutines.kotlin_builtins",
            "kotlin/internal/internal.kotlin_builtins",
            "kotlin/kotlin.kotlin_builtins",
            "kotlin/ranges/ranges.kotlin_builtins",
            "kotlin/reflect/reflect.kotlin_builtins",
        )
        resources.excludes += setOf(
            "META-INF/*.SF",
            "META-INF/*.DSA",
            "META-INF/*.RSA",
        )
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        ignoreTestSources = true
        // These checks query mutable remote "latest version" state. Runtime/compiler versions are
        // intentionally frozen and upgraded through reviewed build-logic changes, so they cannot
        // be a reproducible zero-new-warning CI gate.
        disable += setOf(
            "AndroidGradlePluginVersion",
            "GradleDependency",
            "NewerVersionAvailable",
        )
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    implementation(files(protocolArtifacts))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinCompilerVersion")
    implementation(
        files(
            layout.buildDirectory.file(
                "generated/kotlin-compiler/kotlin-compiler-embeddable-$kotlinCompilerVersion-android.jar",
            ),
        ).builtBy("patchKotlinCompilerForAndroid"),
    )
    implementation("org.jetbrains.kotlin:kotlin-script-runtime:$kotlinCompilerVersion")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinCompilerReflectVersion")
    implementation("org.jetbrains.kotlin:kotlin-daemon-embeddable:$kotlinCompilerVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$kotlinCompilerCoroutinesVersion")
    implementation("org.jetbrains.intellij.deps:trove4j:$kotlinCompilerTroveVersion")
    implementation("com.android.tools:r8:$d8Version")
    coreLibraryDesugaring("com.android.tools:$desugarArtifact:$desugarVersion")
    add(kotlinCompilerLibraries.name, "org.jetbrains.kotlin:kotlin-stdlib:$kotlinCompilerVersion")
    add(kotlinCompilerEmbeddable.name, "org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinCompilerVersion")
    add(kotlinCompilerTrove.name, "org.jetbrains.intellij.deps:trove4j:$kotlinCompilerTroveVersion")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.eclipse.jdt:ecj:3.26.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0") {
        // AndroidX also requests the multiplatform root module, while this Android target already
        // resolves the concrete core-jvm runtime through test-core. Avoid making offline lint model
        // generation depend on a redundant metadata-only artifact.
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
    }
    androidTestImplementation("androidx.test:runner:1.7.0")
}

val kotlinPerformanceManagerClass = "org/jetbrains/kotlin/util/PerformanceManager"
val kotlinTimeClass = "org/jetbrains/kotlin/util/Time"
val kotlinDefaultJava11ShimClass = "org/jetbrains/kotlin/com/intellij/util/DefaultJava11Shim"
val androidConcurrentLongObjectMapClass =
    "org/autojs/plugin/jvmsource/kotlin/compat/AndroidConcurrentLongObjectMap"
val kotlinPathUtilClass = "org/jetbrains/kotlin/utils/PathUtil"
val kotlinCompilerResourceRootClass =
    "org/autojs/plugin/jvmsource/kotlin/compat/KotlinCompilerResourceRoot"
val kotlinCoreEnvironmentCompanionClass =
    "org/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreEnvironment\$Companion"
val kotlinCompilerExtensionPointsClass =
    "org/autojs/plugin/jvmsource/kotlin/compat/KotlinCompilerExtensionPoints"
val kotlinCompilerExtensionDescriptorSha256 =
    "b56579274b38787840fc11e0703da184173d0a0b4f9d9964e3c346746d76b6e2"
val headlessDesktopClass = "org/autojs/plugin/jvmsource/kotlin/compat/HeadlessDesktop"
val kotlinDesktopTypeMap = linkedMapOf(
    "java/awt/AWTEvent" to "$headlessDesktopClass\$AWTEvent",
    "java/awt/Color" to "$headlessDesktopClass\$Color",
    "java/awt/Component" to "$headlessDesktopClass\$Component",
    "java/awt/Dialog" to "$headlessDesktopClass\$Dialog",
    "java/awt/event/InvocationEvent" to "$headlessDesktopClass\$InvocationEvent",
    "java/awt/EventQueue" to "$headlessDesktopClass\$EventQueue",
    "java/awt/Graphics" to "$headlessDesktopClass\$Graphics",
    "java/awt/Rectangle" to "$headlessDesktopClass\$Rectangle",
    "java/awt/Toolkit" to "$headlessDesktopClass\$Toolkit",
    "java/awt/Window" to "$headlessDesktopClass\$Window",
    "java/beans/Introspector" to "$headlessDesktopClass\$Introspector",
    "java/beans/PropertyChangeSupport" to "$headlessDesktopClass\$PropertyChangeSupport",
    "javax/swing/Icon" to "$headlessDesktopClass\$Icon",
    "javax/swing/JComponent" to "$headlessDesktopClass\$JComponent",
    "javax/swing/RepaintManager" to "$headlessDesktopClass\$RepaintManager",
    "javax/swing/SwingUtilities" to "$headlessDesktopClass\$SwingUtilities",
    "javax/swing/text/html/HTML" to "$headlessDesktopClass\$Html",
    "javax/swing/text/html/HTML\$Tag" to "$headlessDesktopClass\$Html\$Tag",
    "javax/swing/text/html/HTMLEditorKit" to "$headlessDesktopClass\$HtmlEditorKit",
    "javax/swing/text/html/HTMLEditorKit\$ParserCallback" to
        "$headlessDesktopClass\$HtmlEditorKit\$ParserCallback",
    "javax/swing/text/html/parser/ParserDelegator" to "$headlessDesktopClass\$ParserDelegator",
    "javax/swing/text/MutableAttributeSet" to "$headlessDesktopClass\$MutableAttributeSet",
)

fun relocateKotlinDesktopTypes(
    original: ByteArray,
    observedTypes: MutableSet<String>,
): ByteArray {
    val writer = ClassWriter(0)
    val remapper = object : Remapper(Opcodes.ASM9) {
        override fun map(internalName: String): String {
            val isDesktopType = internalName.startsWith("java/awt/") ||
                internalName.startsWith("java/beans/") ||
                internalName.startsWith("javax/swing/")
            if (!isDesktopType) return internalName
            val mapped = kotlinDesktopTypeMap[internalName]
                ?: error("Unadmitted Kotlin desktop dependency: $internalName")
            observedTypes += internalName
            return mapped
        }
    }
    ClassReader(original).accept(ClassRemapper(writer, remapper), 0)
    return writer.toByteArray()
}
val performanceManagerPatches = setOf(
    "currentTime()L$kotlinTimeClass;",
    "initializeCurrentThread()V",
    "enableExtendedStats()V",
)

fun patchKotlinPerformanceManagerForAndroid(original: ByteArray): ByteArray {
    val classNode = ClassNode()
    ClassReader(original).accept(classNode, 0)
    check(classNode.name == kotlinPerformanceManagerClass) {
        "Unexpected Kotlin performance manager class: ${classNode.name}"
    }
    val patched = linkedSetOf<String>()
    classNode.methods.forEach { method ->
        val identity = method.name + method.desc
        if (identity !in performanceManagerPatches) return@forEach
        method.instructions.clear()
        method.tryCatchBlocks.clear()
        method.localVariables?.clear()
        when (identity) {
            "currentTime()L$kotlinTimeClass;" -> method.instructions.apply {
                add(TypeInsnNode(Opcodes.NEW, kotlinTimeClass))
                add(InsnNode(Opcodes.DUP))
                add(MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false))
                add(InsnNode(Opcodes.LCONST_0))
                add(InsnNode(Opcodes.LCONST_0))
                add(MethodInsnNode(Opcodes.INVOKESPECIAL, kotlinTimeClass, "<init>", "(JJJ)V", false))
                add(InsnNode(Opcodes.ARETURN))
            }
            "initializeCurrentThread()V" -> method.instructions.apply {
                add(VarInsnNode(Opcodes.ALOAD, 0))
                add(MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "java/lang/Thread",
                    "currentThread",
                    "()Ljava/lang/Thread;",
                    false,
                ))
                add(FieldInsnNode(
                    Opcodes.PUTFIELD,
                    kotlinPerformanceManagerClass,
                    "thread",
                    "Ljava/lang/Thread;",
                ))
                add(InsnNode(Opcodes.RETURN))
            }
            "enableExtendedStats()V" -> method.instructions.add(InsnNode(Opcodes.RETURN))
        }
        method.maxStack = 0
        method.maxLocals = 1
        patched += identity
    }
    check(patched == performanceManagerPatches) {
        "Kotlin $kotlinCompilerVersion performance manager shape changed: patched=$patched"
    }
    val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
    classNode.accept(writer)
    return writer.toByteArray().also { transformed ->
        val verification = ClassNode()
        ClassReader(transformed).accept(verification, 0)
        verification.methods
            .filter { it.name + it.desc in performanceManagerPatches }
            .flatMap { it.instructions.toArray().filterIsInstance<MethodInsnNode>() }
            .forEach { invocation ->
                check(!invocation.owner.startsWith("java/lang/management/")) {
                    "Android Kotlin compiler patch retained ${invocation.owner}.${invocation.name}"
                }
            }
    }
}

fun patchKotlinJava11ShimForAndroid(original: ByteArray): ByteArray {
    val classNode = ClassNode()
    ClassReader(original).accept(classNode, 0)
    check(classNode.name == kotlinDefaultJava11ShimClass) {
        "Unexpected Kotlin Java 11 shim class: ${classNode.name}"
    }
    val identity = "createConcurrentLongObjectMap()" +
        "Lorg/jetbrains/kotlin/com/intellij/util/containers/ConcurrentLongObjectMap;"
    val method = classNode.methods.singleOrNull { it.name + it.desc == identity }
        ?: error("Kotlin $kotlinCompilerVersion Java 11 shim shape changed")
    method.instructions.clear()
    method.tryCatchBlocks.clear()
    method.localVariables?.clear()
    method.instructions.apply {
        add(TypeInsnNode(Opcodes.NEW, androidConcurrentLongObjectMapClass))
        add(InsnNode(Opcodes.DUP))
        add(MethodInsnNode(
            Opcodes.INVOKESPECIAL,
            androidConcurrentLongObjectMapClass,
            "<init>",
            "()V",
            false,
        ))
        add(InsnNode(Opcodes.ARETURN))
    }
    method.maxStack = 0
    method.maxLocals = 1
    val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
    classNode.accept(writer)
    return writer.toByteArray().also { transformed ->
        val verification = ClassNode()
        ClassReader(transformed).accept(verification, 0)
        val constructorOwners = verification.methods
            .single { it.name + it.desc == identity }
            .instructions
            .toArray()
            .filterIsInstance<MethodInsnNode>()
            .filter { it.name == "<init>" }
            .map { it.owner }
        check(constructorOwners == listOf(androidConcurrentLongObjectMapClass)) {
            "Android Java 11 shim patch targets differ: $constructorOwners"
        }
    }
}

fun patchKotlinPathUtilForAndroid(original: ByteArray): ByteArray {
    val classNode = ClassNode()
    ClassReader(original).accept(classNode, 0)
    check(classNode.name == kotlinPathUtilClass) {
        "Unexpected Kotlin PathUtil class: ${classNode.name}"
    }
    val identity = "getResourcePathForClass(Ljava/lang/Class;)Ljava/io/File;"
    val method = classNode.methods.singleOrNull { it.name + it.desc == identity }
        ?: error("Kotlin $kotlinCompilerVersion PathUtil shape changed")
    method.instructions.clear()
    method.tryCatchBlocks.clear()
    method.localVariables?.clear()
    method.instructions.apply {
        add(VarInsnNode(Opcodes.ALOAD, 0))
        add(MethodInsnNode(
            Opcodes.INVOKESTATIC,
            kotlinCompilerResourceRootClass,
            "resolve",
            "(Ljava/lang/Class;)Ljava/io/File;",
            false,
        ))
        add(InsnNode(Opcodes.ARETURN))
    }
    method.maxStack = 0
    method.maxLocals = 1
    val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
    classNode.accept(writer)
    return writer.toByteArray().also { transformed ->
        val verification = ClassNode()
        ClassReader(transformed).accept(verification, 0)
        val invocations = verification.methods
            .single { it.name + it.desc == identity }
            .instructions
            .toArray()
            .filterIsInstance<MethodInsnNode>()
            .map { "${it.owner}.${it.name}${it.desc}" }
        check(
            invocations == listOf(
                "$kotlinCompilerResourceRootClass.resolve(Ljava/lang/Class;)Ljava/io/File;",
            ),
        ) {
            "Android PathUtil patch targets differ: $invocations"
        }
    }
}

fun patchKotlinExtensionPointLoadingForAndroid(original: ByteArray): ByteArray {
    val classNode = ClassNode()
    ClassReader(original).accept(classNode, 0)
    check(classNode.name == kotlinCoreEnvironmentCompanionClass) {
        "Unexpected KotlinCoreEnvironment companion class: ${classNode.name}"
    }
    val identity = "registerApplicationExtensionPointsAndExtensionsFrom" +
        "(Lorg/jetbrains/kotlin/config/CompilerConfiguration;Ljava/lang/String;)V"
    val method = classNode.methods.singleOrNull { it.name + it.desc == identity }
        ?: error("Kotlin $kotlinCompilerVersion extension registration shape changed")
    method.instructions.clear()
    method.tryCatchBlocks.clear()
    method.localVariables?.clear()
    method.instructions.apply {
        add(MethodInsnNode(
            Opcodes.INVOKESTATIC,
            kotlinCompilerExtensionPointsClass,
            "register",
            "()V",
            false,
        ))
        add(InsnNode(Opcodes.RETURN))
    }
    method.maxStack = 0
    method.maxLocals = 3
    val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
    classNode.accept(writer)
    return writer.toByteArray().also { transformed ->
        val verification = ClassNode()
        ClassReader(transformed).accept(verification, 0)
        val invocations = verification.methods
            .single { it.name + it.desc == identity }
            .instructions
            .toArray()
            .filterIsInstance<MethodInsnNode>()
            .map { "${it.owner}.${it.name}${it.desc}" }
        check(invocations == listOf("$kotlinCompilerExtensionPointsClass.register()V")) {
            "Android Kotlin extension registration patch targets differ: $invocations"
        }
    }
}

val patchedKotlinCompilerJar = layout.buildDirectory.file(
    "generated/kotlin-compiler/kotlin-compiler-embeddable-$kotlinCompilerVersion-android.jar",
)

val patchKotlinCompilerForAndroid = tasks.register("patchKotlinCompilerForAndroid") {
    group = "build"
    description = "Removes unsupported java.lang.management timing from the embedded Kotlin compiler."
    inputs.files(kotlinCompilerEmbeddable)
    outputs.file(patchedKotlinCompilerJar)

    doLast {
        val source = kotlinCompilerEmbeddable.singleFile
        check(source.name == "kotlin-compiler-embeddable-$kotlinCompilerVersion.jar") {
            "Unexpected Kotlin compiler artifact: $source"
        }
        val destination = patchedKotlinCompilerJar.get().asFile
        destination.parentFile.mkdirs()
        val patchedClasses = linkedSetOf<String>()
        val relocatedDesktopTypes = linkedSetOf<String>()
        var extensionDescriptorVerified = false
        ZipFile(source).use { input ->
            JarOutputStream(FileOutputStream(destination).buffered()).use { output ->
                input.entries().asSequence()
                    .filterNot { entry ->
                        val upper = entry.name.uppercase()
                        upper.startsWith("META-INF/") &&
                            (upper.endsWith(".SF") || upper.endsWith(".DSA") || upper.endsWith(".RSA"))
                    }
                    .sortedWith(compareBy(
                        { if (it.name == "META-INF/MANIFEST.MF") 0 else 1 },
                        { it.name },
                    ))
                    .forEach { entry ->
                        val bytes = if (entry.isDirectory) {
                            ByteArray(0)
                        } else {
                            input.getInputStream(entry).use { it.readBytes() }
                        }
                        if (entry.name == "META-INF/extensions/compiler.xml") {
                            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
                                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                            check(digest == kotlinCompilerExtensionDescriptorSha256) {
                                "Kotlin $kotlinCompilerVersion compiler.xml changed: $digest"
                            }
                            extensionDescriptorVerified = true
                        }
                        val androidPatched = when (entry.name) {
                            "$kotlinPerformanceManagerClass.class" -> {
                                check(patchedClasses.add(kotlinPerformanceManagerClass)) {
                                    "Duplicate Kotlin PerformanceManager class"
                                }
                                patchKotlinPerformanceManagerForAndroid(bytes)
                            }
                            "$kotlinDefaultJava11ShimClass.class" -> {
                                check(patchedClasses.add(kotlinDefaultJava11ShimClass)) {
                                    "Duplicate Kotlin DefaultJava11Shim class"
                                }
                                patchKotlinJava11ShimForAndroid(bytes)
                            }
                            "$kotlinPathUtilClass.class" -> {
                                check(patchedClasses.add(kotlinPathUtilClass)) {
                                    "Duplicate Kotlin PathUtil class"
                                }
                                patchKotlinPathUtilForAndroid(bytes)
                            }
                            "$kotlinCoreEnvironmentCompanionClass.class" -> {
                                check(patchedClasses.add(kotlinCoreEnvironmentCompanionClass)) {
                                    "Duplicate KotlinCoreEnvironment companion class"
                                }
                                patchKotlinExtensionPointLoadingForAndroid(bytes)
                            }
                            else -> bytes
                        }
                        val transformed = if (!entry.isDirectory && entry.name.endsWith(".class")) {
                            relocateKotlinDesktopTypes(androidPatched, relocatedDesktopTypes)
                        } else {
                            androidPatched
                        }
                        output.putNextEntry(JarEntry(entry.name).apply { time = 0L })
                        output.write(transformed)
                        output.closeEntry()
                    }
            }
        }
        check(
            patchedClasses == setOf(
                kotlinPerformanceManagerClass,
                kotlinDefaultJava11ShimClass,
                kotlinPathUtilClass,
                kotlinCoreEnvironmentCompanionClass,
            ),
        ) {
            "Kotlin compiler Android patch class set differs: $patchedClasses"
        }
        check(extensionDescriptorVerified) { "Kotlin compiler has no compiler.xml descriptor" }
        check(relocatedDesktopTypes == kotlinDesktopTypeMap.keys) {
            "Kotlin compiler desktop dependency set differs: $relocatedDesktopTypes"
        }
    }
}

fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

fun verifyPinnedProtocolInputs(protocolDirectory: File) {
    check(versions["REQUIRED_HOST_VERSION_CODE"] == "5276") {
        "plugin_requires_host_version must stay aligned with REQUIRED_HOST_VERSION_CODE"
    }
    val lockFile = protocolDirectory.resolve("protocol-artifacts.lock.json")
    check(lockFile.isFile) { "Missing protocol lock: $lockFile" }
    val lock = JsonSlurper().parse(lockFile) as? Map<*, *>
        ?: error("Protocol lock root must be a JSON object")
    check((lock["schemaVersion"] as? Number)?.toInt() == 1) {
        "Unsupported protocol lock schema"
    }
    val rows = lock["artifacts"] as? List<*>
        ?: error("Protocol lock artifacts must be an array")
    val lockedArtifacts = rows.associate { rawRow ->
        val row = rawRow as? Map<*, *> ?: error("Protocol artifact row must be an object")
        val fileName = row["file"] as? String ?: error("Protocol artifact file is missing")
        val sourceModule = row["sourceModule"] as? String
            ?: error("Protocol artifact sourceModule is missing")
        val sha256 = row["sha256"] as? String ?: error("Protocol artifact sha256 is missing")
        check(sha256.matches(Regex("^[0-9a-f]{64}$"))) {
            "Protocol artifact digest is not lowercase SHA-256: $fileName"
        }
        fileName to (sourceModule to sha256)
    }
    check(lockedArtifacts.keys == expectedProtocolModules.keys) {
        "Protocol lock file set differs: ${lockedArtifacts.keys}"
    }
    expectedProtocolModules.forEach { (fileName, sourceModule) ->
        val artifact = protocolDirectory.resolve(fileName)
        check(artifact.isFile) { "Pinned protocol artifact is missing: $artifact" }
        check(!Files.isSymbolicLink(artifact.toPath())) {
            "Pinned protocol artifact must not be a symlink: $artifact"
        }
        val locked = checkNotNull(lockedArtifacts[fileName])
        check(locked.first == sourceModule) {
            "Protocol source module differs for $fileName: ${locked.first}"
        }
        check(artifact.sha256() == locked.second) {
            "Pinned protocol artifact digest differs: $fileName"
        }
    }
}

val verifyPinnedInputs = tasks.register("verifyPinnedInputs") {
    group = "verification"
    description = "Verifies the frozen AutoJs6 protocol AAR set and exact digests."
    inputs.file(protocolLockFile)
    inputs.files(protocolArtifacts)

    doLast {
        verifyPinnedProtocolInputs(rootProject.file("protocol"))
    }
}

val verifyPinnedInputsFailurePath = tasks.register("verifyPinnedInputsFailurePath") {
    group = "verification"
    description = "Proves that one-byte protocol AAR corruption is rejected by the pinned-input verifier."
    inputs.file(protocolLockFile)
    inputs.files(protocolArtifacts)

    doLast {
        val isolatedProtocolDirectory = temporaryDir.resolve("protocol").also { directory ->
            if (directory.exists()) check(directory.deleteRecursively()) {
                "Unable to reset pinned-input failure-path directory: $directory"
            }
            check(directory.mkdirs()) {
                "Unable to create pinned-input failure-path directory: $directory"
            }
        }
        protocolLockFile.copyTo(
            isolatedProtocolDirectory.resolve(protocolLockFile.name),
            overwrite = false,
        )
        protocolArtifacts.forEach { artifact ->
            artifact.copyTo(isolatedProtocolDirectory.resolve(artifact.name), overwrite = false)
        }
        val corruptedArtifactName = expectedProtocolModules.keys.first()
        FileOutputStream(isolatedProtocolDirectory.resolve(corruptedArtifactName), true).use { output ->
            output.write(0)
        }

        val failure = runCatching {
            verifyPinnedProtocolInputs(isolatedProtocolDirectory)
        }.exceptionOrNull()
        checkNotNull(failure) {
            "Pinned-input verifier accepted one-byte corruption in $corruptedArtifactName"
        }
        check(failure.message == "Pinned protocol artifact digest differs: $corruptedArtifactName") {
            "Pinned-input corruption failed for an unexpected reason: ${failure.message}"
        }
    }
}

val verifyKotlinCompilerRuntime = tasks.register("verifyKotlinCompilerRuntime") {
    group = "verification"
    description = "Verifies Android-packaged support classes required by the embedded Kotlin compiler."
    inputs.files(kotlinCompilerTrove)

    doLast {
        val troveJar = kotlinCompilerTrove.singleFile
        check(
            troveJar.isFile &&
                troveJar.name == "trove4j-$kotlinCompilerTroveVersion.jar",
        ) {
            "Unexpected Kotlin compiler Trove runtime artifact: $troveJar"
        }
        ZipFile(troveJar).use { zip ->
            check(zip.getEntry("gnu/trove/TObjectHashingStrategy.class") != null) {
                "Kotlin compiler Trove runtime is missing TObjectHashingStrategy: $troveJar"
            }
        }
    }
}

val androidSdkDirectory = androidComponents.sdkComponents.sdkDirectory.get().asFile
val sdkAndroidJar = File(androidSdkDirectory, "platforms/android-$compilerStubApi/android.jar")

val prepareJvmSourceCompilerClasspath = tasks.register("prepareJvmSourceCompilerClasspath") {
    group = "build"
    description = "Packages controlled API 24, entry ABI, and Kotlin stdlib compiler assets."
    dependsOn(verifyPinnedInputs)
    inputs.file(sdkAndroidJar)
    inputs.file(jvmSourceApiAar)
    inputs.files(kotlinCompilerLibraries)
    outputs.dir(generatedCompilerClasspathAssets)

    doLast {
        check(sdkAndroidJar.isFile) {
            "Missing required API $compilerStubApi android.jar: $sdkAndroidJar"
        }
        val kotlinStdlibJar = kotlinCompilerLibraries.singleFile
        check(kotlinStdlibJar.isFile && kotlinStdlibJar.name == "kotlin-stdlib-$kotlinCompilerVersion.jar") {
            "Unexpected Kotlin stdlib compiler classpath artifact: $kotlinStdlibJar"
        }
        val classesJarBytes = ZipFile(jvmSourceApiAar).use { aar ->
            val entry = checkNotNull(aar.getEntry("classes.jar")) {
                "jvm-source-api AAR has no classes.jar"
            }
            aar.getInputStream(entry).use { it.readBytes() }
        }
        val selectedClasses = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(classesJarBytes)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                if (!entry.isDirectory && entry.name in entryApiClassEntries) {
                    check(selectedClasses.put(entry.name, input.readBytes()) == null) {
                        "Duplicate entry ABI class: ${entry.name}"
                    }
                }
                input.closeEntry()
            }
        }
        check(selectedClasses.keys == entryApiClassEntries) {
            "jvm-source entry ABI classes differ: ${selectedClasses.keys}"
        }

        val assetRoot = generatedCompilerClasspathAssets.get().asFile
        if (assetRoot.exists()) check(assetRoot.deleteRecursively()) {
            "Unable to replace generated compiler classpath: $assetRoot"
        }
        val classpathRoot = assetRoot.resolve("compiler-classpath")
        check(classpathRoot.mkdirs()) { "Unable to create compiler classpath: $classpathRoot" }
        sdkAndroidJar.copyTo(classpathRoot.resolve("android.jar"), overwrite = false)
        kotlinStdlibJar.copyTo(classpathRoot.resolve("kotlin-stdlib.jar"), overwrite = false)
        JarOutputStream(
            FileOutputStream(classpathRoot.resolve("entry-api.jar")).buffered(),
        ).use { output ->
            entryApiClassEntries.forEach { name ->
                output.putNextEntry(JarEntry(name).apply { time = 0L })
                output.write(checkNotNull(selectedClasses[name]))
                output.closeEntry()
            }
        }
    }
}

tasks.matching { task ->
    task.name.startsWith("compile") ||
        task.name.startsWith("assemble") ||
        task.name.startsWith("bundle")
}.configureEach {
    dependsOn(verifyPinnedInputs)
    dependsOn(verifyKotlinCompilerRuntime)
}

val verifyPinnedInputsWiring = tasks.register("verifyPinnedInputsWiring") {
    group = "verification"
    description = "Verifies that debug and release assembly cannot bypass frozen protocol inputs."

    doLast {
        val verifier = verifyPinnedInputs.get()
        listOf("assembleDebug", "assembleRelease").forEach { taskName ->
            val guardedTask = tasks.named(taskName).get()
            check(verifier in guardedTask.taskDependencies.getDependencies(guardedTask)) {
                "$taskName must depend directly on ${verifier.name}"
            }
        }
    }
}

tasks.matching { task ->
    task.name.startsWith("merge") && task.name.endsWith("Assets") ||
        task.name.contains("lint", ignoreCase = true)
}.configureEach {
    dependsOn(prepareJvmSourceCompilerClasspath)
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    dependsOn(prepareJvmSourceCompilerClasspath)
    systemProperty(
        "autojs.kotlin.compilerClasspathRoot",
        generatedCompilerClasspathAssets.get().asFile.resolve("compiler-classpath").absolutePath,
    )
}

versions.handleIfNeeded(project, "", listOf("debug", "release"))
