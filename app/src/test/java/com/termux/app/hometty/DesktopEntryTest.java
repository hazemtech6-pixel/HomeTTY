package com.termux.app.hometty;
import org.junit.Test;
import static org.junit.Assert.*;
public class DesktopEntryTest {
    @Test public void fieldExpansionAndShellMetacharactersAreLiteralArguments() {
        DesktopEntry entry = DesktopEntry.parse("[Desktop Entry]\nType=Application\nName=A browser\nIcon=browser\nExec=browser --name %c %i %U \"$(touch /tmp/example)\"\n", "/home/app.desktop");
        assertEquals("export DISPLAY=${DISPLAY:-:1}; exec 'browser' '--name' 'A browser' '--icon' 'browser' '$(touch /tmp/example)'", entry.command);
    }
    @Test public void hiddenEntriesAndMalformedExecAreNotLaunched() {
        assertNull(DesktopEntry.parse("[Desktop Entry]\nType=Application\nName=Hidden\nExec=hidden\nNoDisplay=true", "x"));
        for (String command : new String[]{"bad %z", "bad \"unfinished", "bad %Fembedded"}) {
            try { DesktopEntry.parse("[Desktop Entry]\nType=Application\nName=Bad\nExec=" + command, "x"); fail(command); } catch (IllegalArgumentException expected) {}
        }
    }
    @Test public void apostrophesAreQuoted() { assertEquals("'it'\\''s'", DesktopEntry.quote("it's")); }
}
