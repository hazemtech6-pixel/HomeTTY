package com.termux.app.hometty;

import java.util.*;

/** Freedesktop application entry. Exec is argv, never executable shell syntax. */
public final class DesktopEntry {
    public final String name, icon, command;
    private DesktopEntry(String name, String icon, String command) { this.name = name; this.icon = icon; this.command = command; }
    public static DesktopEntry parse(String source, String filename) {
        Map<String,String> values = new HashMap<>(); boolean section = false;
        for (String line : source.split("\\r?\\n")) {
            line = line.trim(); if (line.startsWith("[")) { section = "[Desktop Entry]".equals(line); continue; }
            if (!section || line.isEmpty() || line.startsWith("#")) continue;
            int equals = line.indexOf('='); if (equals > 0) values.put(line.substring(0, equals).trim(), line.substring(equals + 1));
        }
        if (!"Application".equals(values.get("Type")) || "true".equals(values.get("Hidden")) || "true".equals(values.get("NoDisplay"))) return null;
        String name = values.get("Name"), executable = values.get("Exec"), icon = values.getOrDefault("Icon", "");
        if (name == null || name.isEmpty() || executable == null || executable.isEmpty()) return null;
        List<String> argv = tokenize(executable), expanded = new ArrayList<>();
        for (String word : argv) {
            if (Arrays.asList("%f", "%F", "%u", "%U").contains(word)) continue;
            if ("%i".equals(word)) { if (!icon.isEmpty()) { expanded.add("--icon"); expanded.add(icon); } continue; }
            if ("%c".equals(word)) { expanded.add(name); continue; }
            if ("%k".equals(word)) { expanded.add(filename); continue; }
            StringBuilder literal = new StringBuilder();
            for (int index = 0; index < word.length(); index++) {
                char character = word.charAt(index);
                if (character == '%') { if (++index >= word.length() || word.charAt(index) != '%') throw new IllegalArgumentException("Unsupported desktop Exec field code"); }
                literal.append(character);
            }
            expanded.add(literal.toString());
        }
        if (expanded.isEmpty() || expanded.get(0).isEmpty()) return null;
        StringBuilder command = new StringBuilder("export DISPLAY=${DISPLAY:-:1}; exec");
        for (String word : expanded) command.append(' ').append(quote(word));
        return new DesktopEntry(name, icon, command.toString());
    }
    static List<String> tokenize(String source) {
        List<String> result = new ArrayList<>(); StringBuilder word = new StringBuilder(); boolean quoted = false, started = false;
        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '\\') { if (++index >= source.length()) throw new IllegalArgumentException("Incomplete desktop escape"); word.append(source.charAt(index)); started = true; }
            else if (character == '"') { quoted = !quoted; started = true; }
            else if (Character.isWhitespace(character) && !quoted) { if (started) { result.add(word.toString()); word.setLength(0); started = false; } }
            else { word.append(character); started = true; }
        }
        if (quoted) throw new IllegalArgumentException("Unclosed desktop Exec quote");
        if (started) result.add(word.toString()); return result;
    }
    public static String quote(String argument) { if (argument.indexOf('\0') >= 0) throw new IllegalArgumentException("NUL argument"); return "'" + argument.replace("'", "'\\''") + "'"; }
}
