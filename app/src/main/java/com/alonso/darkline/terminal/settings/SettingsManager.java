/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.alonso.darkline.terminal.settings;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class SettingsManager {

  public static final List<String> PROTECTED_BIND_MOUNTS = Arrays.asList(
      "/dev",
      "/dev/urandom:/dev/random",
      "/proc",
      "/sys");

  private static final List<String> DEFAULT_BIND_MOUNTS = Arrays.asList(
      "/storage/emulated/0:/sdcard",
      "/data/data/com.alonso.darkline.terminal:/darkline-terminal",
      "/storage/emulated/0:/storage/emulated/0");

  private final SharedPreferences prefs;

  public SettingsManager(Context context) {
    prefs = context.getApplicationContext()
        .getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE);
  }

  public boolean isShowExtraKeys() {
    return prefs.getBoolean(SettingsKeys.SHOW_EXTRA_KEYS, SettingsKeys.DEFAULT_SHOW_EXTRA_KEYS);
  }

  public void setShowExtraKeys(boolean value) {
    prefs.edit().putBoolean(SettingsKeys.SHOW_EXTRA_KEYS, value).apply();
  }

  public String getStartupCommand() {
    return prefs.getString(SettingsKeys.STARTUP_COMMAND, SettingsKeys.DEFAULT_STARTUP_COMMAND);
  }

  public void setStartupCommand(String value) {
    prefs.edit().putString(SettingsKeys.STARTUP_COMMAND, value == null ? "" : value).apply();
  }

  public int getAutoCloseMinutes() {
    return prefs.getInt(SettingsKeys.AUTO_CLOSE_MINUTES, SettingsKeys.DEFAULT_AUTO_CLOSE_MINUTES);
  }

  public void setAutoCloseMinutes(int value) {
    prefs.edit().putInt(SettingsKeys.AUTO_CLOSE_MINUTES, Math.max(0, value)).apply();
  }

  public List<String> getBindMounts() {
    String json = prefs.getString(SettingsKeys.BIND_MOUNTS, null);
    if (json == null) {
      return new ArrayList<>(DEFAULT_BIND_MOUNTS);
    }
    try {
      JSONArray array = new JSONArray(json);
      List<String> result = new ArrayList<>();
      for (int i = 0; i < array.length(); i++) {
        String item = array.optString(i, null);
        if (item != null && !item.isEmpty() && !PROTECTED_BIND_MOUNTS.contains(item)) {
          result.add(item);
        }
      }
      return result;
    } catch (JSONException e) {
      return new ArrayList<>(DEFAULT_BIND_MOUNTS);
    }
  }

  public void setBindMounts(List<String> mounts) {
    JSONArray array = new JSONArray();
    if (mounts != null) {
      for (String mount : mounts) {
        if (mount != null && !mount.isEmpty() && !PROTECTED_BIND_MOUNTS.contains(mount)) {
          array.put(mount);
        }
      }
    }
    prefs.edit().putString(SettingsKeys.BIND_MOUNTS, array.toString()).apply();
  }

  public static List<String> getDefaultBindMounts() {
    return new ArrayList<>(DEFAULT_BIND_MOUNTS);
  }

  public String getGroqKey() {
    return prefs.getString(SettingsKeys.AI_GROQ_KEY, "");
  }

  public void setGroqKey(String key) {
    prefs.edit().putString(SettingsKeys.AI_GROQ_KEY, key == null ? "" : key).apply();
  }

  public String getGroqModel() {
    return prefs.getString(SettingsKeys.AI_GROQ_MODEL, SettingsKeys.DEFAULT_GROQ_MODEL);
  }

  public void setGroqModel(String model) {
    prefs.edit().putString(SettingsKeys.AI_GROQ_MODEL, model).apply();
  }

  public String getActiveApiKey() {
    return getGroqKey();
  }

  public String getActiveModel() {
    return getGroqModel();
  }

  public String getLoginShellOverride() {
    return prefs.getString(SettingsKeys.LOGIN_SHELL_OVERRIDE, "");
  }

  public void setLoginShellOverride(String value) {
    prefs.edit().putString(SettingsKeys.LOGIN_SHELL_OVERRIDE, value == null ? "" : value).apply();
  }
}