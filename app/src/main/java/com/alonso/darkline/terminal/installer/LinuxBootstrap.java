/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.alonso.darkline.terminal.installer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.termux.terminal.TerminalSession;

import java.io.File;
import java.io.IOException;

public final class LinuxBootstrap {

  private LinuxBootstrap() {
  }

  private static final Handler mainHandler = new Handler(Looper.getMainLooper());

  public interface InstallCallback {
    void onProgress(int extractedEntries);

    void onSuccess();

    void onError(Exception e);
  }

  public static File getRootfsDir(Context context, String rootfsPath) {
    if (rootfsPath == null || rootfsPath.isEmpty()) {
      return null;
    }
    return new File(context.getFilesDir(), rootfsPath);
  }

  public static boolean isInstalled(Context context, String rootfsPath, String checkFile) {
    File rootfs = getRootfsDir(context, rootfsPath);
    if (rootfs == null || !rootfs.isDirectory()) {
      return false;
    }
    if (checkFile == null || checkFile.isEmpty()) {
      return true;
    }
    return new File(rootfs, checkFile).exists();
  }

  public static void uninstall(File rootfs, Runnable onDone) {
    new Thread(() -> {
      deleteRecursive(rootfs);
      if (onDone != null)
        mainHandler.post(onDone);
    }).start();
  }

  private static void deleteRecursive(File file) {
    if (file == null || !file.exists())
      return;
    File[] children = file.listFiles();
    if (children != null) {
      for (File child : children)
        deleteRecursive(child);
    }
    file.delete();
  }

  public static void installFromArchiveRootfs(Context context, TerminalSession session,
      File downloadedArchiveRootfs, File rootfsDir, String defaultShell,
      InstallCallback callback) {
    if (rootfsDir == null) {
      if (callback != null) {
        callback.onError(new IOException("rootfsDir is null"));
      }
      return;
    }

    LinuxExtractor.extract(context, session, downloadedArchiveRootfs, rootfsDir, defaultShell,
        new LinuxExtractor.ProgressListener() {
          @Override
          public void onProgress(int extractedEntries) {
            mainHandler.post(() -> {
              if (callback != null)
                callback.onProgress(extractedEntries);
            });
          }

          @Override
          public void onError(String error) {
            mainHandler.post(() -> {
              if (callback != null)
                callback.onError(new IOException(error));
            });
          }

          @Override
          public void onSuccess() {
            mainHandler.post(() -> {
              if (callback != null)
                callback.onSuccess();
            });
          }
        });
  }
}