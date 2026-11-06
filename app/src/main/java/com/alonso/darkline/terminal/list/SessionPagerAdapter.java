/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.alonso.darkline.terminal.list;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.alonso.darkline.terminal.fragment.FileManagerFragment;
import com.alonso.darkline.terminal.fragment.SessionListFragment;

public class SessionPagerAdapter extends FragmentStateAdapter {

  private final SessionListFragment sessionFragment;
  private final FileManagerFragment fileFragment;

  public SessionPagerAdapter(@NonNull FragmentActivity activity,
      SessionListFragment sessionFragment, FileManagerFragment fileFragment) {
    super(activity);
    this.sessionFragment = sessionFragment;
    this.fileFragment = fileFragment;
  }

  @NonNull
  @Override
  public Fragment createFragment(int position) {
    return position == 0 ? sessionFragment : fileFragment;
  }

  @Override
  public int getItemCount() {
    return 2;
  }
}