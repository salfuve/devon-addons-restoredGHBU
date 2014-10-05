package org.oasp.module.monitoring;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import kieker.common.record.AbstractMonitoringRecord;
import kieker.common.record.controlflow.OperationExecutionRecord;
import kieker.common.util.registry.IRegistry;

/**
 * This class extends {@link kieker.common.record.controlflow.OperationExecutionRecord} by additional important
 * monitoring data. Modify this class and the {@link CustomOperationExecutionMethodInvocationInterceptor} to customize
 * your performance monitoring.
 * 
 * @author jmensing
 * @version 1.0
 */
public class CustomOperationExecutionRecord extends OperationExecutionRecord {

  /**
   * The size of the record as a byte array.
   */
  @SuppressWarnings("hiding")
  public static final int SIZE = (2 * TYPE_SIZE_STRING) + (3 * TYPE_SIZE_LONG) + TYPE_SIZE_STRING
      + (2 * TYPE_SIZE_INT) + TYPE_SIZE_STRING + (2 * TYPE_SIZE_LONG) + TYPE_SIZE_BOOLEAN;

  /**
   * An array defining the types of data in the array created by {@link #toArray()}
   */
  @SuppressWarnings("hiding")
  public static final Class<?>[] TYPES = { String.class, // operationSignature
  String.class, // sessionId
  long.class, // traceId
  long.class, // tin
  long.class, // tout
  String.class, // hostname
  int.class, // eoi
  int.class, // ess
  String.class, // actionId
  long.class, // userId
  long.class, // threadId
  boolean.class, // success
  };

  private static final long serialVersionUID = 4212157085975567954L;

  /**
   * Constant to be used if no action ID required.
   */
  public static final String NO_ACTIONID = "<no-action-id>";

  /**
   * Constant to be used if no user ID required.
   */
  public static final long NO_USERID = -1;

  /**
   * Constant to be used if no thread ID required.
   */
  public static final long NO_THREADID = -1;

  private final String actionId; // a String defining the action, e.g. dialogue-name, use-case-name, batch-step...

  private final long userId;

  private final long threadId;

  private final boolean success; // a success indicator. True if no exception was thrown

  /**
   * 
   * @param operationSignature an operation string, as defined in
   *        {@link kieker.common.util.signature.ClassOperationSignaturePair#splitOperationSignatureStr(String)}.
   * @param sessionId the session ID; must not be null, use {@link #NO_SESSION_ID} if no session ID desired.
   * @param traceId the trace ID; use {@link #NO_TRACEID} if no trace ID desired.
   * @param tin the execution start timestamp; use {@link #NO_TIMESTAMP} if no timestamp desired.
   * @param tout the execution stop timestamp; use {@link #NO_TIMESTAMP} if no timestamp desired.
   * @param hostname the host name; must not be null, use {@link #NO_HOSTNAME} if no host name desired.
   * @param eoi the execution order index (eoi); use {@link #NO_EOI_ESS} if no eoi desired.
   * @param ess the execution stack size (ess); use {@link #NO_EOI_ESS} if no ess desired.
   * @param actionId a String defining the action, e.g. dialogue-name, use-case-name, batch-step. Use
   *        {@link #NO_ACTIONID} if no action ID desired.
   * @param userId the user ID; use {@link #NO_USERID} if no user ID desired.
   * @param threadId the thread ID; use {@link #NO_THREADID} if no tread ID desired.
   * @param success a success indicator; true if no exception was thrown
   */
  public CustomOperationExecutionRecord(final String operationSignature, final String sessionId,
      final long traceId, final long tin, final long tout, final String hostname, final int eoi, final int ess,
      final String actionId, final long userId, final long threadId, final boolean success) {

    super(operationSignature, sessionId, traceId, tin, tout, hostname, eoi, ess);
    this.actionId = (actionId == null) ? NO_ACTIONID : actionId;
    this.userId = userId;
    this.threadId = threadId;
    this.success = success;
  }

  /**
   * @param buffer the bytes for the record
   * @param stringRegistry usually the monitoringController instance
   * @throws BufferUnderflowException if buffer not sufficient
   */
  public CustomOperationExecutionRecord(final ByteBuffer buffer, final IRegistry<String> stringRegistry)
      throws BufferUnderflowException {

    super(buffer, stringRegistry);
    this.actionId = stringRegistry.get(buffer.getInt());
    this.userId = buffer.getLong();
    this.threadId = buffer.getLong();
    this.success = buffer.get() != 0;
  }

  /**
   * @return value of actionId
   */
  public String getActionId() {

    return this.actionId;
  }

  /**
   * @return value of userId
   */
  public long getUserId() {

    return this.userId;
  }

  /**
   * @return value of threadId
   */
  public long getThreadId() {

    return this.threadId;
  }

  /**
   * @return value of success
   */
  public boolean isSuccess() {

    return this.success;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Object[] toArray() {

    return new Object[] { getOperationSignature(), getSessionId(), getTraceId(), getTin(), getTout(),
    getHostname(), getEoi(), getEss(), getActionId(), getUserId(), getThreadId(), isSuccess() };
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void writeBytes(final ByteBuffer buffer, final IRegistry<String> stringRegistry)
      throws BufferOverflowException {

    super.writeBytes(buffer, stringRegistry);

    buffer.putInt(stringRegistry.get(getActionId()));
    buffer.putLong(getUserId());
    buffer.putLong(getThreadId());
    buffer.put((byte) (isSuccess() ? 1 : 0));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Class<?>[] getValueTypes() {

    return CustomOperationExecutionRecord.TYPES;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int getSize() {

    return CustomOperationExecutionRecord.SIZE;
  }

  /**
   * This constructor converts the given array into a record. It is recommended to use the array which is the result of
   * a call to {@link #toArray()}. It is used during the analysis, e.g. when a reader class recreates records from a
   * textual file.
   * 
   * @param values The values for the record.
   */
  public CustomOperationExecutionRecord(final Object[] values) {

    super(Arrays.copyOf(values, 8));
    AbstractMonitoringRecord.checkArray(values, TYPES);
    this.actionId = (String) values[8];
    this.userId = (long) values[9];
    this.threadId = (long) values[10];
    this.success = (boolean) values[11];

  }

}
