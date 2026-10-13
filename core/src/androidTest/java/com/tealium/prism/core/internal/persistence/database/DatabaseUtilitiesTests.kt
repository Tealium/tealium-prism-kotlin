package com.tealium.prism.core.internal.persistence.database

import android.database.sqlite.SQLiteDatabase
import android.os.Build
import androidx.test.filters.SdkSuppress
import com.tealium.prism.core.api.persistence.PersistenceException
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class DatabaseUtilitiesTests {

    @RelaxedMockK
    private lateinit var mockDb: SQLiteDatabase

    private val opened = DatabaseTestUtils.OpenedDatabases()

    /** A real in-memory database, for the behaviour that the mock's call contract cannot show. */
    private lateinit var db: SQLiteDatabase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        db = opened.track(SQLiteDatabase.create(null))
        db.execSQL("CREATE TABLE $TABLE (value TEXT);")
    }

    @After
    fun tearDown() {
        opened.closeAll()
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.P)
    fun transaction_MarksTransactionSuccessful_AndEndsTransaction() {
        mockDb.transaction { }

        verify {
            mockDb.beginTransactionNonExclusive()
            mockDb.setTransactionSuccessful()
            mockDb.endTransaction()
        }
    }

    @Test
    fun transaction_CommitsTheBlock_And_EndsTheTransaction_When_BlockSucceeds() {
        db.transaction {
            execSQL("INSERT INTO $TABLE VALUES ('committed');")
        }

        assertFalse(db.inTransaction())
        assertEquals(listOf("committed"), values())
    }

    @Test
    fun transaction_RollsBackTheBlock_And_EndsTheTransaction_When_BlockThrows() {
        assertThrows(PersistenceException::class.java) {
            db.transaction {
                execSQL("INSERT INTO $TABLE VALUES ('rolled back');")
                throw IllegalStateException("block failed")
            }
        }

        assertFalse(db.inTransaction())
        assertEquals(emptyList<String>(), values())
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

    private fun values(): List<String> =
        db.rawQuery("SELECT value FROM $TABLE", null).use { cursor ->
            generateSequence { if (cursor.moveToNext()) cursor.getString(0) else null }.toList()
        }
}

private const val TABLE = "transaction_test"
