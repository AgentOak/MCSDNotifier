package me.agentoak.mcsdnotifier.system;

import com.sun.jna.Native;
import com.sun.jna.ptr.LongByReference;

import java.nio.file.FileSystems;
import java.nio.file.Files;

/**
 * More Java-like wrapper for the {@code sd-daemon} API of libsystemd. Handles building the state string for
 * {@code sd_notify(3)}.
 *
 * @see <a href="https://www.freedesktop.org/software/systemd/man/latest/sd-daemon.html">sd-daemon(3)</a>
 * @see LibSystemd#sd_notify(int, String)
 */
public class SDNotify {
    protected final int pid;
    protected final LibSystemd libsystemd;

    protected final long watchdogUsec;

    /**
     * @throws SDNotifyException if native library could not be loaded or {@code sd_watchdog_enabled(3)} call failed
     * @see Native#loadLibrary(String, Class)
     * @see LibSystemd#sd_watchdog_enabled(int, LongByReference)
     */
    public SDNotify() throws SDNotifyException {
        try {
            pid = LibC.load().getpid();
        } catch (UnsatisfiedLinkError e) {
            throw new SDNotifyException("Could not load libc to determine pid", e);
        }

        try {
            libsystemd = LibSystemd.load();
        } catch (UnsatisfiedLinkError e) {
            throw new SDNotifyException("Could not load systemd library. Is libsystemd installed?", e);
        }

        LongByReference usec = new LongByReference();
        int retval = libsystemd.sd_watchdog_enabled(0, usec);
        if (retval < 0) {
            // We could translate errno with strerror but that would require even more error handling
            throw new SDNotifyException("sd_watchdog_enabled() failed: errno=" + -retval);
        } else if (retval == 0) {
            // Watchdog is disabled (usec is not valid)
            watchdogUsec = 0;
        } else {
            // usec type is uint64_t but dealing with unsigned types is annoying so clamp to signed maximum value
            watchdogUsec = usec.getValue() < 0 ? Long.MAX_VALUE : usec.getValue();
        }
    }

    /**
     * Check if {@code sd_notify(3)} is supported on this platform. This implements the same check as
     * {@code sd_booted(3)} but in plain Java code, i.e. native libsystemd does not get loaded for this method.
     * <p>
     * This does not verify that we are running directly under a service manager that reads our notifications, nor that
     * watchdog is enabled. Note that {@code sd_notify(3)} documentation recommends ignoring errors, i.e. notifications
     * not being received by anyone should not be considered an error.
     *
     * @see <a href="https://www.freedesktop.org/software/systemd/man/latest/sd_booted.html">sd_booted(3)</a>
     * @see <a href="https://www.freedesktop.org/software/systemd/man/latest/sd_notify.html#Return%20Value">sd_notify(3) Return Value</a>
     */
    public static boolean isPlatformSupported() {
        // Check if NOTIFY_SOCKET is set in case some future non-systemd init system supports it
        String socketVar = System.getenv("NOTIFY_SOCKET");
        return (socketVar != null && !socketVar.isEmpty()) ||
                   Files.isDirectory(FileSystems.getDefault().getPath("/run", "systemd", "system"));
    }

    /**
     * Result of {@code getpid(2)} call. Obtained only once and then cached, so this method has no syscall cost.
     *
     * @see LibC#getpid()
     */
    public int getPid() {
        return pid;
    }

    /**
     * Check whether the service manager expects watchdog keep-alive notifications.
     *
     * @return Watchdog interval in µs, or 0 if watchdog is disabled
     * @see <a href="https://www.freedesktop.org/software/systemd/man/latest/sd_watchdog_enabled.html">sd_watchdog_enabled(3)</a>
     */
    public long getWatchdogUsec() {
        return watchdogUsec;
    }

    /**
     * Sends our pid as MAINPID, so the service manager can keep track of the main process in case we were forked off
     * from a container process like {@code screen(1)}, and sets NOTIFYACCESS=main to lock down notify socket access to
     * this process.
     * <p>
     * Also passes a free-form string back to the service manager that describes the service state.
     *
     * @param status a single-line free-form status string, {@code null} to send a generic default text
     */
    public void init(String status) {
        libsystemd.sd_notify(0, String.format("MAINPID=%d\nNOTIFYACCESS=main\nSTATUS=%s", pid,
            status == null ? "Loading" : status));
    }

    /**
     * Tells the service manager that the service is ready, i.e. finished starting or reloading.
     * <p>
     * Also passes a free-form string back to the service manager that describes the service state.
     *
     * @param status a single-line free-form status string, {@code null} to send a generic default text
     */
    public void ready(String status) {
        libsystemd.sd_notify(0, String.format("READY=1\nSTATUS=%s", status == null ? "Running" : status));
    }

    /**
     * Tells the service manager to update the watchdog timestamp. This is the keep-alive ping that services need to
     * issue in regular intervals if {@code WatchdogSec=} is enabled for it.
     * <p>
     * Also passes a free-form string back to the service manager that describes the service state.
     *
     * @param status a single-line free-form status string, {@code null} to send a generic default text
     * @see #getWatchdogUsec()
     */
    public void watchdog(String status) {
        libsystemd.sd_notify(0, String.format("WATCHDOG=1\nSTATUS=%s", status == null ? "Running" : status));
    }

    /**
     * Tell the service manager that the service is beginning to reload. Should call {@link #ready(String)} when
     * done.
     * <p>
     * Also passes a free-form string back to the service manager that describes the service state.
     *
     * @param status a single-line free-form status string, {@code null} to send a generic default text
     */
    public void reloading(String status) {
        libsystemd.sd_notify(0, String.format("RELOADING=1\nSTATUS=%s", status == null ? "Reloading" : status));
    }

    /**
     * Tells the service manager that the service is beginning its shutdown.
     * <p>
     * Also passes a free-form string back to the service manager that describes the service state.
     *
     * @param status a single-line free-form status string, {@code null} to send a generic default text
     */
    public void stopping(String status) {
        libsystemd.sd_notify(0, String.format("STOPPING=1\nSTATUS=%s", status == null ? "Stopping" : status));
    }
}
