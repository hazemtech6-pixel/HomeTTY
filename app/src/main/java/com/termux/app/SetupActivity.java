package com.termux.app;

import android.app.*;
import android.content.*;
import android.os.*;
import android.util.AtomicFile;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.termux.app.hometty.*;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.shared.termux.terminal.TermuxTerminalViewClientBase;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;
import java.io.*;
import java.util.*;
import java.util.regex.*;

/** Resumable setup uses service-owned PTYs, keeping progress visible during input. */
public final class SetupActivity extends Activity implements ServiceConnection {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private InstallPlan plan;
    private AtomicFile state;
    private TermuxService service;
    private TerminalView terminal;
    private TextView label;
    private ProgressBar progress;
    private Button next, interact, skip, cancel;
    private boolean bound;
    private String outputError = "";
    private String lastTranscript = "", lastTranscriptStep = "";
    private final Runnable tick = new Runnable() { @Override public void run() { poll(); handler.postDelayed(this,500); } };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        try {
            new HomeConfig();
            String prefix=TermuxConstants.TERMUX_PREFIX_DIR_PATH;
            plan=new InstallPlan(Arrays.asList(
                new InstallPlan.Step("android", "Check Android environment", "id; uname -m; test -d \"$HOME\"",10,false),
                new InstallPlan.Step("api", "Install optional HomeTTY:API", "companion:api",10,true),
                new InstallPlan.Step("widget", "Install optional HomeTTY:Widget", "companion:widget",10,true),
                new InstallPlan.Step("tasker", "Install optional HomeTTY:Tasker", "companion:tasker",10,true),
                new InstallPlan.Step("x11", "Install optional HomeTTY:X11", "companion:x11",10,true),
                new InstallPlan.Step("bootstrap", "Verify HomeTTY bootstrap", "test -x '"+prefix+"/bin/bash' && '"+prefix+"/bin/bash' -c 'printf \"HomeTTY shell ready\\n\"'",60,false),
                new InstallPlan.Step("packages", "Verify package manager", "'"+prefix+"/bin/dpkg' --audit",20,false),
                new InstallPlan.Step("desktop", "Verify optional desktop", "test -x '"+prefix+"/bin/startxfce4'",10,true)));
            state=new AtomicFile(new File(HomeConfig.DATA,"installer.properties"));
            if (state.getBaseFile().exists()) { Properties p=new Properties(); try (InputStream in=state.openRead()) { p.load(in); } plan.restore(p); }
        } catch (Exception e) { new AlertDialog.Builder(this).setMessage("Cannot resume setup: " + e.getMessage()).setPositiveButton("Close",(d,w)->finish()).show(); return; }
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(16,16,16,16); setContentView(root);
        label=new TextView(this); root.addView(label);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.setMax(100); root.addView(progress);
        terminal=new TerminalView(this,null); terminal.setTextSize(24); terminal.setTerminalViewClient(new TermuxTerminalViewClientBase() {
            @Override public void onSingleTapUp(MotionEvent event) { keyboard(); }
        }); root.addView(terminal,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout controls=new LinearLayout(this); controls.setOrientation(LinearLayout.VERTICAL); root.addView(controls);
        LinearLayout top=new LinearLayout(this), bottom=new LinearLayout(this); controls.addView(top); controls.addView(bottom);
        next=button(top,"Continue",this::advance);
        interact=button(top,"Interact",()-> { InstallPlan.Step step=plan.current(); if (step!=null && step.live()) { if (step.command.startsWith("companion:")) { installCompanion(step); } else { step.waiting(); persist(); keyboard(); } } });
        skip=button(bottom,"Skip",()-> { InstallPlan.Step step=plan.current(); if (step!=null) { step.skip(); persist(); refresh(); } });
        cancel=button(bottom,"Cancel",()-> new AlertDialog.Builder(this).setMessage("Cancel this setup command? A running command will be stopped. Completed steps are retained.").setNegativeButton("Keep running",null).setPositiveButton("Cancel command",(d,w)-> { InstallPlan.Step step=plan.current(); if (step!=null) { TerminalSession session=find(step.handle()); if (session!=null && session.isRunning()) session.finishIfRunning(); step.cancel(); persist(); refresh(); } }).show());
        startService(new Intent(this,TermuxService.class)); bound=bindService(new Intent(this,TermuxService.class),this,0); refresh();
    }
    private Button button(LinearLayout row,String title,Runnable action) { Button b=new Button(this); b.setText(title); row.addView(b,new LinearLayout.LayoutParams(0,-2,1)); b.setOnClickListener(v->action.run()); return b; }
    private void keyboard() { terminal.requestFocus(); ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(terminal,InputMethodManager.SHOW_IMPLICIT); }
    @Override public void onServiceConnected(ComponentName name,IBinder binder) { service=((TermuxService.LocalBinder)binder).service; tick.run(); }
    @Override public void onServiceDisconnected(ComponentName name) { service=null; }
    @Override protected void onResume() { super.onResume(); ActivityLock.resume(this,"security.lock_terminal"); }
    @Override protected void onStop() { ActivityLock.stop(this); super.onStop(); }
    @Override protected void onActivityResult(int request,int result,Intent data) { super.onActivityResult(request,result,data); ActivityLock.result(this,request,result); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); if (bound) unbindService(this); super.onDestroy(); }
    private TerminalSession find(String handle) { if (service==null) return null; for (TermuxSession session:service.getTermuxSessions()) if (session.getTerminalSession().mHandle.equals(handle)) return session.getTerminalSession(); return null; }
    private void advance() {
        InstallPlan.Step step=plan.current(); if (step==null || service==null) return;
        if (step.command.startsWith("companion:")) {
            if (step.status()==InstallPlan.Status.FAILED || step.status()==InstallPlan.Status.CANCELLED) step.retry();
            installCompanion(step); return;
        }
        if (step.live()) { step.interactDone(); persist(); refresh(); return; }
        if (step.status()==InstallPlan.Status.FAILED || step.status()==InstallPlan.Status.CANCELLED) step.retry();
        if ("bootstrap".equals(step.id) && !new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH,"bin/bash").canExecute()) {
            if (!com.termux.BuildConfig.HOMETTY_HAS_BOOTSTRAP) { new AlertDialog.Builder(this).setMessage("This APK requires a custom-prefix bootstrap build. Setup is saved; retry after installing a HomeTTY APK with rebuilt packages. Stock Termux packages cannot be used.").setPositiveButton("OK",null).show(); return; }
            TermuxInstaller.setupBootstrapIfNeeded(this,this::advance); return;
        }
        TermuxSession session=service.createTermuxSession("/system/bin/sh",new String[]{"-c",step.command},null,HomeConfig.HOME.getAbsolutePath(),true,"HomeTTY setup: " + step.id);
        if (session==null) { Toast.makeText(this,"Could not create PTY",Toast.LENGTH_LONG).show(); return; }
        com.termux.app.hometty.SetupOutput.attach(session.getTerminalSession(),step.id);
        step.start(session.getTerminalSession().mHandle); persist(); terminal.attachSession(session.getTerminalSession()); refresh();
    }
    private void installCompanion(InstallPlan.Step step) {
        String name=step.command.substring("companion:".length());
        try {
            if (!step.live()) step.start("package:"+name);
            if (CompanionInstaller.installed(this,name)) { step.exited(0); persist(); refresh(); return; }
            step.waiting(); persist(); CompanionInstaller.request(this,name); refresh();
        } catch (Exception e) { if (step.live()) step.exited(1); persist(); new AlertDialog.Builder(this).setMessage("Companion installation could not start: "+e.getMessage()).setPositiveButton("OK",null).show(); refresh(); }
    }
    private void poll() {
        if (service==null || plan==null) return;
        InstallPlan.Step step=plan.current();
        if (step!=null && step.live() && step.command.startsWith("companion:")) {
            if (CompanionInstaller.installed(this,step.command.substring("companion:".length()))) { step.exited(0); persist(); }
            refresh(); return;
        }
        if (step!=null && step.live()) {
            TerminalSession session=find(step.handle());
            if (session==null) { step.lost(); persist(); }
            else {
                com.termux.app.hometty.SetupOutput output=com.termux.app.hometty.SetupOutput.attach(session,step.id);
                outputError=output.error()==null?"":"\nCannot save raw output: "+output.error();
                if (output.progress()>step.progress()) { step.progress(output.progress()); persist(); }
                terminal.attachSession(session);
                if (session.getEmulator()!=null) {
                    String transcript=session.getEmulator().getScreen().getTranscriptText();
                    Matcher matcher=Pattern.compile("(?m)^HOMETTY_PROGRESS=([0-9]{1,3})\\s*$").matcher(transcript);
                    while (matcher.find()) { int value=Integer.parseInt(matcher.group(1)); if (value<=100 && value>step.progress()) { step.progress(value); persist(); } }
                    if (!step.id.equals(lastTranscriptStep) || !transcript.equals(lastTranscript)) {
                    terminal.onScreenUpdated();
                    try { AtomicFile log=new AtomicFile(new File(HomeConfig.DATA,"setup-"+step.id+".log")); FileOutputStream out=log.startWrite(); try { out.write(transcript.getBytes(java.nio.charset.StandardCharsets.UTF_8)); log.finishWrite(out); lastTranscript=transcript; lastTranscriptStep=step.id; } catch (IOException e) { log.failWrite(out); throw e; } } catch (IOException e) { label.setText("Cannot save command output: " + e.getMessage()); }
                    }
                }
                if (!session.isRunning()) { step.exited(session.getExitStatus()); persist(); }
            }
        }
        refresh();
    }
    private void refresh() {
        if (plan==null || label==null) return;
        InstallPlan.Step step=plan.current(); progress.setProgress(plan.overall());
        String status="Setup " + plan.overall()+"%\n"+(step==null ? "Verification complete" : step.title+" · "+step.status()+"\n"+step.message())+outputError;
        if (!status.contentEquals(label.getText())) label.setText(status);
        next.setEnabled(step!=null && service!=null);
        String nextTitle=step!=null && step.live() ? (step.command.startsWith("companion:") ? "Install" : "Done input") : step!=null && (step.status()==InstallPlan.Status.FAILED || step.status()==InstallPlan.Status.CANCELLED) ? "Retry" : "Continue";
        if (!nextTitle.contentEquals(next.getText())) next.setText(nextTitle);
        interact.setEnabled(step!=null && step.live()); skip.setEnabled(step!=null && step.optional && !step.live()); cancel.setEnabled(step!=null);
    }
    private void persist() {
        FileOutputStream out=null;
        try { out=state.startWrite(); plan.snapshot().store(out,"HomeTTY resumable setup"); state.finishWrite(out); }
        catch (IOException e) { if (out!=null) state.failWrite(out); new AlertDialog.Builder(this).setMessage("Setup state could not be saved: "+e.getMessage()+". Any live PTY is still running.").setPositiveButton("OK",null).show(); }
    }
}
