package com.termux.app;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ServiceTestRule;
import com.termux.app.hometty.HomeConfig;
import com.termux.app.hometty.InstallPlan;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Exercises the real Android PTY, not a mocked shell or host subprocess. */
@RunWith(AndroidJUnit4.class)
public class PtyInstallerTest {
    @Rule public final ServiceTestRule serviceRule = new ServiceTestRule();

    @Test public void interactivePromptKeepsSamePtyAcrossInstallerStateRestore() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        new HomeConfig();
        Intent serviceIntent = new Intent(context, TermuxService.class);
        serviceRule.startService(serviceIntent);
        IBinder binder = serviceRule.bindService(serviceIntent);
        TermuxService service = ((TermuxService.LocalBinder) binder).service;
        AtomicReference<TerminalSession> reference = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            TermuxSession created = service.createTermuxSession("/system/bin/sh",
                new String[]{"-c", "printf 'Ready? '; read answer; printf 'answer=%s\\n' \"$answer\""},
                null, HomeConfig.HOME.getAbsolutePath(), true, "HomeTTY instrumentation prompt");
            assertNotNull(created);
            TerminalSession session = created.getTerminalSession();
            session.updateSize(80, 24, 8, 16);
            reference.set(session);
        });
        TerminalSession session = reference.get();
        try {
            long deadline = android.os.SystemClock.elapsedRealtime() + 10000;
            while (android.os.SystemClock.elapsedRealtime() < deadline && !transcript(session).contains("Ready?")) Thread.sleep(50);
            assertTrue("Real shell prompt did not appear", transcript(session).contains("Ready?"));
            assertTrue(session.isRunning());
            InstallPlan plan = plan(); plan.current().start(session.mHandle); plan.current().waiting();
            InstallPlan restored = plan(); restored.restore(plan.snapshot());
            assertEquals(InstallPlan.Status.WAITING_FOR_USER, restored.current().status());
            assertEquals(session.mHandle, restored.current().handle());
            assertTrue("Installer prompt must not stop the PTY", session.isRunning());
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> session.write("yes\n"));
            deadline = android.os.SystemClock.elapsedRealtime() + 10000;
            while (android.os.SystemClock.elapsedRealtime() < deadline && session.isRunning()) Thread.sleep(50);
            assertFalse("Shell did not complete after input", session.isRunning());
            assertEquals(0, session.getExitStatus());
            assertTrue(transcript(session).contains("answer=yes"));
            restored.current().exited(session.getExitStatus());
            assertEquals(100, restored.overall());
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> service.removeTermuxSession(session));
        }
    }
    private static InstallPlan plan() {
        return new InstallPlan(Collections.singletonList(new InstallPlan.Step("prompt", "Prompt", "read answer", 100, false)));
    }
    private static String transcript(TerminalSession session) {
        AtomicReference<String> result = new AtomicReference<>("");
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            if (session.getEmulator() != null) result.set(session.getEmulator().getScreen().getTranscriptText());
        });
        return result.get();
    }
}
