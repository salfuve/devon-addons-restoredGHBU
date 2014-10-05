package org.oasp.module.monitoring;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import kieker.common.logging.Log;
import kieker.common.logging.LogFactory;
import kieker.monitoring.core.controller.MonitoringController;

/**
 * This class is an enhanced version of the {@link kieker.monitoring.core.registry.ControlFlowRegistry}. It stores
 * thread-local information for each monitored thread.
 * 
 * <em>!You have to store the context in an instance of this class BEFORE you can access it from any monitoring probe!</em>
 * 
 * "Extend" this class to provide further business logic context to customized monitoring probes. As enums cannot be
 * extended, you will have to copy the code to add more business logic context.
 * 
 * @author jmensing
 * @version 1.0
 */
public enum CustomControlFlowRegistry {
  // Singleton (Effective Java #3)
  /** The singleton instance. */
  INSTANCE;

  private static final Log LOG = LogFactory.getLog(CustomControlFlowRegistry.class); // NOPMD (enum logger)

  // In order to (probabilistically!) avoid that other instances in our system (on another node, in another vm, ...)
  // generate the same thread ids, we fill the
  // left-most 16 bits of the thread id with a uniquely distributed random number (0,0000152587890625 = 0,00152587890625
  // %). As a consequence, this constitutes a
  // uniquely distributed offset of size 2^(64-1-16) = 2^47 = 140737488355328L in the worst case. Note that we restrict
  // ourselves to the positive long values so
  // far. Of course, negative values may occur (as a result of an overflow) -- this does not hurt!
  private final AtomicLong lastThreadId = new AtomicLong(MonitoringController.getInstance().isDebug() ? 0 // NOCS
      : (long) new Random().nextInt(65536) << (Long.SIZE - 16 - 1));

  private final transient ThreadLocal<Long> threadLocalTraceId = new ThreadLocal<>();

  private final transient ThreadLocal<Integer> threadLocalEoi = new ThreadLocal<>();

  private final transient ThreadLocal<Integer> threadLocalEss = new ThreadLocal<>();

  private final transient ThreadLocal<String> threadLocalActionId = new ThreadLocal<>();

  private final transient ThreadLocal<Long> threadLocalUserId = new ThreadLocal<>();

  private final transient ThreadLocal<String> threadLocalSessionId = new ThreadLocal<>();

  static {
    LOG.info("First threadId will be " + INSTANCE.lastThreadId.get());
  }

  /**
   * This methods returns a globally unique trace id.
   * 
   * @return a globally unique trace id.
   */
  public final long getUniqueTraceId() {

    final long id = this.lastThreadId.incrementAndGet();
    // Since we use -1 as a marker for an invalid traceId, it must not be returned!
    if (id == -1) {
      // in this case, choose a valid threadId. Note, that this is not necessarily 0 due to concurrent executions of
      // this method.
      //
      // Example: like the following one, but it seems to fine:
      //
      // (this.lastThreadId = -2) Thread A: id = -1 (inc&get -2)
      // (this.lastThreadId = -1) Thread B: id = 0 (inc&get -1)
      // (this.lastThreadId = 0) Thread A: returns 1 (because id == -1, and this.lastThreadId=0 in the meantime)
      // (this.lastThreadId = 1) Thread B: returns 0 (because id != -1)
      return this.lastThreadId.incrementAndGet();
    } else { // i.e., id <> -1
      return id;
    }
  }

  /**
   * This method returns a thread-local traceid which is globally unique and stored it local for the thread. The thread
   * is responsible for invalidating the stored curTraceId using the method unsetThreadLocalTraceId()!
   * 
   * @return A unique trace ID.
   */
  public final long getAndStoreUniqueThreadLocalTraceId() {

    final long id = getUniqueTraceId();
    this.threadLocalTraceId.set(id);
    return id;
  }

  /**
   * This method stores a thread-local curTraceId. The thread is responsible for invalidating the stored curTraceId
   * using the method unsetThreadLocalTraceId()!
   * 
   * @param traceId The trace ID to store in a thread-local way.
   */
  public final void storeThreadLocalTraceId(final long traceId) {

    this.threadLocalTraceId.set(traceId);
  }

  /**
   * This method returns the thread-local traceid previously registered using the method registerTraceId(curTraceId).
   * 
   * @return the traceid. -1 if no curTraceId has been registered for this thread.
   */
  public final long recallThreadLocalTraceId() {

    final Long traceIdObj = this.threadLocalTraceId.get();
    if (traceIdObj == null) {
      return -1;
    }
    return traceIdObj;
  }

  /**
   * This method unsets a previously registered traceid.
   */
  public final void unsetThreadLocalTraceId() {

    this.threadLocalTraceId.remove();
  }

  /**
   * Used to explicitly register an curEoi. The thread is responsible for invalidating the stored curTraceId using the
   * method unsetThreadLocalEOI()!
   * 
   * @param eoi The execution order index to register.
   */
  public final void storeThreadLocalEOI(final int eoi) {

    this.threadLocalEoi.set(eoi);
  }

  /**
   * Since this method accesses a ThreadLocal variable, it is not (necessary to be) thread-safe.
   * 
   * @return Increments the thread local execution order index and returns it.
   */
  public final int incrementAndRecallThreadLocalEOI() {

    final Integer curEoi = this.threadLocalEoi.get();
    if (curEoi == null) {
      LOG.error("eoi has not been registered before");
      return -1;
    }
    final int newEoi = curEoi + 1;
    this.threadLocalEoi.set(newEoi);
    return newEoi;
  }

  /**
   * This method returns the thread-local curEoi previously registered using the method registerTraceId(curTraceId).
   * 
   * @return the sessionid. -1 if no curEoi registered.
   */
  public final int recallThreadLocalEOI() {

    final Integer curEoi = this.threadLocalEoi.get();
    if (curEoi == null) {
      LOG.error("eoi has not been registered before");
      return -1;
    }
    return curEoi;
  }

  /**
   * This method unsets a previously registered traceid.
   */
  public final void unsetThreadLocalEOI() {

    this.threadLocalEoi.remove();
  }

  /**
   * Used to explicitly register a execution stack size (ess) value. The thread is responsible for invalidating the
   * stored value using the method {@link #unsetThreadLocalESS()}!
   * 
   * @param ess The execution stack size to store.
   */
  public final void storeThreadLocalESS(final int ess) {

    this.threadLocalEss.set(ess);
  }

  /**
   * Since this method accesses a ThreadLocal variable, it is not (necessary to be) thread-safe.
   * 
   * @return The current execution stack size, before the incrementation.
   */
  public final int recallAndIncrementThreadLocalESS() {

    final Integer curEss = this.threadLocalEss.get();
    if (curEss == null) {
      LOG.error("ess has not been registered before");
      return -1;
    }
    this.threadLocalEss.set(curEss + 1);
    return curEss;
  }

  /**
   * This method returns the thread-local curEss previously registered using the method registerTraceId(curTraceId).
   * 
   * @return the sessionid. -1 if no curEss registered.
   */
  public final int recallThreadLocalESS() {

    final Integer ess = this.threadLocalEss.get();
    if (ess == null) {
      LOG.error("ess has not been registered before");
      return -1;
    }
    return ess;
  }

  /**
   * This method unsets a previously registered curEss.
   */
  public final void unsetThreadLocalESS() {

    this.threadLocalEss.remove();
  }

  /**
   * Used to explicitly register a actionId value. The thread is responsible for invalidating the stored value using the
   * method {@link #unsetThreadLocalActionId()}!
   * 
   * @param actionId The actionId to store.
   */
  public final void storeThreadLocalActionId(final String actionId) {

    this.threadLocalActionId.set(actionId);
  }

  /**
   * This method returns the thread-local actionId previously registered using the method registerActionId(actionId).
   * 
   * @return the actionId. {@link org.oasp.module.monitoring.CustomOperationExecutionRecord#NO_ACTIONID} if no actionId
   *         registered.
   */
  public final String recallThreadLocalActionId() {

    if (this.threadLocalActionId.get() == null) {
      LOG.error("actionId has not been registered before");
      return CustomOperationExecutionRecord.NO_ACTIONID;
    }
    return this.threadLocalActionId.get();
  }

  /**
   * This method unsets a previously registered actionId.
   */
  public final void unsetThreadLocalActionId() {

    this.threadLocalActionId.remove();
  }

  /**
   * Used to explicitly register a userId value. The thread is responsible for invalidating the stored value using the
   * method {@link #unsetThreadLocalUserId()}!
   * 
   * @param userId The userId to store.
   */
  public final void storeThreadLocalUserId(final Long userId) {

    this.threadLocalUserId.set(userId);
  }

  /**
   * This method returns the thread-local userId previously registered using the method registerUserId(userId).
   * 
   * @return the userId. {@link org.oasp.module.monitoring.CustomOperationExecutionRecord#NO_USERID} if no userId
   *         registered.
   */
  public final Long recallThreadLocalUserId() {

    if (this.threadLocalUserId.get() == null) {
      LOG.error("userId has not been registered before");
      return CustomOperationExecutionRecord.NO_USERID;
    }
    return this.threadLocalUserId.get();
  }

  /**
   * This method unsets a previously registered userId.
   */
  public final void unsetThreadLocalUserId() {

    this.threadLocalUserId.remove();
  }

  /**
   * Used to explicitly register a sessionId value. The thread is responsible for invalidating the stored value using
   * the method {@link #unsetThreadLocalSessionId()}!
   * 
   * @param sessionId The sessionId to store.
   */
  public final void storeThreadLocalSessionId(final String sessionId) {

    this.threadLocalSessionId.set(sessionId);
  }

  /**
   * This method returns the thread-local sessionId previously registered using the method registerSessionId(sessionId).
   * 
   * @return the sessionId. {@link org.oasp.module.monitoring.CustomOperationExecutionRecord#NO_SESSION_ID} if no
   *         sessionId registered.
   */
  public final String recallThreadLocalSessionId() {

    if (this.threadLocalSessionId.get() == null) {
      LOG.error("sessionId has not been registered before");
      return CustomOperationExecutionRecord.NO_SESSION_ID;
    }
    return this.threadLocalSessionId.get();
  }

  /**
   * This method unsets a previously registered sessionId.
   */
  public final void unsetThreadLocalSessionId() {

    this.threadLocalSessionId.remove();
  }
}
