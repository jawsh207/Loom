/*
 * Copyright (C) 2026 The Folio Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.folio;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;

import androidx.preference.Preference;

import com.android.launcher3.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** The "Icon pack" entry in Folio's settings and its chooser dialog. */
public final class IconPackPicker {

    private IconPackPicker() { }

    /** Sets up the settings entry: summary shows the current pack, tap opens the chooser. */
    public static void bind(Preference preference) {
        Context context = preference.getContext();
        updateSummary(preference);
        preference.setOnPreferenceClickListener(p -> {
            show(context, () -> updateSummary(preference));
            return true;
        });
    }

    /** Sets up the "style other icons" switch so a change re-renders icons. */
    public static void bindFallback(Preference preference) {
        preference.setOnPreferenceChangeListener((p, value) -> {
            IconPackManager.get(p.getContext()).setFallbackEnabled((Boolean) value);
            return true;
        });
    }

    private static void updateSummary(Preference preference) {
        preference.setSummary(currentLabel(preference.getContext()));
    }

    /** The chosen icon pack's name, or "System icons". */
    public static CharSequence currentLabel(Context context) {
        CharSequence label = IconPackManager.get(context).getSelectedLabel();
        return label != null ? label : context.getString(R.string.icon_pack_system);
    }

    private static void show(Context context, Runnable onChanged) {
        IconPackManager manager = IconPackManager.get(context);
        List<IconPackManager.PackInfo> packs = manager.getInstalledPacks();
        if (packs.isEmpty() && manager.getSelectedPackage() == null) {
            DrawerTabDialogs.builder(context)
                    .setTitle(R.string.icon_pack_title)
                    .setMessage(R.string.icon_pack_none_found)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        // First row is "System icons" (no pack).
        List<CharSequence> labels = new ArrayList<>();
        List<Drawable> icons = new ArrayList<>();
        List<String> packages = new ArrayList<>();
        labels.add(context.getString(R.string.icon_pack_system));
        icons.add(null);
        packages.add(null);
        for (IconPackManager.PackInfo pack : packs) {
            labels.add(pack.label);
            icons.add(pack.icon);
            packages.add(pack.packageName);
        }
        int checked = Math.max(0, packages.indexOf(manager.getSelectedPackage()));

        int iconSize = context.getResources().getDimensionPixelSize(R.dimen.folio_pack_icon_size);
        int iconPadding = context.getResources()
                .getDimensionPixelSize(R.dimen.folio_pack_icon_padding);
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(
                DrawerTabDialogs.builder(context).getContext(),
                android.R.layout.simple_list_item_single_choice, labels) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                CheckedTextView row = (CheckedTextView) super.getView(
                        position, convertView, parent);
                Drawable icon = icons.get(position);
                if (icon != null) {
                    icon = icon.getConstantState() != null
                            ? icon.getConstantState().newDrawable().mutate() : icon;
                    icon.setBounds(0, 0, iconSize, iconSize);
                }
                row.setCompoundDrawablesRelative(icon, null, null, null);
                row.setCompoundDrawablePadding(iconPadding);
                row.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                return row;
            }
        };

        DrawerTabDialogs.builder(context)
                .setTitle(R.string.icon_pack_title)
                .setSingleChoiceItems(adapter, checked, (dialog, which) -> {
                    dialog.dismiss();
                    String pkg = packages.get(which);
                    if (!Objects.equals(pkg, manager.getSelectedPackage())) {
                        manager.setSelectedPackage(pkg);
                        onChanged.run();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
