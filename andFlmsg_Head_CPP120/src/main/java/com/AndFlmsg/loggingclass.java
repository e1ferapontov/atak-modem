package com.AndFlmsg;

import android.util.Log;

public class loggingclass {
    private static String Application = "AndFlmsg";

    public loggingclass(String app) {
        Application = app;
    }

    public static void writelog(String msg, Exception e) {
        if (e == null) {
            Log.d(Application, msg);
        } else {
            Log.e(Application, msg, e);
        }

        ModemService.TermWindow += (msg + "\n");
        AndFlmsg.mHandler.post(AndFlmsg.addtoterminal);

    }

}
