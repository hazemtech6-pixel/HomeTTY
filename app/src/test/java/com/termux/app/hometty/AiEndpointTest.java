package com.termux.app.hometty;
import org.junit.Test;
import static org.junit.Assert.*;
public class AiEndpointTest {
    @Test public void onlyExplicitSecureOrLocalServersAreAllowed() {
        assertEquals("https://example.com/ollama/api/chat",new AiEndpoint("https://example.com/ollama",false).chat.toString());
        assertFalse(new AiEndpoint("http://127.0.0.1:11434",false).encrypted());
        assertNotNull(new AiEndpoint("http://10.0.2.2:11434",true));
        for (String endpoint:new String[]{"http://example.com","https://key@example.com","https://example.com?token=secret","https://example.com#fragment","http://10.0.2.2:11434","https://example.com:0"}) {
            try { new AiEndpoint(endpoint,false); fail(endpoint); } catch (IllegalArgumentException expected) {}
        }
    }
}
