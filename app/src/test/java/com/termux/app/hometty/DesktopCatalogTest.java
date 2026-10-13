package com.termux.app.hometty;
import org.junit.Test;
import static org.junit.Assert.*;
public class DesktopCatalogTest {
    @Test public void catalogMatchesAllUpstreamSelections() { assertEquals(14,DesktopCatalog.START.size()); for (String desktop:DesktopCatalog.START.keySet()) { assertTrue(DesktopCatalog.command(desktop,"vnc").contains("-localhost yes")); assertTrue(DesktopCatalog.command(desktop,"x11").contains("dbus-run-session")); } }
    @Test public void companionRouting() { assertEquals("com.termux.x11",DesktopCatalog.x11Package("com.termux")); assertEquals("com.hazemtech.termux.hometty.x11",DesktopCatalog.x11Package("com.hazemtech.termux.hometty")); }
    @Test(expected=IllegalArgumentException.class) public void noShellInjectionViaDesktopName() { DesktopCatalog.command("xfce; rm file","x11"); }
}
