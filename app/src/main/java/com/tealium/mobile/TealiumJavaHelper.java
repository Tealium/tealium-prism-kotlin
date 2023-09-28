package com.tealium.mobile;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;

import com.tealium.core.Dispatches;
import com.tealium.core.Environment;
import com.tealium.core.Modules;
import com.tealium.core.Tealium;
import com.tealium.core.TealiumConfig;
import com.tealium.core.api.Dispatch;
import com.tealium.core.api.TealiumDispatchType;
import com.tealium.core.api.data.TealiumBundle;
import com.tealium.core.api.data.TealiumSerializable;
import com.tealium.core.api.data.TealiumValue;

import java.util.Collections;

public class TealiumJavaHelper {
    private TealiumJavaHelper() {
    }

    public static void init(Application app) {
        Tealium teal = Tealium.create(
                "",
                new TealiumConfig(app, "tealiummobile", "android", Environment.DEV, "tealium-settings.json",
                        Collections.singletonList(Modules.Collect)), (tealium, error) -> {
                    Log.d("", "Ready");
                });
        teal.getTrace().join("");
    }

    public void track() {
        Tealium tealium = Tealium.get("teal");
        if (tealium == null) return;

        TealiumBundle bundle = new TealiumBundle.Builder()
                .put("", "")
                .put("", new TealiumSerializable() {
                    @NonNull
                    @Override
                    public TealiumValue asTealiumValue() {
                        return TealiumValue.string("");
                    }
                })
                .getBundle();

        tealium.track(Dispatch.create("",
                TealiumDispatchType.Event,
                TealiumBundle.EMPTY_BUNDLE));

        tealium.track(
                Dispatches.event("")
                        .putContextData(new TealiumBundle.Builder()
                                .put("some_key", "some string value")
                                .getBundle())
                        .build()
        );
    }
}
