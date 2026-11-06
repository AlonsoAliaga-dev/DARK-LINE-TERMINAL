/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.alonso.darkline.terminal.distro;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.alonso.darkline.terminal.R;
import com.alonso.darkline.terminal.installer.LinuxInstaller;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.termux.terminal.TerminalSession;

import java.io.File;

public class DistroInstallDialog extends DialogFragment {

  public interface Listener {
    void onInstallSuccess(DistroInfo distro);

    void onInstallError(String message);
  }

  private DistroInfo distro;
  private TerminalSession session;
  private Listener listener;

  private TextView txtStatus;
  private LinearProgressIndicator progressBar;

  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private boolean isInstalling = false;

  public void setDistro(DistroInfo distro) {
    this.distro = distro;
  }

  public void setSession(TerminalSession session) {
    this.session = session;
  }

  public void setListener(Listener listener) {
    this.listener = listener;
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
    View view = LayoutInflater.from(requireContext())
        .inflate(R.layout.dialog_distro_install, null);
    txtStatus = view.findViewById(R.id.txtInstallStatus);
    progressBar = view.findViewById(R.id.progressInstall);

    return new MaterialAlertDialogBuilder(requireContext())
        .setTitle("Install " + (distro == null ? "" : distro.getDisplayName()))
        .setView(view)
        .setCancelable(false)
        .setNegativeButton("Cancel", (d, which) -> LinuxInstaller.cancelInstall())
        .create();
  }

  @Override
  public void onStart() {
    super.onStart();
    startInstall();
  }

  private void startInstall() {
    if (distro == null || session == null) {
      updateStatus("Internal error: missing distro or session");
      return;
    }

    String abi = DistroRegistry.getCompatibleAbi(distro);
    if (abi == null) {
      updateStatus("Your device is not supported for this distro");
      return;
    }

    String url = distro.getArchiveUrl(abi);
    if (url == null || url.isEmpty()) {
      updateStatus("No download URL for your architecture");
      return;
    }

    isInstalling = true;
    updateStatus("Starting download...");
    mainHandler.post(() -> {
      progressBar.setIndeterminate(false);
      progressBar.setMax(100);
      progressBar.setProgress(0);
    });

    File rootfsDir = new File(requireContext().getFilesDir(), distro.getRootfsPath());
    String defaultShell = distro.getDefaultShell();

    LinuxInstaller.installFrom(requireContext(), session, url, rootfsDir, defaultShell,
        new LinuxInstaller.InstallListener() {
          @Override
          public void onDownloadProgress(int percent) {
            mainHandler.post(() -> {
              if (!isAdded()) return;
              txtStatus.setText("Downloading... " + percent + "%");
              progressBar.setIndeterminate(false);
              progressBar.setProgress(percent);
            });
          }

          @Override
          public void onExtractProgress(int extractedEntries) {
            mainHandler.post(() -> {
              if (!isAdded()) return;
              txtStatus.setText("Extracting... (" + extractedEntries + " files)");
              progressBar.setIndeterminate(true);
            });
          }

          @Override
          public void onSuccess() {
            isInstalling = false;
            mainHandler.post(() -> {
              if (!isAdded()) return;
              DistroManager manager = new DistroManager(requireContext());
              manager.saveInstalled(distro);
              if (listener != null) listener.onInstallSuccess(distro);
              dismissAllowingStateLoss();
            });
          }

          @Override
          public void onError(String message) {
            isInstalling = false;
            mainHandler.post(() -> {
              if (!isAdded()) return;
              txtStatus.setText("Error: " + message);
              if (listener != null) listener.onInstallError(message);
              showErrorDialog(message);
            });
          }
        });
  }

  private void updateStatus(String msg) {
    if (txtStatus != null) txtStatus.setText(msg);
  }

  private void showErrorDialog(String message) {
    if (!isAdded()) return;
    new MaterialAlertDialogBuilder(requireContext())
        .setTitle("Installation failed")
        .setMessage(message)
        .setPositiveButton("OK", (d, w) -> dismissAllowingStateLoss())
        .setCancelable(false)
        .show();
  }

  @Override
  public void onDismiss(@NonNull DialogInterface dialog) {
    if (isInstalling) {
      LinuxInstaller.cancelInstall();
    }
    super.onDismiss(dialog);
  }
}