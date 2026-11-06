/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.alonso.darkline.terminal.filemanager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class FileUtils {

  private static final Set<String> TEXT_EXTENSIONS = new HashSet<>(Arrays.asList(
      "txt", "md", "sh", "bash", "zsh", "fish", "py", "js", "ts", "rb", "php",
      "java", "kt", "c", "cpp", "h", "hpp", "conf", "cnf", "ini", "cfg",
      "json", "xml", "yaml", "yml", "toml", "html", "css", "scss",
      "log", "properties", "gitignore", "env", "rc", "profile", "service"
  ));

  private FileUtils() {
  }

  public static boolean isTextFile(File file) {
    if (file == null || file.isDirectory()) return false;
    if (file.length() > 3 * 1024 * 1024) return false;
    String name = file.getName().toLowerCase();
    int dot = name.lastIndexOf('.');
    if (dot == -1) return true;
    String ext = name.substring(dot + 1);
    return TEXT_EXTENSIONS.contains(ext);
  }

  public static boolean deleteRecursive(File file) {
    if (file == null || !file.exists()) return false;
    if (file.isDirectory()) {
      File[] children = file.listFiles();
      if (children != null) {
        for (File child : children) {
          if (!deleteRecursive(child)) return false;
        }
      }
    }
    return file.delete();
  }

  public static boolean copyRecursive(File src, File dst) {
    if (src == null || !src.exists()) return false;
    try {
      if (src.isDirectory()) {
        if (!dst.exists() && !dst.mkdirs()) return false;
        File[] children = src.listFiles();
        if (children != null) {
          for (File child : children) {
            File newDst = new File(dst, child.getName());
            if (!copyRecursive(child, newDst)) return false;
          }
        }
        return true;
      } else {
        File parent = dst.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (InputStream in = new FileInputStream(src);
            OutputStream out = new FileOutputStream(dst)) {
          byte[] buffer = new byte[8192];
          int len;
          while ((len = in.read(buffer)) != -1) {
            out.write(buffer, 0, len);
          }
        }
        return true;
      }
    } catch (Exception e) {
      return false;
    }
  }

  public static String generateUniqueName(File parent, String baseName) {
    File candidate = new File(parent, baseName);
    if (!candidate.exists()) return baseName;

    String name = baseName;
    String ext = "";
    int dot = baseName.lastIndexOf('.');
    if (dot > 0) {
      name = baseName.substring(0, dot);
      ext = baseName.substring(dot);
    }

    int counter = 1;
    while (true) {
      String newName = name + " (" + counter + ")" + ext;
      if (!new File(parent, newName).exists()) return newName;
      counter++;
    }
  }
}