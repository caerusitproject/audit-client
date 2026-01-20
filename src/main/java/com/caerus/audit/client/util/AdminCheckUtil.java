package com.caerus.audit.client.util;

import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.IntByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdminCheckUtil {

    private static final Logger log = LoggerFactory.getLogger(AdminCheckUtil.class);

    public static boolean isLoggedInUserAdmin() {
        WinNT.HANDLEByReference userToken = new WinNT.HANDLEByReference();

        try {
            int sessionId = Kernel32Ext.INSTANCE.WTSGetActiveConsoleSessionId();
            if (sessionId == 0xFFFFFFFF) {
                log.warn("No active console session found.");
                return false;
            }

            boolean gotToken =
                    WtsApiExt.INSTANCE.WTSQueryUserToken(sessionId, userToken);

            if (!gotToken) {
                log.warn("WTSQueryUserToken failed: {}", Kernel32.INSTANCE.GetLastError());
                return false;
            }

            WinNT.TOKEN_ELEVATION elevation = new WinNT.TOKEN_ELEVATION();
            IntByReference returnLength = new IntByReference();

            boolean result =
                    Advapi32.INSTANCE.GetTokenInformation(
                            userToken.getValue(),
                            WinNT.TOKEN_INFORMATION_CLASS.TokenElevation,
                            elevation,
                            elevation.size(),
                            returnLength);

            if (!result) {
                log.warn("GetTokenInformation failed: {}", Kernel32.INSTANCE.GetLastError());
                return false;
            }

            boolean isAdmin = elevation.TokenIsElevated != 0;
            return isAdmin;

        } catch (Exception e) {
            log.error("Error checking logged-in user privileges: {}", e.getMessage(), e);
            return false;

        } finally {
            if (userToken.getValue() != null) {
                Kernel32.INSTANCE.CloseHandle(userToken.getValue());
            }
        }
    }
}
