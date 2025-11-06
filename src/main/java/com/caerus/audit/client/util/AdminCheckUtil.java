package com.caerus.audit.client.util;

import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.IntByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdminCheckUtil {
    private static final Logger log = LoggerFactory.getLogger(AdminCheckUtil.class);

    public static boolean isCurrentUserAdmin() {
        WinNT.HANDLEByReference hTokenRef = new WinNT.HANDLEByReference();
        try {
            boolean success = Advapi32.INSTANCE.OpenProcessToken(
                    Kernel32.INSTANCE.GetCurrentProcess(),
                    WinNT.TOKEN_QUERY,
                    hTokenRef
            );

            if (!success) {
                log.warn("OpenProcessToken failed: {}", Kernel32.INSTANCE.GetLastError());
                return false;
            }

            WinNT.TOKEN_ELEVATION elevation = new WinNT.TOKEN_ELEVATION();
            IntByReference returnLength = new IntByReference();

            boolean result = Advapi32.INSTANCE.GetTokenInformation(
                    hTokenRef.getValue(),
                    WinNT.TOKEN_INFORMATION_CLASS.TokenElevation,
                    elevation,
                    elevation.size(),
                    returnLength
            );

            if (!result) {
                log.warn("GetTokenInformation failed: {}", Kernel32.INSTANCE.GetLastError());
                return false;
            }

            boolean isAdmin = elevation.TokenIsElevated != 0;
            log.info("User '{}' running as admin: {}", Advapi32Util.getUserName(), isAdmin);
            return isAdmin;

        } catch (Exception e) {
            log.error("Error checking admin privileges: {}", e.getMessage(), e);
            return false;
        } finally {
            if (hTokenRef.getValue() != null) {
                Kernel32.INSTANCE.CloseHandle(hTokenRef.getValue());
            }
        }
    }
}