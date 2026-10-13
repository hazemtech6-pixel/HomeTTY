package com.termux.app.hometty;
import com.termux.terminal.TerminalSession;
import java.io.*;
import java.util.WeakHashMap;

/** Session-owned logging continues when the installer activity is closed. */
public final class SetupOutput implements TerminalSession.OutputListener {
    private static final WeakHashMap<TerminalSession,SetupOutput> outputs=new WeakHashMap<>();
    private final File file;
    private final InstallProgress progress=new InstallProgress();
    private OutputStream stream;
    private volatile String error;
    private SetupOutput(String step) { file=new File(HomeConfig.DATA,"setup-"+step+".raw.log"); }
    public static synchronized SetupOutput attach(TerminalSession session,String step) {
        if (!step.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Invalid setup step");
        SetupOutput output=outputs.get(session);
        if (output==null) { output=new SetupOutput(step); outputs.put(session,output); session.addOutputListener(output); }
        return output;
    }
    @Override public synchronized void onOutput(byte[] data,int count) {
        progress.accept(data,count);
        if (error!=null) return;
        try { if (stream==null) stream=new BufferedOutputStream(new FileOutputStream(file,true)); stream.write(data,0,count); stream.flush(); }
        catch (IOException failure) { error=failure.getMessage(); close(); }
    }
    @Override public synchronized void onOutputClosed() { close(); }
    private void close() { if (stream!=null) { try { stream.close(); } catch (IOException failure) { error=failure.getMessage(); } stream=null; } }
    public int progress() { return progress.value(); }
    public String error() { return error; }
}
