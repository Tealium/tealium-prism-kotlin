package com.tealium.core

import com.tealium.core.api.ExternalMessenger
import com.tealium.core.api.Messenger
import com.tealium.core.api.MessengerService
import com.tealium.core.api.listeners.ExternalListener
import com.tealium.core.api.listeners.Listener
import com.tealium.core.internal.EventDispatcher
import com.tealium.core.internal.EventRouter
import com.tealium.core.internal.MessengerServiceImpl
import org.junit.Before
import org.junit.Test
import kotlin.reflect.KClass

class EventDispatcherTests {

    lateinit var router: EventDispatcher
    lateinit var messengerService: MessengerService

    lateinit var counter: CounterModule
    lateinit var incrementor: IncrementerModule

    @Before
    fun setup() {
        router = EventDispatcher()
        messengerService = MessengerServiceImpl(router)

        counter = CounterModule(router)
        incrementor = IncrementerModule(router)
        router.subscribe(counter)
        router.subscribe(incrementor)
    }

    @Test
    fun testIncrements() {
        counter.notifyCountUpdated()

        incrementor.sendIncrementMessage(1)
        incrementor.sendIncrementMessage(10)
    }

    @Test
    fun testIncrements_ForMultipleListeners() {
        counter.notifyCountUpdated()

        // add second counter
        val counter2 = CounterModule(router)
        router.subscribe(counter2)
        counter2.notifyCountUpdated()

        incrementor.sendIncrementMessage(1)
        incrementor.sendIncrementMessage(10)
    }

    @Test
    fun testExternalIncrements() {
        counter.notifyCountUpdated()

        incrementor.sendIncrementMessage(1)

        router.send(IncrementMessenger(-1))
//        router.send(CountMessenger(9))
    }

    @Test
    fun testMessengerRestrictions() {
        // CAN'T send built-in internal Messengers (they'd be obfuscated in pro-guard for android anyway)
//        messengerService.send(DispatchReadyMessenger(Dispatch()))

        // CAN'T create a new anonymous class/messenger for an internal listener
//        messengerService.send(object: Messenger<DispatchReadyListener> {
//            override val listenerClass: KClass<DispatchReadyListener>
//                get() = DispatchReadyListener::class
//
//            override fun deliver(listener: DispatchReadyListener) {
//                listener.onDispatchReady(Dispatch("", TealiumDispatchType.Event))
//            }
//        })

        // CAN'T create a new anonymous class/ExternalMessenger for an internal listener
//        messengerService.send(object: ExternalMessenger<DispatchReadyListener> {
//            override val listenerClass: KClass<DispatchReadyListener>
//                get() = DispatchReadyListener::class
//
//            override fun deliver(listener: DispatchReadyListener) {
//                listener.onDispatchReady(Dispatch("", TealiumDispatchType.Event))
//            }
//        })

        // CAN create a new anonymous class/ExternalMessenger for an External listener
        messengerService.send(object: ExternalMessenger<TestExternalListener> {
            override val listenerClass: KClass<TestExternalListener>
                get() = TestExternalListener::class

            override fun deliver(listener: TestExternalListener) {
                listener.onEvent()
            }
        })
    }
}

interface CountListener: Listener {
    fun onCountUpdated(count: Int)
}
interface IncrementListener: Listener {
    fun increment(amount: Int)
}
interface TestExternalListener: ExternalListener {
    fun onEvent()
}

class CounterModule(
    private val eventRouter: EventRouter
): IncrementListener {
    private var count = 0

    fun notifyCountUpdated() {
        eventRouter.send(
            CountMessenger(count)
        )
    }

    override fun increment(amount: Int) {
        if (amount == 0) {
            println("Cannot increment by 0; no-op")
            return
        }

        if (amount > 0) {
            println("Incrementing by $amount")
        } else {
            println("Decrementing by $amount")
        }

        count += amount
        notifyCountUpdated()
    }

    // bad things below
//    fun doSomethingWrong() {
//        eventRouter.send(object : Messenger<IncrementListener>(IncrementListener::class) {
//            override fun deliver(listener: IncrementListener) {
//                listener.increment(100)
//            }
//        })
//    }

    private class CountMessenger(
        private val count: Int
    ): Messenger<CountListener> {
        override val listenerClass: KClass<CountListener>
            get() = CountListener::class

        override fun deliver(listener: CountListener) {
            listener.onCountUpdated(count)
        }
    }
}

class IncrementMessenger(
    private val amount: Int
): Messenger<IncrementListener> {
    override val listenerClass: KClass<IncrementListener>
        get() = IncrementListener::class

    override fun deliver(listener: IncrementListener) {
        listener.increment(amount)
    }
}

class IncrementerModule(
    private val eventRouter: EventRouter
): CountListener {
    fun sendIncrementMessage(amount: Int = 1) {
        eventRouter.send(
            IncrementMessenger(amount)
        )
    }

    override fun onCountUpdated(count: Int) {
        println("Count = $count")
    }
}