package com.termux.app.hometty;
import android.graphics.*;
import android.graphics.drawable.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class HomeIconsTest {
    @Test public void customIconIsBoundedAndLoadedFromTheSameTomlConfiguration() throws Exception {
        new HomeConfig(); String original=HomeConfig.read(),component="com.example/com.example.Icon_"+UUID.randomUUID().toString().replace('-','_'); File icon=null;
        Bitmap bitmap=Bitmap.createBitmap(64,32,Bitmap.Config.ARGB_8888); bitmap.eraseColor(Color.GREEN); bitmap.setPixel(32,16,0xff000000|new java.security.SecureRandom().nextInt(0x1000000));
        try {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream(); assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes));
            String path=HomeIcons.store(bytes.toByteArray()); icon=new File(HomeConfig.HOME,path.substring(2)); assertTrue(icon.isFile());
            HomeConfig.save(ConfigEdits.scalar(original,Arrays.asList("app_overrides",component,"icon"),path));
            Drawable drawable=HomeIcons.load(InstrumentationRegistry.getInstrumentation().getTargetContext(),new HomeConfig(),component); assertTrue(drawable instanceof BitmapDrawable);
            Bitmap rendered=((BitmapDrawable)drawable).getBitmap(); assertEquals(128,rendered.getWidth()); assertEquals(64,rendered.getHeight()); assertEquals(Color.GREEN,rendered.getPixel(0,0));
        } finally { bitmap.recycle(); HomeConfig.save(original); if (icon!=null) icon.delete(); }
    }
}
