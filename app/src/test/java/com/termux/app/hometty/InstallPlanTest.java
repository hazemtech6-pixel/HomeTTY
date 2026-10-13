package com.termux.app.hometty;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class InstallPlanTest {
    private InstallPlan plan() { return new InstallPlan(Arrays.asList(new InstallPlan.Step("required","Required","echo ok",80,false),new InstallPlan.Step("optional","Optional","echo optional",20,true))); }
    @Test public void promptRetainsPtyAndProgressAcrossRestore() {
        InstallPlan a=plan(); a.current().start("pty-1"); a.current().progress(50); a.current().waiting();
        InstallPlan b=plan(); b.restore(a.snapshot()); assertEquals(40,b.overall()); assertEquals("pty-1",b.current().handle()); assertEquals(InstallPlan.Status.WAITING_FOR_USER,b.current().status());
        b.current().interactDone(); b.current().exited(0); assertEquals(80,b.overall()); b.current().skip(); assertEquals(100,b.overall()); assertNull(b.current());
    }
    @Test public void failureOnlyAfterExitAndRetryDoesNotRestartLiveProcess() {
        InstallPlan.Step s=plan().current(); s.start("pty-1"); s.waiting();
        try { s.retry(); fail(); } catch (IllegalStateException expected) {}
        try { s.start("pty-2"); fail(); } catch (IllegalStateException expected) {}
        assertEquals("pty-1",s.handle()); s.exited(1); assertEquals(InstallPlan.Status.FAILED,s.status()); s.retry(); s.start("pty-2"); assertEquals("pty-2",s.handle());
    }
    @Test public void cannotSkipRequiredOrLiveOptionalSteps() {
        InstallPlan a=plan(); try { a.current().skip(); fail(); } catch (IllegalStateException expected) {}
        a.current().start("pty"); a.current().exited(0); a.current().start("optional-pty");
        try { a.current().skip(); fail(); } catch (IllegalStateException expected) {}
    }
    @Test public void progressCannotCompleteRunningCommand() {
        InstallPlan a=plan(); a.current().start("pty"); a.current().progress(100); assertEquals(79,a.overall()); a.current().progress(1); assertEquals(99,a.current().progress());
        a.current().lost(); assertEquals(InstallPlan.Status.FAILED,a.current().status()); assertEquals("",a.current().handle());
    }
    @Test public void changedCommandCannotReuseSuccessfulState() {
        Properties p=plan().snapshot(); p.setProperty("required.command","changed");
        try { plan().restore(p); fail(); } catch (IllegalArgumentException expected) {}
    }
    @Test public void cancelledStepRequiresExplicitRetry() { InstallPlan a=plan(); a.current().start("pty"); a.current().cancel(); assertEquals(InstallPlan.Status.CANCELLED,a.current().status()); a.current().retry(); assertEquals(InstallPlan.Status.PENDING,a.current().status()); }
    @Test public void olderPlanRetainsCompletedWorkWhenOptionalCompanionStepsAreAdded() {
        InstallPlan old=plan(); old.current().start("pty"); old.current().exited(0);
        InstallPlan migrated=new InstallPlan(Arrays.asList(new InstallPlan.Step("required","Required","echo ok",80,false),new InstallPlan.Step("api","API","companion:api",10,true),new InstallPlan.Step("optional","Optional","echo optional",20,true)));
        migrated.restore(old.snapshot()); assertEquals(InstallPlan.Status.SUCCEEDED,migrated.steps.get(0).status()); assertEquals("api",migrated.current().id);
    }
}
