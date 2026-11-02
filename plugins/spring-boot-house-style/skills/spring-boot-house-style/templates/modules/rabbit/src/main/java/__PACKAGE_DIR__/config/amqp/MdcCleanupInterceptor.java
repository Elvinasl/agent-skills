package __PACKAGE__.config.amqp;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.MDC;

/**
 * Must be the outermost advice so MDC survives every retry attempt and the recoverer's log line, then clears once
 * the delivery is resolved. Listener threads are pooled, so a leftover context would leak onto the next message.
 */
public class MdcCleanupInterceptor implements MethodInterceptor {

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        try {
            return invocation.proceed();
        } finally {
            MDC.clear();
        }
    }
}
