package com.termux.app.hometty;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
/** Explicit catalog browsing/download only; never executes downloaded material. */
public final class PluginNetwork {
    public static String fetch(String address) throws IOException {
        URI url=PluginCatalog.resolveSource(address); HttpURLConnection connection=(HttpURLConnection)url.toURL().openConnection();
        connection.setConnectTimeout(10000); connection.setReadTimeout(15000); connection.setInstanceFollowRedirects(false);
        try {
            if (connection.getResponseCode()!=200) throw new IOException("Catalog HTTP "+connection.getResponseCode());
            try (InputStream input=connection.getInputStream(); ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[8192]; int count; while ((count=input.read(buffer))!=-1) { if (bytes.size()+count>262144) throw new IOException("Catalog or manifest exceeds 256 KiB"); bytes.write(buffer,0,count); }
                return new String(bytes.toByteArray(),StandardCharsets.UTF_8);
            }
        } finally { connection.disconnect(); }
    }
}
