package org.oasp.module.monitoring;

import java.util.Calendar;

import javax.servlet.http.HttpSession;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * This class is an alternative example to {@link org.oasp.module.monitoring.ContextProvider} that can be used in
 * combination with {@link org.oasp.module.monitoring.ManualOperationExecutionProbe} when Spring-AOP is not an available
 * option.
 * 
 * @author jmensing
 * @version 1.0
 */
public class ManualContextProvider {

  private static final CustomControlFlowRegistry CF_REGISTRY = CustomControlFlowRegistry.INSTANCE;

  /**
   * This method sets the business logic context of a monitored use-case thread-locally. It should be called inside the
   * entry-point-method of a monitored use-case before any other code.
   * 
   * @param actionId The String identifier for the use-case.
   */
  public static void before(String actionId) {

    final long userId;
    final String sessionId;

    ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
    HttpSession session = attr.getRequest().getSession();
    sessionId = session.getId();

    Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    userId = (principal instanceof UserDetails) ? ((UserDetails) principal).getUsername().hashCode()
        : CustomOperationExecutionRecord.NO_USERID;

    CF_REGISTRY.storeThreadLocalActionId(actionId);
    CF_REGISTRY.storeThreadLocalUserId(userId);
    CF_REGISTRY.storeThreadLocalSessionId(sessionId);
  }

  /**
   * This method is an alternative to {@link #before(String)}. It uses an auto-generated use-case-identifier. The
   * identifier is generated like so: &lt;method-name&gt;@&lt;timeInMillis&gt;.
   */
  public static void before() {

    before(Thread.currentThread().getStackTrace()[2].getMethodName() + "@"
        + Calendar.getInstance().getTimeInMillis());
  }

  /**
   * This method unsets the thread-local business logic context of a monitored use-case. It should ALWAYS be called at
   * the last possible point of the entry-point-method of a monitored use-case, regardless of it's completion status.
   * Therefore a try-finally block should be used.
   */
  public static void after() {

    CF_REGISTRY.unsetThreadLocalActionId();
    CF_REGISTRY.unsetThreadLocalUserId();
    CF_REGISTRY.unsetThreadLocalSessionId();
  }

}
