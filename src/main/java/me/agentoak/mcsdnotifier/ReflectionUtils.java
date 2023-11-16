package me.agentoak.mcsdnotifier;

import java.lang.reflect.InvocationTargetException;

final class ReflectionUtils {
    private ReflectionUtils() {
    }

    /**
     * Check if a class by the given name exists. Loads the class if it exists but wasn't loaded already.
     */
    public static boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    /**
     * Check if a method exists, and if it does, invokes it and checks if it returns {@code true}.
     *
     * @return {@code true} if method exists and returned {@code true}, {@code false} otherwise
     */
    public static boolean methodIsTrue(Object object, String methodName) {
        try {
            return Boolean.TRUE.equals(object.getClass().getMethod(methodName).invoke(object));
        } catch (IllegalAccessException | NoSuchMethodException ignored) {
            return false;
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e.getCause());
        }
    }
}
