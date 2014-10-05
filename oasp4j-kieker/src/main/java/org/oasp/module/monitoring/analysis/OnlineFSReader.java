package org.oasp.module.monitoring.analysis;

import java.io.File;
import java.util.PriorityQueue;

import kieker.analysis.IProjectContext;
import kieker.analysis.plugin.annotation.OutputPort;
import kieker.analysis.plugin.annotation.Plugin;
import kieker.analysis.plugin.annotation.Property;
import kieker.analysis.plugin.reader.AbstractReaderPlugin;
import kieker.common.configuration.Configuration;
import kieker.common.record.IMonitoringRecord;
import kieker.common.record.misc.EmptyRecord;
import kieker.common.util.registry.IMonitoringRecordReceiver;

/**
 * This class is an extended version of the {@link kieker.analysis.plugin.reader.filesystem.FSReader}. It has been
 * modified to read from directories <em>while</em> monitoring logs are being written. It does not support reading of
 * binary or zip files!
 * 
 * @author jmensing
 * @version 1.0
 */
@Plugin(
    description = "A file system reader which reads records from multiple directories",
    outputPorts = { @OutputPort(
        name = OnlineFSReader.OUTPUT_PORT_NAME_RECORDS, eventTypes = { IMonitoringRecord.class },
        description = "Output Port of the OnlineFSReader") }, configuration = {
    @Property(
        name = OnlineFSReader.CONFIG_PROPERTY_NAME_INPUTDIRS, defaultValue = ".",
        description = "The name of the input dirs used to read data (multiple dirs are separated by |)."),
    @Property(
        name = OnlineFSReader.CONFIG_PROPERTY_NAME_IGNORE_UNKNOWN_RECORD_TYPES, defaultValue = "false",
        description = "Ignore unknown records? Aborts if encountered and value is false.") })
public class OnlineFSReader extends AbstractReaderPlugin implements IMonitoringRecordReceiver {

  /** The name of the output port delivering the record read by this plugin. */
  public static final String OUTPUT_PORT_NAME_RECORDS = "monitoringRecords";

  /** The name of the configuration determining the input directories for this plugin. */
  public static final String CONFIG_PROPERTY_NAME_INPUTDIRS = "inputDirs";

  /** The name of the configuration determining whether the reader ignores unknown record types or not. */
  public static final String CONFIG_PROPERTY_NAME_IGNORE_UNKNOWN_RECORD_TYPES = "ignoreUnknownRecordTypes";

  /** This dummy record can be send to the reader's record queue to mark the end of the current file. */
  public static final IMonitoringRecord EOF = new EmptyRecord();

  private final boolean ignoreUnknownRecordTypes;

  private final String[] inputDirs;

  private final PriorityQueue<IMonitoringRecord> recordQueue;

  private volatile boolean running = true;

  /**
   * Creates a new instance of this class using the given parameters.
   * 
   * @param configuration The configuration for this component.
   * @param projectContext The project context for this component.
   */
  public OnlineFSReader(final Configuration configuration, final IProjectContext projectContext) {

    super(configuration, projectContext);

    this.inputDirs = this.configuration.getStringArrayProperty(CONFIG_PROPERTY_NAME_INPUTDIRS);
    int nDirs = this.inputDirs.length;
    for (int i = 0; i < nDirs; i++) {
      this.inputDirs[i] = Configuration.convertToPath(this.inputDirs[i]);
    }
    if (nDirs == 0) {
      this.log.warn("The list of input dirs passed to the " + OnlineFSReader.class.getSimpleName() + " is empty");
      nDirs = 1;
    }
    this.recordQueue = new PriorityQueue<>(nDirs);
    this.ignoreUnknownRecordTypes = this.configuration
        .getBooleanProperty(CONFIG_PROPERTY_NAME_IGNORE_UNKNOWN_RECORD_TYPES);
  }

  /**
   * {@inheritDoc}
   */
  public void terminate(final boolean error) {

    this.log.info("Shutting down reader.");
    this.running = false;
  }

  /**
   * {@inheritDoc}
   */
  public boolean read() {

    // start all reader
    int notInitializesReaders = 0;
    for (final String inputDirFn : this.inputDirs) {
      // Make sure that white spaces in paths are handled correctly
      final File inputDir = new File(inputDirFn);
      final Thread readerThread;
      if (inputDir.isDirectory()) {
        readerThread = new Thread(new OnlineFSDirectoryReader(inputDir, this, this.ignoreUnknownRecordTypes));
      } else {
        this.log.warn("Invalid directory name (no Kieker log): " + inputDirFn);
        notInitializesReaders++;
        continue;
      }
      readerThread.setDaemon(true);
      readerThread.start();
    }
    // consume incoming records
    int readingReaders = this.inputDirs.length - notInitializesReaders;
    while (readingReaders > 0) {
      synchronized (this.recordQueue) { // with newMonitoringRecord()
        while (this.recordQueue.size() < readingReaders) {
          try {
            this.recordQueue.wait();
          } catch (final InterruptedException ex) {
            // ignore InterruptedException
          }
        }
      }
      final IMonitoringRecord record = this.recordQueue.remove();
      synchronized (record) { // with newMonitoringRecord()
        record.notifyAll();
      }
      if (record == EOF) { // NOPMD (CompareObjectsWithEquals)
        readingReaders--;
      } else {
        super.deliver(OUTPUT_PORT_NAME_RECORDS, record);
      }
    }
    return true;
  }

  /**
   * @return true if this is running; false otherwise
   */
  public boolean isRunning() {

    return this.running;
  }

  /**
   * {@inheritDoc}
   */
  public boolean newMonitoringRecord(final IMonitoringRecord record) {

    synchronized (record) { // with read()
      synchronized (this.recordQueue) { // with read()
        this.recordQueue.add(record);
        this.recordQueue.notifyAll();
      }
      try {
        record.wait();
      } catch (final InterruptedException ex) {
        // ignore InterruptedException
      }
    }
    return this.running;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Configuration getCurrentConfiguration() {

    final Configuration conf = new Configuration();
    conf.setProperty(CONFIG_PROPERTY_NAME_INPUTDIRS, Configuration.toProperty(this.inputDirs));
    conf.setProperty(CONFIG_PROPERTY_NAME_IGNORE_UNKNOWN_RECORD_TYPES,
        Boolean.toString(this.ignoreUnknownRecordTypes));
    return conf;
  }
}
