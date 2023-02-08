package com.tealium.core.internal

import com.tealium.core.TealiumContext
import com.tealium.core.api.DataLayer
import com.tealium.core.api.Module
import com.tealium.core.api.ModuleFactory
import com.tealium.core.api.ModuleSettings
import com.tealium.core.api.Subscribable
import com.tealium.core.api.data.MutableObservableProperty
import com.tealium.core.api.data.bundle.TealiumBundle
import com.tealium.core.api.data.bundle.TealiumList
import com.tealium.core.api.data.bundle.TealiumValue
import com.tealium.core.internal.modules.ModuleManagerImpl
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference

class DataLayerWrapper(
    private val moduleManager: WeakReference<ModuleManagerImpl>
) : DataLayer {
    private val delegate: DataLayer?
        get() = moduleManager.get()?.getModuleOfType(DataLayer::class.java)

    override fun put(key: String, value: TealiumValue) {
        delegate?.put(key, value)
    }

    override fun put(key: String, value: String) {
        delegate?.put(key, value)
    }

    override fun put(key: String, value: Int) {
        delegate?.put(key, value)
    }

    override fun put(key: String, value: Float) {
        delegate?.put(key, value)
    }

    override fun put(key: String, value: Long) {
        delegate?.put(key, value)
    }

    override fun put(key: String, value: Double) {
        delegate?.put(key, value)
    }

    override fun put(key: String, value: Boolean) {
        delegate?.put(key, value)
    }

    override fun put(key: String, value: TealiumBundle) {
        delegate?.put(key, value)
    }

    override fun put(key: String, value: TealiumList) {
        delegate?.put(key, value)
    }

    override fun put(key: String, value: JSONObject) {
        delegate?.put(key, value)
    }

    override fun put(key: String, value: JSONArray) {
        delegate?.put(key, value)
    }

    override fun put(bundle: TealiumBundle) {
        delegate?.put(bundle)
    }

    override fun put(block: TealiumBundle.Builder.() -> Unit) {
        delegate?.put(block)
    }

    override fun put(key: String, value: Array<String>) {
        delegate?.put(key, value)
    }

    override fun put(key: String, value: Array<Float>) {
        delegate?.put(key, value)
    }

    override fun put(key: String, value: Array<Long>) {
        delegate?.put(key, value)
    }

    override fun put(key: String, value: Array<Double>) {
        delegate?.put(key, value)
    }

    override fun put(key: String, value: Array<Int>) {
        delegate?.put(key, value)
    }

    override fun put(key: String, value: Array<Boolean>) {
        delegate?.put(key, value)
    }

    override fun remove(key: String) {
        delegate?.remove(key)
    }

    override val onDataUpdated: Subscribable<DataLayer.DataLayerUpdatedListener>
        get() = delegate?.onDataUpdated ?: throw Exception()
    override val onDataRemoved: Subscribable<DataLayer.DataLayerRemovedListener>
        get() = delegate?.onDataRemoved ?: throw Exception()
}

class DataLayerImpl(
    private val _onDataUpdated: MutableObservableProperty<Pair<String, Any>, DataLayer.DataLayerUpdatedListener>,
    private val _onDataRemoved: MutableObservableProperty<Set<String>, DataLayer.DataLayerRemovedListener>
) : DataLayer, Module {
    //    private val eventRouter: EventRouter<DataLayer.DataLayerListener> = EventDispatcher()
    private val data = mutableMapOf<String, Any>()

    constructor(context: TealiumContext) : this(
        _onDataUpdated = context.observables.createProperty(
            initial = ("" to Any()), // TODO - make initial value nullable?
            deliver = { observer, value ->
                observer.onDataUpdated(value.first, value.second)
            }
        ),
        _onDataRemoved = context.observables.createProperty(
            initial = setOf(), // TODO - make initial value nullable?
            deliver = { observer, value ->
                observer.onDataRemoved(value)
            }
        )
    )

//    override val updated: Subscribable<DataLayer.DataLayerUpdatedListener>
//        get() = eventRouter
//    override val removed: Subscribable<DataLayer.DataLayerRemovedListener>
//        get() = eventRouter

    override val onDataUpdated: Subscribable<DataLayer.DataLayerUpdatedListener>
        get() = _onDataUpdated
    override val onDataRemoved: Subscribable<DataLayer.DataLayerRemovedListener>
        get() = _onDataRemoved

    override fun put(key: String, value: TealiumValue) {

    }

    override fun put(key: String, value: String) {
        data[key] = value
        _onDataUpdated.update(key to value)
    }

    override fun put(key: String, value: Int) {

    }

    override fun put(key: String, value: Float) {

    }

    override fun put(key: String, value: Long) {

    }

    override fun put(key: String, value: Double) {

    }

    override fun put(key: String, value: Boolean) {

    }

    override fun put(key: String, value: TealiumBundle) {

    }

    override fun put(key: String, value: TealiumList) {

    }

    override fun put(key: String, value: JSONObject) {

    }

    override fun put(key: String, value: JSONArray) {

    }

    override fun put(bundle: TealiumBundle) {
        // do something
    }

    // TODO - could separate to a `-ktx` module
    override fun put(block: TealiumBundle.Builder.() -> Unit) {
        val builder = TealiumBundle.Builder()
        block.invoke(builder)
        put(builder.getBundle())
    }

    override fun put(key: String, value: Array<String>) {
        TODO("Not yet implemented")
    }

    override fun put(key: String, value: Array<Float>) {
        TODO("Not yet implemented")
    }

    override fun put(key: String, value: Array<Long>) {
        TODO("Not yet implemented")
    }

    override fun put(key: String, value: Array<Double>) {
        TODO("Not yet implemented")
    }

    override fun put(key: String, value: Array<Int>) {
        TODO("Not yet implemented")
    }

    override fun put(key: String, value: Array<Boolean>) {
        TODO("Not yet implemented")
    }

    override fun remove(key: String) {
        val removed = data.remove(key)

        if (removed != null) {
            _onDataRemoved.update(setOf(key))
        }
    }

    override val name: String
        get() = moduleName
    override val version: String
        get() = "" //TODO("Not yet implemented")


    companion object : ModuleFactory {
        private const val moduleName = "DataLayer"

        override val name: String
            get() = moduleName

        override fun create(context: TealiumContext, settings: ModuleSettings): Module {
            return DataLayerImpl(context)
        }
    }

//    private class DataLayerUpdatedMessenger(
//        private val key: String,
//        private val value: Any
//    ) : Messenger<DataLayer.DataLayerUpdatedListener> {
//        override val listenerClass: KClass<DataLayer.DataLayerUpdatedListener>
//            get() = DataLayer.DataLayerUpdatedListener::class
//
//        override fun deliver(listener: DataLayer.DataLayerUpdatedListener) {
//            listener.onDataUpdated(key, value)
//        }
//    }
//
//    private class DataLayerRemovedMessenger(private val keys: Set<String>) :
//        Messenger<DataLayer.DataLayerRemovedListener> {
//        override val listenerClass: KClass<DataLayer.DataLayerRemovedListener>
//            get() = DataLayer.DataLayerRemovedListener::class
//
//        override fun deliver(listener: DataLayer.DataLayerRemovedListener) {
//            listener.onDataRemoved(keys)
//        }
//    }
}
