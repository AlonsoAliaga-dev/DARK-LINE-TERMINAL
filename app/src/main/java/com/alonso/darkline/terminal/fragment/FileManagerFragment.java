/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.alonso.darkline.terminal.fragment;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alonso.darkline.terminal.R;
import com.alonso.darkline.terminal.activity.FileEditorActivity;
import com.alonso.darkline.terminal.databinding.DialogInputBinding;
import com.alonso.darkline.terminal.databinding.FragmentFileManagerBinding;
import com.alonso.darkline.terminal.databinding.ItemFileRowBinding;
import com.alonso.darkline.terminal.distro.DistroManager;
import com.alonso.darkline.terminal.utils.FileUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FileManagerFragment extends Fragment {

  private FragmentFileManagerBinding binding;
  private FileAdapter adapter;
  private File rootDir;
  private File currentDir;
  private File clipboardFile;
  private boolean clipboardIsCut = false;

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
      @Nullable Bundle savedInstanceState) {
    binding = FragmentFileManagerBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    Window window = requireActivity().getWindow();
    window.setStatusBarColor(Color.TRANSPARENT);
    window.setNavigationBarColor(Color.TRANSPARENT);

    adapter = new FileAdapter();
    binding.recyclerFiles.setLayoutManager(new LinearLayoutManager(requireContext()));
    binding.recyclerFiles.setAdapter(adapter);

    binding.btnUp.setOnClickListener(v -> goUp());
    binding.btnNewFile.setOnClickListener(v -> showCreateDialog());

    loadIfInstalled();
  }

  @Override
  public void onResume() {
    super.onResume();
    loadIfInstalled();
  }

  private void loadIfInstalled() {
    DistroManager dm = new DistroManager(requireContext());
    if (!dm.isInstalled()) {
      showEmpty("No distro installed");
      return;
    }
    File dir = dm.getInstalledRootfsDir();
    if (dir == null || !dir.isDirectory()) {
      showEmpty("Rootfs not found");
      return;
    }
    if (rootDir == null || !rootDir.equals(dir)) {
      rootDir = dir;
      currentDir = dir;
      openDirectory(currentDir);
    }
  }

  private void showEmpty(String message) {
    binding.loadingContainer.setVisibility(View.GONE);
    binding.fileManagerContainer.setVisibility(View.GONE);
    binding.emptyContainer.setVisibility(View.VISIBLE);
    binding.txtEmptyMessage.setText(message);
  }

  private void showContent() {
    binding.loadingContainer.setVisibility(View.GONE);
    binding.emptyContainer.setVisibility(View.GONE);
    binding.fileManagerContainer.setVisibility(View.VISIBLE);
  }

  private void openDirectory(File dir) {
    if (dir == null || !dir.isDirectory()) return;
    currentDir = dir;
    showContent();
    updatePath();

    List<File> items = new ArrayList<>();
    File[] files = dir.listFiles();
    if (files != null) {
      Collections.addAll(items, files);
    }
    Collections.sort(items, (a, b) -> {
      if (a.isDirectory() && !b.isDirectory()) return -1;
      if (!a.isDirectory() && b.isDirectory()) return 1;
      return a.getName().compareToIgnoreCase(b.getName());
    });

    adapter.setItems(items);
  }

  private void updatePath() {
    String path = currentDir.getAbsolutePath();
    String rootPath = rootDir.getAbsolutePath();
    if (path.equals(rootPath)) {
      binding.txtCurrentPath.setText("/");
    } else {
      binding.txtCurrentPath.setText(path.substring(rootPath.length()));
    }
    boolean isRoot = currentDir.equals(rootDir);
    binding.btnUp.setEnabled(!isRoot);
    binding.btnUp.setAlpha(isRoot ? 0.4f : 1f);
  }

  private void goUp() {
    if (currentDir == null || currentDir.equals(rootDir)) return;
    File parent = currentDir.getParentFile();
    if (parent != null) openDirectory(parent);
  }

  private void showCreateDialog() {
    String[] options = { "New file", "New folder" };
    new MaterialAlertDialogBuilder(requireContext())
        .setTitle("Create")
        .setItems(options, (d, which) -> {
          if (which == 0) showNewFileDialog();
          else showNewFolderDialog();
        })
        .show();
  }

  private void showNewFileDialog() {
    DialogInputBinding dialogBinding = DialogInputBinding.inflate(getLayoutInflater());
    dialogBinding.inputLabel.setText("File name:");
    dialogBinding.inputValue.setHint("example.txt");

    new MaterialAlertDialogBuilder(requireContext())
        .setTitle("New file")
        .setView(dialogBinding.getRoot())
        .setPositiveButton("Create", (d, w) -> {
          String name = dialogBinding.inputValue.getText() == null ? ""
              : dialogBinding.inputValue.getText().toString().trim();
          if (name.isEmpty()) return;
          File newFile = new File(currentDir, name);
          if (newFile.exists()) {
            Toast.makeText(getContext(), "Already exists", Toast.LENGTH_SHORT).show();
            return;
          }
          try {
            if (newFile.createNewFile()) openDirectory(currentDir);
            else Toast.makeText(getContext(), "Failed", Toast.LENGTH_SHORT).show();
          } catch (Exception e) {
            Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
          }
        })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void showNewFolderDialog() {
    DialogInputBinding dialogBinding = DialogInputBinding.inflate(getLayoutInflater());
    dialogBinding.inputLabel.setText("Folder name:");
    dialogBinding.inputValue.setHint("myfolder");

    new MaterialAlertDialogBuilder(requireContext())
        .setTitle("New folder")
        .setView(dialogBinding.getRoot())
        .setPositiveButton("Create", (d, w) -> {
          String name = dialogBinding.inputValue.getText() == null ? ""
              : dialogBinding.inputValue.getText().toString().trim();
          if (name.isEmpty()) return;
          File newDir = new File(currentDir, name);
          if (newDir.exists()) {
            Toast.makeText(getContext(), "Already exists", Toast.LENGTH_SHORT).show();
            return;
          }
          if (newDir.mkdirs()) openDirectory(currentDir);
          else Toast.makeText(getContext(), "Failed", Toast.LENGTH_SHORT).show();
        })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void showItemOptions(File file, View anchor) {
    boolean isDir = file.isDirectory();
    PopupMenu popup = new PopupMenu(requireContext(), anchor);

    if (isDir) {
      popup.getMenu().add(0, 1, 0, "Open");
      popup.getMenu().add(0, 2, 0, "New file");
      popup.getMenu().add(0, 3, 0, "New folder");
    } else if (FileEditorActivity.isTextFile(file)) {
      popup.getMenu().add(0, 10, 0, "Open with editor");
    }
    popup.getMenu().add(0, 4, 0, "Rename");
    popup.getMenu().add(0, 5, 0, "Copy");
    popup.getMenu().add(0, 6, 0, "Cut");
    if (isDir) popup.getMenu().add(0, 9, 0, "Paste here");
    popup.getMenu().add(0, 7, 0, "Copy path");
    popup.getMenu().add(0, 8, 0, "Delete");

    popup.setOnMenuItemClickListener(item -> {
      int id = item.getItemId();
      if (id == 1) { openDirectory(file); return true; }
      if (id == 2) {
        File tmp = currentDir;
        currentDir = file;
        showNewFileDialog();
        currentDir = tmp;
        return true;
      }
      if (id == 3) {
        File tmp = currentDir;
        currentDir = file;
        showNewFolderDialog();
        currentDir = tmp;
        return true;
      }
      if (id == 4) { renameItem(file); return true; }
      if (id == 5) {
        clipboardFile = file;
        clipboardIsCut = false;
        Toast.makeText(getContext(), "Copied: " + file.getName(), Toast.LENGTH_SHORT).show();
        return true;
      }
      if (id == 6) {
        clipboardFile = file;
        clipboardIsCut = true;
        Toast.makeText(getContext(), "Cut: " + file.getName(), Toast.LENGTH_SHORT).show();
        return true;
      }
      if (id == 7) { copyPath(file.getAbsolutePath()); return true; }
      if (id == 8) { confirmDelete(file); return true; }
      if (id == 9) { pasteInto(file); return true; }
      if (id == 10) { FileEditorActivity.open(requireContext(), file); return true; }
      return false;
    });
    popup.show();
  }

  private void renameItem(File file) {
    DialogInputBinding dialogBinding = DialogInputBinding.inflate(getLayoutInflater());
    dialogBinding.inputLabel.setText("New name:");
    dialogBinding.inputValue.setText(file.getName());

    new MaterialAlertDialogBuilder(requireContext())
        .setTitle("Rename")
        .setView(dialogBinding.getRoot())
        .setPositiveButton("Rename", (d, w) -> {
          String name = dialogBinding.inputValue.getText() == null ? ""
              : dialogBinding.inputValue.getText().toString().trim();
          if (name.isEmpty() || name.equals(file.getName())) return;
          File newFile = new File(file.getParentFile(), name);
          if (newFile.exists()) {
            Toast.makeText(getContext(), "Already exists", Toast.LENGTH_SHORT).show();
            return;
          }
          if (file.renameTo(newFile)) openDirectory(currentDir);
          else Toast.makeText(getContext(), "Failed", Toast.LENGTH_SHORT).show();
        })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void confirmDelete(File file) {
    new MaterialAlertDialogBuilder(requireContext())
        .setTitle("Delete?")
        .setMessage("Delete " + file.getName() + "?")
        .setPositiveButton("Delete", (d, w) -> {
          if (FileUtil.deleteFile(file.getAbsolutePath())) openDirectory(currentDir);
          else Toast.makeText(getContext(), "Failed", Toast.LENGTH_SHORT).show();
        })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void copyPath(String path) {
    ClipboardManager cm = (ClipboardManager) requireContext()
        .getSystemService(Context.CLIPBOARD_SERVICE);
    if (cm != null) {
      cm.setPrimaryClip(ClipData.newPlainText("path", path));
      Toast.makeText(getContext(), "Path copied", Toast.LENGTH_SHORT).show();
    }
  }

  private void pasteInto(File dir) {
    if (clipboardFile == null) {
      Toast.makeText(getContext(), "Clipboard is empty", Toast.LENGTH_SHORT).show();
      return;
    }
    if (clipboardFile.getAbsolutePath().equals(dir.getAbsolutePath())) {
      Toast.makeText(getContext(), "Cannot paste into itself", Toast.LENGTH_SHORT).show();
      return;
    }

    String newName = FileUtil.generateUniqueName(dir, clipboardFile.getName());
    File dst = new File(dir, newName);

    if (clipboardIsCut) {
      if (clipboardFile.renameTo(dst)) {
        clipboardFile = null;
        clipboardIsCut = false;
        openDirectory(currentDir);
      } else {
        if (FileUtil.copyRecursive(clipboardFile, dst)) {
          FileUtil.deleteFile(clipboardFile.getAbsolutePath());
          clipboardFile = null;
          clipboardIsCut = false;
          openDirectory(currentDir);
        } else {
          Toast.makeText(getContext(), "Failed to move", Toast.LENGTH_SHORT).show();
        }
      }
    } else {
      if (FileUtil.copyRecursive(clipboardFile, dst)) {
        openDirectory(currentDir);
      } else {
        Toast.makeText(getContext(), "Failed to copy", Toast.LENGTH_SHORT).show();
      }
    }
  }

  public boolean onBackPressed() {
    if (currentDir != null && rootDir != null && !currentDir.equals(rootDir)) {
      File parent = currentDir.getParentFile();
      if (parent != null && parent.getAbsolutePath().startsWith(rootDir.getAbsolutePath())) {
        openDirectory(parent);
        return true;
      }
    }
    return false;
  }

  private class FileAdapter extends RecyclerView.Adapter<FileAdapter.VH> {

    private final List<File> items = new ArrayList<>();

    void setItems(List<File> newItems) {
      items.clear();
      items.addAll(newItems);
      notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      ItemFileRowBinding b = ItemFileRowBinding.inflate(
          LayoutInflater.from(parent.getContext()), parent, false);
      return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
      File file = items.get(position);
      holder.binding.txtName.setText(file.getName());

      if (file.isDirectory()) {
        holder.binding.imgIcon.setImageResource(R.drawable.ic_folder_24);
        holder.binding.txtMeta.setVisibility(View.GONE);
      } else {
        holder.binding.imgIcon.setImageResource(R.drawable.ic_file_24);
        holder.binding.txtMeta.setVisibility(View.VISIBLE);
        holder.binding.txtMeta.setText(formatSize(file.length()));
      }

      holder.itemView.setOnClickListener(v -> {
        if (file.isDirectory()) {
          openDirectory(file);
        } else if (FileEditorActivity.isTextFile(file)) {
          FileEditorActivity.open(requireContext(), file);
        } else {
          new MaterialAlertDialogBuilder(requireContext())
              .setTitle(file.getName())
              .setMessage("Size: " + formatSize(file.length())
                  + "\nPath: " + file.getAbsolutePath())
              .setPositiveButton("OK", null)
              .show();
        }
      });

      holder.itemView.setOnLongClickListener(v -> {
        showItemOptions(file, v);
        return true;
      });
    }

    @Override
    public int getItemCount() {
      return items.size();
    }

    class VH extends RecyclerView.ViewHolder {
      final ItemFileRowBinding binding;

      VH(@NonNull ItemFileRowBinding binding) {
        super(binding.getRoot());
        this.binding = binding;
      }
    }
  }

  private String formatSize(long bytes) {
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024)
      return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
    if (bytes < 1024 * 1024 * 1024)
      return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024));
    return String.format(java.util.Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024));
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }
}