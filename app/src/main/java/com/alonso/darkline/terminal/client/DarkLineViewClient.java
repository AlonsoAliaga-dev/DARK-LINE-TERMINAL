/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.alonso.darkline.terminal.client;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.util.Log;

import com.blankj.utilcode.util.KeyboardUtils;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

public class DarkLineViewClient implements TerminalViewClient {

  public interface KeyModifierState {
    boolean isCtrlToggled();

    boolean isAltToggled();

    void consumeCtrlToggle();

    void consumeAltToggle();
  }

  private static final String PREF_NAME = "darkline_terminal_prefs";
  private static final String KEY_FONT_SIZE = "font_size_sp";

  private static final int MIN_FONT_SIZE_SP = 5;
  private static final int MAX_FONT_SIZE_SP = 50;
  private static final int DEFAULT_FONT_SIZE_SP = 7;
  private static final int FONT_SIZE_STEP_SP = 1;

  private final TerminalView terminalView;
  private final KeyModifierState modifierState;
  private final SharedPreferences sharedPreferences;
  private int currentFontSizeSp;

  public DarkLineViewClient(TerminalView terminalView, KeyModifierState modifierState) {
    this.terminalView = terminalView;
    this.modifierState = modifierState;

    // دریافت SharedPreferences
    Context context = terminalView.getContext();
    sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

    // بازیابی اندازه ذخیره‌شده یا استفاده از مقدار پیش‌فرض
    currentFontSizeSp = sharedPreferences.getInt(KEY_FONT_SIZE, DEFAULT_FONT_SIZE_SP);

    // اعمال اندازه
    applyFontSize();
  }

  private void applyFontSize() {
    float scaledDensity = terminalView.getResources().getDisplayMetrics().scaledDensity;
    terminalView.setTextSize(Math.round(currentFontSizeSp * scaledDensity));
  }

  private void saveFontSize() {
    sharedPreferences.edit().putInt(KEY_FONT_SIZE, currentFontSizeSp).apply();
  }

  @Override
  public float onScale(float scale) {
    if (scale < 0.9f || scale > 1.1f) {
      boolean increase = scale > 1.1f;
      currentFontSizeSp = increase
          ? Math.min(MAX_FONT_SIZE_SP, currentFontSizeSp + FONT_SIZE_STEP_SP)
          : Math.max(MIN_FONT_SIZE_SP, currentFontSizeSp - FONT_SIZE_STEP_SP);

      applyFontSize();
      saveFontSize(); // ذخیره‌سازی تغییرات

      return 1.0f;
    }
    return scale;
  }

  // متد جدید برای تنظیم دستی اندازه
  public void setFontSize(int sizeSp) {
    if (sizeSp < MIN_FONT_SIZE_SP || sizeSp > MAX_FONT_SIZE_SP) {
      throw new IllegalArgumentException(
          "Font size must be between " + MIN_FONT_SIZE_SP + " and " + MAX_FONT_SIZE_SP);
    }
    currentFontSizeSp = sizeSp;
    applyFontSize();
    saveFontSize();
  }

  // متد جدید برای دریافت اندازه فعلی
  public int getFontSize() {
    return currentFontSizeSp;
  }

  // متد جدید برای بازنشانی به اندازه پیش‌فرض
  public void resetFontSize() {
    currentFontSizeSp = DEFAULT_FONT_SIZE_SP;
    applyFontSize();
    saveFontSize();
  }

  @Override
  public void onSingleTapUp(MotionEvent e) {
    terminalView.setFocusable(true);
    terminalView.setFocusableInTouchMode(true);
    terminalView.requestFocus();
    KeyboardUtils.showSoftInput(terminalView);
  }

  @Override
  public boolean shouldBackButtonBeMappedToEscape() {
    return false;
  }

  @Override
  public boolean shouldEnforceCharBasedInput() {
    return true;
  }

  @Override
  public boolean shouldUseCtrlSpaceWorkaround() {
    return false;
  }

  @Override
  public boolean isTerminalViewSelected() {
    return true;
  }

  @Override
  public void copyModeChanged(boolean copyMode) {
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session) {
    return false;
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent e) {
    return false;
  }

  @Override
  public boolean onLongPress(MotionEvent event) {
    return false;
  }

  @Override
  public boolean readControlKey() {
    return modifierState.isCtrlToggled();
  }

  @Override
  public boolean readAltKey() {
    return modifierState.isAltToggled();
  }

  @Override
  public boolean readShiftKey() {
    return false;
  }

  @Override
  public boolean readFnKey() {
    return false;
  }

  @Override
  public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session) {
    if (modifierState.isCtrlToggled())
      modifierState.consumeCtrlToggle();
    if (modifierState.isAltToggled())
      modifierState.consumeAltToggle();
    return false;
  }

  @Override
  public void onEmulatorSet() {
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
}