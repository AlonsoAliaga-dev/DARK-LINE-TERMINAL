/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.alonso.darkline.terminal.activity;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.alonso.darkline.terminal.ai.AiPanelController;
import com.alonso.darkline.terminal.client.DarkLineViewClient;
import com.alonso.darkline.terminal.databinding.ActivityTerminalBinding;
import com.alonso.darkline.terminal.databinding.DrawerTerminalBinding;
import com.alonso.darkline.terminal.distro.DistroInfo;
import com.alonso.darkline.terminal.distro.DistroInstallDialog;
import com.alonso.darkline.terminal.distro.DistroManager;
import com.alonso.darkline.terminal.distro.DistroSelectorDialog;
import com.alonso.darkline.terminal.distro.ImportRootfsDialog;
import com.alonso.darkline.terminal.distro.RootfsImporter;
import com.alonso.darkline.terminal.fragment.FileManagerFragment;
import com.alonso.darkline.terminal.installer.LinuxBootstrap;
import com.alonso.darkline.terminal.fragment.SessionListFragment;
import com.alonso.darkline.terminal.list.SessionPagerAdapter;
import com.alonso.darkline.terminal.list.item.SessionItem;
import com.alonso.darkline.terminal.service.TerminalSessionService;
import com.alonso.darkline.terminal.settings.SettingsManager;
import com.alonso.darkline.terminal.R;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class TerminalActivity extends AppCompatActivity
    implements DarkLineViewClient.KeyModifierState, TerminalSessionService.SessionListener,
    SessionListFragment.Callback {

  public static final String EXTRA_WORKING_DIR = "working_dir";
  public static final String EXTRA_COMMAND = "command";

  private ActivityTerminalBinding binding;

  private TerminalView terminalView;
  private Button keyEsc, keyTab, keyCtrl, keyAlt;
  private Button keyUp, keyDown, keyLeft, keyRight;
  private Button keySlash, keyAnd;

  private DrawerLayout drawerLayout;
  private LinearLayout sessionPanel;
  private DrawerTerminalBinding drawerBinding;

  private ViewPager2 drawerViewPager;
  private TabLayout drawerTabs;
  private SessionListFragment sessionListFragment;
  private FileManagerFragment fileManagerFragment;
  private SessionPagerAdapter pagerAdapter;

  private View aiPanelView;
  private AiPanelController aiPanelController;

  private TerminalSessionService service;
  private boolean isBound = false;
  private int currentTabIndex = -1;

  private boolean ctrlToggled = false;
  private boolean altToggled = false;
  private int defaultKeyTextColor;

  private SettingsManager settings;
  private DistroManager distroManager;

  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private boolean distroDialogShownThisLaunch = false;

  private final ServiceConnection connection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName name, IBinder binderObj) {
      service = ((TerminalSessionService.LocalBinder) binderObj).getService();
      isBound = true;
      service.setUiListener(TerminalActivity.this);

      if (sessionListFragment != null) {
        sessionListFragment.refresh();
      }

      String command = getIntent().getStringExtra(EXTRA_COMMAND);
      if (command != null && !command.isEmpty()) {
        if (!distroManager.isInstalled()) {
          Toast.makeText(TerminalActivity.this, "No distro installed!", Toast.LENGTH_LONG).show();
          return;
        }
        openInstalledLinuxSession();
        getIntent().removeExtra(EXTRA_COMMAND);
        return;
      }

      List<SessionItem> sessions = service.getSessions();
      if (sessions.isEmpty()) {
        terminalView.setVisibility(View.VISIBLE);

        if (distroManager.isInstalled()) {
          openInstalledLinuxSession();
        } else {
          addNewAndroidSession();
          if (!distroDialogShownThisLaunch) {
            distroDialogShownThisLaunch = true;
            mainHandler.postDelayed(TerminalActivity.this::showDistroSelectorIfPossible, 700);
          }
        }
      } else {
        int index = (currentTabIndex >= 0 && currentTabIndex < sessions.size()) ? currentTabIndex
            : sessions.size() - 1;
        switchToTab(index);
      }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
      isBound = false;
      service = null;
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    binding = ActivityTerminalBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());

    defaultKeyTextColor = MaterialColors.getColor(
        binding.getRoot(),
        com.google.android.material.R.attr.colorOnSurfaceVariant);

    settings = new SettingsManager(this);
    distroManager = new DistroManager(this);

    getWindow().setStatusBarColor(Color.TRANSPARENT);
    getWindow().setNavigationBarColor(Color.TRANSPARENT);

    setupToolbar();

    terminalView = binding.terminalView;

    keyEsc = binding.keyEsc;
    keyTab = binding.keyTab;
    keyUp = binding.keyUp;
    keyCtrl = binding.keyCtrl;
    keyAlt = binding.keyAlt;
    keySlash = binding.keySlash;
    keyLeft = binding.keyLeft;
    keyDown = binding.keyDown;
    keyRight = binding.keyRight;
    keyAnd = binding.keyAnd;

    drawerLayout = binding.drawer;
    sessionPanel = binding.sessionPanel;

    drawerBinding = DrawerTerminalBinding.bind(sessionPanel);
    setupDrawerPager();

    aiPanelView = findViewById(R.id.aiPanel);
    aiPanelController = new AiPanelController(this, drawerLayout, aiPanelView);

    terminalView.setTerminalViewClient(new DarkLineViewClient(terminalView, this));

    applyExtraKeysVisibility();

    keyEsc.setOnClickListener(v -> sendKeyEvent(KeyEvent.KEYCODE_ESCAPE));
    keyTab.setOnClickListener(v -> sendKeyEvent(KeyEvent.KEYCODE_TAB));
    keyUp.setOnClickListener(v -> sendKeyEvent(KeyEvent.KEYCODE_DPAD_UP));
    keyDown.setOnClickListener(v -> sendKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN));
    keyLeft.setOnClickListener(v -> sendKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT));
    keyRight.setOnClickListener(v -> sendKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT));
    keySlash.setOnClickListener(v -> typeText("/"));
    keyAnd.setOnClickListener(v -> typeText("&"));

    keyCtrl.setOnClickListener(v -> {
      ctrlToggled = !ctrlToggled;
      keyCtrl.setTextColor(ctrlToggled ? Color.RED : defaultKeyTextColor);
    });

    keyAlt.setOnClickListener(v -> {
      altToggled = !altToggled;
      keyAlt.setTextColor(altToggled ? Color.RED : defaultKeyTextColor);
    });
    
    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

    getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
      @Override
      public void handleOnBackPressed() {
        if (fileManagerFragment != null
            && drawerViewPager.getCurrentItem() == 1
            && fileManagerFragment.onBackPressed()) {
          return;
        }
        
        if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
          drawerLayout.closeDrawer(GravityCompat.END);
        } else if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
          drawerLayout.closeDrawer(GravityCompat.START);
        } else {
          setEnabled(false);
          getOnBackPressedDispatcher().onBackPressed();
        }
      }
    });

    if (Build.VERSION.SDK_INT >= 33) {
      if (ContextCompat.checkSelfPermission(this,
          Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(this,
            new String[] { Manifest.permission.POST_NOTIFICATIONS }, 4821);
      }
    }
    if (checkSelfPermission(
        Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED
        || checkSelfPermission(
            Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
      requestPermissions(new String[] { Manifest.permission.READ_EXTERNAL_STORAGE,
          Manifest.permission.WRITE_EXTERNAL_STORAGE }, 1000);
    }
  }

  private void setupDrawerPager() {
    drawerViewPager = drawerBinding.drawerViewPager;
    drawerTabs = drawerBinding.drawerTabs;

    sessionListFragment = new SessionListFragment();
    sessionListFragment.setCallback(this);

    fileManagerFragment = new FileManagerFragment();

    pagerAdapter = new SessionPagerAdapter(this, sessionListFragment, fileManagerFragment);
    drawerViewPager.setAdapter(pagerAdapter);
    
    drawerViewPager.setUserInputEnabled(false);

    new TabLayoutMediator(drawerTabs, drawerViewPager,
        (tab, position) -> tab.setText(position == 0 ? "Sessions" : "Files")
    ).attach();
  }

  private void setupToolbar() {
    binding.toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
    binding.toolbar.inflateMenu(R.menu.menu_terminal);
    binding.toolbar.setOnMenuItemClickListener(item -> {
      int id = item.getItemId();
      if (id == R.id.action_ai) {
        aiPanelController.openPanel();
        return true;
      } else if (id == R.id.action_settings) {
        startActivity(new Intent(this, SettingsActivity.class));
        return true;
      } else if (id == R.id.action_new_session) {
        showNewSessionMenu();
        return true;
      }
      return false;
    });
  }

  private void showNewSessionMenu() {
    PopupMenu popup = new PopupMenu(this, binding.toolbar);
    popup.getMenu().add(0, 1, 0, "Android");
    popup.getMenu().add(0, 2, 1, "Linux");
    popup.setOnMenuItemClickListener(item -> {
      if (item.getItemId() == 2) {
        addNewLinuxSession();
      } else {
        addNewAndroidSession();
      }
      return true;
    });
    popup.show();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (settings != null) {
      applyExtraKeysVisibility();
    }
  }

  private void applyExtraKeysVisibility() {
    binding.extraKeysContainer
        .setVisibility(settings.isShowExtraKeys() ? View.VISIBLE : View.GONE);
  }

  private void ensureResolvConf(String rootfsPath) {
    if (rootfsPath == null || rootfsPath.isEmpty()) return;

    try {
      File rootfs = new File(getFilesDir(), rootfsPath);
      File etc = new File(rootfs, "etc");
      if (!etc.exists()) {
        etc.mkdirs();
      }

      File resolvConf = new File(etc, "resolv.conf");

      if (resolvConf.exists() && resolvConf.length() > 0) {
        String content = readFile(resolvConf);
        if (content.contains("nameserver")) {
          return;
        }
      }

      Runtime.getRuntime().exec(new String[] { "rm", "-rf", resolvConf.getAbsolutePath() });
      Thread.sleep(100);

      FileOutputStream out = new FileOutputStream(resolvConf);
      out.write("nameserver 8.8.8.8\nnameserver 8.8.4.4\n".getBytes(StandardCharsets.UTF_8));
      out.close();
      Runtime.getRuntime().exec(new String[] { "chmod", "644", resolvConf.getAbsolutePath() });

    } catch (Exception e) {
    }
  }

  private String readFile(File file) {
    try {
      BufferedReader reader = new BufferedReader(new FileReader(file));
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append("\n");
      }
      reader.close();
      return sb.toString();
    } catch (Exception e) {
      return "";
    }
  }

  private void addNewAndroidSession() {
    if (service == null) {
      Toast.makeText(this, "Service not connected", Toast.LENGTH_SHORT).show();
      return;
    }
    service.createSession(null);
    if (sessionListFragment != null) sessionListFragment.refresh();
    List<SessionItem> sessions = service.getSessions();
    switchToTab(sessions.size() - 1);
  }

  private void addNewLinuxSession() {
    if (service == null) {
      Toast.makeText(this, "Service not connected", Toast.LENGTH_SHORT).show();
      return;
    }

    if (distroManager.isInstalled()) {
      openInstalledLinuxSession();
    } else {
      showDistroSelector();
    }
  }

  private void openInstalledLinuxSession() {
    String rootfsPath = distroManager.getInstalledRootfsPath();
    if (rootfsPath == null || rootfsPath.isEmpty()) {
      Toast.makeText(this, "Installed distro path missing", Toast.LENGTH_LONG).show();
      return;
    }

    ensureResolvConf(rootfsPath);

    try {
      service.createLinuxSession();
    } catch (Exception e) {
      Toast.makeText(this, "Failed to start: " + e.getMessage(), Toast.LENGTH_LONG).show();
      return;
    }

    if (sessionListFragment != null) sessionListFragment.refresh();
    List<SessionItem> sessions = service.getSessions();
    switchToTab(sessions.size() - 1);
    TerminalSession session = currentSession();

    if (session != null) {
      String startup = settings.getStartupCommand();
      if (startup == null || startup.isEmpty()) {
        startup = distroManager.getInstalledLoginCommand();
      }
      if (startup != null && !startup.isEmpty()) {
        byte[] data = (startup + "\n").getBytes(StandardCharsets.UTF_8);
        session.write(data, 0, data.length);
      }
    }

    String command = getIntent().getStringExtra(EXTRA_COMMAND);
    if (command != null && !command.isEmpty()) {
      if (session != null) {
        byte[] data = (command + "\n").getBytes(StandardCharsets.UTF_8);
        session.write(data, 0, data.length);
      }
    }
  }

  private void showDistroSelectorIfPossible() {
    if (isFinishing() || isDestroyed()) return;
    if (distroManager.isInstalled()) return;
    if (getSupportFragmentManager().findFragmentByTag("distro_selector") != null) return;
    showDistroSelector();
  }

  private void showDistroSelector() {
    if (getSupportFragmentManager().findFragmentByTag("distro_selector") != null) {
      return;
    }

    DistroSelectorDialog selector = new DistroSelectorDialog();
    selector.setListener(new DistroSelectorDialog.Listener() {
      @Override
      public void onDistroSelected(DistroInfo distro) {
        if (service == null) return;

        TerminalSession session = currentSession();
        if (session == null) {
          Toast.makeText(TerminalActivity.this, "No active session", Toast.LENGTH_SHORT).show();
          return;
        }

        mainHandler.postDelayed(() -> showDistroInstallDialog(distro, session), 300);
      }

      @Override
      public void onLocalImportClicked() {
        mainHandler.postDelayed(TerminalActivity.this::showImportRootfsDialog, 300);
      }
    });
    selector.show(getSupportFragmentManager(), "distro_selector");
  }

  private void showImportRootfsDialog() {
    if (isFinishing() || isDestroyed()) return;
    if (getSupportFragmentManager().findFragmentByTag("import_rootfs") != null) return;

    ImportRootfsDialog dialog = new ImportRootfsDialog();
    dialog.setListener((config, archiveUri) -> startLocalImport(config, archiveUri));
    dialog.show(getSupportFragmentManager(), "import_rootfs");
  }

  private void showDistroInstallDialog(DistroInfo distro, TerminalSession session) {
    if (isFinishing() || isDestroyed()) return;
    if (getSupportFragmentManager().findFragmentByTag("distro_install") != null) return;

    DistroInstallDialog installDialog = new DistroInstallDialog();
    installDialog.setDistro(distro);
    installDialog.setSession(session);
    installDialog.setListener(new DistroInstallDialog.Listener() {
      @Override
      public void onInstallSuccess(DistroInfo info) {
        Toast.makeText(TerminalActivity.this,
            info.getDisplayName() + " installed successfully", Toast.LENGTH_LONG).show();
      }

      @Override
      public void onInstallError(String message) {
      }
    });
    installDialog.show(getSupportFragmentManager(), "distro_install");
  }

  private void startLocalImport(ImportRootfsDialog.ImportConfig config, Uri archiveUri) {
    if (distroManager.isInstalled()) {
      new MaterialAlertDialogBuilder(this)
          .setTitle("Replace existing distro?")
          .setMessage("An existing distro (" + distroManager.getInstalledName() +
              ") is installed. It will be deleted before importing the new one.")
          .setPositiveButton("Continue", (d, w) -> doLocalImport(config, archiveUri))
          .setNegativeButton("Cancel", null)
          .show();
    } else {
      doLocalImport(config, archiveUri);
    }
  }

  private void doLocalImport(ImportRootfsDialog.ImportConfig config, Uri archiveUri) {
    if (distroManager.isInstalled()) {
      File oldRootfs = distroManager.getInstalledRootfsDir();
      distroManager.clear();
      if (oldRootfs != null && oldRootfs.exists()) {
        LinuxBootstrap.uninstall(oldRootfs, null);
      }
    }

    TerminalSession session = currentSession();
    if (session == null) {
      Toast.makeText(this, "No active session", Toast.LENGTH_SHORT).show();
      return;
    }

    RootfsImporter.importRootfs(this, session, archiveUri, config,
        new RootfsImporter.ImportListener() {
          @Override
          public void onSuccess() {
            runOnUiThread(() -> Toast.makeText(TerminalActivity.this,
                "RootFS imported successfully", Toast.LENGTH_LONG).show());
          }

          @Override
          public void onError(String message) {
            runOnUiThread(() -> Toast.makeText(TerminalActivity.this,
                message, Toast.LENGTH_LONG).show());
          }
        });
  }

  private void switchToTab(int position) {
    if (service == null)
      return;
    List<SessionItem> sessions = service.getSessions();
    if (position < 0 || position >= sessions.size())
      return;

    if (currentTabIndex >= 0 && currentTabIndex < sessions.size()) {
      SessionItem previous = sessions.get(currentTabIndex);
      previous.setAttached(false);
      service.markSessionAttached(previous.session, false);
    }

    currentTabIndex = position;
    SessionItem tab = sessions.get(position);
    tab.setAttached(true);
    service.markSessionAttached(tab.session, true);

    terminalView.attachSession(tab.session);
    terminalView.setVisibility(View.VISIBLE);

    if (sessionListFragment != null) {
      sessionListFragment.setSelectedPosition(position);
    }

    String title = tab.getDisplayTitle();
    binding.toolbar.setTitle(title == null || title.isEmpty() ? "Terminal" : title);
  }

  private void closeTab(int position) {
    if (service == null)
      return;
    List<SessionItem> sessions = service.getSessions();
    if (position < 0 || position >= sessions.size())
      return;
    service.removeSession(sessions.get(position).session);
    if (sessionListFragment != null) sessionListFragment.refresh();
    sessions = service.getSessions();
    if (sessions.isEmpty()) {
      finish();
      return;
    }
    int newIndex = Math.min(position, sessions.size() - 1);
    switchToTab(newIndex);
  }

  @Nullable
  private TerminalSession currentSession() {
    if (service == null)
      return null;
    List<SessionItem> sessions = service.getSessions();
    if (currentTabIndex < 0 || currentTabIndex >= sessions.size())
      return null;
    return sessions.get(currentTabIndex).session;
  }

  private int indexOfSession(TerminalSession session) {
    if (service == null)
      return -1;
    List<SessionItem> sessions = service.getSessions();
    for (int i = 0; i < sessions.size(); i++) {
      if (sessions.get(i).session == session)
        return i;
    }
    return -1;
  }

  private void sendKeyEvent(int keyCode) {
    long now = SystemClock.uptimeMillis();
    terminalView.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
    terminalView.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
  }

  private void typeText(String text) {
    TerminalSession session = currentSession();
    if (session == null)
      return;
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    session.write(bytes, 0, bytes.length);
  }

  @Override
  public void onTextChanged(TerminalSession session) {
    if (session == currentSession()) {
      terminalView.invalidate();
    }
  }

  @Override
  public void onTitleChanged(TerminalSession session) {
    int index = indexOfSession(session);
    if (index >= 0 && sessionListFragment != null) {
      sessionListFragment.refresh();
    }
    if (session == currentSession()) {
      String title = session.getTitle();
      if (title == null || title.isEmpty()) title = "Terminal";
      binding.toolbar.setTitle(title);
    }
  }

  @Override
  public void onSessionFinished(TerminalSession session) {
    if (sessionListFragment != null) sessionListFragment.refresh();
    if (service == null)
      return;
    List<SessionItem> sessions = service.getSessions();
    if (sessions.isEmpty()) {
      finish();
      return;
    }
    int newIndex = Math.min(Math.max(currentTabIndex, 0), sessions.size() - 1);
    switchToTab(newIndex);
  }

  @Override
  public boolean isCtrlToggled() {
    return ctrlToggled;
  }

  @Override
  public boolean isAltToggled() {
    return altToggled;
  }

  @Override
  public void consumeCtrlToggle() {
    ctrlToggled = false;
    keyCtrl.setTextColor(defaultKeyTextColor);
  }

  @Override
  public void consumeAltToggle() {
    altToggled = false;
    keyAlt.setTextColor(defaultKeyTextColor);
  }

  @Override
  protected void onStart() {
    super.onStart();
    Intent serviceIntent = new Intent(this, TerminalSessionService.class);
    ContextCompat.startForegroundService(this, serviceIntent);
    bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);
  }

  @Override
  protected void onStop() {
    if (isBound) {
      service.setUiListener(null);
      unbindService(connection);
      isBound = false;
    }
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    if (aiPanelController != null) {
      aiPanelController.getChatManager().saveCurrentConversation();
      aiPanelController.getChatManager().cancelActiveRequest();
    }
    if (isBound) {
      try {
        unbindService(connection);
      } catch (Exception e) {
      }
      isBound = false;
    }
    super.onDestroy();
  }

  // =============== SessionListFragment.Callback ===============

  @Override
  public TerminalSessionService getService() {
    return service;
  }

  @Override
  public void onTabSelected(int position) {
    switchToTab(position);
  }

  @Override
  public void onTabClosed(int position) {
    closeTab(position);
  }

  @Override
  public void onNewSessionClicked(View anchor) {
    PopupMenu popup = new PopupMenu(this, anchor);
    popup.getMenu().add(0, 1, 0, "Android");
    popup.getMenu().add(0, 2, 1, "Linux");
    popup.setOnMenuItemClickListener(item -> {
      if (item.getItemId() == 2) {
        addNewLinuxSession();
      } else {
        addNewAndroidSession();
      }
      return true;
    });
    popup.show();
  }

  @Override
  public void onRequestCloseDrawer() {
    drawerLayout.closeDrawer(GravityCompat.START);
  }
}