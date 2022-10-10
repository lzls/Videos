/*
 * Created on 2021-12-20 2:19:41 PM.
 * Copyright © 2021 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.bean;

import androidx.core.util.ObjectsCompat;

import com.google.common.base.Strings;

import kotlin.text.StringsKt;

public class Device {
    private String id;
    private String manufacturer;
    private String model;

    public Device() {
    }

    public Device(String id, String manufacturer, String model) {
        this.id = id;
        this.manufacturer = manufacturer;
        this.model = model;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getNormalizedDeviceModel() {
        String manufacturer = Strings.nullToEmpty(this.manufacturer).trim();
        String model = Strings.nullToEmpty(this.model).trim();
        if (StringsKt.startsWith(model, manufacturer, true)) {
            return model;
        } else {
            return manufacturer + " " + model;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        Device device = (Device) o;
        return ObjectsCompat.equals(id, device.id)
                && ObjectsCompat.equals(manufacturer, device.manufacturer)
                && ObjectsCompat.equals(model, device.model);
    }

    @Override
    public int hashCode() {
        return ObjectsCompat.hash(id, manufacturer, model);
    }

    @Override
    public String toString() {
        return "Device{" +
                "id='" + id + '\'' +
                ", manufacturer='" + manufacturer + '\'' +
                ", model='" + model + '\'' +
                '}';
    }
}
