/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.alonso.darkline.terminal.installer;

import android.content.Context;

import com.termux.terminal.TerminalSession;

import java.io.File;
import java.nio.charset.StandardCharsets;

public final class LinuxExtractor {

  private LinuxExtractor() {
  }

  public interface ProgressListener {
    void onProgress(int extractedEntries);

    void onError(String error);

    void onSuccess();
  }

  public static void extract(Context context, TerminalSession session, File archiveRootfsFile,
      File destDir, String defaultShell, ProgressListener listener) {

    new Thread(() -> {
      try {
        if (!destDir.exists() && !destDir.mkdirs()) {
          if (listener != null) {
            listener.onError("Cannot create destination directory: " + destDir);
          }
          return;
        }

        String extractCmd = "tar -xzvpf '" + archiveRootfsFile.getAbsolutePath() + "' -C '"
            + destDir.getAbsolutePath() + "'\n";

        session.write(extractCmd.getBytes(StandardCharsets.UTF_8), 0, extractCmd.length());

        File binDir = new File(destDir, "bin");
        File usrDir = new File(destDir, "usr");
        File etcDir = new File(destDir, "etc");

        boolean completed = false;
        int maxWait = 300;
        int waited = 0;

        while (waited < maxWait && !completed) {
          Thread.sleep(1000);
          waited++;

          boolean binReady = binDir.isDirectory() && binDir.listFiles() != null
              && binDir.listFiles().length > 0;
          boolean usrOrEtcReady = (usrDir.isDirectory() && usrDir.listFiles() != null
              && usrDir.listFiles().length > 0)
              || (etcDir.isDirectory() && etcDir.listFiles() != null
              && etcDir.listFiles().length > 0);

          if (binReady && usrOrEtcReady) {
            Thread.sleep(2000);
            completed = true;
            break;
          }
        }

        if (!completed) {
          if (listener != null) {
            listener.onError("Extraction timeout (bin/ or usr/ not populated)");
          }
          return;
        }

        if (listener != null) {
          listener.onSuccess();
        }

      } catch (Exception e) {
        if (listener != null) {
          listener.onError("Extraction error: " + e.getMessage());
        }
      }
    }).start();
  }
}