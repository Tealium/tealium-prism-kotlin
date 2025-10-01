package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.Disposable
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DisposableContainerTests {

    @RelaxedMockK
    private lateinit var toAdd: Disposable

    private lateinit var disposables: MutableList<Disposable>
    private lateinit var container: DisposableContainer

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        disposables = mutableListOf()
        container = DisposableContainer(disposables)
    }

    @Test
    fun dispose_Disposes_All_Disposables_When_Not_Disposed() {
        container.add(toAdd)
        container.add(toAdd)
        container.add(toAdd)

        container.dispose()

        verify(exactly = 3) {
            toAdd.dispose()
        }
    }

    @Test
    fun dispose_Clears_All_Disposables_When_Not_Disposed() {
        disposables.addAll(listOf(toAdd, toAdd))

        container.dispose()

        assertEquals(0, disposables.size)
    }

    @Test
    fun dispose_Called_Multiple_Times_Does_Nothing() {
        disposables.add(toAdd)

        container.dispose()
        container.dispose()
        container.dispose()

        verify(exactly = 1) { toAdd.dispose() }
    }

    @Test
    fun dispose_Does_Not_Dispose_List_When_Already_Disposed() {
        container.dispose()
        disposables.add(toAdd)

        container.dispose()

        assertEquals(1, disposables.size)
        verify(inverse = true) { toAdd.dispose() }
    }

    @Test
    fun dispose_Sets_IsDisposed_True() {
        assertFalse(container.isDisposed)

        container.dispose()

        assertTrue(container.isDisposed)
    }

    @Test
    fun add_Adds_Disposable_To_List_When_Container_Not_Disposed() {
        container.add(toAdd)

        assertEquals(1, disposables.size)
        assertTrue(disposables.contains(toAdd))
    }

    @Test
    fun add_Does_Not_Add_Disposable_To_List_When_Disposable_Is_Disposed() {
        every { toAdd.isDisposed } returns true
        container.add(toAdd)

        assertEquals(0, disposables.size)
        assertFalse(disposables.contains(toAdd))
    }

    @Test
    fun add_Does_Not_Add_Disposable_To_List_When_Container_Is_Disposed() {
        container.dispose()
        container.add(toAdd)

        assertEquals(0, disposables.size)
        assertFalse(disposables.contains(toAdd))
    }

    @Test
    fun add_Disposes_Disposable_When_Container_Is_Disposed() {
        container.dispose()
        container.add(toAdd)

        verify { toAdd.dispose() }
    }
}