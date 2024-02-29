package com.tealium.core.internal

import com.tealium.core.BuildConfig
import com.tealium.core.TealiumContext
import com.tealium.core.api.DataLayer
import com.tealium.core.api.Module
import com.tealium.core.api.ModuleFactory
import com.tealium.core.api.ModuleManager
import com.tealium.core.api.settings.ModuleSettings
import com.tealium.core.api.Subscribable
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.data.TealiumList
import com.tealium.core.api.data.TealiumValue
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference

class DataLayerWrapper(
    private val moduleManager: WeakReference<ModuleManager>
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
    context: TealiumContext
) : DataLayer, Module {
    private val data = mutableMapOf<String, Any>()

    override val onDataUpdated: Subscribable<DataLayer.DataLayerUpdatedListener>
        get() = TODO () //_onDataUpdated
    override val onDataRemoved: Subscribable<DataLayer.DataLayerRemovedListener>
        get() =  TODO () //_onDataRemoved

    override fun put(key: String, value: TealiumValue) {

    }

    override fun put(key: String, value: String) {
        data[key] = value
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

        }
    }

    override val name: String
        get() = moduleName
    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION


    companion object : ModuleFactory {
        private const val moduleName = "DataLayer"

        override val name: String
            get() = moduleName

        override fun create(context: TealiumContext, settings: ModuleSettings): Module? {
            return DataLayerImpl(context)
        }
    }
}
