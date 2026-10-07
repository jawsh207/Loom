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

import android.content.ComponentName;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.android.launcher3.R;

import java.util.ArrayList;
import java.util.List;

/**
 * First step of "Edit icon": use the default icon, the app's own icon, or browse a pack.
 */
public final class EditIconDialog {

    private EditIconDialog() { }

    public static void show(Context context, ComponentName component, CharSequence appLabel) {
        IconPackManager manager = IconPackManager.get(context);
        String current = manager.getOverride(component);
        boolean packSelected = manager.getSelectedPackage() != null;

        List<CharSequence> labels = new ArrayList<>();
        List<Drawable> icons = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        labels.add(context.getString(packSelected
                ? R.string.icon_edit_default_pack : R.string.icon_edit_default));
        icons.add(null);
        actions.add(() -> manager.setOverride(component, null));

        if (packSelected) {
            labels.add(context.getString(R.string.icon_edit_original));
            icons.add(null);
            actions.add(() -> manager.setOverride(component, IconPackManager.OVERRIDE_ORIGINAL));
        }

        for (IconPackManager.PackInfo pack : manager.getInstalledPacks()) {
            labels.add(context.getString(R.string.icon_edit_from_pack, pack.label));
            icons.add(pack.icon);
            actions.add(() -> context.startActivity(IconPickerActivity.newIntent(
                    context, component, pack.packageName, appLabel)));
        }

        int checked = current == null ? 0
                : IconPackManager.OVERRIDE_ORIGINAL.equals(current) && packSelected ? 1 : -1;

        int iconSize = context.getResources().getDimensionPixelSize(R.dimen.folio_pack_icon_size);
        int iconPadding = context.getResources()
                .getDimensionPixelSize(R.dimen.folio_pack_icon_padding);
        Context dialogContext = DrawerTabDialogs.builder(context).getContext();
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(dialogContext,
                android.R.layout.simple_list_item_single_choice, labels) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView row = (TextView) super.getView(position, convertView, parent);
                Drawable icon = icons.get(position);
                if (icon != null) {
                    icon = icon.getConstantState() != null
                            ? icon.getConstantState().newDrawable().mutate() : icon;
                    icon.setBounds(0, 0, iconSize, iconSize);
                }
                row.setCompoundDrawablesRelative(icon, null, null, null);
                row.setCompoundDrawablePadding(iconPadding);
                return row;
            }
        };

        DrawerTabDialogs.builder(context)
                .setTitle(appLabel)
                .setSingleChoiceItems(adapter, checked, (dialog, which) -> {
                    dialog.dismiss();
                    actions.get(which).run();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
