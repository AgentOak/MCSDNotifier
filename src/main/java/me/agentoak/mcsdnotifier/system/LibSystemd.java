package me.agentoak.mcsdnotifier.system;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.ptr.LongByReference;

public interface LibSystemd extends Library {
    String LIBRARY_NAME = "systemd";

    /**
     * @see Native#loadLibrary(String, Class)
     */
    static LibSystemd load() {
        return Native.loadLibrary(LIBRARY_NAME, LibSystemd.class);
    }

    /**
     * @see <a href="https://www.freedesktop.org/software/systemd/man/latest/sd_notify.html">sd_notify(3)</a>
     */
    int sd_notify(int unset_environment, String state);

    /**
     * @see <a href="https://www.freedesktop.org/software/systemd/man/latest/sd_watchdog_enabled.html">sd_watchdog_enabled(3)</a>
     */
    int sd_watchdog_enabled(int unset_environment, LongByReference watchdog_usec);
}
