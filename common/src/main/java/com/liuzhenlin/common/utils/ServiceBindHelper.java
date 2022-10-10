/*
 * Created on 2022-3-5 12:45:06 AM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.collection.SimpleArrayMap;
import androidx.core.util.Consumer;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;

public class ServiceBindHelper {
    private ServiceBindHelper() {
    }

    private static final SimpleArrayMap<Class<? extends Service>, WeakHashMap<Context, ServiceConn>>
            sServiceClientMap = new SimpleArrayMap<>();

    private static Field sBaseContextField;

    static {
        try {
            //noinspection JavaReflectionMemberAccess,DiscouragedPrivateApi
            sBaseContextField = ContextWrapper.class.getDeclaredField("mBase");
            sBaseContextField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    public static void bind(
            @NonNull Context context,
            @NonNull Class<? extends Service> serviceCls,
            @Nullable Consumer<IBinder> onBindAction) {
        bind(context, serviceCls, null, onBindAction);
    }

    public static void bind(
            @NonNull Context context,
            @NonNull Class<? extends Service> serviceCls,
            @Nullable Intent intent,
            @Nullable Consumer<IBinder> onBindAction) {
        // Try to cache the onBindAction as a member of the client Context to avoid it to be
        // unexpectedly collected by GC, as we use only a WeakReference here to point to it.
        replaceClientContextBaseIfNeeded(context);
        addOnBindActionToClientBaseContext(context, onBindAction);
        // Avoid memory leaks...
        WeakReference<Context> ctxRef = new WeakReference<>(context);
        WeakReference<Consumer<IBinder>> onBindActionRef = new WeakReference<>(onBindAction);
        context = null;
        onBindAction = null;

        LooperExecutor mainExecutor = Executors.MAIN_EXECUTOR;
        mainExecutor.execute(() -> {
            Context ctx = ctxRef.get();
            if (ctx == null) {
                return;
            }

            WeakHashMap<Context, ServiceConn> clients = sServiceClientMap.get(serviceCls);
            if (clients == null) {
                clients = new WeakHashMap<>();
                sServiceClientMap.put(serviceCls, clients);
            }

            ServiceConn[] conn = {clients.get(ctx)};
            if (conn[0] == null) {
                conn[0] = new ServiceConn(
                        clients, ctxRef, new ArrayList<>(Collections.singletonList(onBindActionRef)));
                clients.put(ctx, conn[0]);
                ctx.bindService(intent != null ? intent : new Intent(ctx, serviceCls),
                        conn[0], Context.BIND_AUTO_CREATE);

                if (ctx instanceof LifecycleOwner) {
                    ((LifecycleOwner) ctx).getLifecycle().addObserver((LifecycleEventObserver)
                            (source, event) -> {
                                if (event == Lifecycle.Event.ON_DESTROY) {
                                    conn[0].disconnect();
                                }
                            });
                } else if (ctx instanceof Activity) {
                    ((Activity) ctx).getFragmentManager().beginTransaction()
                            .add(new HostDeathMonitorFragment(ctx, serviceCls), null)
                            .commitAllowingStateLoss();
                }
                // Maybe the context is in the process of binding with the target service.
                // Just add the onBindAction into the list of actions pending to be executed.
            } else if (conn[0].mClient == null) {
                conn[0].mOnBindActions.add(onBindActionRef);
            } else {
                Consumer<IBinder> onCtxBindAction = onBindActionRef.get();
                if (onCtxBindAction != null) {
                    removeOnBindActionFromClientBaseContext(ctx, onCtxBindAction);
                    onCtxBindAction.accept(conn[0].mClient);
                }
            }
        });
    }

    public static void unbind(
            @NonNull Context context,
            @NonNull Class<? extends Service> serviceCls) {
        // Avoid memory leaks...
        WeakReference<Context> ctxRef = new WeakReference<>(context);
        context = null;

        LooperExecutor mainExecutor = Executors.MAIN_EXECUTOR;
        mainExecutor.execute(() -> {
            WeakHashMap<Context, ServiceConn> clients = sServiceClientMap.get(serviceCls);
            if (clients == null) return;

            ServiceConn conn = clients.get(ctxRef.get());
            if (conn == null) return;

            if (conn.mClient == null) {
                // Maybe the context is being bound to the target service.
                // Just waits till the binding completes.
                long start = SystemClock.uptimeMillis();
                Utils.postTillConditionMeets(mainExecutor.getHandler(), conn::disconnect,
                        () -> {
                            Context ctx = ctxRef.get();
                            return ctx == null
                                    || conn.mClient != null
                                    || SystemClock.uptimeMillis() - start > 10 * 1000;
                        });
            } else {
                conn.disconnect();
            }
        });
    }

    private static final class ServiceConn implements ServiceConnection {

        private final WeakHashMap<Context, ServiceConn> mClients;
        private final WeakReference<Context> mContext;
        final List<WeakReference<Consumer<IBinder>>> mOnBindActions;
        IBinder mClient;

        ServiceConn(
                WeakHashMap<Context, ServiceConn> clients,
                WeakReference<Context> context,
                List<WeakReference<Consumer<IBinder>>> onBindAction) {
            mClients = clients;
            mContext = context;
            mOnBindActions = onBindAction;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder client) {
            Context ctx = mContext.get();
            if (ctx != null && mClients.containsKey(ctx)) {
                mClient = client;
                List<WeakReference<Consumer<IBinder>>> onBindActions = mOnBindActions;
                for (int i = onBindActions.size() - 1; i >= 0; i--) {
                    Consumer<IBinder> onBindAction = onBindActions.get(i).get();
                    if (onBindAction != null) {
                        removeOnBindActionFromClientBaseContext(ctx, onBindAction);
                        onBindAction.accept(client);
                    }
                }
                onBindActions.clear();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mClient = null;
        }

        @Override
        public void onBindingDied(ComponentName name) {
            disconnect();
        }

        @Override
        public void onNullBinding(ComponentName name) {
            disconnect();
        }

        void disconnect() {
            Context ctx = mContext.get();
            if (ctx != null) {
                ctx.unbindService(this);
            }
            mClients.remove(ctx);
            mClient = null;
        }
    }

    @VisibleForTesting
    public static final class HostDeathMonitorFragment extends Fragment {

        private final Context mContext;
        private final Class<? extends Service> mServiceCls;

        public HostDeathMonitorFragment() {
            this(null, null);
        }

        @SuppressWarnings("deprecation")
        @SuppressLint("ValidFragment")
        HostDeathMonitorFragment(Context context, Class<? extends Service> serviceCls) {
            mContext = context;
            mServiceCls = serviceCls;
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (mContext == null) {
                // This fragment was recreated during state restore... So remove it
                getFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (mContext != null) {
                unbind(mContext, mServiceCls);
            }
        }
    }

    private static void replaceClientContextBaseIfNeeded(Context context) {
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (context) {
            if (context instanceof ContextWrapper
                    && !(((ContextWrapper) context).getBaseContext() instanceof ClientBaseContext)) {
                if (sBaseContextField != null) {
                    try {
                        sBaseContextField.set(context,
                                new ClientBaseContext(((ContextWrapper) context).getBaseContext()));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private static void addOnBindActionToClientBaseContext(Context context, Consumer<IBinder> action) {
        if (context instanceof ContextWrapper
                && ((ContextWrapper) context).getBaseContext() instanceof ClientBaseContext) {
            ClientBaseContext clientBaseContext =
                    (ClientBaseContext) ((ContextWrapper) context).getBaseContext();
            clientBaseContext.addOnBindAction(action);
        }
    }

    @Synthetic
    static void removeOnBindActionFromClientBaseContext(Context context, Consumer<IBinder> action) {
        if (context instanceof ContextWrapper
                && ((ContextWrapper) context).getBaseContext() instanceof ClientBaseContext) {
            ClientBaseContext clientBaseContext =
                    (ClientBaseContext) ((ContextWrapper) context).getBaseContext();
            clientBaseContext.removeOnBindAction(action);
        }
    }

    private static final class ClientBaseContext extends ContextWrapper {

        private final List<Consumer<IBinder>> mOnBindActions = new ArrayList<>();

        void addOnBindAction(Consumer<IBinder> action) {
            synchronized (mOnBindActions) {
                if (!mOnBindActions.contains(action)) {
                    mOnBindActions.add(action);
                }
            }
        }

        void removeOnBindAction(Consumer<IBinder> action) {
            synchronized (mOnBindActions) {
                mOnBindActions.remove(action);
            }
        }

        ClientBaseContext(Context base) {
            super(base);
        }
    }
}
