package com.tealium.prism.core.api.settings.modules

import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.internal.modules.trace.TraceModuleConfiguration

/**
 * A [ModuleSettingsBuilder] implementation to configure settings relevant to the Trace [Module]
 */
class TraceSettingsBuilder : CollectorSettingsBuilder<TraceSettingsBuilder>(Modules.Types.TRACE) {

    /**
     * Sets whether or not errors should be tracked during a Trace session.
     *
     * When enabled, the Trace module will automatically capture and send error events
     * that occur while a trace session is active. This is useful for debugging and
     * monitoring application errors in conjunction with trace data.
     *
     * Error events are sent as separate dispatches with the event name "tealium_error"
     * and include an "error_description" field containing the error details.
     *
     * The default setting is `false`
     *
     * @param enabled `true` if errors should be tracked automatically during trace session; else `false`
     */
    fun setTrackErrors(enabled: Boolean) = apply {
        configuration.put(TraceModuleConfiguration.KEY_TRACK_ERRORS, enabled)
    }
}