/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.alonso.darkline.terminal.list;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.alonso.darkline.terminal.databinding.ItemSessionBinding;
import com.alonso.darkline.terminal.list.item.SessionItem;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

public class SessionListAdapter extends RecyclerView.Adapter<SessionListAdapter.ViewHolder> {

  public interface Listener {
    void onTabSelected(int position);
    void onTabClosed(int position);
  }

  private final List<SessionItem> tabs = new ArrayList<>();
  private final Listener listener;
  private int selectedPosition = 0;

  public SessionListAdapter(List<SessionItem> tabs, Listener listener) {
    if (tabs != null) this.tabs.addAll(tabs);
    this.listener = listener;
    setHasStableIds(true);
  }

  public void updateSessions(List<SessionItem> newSessions) {
    tabs.clear();
    if (newSessions != null) tabs.addAll(newSessions);
    if (selectedPosition >= tabs.size()) selectedPosition = Math.max(0, tabs.size() - 1);
    notifyDataSetChanged();
  }

  public void setSelectedPosition(int position) {
    int previous = selectedPosition;
    selectedPosition = position;
    if (previous >= 0 && previous < tabs.size()) notifyItemChanged(previous);
    if (selectedPosition >= 0 && selectedPosition < tabs.size()) notifyItemChanged(selectedPosition);
  }

  @Override
  public long getItemId(int position) {
    return tabs.get(position).id;
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    ItemSessionBinding binding = ItemSessionBinding
        .inflate(LayoutInflater.from(parent.getContext()), parent, false);
    return new ViewHolder(binding);
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    SessionItem tab = tabs.get(position);
    holder.binding.sessionTitle.setText(tab.getDisplayTitle());

    boolean isSelected = position == selectedPosition;
    MaterialCardView card = holder.binding.sessionCard;

    int colorSurfaceContainerHigh = MaterialColors.getColor(
        holder.itemView, com.google.android.material.R.attr.colorSurfaceContainerHigh);
    int colorSecondaryContainer = MaterialColors.getColor(
        holder.itemView, com.google.android.material.R.attr.colorSecondaryContainer);
    int colorOnSecondaryContainer = MaterialColors.getColor(
        holder.itemView, com.google.android.material.R.attr.colorOnSecondaryContainer);
    int colorOnSurface = MaterialColors.getColor(
        holder.itemView, com.google.android.material.R.attr.colorOnSurface);

    if (isSelected) {
      card.setCardBackgroundColor(ColorStateList.valueOf(colorSecondaryContainer));
      holder.binding.sessionTitle.setTextColor(colorOnSecondaryContainer);
      holder.binding.sessionTitle.setTypeface(null, Typeface.BOLD);
    } else {
      card.setCardBackgroundColor(ColorStateList.valueOf(colorSurfaceContainerHigh));
      holder.binding.sessionTitle.setTextColor(colorOnSurface);
      holder.binding.sessionTitle.setTypeface(null, Typeface.NORMAL);
    }

    holder.itemView.setOnClickListener(v -> {
      int pos = holder.getBindingAdapterPosition();
      if (pos != RecyclerView.NO_POSITION && listener != null) {
        listener.onTabSelected(pos);
      }
    });

    holder.itemView.setOnLongClickListener(v -> {
      int pos = holder.getBindingAdapterPosition();
      if (pos != RecyclerView.NO_POSITION && listener != null) {
        listener.onTabClosed(pos);
        return true;
      }
      return false;
    });
  }

  @Override
  public int getItemCount() {
    return tabs.size();
  }

  static class ViewHolder extends RecyclerView.ViewHolder {
    final ItemSessionBinding binding;

    ViewHolder(ItemSessionBinding binding) {
      super(binding.getRoot());
      this.binding = binding;
    }
  }
}