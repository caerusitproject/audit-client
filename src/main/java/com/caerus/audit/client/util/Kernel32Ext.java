package com.caerus.audit.client.util;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.win32.W32APIOptions;

public interface Kernel32Ext extends Library {

    Kernel32Ext INSTANCE =
            Native.load("kernel32", Kernel32Ext.class, W32APIOptions.DEFAULT_OPTIONS);

    int WTSGetActiveConsoleSessionId();
}