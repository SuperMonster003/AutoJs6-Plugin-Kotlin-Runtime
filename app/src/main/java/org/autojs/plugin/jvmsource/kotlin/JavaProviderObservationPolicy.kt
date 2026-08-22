package org.autojs.plugin.jvmsource.kotlin

import org.autojs.plugin.jvmsource.api.JvmSourceContract
import java.util.Collections
import java.util.EnumMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal enum class JavaProviderStartProfile(val wireCode: Int) {
    COLD(1),
    WARM(2),
}

internal enum class JavaProviderObservedProcess(val wireCode: Int) {
    HOST(1),
    COMPILER(2),
    WORKER(3),
}

internal enum class JavaProviderObservedPhase(val wireCode: Int) {
    COMPILE(1),
    D8(2),
    LOAD(3),
    RUN(4),
    TERMINATION(5),
}

internal enum class JavaProviderCacheOutcome(val wireCode: Int) {
    NOT_EVALUATED(1),
    HIT(2),
    MISS(3),
}

internal enum class JavaProviderCacheMissReason(val wireCode: Int) {
    NOT_FOUND(1),
    INVALID_OR_EXPIRED(2),
    CACHE_UNAVAILABLE(3),
    MATERIALIZATION_FAILED(4),
    CACHE_DISABLED(5),
    PROVIDER_IDENTITY_UNAVAILABLE(6),
    PROVIDER_IDENTITY_DRIFTED(7),
}

/**
 * A bounded, path-free resource sample. Null means the platform probe was unavailable; zero is a
 * real observation. Protocol V1 does not export this model.
 */
internal data class JavaProviderResourceObservation(
    val process: JavaProviderObservedProcess,
    val phase: JavaProviderObservedPhase,
    val rssBytes: Long?,
    val openFileDescriptorCount: Int?,
    val temporaryStorageBytes: Long?,
    val outputBytes: Long?,
)

internal data class JavaProviderObservation(
    val startProfile: JavaProviderStartProfile,
    val compileDurationMillis: Long?,
    val d8DurationMillis: Long?,
    val loadDurationMillis: Long?,
    val runDurationMillis: Long?,
    val terminationDurationMillis: Long?,
    val cacheOutcome: JavaProviderCacheOutcome,
    val cacheMissReason: JavaProviderCacheMissReason?,
    val resources: List<JavaProviderResourceObservation>,
) {
    init {
        require(resources.size <= JavaProviderObservationPolicy.MAX_RESOURCE_SAMPLES)
        require((cacheOutcome == JavaProviderCacheOutcome.MISS) == (cacheMissReason != null))
    }
}

/**
 * Source-deployed R3 collection policy. It records only coarse numeric counters and durations;
 * request IDs, paths, package/component names, signatures, Binder values, UIDs and PIDs are not
 * representable. Export needs a future negotiated protocol and runtime/performance evidence.
 */
internal class JavaProviderObservationCollector(
    private val startProfile: JavaProviderStartProfile,
) {
    private val durations = EnumMap<JavaProviderObservedPhase, Long>(JavaProviderObservedPhase::class.java)
    private val resources = ArrayList<JavaProviderResourceObservation>()
    private var cacheOutcome = JavaProviderCacheOutcome.NOT_EVALUATED
    private var cacheMissReason: JavaProviderCacheMissReason? = null

    @Synchronized
    fun recordDuration(phase: JavaProviderObservedPhase, durationMillis: Long): Boolean {
        if (!JavaProviderObservationPolicy.isDurationAdmitted(durationMillis) || durations.containsKey(phase)) {
            return false
        }
        durations[phase] = durationMillis
        return true
    }

    @Synchronized
    fun recordResource(value: JavaProviderResourceObservation): Boolean {
        if (resources.size >= JavaProviderObservationPolicy.MAX_RESOURCE_SAMPLES ||
            !JavaProviderObservationPolicy.isResourceAdmitted(value)
        ) return false
        resources += value
        return true
    }

    @Synchronized
    fun recordCache(
        outcome: JavaProviderCacheOutcome,
        missReason: JavaProviderCacheMissReason? = null,
    ): Boolean {
        if (cacheOutcome != JavaProviderCacheOutcome.NOT_EVALUATED ||
            (outcome == JavaProviderCacheOutcome.MISS) != (missReason != null) ||
            outcome == JavaProviderCacheOutcome.NOT_EVALUATED
        ) return false
        cacheOutcome = outcome
        cacheMissReason = missReason
        return true
    }

    @Synchronized
    fun snapshot(): JavaProviderObservation = JavaProviderObservation(
        startProfile = startProfile,
        compileDurationMillis = durations[JavaProviderObservedPhase.COMPILE],
        d8DurationMillis = durations[JavaProviderObservedPhase.D8],
        loadDurationMillis = durations[JavaProviderObservedPhase.LOAD],
        runDurationMillis = durations[JavaProviderObservedPhase.RUN],
        terminationDurationMillis = durations[JavaProviderObservedPhase.TERMINATION],
        cacheOutcome = cacheOutcome,
        cacheMissReason = cacheMissReason,
        resources = Collections.unmodifiableList(resources.toList()),
    )
}

/** One bounded last-snapshot slot per provider process; no observation crosses Protocol V1. */
internal object JavaProviderObservationRegistry {
    private val compilerCompleted = AtomicBoolean(false)
    private val lastCompiler = AtomicReference<JavaProviderObservation?>()
    private val lastWorker = AtomicReference<JavaProviderObservation?>()

    fun compilerStartProfile(environmentWasAlreadyInitialized: Boolean): JavaProviderStartProfile =
        JavaProviderObservationPolicy.startProfile(
            environmentWasAlreadyInitialized = environmentWasAlreadyInitialized,
            priorSessionCompletedInCompilerProcess = compilerCompleted.get(),
            process = JavaProviderObservedProcess.COMPILER,
        )

    fun workerStartProfile(): JavaProviderStartProfile = JavaProviderStartProfile.COLD

    fun publishCompiler(value: JavaProviderObservation) {
        lastCompiler.set(value)
        compilerCompleted.set(true)
    }

    fun publishWorker(value: JavaProviderObservation) {
        lastWorker.set(value)
    }

    fun compilerSnapshot(): JavaProviderObservation? = lastCompiler.get()
    fun workerSnapshot(): JavaProviderObservation? = lastWorker.get()

    internal fun resetForTest() {
        compilerCompleted.set(false)
        lastCompiler.set(null)
        lastWorker.set(null)
    }
}

internal object JavaProviderObservationPolicy {
    const val MAX_RESOURCE_SAMPLES = 32
    private const val MAX_RSS_BYTES = 8L * 1024L * 1024L * 1024L
    private const val MAX_OPEN_FILE_DESCRIPTORS = 32_768
    private const val MAX_TEMPORARY_STORAGE_BYTES =
        JvmSourceContract.MAX_SOURCE_BYTES +
            JvmSourceContract.MAX_CLASS_ARTIFACT_BYTES +
            JvmSourceContract.MAX_DEX_ARTIFACT_BYTES
    private const val MAX_OUTPUT_BYTES = JvmSourceContract.MAX_STDOUT_BYTES + JvmSourceContract.MAX_STDERR_BYTES

    fun startProfile(
        environmentWasAlreadyInitialized: Boolean,
        priorSessionCompletedInCompilerProcess: Boolean,
        process: JavaProviderObservedProcess,
    ): JavaProviderStartProfile = when {
        process == JavaProviderObservedProcess.WORKER -> JavaProviderStartProfile.COLD
        environmentWasAlreadyInitialized && priorSessionCompletedInCompilerProcess -> JavaProviderStartProfile.WARM
        else -> JavaProviderStartProfile.COLD
    }

    fun isDurationAdmitted(value: Long): Boolean = value in 0L..MAX_PHASE_DURATION_MILLIS

    fun isResourceAdmitted(value: JavaProviderResourceObservation): Boolean =
        value.rssBytes.isMissingOrIn(0L..MAX_RSS_BYTES) &&
            value.openFileDescriptorCount.isMissingOrIn(0..MAX_OPEN_FILE_DESCRIPTORS) &&
            value.temporaryStorageBytes.isMissingOrIn(0L..MAX_TEMPORARY_STORAGE_BYTES) &&
            value.outputBytes.isMissingOrIn(0L..MAX_OUTPUT_BYTES) &&
            listOf(
                value.rssBytes,
                value.openFileDescriptorCount,
                value.temporaryStorageBytes,
                value.outputBytes,
            ).any { it != null }

    private fun Long?.isMissingOrIn(range: LongRange): Boolean = this == null || this in range
    private fun Int?.isMissingOrIn(range: IntRange): Boolean = this == null || this in range

    private const val MAX_PHASE_DURATION_MILLIS = JvmSourceContract.MAX_TIMEOUT_MILLIS + 10_000L
}
