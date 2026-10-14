package com.termux.app.hometty;
import java.util.*;

/** Fixed package recipes adapted from the pinned desktop catalog; never remote scripts. */
public final class InstallRecipes {
    public static final Map<String,String> DESKTOP_PACKAGES;
    static {
        Map<String,String> packages=new LinkedHashMap<>();
        packages.put("xfce","xfce4 xfce4-terminal thunar");
        packages.put("lxqt","lxqt openbox"); packages.put("openbox","openbox xterm");
        packages.put("mate","mate mate-terminal"); packages.put("gnome","gnome-shell gnome-session gnome-terminal");
        packages.put("cinnamon","cinnamon nemo gnome-terminal"); packages.put("plasma","plasma");
        packages.put("i3wm","i3 i3status xterm"); packages.put("dwm","dwm st");
        packages.put("bspwm","bspwm sxhkd xterm"); packages.put("awesome","awesome xterm");
        packages.put("fluxbox","fluxbox xterm"); packages.put("icewm","icewm xterm"); packages.put("wmaker","wmaker xterm");
        DESKTOP_PACKAGES=Collections.unmodifiableMap(packages);
    }
    public static String command(String recipe,String prefix,String desktop,String backend) {
        if (!prefix.matches("/data/data/[a-zA-Z0-9_.]+/files/usr")) throw new IllegalArgumentException("Invalid package prefix");
        String packages,verify;
        if ("tools".equals(recipe)) { packages="python termux-api"; verify="test -x \"$PREFIX/bin/python3\" && test -x \"$PREFIX/bin/termux-battery-status\""; }
        else if ("desktop".equals(recipe)) {
            packages=DESKTOP_PACKAGES.get(desktop); if (packages==null) throw new IllegalArgumentException("Unknown desktop");
            if ("x11".equals(backend)) packages+=" hometty-x11";
            else if ("vnc".equals(backend)) packages+=" tigervnc";
            else throw new IllegalArgumentException("Unknown display backend");
            packages+=" dbus pulseaudio";
            verify="test -x \"$PREFIX/bin/"+DesktopCatalog.START.get(desktop)+"\"";
        } else throw new IllegalArgumentException("Unknown installation recipe");
        // Do not add stock repositories or suppress apt/dpkg questions. APT status
        // records share the PTY with output and are consumed by InstallProgress.
        return "export PREFIX='"+prefix+"'; export PATH=\"$PREFIX/bin:/system/bin\"; "+
            "test -x \"$PREFIX/bin/apt-get\" || { echo 'HomeTTY bootstrap required'; exit 127; }; "+
            "\"$PREFIX/bin/apt-get\" update && "+
            "\"$PREFIX/bin/apt-get\" -o APT::Status-Fd=1 install -- "+packages+" && "+verify;
    }
}
