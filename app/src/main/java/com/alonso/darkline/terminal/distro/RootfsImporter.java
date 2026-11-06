/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.alonso.darkline.terminal.distro;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.alonso.darkline.terminal.client.DarkLineSessionClient;
import com.alonso.darkline.terminal.installer.LinuxExtractor;
import com.alonso.darkline.terminal.shell.AndroidShellSession;
import com.termux.terminal.TerminalSession;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class RootfsImporter {

  private static final String TAG = "RootfsImporter";

  public interface ImportListener {
    void onSuccess();

    void onError(String message);
  }

  private RootfsImporter() {
  }

  public static void importRootfs(Context context, TerminalSession session, Uri archiveUri,
      ImportRootfsDialog.ImportConfig config, ImportListener listener) {

    Handler mainHandler = new Handler(Looper.getMainLooper());

    new Thread(() -> {
      try {
        File importDir = new File(context.getFilesDir(), "Import");
        if (!importDir.exists() && !importDir.mkdirs()) {
          mainHandler.post(() -> listener.onError("Cannot create import directory"));
          return;
        }

        File localArchive = new File(importDir, "import_rootfs.tar.gz");
        if (localArchive.exists()) localArchive.delete();

        try (InputStream input = context.getContentResolver().openInputStream(archiveUri);
            FileOutputStream output = new FileOutputStream(localArchive)) {

          if (input == null) {
            mainHandler.post(() -> listener.onError("Cannot open archive"));
            return;
          }

          byte[] buffer = new byte[8192];
          int bytesRead;
          long total = 0;
          while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
            total += bytesRead;
          }
          Log.d(TAG, "Copied " + total + " bytes");
        }

        if (!localArchive.exists() || localArchive.length() == 0) {
          mainHandler.post(() -> listener.onError("Archive is empty"));
          return;
        }

        File rootfsDir = new File(context.getFilesDir(), config.rootfsPath);

        if (session == null) {
          mainHandler.post(() -> listener.onError("No active session"));
          return;
        }

        LinuxExtractor.extract(context, session, localArchive, rootfsDir, config.shell,
            new LinuxExtractor.ProgressListener() {
              @Override
              public void onProgress(int extractedEntries) {
              }

              @Override
              public void onError(String error) {
                if (localArchive.exists()) localArchive.delete();
                mainHandler.post(() -> listener.onError(error));
              }

              @Override
              public void onSuccess() {
                if (localArchive.exists()) localArchive.delete();

                DistroManager manager = new DistroManager(context);
                manager.saveCustomInstalled(config);

                mainHandler.post(listener::onSuccess);
              }
            });

      } catch (Exception e) {
        Log.e(TAG, "Import failed", e);
        mainHandler.post(() -> listener.onError("Import error: " + e.getMessage()));
      }
    }).start();
  }
}