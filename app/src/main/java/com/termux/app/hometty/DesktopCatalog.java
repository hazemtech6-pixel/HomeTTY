package com.termux.app.hometty;
import java.util.*;

/** Adapted from sabamdarif/termux-desktop's GPLv3 desktop selection table.
 * Pinned source and license: vendor/termux-desktop. No upstream downloader runs.
 */
public final class DesktopCatalog {
    public static final Map<String,String> START;
    static {
        Map<String,String> entries=new LinkedHashMap<>();
        entries.put("xfce","startxfce4"); entries.put("lxqt","startlxqt"); entries.put("openbox","openbox-session");
        entries.put("mate","mate-session"); entries.put("gnome","gnome-session"); entries.put("cinnamon","cinnamon-session");
        entries.put("plasma","startplasma-x11"); entries.put("i3wm","i3"); entries.put("dwm","dwm");
        entries.put("bspwm","bspwm"); entries.put("awesome","awesome"); entries.put("fluxbox","fluxbox");
        entries.put("icewm","icewm-session"); entries.put("wmaker","wmaker"); START=Collections.unmodifiableMap(entries);
    }
    public static String command(String desktop,String backend) {
        String command=START.get(desktop); if (command==null) throw new IllegalArgumentException("Unsupported desktop: "+desktop);
        if ("x11".equals(backend)) return "export DISPLAY=:1; command -v termux-x11 >/dev/null || exit 127; termux-x11 :1 & x11_pid=$!; trap 'kill \"$x11_pid\" 2>/dev/null || true' EXIT; dbus-run-session -- "+command;
        if ("vnc".equals(backend)) return "exec vncserver :1 -localhost yes -xstartup \"$PREFIX/bin/"+command+"\"";
        throw new IllegalArgumentException("Desktop backend must be x11 or vnc");
    }
    public static String x11Package(String appId) { return "com.termux".equals(appId) ? "com.termux.x11" : appId+".x11"; }
}
