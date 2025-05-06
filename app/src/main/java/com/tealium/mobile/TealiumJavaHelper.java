package com.tealium.mobile;

import android.app.Application;
import android.util.Log;

import com.tealium.core.api.Modules;
import com.tealium.core.api.Tealium;
import com.tealium.core.api.TealiumConfig;
import com.tealium.core.api.data.DataItem;
import com.tealium.core.api.data.DataItemUtils;
import com.tealium.core.api.data.DataList;
import com.tealium.core.api.data.DataObject;
import com.tealium.core.api.data.UnsupportedDataItemException;
import com.tealium.core.api.misc.Environment;
import com.tealium.core.api.modules.ModuleFactory;
import com.tealium.core.api.modules.consent.ConsentManagementAdapter;
import com.tealium.core.api.tracking.Dispatch;
import com.tealium.core.api.tracking.TealiumDispatchType;
import com.tealium.lifecycle.Lifecycle;
import com.tealium.lifecycle.LifecycleDataTarget;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TealiumJavaHelper {
    private TealiumJavaHelper() {
    }

    public static void init(Application app) {
        TealiumConfig config = new TealiumConfig(app, "tealiummobile", "android", Environment.DEV,
                configureModules());

        Tealium teal = Tealium.create(config, (result) -> {
            if (result.isSuccess()) {
                Log.d("", "Ready");
            } else {
                Log.d("", "Tealium failed", result.exceptionOrNull());
            }
        });
        teal.getTrace().join("");
//        Lifecycle.getInstance(teal);
    }

    public void track() {
        Tealium.get("teal", (tealium) -> {
            if (tealium == null) return;

            List<Integer> ints = Arrays.asList(1, 2, 3);
            DataList intList = DataList.fromIntCollection(ints);

            Map<String, List<Integer>> mapOfIntLists = new HashMap<>();
            mapOfIntLists.put("one", ints);
            mapOfIntLists.put("two", ints);
            mapOfIntLists.put("three", ints);
            DataObject list = DataItemUtils.dataObjectFromMapOfIntCollections(mapOfIntLists);

            try {
                int[] intArr = new int[]{1, 2, 3};
                DataList intListPrim = DataList.fromArray(intArr);
                DataList intListObj = DataList.fromArray(intArr);
            } catch (UnsupportedDataItemException ignore) {
            }

            Map<String, String> stringMap = new HashMap<>();
            stringMap.put("1", "1");
            stringMap.put("2", "2");
            DataObject.fromMapOfStrings(stringMap);

            try {
                Map<Object, String> data = new HashMap<>();
                data.put("", "");
                data.put(1, "");
                // Unsafe
                DataObject.fromMap(data);
            } catch (UnsupportedDataItemException ignore) {
            }

            DataObject dataObject = new DataObject.Builder()
                    .put("", "")
                    .put("", () -> DataItem.string(""))
                    .build();

            tealium.track(Dispatch.create("",
                    TealiumDispatchType.Event,
                    DataObject.EMPTY_OBJECT));

            tealium.track(Dispatch.create("",
                    TealiumDispatchType.Event,
                    DataObject.EMPTY_OBJECT), (status) -> {
                Log.d("TealiumJavaHelper", "Processing status: " + status.getDispatch().getTealiumEvent() + " - " + status);
            });
        });
    }

    private static List<ModuleFactory> configureModules() {
        return Arrays.asList(
                configureCollect()
//                configureConsent()
        );
    }

    private static ModuleFactory configureCollect() {
//        return Modules.collect();
//        return Modules.collect((settings) -> settings);

        return Modules.collect((settings) -> {
            String localhost = "https://localhost/";
            settings.setUrl(localhost)
                    .setBatchUrl(localhost);
            return settings;
        });
    }

    private static ModuleFactory configureLifecycle() {
//        return Lifecycle.configure();
        return Lifecycle.configure(lifecycleSettingsBuilder ->
            lifecycleSettingsBuilder.setDataTarget(LifecycleDataTarget.AllEvents)
        );
    }

    private static ModuleFactory configureConsent() {
        ConsentManagementAdapter cmp = new ExampleConsentManagementAdapter();
        return Modules.consent(cmp);

//        return Modules.consent(cmp, (settings) -> {
//            settings.setShouldRefireDispatchers(SetsKt.hashSetOf("CollectDispatcher"))
//                    .setDispatcherToPurposes(Collections.singletonMap("CollectDispatcher", SetsKt.hashSetOf("some_required_purpose")));
//            return settings;
//        });
    }
}
