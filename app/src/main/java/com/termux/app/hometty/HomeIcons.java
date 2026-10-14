package com.termux.app.hometty;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.util.AtomicFile;
import android.util.LruCache;
import java.io.*;
import java.util.*;

/** User-selected bitmap icons; bounded decoding and immutable content-addressed files. */
public final class HomeIcons {
    private static final LruCache<String,Bitmap> images=new LruCache<>(64);
    private static Bitmap decode(byte[] source) throws IOException {
        if (source.length>1048576) throw new IOException("Icon image exceeds 1 MiB");
        BitmapFactory.Options bounds=new BitmapFactory.Options(); bounds.inJustDecodeBounds=true; BitmapFactory.decodeByteArray(source,0,source.length,bounds);
        if (bounds.outWidth<1 || bounds.outHeight<1 || Math.max(bounds.outWidth,bounds.outHeight)>8192) throw new IOException("Unsupported image or icon dimensions exceed 8192 pixels");
        BitmapFactory.Options options=new BitmapFactory.Options(); options.inSampleSize=1;
        while (Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>256) options.inSampleSize*=2;
        Bitmap bitmap=BitmapFactory.decodeByteArray(source,0,source.length,options); if (bitmap==null) throw new IOException("Cannot decode icon");
        double scale=128.0/Math.max(bitmap.getWidth(),bitmap.getHeight()); Bitmap icon=Bitmap.createScaledBitmap(bitmap,Math.max(1,(int)(bitmap.getWidth()*scale)),Math.max(1,(int)(bitmap.getHeight()*scale)),true); if (icon!=bitmap) bitmap.recycle(); return icon;
    }
    public static synchronized String store(byte[] source) throws Exception {
        Bitmap bitmap=decode(source); ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try { if (!bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes)) throw new IOException("Cannot encode icon"); } finally { bitmap.recycle(); }
        byte[] png=bytes.toByteArray(); java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256"); StringBuilder name=new StringBuilder(); for (byte value:hash.digest(png)) name.append(String.format(Locale.ROOT,"%02x",value&255));
        File directory=new File(HomeConfig.HOME,".config/hometty/icons"); if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create icon directory");
        File target=HomeFiles.child(HomeConfig.HOME,directory,name+".png");
        if (!target.exists()) {
            AtomicFile file=new AtomicFile(target); FileOutputStream output=file.startWrite(); try { output.write(png); file.finishWrite(output); } catch (IOException error) { file.failWrite(output); throw error; }
        }
        images.evictAll(); return "~/.config/hometty/icons/"+target.getName();
    }
    public static Drawable load(Context context,HomeConfig config,String component) {
        String path=config.stringPath(Arrays.asList("app_overrides",component,"icon"),""); if (path.isEmpty()) return null;
        File source=path.startsWith("~/")?new File(HomeConfig.HOME,path.substring(2)):new File(path);
        try {
            source=HomeFiles.contained(HomeConfig.HOME,source); if (!source.isFile() || source.length()>1048576) return null;
            String key=source.getAbsolutePath()+":"+source.lastModified()+":"+source.length(); Bitmap image=images.get(key);
            if (image==null) {
                try (InputStream input=new FileInputStream(source); ByteArrayOutputStream bytes=new ByteArrayOutputStream()) { byte[] buffer=new byte[8192]; int count; while ((count=input.read(buffer))!=-1) { if (bytes.size()+count>1048576) throw new IOException("Icon too large"); bytes.write(buffer,0,count); } image=decode(bytes.toByteArray()); }
                images.put(key,image);
            }
            return new BitmapDrawable(context.getResources(),image);
        } catch (IOException | RuntimeException invalid) { return null; }
    }
}
