/*
 * Created on 2022-11-26 12:46:15 AM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.utils;

import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class SynchronizedPlatformSparseArray<E> extends SparseArray<E> {

    private final Object mLock;

    public SynchronizedPlatformSparseArray() {
        mLock = this;
    }

    public SynchronizedPlatformSparseArray(int initialCapacity) {
        super(initialCapacity);
        mLock = this;
    }

    public SynchronizedPlatformSparseArray(@NonNull Object lock) {
        mLock = lock;
    }

    public SynchronizedPlatformSparseArray(@NonNull Object lock, int initialCapacity) {
        super(initialCapacity);
        mLock = lock;
    }

    @NonNull
    @Override
    public SynchronizedPlatformSparseArray<E> clone() {
        synchronized (mLock) {
            return (SynchronizedPlatformSparseArray<E>) super.clone();
        }
    }

    @Override
    public boolean contains(int key) {
        synchronized (mLock) {
            return super.contains(key);
        }
    }

    @Override
    public E get(int key) {
        synchronized (mLock) {
            return super.get(key);
        }
    }

    @Override
    public E get(int key, E valueIfKeyNotFound) {
        synchronized (mLock) {
            return super.get(key, valueIfKeyNotFound);
        }
    }

    @Override
    public void delete(int key) {
        synchronized (mLock) {
            super.delete(key);
        }
    }

    @Override
    public void remove(int key) {
        synchronized (mLock) {
            super.remove(key);
        }
    }

    @Override
    public void removeAt(int index) {
        synchronized (mLock) {
            super.removeAt(index);
        }
    }

    @Override
    public void removeAtRange(int index, int size) {
        synchronized (mLock) {
            super.removeAtRange(index, size);
        }
    }

    @Override
    public void set(int key, E value) {
        synchronized (mLock) {
            super.set(key, value);
        }
    }

    @Override
    public void put(int key, E value) {
        synchronized (mLock) {
            super.put(key, value);
        }
    }

    @Override
    public int size() {
        synchronized (mLock) {
            return super.size();
        }
    }

    @Override
    public int keyAt(int index) {
        synchronized (mLock) {
            return super.keyAt(index);
        }
    }

    @Override
    public E valueAt(int index) {
        synchronized (mLock) {
            return super.valueAt(index);
        }
    }

    @Override
    public void setValueAt(int index, E value) {
        synchronized (mLock) {
            super.setValueAt(index, value);
        }
    }

    @Override
    public int indexOfKey(int key) {
        synchronized (mLock) {
            return super.indexOfKey(key);
        }
    }

    @Override
    public int indexOfValue(E value) {
        synchronized (mLock) {
            return super.indexOfValue(value);
        }
    }

    @Override
    public void clear() {
        synchronized (mLock) {
            super.clear();
        }
    }

    @Override
    public void append(int key, E value) {
        synchronized (mLock) {
            super.append(key, value);
        }
    }

    @NonNull
    @Override
    public String toString() {
        synchronized (mLock) {
            return super.toString();
        }
    }

    @Override
    public boolean contentEquals(@Nullable SparseArray<?> other) {
        synchronized (mLock) {
            return super.contentEquals(other);
        }
    }

    @Override
    public int contentHashCode() {
        synchronized (mLock) {
            return super.contentHashCode();
        }
    }
}
