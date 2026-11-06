/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.alonso.darkline.terminal.shell;

import android.content.Context;

import com.alonso.darkline.terminal.client.DarkLineSessionClient;
import com.alonso.darkline.terminal.settings.SettingsManager;
import com.termux.terminal.TerminalSession;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class LinuxShellSession {

  private LinuxShellSession() {
  }

  private static final String PROOT_LIBRARY_NAME = "libproot.so";
  private static final String LOADER_LIBRARY_NAME = "libloader.so";
  private static final String LOADER32_LIBRARY_NAME = "libloader32.so";

  public static TerminalSession createProotSession(Context context, File rootfsDir,
      String loginShell, DarkLineSessionClient client) {
    try {
      return createProotSessionInternal(context, rootfsDir, loginShell, client);
    } catch (IllegalStateException e) {
      throw e;
    } catch (Throwable t) {
      throw new IllegalStateException("Unexpected error: " + t, t);
    }
  }

  private static TerminalSession createProotSessionInternal(Context context, File rootfsDir,
      String loginShell, DarkLineSessionClient client) {

    String nativeLibDir = context.getApplicationInfo().nativeLibraryDir;

    File prootBinary = new File(nativeLibDir, PROOT_LIBRARY_NAME);
    if (!prootBinary.exists()) {
      throw new IllegalStateException("libproot.so not found: " + prootBinary.getAbsolutePath());
    }

    File loaderBinary = new File(nativeLibDir, LOADER_LIBRARY_NAME);
    if (!loaderBinary.exists()) {
      throw new IllegalStateException("libloader.so not found: " + loaderBinary.getAbsolutePath());
    }

    if (rootfsDir == null || !rootfsDir.isDirectory()) {
      throw new IllegalStateException("rootfs not found: " +
          (rootfsDir == null ? "null" : rootfsDir.getAbsolutePath()));
    }

    File tmpDir = new File(context.getCacheDir(), "proot-tmp/" + System.currentTimeMillis());
    tmpDir.mkdirs();

    SettingsManager settings = new SettingsManager(context);
    List<String> userBindMounts = settings.getBindMounts();

    List<String> args = new ArrayList<>();
    args.add("proot");
    args.add("--link2symlink");
    args.add("--kill-on-exit");
    args.add("--sysvipc");
    args.add("-L");
    args.add("-0");
    args.add("-r");
    args.add(rootfsDir.getAbsolutePath());

    args.add("-b");
    args.add("/dev");
    args.add("-b");
    args.add("/dev/urandom:/dev/random");
    args.add("-b");
    args.add("/proc");
    args.add("-b");
    args.add("/sys");

    for (String mount : userBindMounts) {
      args.add("-b");
      args.add(mount);
    }

    args.add("-w");
    args.add("/root");
    args.add("/usr/bin/env");
    args.add("-i");
    args.add("HOME=/root");
    args.add("TERM=xterm-256color");
    args.add("LANG=C.UTF-8");
    args.add("PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");

    String shell = loginShell;
    if (shell == null || shell.trim().isEmpty()) {
      shell = "/bin/sh";
    }
    args.add(shell);
    args.add("--login");

    List<String> env = new ArrayList<>();
    env.add("PROOT_TMP_DIR=" + tmpDir.getAbsolutePath());
    env.add("PROOT_LOADER=" + loaderBinary.getAbsolutePath());
    env.add("LD_LIBRARY_PATH=" + nativeLibDir);

    File loader32 = new File(nativeLibDir, LOADER32_LIBRARY_NAME);
    if (loader32.exists()) {
      env.add("PROOT_LOADER_32=" + loader32.getAbsolutePath());
    }

    return new TerminalSession(prootBinary.getAbsolutePath(), rootfsDir.getAbsolutePath(),
        args.toArray(new String[0]), env.toArray(new String[0]), null, client);
  }
}