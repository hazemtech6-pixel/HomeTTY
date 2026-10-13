package com.termux.app.hometty;
import java.io.*;
import java.nio.charset.StandardCharsets;
final class BoundedFiles {
    static String read(File file,int limit) throws IOException {
        try (InputStream in=new FileInputStream(file); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] b=new byte[4096]; int n; while ((n=in.read(b))!=-1) { if (out.size()+n>limit) throw new IOException("File too large"); out.write(b,0,n); }
            return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }
    }
}
