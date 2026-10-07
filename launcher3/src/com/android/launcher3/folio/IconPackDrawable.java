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

import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;

import androidx.annotation.NonNull;

import com.android.launcher3.icons.BaseIconFactory;
import com.android.launcher3.icons.BitmapInfo;

/**
 * An icon from an icon pack. The launcher normally shrinks non-adaptive icons onto a white
 * shape ("legacy" treatment), which would ruin pack icons that already have their own shape.
 * As a {@link BitmapInfo.Extender}, this re-renders itself as-is and keeps the badge and user
 * information the launcher computed.
 */
public class IconPackDrawable extends DrawableWrapper implements BitmapInfo.Extender {

    public IconPackDrawable(@NonNull Drawable icon) {
        super(icon);
    }

    @NonNull
    @Override
    public BitmapInfo getUpdatedBitmapInfo(@NonNull BitmapInfo info,
            @NonNull BaseIconFactory factory) {
        BitmapInfo raw = factory.createBadgedIconBitmap(getDrawable(),
                new BaseIconFactory.IconOptions()
                        .setWrapNonAdaptiveIcon(false)
                        .setDrawFullBleed(false));
        return new BitmapInfo(
                raw.icon,
                raw.color,
                // Pack icons keep their own shape, so they are never clipped as full-bleed.
                info.getFlags() & ~BitmapInfo.FLAG_FULL_BLEED,
                info.getDefaultIconShape(),
                info.getThemedBitmap(),
                info.getBadgeInfo(),
                info.getDelegateFactory(),
                info.getBadgeProvider());
    }

    @Override
    public void drawForPersistence() { }
}
