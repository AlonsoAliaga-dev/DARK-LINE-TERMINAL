/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.alonso.darkline.terminal.distro;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.alonso.darkline.terminal.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class ImportRootfsDialog extends DialogFragment {

  public interface Listener {
    void onImportRequested(ImportConfig config, Uri archiveUri);
  }

  public static class ImportConfig {
    public String name;
    public String version;
    public String description;
    public String rootfsPath;
    public String shell;
    public String loginCommand;
  }

  private Listener listener;

  private TextInputLayout nameLayout;
  private TextInputLayout pathLayout;
  private TextInputLayout shellLayout;

  private TextInputEditText nameInput;
  private TextInputEditText versionInput;
  private TextInputEditText descriptionInput;
  private TextInputEditText pathInput;
  private TextInputEditText shellInput;
  private TextInputEditText commandInput;

  private TextView txtSelectedFile;
  private MaterialButton btnSelectFile;

  private Uri selectedArchiveUri;
  private boolean pathManuallyEdited = false;
  private AlertDialog configDialog;

  private final Handler mainHandler = new Handler(Looper.getMainLooper());

  private ActivityResultLauncher<Intent> filePicker;

  public void setListener(Listener listener) {
    this.listener = listener;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    filePicker = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(), result -> {
          if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            Uri uri = result.getData().getData();
            if (uri != null) {
              selectedArchiveUri = uri;
              updateSelectedFileText();
            }
          }
        });
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
    View view = LayoutInflater.from(requireContext())
        .inflate(R.layout.dialog_import_rootfs, null);

    nameLayout = view.findViewById(R.id.inputNameLayout);
    pathLayout = view.findViewById(R.id.inputPathLayout);
    shellLayout = view.findViewById(R.id.inputShellLayout);

    nameInput = view.findViewById(R.id.inputName);
    versionInput = view.findViewById(R.id.inputVersion);
    descriptionInput = view.findViewById(R.id.inputDescription);
    pathInput = view.findViewById(R.id.inputPath);
    shellInput = view.findViewById(R.id.inputShell);
    commandInput = view.findViewById(R.id.inputCommand);

    txtSelectedFile = view.findViewById(R.id.txtSelectedFile);
    btnSelectFile = view.findViewById(R.id.btnSelectFile);

    shellInput.setText("/bin/bash");

    btnSelectFile.setOnClickListener(v -> pickFile());

    nameInput.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
      }

      @Override
      public void afterTextChanged(Editable s) {
        if (!pathManuallyEdited) {
          String name = s.toString().trim();
          if (!name.isEmpty()) {
            pathInput.setText("Linux/" + name.replace(" ", "-"));
          } else {
            pathInput.setText("");
          }
        }
      }
    });

    pathInput.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
      }

      @Override
      public void afterTextChanged(Editable s) {
        if (pathInput.hasFocus()) {
          pathManuallyEdited = true;
        }
      }
    });

    pathInput.setOnFocusChangeListener((v, hasFocus) -> {
      if (hasFocus) pathManuallyEdited = true;
    });

    configDialog = new MaterialAlertDialogBuilder(requireContext())
        .setTitle("Import RootFS")
        .setView(view)
        .setCancelable(false)
        .setPositiveButton("Install", null)
        .setNegativeButton("Cancel", null)
        .create();

    configDialog.setOnShowListener(d -> {
      configDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
        onConfirm();
      });
    });

    return configDialog;
  }

  private void pickFile() {
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("*/*");
    intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
        "application/gzip",
        "application/x-gzip",
        "application/octet-stream"
    });
    filePicker.launch(intent);
  }

  private void updateSelectedFileText() {
    if (txtSelectedFile == null) return;
    if (selectedArchiveUri == null) {
      txtSelectedFile.setText("No file selected");
      txtSelectedFile.setTextColor(MaterialColors.getColor(
          txtSelectedFile, com.google.android.material.R.attr.colorOnSurfaceVariant));
    } else {
      String name = getFileName(selectedArchiveUri);
      txtSelectedFile.setText("Selected: " + name);
      txtSelectedFile.setTextColor(MaterialColors.getColor(
          txtSelectedFile, com.google.android.material.R.attr.colorPrimary));
    }
  }

  private String getFileName(Uri uri) {
    String path = uri.getPath();
    if (path == null) return "unknown";
    int cut = path.lastIndexOf('/');
    if (cut != -1) {
      return path.substring(cut + 1);
    }
    return path;
  }

  private void onConfirm() {
    boolean valid = true;

    if (selectedArchiveUri == null) {
      if (txtSelectedFile != null) {
        txtSelectedFile.setText("Please select a file first");
        txtSelectedFile.setTextColor(MaterialColors.getColor(
            txtSelectedFile, com.google.android.material.R.attr.colorError));
      }
      valid = false;
    }

    String name = text(nameInput);
    String path = text(pathInput);
    String shell = text(shellInput);

    if (name.isEmpty()) {
      nameLayout.setError("Required");
      valid = false;
    } else {
      nameLayout.setError(null);
    }

    if (path.isEmpty()) {
      pathLayout.setError("Required");
      valid = false;
    } else {
      pathLayout.setError(null);
    }

    if (shell.isEmpty()) {
      shellLayout.setError("Required");
      valid = false;
    } else {
      shellLayout.setError(null);
    }

    if (!valid) return;

    ImportConfig config = new ImportConfig();
    config.name = name;
    config.version = text(versionInput);
    config.description = text(descriptionInput);
    config.rootfsPath = path;
    config.shell = shell;
    config.loginCommand = text(commandInput);

    dismissAllowingStateLoss();

    if (listener != null) {
      listener.onImportRequested(config, selectedArchiveUri);
    }
  }

  private String text(TextInputEditText input) {
    return input.getText() == null ? "" : input.getText().toString().trim();
  }
}