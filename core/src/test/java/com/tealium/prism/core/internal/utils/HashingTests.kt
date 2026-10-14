package com.tealium.prism.core.internal.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HashingTests {

    @Test
    fun sha256_Returns_KnownLowerCaseDigest_For_FixedInput() {
        val input = "Some string"
        val expected = "2beaf0548e770c4c392196e0ec8e7d6d81cc9280ac9c7f3323e4c6abc231e95a"
        val result = input.sha256()
        assertEquals(expected, result)
    }

    @Test
    fun sha256_Returns_LowerCaseHex_Digest() {
        val testCases = listOf(
            "",
            "newIdentity",
            "🎯 Unicode test"
        )
        val hexRegex = Regex("^[0-9a-f]{64}$")
        testCases.forEach { input ->
            val result = input.sha256()
            assertTrue("sha256('$input') = '$result' does not match pattern", hexRegex.matches(result))
        }
    }

    @Test
    fun sha256_Matches_SwiftReferenceDigest_For_NewIdentity() {
        val input = "newIdentity"
        val expected = "2d66ba8ba26bf2ee9c7b40d5225e73c1db3a56f158cc9f669fbe340590af62db"
        val result = input.sha256()
        assertEquals(expected, result)
    }
}
