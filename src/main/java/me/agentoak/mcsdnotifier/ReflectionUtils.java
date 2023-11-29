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
     * Check if the given class has a method with the given name.
     */
    public static boolean methodExists(Class<?> clazz, String methodName) {
        try {
            clazz.getMethod(methodName);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    /**
     * Call a method with no arguments and return its result.
     *
     * @throws RuntimeException if the method does not exist or is not accessible
     */
    @SuppressWarnings("unchecked")
    public static <T> T methodGetter(Object object, String methodName) {
        try {
            return (T) object.getClass().getMethod(methodName).invoke(object);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException("Cannot call method " + object.getClass().getName() + "#" + methodName + "(). " +
                                           "This is a bug", e);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            } else if (e.getCause() instanceof Error) {
                throw (Error) e.getCause();
            }
            throw new RuntimeException(e.getCause());
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
        } catch (NoSuchMethodException | IllegalAccessException ignored) {
            return false;
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            } else if (e.getCause() instanceof Error) {
                throw (Error) e.getCause();
            }
            throw new RuntimeException(e.getCause());
        }
    }
}
