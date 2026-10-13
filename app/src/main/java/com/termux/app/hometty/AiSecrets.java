package com.termux.app.hometty;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
/** Optional server credential, encrypted and bound to the reviewed endpoint. */
public final class AiSecrets {
    private static final String NAME="ai_provider_credential";
    public static boolean configured() { return SecretStore.contains(NAME); }
    public static void remove() { SecretStore.delete(NAME); }
    public static void put(AiEndpoint endpoint,String credential) throws Exception {
        if (!endpoint.encrypted()) throw new IllegalArgumentException("Server credentials require HTTPS");
        if (credential.isEmpty() || credential.length()>4096 || credential.indexOf('\r')>=0 || credential.indexOf('\n')>=0) throw new IllegalArgumentException("Invalid server credential");
        byte[] record=new JSONObject().put("endpoint",endpoint.base.toString()).put("credential",credential).toString().getBytes(StandardCharsets.UTF_8);
        try { SecretStore.put(NAME,record); } finally { Arrays.fill(record,(byte)0); }
    }
    public static String get(AiEndpoint endpoint) throws Exception {
        if (!configured()) return null;
        byte[] encoded=SecretStore.get(NAME);
        try {
            JSONObject record=new JSONObject(new String(encoded,StandardCharsets.UTF_8));
            if (!endpoint.encrypted() || !endpoint.base.toString().equals(record.getString("endpoint"))) throw new SecurityException("The configured server changed; review its credential in AI settings");
            return record.getString("credential");
        } finally { Arrays.fill(encoded,(byte)0); }
    }
}
