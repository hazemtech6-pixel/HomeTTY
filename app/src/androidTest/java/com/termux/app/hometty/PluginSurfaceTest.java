package com.termux.app.hometty;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.*;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class PluginSurfaceTest {
    @Test public void widgetSurfaceRequiresAnExactContentGrant() throws Exception {
        new HomeConfig(); LauncherState state=new LauncherState(); String presented=state.get("setup.presented","false"); state.put("setup.presented","true");
        File plugins=new File(HomeConfig.HOME,".config/hometty/plugins"); assertTrue(plugins.isDirectory() || plugins.mkdirs());
        String id="test_widget_"+UUID.randomUUID(), title="Plugin Widget Fixture "+UUID.randomUUID(); File file=new File(plugins,id+".toml");
        String source="[plugin]\nid='"+id+"'\nname='"+title+"'\nsurface='widget'\ncapabilities=['widget','android_settings']\n[action]\nkind='settings'\ntarget='wifi'\n";
        try {
            write(file,source);
            // Keep one scenario: Android may immediately relaunch its default HOME
            // after finish(), which races repeated singleTask scenario launches.
            try (ActivityScenario<HomeActivity> scenario=ActivityScenario.launch(HomeActivity.class)) {
                scenario.onActivity(activity -> assertFalse(contains(activity.findViewById(android.R.id.content),title)));
                state.put("grant."+id,PluginManifest.digest(source));
                scenario.recreate();
                scenario.onActivity(activity -> assertTrue(contains(activity.findViewById(android.R.id.content),title)));
                write(file,source+"# edited after review\n");
                scenario.recreate();
                scenario.onActivity(activity -> assertFalse(contains(activity.findViewById(android.R.id.content),title)));
                scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
            }
        } finally { file.delete(); state.put("grant."+id,""); state.put("setup.presented",presented); }
    }
    private static void write(File file,String text) throws IOException { try (FileOutputStream output=new FileOutputStream(file)) { output.write(text.getBytes("UTF-8")); } }
    private static boolean contains(View view,String text) {
        if (view instanceof TextView && text.contentEquals(((TextView)view).getText())) return true;
        if (view instanceof ViewGroup) for (int index=0;index<((ViewGroup)view).getChildCount();index++) if (contains(((ViewGroup)view).getChildAt(index),text)) return true;
        return false;
    }
}
