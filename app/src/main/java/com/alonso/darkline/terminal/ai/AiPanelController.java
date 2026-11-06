/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.alonso.darkline.terminal.ai;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alonso.darkline.terminal.databinding.DrawerAiBinding;
import com.alonso.darkline.terminal.databinding.ItemAiConversationBinding;
import com.alonso.darkline.terminal.databinding.ItemAiMessageBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class AiPanelController {

  private final Context context;
  private final DrawerLayout drawerLayout;
  private final View aiPanelView;
  private final DrawerAiBinding binding;
  private final AiChatManager chatManager;

  private final List<AiChatManager.AiMessage> messages = new ArrayList<>();
  private MessageAdapter messageAdapter;

  private final List<AiChatManager.AiConversation> conversations = new ArrayList<>();
  private ConversationAdapter conversationAdapter;

  private boolean showingHistory = false;

  public AiPanelController(Context context, DrawerLayout drawerLayout, View aiPanelView) {
    this.context = context;
    this.drawerLayout = drawerLayout;
    this.aiPanelView = aiPanelView;
    this.binding = DrawerAiBinding.bind(aiPanelView);
    this.chatManager = new AiChatManager(context);

    setupRecyclerViews();
    setupInput();
    setupHeaderButtons();
    updateHeader();
    showMessages();
    chatManager.startNewConversation();
    updateEmptyState();
  }

  public AiChatManager getChatManager() {
    return chatManager;
  }

  public void openPanel() {
    chatManager.loadAllConversations(loaded -> {
      conversations.clear();
      conversations.addAll(loaded);
      if (conversationAdapter != null) conversationAdapter.notifyDataSetChanged();
    });
    showMessages();
    updateHeader();
    drawerLayout.openDrawer(GravityCompat.END);
  }

  public void closePanel() {
    drawerLayout.closeDrawer(GravityCompat.END);
  }

  public boolean isOpen() {
    return drawerLayout.isDrawerOpen(GravityCompat.END);
  }

  private void setupRecyclerViews() {
    messageAdapter = new MessageAdapter();
    binding.recyclerAiMessages.setLayoutManager(new LinearLayoutManager(context));
    binding.recyclerAiMessages.setAdapter(messageAdapter);

    conversationAdapter = new ConversationAdapter();
    binding.recyclerAiConversations.setLayoutManager(new LinearLayoutManager(context));
    binding.recyclerAiConversations.setAdapter(conversationAdapter);
  }

  private void setupInput() {
    binding.btnAiSend.setOnClickListener(v -> sendCurrentInput());
    binding.btnAiStop.setOnClickListener(v -> {
      chatManager.cancelActiveRequest();
      setSendingState(false);
    });
  }

  private void setupHeaderButtons() {
    binding.btnAiBack.setOnClickListener(v -> {
      if (showingHistory) {
        showMessages();
      } else {
        closePanel();
      }
    });

    binding.btnAiNewChat.setOnClickListener(v -> {
      chatManager.saveCurrentConversation();
      chatManager.startNewConversation();
      messages.clear();
      messageAdapter.notifyDataSetChanged();
      showMessages();
      updateHeader();
      updateEmptyState();
    });

    binding.btnAiHistory.setOnClickListener(v -> {
      if (showingHistory) {
        showMessages();
      } else {
        chatManager.loadAllConversations(loaded -> {
          conversations.clear();
          conversations.addAll(loaded);
          conversationAdapter.notifyDataSetChanged();
        });
        showHistory();
      }
    });
  }

  private void showMessages() {
    showingHistory = false;
    binding.recyclerAiMessages.setVisibility(View.VISIBLE);
    binding.recyclerAiConversations.setVisibility(View.GONE);
    binding.layoutEmptyState.setVisibility(messages.isEmpty() ? View.VISIBLE : View.GONE);
    binding.aiInputContainer.setVisibility(View.VISIBLE);
    binding.btnAiNewChat.setVisibility(View.VISIBLE);
    binding.btnAiHistory.setVisibility(View.VISIBLE);
    updateHeader();
  }

  private void showHistory() {
    showingHistory = true;
    binding.recyclerAiMessages.setVisibility(View.GONE);
    binding.recyclerAiConversations.setVisibility(View.VISIBLE);
    binding.layoutEmptyState.setVisibility(View.GONE);
    binding.aiInputContainer.setVisibility(View.GONE);
    binding.btnAiNewChat.setVisibility(View.GONE);
    binding.btnAiHistory.setVisibility(View.GONE);
    binding.txtAiTitle.setText("Chat history");
    binding.txtAiSubtitle.setText(conversations.size() + " conversations");
  }

  private void updateHeader() {
    if (showingHistory) return;
    AiChatManager.AiConversation current = chatManager.getCurrentConversation();
    String title = (current != null && !TextUtils.isEmpty(current.title)) ? current.title : "AI Assistant";
    binding.txtAiTitle.setText(title);
    binding.txtAiSubtitle.setText(chatManager.getCurrentProviderLabel());
  }

  private void updateEmptyState() {
    binding.layoutEmptyState.setVisibility(messages.isEmpty() ? View.VISIBLE : View.GONE);
  }

  private void sendCurrentInput() {
    String text = binding.aiInput.getText() == null ? "" : binding.aiInput.getText().toString().trim();
    if (text.isEmpty()) return;

    if (!chatManager.isConfigured()) {
      new MaterialAlertDialogBuilder(context)
          .setTitle("API key required")
          .setMessage("Please set your AI API key in Settings → AI Assistant.")
          .setPositiveButton("OK", null)
          .show();
      return;
    }

    binding.aiInput.setText("");
    setSendingState(true);

    chatManager.sendMessage(text, new AiChatManager.MessageListener() {
      @Override
      public void onUserMessage(AiChatManager.AiMessage message) {
        messages.add(message);
        messageAdapter.notifyItemInserted(messages.size() - 1);
        scrollToBottom();
        updateEmptyState();
      }

      @Override
      public void onAssistantStarted(AiChatManager.AiMessage message) {
        messages.add(message);
        messageAdapter.notifyItemInserted(messages.size() - 1);
        scrollToBottom();
      }

      @Override
      public void onAssistantChunk(AiChatManager.AiMessage message, String fullText) {
        message.content = fullText;
        int idx = messages.indexOf(message);
        if (idx >= 0) {
          messageAdapter.notifyItemChanged(idx);
          scrollToBottom();
        }
      }

      @Override
      public void onAssistantFinished(AiChatManager.AiMessage message) {
        setSendingState(false);
        updateHeader();
        int idx = messages.indexOf(message);
        if (idx >= 0) messageAdapter.notifyItemChanged(idx);
      }

      @Override
      public void onError(String error) {
        setSendingState(false);
        Toast.makeText(context, error, Toast.LENGTH_LONG).show();
        if (!messages.isEmpty()) {
          AiChatManager.AiMessage last = messages.get(messages.size() - 1);
          if ("assistant".equals(last.role) && (last.content == null || last.content.isEmpty())) {
            messages.remove(messages.size() - 1);
            messageAdapter.notifyItemRemoved(messages.size());
          }
        }
      }
    });
  }

  private void setSendingState(boolean sending) {
    binding.btnAiSend.setVisibility(sending ? View.GONE : View.VISIBLE);
    binding.btnAiStop.setVisibility(sending ? View.VISIBLE : View.GONE);
    binding.aiInput.setEnabled(!sending);
  }

  private void scrollToBottom() {
    if (messages.isEmpty()) return;
    binding.recyclerAiMessages.post(() ->
        binding.recyclerAiMessages.smoothScrollToPosition(messages.size() - 1));
  }

  private class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.VH> {

    private static final int TYPE_USER = 1;
    private static final int TYPE_AI = 2;

    @Override
    public int getItemViewType(int position) {
      return "user".equals(messages.get(position).role) ? TYPE_USER : TYPE_AI;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      ItemAiMessageBinding b = ItemAiMessageBinding.inflate(
          LayoutInflater.from(parent.getContext()), parent, false);
      return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
      AiChatManager.AiMessage msg = messages.get(position);
      boolean isUser = "user".equals(msg.role);

      holder.binding.userContainer.setVisibility(isUser ? View.VISIBLE : View.GONE);
      holder.binding.aiContainer.setVisibility(isUser ? View.GONE : View.VISIBLE);

      if (isUser) {
        holder.binding.txtUserMessage.setText(msg.content == null ? "" : msg.content);
      } else {
        String content = msg.content == null ? "" : msg.content;
        if (content.isEmpty()) {
          holder.binding.txtAiMessage.setText("...");
        } else {
          holder.binding.txtAiMessage.setText(renderMarkdown(content));
        }
        holder.binding.btnCopy.setOnClickListener(v -> copyToClipboard(content));
      }
    }

    @Override
    public int getItemCount() {
      return messages.size();
    }

    class VH extends RecyclerView.ViewHolder {
      final ItemAiMessageBinding binding;

      VH(ItemAiMessageBinding binding) {
        super(binding.getRoot());
        this.binding = binding;
      }
    }
  }

  private class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.VH> {

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      ItemAiConversationBinding b = ItemAiConversationBinding.inflate(
          LayoutInflater.from(parent.getContext()), parent, false);
      return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
      AiChatManager.AiConversation conv = conversations.get(position);
      holder.binding.txtConversationTitle.setText(conv.title);
      holder.binding.txtConversationPreview.setText(conv.getPreview());

      holder.itemView.setOnClickListener(v -> {
        chatManager.saveCurrentConversation();
        chatManager.loadConversation(conv);
        messages.clear();
        if (conv.messages != null) messages.addAll(conv.messages);
        messageAdapter.notifyDataSetChanged();
        showMessages();
        scrollToBottom();
        updateEmptyState();
      });

      holder.binding.btnDeleteConversation.setOnClickListener(v -> {
        new MaterialAlertDialogBuilder(context)
            .setTitle("Delete conversation")
            .setMessage("Delete \"" + conv.title + "\"?")
            .setPositiveButton("Delete", (d, w) -> {
              chatManager.deleteConversation(conv);
              conversations.remove(position);
              notifyItemRemoved(position);
              if (conversations.isEmpty()) showMessages();
            })
            .setNegativeButton("Cancel", null)
            .show();
      });
    }

    @Override
    public int getItemCount() {
      return conversations.size();
    }

    class VH extends RecyclerView.ViewHolder {
      final ItemAiConversationBinding binding;

      VH(ItemAiConversationBinding binding) {
        super(binding.getRoot());
        this.binding = binding;
      }
    }
  }

  private void copyToClipboard(String text) {
    if (text == null || text.isEmpty()) return;
    ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    if (cm != null) {
      cm.setPrimaryClip(ClipData.newPlainText("ai_message", text));
      Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show();
    }
  }

  // ============ رندرر ساده‌ی Markdown ============

  private CharSequence renderMarkdown(String text) {
    SpannableStringBuilder result = new SpannableStringBuilder();

    int i = 0;
    while (i < text.length()) {
      // Code block
      if (text.startsWith("```", i)) {
        int end = text.indexOf("```", i + 3);
        if (end == -1) {
          result.append(text.substring(i));
          break;
        }
        int firstNewline = text.indexOf('\n', i + 3);
        String code;
        if (firstNewline != -1 && firstNewline < end) {
          code = text.substring(firstNewline + 1, end);
        } else {
          code = text.substring(i + 3, end);
        }
        int start = result.length();
        result.append(code);
        int stop = result.length();
        if (stop > start) {
          result.setSpan(new TypefaceSpan("monospace"), start, stop,
              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
          result.setSpan(new BackgroundColorSpan(0x22888888), start, stop,
              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        i = end + 3;
        continue;
      }

      // Bold
      if (text.startsWith("**", i)) {
        int end = text.indexOf("**", i + 2);
        if (end != -1) {
          String content = text.substring(i + 2, end);
          int start = result.length();
          result.append(content);
          result.setSpan(new StyleSpan(Typeface.BOLD), start, result.length(),
              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
          i = end + 2;
          continue;
        }
      }

      // Inline code
      if (text.charAt(i) == '`') {
        int end = text.indexOf('`', i + 1);
        if (end != -1) {
          String content = text.substring(i + 1, end);
          int start = result.length();
          result.append(content);
          result.setSpan(new TypefaceSpan("monospace"), start, result.length(),
              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
          result.setSpan(new BackgroundColorSpan(0x22888888), start, result.length(),
              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
          i = end + 1;
          continue;
        }
      }

      // Italic
      if (text.charAt(i) == '*') {
        int end = text.indexOf('*', i + 1);
        if (end != -1 && end > i + 1) {
          String content = text.substring(i + 1, end);
          int start = result.length();
          result.append(content);
          result.setSpan(new StyleSpan(Typeface.ITALIC), start, result.length(),
              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
          i = end + 1;
          continue;
        }
      }

      result.append(text.charAt(i));
      i++;
    }

    return result;
  }
}