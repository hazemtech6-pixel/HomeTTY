package com.termux.app.hometty;

import android.app.*;
import android.app.role.RoleManager;
import android.appwidget.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.widget.*;
import com.termux.app.TermuxActivity;
import com.termux.shared.termux.TermuxConstants;
import java.io.*;
import java.util.*;

/** Android HOME surface; the original Termux activity continues to own the terminal. */
public final class HomeActivity extends Activity {
    private HomeConfig config;
    private LauncherState state;
    private LinearLayout root, content, tabs;
    private String selected = "Home";
    private AppWidgetHost widgets;
    private int pendingWidget = -1;
    private Runnable afterAuthentication;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final int PICK_WIDGET = 10, CONFIGURE_WIDGET = 11, AUTHENTICATE = 12, STORAGE = 13, BACKUP = 14, RESTORE = 15, IMPORT_PLUGIN = 16;
    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { if ("Apps".equals(selected) || "Home".equals(selected)) show(selected); }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        if (saved != null) { selected = saved.getString("tab", "Home"); pendingWidget = saved.getInt("widget", -1); }
        try { config = new HomeConfig(); state = new LauncherState(); }
        catch (Exception e) { configurationError(e); return; }
        widgets = new AppWidgetHost(this, 1024);
        IntentFilter filter = new IntentFilter(); filter.addAction(Intent.ACTION_PACKAGE_ADDED); filter.addAction(Intent.ACTION_PACKAGE_REMOVED); filter.addAction(Intent.ACTION_PACKAGE_CHANGED); filter.addDataScheme("package");
        registerReceiver(packages, filter);
        build();
    }
    private void configurationError(Exception e) {
        LinearLayout recovery = new LinearLayout(this); recovery.setOrientation(LinearLayout.VERTICAL); recovery.setPadding(24,24,24,24); setContentView(recovery);
        TextView message = new TextView(this); message.setText("Configuration error: " + e.getMessage() + "\nEdit the existing file below; nothing has been reset."); recovery.addView(message);
        EditText editor = new EditText(this); try { editor.setText(HomeConfig.read()); } catch (IOException ignored) { editor.setText(HomeConfig.DEFAULT); }
        recovery.addView(editor, new LinearLayout.LayoutParams(-1, 0, 1));
        Button save = new Button(this); save.setText("Validate and save"); recovery.addView(save);
        save.setOnClickListener(v -> { try { HomeConfig.save(editor.getText().toString()); recreate(); } catch (Exception error) { message.setText(error.getMessage()); } });
    }
    @Override protected void onResume() {
        super.onResume();
        if (config!=null && "Settings".equals(selected)) ActivityLock.resume(this,"security.lock_settings");
    }
    @Override protected void onStart() { super.onStart(); if (widgets != null) widgets.startListening(); }
    @Override protected void onStop() { ActivityLock.stop(this); if (widgets != null) widgets.stopListening(); super.onStop(); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); if (widgets != null) unregisterReceiver(packages); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putString("tab", selected); out.putInt("widget", pendingWidget); }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); if (config != null) show("Home"); }
    @Override public void onBackPressed() { if (config != null) show("Home"); }
    private void build() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(16,16,16,8); root.setBackgroundColor(0xcc101418); setContentView(root);
        HorizontalScrollView navigation = new HorizontalScrollView(this); navigation.setHorizontalScrollBarEnabled(false);
        tabs = new LinearLayout(this); navigation.addView(tabs); root.addView(navigation);
        for (String name : new String[]{"Home", "Apps", "Terminal", "Tools", "Desktop", "Settings"}) tab(name);
        if (config.flag("ai.enabled", false)) tab("AI");
        ScrollView scroll = new ScrollView(this); content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
        show(selected);
    }
    private void tab(String name) { Button b = new Button(this); b.setText(name); tabs.addView(b); b.setOnClickListener(v -> {
        if ("Terminal".equals(name)) { terminal(false); return; }
        if ("Settings".equals(name)) { show(name); ActivityLock.resume(this,"security.lock_settings"); } else show(name);
    }); }
    private int dp(int size) { return Math.round(size*getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size) { TextView view = new TextView(this); view.setText(value); view.setTextColor(Color.WHITE); view.setTextSize(size); view.setPadding(8,12,8,12); content.addView(view); return view; }
    private Button action(String label, Runnable runnable) { Button view = new Button(this); view.setText(label); content.addView(view); view.setOnClickListener(v -> runnable.run()); return view; }
    private void show(String name) {
        selected = name; content.removeAllViews();
        switch (name) {
            case "Home": home(); break;
            case "Apps": apps(); break;
            case "Tools": tools(); break;
            case "Settings": settings(); break;
            case "Desktop": desktop(); break;
            case "AI": if (config.flag("ai.enabled", false)) ai(); else show("Home"); break;
            default: show("Home");
        }
    }
    private void home() {
        if (config.flag("home.clock", true)) {
            TextClock clock = new TextClock(this); clock.setTextSize(42); clock.setTextColor(Color.WHITE); clock.setFormat12Hour("h:mm a"); clock.setFormat24Hour("HH:mm"); content.addView(clock);
            clock.setOnClickListener(v -> open(new Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)));
        }
        text(java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL).format(new Date()), 16);
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery != null) { int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE,100); text("Battery " + battery.getIntExtra(BatteryManager.EXTRA_LEVEL,0) * 100 / Math.max(1,scale) + "%",14).setOnClickListener(v -> open(new Intent(Intent.ACTION_POWER_USAGE_SUMMARY))); }
        if (config.flag("weather.enabled",false)) weather();
        if (config.flag("home.media",false)) media();
        android.net.ConnectivityManager network=getSystemService(android.net.ConnectivityManager.class);
        android.net.NetworkInfo active=network.getActiveNetworkInfo();
        text(active!=null && active.isConnected() ? "Network: "+active.getTypeName() : "Offline",14).setOnClickListener(v -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        search();
        if (config.flag("home.dock", true)) { text("Pinned apps",18); renderApps("", true, content); }
        for (int id : widgetIds()) {
            AppWidgetProviderInfo info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id);
            if (info == null) continue;
            AppWidgetHostView view = widgets.createView(this,id,info); int height = Math.max(120, info.minHeight);
            content.addView(view,new LinearLayout.LayoutParams(-1,height));
            view.updateAppWidgetSize(null, 240, height, 400, height);
            Button remove = action("Remove " + info.loadLabel(getPackageManager()), () -> confirm("Remove widget?", () -> { widgets.deleteAppWidgetId(id); ArrayList<Integer> ids = widgetIds(); ids.remove(Integer.valueOf(id)); saveWidgets(ids); show("Home"); }));
        }
        action("Add widget", this::pickWidget);
        action("Wallpaper", () -> open(new Intent(Intent.ACTION_SET_WALLPAPER)));
        action("Choose HomeTTY as Home", this::requestHome);
        action("Setup / resume", () -> open(new Intent(this,com.termux.app.SetupActivity.class)));
    }
    private List<ResolveInfo> activities() {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> result = getPackageManager().queryIntentActivities(intent,0);
        result.removeIf(item -> item.activityInfo.packageName.equals(getPackageName()));
        result.sort((a,b) -> { String x = id(a), y = id(b); int pins = Boolean.compare(state.pinned(y),state.pinned(x)); if (pins != 0) return pins;
            int usage = Integer.compare(state.uses(y),state.uses(x)); return usage != 0 ? usage : label(a).compareToIgnoreCase(label(b)); });
        return result;
    }
    private String id(ResolveInfo item) { return new ComponentName(item.activityInfo.packageName,item.activityInfo.name).flattenToString(); }
    private String label(ResolveInfo item) { return state.get("label." + id(item),item.loadLabel(getPackageManager()).toString()); }
    private void apps() { text("Apps",26); search(); }
    private void search() {
        EditText query = new EditText(this); query.setSingleLine(true); query.setHint("Search apps · app: · shell: · web:"); query.setTextColor(Color.WHITE); content.addView(query);
        LinearLayout results = new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL); content.addView(results);
        Runnable update = () -> { results.removeAllViews(); String value = query.getText().toString().trim();
            if (value.startsWith("shell:")) { resultAction(results,"Run in terminal: " + value.substring(6), () -> confirm("Run this shell command?\n" + value.substring(6), () -> authenticate("security.lock_terminal", () -> ShellCommands.run(this, value.substring(6))))); }
            else if (value.startsWith("web:")) { resultAction(results,"Search the web", () -> open(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search?q=" + Uri.encode(value.substring(4)))))); }
            else renderApps(value.startsWith("app:") ? value.substring(4).trim() : value,false,results);
        };
        query.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s,int start,int count,int after) {} public void onTextChanged(CharSequence s,int start,int before,int count) { update.run(); } public void afterTextChanged(Editable value) {} });
        if ("Apps".equals(selected)) update.run();
    }
    private void resultAction(LinearLayout container, String title, Runnable run) { Button b = new Button(this); b.setText(title); container.addView(b); b.setOnClickListener(v -> run.run()); }
    private void renderApps(String query, boolean pinnedOnly, LinearLayout target) {
        LinearLayout row = null; int index = 0;
        for (ResolveInfo item : activities()) {
            if (pinnedOnly && !state.pinned(id(item))) continue;
            if (!label(item).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) continue;
            if (index++ % config.columns() == 0) { row = new LinearLayout(this); target.addView(row); }
            LinearLayout tile = new LinearLayout(this); tile.setGravity(Gravity.CENTER); tile.setOrientation(LinearLayout.VERTICAL); tile.setPadding(8,12,8,12); row.addView(tile,new LinearLayout.LayoutParams(0,-2,1));
            ImageView icon = new ImageView(this); icon.setImageDrawable(item.loadIcon(getPackageManager())); tile.addView(icon,new LinearLayout.LayoutParams(dp(48),dp(48)));
            TextView label = new TextView(this); label.setText(label(item)); label.setTextColor(Color.WHITE); label.setGravity(Gravity.CENTER); label.setMaxLines(2); tile.addView(label);
            tile.setContentDescription(label(item)); tile.setFocusable(true); tile.setOnClickListener(v -> authenticate("security.lock_apps", () -> {
                Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(ComponentName.unflattenFromString(id(item))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                if (open(launch)) put("uses." + id(item), Integer.toString(Math.min(Integer.MAX_VALUE-1,state.uses(id(item)))+1));
            }));
            tile.setOnLongClickListener(v -> { appMenu(item); return true; });
        }
    }
    private void appMenu(ResolveInfo item) {
        new AlertDialog.Builder(this).setTitle(label(item)).setItems(new String[]{state.pinned(id(item)) ? "Unpin" : "Pin", "Rename", "App info", "Uninstall", "Shortcuts"},(dialog,which) -> {
            if (which == 0) { put("pin." + id(item),Boolean.toString(!state.pinned(id(item)))); show(selected); }
            if (which == 1) { EditText name = new EditText(this); name.setText(label(item)); new AlertDialog.Builder(this).setTitle("App label").setView(name).setPositiveButton("Save",(d,w) -> { put("label." + id(item), name.getText().toString()); show(selected); }).setNegativeButton("Cancel",null).show(); }
            if (which == 2) open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:" + item.activityInfo.packageName)));
            if (which == 3) confirm("Uninstall " + label(item) + "?", () -> open(new Intent(Intent.ACTION_DELETE,Uri.parse("package:" + item.activityInfo.packageName))));
            if (which == 4) shortcuts(item);
        }).show();
    }
    private void shortcuts(ResolveInfo item) {
        if (Build.VERSION.SDK_INT < 25) { toast("Shortcuts require Android 7.1"); return; }
        LauncherApps launcher = getSystemService(LauncherApps.class);
        if (!launcher.hasShortcutHostPermission()) { toast("Choose HomeTTY as your default Home to access shortcuts"); return; }
        try {
            List<ShortcutInfo> shortcuts = launcher.getShortcuts(new LauncherApps.ShortcutQuery().setPackage(item.activityInfo.packageName).setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST | LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED),android.os.Process.myUserHandle());
            if (shortcuts == null || shortcuts.isEmpty()) { toast("No shortcuts available"); return; }
            String[] labels = new String[shortcuts.size()]; for (int i=0;i<labels.length;i++) labels[i] = String.valueOf(shortcuts.get(i).getShortLabel());
            new AlertDialog.Builder(this).setItems(labels,(d,w) -> authenticate("security.lock_apps", () -> { try { launcher.startShortcut(shortcuts.get(w),null,null); } catch (RuntimeException e) { toast(e.getMessage()); } })).show();
        } catch (RuntimeException e) { toast(e.getMessage()); }
    }
    private void tools() {
        text("Tools",26);
        action("Home files", () -> files(HomeConfig.HOME));
        action("Android / external storage", () -> openForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),STORAGE));
        action("Calculator", () -> open(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR)));
        action("Calendar", () -> open(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)));
        action("Android settings", () -> open(new Intent(Settings.ACTION_SETTINGS)));
        action("Wi-Fi", () -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        action("Bluetooth", () -> open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        action("Android system shell", () -> terminal(true));
        action("Plugins and sources", this::plugins);
        action("Privilege providers", this::privilege);
        if (config.flag("privilege.rish", false)) action("RISH", () -> confirm("Start RISH with the privileges granted by your Shizuku provider?", () -> ShellCommands.run(this,"rish")));
    }
    private void privilege() {
        content.removeAllViews(); text("Privilege providers",26); text(PrivilegeBackends.describe(this),16);
        action("Request Shizuku authorization", () -> { try { PrivilegeBackends.requestShizuku(); } catch (Exception e) { toast(e.getMessage()); } });
        action("Refresh capabilities",this::privilege);
        action("Root identity check", () -> authenticate("security.lock_privileged", () -> confirm("Run ‘id’ through your root manager? Its approval is still required.", () -> { try { ShellCommands.system(this,PrivilegeBackends.rootCommand("id")); } catch (Exception e) { toast(e.getMessage()); } })));
        action("Reboot with root", () -> authenticate("security.lock_privileged", () -> confirm("Reboot this Android device now? Unsaved work may be lost. Root permission does not bypass this confirmation.", () -> { try { ShellCommands.system(this,PrivilegeBackends.rootCommand("reboot")); } catch (Exception e) { toast(e.getMessage()); } })));

    }
    private void files(File directory) {
        content.removeAllViews(); text(directory.equals(HomeConfig.HOME) ? "Home files" : directory.getName(),22);
        if (!directory.equals(HomeConfig.HOME)) action("Parent folder", () -> files(directory.getParentFile()));
        File[] entries = directory.listFiles(); if (entries == null) { text("Cannot read this folder",16); return; }
        Arrays.sort(entries,(a,b) -> { int type = Boolean.compare(b.isDirectory(),a.isDirectory()); return type != 0 ? type : a.getName().compareToIgnoreCase(b.getName()); });
        for (File entry : entries) action((entry.isDirectory() ? "▸ " : "") + entry.getName(), () -> {
            try {
                if (!entry.getCanonicalPath().startsWith(HomeConfig.HOME.getCanonicalPath()+"/")) { toast("Use the Android file picker for files outside Home"); return; }
                if (entry.isDirectory()) { files(entry); return; }
                if (entry.length() > 262144) { toast("Preview limited to 256 KiB"); return; }
                try (FileInputStream in = new FileInputStream(entry); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] b = new byte[4096]; int n; while ((n = in.read(b)) != -1) { if (out.size()+n > 262144) throw new IOException("File exceeds preview limit"); out.write(b,0,n); }
                    text(out.toString("UTF-8"),14).setTextIsSelectable(true);
                }
            } catch (IOException e) { toast(e.getMessage()); }
        });
    }
    private void settings() {
        text("Settings",26); text("~/.config/hometty/config.toml",14);
        EditText editor = new EditText(this); editor.setTextColor(Color.WHITE); editor.setTypeface(android.graphics.Typeface.MONOSPACE); editor.setGravity(Gravity.TOP);
        try { editor.setText(HomeConfig.read()); } catch (IOException e) { toast(e.getMessage()); }
        content.addView(editor);
        action("Validate and save", () -> { try { HomeConfig.save(editor.getText().toString()); config = new HomeConfig(); selected = "Home"; build(); } catch (Exception e) { toast(e.getMessage()); } });
        action("Edit in terminal", () -> ShellCommands.run(this,"${EDITOR:-vi} \"$HOME/.config/hometty/config.toml\""));
        action("Back up configuration", () -> openForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/toml").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"hometty-config.toml"),BACKUP));
        action("Restore configuration", () -> openForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),RESTORE));
        action("Terminal preferences", () -> open(new Intent(this,com.termux.app.activities.SettingsActivity.class)));
    }
    private void media() {
        try {
            android.media.session.MediaSessionManager manager=getSystemService(android.media.session.MediaSessionManager.class);
            List<android.media.session.MediaController> sessions=manager.getActiveSessions(new ComponentName(this,MediaAccess.class));
            if (sessions.isEmpty()) text("No active media",14);
            for (android.media.session.MediaController controller:sessions) {
                android.media.MediaMetadata metadata=controller.getMetadata();
                text(metadata==null ? controller.getPackageName() : String.valueOf(metadata.getDescription().getTitle()),18);
                action("Previous",()->controller.getTransportControls().skipToPrevious());
                action("Play / pause",()-> { android.media.session.PlaybackState playback=controller.getPlaybackState(); if (playback!=null && playback.getState()==android.media.session.PlaybackState.STATE_PLAYING) controller.getTransportControls().pause(); else controller.getTransportControls().play(); });
                action("Next",()->controller.getTransportControls().skipToNext());
            }
        } catch (SecurityException e) { action("Allow media controls",()->open(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))); }
    }
    private void weather() {
        TextView view=text("Loading weather…",16);
        double lat=config.number("weather.latitude",Double.NaN), lon=config.number("weather.longitude",Double.NaN);
        if (!Double.isFinite(lat) || !Double.isFinite(lon)) { view.setText("Set weather.latitude and weather.longitude in TOML"); return; }
        view.setOnClickListener(v -> open(new Intent(Intent.ACTION_VIEW,Uri.parse("https://open-meteo.com/"))));
        new Thread(() -> {
            String result;
            try { result=new WeatherProvider.OpenMeteo().current(lat,lon); } catch (Exception e) { result="Weather unavailable: "+e.getMessage(); }
            final String message=result; runOnUiThread(() -> { if (!isDestroyed()) view.setText(message); });
        },"HomeTTY-weather").start();
    }
    private void plugins() {
        content.removeAllViews(); text("Plugins",26);
        action("Import local TOML plugin", () -> openForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),IMPORT_PLUGIN));
        action("Register catalog source", () -> { EditText input=new EditText(this); input.setHint("https://…"); new AlertDialog.Builder(this).setTitle("Register source — does not install or run code").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Register",(d,w)-> { try { java.net.URI uri=java.net.URI.create(input.getText().toString()); if (!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null) throw new IllegalArgumentException("Use an HTTPS catalog URL without credentials"); put("source."+PluginManifest.digest(uri.toString()),uri.toString()); toast("Source registered. No code installed or executed."); } catch (Exception e) { toast(e.getMessage()); } }).show(); });
        File directory=new File(HomeConfig.HOME,".config/hometty/plugins"); File[] entries=directory.listFiles((dir,name)->name.endsWith(".toml"));
        if (entries==null) return;
        for (File entry:entries) {
            try {
                if (entry.length()>262144) throw new IOException("Plugin manifest too large");
                String source=BoundedFiles.read(entry,262144);
                PluginManifest plugin=new PluginManifest(source); String digest=PluginManifest.digest(source);
                boolean granted=state.get("grant."+plugin.id,"").equals(digest);
                action(plugin.name+(granted ? " · Run" : " · Review capabilities"), () -> {
                    if (!granted) { confirm(plugin.name+" requests: "+plugin.capabilities+". Shell access can read and change all files accessible to HomeTTY. Grant these capabilities for this exact manifest?", () -> { put("grant."+plugin.id,digest); plugins(); }); return; }
                    // Re-read before execution so edits cannot inherit a previously reviewed grant.
                    try { if (!digest.equals(PluginManifest.digest(BoundedFiles.read(entry,262144)))) { toast("Plugin changed; review its capabilities again"); plugins(); return; } } catch (IOException e) { toast(e.getMessage()); return; }
                    if ("web".equals(plugin.kind)) open(new Intent(Intent.ACTION_VIEW,Uri.parse(plugin.target)));
                    if ("app".equals(plugin.kind)) authenticate("security.lock_apps", () -> { Intent launch=getPackageManager().getLaunchIntentForPackage(plugin.target); if (launch!=null) open(launch); else toast("Application is not installed"); });
                    if ("shell".equals(plugin.kind)) authenticate("security.lock_terminal", () -> confirm("Run plugin command?\n"+plugin.target, () -> ShellCommands.run(this,plugin.target)));
                });
                if (granted) action("Revoke "+plugin.name, () -> { put("grant."+plugin.id,""); plugins(); });
            } catch (Exception e) { text(entry.getName()+": "+e.getMessage(),14); }
        }
    }
    private void desktop() {
        text("Desktop",26);
        String desktop=config.string("desktop.environment","xfce"), backend=config.string("desktop.backend","x11");
        text("Selected: "+desktop+" · "+backend,18);
        text("Desktop packages and X11 must be rebuilt for HomeTTY. Choose desktop.environment and desktop.backend in TOML. Available desktops: "+DesktopCatalog.START.keySet(),14);
        action("Start desktop", () -> authenticate("security.lock_desktop", () -> {
            try {
                String executable=DesktopCatalog.START.get(desktop);
                if (executable==null || !new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH,"bin/"+executable).canExecute()) { toast("The selected HomeTTY desktop packages are not installed"); return; }
                if ("x11".equals(backend)) { Intent launch=getPackageManager().getLaunchIntentForPackage(DesktopCatalog.x11Package(getPackageName())); if (launch==null) { toast("HomeTTY X11 companion is not installed"); return; } open(launch); }
                ShellCommands.run(this,DesktopCatalog.command(desktop,backend));
            } catch (Exception e) { toast(e.getMessage()); }
        }));
        action("Open desktop files", () -> files(HomeConfig.HOME));
        action("Setup / resume", () -> open(new Intent(this,com.termux.app.SetupActivity.class)));
    }
    private void ai() { text("AI",26); text("External terminal agents run with the permissions of your shell.",16); action("Open terminal", () -> terminal(false)); }
    private void terminal(boolean systemShell) { open(new Intent(this,TermuxActivity.class).putExtra(TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION,systemShell)); }
    private void authenticate(String flag, Runnable next) {
        if (!config.flag(flag,false)) { next.run(); return; }
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        Intent intent = keyguard.createConfirmDeviceCredentialIntent("Unlock HomeTTY", "Confirm your device credential");
        if (intent == null) { toast("Set an Android screen lock before using locked HomeTTY surfaces"); return; }
        afterAuthentication = next; openForResult(intent,AUTHENTICATE);
    }
    private void requestHome() {
        if (Build.VERSION.SDK_INT >= 29) { RoleManager roles = getSystemService(RoleManager.class); if (roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) { open(roles.createRequestRoleIntent(RoleManager.ROLE_HOME)); return; } }
        open(new Intent(Settings.ACTION_HOME_SETTINGS));
    }
    private ArrayList<Integer> widgetIds() { ArrayList<Integer> result = new ArrayList<>(); for (String id : state.get("widgets","").split(",")) try { result.add(Integer.parseInt(id)); } catch (NumberFormatException ignored) {} return result; }
    private void saveWidgets(List<Integer> ids) { StringBuilder value = new StringBuilder(); for (int id : ids) { if (value.length()>0) value.append(','); value.append(id); } put("widgets",value.toString()); }
    private void pickWidget() { pendingWidget = widgets.allocateAppWidgetId(); openForResult(new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,pendingWidget),PICK_WIDGET); }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (ActivityLock.result(this,request,result)) return;
        if ((request==BACKUP || request==RESTORE || request==IMPORT_PLUGIN) && result==RESULT_OK && data!=null && data.getData()!=null) {
            try {
                if (request==BACKUP) { try (OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")) { if (out==null) throw new IOException("Cannot open destination"); out.write(HomeConfig.read().getBytes(java.nio.charset.StandardCharsets.UTF_8)); } toast("Configuration backed up; secrets and runtime grants are excluded"); }
                else {
                    String source;
                    try (InputStream in=getContentResolver().openInputStream(data.getData()); ByteArrayOutputStream out=new ByteArrayOutputStream()) { if (in==null) throw new IOException("Cannot open source"); byte[] b=new byte[4096]; int n; while ((n=in.read(b))!=-1) { if (out.size()+n>262144) throw new IOException("File too large"); out.write(b,0,n); } source=out.toString("UTF-8"); }
                    if (request==RESTORE) { HomeConfig.parse(source); confirm("Replace your configuration with this validated backup?", () -> { try { HomeConfig.save(source); config=new HomeConfig(); selected="Home"; build(); } catch (Exception e) { toast(e.getMessage()); } }); }
                    else { PluginManifest manifest=new PluginManifest(source); confirm("Import "+manifest.name+"? Capabilities are granted separately before it can run.", () -> { try { File directory=new File(HomeConfig.HOME,".config/hometty/plugins"); directory.mkdirs(); android.util.AtomicFile file=new android.util.AtomicFile(new File(directory,manifest.id+".toml")); FileOutputStream out=file.startWrite(); try { out.write(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)); file.finishWrite(out); } catch (IOException e) { file.failWrite(out); throw e; } plugins(); } catch (Exception e) { toast(e.getMessage()); } }); }
                }
            } catch (Exception e) { toast(e.getMessage()); }
            return;
        }
        if (request == AUTHENTICATE) { Runnable next = afterAuthentication; afterAuthentication = null; if (result == RESULT_OK && next != null) next.run(); return; }
        if (request == STORAGE) { if (result == RESULT_OK && data != null && data.getData() != null) { try { int grants=0; if ((data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0) grants |= Intent.FLAG_GRANT_READ_URI_PERMISSION; if ((data.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=0) grants |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION; getContentResolver().takePersistableUriPermission(data.getData(),grants); open(new Intent(Intent.ACTION_VIEW,data.getData()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)); } catch (RuntimeException e) { toast(e.getMessage()); } } return; }
        if (request != PICK_WIDGET && request != CONFIGURE_WIDGET) return;
        if (pendingWidget == -1) return;
        if (result != RESULT_OK) { widgets.deleteAppWidgetId(pendingWidget); pendingWidget = -1; return; }
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(this).getAppWidgetInfo(pendingWidget);
        if (info == null) { widgets.deleteAppWidgetId(pendingWidget); pendingWidget = -1; return; }
        if (request == PICK_WIDGET && info.configure != null) { openForResult(new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(info.configure).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,pendingWidget),CONFIGURE_WIDGET); return; }
        ArrayList<Integer> ids = widgetIds(); ids.add(pendingWidget); saveWidgets(ids); pendingWidget = -1; show("Home");
    }
    private void put(String key,String value) { try { state.put(key,value); } catch (IOException e) { toast("Could not save: " + e.getMessage()); } }
    private boolean open(Intent intent) { try { startActivity(intent); return true; } catch (RuntimeException e) { toast("Unavailable: " + e.getMessage()); return false; } }
    private void openForResult(Intent intent,int request) { try { startActivityForResult(intent,request); } catch (RuntimeException e) { if (request == PICK_WIDGET || request == CONFIGURE_WIDGET) { widgets.deleteAppWidgetId(pendingWidget); pendingWidget = -1; } toast("Unavailable: " + e.getMessage()); } }
    private void toast(String message) { Toast.makeText(this,message,Toast.LENGTH_LONG).show(); }
    private void confirm(String message,Runnable next) { new AlertDialog.Builder(this).setMessage(message).setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w) -> next.run()).show(); }
}
