/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.alonso.darkline.terminal.list.item;

import com.termux.terminal.TerminalSession;

public class SessionItem {

  public final int id;
  public final TerminalSession session;
  private String customTitle;
  private long lastActiveTime;
  private boolean isAttached;

  public SessionItem(int id, TerminalSession session) {
    this.id = id;
    this.session = session;
    this.lastActiveTime = System.currentTimeMillis();
    this.isAttached = false;
  }

  public String getDisplayTitle() {
    if (customTitle != null && !customTitle.isEmpty()) return customTitle;
    String shellTitle = session.getTitle();
    if (shellTitle != null && !shellTitle.isEmpty()) return shellTitle;
    return "Session " + id;
  }

  public void setCustomTitle(String customTitle) {
    this.customTitle = customTitle;
  }

  public long getLastActiveTime() {
    return lastActiveTime;
  }

  public void setLastActiveTime(long time) {
    this.lastActiveTime = time;
  }

  public void updateLastActiveTime() {
    this.lastActiveTime = System.currentTimeMillis();
  }

  public boolean isAttached() {
    return isAttached;
  }

  public void setAttached(boolean attached) {
    this.isAttached = attached;
    if (attached) {
      updateLastActiveTime();
    }
  }
}