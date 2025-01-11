/*
 * Created on 2022-11-27 2:39:02 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class ReflectionUtils {
    private ReflectionUtils() {
    }

    public static final int FIELD_MODIFIERS =
            Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE |
                    Modifier.STATIC | Modifier.FINAL | Modifier.TRANSIENT |
                    Modifier.VOLATILE;

    private static volatile Field sFieldAccessFlagsField;
    private static volatile boolean sFieldAccessFlagsFieldFetched;

    private static void ensureFieldAccessFlagsFieldFetched() {
        if (!sFieldAccessFlagsFieldFetched) {
            synchronized (ReflectionUtils.class) {
                if (!sFieldAccessFlagsFieldFetched) {
                    try {
                        //noinspection DiscouragedPrivateApi,JavaReflectionMemberAccess
                        sFieldAccessFlagsField = Field.class.getDeclaredField("accessFlags");
                        sFieldAccessFlagsField.setAccessible(true);
                    } catch (NoSuchFieldException e) {
                        e.printStackTrace();
                    }
                    sFieldAccessFlagsFieldFetched = true;
                }
            }
        }
    }

    public static boolean addFieldModifiers(@NonNull Field field, int modifiers) {
        return modFieldModifiers(field, modifiers, true);
    }

    public static boolean removeFieldModifiers(@NonNull Field field, int modifiers) {
        return modFieldModifiers(field, modifiers, false);
    }

    private static boolean modFieldModifiers(Field field, int modifiers, boolean add) {
        ensureFieldAccessFlagsFieldFetched();
        if (sFieldAccessFlagsField != null) {
            try {
                int accessFlags = sFieldAccessFlagsField.getInt(field);
                int newAccessFlags =
                        add ? accessFlags | (modifiers & FIELD_MODIFIERS)
                                : accessFlags & ~(modifiers & FIELD_MODIFIERS);
                if (accessFlags != newAccessFlags) {
                    sFieldAccessFlagsField.setInt(field, newAccessFlags);
                }
                return true;
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    @Nullable
    public static Field getDeclaredField(@NonNull Class<?> clazz, @NonNull String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Nullable
    public static Method getDeclaredMethod(
            @NonNull Class<?> clazz, @NonNull String methodName, @Nullable Class<?>... parameterTypes) {
        try {
            Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Nullable
    public static <T> Constructor<T> getDeclaredConstructor(
            @NonNull Class<T> clazz, @Nullable Class<?>... parameterTypes) {
        try {
            Constructor<T> constructor = clazz.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Nullable
    public static <T> T getDeclaredFieldValue(
            @NonNull Class<?> clazz, @Nullable Object obj, @NonNull String fieldName) {
        Field field = getDeclaredField(clazz, fieldName);
        if (field != null) {
            try {
                //noinspection unchecked
                return (T) field.get(obj);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}
