package com.tealium.prism.core.internal.rules

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.modules.Collector
import com.tealium.prism.core.api.modules.Dispatcher
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.api.rules.Condition
import com.tealium.prism.core.api.rules.Rule
import com.tealium.prism.core.api.rules.Rule.Companion.just
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.DispatchContext
import com.tealium.prism.core.api.tracking.DispatchType
import com.tealium.prism.core.internal.dispatch.DispatchSplit
import com.tealium.prism.core.internal.dispatch.successful
import com.tealium.prism.core.internal.dispatch.unsuccessful
import com.tealium.prism.core.internal.pubsub.CompletedDisposable
import com.tealium.prism.core.internal.settings.ModuleSettings
import com.tealium.prism.core.internal.settings.SdkSettings
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.TestCollector
import com.tealium.tests.common.TestDispatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LoadRuleEngineTests {

    val rules: Map<String, LoadRule> = mapOf(
        "1" to LoadRule(
            "1", Rule.all(
                just(Condition.isEqual(false, "true", "true"))
            )
        ),
        "2" to LoadRule(
            "2", Rule.all(
                just(Condition.isEqual(false, "false", "false"))
            )
        ),
        "throwing" to LoadRule(
            "throwing", Rule.all(
                just(Condition.isGreaterThan(false, "number", "not-a-number"))
            )
        )
    )

    val settings = SdkSettings(
        loadRules = rules,
        modules = listOf(
            ModuleSettings("1",
                rules = Rule.all(
                    just("1")
                )
            ), ModuleSettings("1_and_2",
                rules = Rule.all(
                    just("1"),
                    just("2")
                )
            ), ModuleSettings( "1_or_2",
                rules = Rule.any(
                    just("1"),
                    just("2")
                )
            ), ModuleSettings("just_1",
                rules = just("1")
            ), ModuleSettings("1_and_not_2",
                rules = Rule.all(
                    just("1"), just("2").not()
                )
            ), ModuleSettings( "missing_rules",
                rules = Rule.all(
                    just("missing_rule")
                )
            ), ModuleSettings("throwing_rules",
                rules = Rule.all(
                    just("throwing")
                )
            )
        ).associateBy { it.moduleId }
    )

    private lateinit var sdkSettings: StateSubject<SdkSettings>
    private val module1 = DispatchingCollectorModule("1")
    private val module1And2 = DispatchingCollectorModule("1_and_2")
    private val module1Or2 = DispatchingCollectorModule("1_or_2")
    private val moduleJust1 = DispatchingCollectorModule("just_1")
    private val module1AndNot2 = DispatchingCollectorModule("1_and_not_2")
    private val moduleMissingRules = DispatchingCollectorModule("missing_rules")
    private val moduleWithThrowingRule = DispatchingCollectorModule("throwing_rules")
    private lateinit var engine: LoadRuleEngineImpl

    @Before
    fun setUp() {
        sdkSettings = Observables.stateSubject(settings)
        engine = LoadRuleEngineImpl(sdkSettings, SystemLogger)
    }

    @Test
    fun rulesAllow_Returns_True_When_No_Rules_Found_For_Collector() {
        val dispatch = createDispatch(DataObject.create { put("true", false) })

        assertTrue(engine.rulesAllow(TestCollector("no_load_rule"), dispatch))
    }

    @Test
    fun evaluateLoadRules_Returns_Passed_When_No_Load_Rules_Found_For_Module() {
        val dispatch = createDispatch(DataObject.create { put("true", true) })

        val result = engine.evaluateLoadRules(TestDispatcher("no_load_rule"), dispatch)
        assertTrue(result.successful.contains(dispatch))
    }

    @Test
    fun rulesAllow_Returns_True_When_Simple_Rule_Matches() {
        val dispatch = createDispatch(DataObject.create { put("true", true) })

        assertTrue(engine.rulesAllow(module1, dispatch))
    }

    @Test
    fun evaluateLoadRules_Returns_Passed_When_Simple_Rule_Matches() {
        val dispatch = createDispatch(DataObject.create { put("true", true) })

        val result = engine.evaluateLoadRules(module1, dispatch)
        assertTrue(result.successful.contains(dispatch))
        assertTrue(result.unsuccessful.isEmpty())
    }

    @Test
    fun rulesAllow_Returns_False_When_Simple_Rule_Does_Not_Match() {
        val dispatch = createDispatch(DataObject.create { put("true", false) })

        assertFalse(engine.rulesAllow(module1, dispatch))
    }

    @Test
    fun evaluateLoadRules_Returns_Failed_When_Simple_Rule_Does_Not_Match() {
        val dispatch = createDispatch(DataObject.create { put("true", false) })

        val result = engine.evaluateLoadRules(module1, dispatch)
        assertTrue(result.successful.isEmpty())
        assertTrue(result.unsuccessful.contains(dispatch))
    }

    @Test
    fun rulesAllow_Returns_True_When_All_Rules_Match() {
        val dispatch = createDispatch(DataObject.create {
            put("true", true)
            put("false", false)
        })

        assertTrue(engine.rulesAllow(module1And2, dispatch))
    }

    @Test
    fun evaluateLoadRules_Returns_Passed_When_All_Rules_Match() {
        val dispatch = createDispatch(DataObject.create {
            put("true", true)
            put("false", false)
        })

        val result = engine.evaluateLoadRules(module1And2, dispatch)
        assertTrue(result.successful.contains(dispatch))
        assertTrue(result.unsuccessful.isEmpty())
    }

    @Test
    fun rulesAllow_Returns_False_When_Any_Rule_Does_Not_Match() {
        val dispatch1 = createDispatch(DataObject.create {
            put("true", false)
            put("false", false)
        })
        val dispatch2 = createDispatch(DataObject.create {
            put("true", true)
            put("false", true)
        })

        assertFalse(engine.rulesAllow(module1And2, dispatch1))
        assertFalse(engine.rulesAllow(module1And2, dispatch2))
    }

    @Test
    fun evaluateLoadRules_Returns_Failed_When_Any_Rule_Does_Not_Match() {
        val dispatches = listOf(createDispatch(DataObject.create {
            put("true", false)
            put("false", false)
        }), createDispatch(DataObject.create {
            put("true", true)
            put("false", true)
        }))

        val result = engine.evaluateLoadRules(module1And2, dispatches)

        assertTrue(result.unsuccessful.containsAll(dispatches))
    }

    @Test
    fun rulesAllow_Returns_True_When_Any_Rule_Matches() {
        val dispatch1 = createDispatch(DataObject.create {
            put("true", true)
            put("false", true)
        })
        val dispatch2 = createDispatch(DataObject.create {
            put("true", false)
            put("false", false)
        })

        assertTrue(engine.rulesAllow(module1Or2, dispatch1))
        assertTrue(engine.rulesAllow(module1Or2, dispatch2))
    }

    @Test
    fun evaluateLoadRules_Returns_Passed_When_Any_Rules_Match() {
        val dispatches = listOf(createDispatch(DataObject.create {
            put("true", true)
            put("false", true)
        }), createDispatch(DataObject.create {
            put("true", false)
            put("false", false)
        }))

        val result = engine.evaluateLoadRules(module1Or2, dispatches)

        assertTrue(result.successful.containsAll(dispatches))
    }

    @Test
    fun rulesAllow_Returns_False_When_Both_Rules_Do_Not_Match() {
        val dispatch = createDispatch(DataObject.create {
            put("true", false)
            put("false", true)
        })

        assertFalse(engine.rulesAllow(module1Or2, dispatch))
    }

    @Test
    fun evaluateLoadRules_Returns_Failed_When_Both_Rules_Do_Not_Match() {
        val dispatch = createDispatch(DataObject.create {
            put("true", false)
            put("false", true)
        })

        val result = engine.evaluateLoadRules(module1Or2, dispatch)
        assertTrue(result.unsuccessful.contains(dispatch))
    }

    @Test
    fun rulesAllow_Returns_True_When_Rule_Is_Just_An_Id_And_Matches() {
        val dispatch = createDispatch(DataObject.create {
            put("true", true)
        })

        assertTrue(engine.rulesAllow(moduleJust1, dispatch))
    }

    @Test
    fun evaluateLoadRules_Returns_Passed_When_Rule_Is_Just_An_Id_And_Matches() {
        val dispatch = createDispatch(DataObject.create {
            put("true", true)
        })

        val result = engine.evaluateLoadRules(moduleJust1, dispatch)
        assertTrue(result.successful.contains(dispatch))
    }

    @Test
    fun rulesAllow_Returns_False_When_Rule_Is_Just_An_Id_And_Does_Not_Match() {
        val dispatch = createDispatch(DataObject.create {
            put("true", false)
        })

        assertFalse(engine.rulesAllow(moduleJust1, dispatch))
    }

    @Test
    fun evaluateLoadRules_Returns_Failed_When_Rule_Is_Just_An_Id_And_Does_Not_Match() {
        val dispatch = createDispatch(DataObject.create {
            put("true", false)
        })

        val result = engine.evaluateLoadRules(moduleJust1, dispatch)
        assertTrue(result.unsuccessful.contains(dispatch))
    }

    @Test
    fun rulesAllow_Returns_True_When_Rule_Has_Exclusions_And_They_Do_Not_Match() {
        val dispatch = createDispatch(DataObject.create {
            put("true", true)
            put("false", true)
        })

        assertTrue(engine.rulesAllow(module1AndNot2, dispatch))
    }

    @Test
    fun evaluateLoadRules_Returns_Passed_When_Rule_Has_Exclusions_And_They_Do_Not_Match() {
        val dispatch = createDispatch(DataObject.create {
            put("true", true)
            put("false", true)
        })

        val result = engine.evaluateLoadRules(module1AndNot2, dispatch)
        assertTrue(result.successful.contains(dispatch))
    }

    @Test
    fun rulesAllow_Returns_False_When_Rule_Has_Exclusions_And_They_Match() {
        val dispatch = createDispatch(DataObject.create {
            put("true", true)
            put("false", false)
        })

        assertFalse(engine.rulesAllow(module1AndNot2, dispatch))
    }

    @Test
    fun evaluateLoadRules_Returns_Failed_When_Rule_Has_Exclusions_And_They_Match() {
        val dispatch = createDispatch(DataObject.create {
            put("true", true)
            put("false", false)
        })

        val result = engine.evaluateLoadRules(module1AndNot2, dispatch)
        assertTrue(result.unsuccessful.contains(dispatch))
    }

    @Test
    fun rulesAllow_Returns_False_When_Referenced_Load_Rule_Does_Not_Exist() {
        val dispatch = createDispatch(DataObject.create { put("true", true) })

        assertFalse(engine.rulesAllow(moduleMissingRules, dispatch))
    }

    @Test
    fun evaluateLoadRules_Returns_Failed_When_Referenced_Load_Rule_Does_Not_Exist() {
        val dispatch = createDispatch(DataObject.create { put("true", true) })

        val result = engine.evaluateLoadRules(moduleMissingRules, dispatch)
        assertTrue(result.unsuccessful.contains(dispatch))
    }

    @Test
    fun load_Rules_Get_Updated_When_New_Settings_Published() {
        sdkSettings.onNext(SdkSettings()) // no rules
        val dispatch = createDispatch(DataObject.create { put("true", false) })

        val result = engine.evaluateLoadRules(module1, dispatch)
        assertTrue(result.successful.contains(dispatch))
        assertTrue(result.unsuccessful.isEmpty())
    }

    @Test
    fun evaluateLoadRules_Separates_Passed_And_Failed_When_Mixed_Results() {
        val successful = createDispatch(DataObject.create { put("true", true) })
        val unsuccessful = createDispatch(DataObject.create { put("true", false) })

        val result = engine.evaluateLoadRules(module1, listOf(successful, unsuccessful))
        assertTrue(result.successful.contains(successful))
        assertTrue(result.unsuccessful.contains(unsuccessful))
    }

    @Test
    fun rulesAllow_Does_Not_Throw_When_Rule_Throws() {
        val withNumber = createDispatch(DataObject.create { put("number", 1) })

        val result = engine.rulesAllow(moduleWithThrowingRule, withNumber)

        assertFalse(result)
    }

    @Test
    fun evaluateLoadRules_Does_Not_Throw_When_Rule_Throws() {
        val withNumber = createDispatch(DataObject.create { put("number", 1) })
        val withInvalidNumber = createDispatch(DataObject.create { put("number", false) })

        val result = engine.evaluateLoadRules(moduleWithThrowingRule, listOf(withNumber, withInvalidNumber))

        assertTrue(result.successful.isEmpty())
        assertTrue(result.unsuccessful.containsAll(listOf(withNumber, withInvalidNumber)))
    }

    private fun createDispatch(payload: DataObject, eventName: String = "event"): Dispatch =
        Dispatch.create(eventName, DispatchType.Event, payload)

    private fun LoadRuleEngineImpl.evaluateLoadRules(
        dispatcher: Dispatcher,
        dispatch: Dispatch
    ): DispatchSplit =
        evaluateLoadRules(dispatcher, listOf(dispatch))

    private class DispatchingCollectorModule(
        override val id: String
    ) : Dispatcher, Collector {

        override fun dispatch(
            dispatches: List<Dispatch>,
            callback: Callback<List<Dispatch>>
        ): Disposable = CompletedDisposable

        override val version: String
            get() = ""

        override fun collect(dispatchContext: DispatchContext): DataObject = DataObject.EMPTY_OBJECT

    }
}