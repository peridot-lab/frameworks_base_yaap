/*
 * Copyright (C) 2020 The Pixel Experience Project
 *               2021 AOSP-Krypton Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.internal.util.yaap;

import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;

import com.android.internal.R;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class PixelPropsUtils {
    private static final String PACKAGE_FINSKY = "com.android.vending";
    private static final String PACKAGE_GMS = "com.google.android.gms";
    private static final String PACKAGE_GPHOTOS = "com.google.android.apps.photos";
    private static final String PROCESS_GMS_UNSTABLE = PACKAGE_GMS + ".unstable";
    private static final String VERSION_PREFIX = "VERSION.";
    private static final String FEATURE_NEXUS_PRELOAD = "com.google.android.apps.photos.NEXUS_PRELOAD";

    private static final Set<String> sPixelFeatures = Set.of(
        "PIXEL_2017_EXPERIENCE",
        "PIXEL_2017_PRELOAD",
        "PIXEL_2018_EXPERIENCE",
        "PIXEL_2018_PRELOAD",
        "PIXEL_2019_EXPERIENCE",
        "PIXEL_2019_MIDYEAR_EXPERIENCE",
        "PIXEL_2019_MIDYEAR_PRELOAD",
        "PIXEL_2019_PRELOAD",
        "PIXEL_2020_EXPERIENCE",
        "PIXEL_2020_MIDYEAR_EXPERIENCE",
        "PIXEL_2021_MIDYEAR_EXPERIENCE"
    );

    private static final Set<String> sTensorFeatures = Set.of(
        "PIXEL_2021_EXPERIENCE",
        "PIXEL_2022_EXPERIENCE",
        "PIXEL_2022_MIDYEAR_EXPERIENCE",
        "PIXEL_2023_EXPERIENCE",
        "PIXEL_2023_MIDYEAR_EXPERIENCE",
        "PIXEL_2024_EXPERIENCE",
        "PIXEL_2024_MIDYEAR_EXPERIENCE"
    );

    private final HashMap<String, Object> certifiedProps;
    private final HashMap<String, Object> pixelXLProps;

    private static final ArrayList<String> finskyProps = new ArrayList<>();
    static {
        finskyProps.add("FINGERPRINT");
        finskyProps.add(VERSION_PREFIX + "SECURITY_PATCH");
        finskyProps.add(VERSION_PREFIX + "DEVICE_INITIAL_SDK_INT");
    }

    private static volatile boolean sIsEnabled = false;
    private static volatile boolean sIsPhotos = false;

    private static PixelPropsUtils sInstance = null;

    public static PixelPropsUtils getInstance(Context context) {
        if (sInstance == null) {
            synchronized (PixelPropsUtils.class) {
                try {
                    // see if we can even read the resource before we cache it forever
                    final String fp = context.getResources().getString(R.string.cert_fp);
                    if (fp == null || fp.isEmpty()) {
                        Logger.d("Can't read props from \"" +
                            context.getPackageName() + "\" context");
                        return null;
                    }
                } catch (Resources.NotFoundException e) {
                    Logger.d("Can't read props from \"" +
                            context.getPackageName() + "\" context");
                    return null;
                }
                sIsEnabled = true;
                sInstance = new PixelPropsUtils(context);
            }
        }
        return sInstance;
    }

    private PixelPropsUtils(Context context) {
        Resources res = context.getResources();

        // init certified props
        final String cert_device = res.getString(R.string.cert_device);
        final String cert_fp = res.getString(R.string.cert_fp);
        final String cert_model = res.getString(R.string.cert_model);
        final String cert_spl = res.getString(R.string.cert_spl);
        final String cert_manufacturer = res.getString(R.string.cert_manufacturer);
        final int cert_sdk = Integer.parseInt(res.getString(R.string.cert_sdk));

        Map<String, Object> tMap = new HashMap<>();
        String[] sections = cert_fp.split("/");
        tMap.put("ID", sections[3]);
        tMap.put("BRAND", sections[0]);
        tMap.put("MANUFACTURER", cert_manufacturer);
        tMap.put("MODEL", cert_model);
        tMap.put("PRODUCT", sections[1]);
        tMap.put("DEVICE", cert_device);
        tMap.put(VERSION_PREFIX + "RELEASE", sections[2].split(":")[1]);
        tMap.put(VERSION_PREFIX + "INCREMENTAL", sections[4].split(":")[0]);
        tMap.put(VERSION_PREFIX + "SECURITY_PATCH", cert_spl);
        tMap.put(VERSION_PREFIX + "DEVICE_INITIAL_SDK_INT", cert_sdk);
        tMap.put("FINGERPRINT", cert_fp);
        // conditionally spoofing if different
        if (Build.IS_DEBUGGABLE)
            tMap.put("IS_DEBUGGABLE", false);
        if (Build.IS_ENG)
            tMap.put("IS_ENG", false);
        if (!Build.IS_USER)
            tMap.put("IS_USER", true);
        if (!Build.TYPE.equals("user"))
            tMap.put("TYPE", "user");
        if (!Build.TAGS.equals("release-keys"))
            tMap.put("TAGS", "release-keys");
        certifiedProps = new HashMap<>(tMap);

        // init Original Pixel XL props for Google Photos
        Map<String, Object> xlMap = new HashMap<>();
        xlMap.put("BRAND", "google");
        xlMap.put("MANUFACTURER", "Google");
        xlMap.put("DEVICE", "marlin");
        xlMap.put("PRODUCT", "marlin");
        xlMap.put("MODEL", "Pixel XL");
        xlMap.put("FINGERPRINT", "google/marlin/marlin:10/QP1A.191005.007.A3/5972272:user/release-keys");
        pixelXLProps = new HashMap<>(xlMap);
    }

    public void setProps(String packageName) {
        if (packageName == null || !sIsEnabled) {
            return;
        }
        final String fp = (String) certifiedProps.get("FINGERPRINT");
        if (fp == null || fp.isEmpty()) {
            // no spoofing if the overlay doesn't exist
            Logger.d("Skipping setProps for \"" + packageName +
                    "\" because FINGERPRINT is empty");
            sIsEnabled = false;
            return;
        }
        Logger.d("Package = " + packageName);
        
        // Google Photos spoofing
        if (PACKAGE_GPHOTOS.equals(packageName)) {
            sIsPhotos = true;
            pixelXLProps.forEach(PixelPropsUtils::setPropValue);
            return;
        }
        
        final boolean isFinsky = PACKAGE_FINSKY.equals(packageName);
        if (!isFinsky && (!PACKAGE_GMS.equals(packageName) ||
                !PROCESS_GMS_UNSTABLE.equals(Application.getProcessName()))) {
            return;
        }
        if (isFinsky) {
            certifiedProps.forEach((key, value) -> {
                if (!finskyProps.contains(key)) return; // ≣ continue
                PixelPropsUtils.setPropValue(key, value);
            });
            return;
        }
        certifiedProps.forEach(PixelPropsUtils::setPropValue);
    }

    private static void setPropValue(String key, Object value) {
        try {
            Logger.d("Setting prop " + key + " to " + value);
            Field field;
            if (key.startsWith(VERSION_PREFIX)) {
                field = Build.VERSION.class.getDeclaredField(
                        key.substring(VERSION_PREFIX.length()));
            } else {
                field = Build.class.getDeclaredField(key);
            }
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Logger.e("Failed to set prop " + key, e);
        }
    }

    public static boolean hasSystemFeature(String name, boolean has) {
        if (sIsPhotos) {
            if (has && (sPixelFeatures.stream().anyMatch(name::contains)
                    || sTensorFeatures.stream().anyMatch(name::contains))) {
                Logger.d("Blocked system feature " + name + " for Google Photos");
                has = false;
            } else if (!has && name.equalsIgnoreCase(FEATURE_NEXUS_PRELOAD)) {
                Logger.d("Enabled system feature " + name + " for Google Photos");
                has = true;
            }
        }
        return has;
    }

    public static boolean getIsEnabled() {
        return sIsEnabled;
    }

    private static class Logger {
        private static final String TAG = "PixelPropsUtils";

        private static void e(String msg, Exception e) {
            Log.e(TAG, msg, e);
        }

        private static void d(String msg) {
            if (!isLoggable()) return;
            Log.d(TAG, msg);
        }

        private static boolean isLoggable() {
            return Log.isLoggable(TAG, Log.DEBUG);
        }
    }
}