package org.oasp.module.monitoring;

import java.util.Calendar;

import javax.servlet.http.HttpSession;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * This class is an example of how to store information in a {@link CustomControlFlowRegistry} for the current thread to
 * provide business logic context for {@link CustomOperationExecutionMethodInvocationInterceptor} and subclasses. It is
 * designed to be used as an Spring-AOP aspect that should be called before any monitoring probes on the same thread.
 * 
 * @author jmensing
 * @version 1.0
 */
public class ContextProvider implements MethodInterceptor {

  /**
   * The singleton {@link CustomControlFlowRegistry} that stores thread-local business context for each monitored
   * thread.
   */
  private static final CustomControlFlowRegistry CF_REGISTRY = CustomControlFlowRegistry.INSTANCE;

  /**
   * Should be called at the entry point (and only there) of a monitored use case. If it's not, business logic context
   * for the monitoring cannot be provided.
   * 
   * @see org.aopalliance.intercept.MethodInterceptor#invoke(org.aopalliance.intercept.MethodInvocation)
   * 
   */
  public Object invoke(final MethodInvocation invocation) throws Throwable {

    final String actionId;
    final long userId;
    final String sessionId;

    ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
    HttpSession session = attr.getRequest().getSession();
    sessionId = session.getId();

    Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    userId = (principal instanceof UserDetails) ? ((UserDetails) principal).getUsername().hashCode()
        : CustomOperationExecutionRecord.NO_USERID;

    actionId = invocation.getMethod().getName() + "@" + Calendar.getInstance().getTimeInMillis();

    CF_REGISTRY.storeThreadLocalActionId(actionId);
    CF_REGISTRY.storeThreadLocalUserId(userId);
    CF_REGISTRY.storeThreadLocalSessionId(sessionId);
    final Object retval;
    try {
      retval = invocation.proceed();
    } finally {
      CF_REGISTRY.unsetThreadLocalActionId();
      CF_REGISTRY.unsetThreadLocalUserId();
      CF_REGISTRY.unsetThreadLocalSessionId();
    }
    return retval;
  }
}
