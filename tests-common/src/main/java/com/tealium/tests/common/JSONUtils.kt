package com.tealium.tests.common

/**
 * Utility method to remove all excess whitespace from a JSON String. Allows test JSON
 * to be formatted for readability in tests.
 *
 * Removals inclusive of:
 *  - New lines
 *  - whitespace at beginning and end of lines
 *
 *  Whitespace inside of values will remain unaffected
 */
fun String.trimJson(): String {
    return this.replace(Regex("\\s+(?=(?:(?:[^\"]*\"){2})*[^\"]*+\$)"), "")
}