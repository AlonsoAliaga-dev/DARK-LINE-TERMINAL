/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.alonso.darkline.terminal.activity;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.alonso.darkline.terminal.databinding.ActivitySettingsBinding;
import com.alonso.darkline.terminal.databinding.DialogInputBinding;
import com.alonso.darkline.terminal.distro.DistroManager;
import com.alonso.darkline.terminal.installer.LinuxBootstrap;
import com.alonso.darkline.terminal.settings.BindMountAdapter;
import com.alonso.darkline.terminal.settings.SettingsKeys;
import com.alonso.darkline.terminal.settings.SettingsManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SettingsActivity extends AppCompatActivity {

  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

  private ActivitySettingsBinding binding;
  private SettingsManager settings;
  private DistroManager distroManager;
  private BindMountAdapter bindMountAdapter;
  private final List<String> bindMounts = new ArrayList<>();

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    binding = ActivitySettingsBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());

    settings = new SettingsManager(this);
    distroManager = new DistroManager(this);

    getWindow().setStatusBarColor(Color.TRANSPARENT);
    getWindow().setNavigationBarColor(Color.TRANSPARENT);

    binding.toolbar.setNavigationOnClickListener(v -> finish());

    binding.switchExtraKeys.setChecked(settings.isShowExtraKeys());
    binding.switchExtraKeys.setOnCheckedChangeListener((v, checked) ->
        settings.setShowExtraKeys(checked));

    binding.btnUninstallDistro.setOnClickListener(v -> confirmUninstallDistro());

    binding.inputLoginShell.setText(settings.getLoginShellOverride());
    binding.inputLoginShell.setOnFocusChangeListener((v, hasFocus) -> {
      if (!hasFocus) settings.setLoginShellOverride(text(binding.inputLoginShell));
    });

    binding.inputStartup.setText(settings.getStartupCommand());
    binding.inputStartup.setOnFocusChangeListener((v, hasFocus) -> {
      if (!hasFocus) settings.setStartupCommand(text(binding.inputStartup));
    });

    binding.inputAutoClose.setText(String.valueOf(settings.getAutoCloseMinutes()));
    binding.inputAutoClose.setOnFocusChangeListener((v, hasFocus) -> {
      if (!hasFocus) saveAutoClose();
    });

    setupBindMounts();
    binding.btnAddBindMount.setOnClickListener(v -> showAddBindMountDialog());

    setupAiSection();
  }

  @Override
  protected void onResume() {
    super.onResume();
    updateDistroSection();
  }

  @Override
  protected void onPause() {
    super.onPause();
    saveAllFields();
  }

  private void updateDistroSection() {
    boolean installed = distroManager.isInstalled();
    if (installed) {
      binding.txtDistroStatus.setText(distroManager.getInstalledName());
      binding.txtDistroPath.setText(distroManager.getInstalledRootfsPath());
      binding.btnUninstallDistro.setEnabled(true);
      binding.txtDistroStatus.setTextColor(
          com.google.android.material.color.MaterialColors.getColor(
              binding.getRoot(),
              com.google.android.material.R.attr.colorOnSurface));
    } else {
      binding.txtDistroStatus.setText("No distro installed");
      binding.txtDistroPath.setText("Open a Linux session to install one");
      binding.btnUninstallDistro.setEnabled(false);
      binding.txtDistroStatus.setTextColor(
          com.google.android.material.color.MaterialColors.getColor(
              binding.getRoot(),
              com.google.android.material.R.attr.colorOnSurfaceVariant));
    }
  }

  private void saveAllFields() {
    settings.setStartupCommand(text(binding.inputStartup));
    settings.setLoginShellOverride(text(binding.inputLoginShell));
    saveAutoClose();
    saveAiFields();
  }

  private void setupBindMounts() {
    bindMounts.clear();
    bindMounts.addAll(settings.getBindMounts());
    bindMountAdapter = new BindMountAdapter(bindMounts, position -> {
      bindMounts.remove(position);
      bindMountAdapter.notifyItemRemoved(position);
      settings.setBindMounts(bindMounts);
    });
    binding.recyclerBindMounts.setLayoutManager(new LinearLayoutManager(this));
    binding.recyclerBindMounts.setAdapter(bindMountAdapter);
  }

  private void showAddBindMountDialog() {
    DialogInputBinding dialogBinding = DialogInputBinding.inflate(getLayoutInflater());
    dialogBinding.inputLabel.setText("Example: /storage/emulated/0:/sdcard");
    dialogBinding.inputValue.setHint("/host/path:/guest/path");

    new MaterialAlertDialogBuilder(this)
        .setTitle("Add bind mount")
        .setView(dialogBinding.getRoot())
        .setPositiveButton("Add", (dialog, which) -> {
          String value = text(dialogBinding.inputValue);
          if (value.isEmpty()) return;
          if (SettingsManager.PROTECTED_BIND_MOUNTS.contains(value)) {
            snack("This path is protected and cannot be modified");
            return;
          }
          if (bindMounts.contains(value)) {
            snack("This bind mount already exists");
            return;
          }
          bindMounts.add(value);
          bindMountAdapter.notifyItemInserted(bindMounts.size() - 1);
          settings.setBindMounts(bindMounts);
        })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void saveAutoClose() {
    String value = text(binding.inputAutoClose);
    int minutes;
    try {
      minutes = value.isEmpty() ? 0 : Integer.parseInt(value);
    } catch (NumberFormatException e) {
      minutes = 0;
    }
    if (minutes < 0) minutes = 0;
    settings.setAutoCloseMinutes(minutes);
    binding.inputAutoClose.setText(String.valueOf(minutes));
  }

  private void confirmUninstallDistro() {
    if (!distroManager.isInstalled()) {
      snack("No distro installed");
      return;
    }

    String distroName = distroManager.getInstalledName();
    new MaterialAlertDialogBuilder(this)
        .setTitle("Uninstall " + distroName)
        .setMessage("All data inside the distro will be deleted. Continue?")
        .setPositiveButton("Uninstall", (dialog, which) -> {
          File rootfs = distroManager.getInstalledRootfsDir();
          distroManager.clear();
          LinuxBootstrap.uninstall(rootfs, () ->
              runOnUiThread(() -> {
                updateDistroSection();
                snack(distroName + " has been removed");
              }));
        })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void setupAiSection() {
    binding.inputGroqKey.setText(settings.getGroqKey());
    binding.inputGroqModel.setText(settings.getGroqModel());

    binding.inputCustomBaseUrl.setText(settings.getCustomBaseUrl());
    binding.inputCustomApiKey.setText(settings.getCustomApiKey());
    binding.inputCustomModel.setText(settings.getCustomModel());

    binding.btnTestAiConnection.setOnClickListener(v -> testAiConnection());
  }

  private void saveAiFields() {
    settings.setGroqKey(text(binding.inputGroqKey));
    String model = text(binding.inputGroqModel);
    if (model.isEmpty()) {
      model = SettingsKeys.DEFAULT_GROQ_MODEL;
      binding.inputGroqModel.setText(model);
    }
    settings.setGroqModel(model);

    settings.setCustomBaseUrl(text(binding.inputCustomBaseUrl));
    settings.setCustomApiKey(text(binding.inputCustomApiKey));
    settings.setCustomModel(text(binding.inputCustomModel));
  }

  private void testAiConnection() {
    saveAiFields();

    String baseUrl = settings.getActiveBaseUrl();
    if (baseUrl.endsWith("/")) {
      baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
    }
    String url = baseUrl + "/chat/completions";

    String apiKey = settings.getActiveApiKey();
    String model = settings.getActiveModel();

    if (model == null || model.trim().isEmpty()) {
      snack("Model is empty");
      return;
    }

    JsonObject body = new JsonObject();
    body.addProperty("model", model.trim());
    JsonArray messages = new JsonArray();
    JsonObject userMsg = new JsonObject();
    userMsg.addProperty("role", "user");
    userMsg.addProperty("content", "ping");
    messages.add(userMsg);
    body.add("messages", messages);
    body.addProperty("max_tokens", 5);

    Request.Builder reqBuilder = new Request.Builder()
        .url(url)
        .addHeader("Content-Type", "application/json")
        .post(RequestBody.create(body.toString(), JSON));

    if (apiKey != null && !apiKey.trim().isEmpty()) {
      reqBuilder.addHeader("Authorization", "Bearer " + apiKey.trim());
    }

    OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build();

    snack("Testing connection...");

    client.newCall(reqBuilder.build()).enqueue(new Callback() {
      @Override
      public void onFailure(Call call, java.io.IOException e) {
        runOnUiThread(() -> snack("Connection failed: " + e.getMessage()));
      }

      @Override
      public void onResponse(Call call, Response response) {
        int code = response.code();
        String bodyText = "";
        try {
          if (response.body() != null) {
            bodyText = response.body().string();
            if (bodyText.length() > 150) bodyText = bodyText.substring(0, 150);
          }
        } catch (Exception ignored) {
        }
        response.close();

        final String finalBody = bodyText;
        runOnUiThread(() -> {
          if (code >= 200 && code < 300) {
            snack("Connection OK (" + code + ")");
          } else {
            snack("HTTP " + code + ": " + finalBody);
          }
        });
      }
    });
  }

  private String text(EditText editText) {
    return editText.getText() == null ? "" : editText.getText().toString().trim();
  }

  private void snack(String message) {
    Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
  }
}