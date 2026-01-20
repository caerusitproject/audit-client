package com.caerus.audit.client.util;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.W32APIOptions;

public interface WtsApiExt extends Library {

    WtsApiExt INSTANCE =
            Native.load("Wtsapi32", WtsApiExt.class, W32APIOptions.DEFAULT_OPTIONS);

    boolean WTSQueryUserToken(int sessionId, WinNT.HANDLEByReference phToken);
}
