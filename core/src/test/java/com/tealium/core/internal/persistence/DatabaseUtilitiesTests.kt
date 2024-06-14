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


    @Test
    fun tail_Returns_Last_X_Entries() {
        val list = listOf(1, 2, 3)
        val tail = list.tail(2)

        assertEquals(2, tail.size)
        assertEquals(2, tail[0])
        assertEquals(3, tail[1])
    }

    @Test
    fun tail_Returns_Last_Entry() {
        val list = listOf(1, 2, 3)
        val tail = list.tail(1)

        assertEquals(1, tail.size)
        assertEquals(3, tail[0])
    }

    @Test
    fun tail_Returns_All_Entries_When_Count_Bigger_Than_Size() {
        val list = listOf(1, 2, 3)
        val tail = list.tail(10)

        assertEquals(3, tail.size)
        assertEquals(1, tail[0])
        assertEquals(2, tail[1])
        assertEquals(3, tail[2])
    }

    @Test
    fun tail_Returns_All_Entries_When_Count_Is_Negative() {
        val list = listOf(1, 2, 3)
        val tail = list.tail(-5)

        assertEquals(3, tail.size)
        assertEquals(1, tail[0])
        assertEquals(2, tail[1])
        assertEquals(3, tail[2])
    }

    @Test
    fun tail_Returns_No_Entries_When_Count_Is_Zero() {
        val list = listOf(1, 2, 3)
        val tail = list.tail(0)

        assertEquals(0, tail.size)
    }
}