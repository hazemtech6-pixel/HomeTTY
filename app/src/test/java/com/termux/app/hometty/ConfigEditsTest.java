package com.termux.app.hometty;
import org.junit.Test;
import static org.junit.Assert.*;
public class ConfigEditsTest {
    @Test public void scalarEditsPreserveCommentsUnknownTablesAndQuotedKeys() {
        String original="# keep\n[\"home\"]\n\"columns\" = 4 # layout\n[unknown]\nvalues = [1,2,3]\n";
        String result=ConfigEdits.scalar(original,"home.columns",6);
        assertTrue(result.contains("\"columns\" = 6 # layout")); assertTrue(result.contains("values = [1,2,3]"));
        result=ConfigEdits.scalar(result,"theme.plugin","night"); assertEquals("night",HomeConfig.parse(result).getString("theme.plugin"));
    }
    @Test public void multilineAndInlineStructuresCannotBeCorrupted() {
        String original="[unknown]\ntext = '''\n[theme]\nplugin = fake\n'''\n";
        String result=ConfigEdits.scalar(original,"theme.plugin","night"); assertEquals(HomeConfig.parse(original).getString("unknown.text"),HomeConfig.parse(result).getString("unknown.text"));
        try { ConfigEdits.scalar("theme={plugin='old',custom=1}\n","theme.plugin","night"); fail(); } catch (IllegalArgumentException expected) {}
    }
}
