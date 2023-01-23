package com.tealium.core.internal

import com.tealium.core.api.DataLayer
import com.tealium.core.api.Module
import com.tealium.core.api.TimedEventsManager
import com.tealium.core.api.data.bundle.TealiumBundle
import com.tealium.core.api.data.bundle.TealiumList
import com.tealium.core.api.data.bundle.TealiumValue
import com.tealium.core.internal.modules.ModuleManagerImpl
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference

class DataLayerWrapper(
    private val moduleManager: WeakReference<ModuleManagerImpl>
): DataLayer {
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
}

class DataLayerImpl: DataLayer, Module {
    override fun put(key: String, value: TealiumValue) {
        TODO("Not yet implemented")
    }

    override fun put(key: String, value: String) {
        TODO("Not yet implemented")
    }

    override fun put(key: String, value: Int) {
        TODO("Not yet implemented")
    }

    override fun put(key: String, value: Float) {
        TODO("Not yet implemented")
    }

    override fun put(key: String, value: Long) {
        TODO("Not yet implemented")
    }

    override fun put(key: String, value: Double) {
        TODO("Not yet implemented")
    }

    override fun put(key: String, value: Boolean) {
        TODO("Not yet implemented")
    }

    override fun put(key: String, value: TealiumBundle) {
        TODO("Not yet implemented")
    }

    override fun put(key: String, value: TealiumList) {
        TODO("Not yet implemented")
    }

    override fun put(key: String, value: JSONObject) {
        TODO("Not yet implemented")
    }

    override fun put(key: String, value: JSONArray) {
        TODO("Not yet implemented")
    }

    override fun put(bundle: TealiumBundle) {
        // do something
    }

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

    override val name: String
        get() = "DataLayer"
    override val version: String
        get() = "" //TODO("Not yet implemented")
}
