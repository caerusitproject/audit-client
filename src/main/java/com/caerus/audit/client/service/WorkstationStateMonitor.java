package com.caerus.audit.client.service;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.win32.StdCallLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkstationStateMonitor {
  private static final Logger log = LoggerFactory.getLogger(WorkstationStateMonitor.class);

  private final ScreenshotService screenshotService;

  private static final int WM_WTSSESSION_CHANGE = 0x02B1;
  private static final int WTS_SESSION_LOCK = 0x7;
  private static final int WTS_SESSION_UNLOCK = 0x8;
  private static final int NOTIFY_FOR_THIS_SESSION = 0;

  private volatile boolean running = false;
  private WinDef.HWND hwnd;

  public WorkstationStateMonitor(ScreenshotService screenshotService) {
    this.screenshotService = screenshotService;
  }

  public interface Wtsapi32 extends StdCallLibrary {
    Wtsapi32 INSTANCE = Native.load("Wtsapi32", Wtsapi32.class);

    boolean WTSRegisterSessionNotification(WinDef.HWND hWnd, int dwFlags);

    boolean WTSUnRegisterSessionNotification(WinDef.HWND hWnd);
  }

  public void start() {
    if (running) {
      log.warn("WorkstationStateMonitor already running");
      return;
    }
    running = true;

    Thread listenerThread = new Thread(this::runMessageLoop, "WorkstationStateMonitor-Thread");
    listenerThread.setDaemon(true);
    listenerThread.start();

    log.info("WorkstationStateMonitor started");
  }

  public void stop() {
    running = false;
    if (hwnd != null) {
      try {
        log.info("Unregistering workstation session notifications...");
        Wtsapi32.INSTANCE.WTSUnRegisterSessionNotification(hwnd);
        User32.INSTANCE.PostMessage(hwnd, WinUser.WM_QUIT, null, null);
      } catch (Exception e) {
        log.error("Error during WorkstationStateMonitor cleanup: {}", e.getMessage(), e);
      }
    }
  }

  private void runMessageLoop() {
    final User32 user32 = User32.INSTANCE;
    final Kernel32 kernel32 = Kernel32.INSTANCE;

    WinDef.HINSTANCE hInst = kernel32.GetModuleHandle(null);
    String className = "AuditClientHiddenWindow";

    // Define the hidden window class
    WinUser.WNDCLASSEX wndClass = new WinUser.WNDCLASSEX();
    wndClass.cbSize = wndClass.size();

    wndClass.lpfnWndProc =
        new WinUser.WindowProc() {
          @Override
          public WinDef.LRESULT callback(
              WinDef.HWND hwnd, int uMsg, WinDef.WPARAM wParam, WinDef.LPARAM lParam) {
            if (uMsg == WM_WTSSESSION_CHANGE) {
              int event = wParam.intValue();
              switch (event) {
                case WTS_SESSION_LOCK:
                  log.info("Workstation locked — pausing ScreenshotService");
                  screenshotService.stop();
                  break;
                case WTS_SESSION_UNLOCK:
                  log.info("Workstation unlocked — resuming ScreenshotService");
                  screenshotService.start();
                  break;
                default:
                  break;
              }
            }
            return user32.DefWindowProc(hwnd, uMsg, wParam, lParam);
          }
        };

    wndClass.lpszClassName = className;
    user32.RegisterClassEx(wndClass);

    hwnd =
        user32.CreateWindowEx(0, className, "HiddenWindow", 0, 0, 0, 0, 0, null, null, hInst, null);

    Wtsapi32.INSTANCE.WTSRegisterSessionNotification(hwnd, NOTIFY_FOR_THIS_SESSION);

    WinUser.MSG msg = new WinUser.MSG();
    while (running && user32.GetMessage(msg, hwnd, 0, 0) != 0) {
      user32.TranslateMessage(msg);
      user32.DispatchMessage(msg);
    }

    // Cleanup
    Wtsapi32.INSTANCE.WTSUnRegisterSessionNotification(hwnd);
    user32.DestroyWindow(hwnd);
    user32.UnregisterClass(className, hInst);

    log.info("WorkstationStateMonitor stopped and cleaned up");
  }
}
