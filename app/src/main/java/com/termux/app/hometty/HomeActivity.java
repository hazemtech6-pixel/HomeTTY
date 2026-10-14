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
    private String loadedConfiguration;
    private EditText configurationEditor;
    private String configurationBaseline;
    private LauncherState state;
    private List<LocalPlugin> activePlugins=Collections.emptyList();
    private LocalPlugin activeTheme;
    private final java.util.concurrent.ExecutorService indexWorker = java.util.concurrent.Executors.newFixedThreadPool(2);
    private List<SearchIndex.Entry> localIndex = Collections.emptyList();
    private long indexedAt;
    private final List<OllamaClient.Message> aiHistory=new ArrayList<>();
    private String aiDraft="";
    private OllamaClient aiClient;
    private java.util.concurrent.ExecutorService aiWorker;
    private boolean aiRunning;
    private int aiGeneration;
    private boolean indexing;
    private LinearLayout root, content, tabs;
    private String selected = "Home";
    private AppWidgetHost widgets;
    private int pendingWidget = -1;
    private String pendingIcon;
    private Runnable afterAuthentication;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final int PICK_WIDGET = 10, CONFIGURE_WIDGET = 11, AUTHENTICATE = 12, STORAGE = 13, BACKUP = 14, RESTORE = 15, IMPORT_PLUGIN = 16, REQUEST_HOME = 17, PICK_ICON = 18;
    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { if ("Apps".equals(selected) || "Home".equals(selected)) show(selected); }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        if (saved != null) { selected = saved.getString("tab", "Home"); pendingWidget = saved.getInt("widget", -1); pendingIcon=saved.getString("icon"); }
        try { config = new HomeConfig(); loadedConfiguration = HomeConfig.read(); state = new LauncherState(); }
        catch (Exception e) { configurationError(e); return; }
        widgets = new AppWidgetHost(this, 1024);
        IntentFilter filter = new IntentFilter(); filter.addAction(Intent.ACTION_PACKAGE_ADDED); filter.addAction(Intent.ACTION_PACKAGE_REMOVED); filter.addAction(Intent.ACTION_PACKAGE_CHANGED); filter.addDataScheme("package");
        registerReceiver(packages, filter);
        build();
        if (!Boolean.parseBoolean(state.get("setup.presented","false"))) {
            put("setup.presented","true");
            open(new Intent(this,com.termux.app.SetupActivity.class));
        }
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
        if (config != null) {
            try {
                String current = HomeConfig.read();
                if (!current.equals(loadedConfiguration)) {
                    config = new HomeConfig(); loadedConfiguration = current;
                    if (!config.flag("ai.enabled",false)) { cancelAi(); aiHistory.clear(); aiDraft=""; }
                    if ("AI".equals(selected) && !config.flag("ai.enabled", false)) selected = "Home";
                    boolean editing = "Settings".equals(selected) && configurationEditor != null && !configurationEditor.getText().toString().equals(configurationBaseline);
                    if (editing) {
                        toast("Configuration changed externally; your unsaved edit is preserved. Reopen Settings to reload.");
                        if (!config.flag("ai.enabled", false)) for (int index = tabs.getChildCount() - 1; index >= 0; index--) {
                            View view = tabs.getChildAt(index); if (view instanceof Button && "AI".contentEquals(((Button)view).getText())) tabs.removeViewAt(index);
                        }
                    } else build();
                }
            } catch (Exception error) { config = null; configurationError(error); }
        }
        if (config!=null && "AI".equals(selected)) show("AI");
        if (config!=null && "Settings".equals(selected)) ActivityLock.resume(this,"security.lock_settings");
    }
    @Override protected void onStart() { super.onStart(); if (widgets != null) widgets.startListening(); }
    @Override protected void onStop() { ActivityLock.stop(this); cancelAi(); if (widgets != null) widgets.stopListening(); super.onStop(); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); indexWorker.shutdownNow(); if (aiWorker!=null) aiWorker.shutdownNow(); if (widgets != null) unregisterReceiver(packages); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putString("tab", selected); out.putInt("widget", pendingWidget); out.putString("icon",pendingIcon); }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); if (config != null) show("Home"); }
    @Override public void onBackPressed() { if (config != null) show("Home"); }
    private void build() {
        activePlugins=LocalPlugin.list(); activeTheme=null;
        String theme=config.string("theme.plugin","");
        for (LocalPlugin plugin:activePlugins) if ("theme".equals(plugin.manifest.surface) && plugin.manifest.id.equals(theme) && plugin.granted(state)) activeTheme=plugin;
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(16,16,16,8); root.setBackgroundColor(themeColor("background",0xcc101418)); setContentView(root);
        HorizontalScrollView navigation = new HorizontalScrollView(this); navigation.setHorizontalScrollBarEnabled(false);
        tabs = new LinearLayout(this); navigation.addView(tabs); root.addView(navigation);
        for (String name : new String[]{"Home", "Apps", "Terminal", "Tools", "Desktop", "Settings"}) tab(name);
        if (config.flag("ai.enabled", false)) tab("AI");
        for (LocalPlugin plugin:activePlugins) if ("tab".equals(plugin.manifest.surface) && plugin.granted(state)) {
            Button button=new Button(this); button.setText(plugin.manifest.name); styleButton(button); tabs.addView(button); button.setOnClickListener(v -> show("plugin:"+plugin.manifest.id));
        }
        ScrollView scroll = new ScrollView(this); content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
        show(selected);
    }
    private void tab(String name) { Button b = new Button(this); b.setText(name); styleButton(b); tabs.addView(b); b.setOnClickListener(v -> {
        if ("Terminal".equals(name)) { terminal(false); return; }
        if ("Settings".equals(name)) { show(name); ActivityLock.resume(this,"security.lock_settings"); } else show(name);
    }); }
    private int themeColor(String name,int fallback) {
        if (activeTheme==null || !activeTheme.granted(state)) return fallback;
        String value=activeTheme.manifest.colors.get(name); return value==null?fallback:Color.parseColor(value);
    }
    private void styleButton(Button button) { if (activeTheme!=null && activeTheme.granted(state)) { button.setTextColor(themeColor("foreground",Color.WHITE)); button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(themeColor("accent",0xff354552))); } }
    private int dp(int size) { return Math.round(size*getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size) { TextView view = new TextView(this); view.setText(value); view.setTextColor(themeColor("foreground",Color.WHITE)); view.setTextSize(size); view.setPadding(8,12,8,12); content.addView(view); return view; }
    private Button action(String label, Runnable runnable) { Button view = new Button(this); view.setText(label); styleButton(view); content.addView(view); view.setOnClickListener(v -> runnable.run()); return view; }
    private void show(String name) {
        if (!"AI".equals(name) || "AI".equals(selected)) cancelAi();
        selected = name; configurationEditor = null; configurationBaseline = null; content.removeAllViews();
        switch (name) {
            case "Home": home(); break;
            case "Apps": apps(); break;
            case "Tools": tools(); break;
            case "Settings": settings(); break;
            case "Desktop": desktop(); break;
            case "AI": if (config.flag("ai.enabled", false)) ai(); else show("Home"); break;
            default: if (name.startsWith("plugin:")) pluginTab(name.substring(7)); else show("Home");
        }
    }
    private void home() {
        if (config.flag("home.clock", true)) {
            TextClock clock = new TextClock(this); clock.setTextSize(42); clock.setTextColor(themeColor("foreground",Color.WHITE)); clock.setFormat12Hour("h:mm a"); clock.setFormat24Hour("HH:mm"); content.addView(clock);
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
        for (LocalPlugin plugin:activePlugins) if ("widget".equals(plugin.manifest.surface) && plugin.granted(state)) action(plugin.manifest.text, () -> runPlugin(plugin));
        action("Add widget", this::pickWidget);
        action("Wallpaper", () -> open(new Intent(Intent.ACTION_SET_WALLPAPER)));
        action("Choose HomeTTY as Home", this::requestHome);
        action("Setup / resume", () -> open(new Intent(this,com.termux.app.SetupActivity.class)));
    }
    private List<ResolveInfo> activities() {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> result = getPackageManager().queryIntentActivities(intent,0);
        result.removeIf(item -> item.activityInfo.packageName.equals(getPackageName()));
        result.sort((a,b) -> { String x = id(a), y = id(b); int pins = Boolean.compare(isPinned(y),isPinned(x)); if (pins != 0) return pins;
            int usage = Integer.compare(state.uses(y),state.uses(x)); return usage != 0 ? usage : label(a).compareToIgnoreCase(label(b)); });
        return result;
    }
    private String id(ResolveInfo item) { return new ComponentName(item.activityInfo.packageName,item.activityInfo.name).flattenToString(); }
    private String label(ResolveInfo item) { String value=config.stringPath(Arrays.asList("app_overrides",id(item),"label"),state.get("label."+id(item),item.loadLabel(getPackageManager()).toString())); return value.trim().isEmpty()?item.loadLabel(getPackageManager()).toString():value; }
    private android.graphics.drawable.Drawable appIcon(ResolveInfo item) { android.graphics.drawable.Drawable custom=HomeIcons.load(this,config,id(item)); return custom==null?item.loadIcon(getPackageManager()):custom; }
    private void apps() { text("Apps",26); search(); }
    private void search() {
        EditText query = new EditText(this); query.setSingleLine(true); query.setHint("Search · app: · shell: · desktop: · file: · web:"); query.setTextColor(themeColor("foreground",Color.WHITE)); content.addView(query);
        LinearLayout results = new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL); content.addView(results);
        Runnable update = () -> searchResults(query.getText().toString().trim(), results);
        query.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s,int start,int count,int after) {} public void onTextChanged(CharSequence s,int start,int before,int count) { update.run(); } public void afterTextChanged(Editable value) {} });
        if ("Apps".equals(selected)) update.run();
        if (!indexing && System.currentTimeMillis()-indexedAt>60000) {
            indexing=true;
            indexWorker.execute(() -> {
                List<SearchIndex.Entry> entries;
                try { entries=SearchIndex.build(HomeConfig.HOME,new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH)); } catch (IOException | RuntimeException error) { entries=Collections.emptyList(); }
                final List<SearchIndex.Entry> indexed=entries;
                runOnUiThread(() -> { indexing=false; if (isFinishing() || isDestroyed()) return; localIndex=indexed; indexedAt=System.currentTimeMillis(); if (query.isAttachedToWindow() && query.length()>0) update.run(); });
            });
        }
    }
    private void searchResults(String value, LinearLayout results) {
        results.removeAllViews();
        if (value.startsWith("shell:")) { resultAction(results,"Run in terminal: " + value.substring(6), () -> confirm("Run this shell command?\n" + value.substring(6), () -> authenticate("security.lock_terminal", () -> ShellCommands.run(this, value.substring(6))))); return; }
        if (value.startsWith("desktop-web:")) {
            String url="https://www.google.com/search?q="+Uri.encode(value.substring(12));
            resultAction(results,"Search using desktop browser", () -> authenticate("security.lock_desktop", () -> ShellCommands.run(this,"export DISPLAY=${DISPLAY:-:1}; exec xdg-open "+DesktopEntry.quote(url)))); return;
        }
        if (value.startsWith("web:")) { resultAction(results,"Search the web", () -> open(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search?q="+Uri.encode(value.substring(4)))))); return; }
        String kind="", term=value; int colon=value.indexOf(':');
        if (colon>0 && Arrays.asList("app","desktop","file","command","plugin").contains(value.substring(0,colon))) { kind=value.substring(0,colon); term=value.substring(colon+1).trim(); }
        if ("Apps".equals(selected) && kind.isEmpty()) kind="app";
        final String category=kind, needle=term.toLowerCase(Locale.ROOT);
        List<SearchIndex.Entry> matches=new ArrayList<>(); Map<String,ResolveInfo> androidApps=new HashMap<>();
        if (category.isEmpty() || "app".equals(category)) for (ResolveInfo app:activities()) {
            if (label(app).toLowerCase(Locale.ROOT).contains(needle)) { SearchIndex.Entry entry=new SearchIndex.Entry("app",label(app),id(app),""); matches.add(entry); androidApps.put(entry.target,app); }
        }
        if (!value.isEmpty()) for (SearchIndex.Entry entry:localIndex) if ((category.isEmpty() || (category.equals(entry.kind) || "command".equals(category) && "plugin".equals(entry.kind))) && entry.title.toLowerCase(Locale.ROOT).contains(needle)) matches.add(entry);
        matches.sort((a,b) -> { String x="app".equals(a.kind)?a.target:a.id(), y="app".equals(b.kind)?b.target:b.id(); int pins=Boolean.compare(isPinned(y),isPinned(x)); if (pins!=0) return pins; int uses=Integer.compare(state.uses(y),state.uses(x)); return uses!=0?uses:a.title.compareToIgnoreCase(b.title); });
        int count=0;
        for (SearchIndex.Entry entry:matches) {
            if (++count>100) break;
            Button button=new Button(this); button.setText(entry.title+" · "+entry.kind); results.addView(button);
            ResolveInfo app=androidApps.get(entry.target);
            if (app!=null) { android.graphics.drawable.Drawable icon=appIcon(app); icon.setBounds(0,0,dp(32),dp(32)); button.setCompoundDrawables(icon,null,null,null); }
            String key="app".equals(entry.kind)?entry.target:entry.id();
            button.setOnClickListener(v -> {
                Runnable used=() -> put("uses."+key,Integer.toString(Math.min(Integer.MAX_VALUE-1,state.uses(key))+1));
                if ("app".equals(entry.kind)) authenticate("security.lock_apps", () -> { if (open(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(ComponentName.unflattenFromString(entry.target)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) used.run(); });
                else if ("plugin".equals(entry.kind)) { try { runPlugin(new LocalPlugin(new File(entry.target))); used.run(); } catch (Exception error) { toast(error.getMessage()); } }
                else if ("file".equals(entry.kind)) { File file=new File(entry.target); files(file.isDirectory()?file:file.getParentFile()); used.run(); }
                else authenticate("desktop".equals(entry.kind)?"security.lock_desktop":"security.lock_terminal", () -> confirm("Launch "+entry.title+"?\n"+entry.command, () -> { ShellCommands.run(this,entry.command); used.run(); }));
            });
            button.setOnLongClickListener(v -> { if (app!=null) appMenu(app); else confirm(isPinned(key)?"Unpin "+entry.title+"?":"Pin "+entry.title+"?", () -> { togglePin(key); searchResults(value,results); }); return true; });
        }
    }
    private void resultAction(LinearLayout container, String title, Runnable run) { Button b = new Button(this); b.setText(title); styleButton(b); container.addView(b); b.setOnClickListener(v -> run.run()); }
    private void renderApps(String query, boolean pinnedOnly, LinearLayout target) {
        LinearLayout row = null; int index = 0;
        for (ResolveInfo item : activities()) {
            if (pinnedOnly && !isPinned(id(item))) continue;
            if (!label(item).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) continue;
            if (index++ % config.columns() == 0) { row = new LinearLayout(this); target.addView(row); }
            LinearLayout tile = new LinearLayout(this); tile.setGravity(Gravity.CENTER); tile.setOrientation(LinearLayout.VERTICAL); tile.setPadding(8,12,8,12); row.addView(tile,new LinearLayout.LayoutParams(0,-2,1));
            ImageView icon = new ImageView(this); icon.setImageDrawable(appIcon(item)); tile.addView(icon,new LinearLayout.LayoutParams(dp(48),dp(48)));
            TextView label = new TextView(this); label.setText(label(item)); label.setTextColor(themeColor("foreground",Color.WHITE)); label.setGravity(Gravity.CENTER); label.setMaxLines(2); tile.addView(label);
            tile.setContentDescription(label(item)); tile.setFocusable(true); tile.setOnClickListener(v -> authenticate("security.lock_apps", () -> {
                Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(ComponentName.unflattenFromString(id(item))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                if (open(launch)) put("uses." + id(item), Integer.toString(Math.min(Integer.MAX_VALUE-1,state.uses(id(item)))+1));
            }));
            tile.setOnLongClickListener(v -> { appMenu(item); return true; });
        }
    }
    private boolean isPinned(String id) { return config.has("home.pins") ? config.strings("home.pins").contains(id) : state.pinned(id); }
    private void togglePin(String id) {
        try {
            String baseline=HomeConfig.read(); HomeConfig latest=new HomeConfig();
            Set<String> pins=new LinkedHashSet<>(latest.strings("home.pins"));
            if (!latest.has("home.pins")) for (String key:state.keys()) if (key.startsWith("pin.") && state.pinned(key.substring(4))) pins.add(key.substring(4));
            if (!pins.remove(id)) pins.add(id);
            String updated=ConfigEdits.scalar(baseline,"home.pins",new ArrayList<>(pins));
            if (!baseline.equals(HomeConfig.read())) throw new IOException("Configuration changed; retry pinning");
            HomeConfig.save(updated); config=new HomeConfig(); loadedConfiguration=updated;
        } catch (Exception error) { toast("Could not save pins: "+error.getMessage()); }
    }
    private void appMenu(ResolveInfo item) {
        new AlertDialog.Builder(this).setTitle(label(item)).setItems(new String[]{isPinned(id(item)) ? "Unpin" : "Pin", "Rename", "App info", "Uninstall", "Shortcuts", "Choose icon", "Reset icon"},(dialog,which) -> {
            if (which == 0) { togglePin(id(item)); show(selected); }
            if (which == 1) fileName("App label",label(item),name -> { if (name.trim().isEmpty() || name.length()>256) throw new IOException("Use a label of 1–256 characters"); saveAppOverride(id(item),"label",name); });
            if (which == 2) open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:" + item.activityInfo.packageName)));
            if (which == 3) confirm("Uninstall " + label(item) + "?", () -> open(new Intent(Intent.ACTION_DELETE,Uri.parse("package:" + item.activityInfo.packageName))));
            if (which == 4) shortcuts(item);
            if (which == 5) { pendingIcon=id(item); openForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),PICK_ICON); }
            if (which == 6) { try { saveAppOverride(id(item),"icon",""); } catch (Exception error) { toast(error.getMessage()); } }
        }).show();
    }
    private void saveAppOverride(String component,String field,String value) throws IOException {
        String baseline=HomeConfig.read(),updated=ConfigEdits.scalar(baseline,Arrays.asList("app_overrides",component,field),value);
        if (!baseline.equals(HomeConfig.read())) throw new IOException("Configuration changed; retry the override");
        HomeConfig.save(updated); loadedConfiguration=updated; config=new HomeConfig(); build();
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
        String tree=state.get("storage.tree", "");
        if (!tree.isEmpty()) action("Saved Android storage", () -> storage(Uri.parse(tree), null));
        action("Android / external storage", () -> openForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),STORAGE));
        action("Calculator", () -> open(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR)));
        action("Calendar", () -> open(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)));
        action("Android settings", () -> open(new Intent(Settings.ACTION_SETTINGS)));
        action("Wi-Fi", () -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        action("Bluetooth", () -> open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        action("Android system shell", () -> terminal(true));
        action("Plugins and sources", this::plugins);
        action("HomeTTY companions", this::companions);
        action("Privilege providers", this::privilege);
        if (config.flag("privilege.rish", false)) action("RISH", () -> authenticate("security.lock_privileged", () -> confirm("Start RISH with the privileges granted by your Shizuku provider?", () -> ShellCommands.run(this,"rish"))));
    }
    private void companions() {
        content.removeAllViews(); text("HomeTTY companions",26);
        for (String name:CompanionInstaller.NAMES) {
            boolean installed=CompanionInstaller.installed(this,name);
            if (!installed && !CompanionInstaller.bundled(this,name)) { text(name.toUpperCase(Locale.ROOT)+" · Not included in this build",14); continue; }
            action(name.toUpperCase(Locale.ROOT)+(installed ? " · Installed" : " · Install"), () -> {
                if (installed) { Intent launch=getPackageManager().getLaunchIntentForPackage(getPackageName()+"."+name); if (launch!=null) open(launch); else toast("This companion has no launcher activity"); return; }
                confirm("Install HomeTTY:"+name+"? Android will request permission and installation consent.", () -> { try { CompanionInstaller.request(this,name); } catch (Exception e) { toast(e.getMessage()); } });
            });
        }

        action("Refresh",this::companions);
    }
    private void privilege() {
        content.removeAllViews(); text("Privilege providers",26); text(PrivilegeBackends.describe(this),16);
        action("Request Shizuku authorization", () -> { try { PrivilegeBackends.requestShizuku(); } catch (Exception e) { toast(e.getMessage()); } });
        action("Refresh capabilities",this::privilege);
        action("Root identity check", () -> authenticate("security.lock_privileged", () -> confirm("Run ‘id’ through your root manager? Its approval is still required.", () -> { try { ShellCommands.system(this,PrivilegeBackends.rootCommand("id")); } catch (Exception e) { toast(e.getMessage()); } })));
        action("Reboot with root", () -> authenticate("security.lock_privileged", () -> confirm("Reboot this Android device now? Unsaved work may be lost. Root permission does not bypass this confirmation.", () -> { try { ShellCommands.system(this,PrivilegeBackends.rootCommand("reboot")); } catch (Exception e) { toast(e.getMessage()); } })));

    }
    private void files(File directory) {
        try { String path=directory.getCanonicalPath(), home=HomeConfig.HOME.getCanonicalPath(); if (!path.equals(home) && !path.startsWith(home+"/")) { toast("Use the Android storage picker outside Home"); return; } }
        catch (IOException error) { toast(error.getMessage()); return; }
        content.removeAllViews(); text(directory.equals(HomeConfig.HOME) ? "Home files" : directory.getName(),22);
        if (!directory.equals(HomeConfig.HOME)) action("Parent folder", () -> files(directory.getParentFile()));
        action("New folder", () -> fileName("Folder name", "", name -> { HomeFiles.create(HomeConfig.HOME,directory,name,true); files(directory); }));
        action("New file", () -> fileName("File name", "", name -> { HomeFiles.create(HomeConfig.HOME,directory,name,false); files(directory); }));
        File[] entries = directory.listFiles(); if (entries == null) { text("Cannot read this folder",16); return; }
        Arrays.sort(entries,(a,b) -> { int type = Boolean.compare(b.isDirectory(),a.isDirectory()); return type != 0 ? type : a.getName().compareToIgnoreCase(b.getName()); });
        for (File entry : entries) { Button item=action((entry.isDirectory() ? "▸ " : "") + entry.getName(), () -> {
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
            item.setOnLongClickListener(v -> { fileMenu(entry); return true; });
        }
    }
    private interface FileNameAction { void run(String name) throws Exception; }
    private void fileName(String title,String initial,FileNameAction action) {
        EditText name=new EditText(this); name.setSingleLine(true); name.setText(initial);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(name).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create(); dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> { try { action.run(name.getText().toString()); dialog.dismiss(); } catch (Exception error) { name.setError(error.getMessage()); } });
    }
    private void fileMenu(File entry) {
        new AlertDialog.Builder(this).setTitle(entry.getName()).setItems(entry.isDirectory()?new String[]{"Rename","Delete empty folder"}:new String[]{"Rename","Delete","Open with Android app","Share"},(dialog,choice) -> {
            if (choice==0) fileName("Rename",entry.getName(),name -> { HomeFiles.rename(HomeConfig.HOME,entry,name); files(entry.getParentFile()); });
            else if (choice==1) confirm("Delete "+entry.getName()+"? This cannot be undone. Folders must be empty.", () -> { try { HomeFiles.delete(HomeConfig.HOME,entry); files(entry.getParentFile()); } catch (IOException error) { toast(error.getMessage()); } });
            else {
                try {
                    File file=HomeFiles.contained(HomeConfig.HOME,entry);
                    Uri uri=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".homefiles",file);
                    String extension=android.webkit.MimeTypeMap.getFileExtensionFromUrl(file.getName()).toLowerCase(Locale.ROOT);
                    String mime=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension); if (mime==null) mime="application/octet-stream";
                    Intent intent=choice==2?new Intent(Intent.ACTION_VIEW).setDataAndType(uri,mime):new Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM,uri);
                    intent.setClipData(ClipData.newRawUri("HomeTTY file",uri)); intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    open(Intent.createChooser(intent,choice==2?"Open file":"Share file").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                } catch (Exception error) { toast(error.getMessage()); }
            }
        }).show();
    }
    private void storage(Uri tree,Uri document) { storage(tree,document,new ArrayList<>()); }
    private void storage(Uri tree,Uri document,List<Uri> parents) {
        Uri current=document==null?android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,android.provider.DocumentsContract.getTreeDocumentId(tree)):document;
        content.removeAllViews(); text("Android storage",22); action("Tools", () -> show("Tools"));
        if (!parents.isEmpty()) action("Parent folder", () -> { List<Uri> previous=new ArrayList<>(parents); Uri parent=previous.remove(previous.size()-1); storage(tree,parent,previous); });
        LinearLayout listing=new LinearLayout(this); listing.setOrientation(LinearLayout.VERTICAL); content.addView(listing);
        TextView loading=new TextView(this); loading.setText("Loading…"); listing.addView(loading);
        indexWorker.execute(() -> {
            List<StorageEntry> entries=new ArrayList<>(); int parentFlags=0; String failure=null;
            try {
                String[] columns={android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,android.provider.DocumentsContract.Document.COLUMN_FLAGS};
                try (android.database.Cursor parent=getContentResolver().query(current,new String[]{android.provider.DocumentsContract.Document.COLUMN_FLAGS},null,null,null)) { if (parent!=null && parent.moveToFirst()) parentFlags=parent.getInt(0); }
                Uri children=android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree,android.provider.DocumentsContract.getDocumentId(current));
                try (android.database.Cursor cursor=getContentResolver().query(children,columns,null,null,null)) {
                    if (cursor==null) throw new IOException("Storage provider returned no listing");
                    while (cursor.moveToNext() && entries.size()<1000) entries.add(new StorageEntry(android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,cursor.getString(0)),cursor.getString(1),cursor.getString(2),cursor.getInt(3)));
                }
                entries.sort((x,y) -> { int type=Boolean.compare(y.directory(),x.directory()); return type!=0?type:x.name.compareToIgnoreCase(y.name); });
            } catch (Exception error) { failure=error.getMessage(); }
            final String error=failure; final int flags=parentFlags;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || !listing.isAttachedToWindow()) return;
                listing.removeAllViews();
                if (error!=null) { loading.setText("Cannot read storage: "+error+". Choose the folder again if its permission was revoked."); listing.addView(loading); return; }
                if ((flags & android.provider.DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE)!=0) {
                    resultAction(listing,"New folder", () -> fileName("Folder name","",name -> { HomeFiles.validName(name); storageChange(() -> { if (android.provider.DocumentsContract.createDocument(getContentResolver(),current,android.provider.DocumentsContract.Document.MIME_TYPE_DIR,HomeFiles.validName(name))==null) throw new IOException("Storage provider rejected creation"); },tree,current,parents,listing); }));
                    resultAction(listing,"New file", () -> fileName("File name","",name -> { HomeFiles.validName(name); storageChange(() -> { if (android.provider.DocumentsContract.createDocument(getContentResolver(),current,"text/plain",HomeFiles.validName(name))==null) throw new IOException("Storage provider rejected creation"); },tree,current,parents,listing); }));
                }
                for (StorageEntry entry:entries) {
                    Button item=new Button(this); item.setText((entry.directory()?"▸ ":"")+entry.name); listing.addView(item);
                    item.setOnClickListener(v -> { if (entry.directory()) { List<Uri> next=new ArrayList<>(parents); next.add(current); storage(tree,entry.uri,next); } else open(new Intent(Intent.ACTION_VIEW).setDataAndType(entry.uri,entry.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)); });
                    item.setOnLongClickListener(v -> {
                        List<String> actions=new ArrayList<>(); if ((entry.flags & android.provider.DocumentsContract.Document.FLAG_SUPPORTS_RENAME)!=0) actions.add("Rename"); if ((entry.flags & android.provider.DocumentsContract.Document.FLAG_SUPPORTS_DELETE)!=0) actions.add("Delete"); if (!entry.directory()) actions.add("Share");
                        if (actions.isEmpty()) return true;
                        new AlertDialog.Builder(this).setTitle(entry.name).setItems(actions.toArray(new String[0]),(dialog,choice) -> {
                            String operation=actions.get(choice);
                            if ("Rename".equals(operation)) fileName("Rename",entry.name,name -> { HomeFiles.validName(name); storageChange(() -> { if (android.provider.DocumentsContract.renameDocument(getContentResolver(),entry.uri,HomeFiles.validName(name))==null) throw new IOException("Storage provider rejected rename"); },tree,current,parents,listing); });
                            else if ("Delete".equals(operation)) confirm("Delete "+entry.name+(entry.directory()?" and all its contents":"")+"? This cannot be undone.", () -> storageChange(() -> { if (!android.provider.DocumentsContract.deleteDocument(getContentResolver(),entry.uri)) throw new IOException("Storage provider rejected deletion"); },tree,current,parents,listing));
                            else { Intent intent=new Intent(Intent.ACTION_SEND).setType(entry.mime).putExtra(Intent.EXTRA_STREAM,entry.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); intent.setClipData(ClipData.newRawUri(entry.name,entry.uri)); open(Intent.createChooser(intent,"Share file").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)); }
                        }).show(); return true;
                    });
                }
            });
        });
    }
    private interface StorageOperation { void run() throws Exception; }
    private void storageChange(StorageOperation operation,Uri tree,Uri current,List<Uri> parents,LinearLayout listing) {
        indexWorker.execute(() -> { String failure=null; try { operation.run(); } catch (Exception error) { failure=error.getMessage(); } final String message=failure; runOnUiThread(() -> { if (isFinishing() || isDestroyed()) return; if (message!=null) toast(message); if (listing.isAttachedToWindow()) storage(tree,current,parents); }); });
    }
    private static final class StorageEntry {
        final Uri uri; final String name,mime; final int flags;
        StorageEntry(Uri uri,String name,String mime,int flags) { this.uri=uri; this.name=name; this.mime=mime; this.flags=flags; }
        boolean directory() { return android.provider.DocumentsContract.Document.MIME_TYPE_DIR.equals(mime); }
    }
    private void settings() {
        text("Settings",26); text("~/.config/hometty/config.toml",14);
        EditText editor = new EditText(this); editor.setTextColor(themeColor("foreground",Color.WHITE)); editor.setTypeface(android.graphics.Typeface.MONOSPACE); editor.setGravity(Gravity.TOP);
        final String baseline;
        try { baseline = HomeConfig.read(); editor.setText(baseline); } catch (IOException e) { toast(e.getMessage()); return; }
        configurationEditor = editor; configurationBaseline = baseline;
        content.addView(editor);
        action("Validate and save", () -> { try { if (!baseline.equals(HomeConfig.read())) throw new IOException("Configuration changed externally; reopen Settings before saving"); HomeConfig.save(editor.getText().toString()); loadedConfiguration = HomeConfig.read(); config = new HomeConfig(); selected = "Home"; build(); } catch (Exception e) { toast(e.getMessage()); } });
        action("Settings TUI", this::settingsTui);
        action("Edit in terminal", () -> ShellCommands.run(this,"${EDITOR:-vi} \"$HOME/.config/hometty/config.toml\""));
        action("Back up configuration", () -> openForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/toml").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"hometty-config.toml"),BACKUP));
        action("Restore configuration", () -> openForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),RESTORE));
        action(HomePassword.configured() ? "Change HomeTTY password / PIN" : "Set HomeTTY password / PIN", () -> deviceAuthentication(() -> HomePassword.prompt(this, true, () -> { toast("HomeTTY password saved; enable the desired lock flags in TOML"); settings(); }, () -> {})));
        if (HomePassword.configured()) action("Remove HomeTTY password / PIN", () -> deviceAuthentication(() -> confirm("Remove the HomeTTY password? Enabled locks will use Android credentials.", () -> { HomePassword.remove(); settings(); })));
        action("Terminal preferences", () -> open(new Intent(this,com.termux.app.activities.SettingsActivity.class)));
    }
    private void settingsTui() {
        File python = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH, "bin/python3");
        if (!python.canExecute()) { toast("Install the rebuilt HomeTTY Python package to use the settings TUI"); return; }
        File directory = new File(HomeConfig.DATA, "bin");
        if (!directory.isDirectory() && !directory.mkdirs()) { toast("Cannot create TUI directory"); return; }
        File target = new File(directory, "settings.py");
        android.util.AtomicFile file = new android.util.AtomicFile(target);
        FileOutputStream output = null;
        try (InputStream input = getAssets().open("hometty_settings.py")) {
            output = file.startWrite(); byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            file.finishWrite(output); output = null;
            ShellCommands.run(this, python.getAbsolutePath() + " '" + target.getAbsolutePath() + "'");
        } catch (IOException error) { if (output != null) file.failWrite(output); toast(error.getMessage()); }
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
            try { result=WeatherCache.current(new WeatherProvider.OpenMeteo(),HomeConfig.CACHE,lat,lon,System.currentTimeMillis()); } catch (Exception e) { result="Weather unavailable: "+e.getMessage(); }
            final String message=result; runOnUiThread(() -> { if (!isDestroyed() && view.isAttachedToWindow()) view.setText(message); });
        },"HomeTTY-weather").start();
    }
    private void plugins() {
        content.removeAllViews(); activePlugins=LocalPlugin.list(); text("Plugins",26);
        action("Official registry (bundled)", () -> browseCatalog("asset:/plugins/catalog.toml"));
        action("Import local TOML plugin", () -> openForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),IMPORT_PLUGIN));
        action("Register catalog source", () -> fileName("HTTPS catalog URL", "https://", address -> {
            String baseline=HomeConfig.read(), updated=PluginSources.register(baseline,address.trim());
            if (!baseline.equals(HomeConfig.read())) throw new IOException("Configuration changed; retry registration");
            HomeConfig.save(updated); loadedConfiguration=updated; config=new HomeConfig(); toast("Source registered. No plugin was downloaded or executed."); plugins();
        }));
        try {
            // Migrate registrations made by earlier development builds into human TOML.
            String baseline=HomeConfig.read(), updated=baseline;
            for (String key:state.keys()) if (key.startsWith("source.") && !state.get(key,"").isEmpty()) updated=PluginSources.register(updated,state.get(key,""));
            if (!updated.equals(baseline)) { if (!baseline.equals(HomeConfig.read())) throw new IOException("Configuration changed"); HomeConfig.save(updated); loadedConfiguration=updated; config=new HomeConfig(); }
            for (String key:state.keys()) if (key.startsWith("source.") && !state.get(key,"").isEmpty()) put(key,"");
            for (PluginSources.Source source:PluginSources.parse(HomeConfig.parse(updated))) action("Browse "+source.name, () -> browseCatalog(source.url));
        } catch (Exception error) { text("Catalog registrations: "+error.getMessage(),14); }
        for (LocalPlugin plugin:activePlugins) {
            boolean granted=plugin.granted(state);
            action(plugin.manifest.name+" · "+plugin.manifest.version+" · "+plugin.manifest.surface+(granted?" · Run / apply":" · Review"), () -> runPlugin(plugin));
            if (granted) action("Revoke "+plugin.manifest.name, () -> { put("grant."+plugin.manifest.id,""); indexedAt=0; build(); plugins(); });
            action("Remove "+plugin.manifest.name, () -> confirm("Remove this plugin manifest and revoke its grants?", () -> { try { HomeFiles.delete(HomeConfig.HOME,plugin.file); put("grant."+plugin.manifest.id,""); indexedAt=0; build(); plugins(); } catch (IOException error) { toast(error.getMessage()); } }));
        }
        if (activeTheme!=null) action("Use default theme", () -> authenticate("security.lock_settings", () -> setScalar("theme.plugin","")));
    }
    private void setScalar(String key,Object value) {
        try { String baseline=HomeConfig.read(), updated=ConfigEdits.scalar(baseline,key,value); if (!baseline.equals(HomeConfig.read())) throw new IOException("Configuration changed; retry the edit"); HomeConfig.save(updated); loadedConfiguration=updated; config=new HomeConfig(); build(); }
        catch (Exception error) { toast(error.getMessage()); }
    }
    private void pluginTab(String id) {
        for (LocalPlugin plugin:activePlugins) if (plugin.manifest.id.equals(id) && "tab".equals(plugin.manifest.surface) && plugin.granted(state)) {
            try { LocalPlugin current=new LocalPlugin(plugin.file); if (!current.digest.equals(plugin.digest)) throw new IOException("Plugin changed; review its capabilities again"); text(plugin.manifest.name,26); text(plugin.manifest.text,16); action("Run action", () -> runPlugin(plugin)); return; }
            catch (Exception error) { toast(error.getMessage()); break; }
        }
        show("Home");
    }
    private void runPlugin(LocalPlugin snapshot) {
        final LocalPlugin plugin;
        try { plugin=new LocalPlugin(snapshot.file); } catch (Exception error) { toast(error.getMessage()); return; }
        if (!plugin.granted(state)) {
            confirm(plugin.manifest.name+" requests "+plugin.manifest.capabilities+" for its "+plugin.manifest.surface+" surface. Shell access can read or change files accessible to HomeTTY. Grant for this exact manifest?", () -> {
                try { if (!new LocalPlugin(plugin.file).digest.equals(plugin.digest)) throw new IOException("Plugin changed during review"); put("grant."+plugin.manifest.id,plugin.digest); indexedAt=0; build(); plugins(); }
                catch (Exception error) { toast(error.getMessage()); }
            }); return;
        }
        Runnable execute=() -> {
            try {
                LocalPlugin current=new LocalPlugin(plugin.file); if (!current.digest.equals(plugin.digest) || !current.granted(state)) throw new IOException("Plugin changed or its capabilities were revoked");
                PluginManifest manifest=current.manifest;
                if ("web".equals(manifest.kind)) open(new Intent(Intent.ACTION_VIEW,Uri.parse(manifest.target)));
                else if ("app".equals(manifest.kind)) { Intent launch=getPackageManager().getLaunchIntentForPackage(manifest.target); if (launch!=null) open(launch); else toast("Application is not installed"); }
                else if ("shell".equals(manifest.kind)) ShellCommands.run(this,manifest.target);
                else if ("settings".equals(manifest.kind)) {
                    Intent intent;
                    switch (manifest.target) { case "wifi": intent=new Intent(Settings.ACTION_WIFI_SETTINGS); break; case "bluetooth": intent=new Intent(Settings.ACTION_BLUETOOTH_SETTINGS); break; case "calendar": intent=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR); break; case "calculator": intent=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR); break; default: intent=new Intent(Settings.ACTION_SETTINGS); }
                    open(intent);
                } else if ("theme".equals(manifest.kind)) setScalar("theme.plugin",manifest.id);
            } catch (Exception error) { toast(error.getMessage()); }
        };
        if ("shell".equals(plugin.manifest.kind)) authenticate("security.lock_terminal", () -> confirm("Run plugin command?\n"+plugin.manifest.target,execute));
        else if ("app".equals(plugin.manifest.kind)) authenticate("security.lock_apps",execute);
        else if ("theme".equals(plugin.manifest.kind)) authenticate("security.lock_settings",execute);
        else execute.run();
    }
    private String pluginAsset(String address) throws IOException {
        if (!address.matches("asset:/plugins/[a-z0-9_-]+\\.toml")) throw new IOException("Invalid bundled plugin path");
        try (InputStream input=getAssets().open(address.substring(7)); ByteArrayOutputStream bytes=new ByteArrayOutputStream()) { byte[] buffer=new byte[4096]; int count; while ((count=input.read(buffer))!=-1) { if (bytes.size()+count>262144) throw new IOException("Bundled manifest too large"); bytes.write(buffer,0,count); } return bytes.toString("UTF-8"); }
    }
    private void browseCatalog(String address) {
        content.removeAllViews(); text("Plugin catalog",26); text(address,14); action("Plugins",this::plugins);
        TextView status=text("Loading catalog…",14); LinearLayout entries=new LinearLayout(this); entries.setOrientation(LinearLayout.VERTICAL); content.addView(entries);
        indexWorker.execute(() -> {
            PluginCatalog catalog=null; String failure=null;
            try { boolean bundled=address.startsWith("asset:"); catalog=new PluginCatalog(bundled?pluginAsset(address):PluginNetwork.fetch(address),bundled); }
            catch (Exception error) { failure=error.getMessage(); }
            final PluginCatalog loaded=catalog; final String message=failure;
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing() || !entries.isAttachedToWindow()) return;
                if (message!=null) { status.setText("Catalog unavailable: "+message); return; }
                status.setText("Choose a manifest to review. Downloading and importing do not grant capabilities.");
                for (PluginCatalog.Entry entry:loaded.entries) {
                    LocalPlugin installed=null; for (LocalPlugin plugin:LocalPlugin.list()) if (plugin.manifest.id.equals(entry.id)) installed=plugin;
                    String label=entry.name+" · "+entry.version+(installed==null?" · Review install":installed.digest.equals(entry.sha256)?" · Installed":" · Review update");
                    resultAction(entries,label, () -> reviewCatalogEntry(entry,entries,status));
                }
            });
        });
    }
    private void reviewCatalogEntry(PluginCatalog.Entry entry,LinearLayout entries,TextView status) {
        status.setText("Downloading manifest for review…");
        indexWorker.execute(() -> {
            String source=null,failure=null; PluginManifest manifest=null;
            try { source=entry.url.startsWith("asset:")?pluginAsset(entry.url):PluginNetwork.fetch(entry.url); manifest=entry.verify(source); }
            catch (Exception error) { failure=error.getMessage(); }
            final String downloaded=source,message=failure; final PluginManifest plugin=manifest;
            runOnUiThread(() -> { if (isDestroyed() || isFinishing() || !entries.isAttachedToWindow()) return; if (message!=null) { status.setText("Manifest rejected: "+message); return; } confirm("Import "+plugin.name+" "+plugin.version+"? Requested capabilities: "+plugin.capabilities+". Existing content will be replaced; changed content loses grants. Nothing runs during import.", () -> importPlugin(downloaded)); });
        });
    }
    private void importPlugin(String source) {
        try {
            PluginManifest manifest=new PluginManifest(source); File directory=new File(HomeConfig.HOME,".config/hometty/plugins"); if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create plugin directory");
            android.util.AtomicFile file=new android.util.AtomicFile(HomeFiles.child(HomeConfig.HOME,directory,manifest.id+".toml")); FileOutputStream output=file.startWrite();
            try { output.write(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)); file.finishWrite(output); } catch (IOException error) { file.failWrite(output); throw error; }
            indexedAt=0; build(); plugins();
        } catch (Exception error) { toast(error.getMessage()); }
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
    private void cancelAi() {
        aiGeneration++; aiRunning=false; if (aiClient!=null) { aiClient.cancel(); aiClient=null; }
    }
    private void ai() {
        if (!config.flag("ai.enabled",false)) return;
        text("AI",26);
        String command=config.string("ai.external_command", "");
        if (!command.isEmpty()) action("Start external terminal agent", () -> authenticate("security.lock_terminal", () -> confirm("Run your configured terminal agent?\n"+command, () -> ShellCommands.run(this,command))));
        action("Open terminal", () -> terminal(false));
        if (!"ollama".equals(config.string("ai.backend","external"))) { text("Set ai.external_command to an installed agent, or select ai.backend = \"ollama\" for the built-in client.",14); return; }
        final AiEndpoint endpoint;
        try { endpoint=new AiEndpoint(config.string("ai.endpoint",""),com.termux.BuildConfig.DEBUG); }
        catch (Exception error) { text("Configure ai.endpoint and ai.model in TOML. "+error.getMessage(),14); return; }
        String model=config.string("ai.model", "");
        text("Ollama · "+endpoint.base+" · "+model,14);
        text("Messages go to this server. Responses do not run commands. Conversation history stays in memory.",14);
        TextView transcript=text(aiTranscript(),16); transcript.setTextIsSelectable(true);
        EditText input=new EditText(this); input.setHint("Message"); input.setTextColor(themeColor("foreground",Color.WHITE)); input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(16384)}); input.setText(aiDraft); content.addView(input);
        input.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s,int a,int c,int n) {} public void onTextChanged(CharSequence s,int a,int b,int c) { aiDraft=s.toString(); } public void afterTextChanged(Editable e) {} });
        TextView status=text("",14);
        action("Send", () -> {
            if (aiRunning || !config.flag("ai.enabled",false)) return;
            String prompt=input.getText().toString().trim(); if (prompt.isEmpty()) return;
            List<OllamaClient.Message> history=new ArrayList<>(aiHistory); OllamaClient.Message message=new OllamaClient.Message("user",prompt); history.add(message);
            aiRunning=true; int generation=++aiGeneration; OllamaClient client=new OllamaClient(); aiClient=client;
            status.setText("Waiting for server…"); input.setEnabled(false);
            if (aiWorker==null) aiWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
            aiWorker.execute(() -> {
                String answer=null,failure=null;
                try { answer=client.chat(endpoint,model,history,AiSecrets.get(endpoint)); } catch (Exception error) { failure=error.getMessage(); }
                final String response=answer,error=failure;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || generation!=aiGeneration || !input.isAttachedToWindow() || !config.flag("ai.enabled",false)) return;
                    aiRunning=false; aiClient=null; input.setEnabled(true);
                    if (error!=null) status.setText("Request failed: "+error);
                    else { aiHistory.add(message); aiHistory.add(new OllamaClient.Message("assistant",response)); while (aiHistory.size()>30) { aiHistory.remove(0); aiHistory.remove(0); } transcript.setText(aiTranscript()); input.setText(""); status.setText("Reply received"); }
                });
            });
        });
        action("Cancel request", () -> { cancelAi(); input.setEnabled(true); status.setText("Request canceled"); });
        action("New conversation", () -> { cancelAi(); aiHistory.clear(); aiDraft=""; show("AI"); });
        action(AiSecrets.configured()?"Replace server credential":"Server credential (optional)", () -> authenticate("security.lock_settings", () -> aiCredential(endpoint)));
        if (AiSecrets.configured()) action("Remove server credential", () -> authenticate("security.lock_settings", () -> confirm("Remove the saved AI server credential?", () -> { AiSecrets.remove(); show("AI"); })));
    }
    private String aiTranscript() {
        StringBuilder output=new StringBuilder(); for (OllamaClient.Message message:aiHistory) output.append("user".equals(message.role)?"You: ":"Assistant: ").append(message.content).append("\n\n"); return output.toString();
    }
    private void aiCredential(AiEndpoint endpoint) {
        if (!config.flag("ai.enabled",false)) return;
        if (!endpoint.encrypted()) { toast("Credentials require an HTTPS server"); return; }
        EditText secret=new EditText(this); secret.setSingleLine(true); secret.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD); secret.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4096)});
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Credential for "+endpoint.base).setView(secret).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create(); dialog.show(); dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String credential=secret.getText().toString(); secret.getText().clear();
            try { HomeConfig latest=new HomeConfig(); if (!latest.flag("ai.enabled",false) || !new AiEndpoint(latest.string("ai.endpoint",""),com.termux.BuildConfig.DEBUG).base.equals(endpoint.base)) throw new IOException("AI settings changed; review the server again"); AiSecrets.put(endpoint,credential); dialog.dismiss(); toast("Server credential saved in Android Keystore"); }
            catch (Exception error) { secret.setError(error.getMessage()); }
        });
    }
    private void terminal(boolean systemShell) { open(new Intent(this,TermuxActivity.class).putExtra(TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION,systemShell)); }
    private void authenticate(String flag, Runnable next) {
        if (!config.flag(flag,false)) { next.run(); return; }
        if (HomePassword.configured()) { HomePassword.prompt(this, false, next, () -> {}); return; }
        deviceAuthentication(next);
    }
    private void deviceAuthentication(Runnable next) {
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        Intent intent = keyguard.createConfirmDeviceCredentialIntent("Unlock HomeTTY", "Confirm your device credential");
        if (intent == null) { toast("Set an Android screen lock before using locked HomeTTY surfaces"); return; }
        afterAuthentication = next; openForResult(intent,AUTHENTICATE);
    }
    private void requestHome() {
        if (Build.VERSION.SDK_INT >= 29) { RoleManager roles = getSystemService(RoleManager.class); if (roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) { openForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME),REQUEST_HOME); return; } }
        open(new Intent(Settings.ACTION_HOME_SETTINGS));
    }
    private ArrayList<Integer> widgetIds() { ArrayList<Integer> result = new ArrayList<>(); for (String id : state.get("widgets","").split(",")) try { result.add(Integer.parseInt(id)); } catch (NumberFormatException ignored) {} return result; }
    private void saveWidgets(List<Integer> ids) { StringBuilder value = new StringBuilder(); for (int id : ids) { if (value.length()>0) value.append(','); value.append(id); } put("widgets",value.toString()); }
    private void pickWidget() { pendingWidget = widgets.allocateAppWidgetId(); openForResult(new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,pendingWidget),PICK_WIDGET); }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (ActivityLock.result(this,request,result)) return;
        if (request==PICK_ICON) {
            String component=pendingIcon; pendingIcon=null;
            if (result==RESULT_OK && component!=null && data!=null && data.getData()!=null) {
                Uri uri=data.getData(); toast("Importing icon…");
                indexWorker.execute(() -> {
                    String icon=null,failure=null;
                    try (InputStream input=getContentResolver().openInputStream(uri); ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
                        if (input==null) throw new IOException("Cannot read selected image"); byte[] buffer=new byte[8192]; int count;
                        while ((count=input.read(buffer))!=-1) { if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Icon import canceled"); if (bytes.size()+count>1048576) throw new IOException("Icon image exceeds 1 MiB"); bytes.write(buffer,0,count); }
                        icon=HomeIcons.store(bytes.toByteArray());
                    } catch (Exception error) { failure=error.getMessage(); }
                    final String stored=icon,message=failure;
                    runOnUiThread(() -> { if (isFinishing() || isDestroyed()) return; if (message!=null) toast(message); else try { saveAppOverride(component,"icon",stored); } catch (Exception error) { toast(error.getMessage()); } });
                });
            } return;
        }
        if (request==REQUEST_HOME) {
            if (Build.VERSION.SDK_INT>=29 && getSystemService(RoleManager.class).isRoleHeld(RoleManager.ROLE_HOME)) toast("HomeTTY is your default Home");
            return;
        }
        if ((request==BACKUP || request==RESTORE || request==IMPORT_PLUGIN) && result==RESULT_OK && data!=null && data.getData()!=null) {
            try {
                if (request==BACKUP) { try (OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")) { if (out==null) throw new IOException("Cannot open destination"); out.write(HomeConfig.read().getBytes(java.nio.charset.StandardCharsets.UTF_8)); } toast("Configuration backed up; secrets and runtime grants are excluded"); }
                else {
                    String source;
                    try (InputStream in=getContentResolver().openInputStream(data.getData()); ByteArrayOutputStream out=new ByteArrayOutputStream()) { if (in==null) throw new IOException("Cannot open source"); byte[] b=new byte[4096]; int n; while ((n=in.read(b))!=-1) { if (out.size()+n>262144) throw new IOException("File too large"); out.write(b,0,n); } source=out.toString("UTF-8"); }
                    if (request==RESTORE) { HomeConfig.parse(source); confirm("Replace your configuration with this validated backup?", () -> { try { HomeConfig.save(source); config=new HomeConfig(); selected="Home"; build(); } catch (Exception e) { toast(e.getMessage()); } }); }
                    else { PluginManifest manifest=new PluginManifest(source); confirm("Import "+manifest.name+"? Capabilities are granted separately before it can run.", () -> { importPlugin(source); }); }
                }
            } catch (Exception e) { toast(e.getMessage()); }
            return;
        }
        if (request == AUTHENTICATE) { Runnable next = afterAuthentication; afterAuthentication = null; if (result == RESULT_OK && next != null) { ActivityLock.authorized(this); next.run(); } return; }
        if (request == STORAGE) { if (result == RESULT_OK && data != null && data.getData() != null) { try { int grants=0; if ((data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0) grants |= Intent.FLAG_GRANT_READ_URI_PERMISSION; if ((data.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=0) grants |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION; getContentResolver().takePersistableUriPermission(data.getData(),grants); put("storage.tree",data.getData().toString()); storage(data.getData(),null); } catch (RuntimeException e) { toast(e.getMessage()); } } return; }
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
