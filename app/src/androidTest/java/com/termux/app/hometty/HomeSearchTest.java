package com.termux.app.hometty;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class HomeSearchTest {
    @Test public void desktopEntryAppearsAfterBackgroundIndexing() throws Exception {
        new HomeConfig(); LauncherState state=new LauncherState(); String presented=state.get("setup.presented","false"); state.put("setup.presented","true");
        File applications=new File(HomeConfig.HOME,".local/share/applications"); assertTrue(applications.isDirectory() || applications.mkdirs());
        String title="HomeTTY Search Fixture "+UUID.randomUUID(); File entry=new File(applications,"hometty-test-"+UUID.randomUUID()+".desktop");
        try {
            try (FileOutputStream output=new FileOutputStream(entry)) { output.write(("[Desktop Entry]\nType=Application\nName="+title+"\nExec=/system/bin/true\n").getBytes("UTF-8")); }
            try (ActivityScenario<HomeActivity> scenario=ActivityScenario.launch(HomeActivity.class)) {
                scenario.onActivity(activity -> { EditText search=findSearch(activity.findViewById(android.R.id.content)); assertNotNull(search); search.setText("desktop:"+title); });
                AtomicBoolean found=new AtomicBoolean(); long deadline=android.os.SystemClock.elapsedRealtime()+10000;
                while (!found.get() && android.os.SystemClock.elapsedRealtime()<deadline) { scenario.onActivity(activity -> found.set(contains(activity.findViewById(android.R.id.content),title+" · desktop"))); Thread.sleep(50); }
                assertTrue("Desktop result was not displayed after asynchronous indexing",found.get());
            }
        } finally { entry.delete(); state.put("setup.presented",presented); }
    }
    private static EditText findSearch(View view) {
        if (view instanceof EditText && ((EditText)view).getHint()!=null && ((EditText)view).getHint().toString().startsWith("Search")) return (EditText)view;
        if (view instanceof ViewGroup) for (int index=0;index<((ViewGroup)view).getChildCount();index++) { EditText found=findSearch(((ViewGroup)view).getChildAt(index)); if (found!=null) return found; }
        return null;
    }
    private static boolean contains(View view,String text) {
        if (view instanceof TextView && text.contentEquals(((TextView)view).getText())) return true;
        if (view instanceof ViewGroup) for (int index=0;index<((ViewGroup)view).getChildCount();index++) if (contains(((ViewGroup)view).getChildAt(index),text)) return true;
        return false;
    }
}
