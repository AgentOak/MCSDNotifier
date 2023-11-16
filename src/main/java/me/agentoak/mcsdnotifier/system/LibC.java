package me.agentoak.mcsdnotifier.system;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;

public interface LibC extends Library {
    /**
     * @see Native#loadLibrary(String, Class)
     */
    static LibC load() {
        return Native.loadLibrary(Platform.C_LIBRARY_NAME, LibC.class);
    }

    /**
     * @see <a href="https://man7.org/linux/man-pages/man2/getpid.2.html">getpid(2)</a>
     */
    int getpid();
}
