/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.alonso.darkline.terminal.distro;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alonso.darkline.terminal.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class DistroSelectorDialog extends DialogFragment {

  public interface Listener {
    void onDistroSelected(DistroInfo distro);

    void onLocalImportClicked();
  }

  private Listener listener;
  private DistroRegistry registry;
  private DistroAdapter adapter;
  private TextView txtLoading;
  private TextView txtError;
  private RecyclerView recycler;
  private MaterialButton btnInstallLocal;

  private final Handler mainHandler = new Handler(Looper.getMainLooper());

  public void setListener(Listener listener) {
    this.listener = listener;
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
    registry = new DistroRegistry(requireContext());

    View view = LayoutInflater.from(requireContext())
        .inflate(R.layout.dialog_distro_selector, null);
    txtLoading = view.findViewById(R.id.txtLoading);
    txtError = view.findViewById(R.id.txtError);
    recycler = view.findViewById(R.id.recyclerDistros);
    btnInstallLocal = view.findViewById(R.id.btnInstallLocal);

    adapter = new DistroAdapter();
    recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
    recycler.setAdapter(adapter);

    btnInstallLocal.setOnClickListener(v -> {
      dismissAllowingStateLoss();
      if (listener != null) {
        listener.onLocalImportClicked();
      }
    });

    return new MaterialAlertDialogBuilder(requireContext())
        .setTitle("Select Linux distribution")
        .setView(view)
        .setNegativeButton("Cancel", null)
        .create();
  }

  @Override
  public void onStart() {
    super.onStart();
    loadDistros();
  }

  private void loadDistros() {
    txtLoading.setVisibility(View.VISIBLE);
    txtError.setVisibility(View.GONE);
    recycler.setVisibility(View.GONE);

    registry.fetch(new DistroRegistry.FetchCallback() {
      @Override
      public void onSuccess(List<DistroInfo> distros) {
        mainHandler.post(() -> {
          if (!isAdded()) return;

          if (distros.isEmpty()) {
            txtLoading.setVisibility(View.GONE);
            txtError.setText("No compatible distros for your device.");
            txtError.setVisibility(View.VISIBLE);
            return;
          }

          txtLoading.setVisibility(View.GONE);
          recycler.setVisibility(View.VISIBLE);
          adapter.setData(distros);
        });
      }

      @Override
      public void onError(String message) {
        mainHandler.post(() -> {
          if (!isAdded()) return;
          txtLoading.setVisibility(View.GONE);
          txtError.setText("Failed to load distros: " + message);
          txtError.setVisibility(View.VISIBLE);
        });
      }
    });
  }

  private class DistroAdapter extends RecyclerView.Adapter<DistroAdapter.VH> {

    private final List<DistroInfo> data = new ArrayList<>();

    void setData(List<DistroInfo> distros) {
      data.clear();
      data.addAll(distros);
      notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      View v = LayoutInflater.from(parent.getContext())
          .inflate(R.layout.item_distro, parent, false);
      return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
      DistroInfo info = data.get(position);
      holder.txtName.setText(info.getDisplayName());
      holder.txtDescription.setText(info.description == null ? "" : info.description);

      String abi = DistroRegistry.getCompatibleAbi(info);
      int sizeMb = info.getSizeMb(abi);
      if (sizeMb > 0) {
        holder.txtSize.setText("~ " + sizeMb + " MB · " + abi);
      } else {
        holder.txtSize.setText(abi);
      }

      holder.itemView.setOnClickListener(v -> {
        if (listener != null) {
          listener.onDistroSelected(info);
        }
        dismissAllowingStateLoss();
      });
    }

    @Override
    public int getItemCount() {
      return data.size();
    }

    class VH extends RecyclerView.ViewHolder {
      final TextView txtName;
      final TextView txtDescription;
      final TextView txtSize;

      VH(@NonNull View itemView) {
        super(itemView);
        txtName = itemView.findViewById(R.id.txtName);
        txtDescription = itemView.findViewById(R.id.txtDescription);
        txtSize = itemView.findViewById(R.id.txtSize);
      }
    }
  }
}