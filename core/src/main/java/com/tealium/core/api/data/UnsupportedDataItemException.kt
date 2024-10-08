package com.tealium.core.api.data

import com.tealium.core.api.misc.TealiumException

/**
 * This [Exception] is thrown in cases where a conversion from an value to a [DataItem] has been
 * attempted but has failed to do so.
 *
 * This may happen in cases where the value was not a supported type; i.e. it is not a supported
 * primitive value, and it does not implement [DataItemConvertible].
 *
 * There are other edge cases that may also throw, for instance, [Double.NaN] or Infinity values may
 * throw depending on the method used to convert them.
 *
 * @param message Explanation of why the conversion failed.
 * @param cause An exception thrown during conversion, if any
 */
class UnsupportedDataItemException(message: String? = null, cause: Throwable? = null) :
    TealiumException(message, cause)