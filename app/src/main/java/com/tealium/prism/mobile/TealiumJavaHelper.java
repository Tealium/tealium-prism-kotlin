package com.tealium.prism.mobile;

import android.app.Application;
import android.util.Log;

import com.tealium.prism.core.api.Modules;
import com.tealium.prism.core.api.Tealium;
import com.tealium.prism.core.api.TealiumConfig;
import com.tealium.prism.core.api.data.DataItem;
import com.tealium.prism.core.api.data.DataItemUtils;
import com.tealium.prism.core.api.data.DataList;
import com.tealium.prism.core.api.data.DataObject;
import com.tealium.prism.core.api.data.UnsupportedDataItemException;
import com.tealium.prism.core.api.misc.Environment;
import com.tealium.prism.core.api.misc.TealiumResult;
import com.tealium.prism.core.api.modules.ModuleFactory;
import com.tealium.prism.core.api.pubsub.Single;
import com.tealium.prism.core.api.pubsub.SingleUtils;
import com.tealium.prism.core.api.tracking.DispatchType;
import com.tealium.prism.core.api.tracking.TrackResult;
import com.tealium.prism.lifecycle.Lifecycle;
import com.tealium.prism.lifecycle.LifecycleDataTarget;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TealiumJavaHelper {
    private TealiumJavaHelper() {
    }

    public static void init(Application app) {
        TealiumConfig.Builder config = new TealiumConfig.Builder(app, "tealiummobile", "android", Environment.DEV,
                configureModules());

//        config.enableConsentIntegration(new ExampleCmpAdapter(app.getApplicationContext()), (settings) -> {
//            settings
//                    .setTealiumPurposeId(Purposes.TEALIUM)
//                    .addPurpose(Purposes.TRACKING, SetsKt.setOf(Modules.Types.COLLECT))
//                    .setRefireDispatcherIds(SetsKt.setOf(Modules.Types.COLLECT));
//            return settings;
//        });

        Tealium teal = Tealium.create(config.build(), (result) -> {
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

            tealium.track("",
                    DispatchType.Event,
                    DataObject.EMPTY_OBJECT);

            Single<TealiumResult<TrackResult>> result = tealium.track("",
                    DispatchType.Event,
                    DataObject.EMPTY_OBJECT);
            SingleUtils.onSuccess(result, (status) -> Log.d("TealiumJavaHelper", "Processing status: " + status.getDispatch().getTealiumEvent() + " - " + status));
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
}
