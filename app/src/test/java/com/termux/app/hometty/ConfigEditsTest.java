package com.termux.app.hometty;
import org.junit.Test;
import static org.junit.Assert.*;
public class ConfigEditsTest {
    @Test public void pinListsRoundTripWithoutChangingOtherConfiguration() {
        java.util.List<String> pins=java.util.Arrays.asList("com.example/com.example.Main","file:a#b");
        String source="[home]\ncolumns=4 # preserve\npins=['old'] # chosen apps\n[unknown]\nvalue=8\n";
        String result=ConfigEdits.scalar(source,"home.pins",pins);
        assertTrue(result.contains("# chosen apps")); assertTrue(result.contains("columns=4 # preserve"));
        assertEquals(pins,HomeConfig.parse(result).getArray("home.pins").toList());
        assertEquals(8,HomeConfig.parse(result).getLong("unknown.value").longValue());
        try { HomeConfig.parse("[home]\npins=[1]\n"); fail(); } catch (IllegalArgumentException expected) {}
    }
    @Test public void scalarEditsPreserveCommentsUnknownTablesAndQuotedKeys() {
        String original="# keep\n[\"home\"]\n\"columns\" = 4 # layout\n[unknown]\nvalues = [1,2,3]\n";
        String result=ConfigEdits.scalar(original,"home.columns",6);
        assertTrue(result.contains("\"columns\" = 6 # layout")); assertTrue(result.contains("values = [1,2,3]"));
        result=ConfigEdits.scalar(result,"theme.plugin","night"); assertEquals("night",HomeConfig.parse(result).getString("theme.plugin"));
    }
    @Test public void literalComponentPathsRemainDistinctFromDottedKeys() {
        String component="com.example/com.example.Main";
        String result=ConfigEdits.scalar(HomeConfig.DEFAULT,java.util.Arrays.asList("app_overrides",component,"label"),"Custom label");
        assertEquals("Custom label",HomeConfig.parse(result).getString(java.util.Arrays.asList("app_overrides",component,"label")));
        result=ConfigEdits.scalar(result,java.util.Arrays.asList("app_overrides",component,"icon"),"~/icon.png");
        assertEquals("Custom label",HomeConfig.parse(result).getString(java.util.Arrays.asList("app_overrides",component,"label")));
    }
    @Test public void multilineAndInlineStructuresCannotBeCorrupted() {
        String original="[unknown]\ntext = '''\n[theme]\nplugin = fake\n'''\n";
        String result=ConfigEdits.scalar(original,"theme.plugin","night"); assertEquals(HomeConfig.parse(original).getString("unknown.text"),HomeConfig.parse(result).getString("unknown.text"));
        try { ConfigEdits.scalar("theme={plugin='old',custom=1}\n","theme.plugin","night"); fail(); } catch (IllegalArgumentException expected) {}
    }
}
