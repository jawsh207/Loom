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
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

import androidx.annotation.Nullable;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An installed icon pack in the common "appfilter" format used by ADW, Nova and most packs on
 * F-Droid and elsewhere. Maps components to drawables in the pack, and optionally provides an
 * icon background, mask and overlay used to style apps the pack doesn't cover.
 */
final class IconPack {

    private static final String TAG = "FolioIconPack";

    /** Pixel size for icons composed from the pack's background, mask and overlay. */
    private static final int COMPOSED_SIZE = 256;

    final String packageName;
    final long versionCode;

    private final Resources mRes;
    private final Map<ComponentName, String> mComponents = new HashMap<>();
    /** First drawable listed for each package, used for package-level icons. */
    private final Map<String, String> mPackages = new HashMap<>();
    /** Calendar apps whose icon is {@code prefix + day of month} (1-31). */
    private final Map<ComponentName, String> mCalendars = new HashMap<>();
    private final Set<String> mCalendarPackages = new HashSet<>();
    private boolean mHasAppFilter;
    private final List<String> mBacks = new ArrayList<>();
    @Nullable private String mMask;
    @Nullable private String mUpon;
    private float mScale = 1f;

    private IconPack(String packageName, long versionCode, Resources res) {
        this.packageName = packageName;
        this.versionCode = versionCode;
        mRes = res;
    }

    /** Loads and parses a pack. Returns null if it isn't installed. */
    @Nullable
    static IconPack load(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        try {
            long version = pm.getPackageInfo(packageName, 0).getLongVersionCode();
            Resources res = pm.getResourcesForApplication(packageName);
            IconPack pack = new IconPack(packageName, version, res);
            pack.mHasAppFilter = pack.parseAppFilter();
            return pack;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /** True if the pack maps apps to icons; packs without it can still be browsed per app. */
    boolean hasAppFilter() {
        return mHasAppFilter;
    }

    /**
     * The pack's own drawable for this component, or null if the pack doesn't include it.
     * Calendar apps get today's date icon when the pack has one.
     */
    @Nullable
    Drawable getIcon(ComponentName component, int density) {
        String prefix = mCalendars.get(component);
        if (prefix != null) {
            Drawable today = loadDrawable(prefix + Calendar.getInstance().get(
                    Calendar.DAY_OF_MONTH), density);
            if (today != null) {
                return today;
            }
        }
        return loadDrawable(mComponents.get(component), density);
    }

    /** True when the pack has a day-of-month icon set for some activity of this package. */
    boolean isCalendarPackage(String pkg) {
        return mCalendarPackages.contains(pkg);
    }

    /** Packages that get a day-of-month icon from this pack. */
    Set<String> getCalendarPackages() {
        return mCalendarPackages;
    }

    /** Name of the drawable the pack assigns to this component, or null. */
    @Nullable
    String getMappedName(ComponentName component) {
        return mComponents.get(component);
    }

    /** Any drawable in the pack, by resource name. */
    @Nullable
    Drawable getDrawableByName(String name, int density) {
        return loadDrawable(name, density);
    }

    /**
     * Every icon the pack offers, for picking one by hand. Uses the pack's drawable.xml when it
     * has one (the list packs provide for this), otherwise the icons named in its appfilter.
     */
    List<String> listDrawables() {
        Set<String> names = new LinkedHashSet<>();
        int xmlId = mRes.getIdentifier("drawable", "xml", packageName);
        try {
            if (xmlId != 0) {
                try (android.content.res.XmlResourceParser parser = mRes.getXml(xmlId)) {
                    collectDrawableNames(parser, names);
                }
            } else {
                try (InputStream in = mRes.getAssets().open("drawable.xml")) {
                    XmlPullParser parser = Xml.newPullParser();
                    parser.setInput(in, null);
                    collectDrawableNames(parser, names);
                }
            }
        } catch (Exception e) {
            // No drawable.xml: fall back to the appfilter below.
        }
        if (names.isEmpty()) {
            List<String> mapped = new ArrayList<>(new HashSet<>(mComponents.values()));
            Collections.sort(mapped);
            names.addAll(mapped);
        }
        // Keep only names that resolve to a drawable in the pack.
        List<String> result = new ArrayList<>();
        for (String name : names) {
            if (mRes.getIdentifier(name, "drawable", packageName) != 0) {
                result.add(name);
            }
        }
        return result;
    }

    private static void collectDrawableNames(XmlPullParser parser, Set<String> out)
            throws Exception {
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (type == XmlPullParser.START_TAG && "item".equals(parser.getName())) {
                String name = parser.getAttributeValue(null, "drawable");
                if (!TextUtils.isEmpty(name)) {
                    out.add(name);
                }
            }
        }
    }

    /** The pack's drawable for any component of this package, or null. */
    @Nullable
    Drawable getIconForPackage(String pkg, int density) {
        return loadDrawable(mPackages.get(pkg), density);
    }

    /** True when the pack can restyle icons it doesn't include. */
    boolean hasFallbackStyle() {
        return !mBacks.isEmpty() || mMask != null || mUpon != null;
    }

    /**
     * Draws an app's own icon in the pack's style: scaled onto the pack's background, cut by its
     * mask, with its overlay on top. {@code seed} picks among several backgrounds consistently.
     */
    @Nullable
    Drawable compose(Drawable original, int density, int seed) {
        Drawable back = mBacks.isEmpty() ? null
                : loadDrawable(mBacks.get(Math.floorMod(seed, mBacks.size())), density);
        Drawable mask = loadDrawable(mMask, density);
        Drawable upon = loadDrawable(mUpon, density);
        if (back == null && mask == null && upon == null) {
            return null;
        }
        int size = COMPOSED_SIZE;
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        if (back != null) {
            back.setBounds(0, 0, size, size);
            back.draw(canvas);
        }

        // The app's icon, scaled, on its own layer so the mask only cuts the icon.
        Bitmap fg = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas fgCanvas = new Canvas(fg);
        int inset = Math.round(size * (1f - mScale) / 2f);
        original.setBounds(inset, inset, size - inset, size - inset);
        original.draw(fgCanvas);
        if (mask != null) {
            // Opaque parts of the mask erase the icon (appfilter convention).
            Bitmap maskBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            mask.setBounds(0, 0, size, size);
            mask.draw(new Canvas(maskBitmap));
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
            fgCanvas.drawBitmap(maskBitmap, 0, 0, paint);
            maskBitmap.recycle();
        }
        canvas.drawBitmap(fg, 0, 0, null);
        fg.recycle();

        if (upon != null) {
            upon.setBounds(0, 0, size, size);
            upon.draw(canvas);
        }
        return new BitmapDrawable(mRes, out);
    }

    @Nullable
    private Drawable loadDrawable(@Nullable String name, int density) {
        if (TextUtils.isEmpty(name)) {
            return null;
        }
        int id = mRes.getIdentifier(name, "drawable", packageName);
        if (id == 0) {
            return null;
        }
        try {
            return mRes.getDrawableForDensity(id, density, null);
        } catch (Resources.NotFoundException e) {
            return null;
        }
    }

    /** Reads res/xml/appfilter.xml, or assets/appfilter.xml for packs that ship it there. */
    private boolean parseAppFilter() {
        int xmlId = mRes.getIdentifier("appfilter", "xml", packageName);
        try {
            if (xmlId != 0) {
                try (android.content.res.XmlResourceParser parser = mRes.getXml(xmlId)) {
                    parse(parser);
                }
                return true;
            }
            try (InputStream in = mRes.getAssets().open("appfilter.xml")) {
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(in, null);
                parse(parser);
                return true;
            }
        } catch (java.io.FileNotFoundException e) {
            return false;
        } catch (Exception e) {
            Log.w(TAG, "Unable to read appfilter of " + packageName, e);
            return !mComponents.isEmpty();
        }
    }

    private void parse(XmlPullParser parser) throws Exception {
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (type != XmlPullParser.START_TAG) {
                continue;
            }
            switch (parser.getName()) {
                case "item" -> {
                    ComponentName cn = parseComponent(parser.getAttributeValue(null, "component"));
                    String drawable = parser.getAttributeValue(null, "drawable");
                    if (cn != null && !TextUtils.isEmpty(drawable)) {
                        mComponents.putIfAbsent(cn, drawable);
                        mPackages.putIfAbsent(cn.getPackageName(), drawable);
                    }
                }
                case "calendar" -> {
                    ComponentName cn = parseComponent(parser.getAttributeValue(null, "component"));
                    String prefix = parser.getAttributeValue(null, "prefix");
                    if (cn != null && !TextUtils.isEmpty(prefix)) {
                        mCalendars.putIfAbsent(cn, prefix);
                        mCalendarPackages.add(cn.getPackageName());
                    }
                }
                case "iconback" -> {
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        if (parser.getAttributeName(i).startsWith("img")) {
                            mBacks.add(parser.getAttributeValue(i));
                        }
                    }
                }
                case "iconmask" -> mMask = parser.getAttributeValue(null, "img1");
                case "iconupon" -> mUpon = parser.getAttributeValue(null, "img1");
                case "scale" -> {
                    try {
                        float f = Float.parseFloat(parser.getAttributeValue(null, "factor"));
                        if (f > 0f && f <= 1f) {
                            mScale = f;
                        }
                    } catch (NumberFormatException | NullPointerException ignored) { }
                }
                default -> { }
            }
        }
    }

    /** Parses {@code ComponentInfo{com.example/com.example.Main}}; returns null if malformed. */
    @Nullable
    static ComponentName parseComponent(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String s = value.trim();
        if (s.startsWith("ComponentInfo{") && s.endsWith("}")) {
            s = s.substring("ComponentInfo{".length(), s.length() - 1);
        }
        return s.contains("/") ? ComponentName.unflattenFromString(s) : null;
    }
}
