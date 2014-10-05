package org.oasp.module.monitoring;

import kieker.common.logging.Log;
import kieker.common.logging.LogFactory;
import kieker.monitoring.core.controller.IMonitoringController;
import kieker.monitoring.core.controller.MonitoringController;
import kieker.monitoring.probe.IMonitoringProbe;

/**
 * This class is an extended version of the
 * {@link org.oasp.module.monitoring.CustomOperationExecutionMethodInvocationInterceptor}. It is designed for manual
 * instrumentation. Use this class if you cannot or don't want to use Spring-AOP or to add to the monitoring with
 * Spring-AOP and {@link org.oasp.module.monitoring.CustomOperationExecutionMethodInvocationInterceptor}.
 * 
 * @author jmensing
 * @version 1.0
 */
public class ManualOperationExecutionProbe implements IMonitoringProbe {
  private static final Log LOG = LogFactory.getLog(CustomOperationExecutionMethodInvocationInterceptor.class);

  private static final CustomControlFlowRegistry CF_REGISTRY = CustomControlFlowRegistry.INSTANCE;

  private ManualOperationExecutionProbe() {

    super();
  }

  /**
   * This method should be called inside the monitored method before any other code.
   * 
   * @param methodSignature The method signature of the monitored method. Can be provided using
   *        "this.getClass().getDeclaredMethod(String name, Class&lt;?&gt;... parameterTypes).toString();".
   * @return The data that needs to be passed to {@link #after(MonitoringInfo, boolean)} to complete the recording of
   *         the monitoring data.
   */
  public static MonitoringInfo before(String methodSignature) { // NOCS (IllegalThrowsCheck)

    IMonitoringController monitoringCtrl = MonitoringController.getInstance();
    boolean failed = false;
    if (!monitoringCtrl.isMonitoringEnabled()) {
      failed = true;
    }

    if (!monitoringCtrl.isProbeActivated(methodSignature)) {
      failed = true;
    }
    boolean entryPoint;
    int eoi;
    int ess;
    long tin;
    long traceId = CF_REGISTRY.recallThreadLocalTraceId(); // traceId, -1 if entry point
    if (traceId == -1) {
      entryPoint = true;
      traceId = CF_REGISTRY.getAndStoreUniqueThreadLocalTraceId();
      CF_REGISTRY.storeThreadLocalEOI(0);
      CF_REGISTRY.storeThreadLocalESS(1); // next operation is ess + 1
      eoi = 0;
      ess = 0;
    } else {
      entryPoint = false;
      eoi = CF_REGISTRY.incrementAndRecallThreadLocalEOI(); // ess > 1
      ess = CF_REGISTRY.recallAndIncrementThreadLocalESS(); // ess >= 0
      if ((eoi == -1) || (ess == -1)) {
        LOG.error("eoi and/or ess have invalid values:" + " eoi == " + eoi + " ess == " + ess);
        monitoringCtrl.terminateMonitoring();
        failed = true;
      }
    }
    tin = monitoringCtrl.getTimeSource().getTime();

    return new MonitoringInfo(failed, monitoringCtrl, methodSignature, entryPoint, eoi, ess, tin, traceId);
  }

  /**
   * Receiving the {@link MonitoringInfo} from {@link #before(String)}This method completes the recording of the
   * monitoring data. It should ALWAYS be called at the last possible point of the monitored method, regardless of it's
   * completion status. Therefore a try-catch-finally block should be used.
   * 
   * @param mi The {@link MonitoringInfo} created by {@link #before(String)} at the beginning of the monitored method.
   * @param success An indicator if the monitored method completed successfully.
   */
  public static void after(MonitoringInfo mi, boolean success) {

    if (!mi.failed) {
      String actionId = CF_REGISTRY.recallThreadLocalActionId();
      long userId = CF_REGISTRY.recallThreadLocalUserId();
      String sessionId = CF_REGISTRY.recallThreadLocalSessionId();
      long threadId = Thread.currentThread().getId();
      long tout = mi.monitoringCtrl.getTimeSource().getTime();
      mi.monitoringCtrl.newMonitoringRecord(new CustomOperationExecutionRecord(mi.methodSignature, sessionId,
          mi.traceId, mi.tin, tout, mi.monitoringCtrl.getHostname(), mi.eoi, mi.ess, actionId, userId, threadId,
          success));
    }
    // cleanup
    if (mi.entryPoint) {
      CF_REGISTRY.unsetThreadLocalTraceId();
      CF_REGISTRY.unsetThreadLocalEOI();
      CF_REGISTRY.unsetThreadLocalESS();
    } else {
      CF_REGISTRY.storeThreadLocalESS(mi.ess); // next operation is ess
    }
  }

  /**
   * This static nested class represents the data that needs to be passed from
   * {@link org.oasp.module.monitoring.ManualOperationExecutionProbe#before(String)} to
   * {@link org.oasp.module.monitoring.ManualOperationExecutionProbe#after(MonitoringInfo, boolean)} in order to
   * complete the recording of the monitoring data.
   * 
   * @author jmensing
   * @version $Id:$
   */
  public static class MonitoringInfo {
    IMonitoringController monitoringCtrl;

    private boolean failed;

    private String methodSignature;

    private boolean entryPoint;

    private int eoi;

    private int ess;

    private long tin;

    private long traceId;

    private MonitoringInfo(boolean failed, IMonitoringController monitoringCtrl, String methodSignature,
        boolean entryPoint, int eoi, int ess, long tin, long traceId) {

      this.failed = failed;
      this.monitoringCtrl = monitoringCtrl;
      this.methodSignature = methodSignature;
      this.entryPoint = entryPoint;
      this.eoi = eoi;
      this.ess = ess;
      this.tin = tin;
      this.traceId = traceId;
    }
  }
}
