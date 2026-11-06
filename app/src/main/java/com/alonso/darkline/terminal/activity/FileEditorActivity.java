/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.alonso.darkline.terminal.activity;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.alonso.darkline.terminal.R;
import com.alonso.darkline.terminal.databinding.ActivityFileEditorBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver;
import io.github.rosemoe.sora.widget.CodeEditor;

import org.eclipse.tm4e.core.registry.IThemeSource;

public class FileEditorActivity extends AppCompatActivity {

  private static final String EXTRA_FILE_PATH = "file_path";

  private static final Set<String> TEXT_EXTENSIONS = new HashSet<>(Arrays.asList(
      "txt", "md", "sh", "bash", "zsh", "fish", "py", "js", "ts", "rb", "php",
      "java", "kt", "c", "cpp", "h", "hpp", "conf", "cnf", "ini", "cfg",
      "json", "xml", "yaml", "yml", "toml", "html", "css", "scss",
      "log", "properties", "env", "rc", "profile", "service"
  ));

  private ActivityFileEditorBinding binding;
  private CodeEditor editor;
  private File currentFile;
  private boolean hasUnsavedChanges = false;

  public static boolean isTextFile(File file) {
    if (file == null || file.isDirectory()) return false;
    if (file.length() > 3 * 1024 * 1024) return false;
    String name = file.getName().toLowerCase();
    int dot = name.lastIndexOf('.');
    if (dot == -1) return true;
    return TEXT_EXTENSIONS.contains(name.substring(dot + 1));
  }

  public static void open(Context context, File file) {
    Intent intent = new Intent(context, FileEditorActivity.class);
    intent.putExtra(EXTRA_FILE_PATH, file.getAbsolutePath());
    context.startActivity(intent);
  }

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    binding = ActivityFileEditorBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());

    String path = getIntent().getStringExtra(EXTRA_FILE_PATH);
    if (path == null) {
      finish();
      return;
    }
    currentFile = new File(path);
    if (!currentFile.exists() || currentFile.isDirectory()) {
      Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show();
      finish();
      return;
    }

    binding.toolbar.setNavigationOnClickListener(v -> handleBack());
    binding.toolbar.setTitle(currentFile.getName());
    binding.toolbar.inflateMenu(R.menu.menu_editor);
    binding.toolbar.setOnMenuItemClickListener(this::onMenuItemClicked);

    setupEditor();
    loadFile();

    binding.fabSave.setOnClickListener(v -> saveFile());
  }

  private boolean onMenuItemClicked(MenuItem item) {
    int id = item.getItemId();
    if (id == R.id.action_undo) {
      if (editor.canUndo()) editor.undo();
      return true;
    } else if (id == R.id.action_redo) {
      if (editor.canRedo()) editor.redo();
      return true;
    } else if (id == R.id.action_find) {
      editor.beginSearchMode();
      return true;
    } else if (id == R.id.action_save) {
      saveFile();
      return true;
    }
    return false;
  }

  private void setupEditor() {
    editor = new CodeEditor(this);
    binding.editorContainer.addView(editor, new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT));

    editor.setTabWidth(4);
    editor.setLigatureEnabled(false);
    editor.setCursorWidth(4);
    editor.setPinLineNumber(true);
    editor.setWordwrap(false);
    editor.setLineNumberEnabled(true);
    editor.setEditable(true);
    try {
      editor.setTypefaceText(Typeface.createFromAsset(getAssets(), "fonts/mono.ttf"));
    } catch (Exception e) {
      editor.setTypefaceText(Typeface.MONOSPACE);
    }
    editor.setTextSize(14);

    boolean isDarkMode = (getResources().getConfiguration().uiMode
        & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

    FileProviderRegistry.getInstance().addFileProvider(
        new AssetsFileResolver(getApplicationContext().getAssets()));

    ThemeRegistry themeRegistry = ThemeRegistry.getInstance();
    String name = isDarkMode ? "Dark" : "Light";
    String themeAssetsPath = "textmate/" + name + ".json";

    try {
      ThemeModel model = new ThemeModel(
          IThemeSource.fromInputStream(
              FileProviderRegistry.getInstance().tryGetInputStream(themeAssetsPath),
              themeAssetsPath,
              null
          ),
          name
      );
      if (isDarkMode) {
        model.setDark(true);
      }
      themeRegistry.loadTheme(model);
      themeRegistry.setTheme(name);
    } catch (Exception e) {
      e.printStackTrace();
    }

    String fileName = currentFile.getName().toLowerCase();
    if (fileName.endsWith(".sh") || fileName.endsWith(".bash")
        || fileName.endsWith(".zsh") || fileName.endsWith(".profile")) {
      setUpLanguage("source.shell");
    } else {
      setUpLanguage("source.text");
    }
  }

  private void setUpLanguage(String scopeName) {
    try {
      GrammarRegistry.getInstance().loadGrammars("textmate/languages.json");
      TextMateLanguage language = TextMateLanguage.create(scopeName, true);
      editor.setEditorLanguage(language);
      try {
        editor.setColorScheme(TextMateColorScheme.create(ThemeRegistry.getInstance()));
      } catch (Exception e) {
        e.printStackTrace();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void loadFile() {
    try (BufferedReader reader = new BufferedReader(new FileReader(currentFile))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append("\n");
      }
      editor.setText(sb.toString());
      hasUnsavedChanges = false;
    } catch (Exception e) {
      Toast.makeText(this, "Failed to read: " + e.getMessage(), Toast.LENGTH_LONG).show();
      finish();
    }
  }

  private void saveFile() {
    try (FileOutputStream out = new FileOutputStream(currentFile)) {
      out.write(editor.getText().toString().getBytes(StandardCharsets.UTF_8));
      hasUnsavedChanges = false;
      Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
    } catch (Exception e) {
      Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }
  }

  private void handleBack() {
    if (hasUnsavedChanges) {
      new MaterialAlertDialogBuilder(this)
          .setTitle("Unsaved changes")
          .setMessage("Save before closing?")
          .setPositiveButton("Save", (d, w) -> { saveFile(); finish(); })
          .setNegativeButton("Discard", (d, w) -> finish())
          .setNeutralButton("Cancel", null)
          .show();
    } else {
      finish();
    }
  }

  @Override
  public void onBackPressed() {
    handleBack();
  }
}