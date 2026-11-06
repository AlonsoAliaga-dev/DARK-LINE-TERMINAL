/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.alonso.darkline.terminal.ai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.alonso.darkline.terminal.settings.SettingsKeys;
import com.alonso.darkline.terminal.settings.SettingsManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

public class AiChatManager {

  private static final String TAG = "AiChatManager";
  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
  private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";

  private static final String SYSTEM_PROMPT =
      "You are a helpful AI assistant inside a Linux terminal. "
          + "Be concise. Use Markdown for code blocks. When giving shell commands, "
          + "put them in ```bash blocks.";

  public interface MessageListener {
    void onUserMessage(AiMessage message);

    void onAssistantStarted(AiMessage message);

    void onAssistantChunk(AiMessage message, String fullText);

    void onAssistantFinished(AiMessage message);

    void onError(String error);
  }

  public interface ConversationsListener {
    void onLoaded(List<AiConversation> conversations);
  }

  public static class AiMessage {
    public String id;
    public String role;
    public String content;
    public long timestamp;

    public AiMessage() {
      this.id = UUID.randomUUID().toString();
      this.timestamp = System.currentTimeMillis();
    }

    public AiMessage(String role, String content) {
      this();
      this.role = role;
      this.content = content;
    }
  }

  public static class AiConversation {
    public String id;
    public String title;
    public long createdAt;
    public long updatedAt;
    public List<AiMessage> messages;

    public AiConversation() {
      this.id = UUID.randomUUID().toString();
      this.createdAt = System.currentTimeMillis();
      this.updatedAt = this.createdAt;
      this.messages = new ArrayList<>();
      this.title = "New chat";
    }

    public String getPreview() {
      if (messages.isEmpty()) return "No messages";
      for (int i = messages.size() - 1; i >= 0; i--) {
        AiMessage m = messages.get(i);
        if ("assistant".equals(m.role) && m.content != null && !m.content.isEmpty()) {
          String text = m.content.replace("\n", " ").trim();
          return text.length() > 60 ? text.substring(0, 60) + "..." : text;
        }
      }
      return "No messages";
    }
  }

  private final Context appContext;
  private final SettingsManager settings;
  private final OkHttpClient httpClient;
  private final Gson gson;
  private final Handler mainHandler;
  private final File chatsDir;

  private AiConversation currentConversation;
  private Call activeCall;

  public AiChatManager(Context context) {
    this.appContext = context.getApplicationContext();
    this.settings = new SettingsManager(appContext);
    this.gson = new Gson();
    this.mainHandler = new Handler(Looper.getMainLooper());
    this.httpClient = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build();
    this.chatsDir = new File(appContext.getFilesDir(), "ai_chats");
    if (!chatsDir.exists()) chatsDir.mkdirs();
  }

  public SettingsManager getSettings() {
    return settings;
  }

  public boolean isConfigured() {
    String key = settings.getActiveApiKey();
    return key != null && !key.trim().isEmpty();
  }

  public String getCurrentProviderLabel() {
    return "Groq";
  }

  public AiConversation getCurrentConversation() {
    return currentConversation;
  }

  public void startNewConversation() {
    currentConversation = new AiConversation();
  }

  public void loadConversation(AiConversation conversation) {
    this.currentConversation = conversation;
  }

  public void saveCurrentConversation() {
    if (currentConversation == null || currentConversation.messages.isEmpty()) return;
    if ("New chat".equals(currentConversation.title) && !currentConversation.messages.isEmpty()) {
      for (AiMessage m : currentConversation.messages) {
        if ("user".equals(m.role) && m.content != null && !m.content.isEmpty()) {
          String t = m.content.replace("\n", " ").trim();
          currentConversation.title = t.length() > 40 ? t.substring(0, 40) + "..." : t;
          break;
        }
      }
    }
    currentConversation.updatedAt = System.currentTimeMillis();
    File file = new File(chatsDir, currentConversation.id + ".json");
    try (FileWriter writer = new FileWriter(file)) {
      gson.toJson(currentConversation, writer);
    } catch (IOException ignored) {
    }
  }

  public void deleteConversation(AiConversation conversation) {
    File file = new File(chatsDir, conversation.id + ".json");
    if (file.exists()) file.delete();
    if (currentConversation != null && currentConversation.id.equals(conversation.id)) {
      currentConversation = null;
    }
  }

  public void loadAllConversations(ConversationsListener listener) {
    new Thread(() -> {
      List<AiConversation> result = new ArrayList<>();
      File[] files = chatsDir.listFiles((dir, name) -> name.endsWith(".json"));
      if (files != null) {
        for (File f : files) {
          try (FileReader reader = new FileReader(f)) {
            AiConversation c = gson.fromJson(reader, AiConversation.class);
            if (c != null && c.messages != null) {
              result.add(c);
            }
          } catch (Exception ignored) {
          }
        }
      }
      Collections.sort(result, (a, b) -> Long.compare(b.updatedAt, a.updatedAt));
      mainHandler.post(() -> listener.onLoaded(result));
    }).start();
  }

  public void sendMessage(String userText, MessageListener listener) {
    if (currentConversation == null) {
      currentConversation = new AiConversation();
    }

    AiMessage userMsg = new AiMessage("user", userText);
    currentConversation.messages.add(userMsg);
    listener.onUserMessage(userMsg);

    AiMessage aiMsg = new AiMessage("assistant", "");
    currentConversation.messages.add(aiMsg);
    listener.onAssistantStarted(aiMsg);

    String apiKey = settings.getActiveApiKey();

    if (apiKey == null || apiKey.trim().isEmpty()) {
      listener.onError("Groq API key not configured. Open Settings → AI Assistant.");
      return;
    }

    sendGroqRequest(apiKey, aiMsg, listener);
  }

  public void cancelActiveRequest() {
    if (activeCall != null && !activeCall.isCanceled()) {
      activeCall.cancel();
    }
    activeCall = null;
  }

  private JsonArray buildMessages() {
    JsonArray arr = new JsonArray();
    JsonObject sys = new JsonObject();
    sys.addProperty("role", "system");
    sys.addProperty("content", SYSTEM_PROMPT);
    arr.add(sys);

    for (AiMessage m : currentConversation.messages) {
      if (m.content == null || m.content.isEmpty()) continue;
      JsonObject obj = new JsonObject();
      obj.addProperty("role", m.role);
      obj.addProperty("content", m.content);
      arr.add(obj);
    }
    return arr;
  }

  private void sendGroqRequest(String apiKey, AiMessage aiMsg, MessageListener listener) {
    String model = settings.getGroqModel();
    if (model == null || model.trim().isEmpty()) {
      model = SettingsKeys.DEFAULT_GROQ_MODEL;
    }
    model = model.trim();

    JsonObject body = new JsonObject();
    body.addProperty("model", model);
    body.add("messages", buildMessages());
    body.addProperty("stream", true);

    Log.d(TAG, "Groq request model: " + model);

    Request request = new Request.Builder()
        .url(GROQ_URL)
        .addHeader("Authorization", "Bearer " + apiKey)
        .addHeader("Content-Type", "application/json")
        .post(RequestBody.create(body.toString(), JSON))
        .build();

    activeCall = httpClient.newCall(request);
    activeCall.enqueue(new Callback() {
      @Override
      public void onFailure(Call call, IOException e) {
        if (call.isCanceled()) return;
        mainHandler.post(() -> listener.onError("Network error: " + e.getMessage()));
      }

      @Override
      public void onResponse(Call call, Response response) {
        if (!response.isSuccessful()) {
          String err = "HTTP " + response.code();
          try {
            if (response.body() != null) {
              String s = response.body().string();
              if (s.length() > 300) s = s.substring(0, 300);
              err += ": " + s;
            }
          } catch (Exception ignored) {
          }
          Log.e(TAG, "Groq error: " + err);
          final String finalErr = err;
          mainHandler.post(() -> listener.onError(finalErr));
          response.close();
          return;
        }

        StringBuilder full = new StringBuilder();
        try {
          ResponseBody rb = response.body();
          if (rb == null) {
            mainHandler.post(() -> listener.onError("Empty response"));
            return;
          }
          BufferedSource source = rb.source();
          String line;
          while ((line = source.readUtf8Line()) != null) {
            if (call.isCanceled()) break;
            if (!line.startsWith("data:")) continue;
            String data = line.substring(5).trim();
            if (data.isEmpty() || "[DONE]".equals(data)) continue;
            try {
              JsonObject obj = JsonParser.parseString(data).getAsJsonObject();
              JsonArray choices = obj.getAsJsonArray("choices");
              if (choices == null || choices.size() == 0) continue;
              JsonObject delta = choices.get(0).getAsJsonObject().getAsJsonObject("delta");
              if (delta == null) continue;
              JsonElement contentEl = delta.get("content");
              if (contentEl == null || contentEl.isJsonNull()) continue;
              String chunk = contentEl.getAsString();
              full.append(chunk);
              final String snapshot = full.toString();
              mainHandler.post(() -> listener.onAssistantChunk(aiMsg, snapshot));
            } catch (Exception ignored) {
            }
          }
        } catch (Exception e) {
          if (!call.isCanceled()) {
            mainHandler.post(() -> listener.onError("Stream error: " + e.getMessage()));
          }
          response.close();
          return;
        }
        response.close();

        aiMsg.content = full.toString();
        saveCurrentConversation();
        mainHandler.post(() -> listener.onAssistantFinished(aiMsg));
      }
    });
  }
}