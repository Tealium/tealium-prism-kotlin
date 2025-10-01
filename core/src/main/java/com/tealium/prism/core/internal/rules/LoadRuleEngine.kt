package com.tealium.prism.core.internal.rules

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.logger.logIfWarnEnabled
import com.tealium.prism.core.api.modules.Collector
import com.tealium.prism.core.api.modules.Dispatcher
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.pubsub.ObservableState
import com.tealium.prism.core.api.rules.InvalidMatchException
import com.tealium.prism.core.api.rules.Matchable
import com.tealium.prism.core.api.rules.Rule
import com.tealium.prism.core.api.rules.RuleNotFoundException
import com.tealium.prism.core.api.rules.asMatchable
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.internal.dispatch.DispatchSplit
import com.tealium.prism.core.internal.logger.LogCategory
import com.tealium.prism.core.internal.settings.ModuleSettings
import com.tealium.prism.core.internal.settings.SdkSettings

/**
 * The [LoadRuleEngineImpl] is responsible for receiving the [SdkSettings] object, and transforming the
 * set of common LoadRules into composite LoadRules for specific [Module] implementations.
 *
 * That is, the [SdkSettings] has common, re-usable [LoadRule]s at [SdkSettings.loadRules], and [Module]s
 * are able to reference them in their own [ModuleSettings.rules] property, as a [Rule<String>].
 *
 * The [LoadRuleEngineImpl] therefore builds [Matchable] implementations upon each update to the [SdkSettings]
 * the can be used to decide whether or not a particular [Module] can execute their task.
 */
interface LoadRuleEngine {

    /**
     * Evaluates the load rules for the given [dispatcher] and [dispatches]. The result is a partition
     * of the [dispatches] into two lists of either "successful" or "unsuccessful"
     */
    fun evaluateLoadRules(dispatcher: Dispatcher, dispatches: List<Dispatch>): DispatchSplit

    /**
     * Evaluates the load rules for the given [collector] and [dispatch] and returns `true` if the
     * load rules allow the [collector] to collect data. Otherwise it will return false.
     */
    fun rulesAllow(collector: Collector, dispatch: Dispatch): Boolean
}


class LoadRuleEngineImpl(
    settings: ObservableState<SdkSettings>,
    private val logger: Logger
) : LoadRuleEngine {

    /**
     * Module Load Rules, keyed as [ModuleId -> Composite-LoadRule.]
     */
    private var loadRules = mapOf<String, Matchable<DataObject>>()

    init {
        settings.subscribe(::initializeRules)
    }

    private fun initializeRules(sdkSettings: SdkSettings) {
        initializeRules(
            sdkSettings.loadRules,
            sdkSettings.modules.mapNotNull { (moduleId, moduleSettings) ->
                val rules = moduleSettings.rules ?: return@mapNotNull null
                moduleId to rules
            }.toMap()
        )
    }

    private fun initializeRules(
        rules: Map<String, LoadRule>,
        moduleRules: Map<String, Rule<String>>
    ) {
        loadRules = moduleRules.mapValues { (moduleId, rule) ->
            rule.asMatchable { ruleId ->
                // If an Id is set but rule is not found, then the rule was not defined or the format
                // was incorrect. Assume a misconfiguration and return a throwing Matchable.
                rules[ruleId]?.conditions?.asMatchable()
                    ?: Matchable { throw RuleNotFoundException(ruleId, moduleId) }
            }
        }
    }

    override fun evaluateLoadRules(
        dispatcher: Dispatcher,
        dispatches: List<Dispatch>
    ): DispatchSplit {
        val rule = loadRules[dispatcher.id]
            ?: return DispatchSplit(dispatches, emptyList())

        return dispatches.partition { dispatch ->
            try {
                rule.matches(dispatch.payload())
            } catch (ex: InvalidMatchException) {
                logger.logIfWarnEnabled(LogCategory.LOAD_RULES) {
                    "LoadRule evaluation failed for Dispatch(${dispatch.logDescription()}) and Dispatcher(${dispatcher.id}). Cause: ${ex.message}"
                }
                false
            }
        }
    }

    override fun rulesAllow(collector: Collector, dispatch: Dispatch): Boolean {
        val rule = loadRules[collector.id]
            ?: return true // no rule set, safe to execute

        return try {
            rule.matches(dispatch.payload())
        } catch (ex: InvalidMatchException) {
            logger.logIfWarnEnabled(LogCategory.LOAD_RULES) {
                "LoadRule evaluation failed for Dispatch(${dispatch.logDescription()}) and Collector(${collector.id}). Cause: ${ex.message}"
            }
            false
        }
    }
}