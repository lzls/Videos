package com.liuzhenlin.floatingmenu;

import android.view.View;

import androidx.annotation.NonNull;

public class MenuItem {

    private int id = View.NO_ID;
    private CharSequence text;
    private int iconResId;
    private boolean enabled = true;

    public MenuItem() {
    }

    public MenuItem(int id, String text) {
        this.id = id;
        this.text = text;
    }

    public MenuItem(int id, String text, int iconResId) {
        this.id = id;
        this.text = text;
        this.iconResId = iconResId;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public CharSequence getText() {
        return text;
    }

    public void setText(CharSequence item) {
        this.text = item;
    }

    public int getIconResId() {
        return iconResId;
    }

    public void setIconResId(int itemResId) {
        this.iconResId = itemResId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @NonNull
    @Override
    public String toString() {
        return "MenuItem{" +
                "id=" + id + ", " +
                "text='" + text + "', " +
                "enabled=" + enabled +
                '}';
    }
}
