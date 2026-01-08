package com.caerus.audit.client.util;

import com.sun.jna.Library;
import com.sun.jna.Native;

public interface SystemLock {
  interface User32 extends Library {
    User32 INSTANCE = Native.load("user32", User32.class);

    boolean LockWorkStation();
  }

  static void lockWorkstation() {
    try {
      User32.INSTANCE.LockWorkStation();
    } catch (Exception e) {
      throw new RuntimeException("Failed to lock workstation", e);
    }
  }
}
