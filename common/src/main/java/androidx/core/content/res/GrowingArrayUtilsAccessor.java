/*
 * Created on 2022-11-26 12:20:24 AM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package androidx.core.content.res;

public class GrowingArrayUtilsAccessor {
    private GrowingArrayUtilsAccessor() {
    }

    public static <T> T[] append(T[] array, int currentSize, T element) {
        return GrowingArrayUtils.append(array, currentSize, element);
    }

    public static int[] append(int[] array, int currentSize, int element) {
        return GrowingArrayUtils.append(array, currentSize, element);
    }

    public static long[] append(long[] array, int currentSize, long element) {
        return GrowingArrayUtils.append(array, currentSize, element);
    }

    public static boolean[] append(boolean[] array, int currentSize, boolean element) {
        return GrowingArrayUtils.append(array, currentSize, element);
    }

    public static <T> T[] insert(T[] array, int currentSize, int index, T element) {
        return GrowingArrayUtils.insert(array, currentSize, index, element);
    }

    public static int[] insert(int[] array, int currentSize, int index, int element) {
        return GrowingArrayUtils.insert(array, currentSize, index, element);
    }

    public static long[] insert(long[] array, int currentSize, int index, long element) {
        return GrowingArrayUtils.insert(array, currentSize, index, element);
    }

    public static boolean[] insert(boolean[] array, int currentSize, int index, boolean element) {
        return GrowingArrayUtils.insert(array, currentSize, index, element);
    }

    public static int growSize(int currentSize) {
        return GrowingArrayUtils.growSize(currentSize);
    }
}
