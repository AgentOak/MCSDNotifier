package me.agentoak.mcsdnotifier.system;

/**
 * Thrown by {@link SDNotify} to indicate an error.
 */
public class SDNotifyException extends Exception {
    private static final long serialVersionUID = 2603880580925652281L;

    public SDNotifyException(String message) {
        super(message);
    }

    public SDNotifyException(String message, Throwable cause) {
        super(message, cause);
    }
}
