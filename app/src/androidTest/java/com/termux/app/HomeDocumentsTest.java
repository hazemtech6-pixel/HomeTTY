package com.termux.app;
import android.content.*;
import android.net.Uri;
import android.provider.DocumentsContract;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.termux.app.hometty.HomeConfig;
import java.io.*;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class HomeDocumentsTest {
    @Test public void safCreateRenameReadAndPathContainment() throws Exception {
        new HomeConfig(); Context context=InstrumentationRegistry.getInstrumentation().getTargetContext(); ContentResolver resolver=context.getContentResolver();
        Uri home=DocumentsContract.buildDocumentUri(context.getPackageName()+".documents",HomeConfig.HOME.getAbsolutePath());
        String name="instrumentation-documents-"+UUID.randomUUID();
        Uri folder=DocumentsContract.createDocument(resolver,home,DocumentsContract.Document.MIME_TYPE_DIR,name); assertNotNull(folder);
        Uri document=null;
        try {
            document=DocumentsContract.createDocument(resolver,folder,"text/plain","notes.txt"); assertNotNull(document);
            try (OutputStream output=resolver.openOutputStream(document,"wt")) { assertNotNull(output); output.write("SAF works".getBytes("UTF-8")); }
            document=DocumentsContract.renameDocument(resolver,document,"renamed.txt"); assertNotNull(document);
            try (InputStream input=resolver.openInputStream(document)) { assertNotNull(input); byte[] buffer=new byte[32]; assertEquals("SAF works",new String(buffer,0,input.read(buffer),"UTF-8")); }
            assertTrue(DocumentsContract.isChildDocument(resolver,home,document));
            Uri sibling=DocumentsContract.buildDocumentUri(context.getPackageName()+".documents",HomeConfig.HOME.getAbsolutePath()+"-outside");
            assertFalse(DocumentsContract.isChildDocument(resolver,home,sibling));
            String invalid="../escape-"+UUID.randomUUID();
            try { assertNull(DocumentsContract.createDocument(resolver,folder,"text/plain",invalid)); } catch (IOException | RuntimeException rejected) {}
            assertFalse(new File(new File(HomeConfig.HOME,name),invalid).exists());
        } finally { if (document!=null) DocumentsContract.deleteDocument(resolver,document); DocumentsContract.deleteDocument(resolver,folder); }
    }
}
