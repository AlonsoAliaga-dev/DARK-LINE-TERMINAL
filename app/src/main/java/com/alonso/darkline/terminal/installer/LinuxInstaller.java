/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.alonso.darkline.terminal.installer;

import android.content.Context;

import com.termux.terminal.TerminalSession;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class LinuxInstaller {

  private LinuxInstaller() {
  }

  public interface InstallListener {
    void onDownloadProgress(int percent);

    void onExtractProgress(int extractedEntries);

    void onSuccess();

    void onError(String message);
  }

  private static volatile boolean cancelled = false;

  public static void installFrom(Context context, TerminalSession session, String url,
      File rootfsDir, String defaultShell, InstallListener listener) {
    cancelled = false;

    new Thread(() -> {
      HttpURLConnection conn = null;
      FileOutputStream output = null;
      InputStream input = null;

      try {
        File downloadDir = new File(context.getFilesDir(), "Download");
        if (!downloadDir.exists()) {
          downloadDir.mkdirs();
        }

        final File downloadedFile = new File(downloadDir, "rootfs.tar.gz");

        if (downloadedFile.exists()) {
          downloadedFile.delete();
        }

        if (listener != null) {
          listener.onDownloadProgress(0);
        }

        URL urlObj = new URL(url);
        conn = (HttpURLConnection) urlObj.openConnection();
        conn.setConnectTimeout(60000);
        conn.setReadTimeout(120000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");

        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
          if (listener != null) {
            listener.onError("Download failed: " + conn.getResponseCode());
          }
          return;
        }

        int totalSize = conn.getContentLength();
        input = conn.getInputStream();
        output = new FileOutputStream(downloadedFile);

        byte[] buffer = new byte[8192];
        int bytesRead;
        long totalRead = 0;
        int lastPercent = -1;

        while ((bytesRead = input.read(buffer)) != -1) {
          if (cancelled) {
            if (listener != null) {
              listener.onError("Installation cancelled");
            }
            return;
          }
          output.write(buffer, 0, bytesRead);
          totalRead += bytesRead;
          if (totalSize > 0 && listener != null) {
            int percent = (int) (totalRead * 100 / totalSize);
            if (percent != lastPercent) {
              lastPercent = percent;
              listener.onDownloadProgress(percent);
            }
          }
        }

        if (!downloadedFile.exists() || downloadedFile.length() == 0) {
          if (listener != null) {
            listener.onError("Download failed: file is empty");
          }
          return;
        }

        if (listener != null) {
          listener.onDownloadProgress(100);
        }

        LinuxBootstrap.installFromArchiveRootfs(context, session, downloadedFile, rootfsDir,
            defaultShell,
            new LinuxBootstrap.InstallCallback() {
              @Override
              public void onProgress(int extractedEntries) {
                if (listener != null) {
                  listener.onExtractProgress(extractedEntries);
                }
              }

              @Override
              public void onSuccess() {
                if (downloadedFile.exists()) {
                  downloadedFile.delete();
                }
                if (listener != null) {
                  listener.onSuccess();
                }
              }

              @Override
              public void onError(Exception e) {
                if (listener != null) {
                  listener.onError("Extraction failed: " + e.getMessage());
                }
              }
            });

      } catch (Exception e) {
        if (listener != null) {
          listener.onError("Download error: " + e.getMessage());
        }
      } finally {
        try {
          if (output != null)
            output.close();
          if (input != null)
            input.close();
          if (conn != null)
            conn.disconnect();
        } catch (Exception e) {
        }
      }
    }).start();
  }

  public static void cancelInstall() {
    cancelled = true;
  }
}