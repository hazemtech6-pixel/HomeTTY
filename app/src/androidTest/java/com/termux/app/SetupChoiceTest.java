package com.termux.app;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.termux.app.hometty.*;
import com.termux.shared.termux.TermuxConstants;
import java.io.*;
import java.nio.file.Files;
import java.util.Properties;
import org.junit.Test;
import static org.junit.Assert.*;

/** Native questions survive recreation without being mistaken for a missing PTY. */
@androidx.test.filters.SdkSuppress(minSdkVersion=26)
public class SetupChoiceTest {
    @Test public void desktopQuestionResumesAndDecliningSkipsDisplayQuestion() throws Exception {
        new HomeConfig(); File state=new File(HomeConfig.DATA,"installer.properties");
        byte[] original=state.exists()?Files.readAllBytes(state.toPath()):null; String config=HomeConfig.read();
        Properties fixture=new Properties(); fixture.setProperty("version","1");
        String prefix=TermuxConstants.TERMUX_PREFIX_DIR_PATH;
        completed(fixture,"android","id; uname -m; test -d \"$HOME\"");
        for (String companion:new String[]{"api","widget","tasker","x11"}) completed(fixture,companion,"companion:"+companion);
        completed(fixture,"bootstrap","test -x '"+prefix+"/bin/bash' && '"+prefix+"/bin/bash' -c 'printf \"HomeTTY shell ready\\n\"'");
        completed(fixture,"packages","'"+prefix+"/bin/dpkg' --audit"); completed(fixture,"tools-install","recipe:tools");
        try {
            HomeConfig.save(ConfigEdits.scalar(config,"security.lock_terminal",false));
            try (FileOutputStream output=new FileOutputStream(state)) { fixture.store(output,"test fixture"); }
            try (ActivityScenario<SetupActivity> scenario=ActivityScenario.launch(SetupActivity.class)) {
                choose(scenario);
                assertEquals("WAITING_FOR_USER",read(state).getProperty("desktop-choice.status"));
                scenario.recreate();
                Thread.sleep(750);
                assertEquals("WAITING_FOR_USER",read(state).getProperty("desktop-choice.status"));
                choose(scenario);
                android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
                instrumentation.waitForIdleSync();
                java.util.List<AccessibilityNodeInfo> options=java.util.Collections.emptyList(); long deadline=android.os.SystemClock.elapsedRealtime()+10000;
                while (options.isEmpty() && android.os.SystemClock.elapsedRealtime()<deadline) {
                    AccessibilityNodeInfo root=instrumentation.getUiAutomation().getRootInActiveWindow();
                    if (root!=null) options=root.findAccessibilityNodeInfosByText("No desktop");
                    if (options.isEmpty()) Thread.sleep(100);
                }
                assertFalse(options.isEmpty()); android.graphics.Rect bounds=new android.graphics.Rect(); options.get(0).getBoundsInScreen(bounds);
                assertFalse("Desktop choice is outside the visible window: "+options.get(0),bounds.isEmpty());
                long time=android.os.SystemClock.uptimeMillis();
                instrumentation.sendPointerSync(MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,bounds.centerX(),bounds.centerY(),0));
                instrumentation.sendPointerSync(MotionEvent.obtain(time,time+50,MotionEvent.ACTION_UP,bounds.centerX(),bounds.centerY(),0));
                instrumentation.waitForIdleSync();
                long answerDeadline=android.os.SystemClock.elapsedRealtime()+5000;
                while (!"SUCCEEDED".equals(read(state).getProperty("desktop-choice.status")) && android.os.SystemClock.elapsedRealtime()<answerDeadline) Thread.sleep(50);
                assertFalse("Desktop choice was not saved; tapped "+bounds,new HomeConfig().flag("setup.desktop",true));
                assertEquals("SUCCEEDED",read(state).getProperty("desktop-choice.status"));
                choose(scenario);
                assertEquals("SKIPPED",read(state).getProperty("display-choice.status"));
                scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
            }
        } finally {
            HomeConfig.save(config);
            if (original==null) state.delete(); else Files.write(state.toPath(),original);
        }
    }
    private static void completed(Properties state,String id,String command) { state.setProperty(id+".command",command); state.setProperty(id+".status","SUCCEEDED"); state.setProperty(id+".progress","100"); }
    private static Properties read(File file) throws IOException { Properties result=new Properties(); try (FileInputStream input=new FileInputStream(file)) { result.load(input); } return result; }
    private static void choose(ActivityScenario<SetupActivity> scenario) throws Exception {
        java.util.concurrent.atomic.AtomicBoolean clicked=new java.util.concurrent.atomic.AtomicBoolean(); long deadline=android.os.SystemClock.elapsedRealtime()+5000;
        while (!clicked.get() && android.os.SystemClock.elapsedRealtime()<deadline) {
            scenario.onActivity(activity -> { Button button=find(activity.findViewById(android.R.id.content),"Choose"); if (button!=null && button.isEnabled()) { button.performClick(); clicked.set(true); } });
            if (!clicked.get()) Thread.sleep(50);
        }
        assertTrue("Choice button unavailable",clicked.get());
    }
    private static Button find(View view,String label) {
        if (view instanceof Button && label.contentEquals(((Button)view).getText())) return (Button)view;
        if (view instanceof ViewGroup) for (int index=0;index<((ViewGroup)view).getChildCount();index++) { Button result=find(((ViewGroup)view).getChildAt(index),label); if (result!=null) return result; }
        return null;
    }
}
