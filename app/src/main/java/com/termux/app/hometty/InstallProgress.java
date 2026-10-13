package com.termux.app.hometty;
import java.util.regex.*;
/** Parses actual apt status percentages and explicit helper progress, across reads. */
public final class InstallProgress {
    private static final Pattern STATUS=Pattern.compile("^(?:HOMETTY_PROGRESS=([0-9]{1,3})|(?:dlstatus|pmstatus):[^:]*:([0-9]{1,3}(?:\\.[0-9]+)?):.*|Progress: \\[\\s*([0-9]{1,3})%\\s*\\]).*$");
    private final StringBuilder line=new StringBuilder();
    private boolean oversized;
    private volatile int progress;
    public int value() { return progress; }
    public void accept(byte[] bytes,int count) {
        for (int index=0;index<count;index++) {
            char character=(char)(bytes[index]&255);
            if (character=='\r' || character=='\n') {
                if (!oversized) {
                    Matcher match=STATUS.matcher(line.toString().replaceAll("\\u001B\\[[0-9;]*[A-Za-z]", ""));
                    if (match.matches()) for (int group=1;group<=3;group++) if (match.group(group)!=null) {
                        double value=Double.parseDouble(match.group(group)); if (value<=100) progress=Math.max(progress,Math.min(99,(int)value));
                    }
                }
                line.setLength(0); oversized=false;
            } else if (line.length()<4096 && !oversized) line.append(character);
            else { line.setLength(0); oversized=true; }
        }
    }
}
