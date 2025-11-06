package com.caerus.audit.client.service;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.win32.StdCallLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

public class IdleMonitor {
    private final Logger log = LoggerFactory.getLogger(IdleMonitor.class);
    private final ConfigService config;
    private final ScreenshotService screenshotService;
    private final ScheduledExecutorService scheduler;
    private boolean paused = false;

    public interface Kernel32 extends StdCallLibrary {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);
        int GetLastError();
    }

    public IdleMonitor(ConfigService config, ScreenshotService screenshotService) {
        this.config = config;
        this.screenshotService = screenshotService;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "IdleMonitor-Thread")
        );
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::check, 1, 1, TimeUnit.SECONDS);
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    private void check() {
        try {
            long idleSec = LastInput.getIdleTimeSeconds();
            var s = config.getLatest();
            int idleThreshold = (s!=null && s.configIdleTimeout != null) ? s.configIdleTimeout : 12;
            if(idleSec >= idleThreshold && !paused){
                paused = true;
                log.info("System idle for {}s; pausing captures", idleSec);
                screenshotService.stop();
            } else if(idleSec < idleThreshold && paused){
                paused = false;
                log.info("System active; resuming captures");
                screenshotService.start();
            }
        } catch (Exception e) {
            log.error("Idle check error: {}", e.getMessage());
        }
    }

    static class LastInput{
        private static final User32 user32 = User32.INSTANCE;
        private static final MyKernel32 kernel32 = MyKernel32.INSTANCE;

        static long getIdleTimeSeconds(){
            WinUser.LASTINPUTINFO info = new WinUser.LASTINPUTINFO();
            info.cbSize = info.size();
            if (!user32.GetLastInputInfo(info)) {
                return 0;
            }
            long last = info.dwTime;
            long now = kernel32.GetTickCount64();
            return Math.max(0, (now - last) / 1000);
        }
    }

    public interface MyKernel32 extends Kernel32 {
        MyKernel32 INSTANCE = Native.load("kernel32", MyKernel32.class);
        long GetTickCount64();
    }
}
