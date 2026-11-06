/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.alonso.darkline.terminal.filemanager;

import java.io.File;

public class FileItem {

  public enum Type {
    FOLDER,
    FILE,
    SCRIPT,
    CONFIG,
    IMAGE,
    ARCHIVE,
    UNKNOWN
  }

  public final File file;
  public final String name;
  public final String path;
  public final boolean isDirectory;
  public final long size;
  public final long lastModified;
  public final Type type;

  public int depth = 0;
  public boolean isExpanded = false;
  public boolean isLoaded = false;

  public FileItem(File file) {
    this.file = file;
    this.name = file.getName();
    this.path = file.getAbsolutePath();
    this.isDirectory = file.isDirectory();
    this.size = file.length();
    this.lastModified = file.lastModified();
    this.type = detectType(file);
  }

  private static Type detectType(File file) {
    if (file.isDirectory()) return Type.FOLDER;
    String name = file.getName().toLowerCase();
    if (name.endsWith(".sh") || name.endsWith(".bash") || name.endsWith(".zsh")
        || name.endsWith(".py") || name.endsWith(".js") || name.endsWith(".rb")
        || name.endsWith(".pl") || name.endsWith(".php")) {
      return Type.SCRIPT;
    }
    if (name.endsWith(".conf") || name.endsWith(".cnf") || name.endsWith(".ini")
        || name.endsWith(".cfg") || name.endsWith(".json") || name.endsWith(".yaml")
        || name.endsWith(".yml") || name.endsWith(".xml") || name.endsWith(".toml")
        || name.endsWith(".properties") || name.endsWith(".env")) {
      return Type.CONFIG;
    }
    if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
        || name.endsWith(".gif") || name.endsWith(".svg") || name.endsWith(".webp")
        || name.endsWith(".bmp") || name.endsWith(".ico")) {
      return Type.IMAGE;
    }
    if (name.endsWith(".zip") || name.endsWith(".tar") || name.endsWith(".gz")
        || name.endsWith(".tar.gz") || name.endsWith(".bz2") || name.endsWith(".xz")
        || name.endsWith(".rar") || name.endsWith(".7z") || name.endsWith(".tgz")) {
      return Type.ARCHIVE;
    }
    return Type.FILE;
  }

  public String getFormattedSize() {
    if (isDirectory) return "";
    long bytes = size;
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
    if (bytes < 1024 * 1024 * 1024)
      return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024));
    return String.format(java.util.Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024));
  }

  public String getFormattedDate() {
    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",
        java.util.Locale.US);
    return sdf.format(new java.util.Date(lastModified));
  }
}