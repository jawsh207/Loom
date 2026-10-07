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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.LauncherFiles;
import com.android.launcher3.dagger.LauncherComponentProvider;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.icons.IconChangeTracker;
import com.android.launcher3.pm.UserCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Keeps track of the selected icon pack and supplies its icons to LauncherIconProvider.
 * Changing the pack (or updating/removing the pack app) re-renders every icon.
 */
public final class IconPackManager {

    public static final String PREF_ICON_PACK = "pref_icon_pack";
    public static final String PREF_FALLBACK = "pref_icon_pack_fallback";

    /** Per-app override value meaning "the app's own icon, even with a pack selected". */
    public static final String OVERRIDE_ORIGINAL = "!original";
    private static final String OVERRIDES_PREFS = "folio_icon_overrides";

    /** Intents icon packs declare so launchers can find them. */
    private static final String[] PACK_ACTIONS = {
            "org.adw.launcher.THEMES",
            "org.adw.launcher.icons.ACTION_PICK_ICON",
            "com.novalauncher.THEME",
            "com.teslacoilsw.launcher.THEME",
            "com.gau.go.launcherex.theme",
            "com.anddoes.launcher.THEME",
            "com.fede.launcher.THEME_ICONPACK",
    };

    /** An installed pack, for the picker. */
    public static final class PackInfo {
        public final String packageName;
        public final CharSequence label;
        public final Drawable icon;

        PackInfo(String packageName, CharSequence label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }

    private static IconPackManager sInstance;

    private final Context mContext;
    private final SharedPreferences mPrefs;
    /** Component (flattened) to "packPackage/drawableName" or {@link #OVERRIDE_ORIGINAL}. */
    private final SharedPreferences mOverrides;
    /** Packs loaded for per-app overrides, by package. */
    private final Map<String, IconPack> mOverridePacks = new HashMap<>();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    @Nullable private IconPack mPack;
    private boolean mPackLoaded;

    public static synchronized IconPackManager get(Context context) {
        if (sInstance == null) {
            sInstance = new IconPackManager(context.getApplicationContext());
        }
        return sInstance;
    }

    private IconPackManager(Context context) {
        mContext = context;
        mPrefs = context.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY,
                Context.MODE_PRIVATE);
        mOverrides = context.getSharedPreferences(OVERRIDES_PREFS, Context.MODE_PRIVATE);

        // Daily calendar icons: redraw the pack's calendar apps when the date changes.
        IntentFilter dateFilter = new IntentFilter();
        dateFilter.addAction(Intent.ACTION_DATE_CHANGED);
        dateFilter.addAction(Intent.ACTION_TIME_CHANGED);
        dateFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                IconPack pack = getActivePack();
                if (pack != null) {
                    for (String pkg : pack.getCalendarPackages()) {
                        notifyAppIconChanged(pkg);
                    }
                }
            }
        }, dateFilter, Context.RECEIVER_NOT_EXPORTED);

        // Re-render icons when the selected pack app is updated or uninstalled.
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        filter.addDataScheme("package");
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                String pkg = intent.getData() == null ? null
                        : intent.getData().getSchemeSpecificPart();
                if (pkg == null) {
                    return;
                }
                synchronized (IconPackManager.this) {
                    mOverridePacks.remove(pkg);
                }
                if (pkg.equals(getSelectedPackage())) {
                    if (Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(intent.getAction())) {
                        mPrefs.edit().remove(PREF_ICON_PACK).commit();
                    }
                    onChanged();
                }
            }
        }, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    /** Selected pack's package name, or null for the system icons. */
    @Nullable
    public String getSelectedPackage() {
        return mPrefs.getString(PREF_ICON_PACK, null);
    }

    public boolean isFallbackEnabled() {
        return mPrefs.getBoolean(PREF_FALLBACK, true);
    }

    /** Selects a pack (null for system icons) and re-renders all icons. Main thread. */
    public void setSelectedPackage(@Nullable String pkg) {
        mPrefs.edit().putString(PREF_ICON_PACK, pkg).commit();
        onChanged();
    }

    /** Called after the "style other icons" switch changes. Main thread. */
    public void setFallbackEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(PREF_FALLBACK, enabled).commit();
        onChanged();
    }

    /** Installed icon packs, sorted by name. */
    public List<PackInfo> getInstalledPacks() {
        PackageManager pm = mContext.getPackageManager();
        Map<String, ResolveInfo> found = new LinkedHashMap<>();
        for (String action : PACK_ACTIONS) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(action),
                    PackageManager.GET_META_DATA)) {
                found.putIfAbsent(ri.activityInfo.packageName, ri);
            }
        }
        List<PackInfo> packs = new ArrayList<>();
        for (String pkg : found.keySet()) {
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                packs.add(new PackInfo(pkg, pm.getApplicationLabel(ai), pm.getApplicationIcon(ai)));
            } catch (PackageManager.NameNotFoundException ignored) { }
        }
        Collections.sort(packs, (a, b) ->
                String.valueOf(a.label).compareToIgnoreCase(String.valueOf(b.label)));
        return packs;
    }

    /** Label of the selected pack, or null for system icons. */
    @Nullable
    public CharSequence getSelectedLabel() {
        String pkg = getSelectedPackage();
        if (pkg == null) {
            return null;
        }
        PackageManager pm = mContext.getPackageManager();
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /**
     * Identifies the current pack setup for the icon cache. When it changes (pack, its version,
     * or the fallback switch) cached icons are rendered again.
     */
    @NonNull
    public String getStateCode() {
        IconPack pack = getActivePack();
        if (pack == null) {
            return "";
        }
        return "|iconpack:" + pack.packageName + ":" + pack.versionCode
                + (isFallbackEnabled() ? ":fb" : "");
    }

    /**
     * Icon for an activity: the pack's own icon if it has one, else the app's icon in the
     * pack's style if the fallback is on, else null to use the system icon.
     */
    @Nullable
    public Drawable getIcon(ComponentInfo info, int density, Supplier<Drawable> systemIcon) {
        ComponentName cn = new ComponentName(info.packageName, info.name);

        // An icon picked for this app wins over everything else.
        String override = getOverride(cn);
        if (OVERRIDE_ORIGINAL.equals(override)) {
            return null;
        }
        if (override != null) {
            Drawable picked = loadOverride(override, density);
            if (picked != null) {
                return new IconPackDrawable(picked);
            }
        }

        IconPack pack = getActivePack();
        if (pack == null) {
            return null;
        }
        Drawable d = pack.getIcon(cn, density);
        if (d == null) {
            d = pack.getIconForPackage(info.packageName, density);
        }
        if (d == null && isFallbackEnabled() && pack.hasFallbackStyle()) {
            d = pack.compose(systemIcon.get(), density, cn.hashCode());
        }
        return d == null ? null : new IconPackDrawable(d);
    }

    /** Icon for a whole app (used e.g. while an app is installing). */
    @Nullable
    public Drawable getIcon(ApplicationInfo info, int density, Supplier<Drawable> systemIcon) {
        IconPack pack = getActivePack();
        if (pack == null) {
            return null;
        }
        Drawable d = pack.getIconForPackage(info.packageName, density);
        if (d == null && isFallbackEnabled() && pack.hasFallbackStyle()) {
            d = pack.compose(systemIcon.get(), density, info.packageName.hashCode());
        }
        return d == null ? null : new IconPackDrawable(d);
    }

    // Per-app icons

    /** The icon picked for this app ("pack/drawable" or {@link #OVERRIDE_ORIGINAL}), or null. */
    @Nullable
    public String getOverride(ComponentName cn) {
        return mOverrides.getString(cn.flattenToString(), null);
    }

    /**
     * Picks an icon for one app: {@code "packPackage/drawableName"}, {@link #OVERRIDE_ORIGINAL},
     * or null to go back to the default. Only that app's icon is redrawn.
     */
    public void setOverride(ComponentName cn, @Nullable String value) {
        if (value == null) {
            mOverrides.edit().remove(cn.flattenToString()).commit();
        } else {
            mOverrides.edit().putString(cn.flattenToString(), value).commit();
        }
        notifyAppIconChanged(cn.getPackageName());
    }

    /** A pack by package, for browsing its icons. Null if it isn't installed. */
    @Nullable
    synchronized IconPack getPack(String pkg) {
        IconPack active = getActivePack();
        if (active != null && active.packageName.equals(pkg)) {
            return active;
        }
        IconPack pack = mOverridePacks.get(pkg);
        if (pack == null) {
            pack = IconPack.load(mContext, pkg);
            if (pack != null) {
                mOverridePacks.put(pkg, pack);
            }
        }
        return pack;
    }

    @Nullable
    private Drawable loadOverride(String value, int density) {
        int slash = value.indexOf('/');
        if (slash <= 0) {
            return null;
        }
        IconPack pack = getPack(value.substring(0, slash));
        return pack == null ? null : pack.getDrawableByName(value.substring(slash + 1), density);
    }

    // Daily calendar icons

    /**
     * Extra cache state for an app whose pack icon changes daily, so a cached icon from an
     * earlier day is redrawn (e.g. after the phone was off at midnight). Null for other apps.
     */
    @Nullable
    public String getDailyState(String pkg) {
        IconPack pack = getActivePack();
        if (pack == null || !pack.isCalendarPackage(pkg)) {
            return null;
        }
        return "day:" + java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR);
    }

    /** Redraws one package's icons for every user profile. */
    private void notifyAppIconChanged(String pkg) {
        mMainHandler.post(() -> {
            IconChangeTracker tracker =
                    LauncherComponentProvider.get(mContext).getIconChangeTracker();
            for (UserHandle user : UserCache.INSTANCE.get(mContext).getUserProfiles()) {
                tracker.notifyIconChanged(pkg, user);
            }
        });
    }

    @Nullable
    private synchronized IconPack getActivePack() {
        if (!mPackLoaded) {
            String pkg = getSelectedPackage();
            mPack = pkg == null ? null : IconPack.load(mContext, pkg);
            mPackLoaded = true;
        }
        return mPack;
    }

    private void onChanged() {
        synchronized (this) {
            mPack = null;
            mPackLoaded = false;
        }
        // Same path as a theme change: clears the icon cache and reloads the launcher.
        mMainHandler.post(() -> ThemeManager.INSTANCE.get(mContext).notifyIconPackChanged());
    }
}
