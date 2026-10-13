package com.termux.app.hometty;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class OllamaClientTest {
    @Test public void disabledAiMakesNoRequestAndEnabledAiUsesConfiguredProtocol() throws Exception {
        new HomeConfig(); String original=HomeConfig.read();
        ExecutorService serverThread=Executors.newSingleThreadExecutor();
        try (ServerSocket server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))) {
            AiEndpoint endpoint=new AiEndpoint("http://127.0.0.1:"+server.getLocalPort(),false);
            HomeConfig.save(HomeConfig.DEFAULT);
            try { new OllamaClient().chat(endpoint,"fixture",Collections.singletonList(new OllamaClient.Message("user","Hello")),null); fail("Disabled AI opened a request"); } catch (SecurityException expected) {}
            server.setSoTimeout(250); try (Socket unexpected=server.accept()) { fail("Disabled AI connected"); } catch (SocketTimeoutException expected) {}
            server.setSoTimeout(10000);
            Future<String> requestBody=serverThread.submit(() -> {
                try (Socket connection=server.accept()) {
                    BufferedReader input=new BufferedReader(new InputStreamReader(connection.getInputStream(),StandardCharsets.UTF_8));
                    assertEquals("POST /api/chat HTTP/1.1",input.readLine()); int length=0; String line;
                    while ((line=input.readLine())!=null && !line.isEmpty()) if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) length=Integer.parseInt(line.substring(line.indexOf(':')+1).trim());
                    char[] body=new char[length]; int offset=0,count; while (offset<length && (count=input.read(body,offset,length-offset))!=-1) offset+=count;
                    String response="{\"message\":{\"role\":\"assistant\",\"content\":\"A test reply\"},\"done\":true}";
                    byte[] bytes=response.getBytes(StandardCharsets.UTF_8); OutputStream output=connection.getOutputStream(); output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8)); output.write(bytes); output.flush(); return new String(body);
                }
            });
            HomeConfig.save(HomeConfig.DEFAULT.replace("enabled = false","enabled = true"));
            String answer=new OllamaClient().chat(endpoint,"fixture",Collections.singletonList(new OllamaClient.Message("user","Hello")),null);
            assertEquals("A test reply",answer); JSONObject body=new JSONObject(requestBody.get(10,TimeUnit.SECONDS));
            assertEquals("fixture",body.getString("model")); assertFalse(body.getBoolean("stream")); assertEquals("Hello",body.getJSONArray("messages").getJSONObject(0).getString("content")); assertFalse(body.has("tools"));
        } finally { HomeConfig.save(original); serverThread.shutdownNow(); }
    }
}
