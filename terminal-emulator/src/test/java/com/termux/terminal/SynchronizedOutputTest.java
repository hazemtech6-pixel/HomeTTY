package com.termux.terminal;
public class SynchronizedOutputTest extends TerminalTestCase {
    public void testModeQueriesAndScreenParsing() {
        withTerminalSized(12,3);
        assertEnteringStringGivesResponse("\033[?2026$p","\033[?2026;2$y");
        enterString("\033[?2026hframe"); assertTrue(mTerminal.isSynchronizedOutput());
        assertEnteringStringGivesResponse("\033[?2026$p","\033[?2026;1$y");
        assertTrue(mTerminal.getScreen().getTranscriptText().contains("frame"));
        enterString("\033[?2026l"); assertFalse(mTerminal.isSynchronizedOutput());
    }
    public void testResetAndProducerTimeoutReleaseTheFrame() throws Exception {
        withTerminalSized(12,3); enterString("\033[?2026h\033c"); assertFalse(mTerminal.isSynchronizedOutput());
        enterString("\033[?2026h"); Thread.sleep(1100); assertFalse(mTerminal.isSynchronizedOutput());
        assertEnteringStringGivesResponse("\033[?2026$p","\033[?2026;2$y");
    }
}
