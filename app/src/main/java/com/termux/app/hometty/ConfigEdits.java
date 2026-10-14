package com.termux.app.hometty;
import org.tomlj.*;
import java.util.*;
import java.util.regex.*;

/** Targeted scalar edits preserve unknown data and comments; ambiguous syntax fails closed. */
public final class ConfigEdits {
    private static final Pattern ASSIGNMENT=Pattern.compile("^(\\s*[^=]+?\\s*=\\s*)(.*)$");
    public static String scalar(String source,String dotted,Object value) {
        if (!dotted.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*")) throw new IllegalArgumentException("Invalid configuration key");
        return scalar(source,Arrays.asList(dotted.split("\\.")),value);
    }
    public static String scalar(String source,List<String> path,Object value) {
        if (path.isEmpty() || path.stream().anyMatch(part -> part.isEmpty() || part.length()>4096)) throw new IllegalArgumentException("Invalid configuration key");
        TomlParseResult before=HomeConfig.parse(source);
        List<String> lines=new ArrayList<>(Arrays.asList(source.split("\\n",-1))); String literal=literal(value); boolean changed=false;
        TomlPosition position=before.inputPositionOf(path);
        if (position!=null) {
            int index=position.line()-1; Matcher assignment=ASSIGNMENT.matcher(lines.get(index));
            if (assignment.matches()) { lines.set(index,assignment.group(1)+literal+comment(assignment.group(2))); changed=true; }
        } else if (path.size()>1) {
            List<String> parent=path.subList(0,path.size()-1); TomlPosition header=before.inputPositionOf(parent);
            if (header!=null && lines.get(header.line()-1).trim().matches("\\[[^\\[\\]]+\\]\\s*(?:#.*)?")) { lines.add(header.line(),literal(path.get(path.size()-1))+" = "+literal); changed=true; }
        }
        String result;
        if (changed) result=String.join("\n",lines);
        else if (path.size()==1) result=literal(path.get(0))+" = "+literal+"\n"+source;
        else result=source.replaceAll("\\s+$","")+"\n\n["+quotedPath(path.subList(0,path.size()-1))+"]\n"+literal(path.get(path.size()-1))+" = "+literal+"\n";
        try {
            TomlParseResult after=HomeConfig.parse(result);
            Object expected=value instanceof Integer?((Integer)value).longValue():value;
            if (!Objects.equals(expected,normalize(after.get(path)))) throw new IllegalArgumentException("Ambiguous scalar syntax");
            Map<String,Object> original=table(before), replacement=table(after); remove(original,path,0); remove(replacement,path,0);
            if (!original.equals(replacement)) throw new IllegalArgumentException("Edit would change other configuration");
            return result;
        } catch (RuntimeException error) { throw new IllegalArgumentException("This field uses complex TOML syntax; use the full editor",error); }
    }
    private static String quotedPath(List<String> path) { List<String> quoted=new ArrayList<>(); for (String part:path) quoted.add(literal(part)); return String.join(".",quoted); }
    public static String literal(Object value) {
        if (value instanceof List) {
            List<?> values=(List<?>)value;
            if (values.size()>4096 || values.stream().anyMatch(item -> !(item instanceof String))) throw new IllegalArgumentException("Expected a bounded string list");
            List<String> literals=new ArrayList<>(); for (Object item:values) literals.add(literal(item));
            return "["+String.join(", ",literals)+"]";
        }
        if (value instanceof Boolean || value instanceof Long || value instanceof Integer) return value.toString();
        if (value instanceof Double && Double.isFinite((Double)value)) return value.toString();
        if (!(value instanceof String)) throw new IllegalArgumentException("Unsupported scalar");
        StringBuilder result=new StringBuilder("\"");
        for (char character:((String)value).toCharArray()) {
            if (character=='\\' || character=='"') result.append('\\').append(character);
            else if (character<32 || character==127) result.append(String.format(Locale.ROOT,"\\u%04x",(int)character));
            else result.append(character);
        }
        return result.append('"').toString();
    }
    private static String comment(String tail) {
        char quote=0; boolean escaped=false;
        for (int index=0;index<tail.length();index++) { char character=tail.charAt(index); if (escaped) escaped=false; else if (quote=='"' && character=='\\') escaped=true; else if (quote!=0 && character==quote) quote=0; else if (quote==0 && (character=='"' || character=='\'')) quote=character; else if (quote==0 && character=='#') return " "+tail.substring(index); }
        return "";
    }
    public static Map<String,Object> snapshot(TomlTable value) { return table(value); }
    private static Map<String,Object> table(TomlTable table) {
        Map<String,Object> result=new TreeMap<>(); for (Map.Entry<String,Object> entry:table.entrySet()) result.put(entry.getKey(),normalize(entry.getValue())); return result;
    }
    private static Object normalize(Object value) {
        if (value instanceof TomlTable) return table((TomlTable)value);
        if (value instanceof TomlArray) { List<Object> result=new ArrayList<>(); TomlArray array=(TomlArray)value; for (int index=0;index<array.size();index++) result.add(normalize(array.get(index))); return result; }
        return value;
    }
    @SuppressWarnings("unchecked") private static void remove(Map<String,Object> table,List<String> path,int index) {
        if (index==path.size()-1) table.remove(path.get(index));
        else if (table.get(path.get(index)) instanceof Map) { Map<String,Object> nested=(Map<String,Object>)table.get(path.get(index)); remove(nested,path,index+1); if (nested.isEmpty()) table.remove(path.get(index)); }
    }
}
