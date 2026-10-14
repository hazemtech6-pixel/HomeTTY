package com.termux.app.hometty;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class HomeLayoutTest {
    @Test public void pagesAndMovesPreserveEmptyAndMissingAppPositions() {
        HomeLayout layout=new HomeLayout(Arrays.asList("app:a","","missing:b","app:c"),2,1);
        assertEquals(2,layout.pages()); assertEquals(Arrays.asList("app:c","","missing:b","app:a"),layout.swap(0,3));
        assertEquals(Arrays.asList("","","missing:b","app:c","","app:a"),layout.swap(0,5));
        assertEquals("app:a",layout.slots.get(0)); assertEquals(1,new HomeLayout(Collections.emptyList(),4,3).pages());
        assertEquals(Arrays.asList("app:a","new","missing:b","app:c"),layout.place("new",1));
        try { layout.place("new",0); fail(); } catch (IllegalArgumentException expected) {}
    }
    @Test public void duplicateAppsAndOutOfRangePositionsCannotCorruptLayout() {
        try { new HomeLayout(Arrays.asList("a","a"),4,3); fail(); } catch (IllegalArgumentException expected) {}
        HomeLayout layout=new HomeLayout(Arrays.asList("a","b"),4,3);
        try { layout.swap(0,4096); fail(); } catch (IllegalArgumentException expected) {}
        try { layout.swap(2,0); fail(); } catch (IllegalArgumentException expected) {}
    }
}
