package com.tealium.prism.core.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.tealium.prism.core.api.barriers.Barriers;
import com.tealium.prism.core.api.command.Command;
import com.tealium.prism.core.api.command.CommandName;
import com.tealium.prism.core.api.command.DispatchCommandUtils;
import com.tealium.prism.core.api.data.DataItem;
import com.tealium.prism.core.api.data.DataObject;
import com.tealium.prism.core.api.data.ExtractionException;
import com.tealium.prism.core.api.data.FailableExtractionUtils;
import com.tealium.prism.core.api.data.JsonObjectPathConvertible;
import com.tealium.prism.core.api.data.JsonPath;
import com.tealium.prism.core.api.data.JsonPathConvertible;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    public void command_synchronous_Accepts_CommandName_And_Is_Static() {
        List<DataObject> received = new ArrayList<>();
        CommandName name = () -> "launch";
        Command command = Command.synchronous(name, received::add);

        command.execute(DataObject.EMPTY_OBJECT, result -> {
        });

        assertEquals("launch", command.getName());
        assertEquals(1, received.size());
    }

    @Test
    public void jsonObjectPathConvertible_Lambda_Converts_To_JsonPathConvertible() {
        JsonPath<JsonPath.Component.Key> path = JsonPath.root("value");
        JsonObjectPathConvertible lambda = () -> path;
        JsonPathConvertible<JsonPath.Component.Key> convertible = lambda;

        assertSame(path, convertible.asJsonPath());
    }

    @Test
    public void failableExtraction_Have_A_Stable_Facade_Name() throws ExtractionException {
        DataObject payload = DataObject.fromString(
                "{\"text\":\"abc\",\"object\":{}}"
        );
        JsonObjectPathConvertible text = () -> JsonPath.root("text");
        JsonObjectPathConvertible object = () -> JsonPath.root("object");
        JsonObjectPathConvertible absent = () -> JsonPath.root("absent");

        assertEquals(DataItem.string("abc"), FailableExtractionUtils.requireDataItem(payload, text));
        assertEquals(
                "abc",
                FailableExtractionUtils.require(payload, text, LenientConverters.STRING, "String")
        );
        assertEquals(DataObject.EMPTY_OBJECT, FailableExtractionUtils.requireDataObject(payload, object));
        assertEquals(
                "abc",
                FailableExtractionUtils.optional(payload, text, LenientConverters.STRING, "String")
        );
        assertNull(FailableExtractionUtils.optional(payload, absent, LenientConverters.STRING, "String"));
        assertEquals(DataObject.EMPTY_OBJECT, FailableExtractionUtils.optionalDataObject(payload, object));
        assertNull(FailableExtractionUtils.optionalDataObject(payload, absent));
        assertEquals("abc", FailableExtractionUtils.extract(payload, text, LenientConverters.STRING));
        assertNull(FailableExtractionUtils.extract(payload, absent, LenientConverters.STRING));
        assertEquals(DataObject.EMPTY_OBJECT, FailableExtractionUtils.extractDataObject(payload, object));
        assertNull(FailableExtractionUtils.extractDataObject(payload, text));
    }

    @Test
    public void failableExtraction_Rule_Helpers_Are_Callable_From_Java() throws ExtractionException {
        DataObject payload = DataObject.fromString("{\"text\":\"abc\",\"count\":2.0}");
        JsonObjectPathConvertible text = () -> JsonPath.root("text");
        JsonObjectPathConvertible count = () -> JsonPath.root("count");
        JsonObjectPathConvertible absent = () -> JsonPath.root("absent");
        Map<String, Integer> values = Collections.singletonMap("abc", 1);

        assertEquals("abc", FailableExtractionUtils.requireNonBlank(payload, text, LenientConverters.STRING));
        assertEquals(Integer.valueOf(1), FailableExtractionUtils.requireOneOf(payload, text, values));
        assertEquals(Integer.valueOf(1), FailableExtractionUtils.optionalOneOf(payload, text, values));
        assertNull(FailableExtractionUtils.optionalOneOf(payload, absent, values));
        assertEquals(
                Integer.valueOf(2),
                FailableExtractionUtils.require(payload, count, LenientConverters.INT, "Int")
        );
    }

    @Test
    public void failableExtraction_Helpers_Accept_Raw_JsonPath_From_Java() throws ExtractionException {
        DataObject payload = DataObject.fromString(
                "{\"text\":\"abc\",\"object\":{}}"
        );
        JsonPath<JsonPath.Component.Key> text = JsonPath.root("text");
        JsonPath<JsonPath.Component.Key> object = JsonPath.root("object");
        Map<String, Integer> values = Collections.singletonMap("abc", 1);

        assertEquals(DataItem.string("abc"), FailableExtractionUtils.requireDataItem(payload, text));
        assertEquals(
                "abc",
                FailableExtractionUtils.require(payload, text, LenientConverters.STRING, "String")
        );
        assertEquals(DataObject.EMPTY_OBJECT, FailableExtractionUtils.requireDataObject(payload, object));
        assertEquals("abc", FailableExtractionUtils.requireNonBlank(payload, text, LenientConverters.STRING));
        assertEquals(Integer.valueOf(1), FailableExtractionUtils.requireOneOf(payload, text, values));
        assertEquals(
                "abc",
                FailableExtractionUtils.optional(payload, text, LenientConverters.STRING, "String")
        );
        assertEquals(DataObject.EMPTY_OBJECT, FailableExtractionUtils.optionalDataObject(payload, object));
        assertEquals(Integer.valueOf(1), FailableExtractionUtils.optionalOneOf(payload, text, values));
        assertEquals("abc", FailableExtractionUtils.extract(payload, text, LenientConverters.STRING));
        assertEquals(DataObject.EMPTY_OBJECT, FailableExtractionUtils.extractDataObject(payload, object));
    }

    @Test
    public void failableExtraction_Throw_ExtractionException_When_Parameter_Is_Missing() {
        JsonObjectPathConvertible missing = () -> JsonPath.root("missing");

        try {
            FailableExtractionUtils.requireDataItem(DataObject.EMPTY_OBJECT, missing);
            throw new AssertionError("Expected an ExtractionException");
        } catch (ExtractionException e) {
            assertTrue(e instanceof ExtractionException.Missing);
            assertEquals("missing", ((ExtractionException.Missing) e).getKey());
        }
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
