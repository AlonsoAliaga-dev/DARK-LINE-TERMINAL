/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.alonso.darkline.terminal.settings;

public final class SettingsKeys {

  public static final String PREFS_NAME = "darkline_settings";

  public static final String SHOW_EXTRA_KEYS = "show_extra_keys";
  public static final String BIND_MOUNTS = "bind_mounts";
  public static final String STARTUP_COMMAND = "startup_command";
  public static final String AUTO_CLOSE_MINUTES = "auto_close_minutes";

  public static final String AI_GROQ_KEY = "ai_groq_key";
  public static final String AI_GROQ_MODEL = "ai_groq_model";

  public static final String INSTALLED_DISTRO_ID = "installed_distro_id";
  public static final String INSTALLED_DISTRO_NAME = "installed_distro_name";
  public static final String INSTALLED_DISTRO_SHELL = "installed_distro_shell";
  public static final String INSTALLED_DISTRO_LOGIN = "installed_distro_login";
  public static final String INSTALLED_DISTRO_PATH = "installed_distro_path";

  public static final String LOGIN_SHELL_OVERRIDE = "login_shell_override";

  public static final boolean DEFAULT_SHOW_EXTRA_KEYS = true;
  public static final String DEFAULT_STARTUP_COMMAND = "";
  public static final int DEFAULT_AUTO_CLOSE_MINUTES = 0;
  public static final String DEFAULT_GROQ_MODEL = "openai/gpt-oss-20b";

  private SettingsKeys() {
  }
}