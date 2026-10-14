package com.termux.app.hometty;
import java.util.*;

/** Stable launcher slots, including empty positions and temporarily missing apps. */
public final class HomeLayout {
    public final List<String> slots;
    public final int capacity;
    public HomeLayout(List<String> slots,int columns,int rows) {
        if (columns<2 || columns>8 || rows<1 || rows>8 || slots.size()>4096) throw new IllegalArgumentException("Invalid Home layout size");
        Set<String> unique=new HashSet<>();
        for (String id:slots) if (id==null || id.length()>4096 || id.indexOf('\0')>=0 || (!id.isEmpty() && !unique.add(id))) throw new IllegalArgumentException("Home layout has an invalid or duplicate app");
        this.slots=Collections.unmodifiableList(new ArrayList<>(slots)); capacity=columns*rows;
    }
    public int pages() { return Math.max(1,(slots.size()+capacity-1)/capacity); }
    public List<String> place(String id,int to) {
        if (id==null || id.isEmpty() || id.length()>4096 || id.indexOf('\0')>=0 || to<0 || to>=4096) throw new IllegalArgumentException("Invalid Home app or position");
        int from=slots.indexOf(id); if (from>=0) return swap(from,to);
        List<String> added=new ArrayList<>(slots); while (added.size()<=to) added.add("");
        if (!added.get(to).isEmpty()) throw new IllegalArgumentException("Home position is occupied");
        added.set(to,id); return added;
    }
    public List<String> swap(int from,int to) {
        if (from<0 || from>=slots.size() || to<0 || to>=4096) throw new IllegalArgumentException("Invalid Home position");
        List<String> moved=new ArrayList<>(slots); while (moved.size()<=to) moved.add("");
        Collections.swap(moved,from,to); return moved;
    }
}
