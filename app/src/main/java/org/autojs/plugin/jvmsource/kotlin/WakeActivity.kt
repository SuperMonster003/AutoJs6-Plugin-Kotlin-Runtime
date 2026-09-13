package org.autojs.plugin.jvmsource.kotlin

import android.app.Activity
import android.os.Bundle

/** Clears the installed package's stopped state through the host activation contract. */
class WakeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
