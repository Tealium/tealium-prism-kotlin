package com.tealium.core.api

interface ModuleManager {

    fun <T> getModulesOfType(clazz: Class<T>): List<T>

    fun <T> getModuleOfType(clazz: Class<T>): T?
}