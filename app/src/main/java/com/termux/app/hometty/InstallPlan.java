package com.termux.app.hometty;

import java.util.*;

/** Pure installer state machine. A prompt is a live process state, not an error. */
public final class InstallPlan {
    public enum Status { PENDING, RUNNING, WAITING_FOR_USER, FAILED, SUCCEEDED, SKIPPED, CANCELLED }
    public static final class Step {
        public final String id, title, command;
        public final int weight;
        public final boolean optional;
        private Status status = Status.PENDING;
        private int progress;
        private String handle = "", message = "";
        public Step(String id, String title, String command, int weight, boolean optional) {
            if (!id.matches("[a-z][a-z0-9_-]*") || weight <= 0) throw new IllegalArgumentException("Invalid installation step");
            this.id=id; this.title=title; this.command=command; this.weight=weight; this.optional=optional;
        }
        public Status status() { return status; }
        public int progress() { return progress; }
        public String handle() { return handle; }
        public String message() { return message; }
        public boolean live() { return status == Status.RUNNING || status == Status.WAITING_FOR_USER; }
        public boolean complete() { return status == Status.SUCCEEDED || status == Status.SKIPPED; }
        public void start(String handle) {
            if (status != Status.PENDING && status != Status.FAILED) throw new IllegalStateException("Cannot restart a live or completed step");
            if (handle == null || handle.isEmpty()) throw new IllegalArgumentException("Missing PTY identity");
            status=Status.RUNNING; this.handle=handle; progress=0; message="";
        }
        public void waiting() { if (!live()) throw new IllegalStateException("No live process"); status=Status.WAITING_FOR_USER; }
        public void interactDone() { if (!live()) throw new IllegalStateException("No live process"); status=Status.RUNNING; }
        public void progress(int percent) { if (!live() || percent < 0 || percent > 100) throw new IllegalArgumentException("Invalid progress"); progress=Math.max(progress,Math.min(percent,99)); }
        public void exited(int exitCode) { if (!live()) throw new IllegalStateException("No live process"); status=exitCode==0 ? Status.SUCCEEDED : Status.FAILED; progress=exitCode==0 ? 100 : progress; message="Exit status " + exitCode; handle=""; }
        public void lost() { if (!live()) return; status=Status.FAILED; handle=""; message="PTY no longer exists. Review output before retrying; the command was not restarted."; }
        public void skip() { if (!optional || live() || complete()) throw new IllegalStateException("This step cannot be safely skipped"); status=Status.SKIPPED; progress=100; }
        public void cancel() { if (complete()) throw new IllegalStateException("Already complete"); status=Status.CANCELLED; handle=""; }
        public void retry() { if (status!=Status.FAILED && status!=Status.CANCELLED) throw new IllegalStateException("Retry requires an ended process"); status=Status.PENDING; progress=0; message=""; }
    }
    public final List<Step> steps;
    public InstallPlan(List<Step> steps) {
        Set<String> ids = new HashSet<>(); for (Step step : steps) if (!ids.add(step.id)) throw new IllegalArgumentException("Duplicate installation step");
        this.steps=Collections.unmodifiableList(new ArrayList<>(steps));
    }
    public Step current() { for (Step step : steps) if (!step.complete()) return step; return null; }
    public int overall() { long total=0, earned=0; for (Step step : steps) { total+=step.weight; earned+=(long)step.weight*step.progress; } return total==0 ? 100 : (int)(earned/total); }
    public Properties snapshot() {
        Properties p=new Properties(); p.setProperty("version","1");
        for (Step step : steps) { String prefix=step.id+"."; p.setProperty(prefix+"status",step.status.name()); p.setProperty(prefix+"progress",Integer.toString(step.progress)); p.setProperty(prefix+"handle",step.handle); p.setProperty(prefix+"message",step.message); p.setProperty(prefix+"command",step.command); }
        return p;
    }
    public void restore(Properties p) {
        if (!"1".equals(p.getProperty("version"))) throw new IllegalArgumentException("Unsupported installer state version");
        for (Step step : steps) {
            String prefix=step.id+".";
            if (!step.command.equals(p.getProperty(prefix+"command"))) throw new IllegalArgumentException("Installer plan changed; review required");
            step.status=Status.valueOf(p.getProperty(prefix+"status")); step.progress=Integer.parseInt(p.getProperty(prefix+"progress"));
            if (step.progress<0 || step.progress>100) throw new IllegalArgumentException("Corrupt progress");
            if (step.status==Status.SKIPPED && !step.optional) throw new IllegalArgumentException("Required step cannot be skipped");
            step.handle=p.getProperty(prefix+"handle",""); step.message=p.getProperty(prefix+"message","");
            if (step.live() && step.handle.isEmpty()) throw new IllegalArgumentException("Missing saved PTY identity");
        }
    }
}
