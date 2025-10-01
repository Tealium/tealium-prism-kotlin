package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.barriers.Barrier
import com.tealium.prism.core.api.barriers.BarrierFactory
import com.tealium.prism.core.api.barriers.BarrierRegistry
import com.tealium.prism.core.api.barriers.BarrierScope
import com.tealium.prism.core.api.barriers.ConfigurableBarrier
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.ObservableState
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.internal.network.ConnectivityBarrier
import com.tealium.prism.core.internal.settings.BarrierSettings
import com.tealium.prism.core.internal.settings.SdkSettings

/**
 * The [BarrierManager] is responsible for initializing [ConfigurableBarrier]'s from their
 * [BarrierFactory] counterpart, as well as updating their settings.
 *
 * It also provides [BarrierRegistry] functionality for any [Barrier] implementations. Note. these
 * will not received any settings updates.
 */
class BarrierManager(
    private val sdkBarrierSettings: ObservableState<Map<String, BarrierSettings>>,
    private val configBarriers: StateSubject<List<ConfigurableBarrier>> = Observables.stateSubject(emptyList()),
): BarrierRegistry {
    private val extraBarriers: StateSubject<List<ScopedBarrier>> =
        Observables.stateSubject(emptyList())
    private var defaultBarrierScopes: Map<String, Set<BarrierScope>> = emptyMap()

    constructor(settings: ObservableState<SdkSettings>) : this(
        sdkBarrierSettings = settings.map(SdkSettings::barriers)
            .withState { settings.value.barriers }
    )

    init {
        this.sdkBarrierSettings.subscribe(::updateSettings)
    }

    val barriers: Observable<List<ScopedBarrier>>
        get() = configBarriers.combine(sdkBarrierSettings) { barriers, settings ->
            combineBarriersAndSettings(barriers, settings, defaultBarrierScopes)
        }.combine(extraBarriers) { configBarriers, extraBarriers ->
            configBarriers + extraBarriers
        }

    fun initializeBarriers(
        factories: List<BarrierFactory>,
        context: TealiumContext,
        defaultBarrierFactories: List<BarrierFactory> = getDefaultBarrierFactories()
    ) {
        val missingDefaults =
            defaultBarrierFactories.filter { default -> factories.find { it.id == default.id } == null }
        val toCreate = factories + missingDefaults

        val barriers = toCreate.map { factory ->
            createBarrier(factory, context)
        }.distinctBy { it.id }
        defaultBarrierScopes = toCreate.associate { it.id to it.defaultScope() }

        configBarriers.onNext(barriers)
    }

    private fun createBarrier(factory: BarrierFactory, context: TealiumContext): ConfigurableBarrier {
        val barrierSettings = sdkBarrierSettings.value[factory.id]
        return factory.create(context, barrierSettings?.configuration ?: DataObject.EMPTY_OBJECT)
    }

    private fun updateSettings(settings: Map<String, BarrierSettings>) {
        configBarriers.value.forEach { barrier ->
            val barrierSettings = settings[barrier.id]
            barrier.updateConfiguration(barrierSettings?.configuration ?: DataObject.EMPTY_OBJECT)
        }
    }

    fun shutdown() {
        configBarriers.onNext(emptyList())
        extraBarriers.onNext(emptyList())
    }

    override fun registerScopedBarrier(barrier: Barrier, scopes: Set<BarrierScope>) {
        val toAdd = barrier to scopes
        val barriers = extraBarriers.value.toMutableList()

        val index = barriers.indexOfFirst { (b, _) -> barrier == b }
        if (index >= 0) {
            barriers[index] = toAdd
        } else {
            barriers.add(toAdd)
        }

        extraBarriers.onNext(barriers)
    }

    override fun unregisterScopedBarrier(barrier: Barrier) {
        val newBarriers = extraBarriers.value.filter { (existing, _) -> barrier != existing }
        extraBarriers.onNext(newBarriers)
    }

    companion object {
        private fun getDefaultBarrierFactories(): List<BarrierFactory> =
            listOf(ConnectivityBarrier.Factory())

        fun combineBarriersAndSettings(
            barriers: List<ConfigurableBarrier>,
            settings: Map<String, BarrierSettings>,
            defaultBarrierScopes: Map<String, Set<BarrierScope>>
        ): List<ScopedBarrier> {
            return barriers.map { barrier ->
                barrier to (settings[barrier.id]?.scope
                    ?: defaultBarrierScopes[barrier.id]
                    ?: setOf(BarrierScope.All))
            }
        }
    }
}