/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.alonso.darkline.terminal.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alonso.darkline.terminal.activity.SettingsActivity;
import com.alonso.darkline.terminal.databinding.FragmentSessionListBinding;
import com.alonso.darkline.terminal.list.SessionListAdapter;
import com.alonso.darkline.terminal.service.TerminalSessionService;

public class SessionListFragment extends Fragment {

  public interface Callback {
    TerminalSessionService getService();
    void onTabSelected(int position);
    void onTabClosed(int position);
    void onNewSessionClicked(View anchor);
    void onRequestCloseDrawer();
  }

  private FragmentSessionListBinding binding;
  private SessionListAdapter adapter;
  private Callback callback;

  public void setCallback(Callback callback) {
    this.callback = callback;
  }

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
      @Nullable Bundle savedInstanceState) {
    binding = FragmentSessionListBinding.inflate(inflater, parent, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    RecyclerView recycler = binding.sessionTabs;
    recycler.setLayoutManager(new LinearLayoutManager(getContext()));

    adapter = new SessionListAdapter(null, new SessionListAdapter.Listener() {
      @Override
      public void onTabSelected(int position) {
        if (callback != null) callback.onTabSelected(position);
      }

      @Override
      public void onTabClosed(int position) {
        if (callback != null) callback.onTabClosed(position);
      }
    });
    recycler.setAdapter(adapter);

    binding.btnNewSession.setOnClickListener(v -> {
      if (callback != null) callback.onNewSessionClicked(v);
    });

    binding.btnOpenSettings.setOnClickListener(v -> {
      startActivity(new Intent(getContext(), SettingsActivity.class));
      if (callback != null) callback.onRequestCloseDrawer();
    });

    refresh();
  }

  public void refresh() {
    if (adapter == null || callback == null) return;
    TerminalSessionService service = callback.getService();
    if (service == null) return;
    adapter.updateSessions(service.getSessions());
  }

  public void setSelectedPosition(int position) {
    if (adapter != null) adapter.setSelectedPosition(position);
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }
}