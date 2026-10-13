package com.termux.app.hometty;

import android.content.*;
import android.net.Uri;
import android.widget.Toast;
import com.termux.app.TermuxService;
import com.termux.shared.termux.TermuxConstants;
import static com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE;

final class ShellCommands {
    static void run(Context context, String command) {
        if (!new java.io.File(TermuxConstants.TERMUX_PREFIX_DIR_PATH,"bin/bash").canExecute()) {
            Toast.makeText(context,"Install a HomeTTY-compatible bootstrap first",Toast.LENGTH_LONG).show(); return;
        }
        dispatch(context, TermuxConstants.TERMUX_PREFIX_DIR_PATH+"/bin/bash",new String[]{"-lc",command});
    }
    static void system(Context context,String command) { dispatch(context,"/system/bin/sh",new String[]{"-c",command}); }
    private static void dispatch(Context context,String shell,String[] arguments) {
        Intent intent = new Intent(context, TermuxService.class).setAction(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE)
            .setData(Uri.parse(TERMUX_SERVICE.URI_SCHEME_SERVICE_EXECUTE + ":" + shell))
            .putExtra(TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, "/system/bin/sh".equals(shell))
            .putExtra(TERMUX_SERVICE.EXTRA_ARGUMENTS,arguments)
            .putExtra(TERMUX_SERVICE.EXTRA_WORKDIR,TermuxConstants.TERMUX_HOME_DIR_PATH)
            .putExtra(TERMUX_SERVICE.EXTRA_BACKGROUND,false)
            .putExtra(TERMUX_SERVICE.EXTRA_SESSION_ACTION,"0");
        try { context.startService(intent); } catch (RuntimeException e) { Toast.makeText(context,e.getMessage(),Toast.LENGTH_LONG).show(); }
    }
}
