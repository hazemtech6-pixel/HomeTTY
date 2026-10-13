package com.termux.app.hometty;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Foreground-only Ollama chat. No tool calls or automatic command execution. */
public final class OllamaClient {
    public static final class Message {
        public final String role,content;
        public Message(String role,String content) { if (!Arrays.asList("user","assistant").contains(role)) throw new IllegalArgumentException("Invalid chat role"); this.role=role; this.content=content; }
    }
    private volatile HttpURLConnection connection;
    private volatile boolean canceled;
    public void cancel() { canceled=true; HttpURLConnection current=connection; if (current!=null) current.disconnect(); }
    public String chat(AiEndpoint endpoint,String model,List<Message> messages,String credential) throws Exception {
        if (!new HomeConfig().flag("ai.enabled",false)) throw new SecurityException("AI is disabled");
        if (model.trim().isEmpty() || model.length()>256) throw new IllegalArgumentException("Set ai.model to a model installed on your server");
        if (messages.isEmpty() || messages.size()>32) throw new IllegalArgumentException("Conversation is too long; start a new conversation");
        if (credential!=null && !endpoint.encrypted()) throw new SecurityException("Credentials require HTTPS");
        JSONArray history=new JSONArray(); int length=0;
        for (Message message:messages) { length+=message.content.length(); if (length>65536) throw new IllegalArgumentException("Conversation exceeds 64 KiB; start a new conversation"); history.put(new JSONObject().put("role",message.role).put("content",message.content)); }
        byte[] body=new JSONObject().put("model",model).put("messages",history).put("stream",false).toString().getBytes(StandardCharsets.UTF_8);
        if (body.length>262144) throw new IllegalArgumentException("Request too large");
        HttpURLConnection request=(HttpURLConnection)endpoint.chat.toURL().openConnection(); connection=request;
        request.setConnectTimeout(10000); request.setReadTimeout(120000); request.setInstanceFollowRedirects(false); request.setRequestMethod("POST"); request.setDoOutput(true); request.setRequestProperty("Content-Type","application/json"); request.setFixedLengthStreamingMode(body.length);
        if (credential!=null) request.setRequestProperty("Authorization","Bearer "+credential);
        try {
            if (canceled || !new HomeConfig().flag("ai.enabled",false)) throw new InterruptedIOException("Request canceled");
            try (OutputStream output=request.getOutputStream()) { output.write(body); }
            if (request.getResponseCode()!=200) throw new IOException("AI server returned HTTP "+request.getResponseCode());
            try (InputStream input=request.getInputStream(); ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[8192]; int count;
                while ((count=input.read(buffer))!=-1) { if (canceled) throw new InterruptedIOException("Request canceled"); if (bytes.size()+count>1048576) throw new IOException("AI response too large"); bytes.write(buffer,0,count); }
                JSONObject response=new JSONObject(bytes.toString("UTF-8"));
                if (response.has("error") || !response.optBoolean("done",false)) throw new IOException("AI server did not return a completed response");
                JSONObject message=response.getJSONObject("message"); if (!"assistant".equals(message.getString("role"))) throw new IOException("Unexpected AI response role");
                String text=message.getString("content"); if (text.length()>131072) throw new IOException("AI response too long"); return text;
            }
        } finally { request.disconnect(); connection=null; Arrays.fill(body,(byte)0); }
    }
}
