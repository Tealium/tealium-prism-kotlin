package com.tealium.mobile;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;

import com.tealium.core.Dispatches;
import com.tealium.core.api.Modules;
import com.tealium.core.api.Tealium;
import com.tealium.core.api.TealiumConfig;
import com.tealium.core.api.data.TealiumBundle;
import com.tealium.core.api.data.TealiumSerializable;
import com.tealium.core.api.data.TealiumValue;
import com.tealium.core.api.misc.Environment;
import com.tealium.core.api.modules.ModuleFactory;
import com.tealium.core.api.modules.consent.ConsentManagementAdapter;
import com.tealium.core.api.tracking.Dispatch;
import com.tealium.core.api.tracking.TealiumDispatchType;

import java.util.Arrays;
import java.util.List;

public class TealiumJavaHelper {
    private TealiumJavaHelper() {
    }

    public static void init(Application app) {
        TealiumConfig config = new TealiumConfig(app, "tealiummobile", "android", Environment.DEV,
                configureModules());

        Tealium teal = Tealium.create("", config, (result) -> {
            if (result.isSuccess()) {
                Log.d("", "Ready");
            } else {
                Log.d("", "Tealium failed", result.exceptionOrNull());
            }
        });
        teal.getTrace().join("");
    }

    public void track() {
        Tealium tealium = Tealium.get("teal");
        if (tealium == null) return;

        TealiumBundle bundle = new TealiumBundle.Builder()
                .put("", "")
                .put("", () -> TealiumValue.string(""))
                .getBundle();

        tealium.track(Dispatch.create("",
                TealiumDispatchType.Event,
                TealiumBundle.EMPTY_BUNDLE));

        tealium.track(Dispatch.create("",
                TealiumDispatchType.Event,
                TealiumBundle.EMPTY_BUNDLE), (dispatch, status) -> {
            Log.d("TealiumJavaHelper", "Processing status: " + dispatch.getTealiumEvent() + " - " + status);
        });

        tealium.track(
                Dispatches.event("")
                        .putContextData(new TealiumBundle.Builder()
                                .put("some_key", "some string value")
                                .getBundle())
                        .build()
        );
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
