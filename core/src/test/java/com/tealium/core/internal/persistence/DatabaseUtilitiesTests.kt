package com.tealium.core.internal.persistence

import android.database.sqlite.SQLiteDatabase
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DatabaseUtilitiesTests {

    @RelaxedMockK
    private lateinit var mockDb: SQLiteDatabase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
    }

    @Test
    fun transaction_MarksTransactionSuccessful_AndEndsTransaction() {
        mockDb.transaction { }

        verify {
            mockDb.beginTransactionNonExclusive()
            mockDb.setTransactionSuccessful()
            mockDb.endTransaction()
        }
    }

    @Test
    fun transaction_NotifiesErrorHandler_AndEndsTransaction() {
        val errorHandler = mockk<(Exception) -> Unit>(relaxed = true)
        val error = Exception()
        mockDb.transaction(errorHandler) {
            throw error
        }

        verify {
            mockDb.beginTransactionNonExclusive()
            errorHandler.invoke(error)
            mockDb.endTransaction()
        }

        verify(exactly = 0) {
            mockDb.setTransactionSuccessful()
        }
    }

    @Test
    fun dropTable_DropsNamedTable() {
        mockDb.dropTable("")
        mockDb.dropTable("   ")

        verify(exactly = 0) {
            mockDb.execSQL(any())
        }
    }

    @Test
    fun dropTable_DoesNothing_WhenTableNameIsEmpty() {
        mockDb.dropTable("test_table")

        verify {
            mockDb.execSQL("DROP TABLE test_table")
        }
    }

    @Test
    fun dropTableIfExists_DoesNothing_WhenTableNameIsEmpty() {
        mockDb.dropTableIfExists("")
        mockDb.dropTableIfExists("   ")

        verify(exactly = 0) {
            mockDb.execSQL(any())
        }
    }

    @Test
    fun placeholderList_GeneratesPlaceholders_ForAllEntriesInList() {
        val listOf1 = listOf(1).placeholderList()
        val listOf5 = listOf(1, 2, 3, 4, 5).placeholderList()

        assertEquals("(?)", listOf1)
        assertEquals("(?,?,?,?,?)", listOf5)
    }
}