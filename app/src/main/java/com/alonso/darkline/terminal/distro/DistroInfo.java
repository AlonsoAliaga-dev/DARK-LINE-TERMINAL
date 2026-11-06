/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.alonso.darkline.terminal.distro;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

public class DistroInfo {

  @SerializedName("id")
  public String id;

  @SerializedName("name")
  public String name;

  @SerializedName("version")
  public String version;

  @SerializedName("description")
  public String description;

  @SerializedName("archives")
  public Map<String, String> archives;

  @SerializedName("size_mb")
  public Map<String, Integer> sizeMb;

  @SerializedName("default_shell")
  public String defaultShell;

  @SerializedName("login_command")
  public String loginCommand;

  @SerializedName("rootfs_path")
  public String rootfsPath;

  public String getArchiveUrl(String abi) {
    if (archives == null) return null;
    return archives.get(abi);
  }

  public int getSizeMb(String abi) {
    if (sizeMb == null) return 0;
    Integer v = sizeMb.get(abi);
    return v == null ? 0 : v;
  }

  public String getDisplayName() {
    if (version == null || version.isEmpty()) return name;
    return name + " " + version;
  }

  public String getDefaultShell() {
    if (defaultShell == null || defaultShell.isEmpty()) return "/bin/sh";
    return defaultShell;
  }

  public String getLoginCommand() {
    if (loginCommand == null) return "";
    return loginCommand;
  }

  public String getRootfsPath() {
    if (rootfsPath == null || rootfsPath.isEmpty()) {
      return "Linux/" + id;
    }
    return rootfsPath;
  }
}