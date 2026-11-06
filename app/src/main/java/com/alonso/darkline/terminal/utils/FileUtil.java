/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.alonso.darkline.terminal.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public final class FileUtil {

  private FileUtil() {
  }

  public static void listDir(String dirPath, ArrayList<String> out) {
    File dir = new File(dirPath);
    if (!dir.isDirectory()) return;
    File[] files = dir.listFiles();
    if (files == null) return;
    for (File f : files) {
      out.add(f.getAbsolutePath());
    }
  }

  public static boolean isFile(String path) {
    return new File(path).isFile();
  }

  public static boolean isDirectory(String path) {
    return new File(path).isDirectory();
  }

  public static boolean isExistFile(String path) {
    return new File(path).exists();
  }

  public static String readFile(String path) {
    try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append("\n");
      }
      return sb.toString();
    } catch (Exception e) {
      return "";
    }
  }

  public static boolean writeFile(String path, String content) {
    try (FileWriter writer = new FileWriter(path)) {
      writer.write(content == null ? "" : content);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public static boolean deleteFile(String path) {
    return deleteRecursive(new File(path));
  }

  private static boolean deleteRecursive(File file) {
    if (file == null || !file.exists()) return false;
    if (file.isDirectory()) {
      File[] children = file.listFiles();
      if (children != null) {
        for (File c : children) {
          if (!deleteRecursive(c)) return false;
        }
      }
    }
    return file.delete();
  }

  public static boolean makeDir(String path) {
    return new File(path).mkdirs();
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
    if (parent == null) return baseName;
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