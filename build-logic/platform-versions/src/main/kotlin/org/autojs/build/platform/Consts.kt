package org.autojs.build.platform

/** Shared constants of the version decision mechanism. */
object Consts {

    /** Placeholder used when the platform version cannot be determined. */
    const val DEFAULT_VERSION = "0"

}

/**
 * Hint suffixes appended to console output, telling how each version was decided.
 *
 * zh-CN: 控制台输出的提示后缀, 用于说明每个版本是如何被决定的.
 */
object Identifier {

    const val UPGRADED = "upgraded"
    const val DOWNGRADED = "downgraded"
    const val NEAREST_LOWER_MATCHED = "nearest-lower-matched"
    const val FALLBACK = "fallback"

    const val SPECIFIED = "specified"
    const val AUTO = "auto"
    const val TOML = "toml"

    const val UPGRADED_SUFFIX = " [$UPGRADED]"
    const val DOWNGRADED_SUFFIX = " [$DOWNGRADED]"
    const val NEAREST_LOWER_MATCHED_SUFFIX = " [$NEAREST_LOWER_MATCHED]"
    const val FALLBACK_SUFFIX = " [$FALLBACK]"
    const val AUTO_SPECIFIED_SUFFIX = " [$AUTO-$SPECIFIED]"
    const val USER_SPECIFIED_SUFFIX = " [user-$SPECIFIED]"
    const val TOML_SPECIFIED_SUFFIX = " [$TOML-$SPECIFIED]"

    /** Marks an R8 that AGP already bundles, so it is reported but not put on the classpath. */
    const val AGP_BUNDLED_SUFFIX = " [agp-bundled]"

}
