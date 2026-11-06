/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.alonso.darkline.terminal.settings;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.alonso.darkline.terminal.R;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class BindMountAdapter extends RecyclerView.Adapter<BindMountAdapter.ViewHolder> {

  public interface Listener {
    void onDelete(int position);
  }

  private final List<String> mounts;
  private final Listener listener;

  public BindMountAdapter(List<String> mounts, Listener listener) {
    this.mounts = mounts;
    this.listener = listener;
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View view = LayoutInflater.from(parent.getContext())
        .inflate(R.layout.item_bind_mount, parent, false);
    return new ViewHolder(view);
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    String mount = mounts.get(position);
    holder.txtBindMount.setText(mount);
    holder.btnDeleteBindMount.setOnClickListener(v -> {
      int pos = holder.getBindingAdapterPosition();
      if (pos != RecyclerView.NO_POSITION && listener != null) {
        listener.onDelete(pos);
      }
    });
  }

  @Override
  public int getItemCount() {
    return mounts.size();
  }

  static class ViewHolder extends RecyclerView.ViewHolder {
    final TextView txtBindMount;
    final MaterialButton btnDeleteBindMount;

    ViewHolder(View itemView) {
      super(itemView);
      txtBindMount = itemView.findViewById(R.id.txtBindMount);
      btnDeleteBindMount = itemView.findViewById(R.id.btnDeleteBindMount);
    }
  }
}