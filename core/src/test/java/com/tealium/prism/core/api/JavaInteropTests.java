package com.tealium.prism.core.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.tealium.prism.core.api.barriers.Barriers;
import com.tealium.prism.core.api.command.Command;
import com.tealium.prism.core.api.command.DispatchCommandUtils;
import com.tealium.prism.core.api.data.DataItem;
import com.tealium.prism.core.api.data.DataObject;
import com.tealium.prism.core.api.data.LenientConverters;
import com.tealium.prism.core.api.misc.Scheduler;
import com.tealium.prism.core.api.pubsub.Disposable;
import com.tealium.prism.core.api.pubsub.Disposables;
import com.tealium.prism.core.api.pubsub.Observables;
import com.tealium.prism.core.api.pubsub.Subject;
import com.tealium.prism.core.api.tracking.Dispatch;
import com.tealium.prism.core.api.tracking.DispatchType;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Verifies that the public API is usable from Java as idiomatically as possible: static factories
 * are called on the type itself (not via {@code INSTANCE}/{@code Companion}), constants are fields
 * and callbacks can be lambdas that don't need to return {@code Unit}.
 * <p>
 * These tests mostly exist to fail to compile if that stops being the case.
 */
@RunWith(RobolectricTestRunner.class)
public class JavaInteropTests {

    @Test
    public void dataItem_parse_Is_Static() {
        DataItem item = DataItem.parse("\"value\"");

        assertEquals("value", item.getString());
    }

    @Test
    public void barriers_Factories_Are_Static() {
        assertNotNull(Barriers.connectivity());
        assertNotNull(Barriers.connectivity(settings -> settings));
        assertNotNull(Barriers.batching());
        assertNotNull(Barriers.batching(settings -> settings));
    }

    @Test
    public void disposables_Factories_Are_Static() {
        assertTrue(Disposables.disposed().isDisposed());
        assertFalse(Disposables.composite().isDisposed());
        assertFalse(Disposables.composite(Scheduler.SYNCHRONOUS).isDisposed());
        assertFalse(Disposables.subscription().isDisposed());
    }

    @Test
    public void disposables_subscription_Accepts_Runnable_Lambda() {
        AtomicBoolean disposed = new AtomicBoolean(false);

        Disposable subscription = Disposables.subscription(() -> disposed.set(true));
        subscription.dispose();

        assertTrue(disposed.get());
    }

    @Test
    public void lenientConverters_Are_Static_Fields() {
        DataItem five = DataItem.string("5");

        assertEquals(Integer.valueOf(5), LenientConverters.INT.convert(five));
        assertEquals(Long.valueOf(5), LenientConverters.LONG.convert(five));
        assertEquals(Double.valueOf(5), LenientConverters.DOUBLE.convert(five));
        assertEquals("5", LenientConverters.STRING.convert(five));
        assertEquals(Boolean.TRUE, LenientConverters.BOOLEAN.convert(DataItem.string("true")));
    }

    @Test
    public void scheduler_Constants_Are_Static_Fields() {
        assertNotNull(Scheduler.MAIN);
        assertNotNull(Scheduler.SYNCHRONOUS);

        AtomicBoolean executed = new AtomicBoolean(false);
        Scheduler.SYNCHRONOUS.execute(() -> executed.set(true));

        assertTrue(executed.get());
    }

    @Test
    public void observable_forEach_Accepts_Consumer_Lambda() {
        Subject<String> subject = Observables.publishSubject();
        List<String> received = new ArrayList<>();

        subject.forEach(received::add).subscribe(value -> {
        });
        subject.onNext("a");
        subject.onNext("b");

        assertEquals(Arrays.asList("a", "b"), received);
    }

    @Test
    public void command_synchronous_Accepts_Consumer_Lambda_And_Is_Static() {
        List<DataObject> received = new ArrayList<>();
        Command command = Command.synchronous("cmd", received::add);

        command.execute(DataObject.EMPTY_OBJECT, result -> {
        });

        assertEquals("cmd", command.getName());
        assertEquals(1, received.size());
        assertSame(DataObject.EMPTY_OBJECT, received.get(0));
    }

    @Test
    public void dispatchCommands_Have_A_Stable_Facade_Name() {
        Dispatch dispatch = Dispatch.create(
                "event",
                DispatchType.Event,
                DataObject.fromString("{\"command_name\":[\"one\",\"two\"]}")
        );

        assertEquals(Arrays.asList("one", "two"), DispatchCommandUtils.getCommands(dispatch));
    }
}
