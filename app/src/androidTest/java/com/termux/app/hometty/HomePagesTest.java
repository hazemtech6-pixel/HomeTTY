package com.termux.app.hometty;
import android.content.*;
import android.content.pm.*;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;

public class HomePagesTest {
    @Test public void configuredPagesAndEmptySlotsSurviveRecreation() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        List<ResolveInfo> apps=context.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0);
        apps.removeIf(app -> context.getPackageName().equals(app.activityInfo.packageName)); Assume.assumeTrue(apps.size()>=2);
        List<String> slots=Arrays.asList(component(apps.get(0)),"",component(apps.get(1)));
        new HomeConfig(); String original=HomeConfig.read(); LauncherState state=new LauncherState(); String presented=state.get("setup.presented","false");
        try {
            String config=ConfigEdits.scalar(original,"home.columns",2); config=ConfigEdits.scalar(config,"home.rows",1); config=ConfigEdits.scalar(config,"home.layout",slots);
            HomeConfig.save(config); state.put("setup.presented","true");
            try (ActivityScenario<HomeActivity> scenario=ActivityScenario.launch(HomeActivity.class)) {
                scenario.onActivity(activity -> {
                    assertNotNull(find(activity.findViewById(android.R.id.content),"Page 1 / 2"));
                    assertNotNull(description(activity.findViewById(android.R.id.content),"Empty Home position"));
                    Button next=(Button)find(activity.findViewById(android.R.id.content),"›"); assertTrue(next.isEnabled()); next.performClick();
                    assertNotNull(find(activity.findViewById(android.R.id.content),"Page 2 / 2"));
                });
                scenario.recreate();
                scenario.onActivity(activity -> assertNotNull(find(activity.findViewById(android.R.id.content),"Page 2 / 2")));
                scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
            }
        } finally { HomeConfig.save(original); state.put("setup.presented",presented); }
    }
    private static String component(ResolveInfo app) { return new ComponentName(app.activityInfo.packageName,app.activityInfo.name).flattenToString(); }
    private static View find(View view,String text) {
        if (view instanceof TextView && text.contentEquals(((TextView)view).getText())) return view;
        if (view instanceof ViewGroup) for (int index=0;index<((ViewGroup)view).getChildCount();index++) { View result=find(((ViewGroup)view).getChildAt(index),text); if (result!=null) return result; }
        return null;
    }
    private static View description(View view,String text) {
        if (view.getContentDescription()!=null && text.contentEquals(view.getContentDescription())) return view;
        if (view instanceof ViewGroup) for (int index=0;index<((ViewGroup)view).getChildCount();index++) { View result=description(((ViewGroup)view).getChildAt(index),text); if (result!=null) return result; }
        return null;
    }
}
