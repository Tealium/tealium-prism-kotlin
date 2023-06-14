package com.tealium.core.internal.persistence

import com.tealium.core.api.data.bundle.TealiumBundle
import com.tealium.core.api.data.bundle.TealiumList

enum class Serialization(val code: Int, val clazz: Class<*>) {
    STRING(0, String::class.java),
    INT(1, Int::class.java),
    DOUBLE(2, Double::class.java),
    LONG(3, Long::class.java),
    BOOLEAN(4, Boolean::class.java),
    STRING_ARRAY(5, TealiumList::class.java),
    INT_ARRAY(6, TealiumList::class.java),
    DOUBLE_ARRAY(7, TealiumList::class.java),
    LONG_ARRAY(8, TealiumList::class.java),
    BOOLEAN_ARRAY(9, TealiumList::class.java),
    JSON_OBJECT(10, TealiumBundle::class.java),
    JSON_ARRAY(11, TealiumList::class.java),
    TEALIUM_LIST(12, TealiumList::class.java),
    TEALIUM_BUNDLE(13, TealiumBundle::class.java);
}