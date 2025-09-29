package com.tealium.core.internal.modules.time

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.DispatchContext
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class TimeDataModuleTests {

    private val firstOfJanuary2000 = 946684800000L
    private lateinit var timeDataModule: TimeDataModule

    @Before
    fun setUp() {
        val timeDataSupplier = TimeDataSupplier.getInstance()
        timeDataModule = TimeDataModule(timeDataSupplier)
    }

    @Test
    fun collect_Returns_Empty_Object_When_Timestamp_Epoch_Milliseconds_Missing_From_Context() {
        val dispatchContext = DispatchContext(
            DispatchContext.Source.application(),
            DataObject.EMPTY_OBJECT
        )
        val collected = timeDataModule.collect(dispatchContext)

        assertEquals(DataObject.EMPTY_OBJECT, collected)
    }

    @Test
    fun collect_Delegates_Date_Formats_To_TimeDateSupplier() {
        val timeDataSupplier = mockk<TimeDataSupplier>()
        every { timeDataSupplier.getTimeData(any()) } returns DataObject.create {
            put(Dispatch.Keys.TEALIUM_TIMESTAMP_UTC, "iso-utc")
            put(Dispatch.Keys.TEALIUM_TIMESTAMP_LOCAL, "iso-local")
            put(Dispatch.Keys.TEALIUM_TIMESTAMP_LOCAL_WITH_OFFSET, "iso-local-offset")
        }

        val collected = TimeDataModule(timeDataSupplier)
            .collect(dispatchContext())

        assertEquals("iso-utc", collected.getString(Dispatch.Keys.TEALIUM_TIMESTAMP_UTC))
        assertEquals("iso-local", collected.getString(Dispatch.Keys.TEALIUM_TIMESTAMP_LOCAL))
        assertEquals("iso-local-offset", collected.getString(Dispatch.Keys.TEALIUM_TIMESTAMP_LOCAL_WITH_OFFSET))
    }

    @Test
    fun collect_Returns_Correctly_Formatted_Iso8601_UTC_Time() {
        val collected = timeDataModule.collect(dispatchContext())

        assertEquals("2000-01-01T00:00:00Z", collected.getString(Dispatch.Keys.TEALIUM_TIMESTAMP_UTC))
    }

    @Test
    fun collect_Returns_Correctly_Formatted_Iso8601_Local_Time() {
        val losAngeles = TimeZone.getTimeZone("America/Los_Angeles")
        TimeZone.setDefault(losAngeles) // -08:00
        val losAngelesTime = timeDataModule.collect(dispatchContext())

        val katmandu = TimeZone.getTimeZone("Asia/Katmandu")
        TimeZone.setDefault(katmandu) // +05:45
        val katmanduTime = timeDataModule.collect(dispatchContext())

        assertEquals("1999-12-31T16:00:00", losAngelesTime.getString(Dispatch.Keys.TEALIUM_TIMESTAMP_LOCAL))
        assertEquals("2000-01-01T05:45:00", katmanduTime.getString(Dispatch.Keys.TEALIUM_TIMESTAMP_LOCAL))
    }

    @Test
    fun collect_Returns_Correctly_Formatted_Iso8601_Local_Time_With_Offset() {
        val losAngeles = TimeZone.getTimeZone("America/Los_Angeles")
        TimeZone.setDefault(losAngeles) // -08:00
        val losAngelesTime = timeDataModule.collect(dispatchContext())

        val katmandu = TimeZone.getTimeZone("Asia/Katmandu")
        TimeZone.setDefault(katmandu) // +05:45
        val katmanduTime = timeDataModule.collect(dispatchContext())

        assertEquals("1999-12-31T16:00:00-08:00", losAngelesTime.getString(Dispatch.Keys.TEALIUM_TIMESTAMP_LOCAL_WITH_OFFSET))
        assertEquals("2000-01-01T05:45:00+05:45", katmanduTime.getString(Dispatch.Keys.TEALIUM_TIMESTAMP_LOCAL_WITH_OFFSET))
    }

    @Test
    fun collect_Returns_Correct_TimeZone_Offset_In_Decimal_Hours() {
        val losAngeles = TimeZone.getTimeZone("America/Los_Angeles")
        TimeZone.setDefault(losAngeles) // -08:00
        val losAngelesTime = timeDataModule.collect(dispatchContext())

        val katmandu = TimeZone.getTimeZone("Asia/Katmandu")
        TimeZone.setDefault(katmandu) // +05:45
        val katmanduTime = timeDataModule.collect(dispatchContext())

        assertEquals(-8.0, losAngelesTime.getDouble(Dispatch.Keys.TEALIUM_TIMESTAMP_OFFSET))
        assertEquals(5.75, katmanduTime.getDouble(Dispatch.Keys.TEALIUM_TIMESTAMP_OFFSET))
    }

    @Test
    fun collect_Returns_Current_TimeZone_Name() {
        val losAngeles = TimeZone.getTimeZone("America/Los_Angeles")
        TimeZone.setDefault(losAngeles) // -08:00
        val losAngelesTime = timeDataModule.collect(dispatchContext())

        val katmandu = TimeZone.getTimeZone("Asia/Katmandu")
        TimeZone.setDefault(katmandu) // +05:45
        val katmanduTime = timeDataModule.collect(dispatchContext())

        assertEquals("America/Los_Angeles", losAngelesTime.getString(Dispatch.Keys.TEALIUM_TIMESTAMP_TIMEZONE))
        assertEquals("Asia/Katmandu", katmanduTime.getString(Dispatch.Keys.TEALIUM_TIMESTAMP_TIMEZONE))
    }

    @Test
    fun collect_Returns_Correct_Timestamp_Epoch_Milliseconds() {
        val collected = timeDataModule.collect(dispatchContext())

        assertEquals(firstOfJanuary2000, collected.getLong(Dispatch.Keys.TEALIUM_TIMESTAMP_EPOCH_MILLISECONDS))
    }

    @Test
    fun collect_Returns_Correct_Timestamp_Epoch_Seconds() {
        val collected = timeDataModule.collect(dispatchContext())

        assertEquals(946684800L, collected.getLong(Dispatch.Keys.TEALIUM_TIMESTAMP_EPOCH))
    }

    @Test
    fun factory_ModuleType_Matches_Module_Id() {
        val timeDataModule = TimeDataModule.Factory.create(TimeDataModule.Factory.moduleType, mockk(), mockk())!!

        assertEquals(TimeDataModule.Factory.moduleType, timeDataModule.id)
    }

    @Test
    fun factory_Creates_New_Instances() {
        val instance1 = TimeDataModule.Factory.create("1", mockk(), mockk())
        val instance2 = TimeDataModule.Factory.create("1", mockk(), mockk())

        assertNotSame(instance1, instance2)
    }

    private fun dispatchContext(timestamp: Long = firstOfJanuary2000): DispatchContext =
        DispatchContext(DispatchContext.Source.application(), DataObject.create {
            put(Dispatch.Keys.TEALIUM_TIMESTAMP_EPOCH_MILLISECONDS, timestamp)
        })
}