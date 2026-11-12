/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.alonso.darkline.terminal.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.alonso.darkline.terminal.R;
import com.alonso.darkline.terminal.activity.TerminalActivity;
import com.alonso.darkline.terminal.client.DarkLineSessionClient;
import com.alonso.darkline.terminal.distro.DistroManager;
import com.alonso.darkline.terminal.list.item.SessionItem;
import com.alonso.darkline.terminal.settings.SettingsManager;
import com.alonso.darkline.terminal.shell.AndroidShellSession;
import com.alonso.darkline.terminal.shell.LinuxShellSession;
import com.termux.terminal.TerminalSession;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TerminalSessionService extends Service {

  private static final String CHANNEL_ID = "terminal_sessions";
  private static final int NOTIFICATION_ID = 4821;
  private static final long AUTO_CLOSE_CHECK_INTERVAL_MS = 30_000L;

  public static final String ACTION_NEW_SESSION = "com.alonso.darkline.terminal.ACTION_NEW_SESSION";
  public static final String ACTION_STOP_ALL = "com.alonso.darkline.terminal.ACTION_STOP_ALL";
  public static final String ACTION_AI_CHAT = "com.alonso.darkline.terminal.ACTION_AI_CHAT";

  public interface SessionListener {
    void onTextChanged(TerminalSession session);

    void onTitleChanged(TerminalSession session);

    void onSessionFinished(TerminalSession session);
  }

  public class LocalBinder extends Binder {
    public TerminalSessionService getService() {
      return TerminalSessionService.this;
    }
  }

  private final IBinder binder = new LocalBinder();
  private final List<SessionItem> sessions = new ArrayList<>();
  private final Handler autoCloseHandler = new Handler(Looper.getMainLooper());
  private int nextSessionId = 1;
  @Nullable
  private SessionListener uiListener;
  private SettingsManager settings;
  private DistroManager distroManager;

  private final Runnable autoCloseRunnable = new Runnable() {
    @Override
    public void run() {
      checkAutoClose();
      autoCloseHandler.postDelayed(this, AUTO_CLOSE_CHECK_INTERVAL_MS);
    }
  };

  private final DarkLineSessionClient.Callback internalCallback = new DarkLineSessionClient.Callback() {
    @Override
    public void onTextChanged(TerminalSession session) {
      SessionItem item = findSessionItem(session);
      if (item != null) {
        item.updateLastActiveTime();
      }
      if (uiListener != null)
        uiListener.onTextChanged(session);
    }

    @Override
    public void onTitleChanged(TerminalSession session) {
      updateNotification();
      if (uiListener != null)
        uiListener.onTitleChanged(session);
    }

    @Override
    public void onSessionFinished(TerminalSession session) {
      removeFromListOnly(session);
      if (uiListener != null)
        uiListener.onSessionFinished(session);
      if (sessions.isEmpty())
        stopServiceIfIdle();
      else
        updateNotification();
    }
  };

  @Override
  public void onCreate() {
    super.onCreate();
    settings = new SettingsManager(this);
    distroManager = new DistroManager(this);
    createNotificationChannel();
    autoCloseHandler.postDelayed(autoCloseRunnable, AUTO_CLOSE_CHECK_INTERVAL_MS);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent != null && intent.getAction() != null) {
      String action = intent.getAction();
      switch (action) {
        case ACTION_NEW_SESSION:
          if (isDistroInstalled()) {
            try {
              createLinuxSession();
            } catch (Exception e) {
              createSession(null);
            }
          } else {
            createSession(null);
          }
          break;
        case ACTION_STOP_ALL:
          List<SessionItem> toClose = new ArrayList<>(sessions);
          for (SessionItem item : toClose) {
            removeSession(item.session);
          }
          break;
        case ACTION_AI_CHAT:
          Intent openApp = new Intent(this, TerminalActivity.class);
          openApp.putExtra(TerminalActivity.EXTRA_OPEN_AI, true);
          openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
          startActivity(openApp);
          break;
      }
    }
    return START_STICKY;
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return binder;
  }

  public void setUiListener(@Nullable SessionListener listener) {
    this.uiListener = listener;
  }

  public List<SessionItem> getSessions() {
    return sessions;
  }

  public SessionItem createSession(@Nullable String workingDir) {
    DarkLineSessionClient client = new DarkLineSessionClient(this, internalCallback);
    TerminalSession session = AndroidShellSession.createSession(this, workingDir, client);
    return registerNewSession(session);
  }

  public SessionItem createLinuxSession() {
    if (!distroManager.isInstalled()) {
      throw new IllegalStateException("No distro installed");
    }
    File rootfs = distroManager.getInstalledRootfsDir();
    if (rootfs == null || !rootfs.isDirectory()) {
      throw new IllegalStateException("Rootfs directory not found");
    }
    String shell = distroManager.getEffectiveShell();

    DarkLineSessionClient client = new DarkLineSessionClient(this, internalCallback);
    TerminalSession session = LinuxShellSession.createProotSession(this, rootfs, shell, client);
    return registerNewSession(session);
  }

  public boolean isDistroInstalled() {
    return distroManager.isInstalled() && distroManager.isRootfsPresent();
  }

  private SessionItem registerNewSession(TerminalSession session) {
    SessionItem tab = new SessionItem(nextSessionId++, session);
    sessions.add(tab);
    startForeground(NOTIFICATION_ID, buildNotification());
    return tab;
  }

  public void removeSession(TerminalSession session) {
    removeFromListOnly(session);
    session.finishIfRunning();
    if (sessions.isEmpty())
      stopServiceIfIdle();
    else
      updateNotification();
  }

  public void markSessionAttached(TerminalSession session, boolean attached) {
    SessionItem item = findSessionItem(session);
    if (item != null) {
      item.setAttached(attached);
    }
  }

  private SessionItem findSessionItem(TerminalSession session) {
    for (SessionItem item : sessions) {
      if (item.session == session) {
        return item;
      }
    }
    return null;
  }

  private void removeFromListOnly(TerminalSession session) {
    Iterator<SessionItem> iterator = sessions.iterator();
    while (iterator.hasNext()) {
      if (iterator.next().session == session) {
        iterator.remove();
        break;
      }
    }
  }

  private void checkAutoClose() {
    int minutes = settings.getAutoCloseMinutes();
    if (minutes <= 0) return;

    long threshold = minutes * 60_000L;
    long now = System.currentTimeMillis();

    List<TerminalSession> toClose = new ArrayList<>();
    for (SessionItem item : sessions) {
      if (item.isAttached()) continue;
      if (now - item.getLastActiveTime() >= threshold) {
        toClose.add(item.session);
      }
    }

    for (TerminalSession session : toClose) {
      removeSession(session);
    }
  }

  private void stopServiceIfIdle() {
    stopForeground(true);
    stopSelf();
  }

  private void updateNotification() {
    if (sessions.isEmpty())
      return;
    NotificationManager nm = getSystemService(NotificationManager.class);
    if (nm != null)
      nm.notify(NOTIFICATION_ID, buildNotification());
  }

  private Notification buildNotification() {
    Intent openIntent = new Intent(this, TerminalActivity.class);
    int flags = PendingIntent.FLAG_UPDATE_CURRENT
        | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
    PendingIntent contentIntent = PendingIntent.getActivity(this, 0, openIntent, flags);

    Intent newSessionIntent = new Intent(this, TerminalSessionService.class);
    newSessionIntent.setAction(ACTION_NEW_SESSION);
    PendingIntent newSessionPi = PendingIntent.getService(this, 1,
        newSessionIntent, flags);

    Intent stopAllIntent = new Intent(this, TerminalSessionService.class);
    stopAllIntent.setAction(ACTION_STOP_ALL);
    PendingIntent stopAllPi = PendingIntent.getService(this, 2,
        stopAllIntent, flags);

    Intent aiIntent = new Intent(this, TerminalActivity.class);
    aiIntent.putExtra(TerminalActivity.EXTRA_OPEN_AI, true);
    aiIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent aiPi = PendingIntent.getActivity(this, 3,
        aiIntent, flags);

    int count = sessions.size();
    String text = count + (count == 1 ? " session running" : " sessions running");

    return new NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.terminal_title))
        .setContentText(text)
        .setSmallIcon(R.drawable.app_icon_notification)
        .setOngoing(true)
        .setContentIntent(contentIntent)
        .addAction(R.drawable.ic_add_24, "New Session", newSessionPi)
        .addAction(R.drawable.ic_stop_24, "Stop All", stopAllPi)
        .addAction(R.drawable.ic_ai_24, "AI Chat", aiPi)
        .build();
  }

  private void createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationManager nm = getSystemService(NotificationManager.class);
      if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Terminal sessions",
            NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        nm.createNotificationChannel(channel);
      }
    }
  }

  @Override
  public void onDestroy() {
    autoCloseHandler.removeCallbacks(autoCloseRunnable);
    for (SessionItem tab : sessions) {
      tab.session.finishIfRunning();
    }
    sessions.clear();
    super.onDestroy();
  }
}