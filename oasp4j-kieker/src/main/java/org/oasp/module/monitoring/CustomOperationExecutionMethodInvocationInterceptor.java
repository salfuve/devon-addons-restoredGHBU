package org.oasp.module.monitoring;

import kieker.common.logging.Log;
import kieker.common.logging.LogFactory;
import kieker.monitoring.core.controller.IMonitoringController;
import kieker.monitoring.core.controller.MonitoringController;
import kieker.monitoring.probe.IMonitoringProbe;
import kieker.monitoring.timer.ITimeSource;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * This class is an extended version of the
 * {@link kieker.monitoring.probe.spring.executions.OperationExecutionMethodInvocationInterceptor}. It should be used as
 * an Spring-AOP aspect and configured via the kieker.monitoring.adaptiveMonitoring.conf and the dedicated spring config
 * file. Modify this class and the {@link CustomOperationExecutionRecord} to customize your performance monitoring.
 * 
 * If you intend to add further business logic context, you also need to extend the {@link CustomControlFlowRegistry}.
 * 
 * @author jmensing
 * @version 1.0
 */
public class CustomOperationExecutionMethodInvocationInterceptor implements MethodInterceptor, IMonitoringProbe {
  private static final Log LOG = LogFactory.getLog(CustomOperationExecutionMethodInvocationInterceptor.class);

  private static final CustomControlFlowRegistry CF_REGISTRY = CustomControlFlowRegistry.INSTANCE;

  private final IMonitoringController monitoringCtrl;

  private final ITimeSource timeSource;

  private final String hostname;

  /**
   * The default constructor.
   */
  public CustomOperationExecutionMethodInvocationInterceptor() {

    this(MonitoringController.getInstance());
  }

  /**
   * This constructor is mainly used for testing, providing a custom {@link IMonitoringController} instead of using the
   * singleton instance.
   * 
   * @param monitoringController must not be null
   */
  public CustomOperationExecutionMethodInvocationInterceptor(final IMonitoringController monitoringController) {

    this.monitoringCtrl = monitoringController;
    this.timeSource = this.monitoringCtrl.getTimeSource();
    this.hostname = this.monitoringCtrl.getHostname();
  }

  /**
   * If you modified {@link CustomOperationExecutionRecord}, also modify this method to provide the new data.
   * 
   * @see org.aopalliance.intercept.MethodInterceptor#invoke(org.aopalliance.intercept.MethodInvocation)
   */
  public Object invoke(final MethodInvocation invocation) throws Throwable { // NOCS (IllegalThrowsCheck)

    if (!this.monitoringCtrl.isMonitoringEnabled()) {
      return invocation.proceed();
    }
    final String signature = invocation.getMethod().toString();
    if (!this.monitoringCtrl.isProbeActivated(signature)) {
      return invocation.proceed();
    }

    final int eoi; // this is executionOrderIndex-th execution in this trace
    final int ess; // this is the height in the dynamic call tree of this execution
    final boolean entrypoint;
    long traceId = CF_REGISTRY.recallThreadLocalTraceId(); // traceId, -1 if entry point
    if (traceId == -1) {
      entrypoint = true;
      traceId = CF_REGISTRY.getAndStoreUniqueThreadLocalTraceId();
      CF_REGISTRY.storeThreadLocalEOI(0);
      CF_REGISTRY.storeThreadLocalESS(1); // next operation is ess + 1
      eoi = 0;
      ess = 0;
    } else {
      entrypoint = false;
      eoi = CF_REGISTRY.incrementAndRecallThreadLocalEOI(); // ess > 1
      ess = CF_REGISTRY.recallAndIncrementThreadLocalESS(); // ess >= 0
      if ((eoi == -1) || (ess == -1)) {
        LOG.error("eoi and/or ess have invalid values:" + " eoi == " + eoi + " ess == " + ess);
        this.monitoringCtrl.terminateMonitoring();
      }
    }
    final long tin = this.timeSource.getTime();
    final String actionId = CF_REGISTRY.recallThreadLocalActionId();
    final long userId = CF_REGISTRY.recallThreadLocalUserId();
    final String sessionId = CF_REGISTRY.recallThreadLocalSessionId();
    final long threadId = Thread.currentThread().getId();
    boolean success = true;
    final Object retval;
    try {
      retval = invocation.proceed();
    } catch (Throwable t) {
      success = false;
      throw t;
    } finally {
      final long tout = this.timeSource.getTime();
      this.monitoringCtrl.newMonitoringRecord(new CustomOperationExecutionRecord(signature, sessionId, traceId,
          tin, tout, this.hostname, eoi, ess, actionId, userId, threadId, success));
      // cleanup
      if (entrypoint) {
        CF_REGISTRY.unsetThreadLocalTraceId();
        CF_REGISTRY.unsetThreadLocalEOI();
        CF_REGISTRY.unsetThreadLocalESS();
      } else {
        CF_REGISTRY.storeThreadLocalESS(ess); // next operation is ess
      }
    }
    return retval;
  }

}
