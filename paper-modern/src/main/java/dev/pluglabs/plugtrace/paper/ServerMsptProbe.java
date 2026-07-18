package dev.pluglabs.plugtrace.paper;

import java.lang.reflect.Method;
import java.util.OptionalDouble;

/** Optional Paper-family MSPT probe kept reflective so reduced-capability artifacts fail closed. */
final class ServerMsptProbe {
    private ServerMsptProbe() {}

    static OptionalDouble sample(Object server) {
        if (server == null) {
            return OptionalDouble.empty();
        }
        try {
            Method method = server.getClass().getMethod("getAverageTickTime");
            Object value = method.invoke(server);
            if (!(value instanceof Number number)) {
                return OptionalDouble.empty();
            }
            double mspt = number.doubleValue();
            return Double.isFinite(mspt) && mspt >= 0.0
                    ? OptionalDouble.of(mspt) : OptionalDouble.empty();
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return OptionalDouble.empty();
        }
    }
}
