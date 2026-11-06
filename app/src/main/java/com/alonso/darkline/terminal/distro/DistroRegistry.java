/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.alonso.darkline.terminal.distro;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class DistroRegistry {

  private static final String TAG = "DistroRegistry";
  private static final String REGISTRY_URL =
      "https://raw.githubusercontent.com/AlonsoAliaga-dev/Darkline-Packages/main/distros.json";
  private static final String CACHE_FILE = "distro_registry_cache.json";

  public interface FetchCallback {
    void onSuccess(List<DistroInfo> distros);

    void onError(String message);
  }

  private final Context appContext;
  private final OkHttpClient httpClient;
  private final Gson gson;
  private final Handler mainHandler;

  public DistroRegistry(Context context) {
    this.appContext = context.getApplicationContext();
    this.gson = new Gson();
    this.mainHandler = new Handler(Looper.getMainLooper());
    this.httpClient = new OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build();
  }

  public void fetch(FetchCallback callback) {
    new Thread(() -> {
      try {
        Request request = new Request.Builder()
            .url(REGISTRY_URL)
            .addHeader("Cache-Control", "no-cache")
            .build();

        Response response = httpClient.newCall(request).execute();
        if (!response.isSuccessful()) {
          String err = "HTTP " + response.code();
          Log.e(TAG, "Fetch failed: " + err);
          mainHandler.post(() -> callback.onError(err));
          response.close();
          return;
        }

        String body = response.body() == null ? "" : response.body().string();
        response.close();

        if (body.isEmpty()) {
          mainHandler.post(() -> callback.onError("Empty response"));
          return;
        }

        saveCache(body);

        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        List<DistroInfo> all = new ArrayList<>();
        if (root.has("distros")) {
          com.google.gson.JsonArray arr = root.getAsJsonArray("distros");
          for (int i = 0; i < arr.size(); i++) {
            DistroInfo info = gson.fromJson(arr.get(i), DistroInfo.class);
            if (info != null && info.id != null) {
              all.add(info);
            }
          }
        }

        List<DistroInfo> compatible = filterByArchitecture(all);
        mainHandler.post(() -> callback.onSuccess(compatible));

      } catch (Exception e) {
        Log.e(TAG, "Fetch error", e);
        List<DistroInfo> cached = loadCache();
        if (!cached.isEmpty()) {
          List<DistroInfo> compatible = filterByArchitecture(cached);
          mainHandler.post(() -> callback.onSuccess(compatible));
        } else {
          mainHandler.post(() -> callback.onError(e.getMessage()));
        }
      }
    }).start();
  }

  private List<DistroInfo> filterByArchitecture(List<DistroInfo> all) {
    List<DistroInfo> result = new ArrayList<>();
    for (DistroInfo info : all) {
      String url = getCompatibleUrl(info);
      if (url != null) {
        result.add(info);
      }
    }
    return result;
  }

  public static String getCompatibleUrl(DistroInfo info) {
    if (Build.SUPPORTED_ABIS == null) return null;
    for (String abi : Build.SUPPORTED_ABIS) {
      String url = info.getArchiveUrl(abi);
      if (url != null && !url.isEmpty()) {
        return url;
      }
    }
    return null;
  }

  public static String getCompatibleAbi(DistroInfo info) {
    if (Build.SUPPORTED_ABIS == null) return null;
    for (String abi : Build.SUPPORTED_ABIS) {
      String url = info.getArchiveUrl(abi);
      if (url != null && !url.isEmpty()) {
        return abi;
      }
    }
    return null;
  }

  private void saveCache(String body) {
    try (FileWriter writer = new FileWriter(new File(appContext.getFilesDir(), CACHE_FILE))) {
      writer.write(body);
    } catch (IOException ignored) {
    }
  }

  private List<DistroInfo> loadCache() {
    File cacheFile = new File(appContext.getFilesDir(), CACHE_FILE);
    if (!cacheFile.exists()) return new ArrayList<>();
    try (FileReader reader = new FileReader(cacheFile)) {
      JsonObject root = JsonParser.parseString(readAll(reader)).getAsJsonObject();
      List<DistroInfo> list = new ArrayList<>();
      if (root.has("distros")) {
        com.google.gson.JsonArray arr = root.getAsJsonArray("distros");
        for (int i = 0; i < arr.size(); i++) {
          DistroInfo info = gson.fromJson(arr.get(i), DistroInfo.class);
          if (info != null && info.id != null) list.add(info);
        }
      }
      return list;
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  private String readAll(FileReader reader) throws IOException {
    StringBuilder sb = new StringBuilder();
    char[] buf = new char[4096];
    int n;
    while ((n = reader.read(buf)) > 0) {
      sb.append(buf, 0, n);
    }
    return sb.toString();
  }
}