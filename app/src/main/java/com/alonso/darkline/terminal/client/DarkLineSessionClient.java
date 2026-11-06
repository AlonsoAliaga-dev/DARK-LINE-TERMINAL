/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.alonso.darkline.terminal.client;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.Log;

import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;

public class DarkLineSessionClient implements TerminalSessionClient {

  public interface Callback {
    void onTextChanged(TerminalSession session);

    void onTitleChanged(TerminalSession session);

    void onSessionFinished(TerminalSession session);
  }

  private final Context appContext;
  private final Callback callback;

  public DarkLineSessionClient(Context context, Callback callback) {
    this.appContext = context.getApplicationContext();
    this.callback = callback;
  }

  @Override
  public void onTextChanged(TerminalSession changedSession) {
    callback.onTextChanged(changedSession);
  }

  @Override
  public void onTitleChanged(TerminalSession changedSession) {
    callback.onTitleChanged(changedSession);
  }

  @Override
  public void onSessionFinished(TerminalSession finishedSession) {
    callback.onSessionFinished(finishedSession);
  }

  @Override
  public void onCopyTextToClipboard(TerminalSession session, String text) {
    if (text == null || text.isEmpty())
      return;
    ClipboardManager clipboard = (ClipboardManager) appContext
        .getSystemService(Context.CLIPBOARD_SERVICE);
    if (clipboard != null) {
      clipboard.setPrimaryClip(ClipData.newPlainText("terminal", text));
    }
  }

  @Override
  public void onPasteTextFromClipboard(TerminalSession session) {
    ClipboardManager clipboard = (ClipboardManager) appContext
        .getSystemService(Context.CLIPBOARD_SERVICE);
    if (clipboard == null || !clipboard.hasPrimaryClip())
      return;

    ClipData clip = clipboard.getPrimaryClip();
    if (clip == null || clip.getItemCount() == 0)
      return;

    ClipData.Item item = clip.getItemAt(0);
    CharSequence pasted = item.getText();
    if (pasted == null || pasted.length() == 0)
      return;

    String text = pasted.toString();
    if (text.isEmpty() || session == null || session.getEmulator() == null)
      return;

    session.getEmulator().paste(text);
  }

  @Override
  public void onBell(TerminalSession session) {
  }

  @Override
  public void onColorsChanged(TerminalSession session) {
  }

  @Override
  public void onTerminalCursorStateChange(boolean state) {
  }

  @Override
  public Integer getTerminalCursorStyle() {
    return null;
  }

  @Override
  public void logError(String tag, String message) {
    Log.e(tag, message);
  }

  @Override
  public void logWarn(String tag, String message) {
    Log.w(tag, message);
  }

  @Override
  public void logInfo(String tag, String message) {
    Log.i(tag, message);
  }

  @Override
  public void logDebug(String tag, String message) {
    Log.d(tag, message);
  }

  @Override
  public void logVerbose(String tag, String message) {
    Log.v(tag, message);
  }

  @Override
  public void logStackTraceWithMessage(String tag, String message, Exception e) {
    Log.e(tag, message, e);
  }

  @Override
  public void logStackTrace(String tag, Exception e) {
    Log.e(tag, "uncaught exception", e);
  }
  
  @Override
  public void setTerminalShellPid(TerminalSession session, int pid) {
    
  }
}