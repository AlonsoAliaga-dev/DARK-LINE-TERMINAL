/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.alonso.darkline.terminal.distro;

import android.content.Context;
import android.content.SharedPreferences;

import com.alonso.darkline.terminal.settings.SettingsKeys;

import java.io.File;

public final class DistroManager {

  private final SharedPreferences prefs;
  private final Context appContext;

  public DistroManager(Context context) {
    this.appContext = context.getApplicationContext();
    this.prefs = appContext.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE);
  }

  public boolean isInstalled() {
    String id = prefs.getString(SettingsKeys.INSTALLED_DISTRO_ID, "");
    return id != null && !id.isEmpty();
  }

  public String getInstalledId() {
    return prefs.getString(SettingsKeys.INSTALLED_DISTRO_ID, "");
  }

  public String getInstalledName() {
    return prefs.getString(SettingsKeys.INSTALLED_DISTRO_NAME, "");
  }

  public String getInstalledShell() {
    String shell = prefs.getString(SettingsKeys.INSTALLED_DISTRO_SHELL, "");
    if (shell == null || shell.isEmpty()) return "/bin/sh";
    return shell;
  }

  public String getLoginShellOverride() {
    String override = prefs.getString(SettingsKeys.LOGIN_SHELL_OVERRIDE, "");
    return override == null ? "" : override;
  }

  public String getEffectiveShell() {
    String override = getLoginShellOverride();
    if (override != null && !override.trim().isEmpty()) {
      return override.trim();
    }
    return getInstalledShell();
  }

  public String getInstalledLoginCommand() {
    String cmd = prefs.getString(SettingsKeys.INSTALLED_DISTRO_LOGIN, "");
    return cmd == null ? "" : cmd;
  }

  public String getInstalledRootfsPath() {
    String path = prefs.getString(SettingsKeys.INSTALLED_DISTRO_PATH, "");
    if (path == null || path.isEmpty()) return null;
    return path;
  }

  public File getInstalledRootfsDir() {
    String path = getInstalledRootfsPath();
    if (path == null) return null;
    return new File(appContext.getFilesDir(), path);
  }

  public void saveInstalled(DistroInfo info) {
    prefs.edit()
        .putString(SettingsKeys.INSTALLED_DISTRO_ID, info.id)
        .putString(SettingsKeys.INSTALLED_DISTRO_NAME, info.getDisplayName())
        .putString(SettingsKeys.INSTALLED_DISTRO_SHELL, info.getDefaultShell())
        .putString(SettingsKeys.INSTALLED_DISTRO_LOGIN, info.getLoginCommand())
        .putString(SettingsKeys.INSTALLED_DISTRO_PATH, info.getRootfsPath())
        .apply();
  }

  public void saveCustomInstalled(ImportRootfsDialog.ImportConfig config) {
    String displayName = config.name;
    if (config.version != null && !config.version.isEmpty()) {
      displayName = config.name + " " + config.version;
    }

    prefs.edit()
        .putString(SettingsKeys.INSTALLED_DISTRO_ID, "custom_" + System.currentTimeMillis())
        .putString(SettingsKeys.INSTALLED_DISTRO_NAME, displayName)
        .putString(SettingsKeys.INSTALLED_DISTRO_SHELL, config.shell)
        .putString(SettingsKeys.INSTALLED_DISTRO_LOGIN, config.loginCommand)
        .putString(SettingsKeys.INSTALLED_DISTRO_PATH, config.rootfsPath)
        .apply();
  }

  public void clear() {
    prefs.edit()
        .remove(SettingsKeys.INSTALLED_DISTRO_ID)
        .remove(SettingsKeys.INSTALLED_DISTRO_NAME)
        .remove(SettingsKeys.INSTALLED_DISTRO_SHELL)
        .remove(SettingsKeys.INSTALLED_DISTRO_LOGIN)
        .remove(SettingsKeys.INSTALLED_DISTRO_PATH)
        .apply();
  }

  public boolean isRootfsPresent() {
    File dir = getInstalledRootfsDir();
    if (dir == null) return false;
    return dir.isDirectory();
  }
}