/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.alonso.darkline.terminal.shell;

import android.content.Context;
import android.os.Environment;
import com.alonso.darkline.terminal.client.DarkLineSessionClient;

import com.termux.terminal.TerminalSession;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class AndroidShellSession {

  private AndroidShellSession() {
  }

  private static final String[] CANDIDATE_SHELLS = { "/system/bin/sh", "/system/xbin/sh",
      "/bin/sh", };

  public static TerminalSession createSession(Context context, String workingDirectory,
      DarkLineSessionClient client) {
    String shellPath = resolveShellPath();
    String cwd = resolveCwd(context, workingDirectory);
    String[] env = buildEnvironment(context, cwd);
    return new TerminalSession(shellPath, cwd, null, env, null, client);
  }

  private static String resolveShellPath() {
    for (String candidate : CANDIDATE_SHELLS) {
      if (new File(candidate).exists())
        return candidate;
    }
    return CANDIDATE_SHELLS[0];
  }

  private static String resolveCwd(Context context, String workingDirectory) {
    if (workingDirectory != null && new File(workingDirectory).isDirectory()) {
      return workingDirectory;
    }
    File external = Environment.getExternalStorageDirectory();
    if (external != null && external.isDirectory())
      return external.getAbsolutePath();
    return context.getFilesDir().getAbsolutePath();
  }

  private static String[] buildEnvironment(Context context, String cwd) {
    List<String> env = new ArrayList<>();
    env.add("HOME=" + cwd);
    env.add("PATH=/sbin:/system/sbin:/system/bin:/system/xbin:/vendor/bin");
    env.add("TERM=xterm-256color");
    env.add("TMPDIR=" + context.getCacheDir().getAbsolutePath());
    env.add("COLORTERM=truecolor");
    return env.toArray(new String[0]);
  }
}