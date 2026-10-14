package com.termux.app.hometty;
import org.junit.Test;
import static org.junit.Assert.*;
public class InstallRecipesTest {
    private static final String PREFIX="/data/data/com.hazemtech.termux.hometty/files/usr";
    @Test public void allDesktopsHaveFixedRecipesAndSelectedBackend() {
        assertEquals(DesktopCatalog.START.keySet(),InstallRecipes.DESKTOP_PACKAGES.keySet());
        for (String desktop:DesktopCatalog.START.keySet()) {
            String x11=InstallRecipes.command("desktop",PREFIX,desktop,"x11");
            assertTrue(x11.contains("hometty-x11")); assertTrue(x11.contains("APT::Status-Fd=1"));
            assertTrue(x11.contains(DesktopCatalog.START.get(desktop))); assertFalse(x11.contains(" -y"));
            String vnc=InstallRecipes.command("desktop",PREFIX,desktop,"vnc");
            assertTrue(vnc.contains("tigervnc")); assertFalse(vnc.contains("hometty-x11"));
        }
    }
    @Test public void inputCannotBecomeShellSyntax() {
        for (String[] input:new String[][]{{"desktop",PREFIX,"xfce;id","x11"},{"tools",PREFIX+";id","xfce","x11"},{"desktop",PREFIX,"xfce","x11;id"}})
            try { InstallRecipes.command(input[0],input[1],input[2],input[3]); fail(); } catch (IllegalArgumentException expected) {}
    }
}
