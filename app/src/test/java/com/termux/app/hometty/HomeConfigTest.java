package com.termux.app.hometty;
import org.junit.Test;
import static org.junit.Assert.*;
public class HomeConfigTest {
    @Test public void defaultsDisableAi() { assertEquals(Boolean.FALSE,HomeConfig.parse(HomeConfig.DEFAULT).getBoolean("ai.enabled")); }
    @Test public void fullTomlSyntaxIsParsed() { assertEquals("value # literal",HomeConfig.parse("[plugin]\nname = 'value # literal'\n").getString("plugin.name")); }
    @Test public void malformedAndMistypedConfigIsRejected() {
        for (String source : new String[]{"[ai]\nenabled = 'false'", "[home]\ncolumns = 99", "[ai]\nenabled = true\nenabled = false", "[ai]\napi_key = 'secret'", "[weather]\nlatitude = nan", "[weather]\nlongitude = 181", "[desktop]\nbackend = 'bad'", "[desktop]\nenvironment = 'bad'"}) {
            try { HomeConfig.parse(source); fail(source); } catch (IllegalArgumentException expected) {}
        }
    }
}
