package com.example.healthtracker.navigation

import android.net.Uri
import com.example.healthtracker.domain.model.GlucoseContext

object DeepLinks {
    const val SCHEME = "healthtracker"
    const val HOST_LOG_GLUCOSE = "log-glucose"
    const val HOST_CARB_TEST = "carb-test"
    const val ARG_CONTEXT = "context"

    /** Pattern for navigation-compose's navDeepLink { uriPattern = ... }. */
    const val LOG_GLUCOSE_PATTERN = "$SCHEME://$HOST_LOG_GLUCOSE?$ARG_CONTEXT={$ARG_CONTEXT}"

    const val CARB_TEST_PATTERN = "$SCHEME://$HOST_CARB_TEST"

    fun carbTest(): Uri = Uri.Builder().scheme(SCHEME).authority(HOST_CARB_TEST).build()

    fun logGlucose(context: GlucoseContext): Uri = Uri.Builder()
        .scheme(SCHEME)
        .authority(HOST_LOG_GLUCOSE)
        .appendQueryParameter(ARG_CONTEXT, context.name)
        .build()
}
