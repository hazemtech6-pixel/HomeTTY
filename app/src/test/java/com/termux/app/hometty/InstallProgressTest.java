package com.termux.app.hometty;
import org.junit.Test;
import static org.junit.Assert.*;
public class InstallProgressTest {
    @Test public void aptStatusIsParsedAcrossReadsAndCompletionWaitsForExit() throws Exception {
        InstallProgress progress=new InstallProgress(); byte[] first="pmstatus:python:4".getBytes("UTF-8"), second="2.8:Unpacking python\r\nHOMETTY_PROGRESS=100\n".getBytes("UTF-8");
        progress.accept(first,first.length); assertEquals(0,progress.value()); progress.accept(second,second.length); assertEquals(99,progress.value());
    }
    @Test public void invalidAndRegressingOutputDoesNotInventProgress() throws Exception {
        InstallProgress progress=new InstallProgress(); byte[] data="dlstatus:1:58.2:Downloading\nHOMETTY_PROGRESS=101\nHOMETTY_PROGRESS=30\nnot progress 90%\n".getBytes("UTF-8");
        progress.accept(data,data.length); assertEquals(58,progress.value());
    }
}
