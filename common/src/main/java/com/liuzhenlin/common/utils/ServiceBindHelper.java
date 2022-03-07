/*
 * Created on 2022-3-5 12:45:06 AM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.utils;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.SimpleArrayMap;
import androidx.core.util.Consumer;

import java.util.WeakHashMap;

public class ServiceBindHelper {
    private ServiceBindHelper() {}

    private static final SimpleArrayMap<Class<? extends Service>, WeakHashMap<Context, IBinder>>
            sServiceClients = new SimpleArrayMap<>();

    public static void bind(
            @NonNull Context context,
            @NonNull Class<? extends Service> serviceCls,
            @NonNull Consumer<IBinder> onBindAction) {
        bind(context, serviceCls, null, onBindAction);
    }

    public static void bind(
            @NonNull Context context,
            @NonNull Class<? extends Service> serviceCls,
            @Nullable Intent intent,
            @NonNull Consumer<IBinder> onBindAction) {
        LooperExecutor mainExecutor = Executors.MAIN_EXECUTOR;
        mainExecutor.execute(() -> {
            WeakHashMap<Context, IBinder> clients = sServiceClients.get(serviceCls);
            if (clients == null) {
                clients = new WeakHashMap<>();
                sServiceClients.put(serviceCls, clients);
            }

            WeakHashMap<Context, IBinder> clientsCopy = clients;
            IBinder client = clients.get(context);
            if (client == null) {
                if (clients.containsKey(context)) {
                    boolean[] contextStillAlive = { false };
                    Utils.postTillConditionMeets(mainExecutor.getHandler(),
                            () -> {
                                if (contextStillAlive[0]) {
                                    bind(context, serviceCls, intent, onBindAction);
                                }
                            },
                            () -> {
                                contextStillAlive[0] = clientsCopy.containsKey(context);
                                return !contextStillAlive[0] || clientsCopy.get(context) != null;
                            });
                    return;
                }
                clients.put(context, null);
                context.bindService(intent != null ? intent : new Intent(context, serviceCls),
                        new ServiceConnection() {
                            @Override
                            public void onServiceConnected(ComponentName name, IBinder client) {
                                clientsCopy.put(context, client);
                                onBindAction.accept(client);
                            }

                            @Override
                            public void onServiceDisconnected(ComponentName name) {
                                clientsCopy.remove(context);
                            }
                        }, Context.BIND_AUTO_CREATE);
            } else {
                onBindAction.accept(client);
            }
        });
    }
}
