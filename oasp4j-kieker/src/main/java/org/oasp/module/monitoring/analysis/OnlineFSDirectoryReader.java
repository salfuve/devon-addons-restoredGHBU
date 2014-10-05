package org.oasp.module.monitoring.analysis;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import kieker.analysis.plugin.reader.filesystem.FSReader;
import kieker.common.exception.MonitoringRecordException;
import kieker.common.logging.Log;
import kieker.common.logging.LogFactory;
import kieker.common.record.AbstractMonitoringRecord;
import kieker.common.record.IMonitoringRecord;
import kieker.common.record.controlflow.OperationExecutionRecord;
import kieker.common.util.filesystem.BinaryCompressionMethod;
import kieker.common.util.filesystem.FSUtil;

/**
 * This class is an extended version of the {@link kieker.analysis.plugin.reader.filesystem.FSDirectoryReader}. It is
 * used by {@link OnlineFSReader} and has been modified to wait for new lines and files when all files in the directory
 * have been read.
 * 
 * @author jmensing
 * @version 1.0
 */
final class OnlineFSDirectoryReader implements Runnable {
  private static final Log LOG = LogFactory.getLog(OnlineFSDirectoryReader.class);

  private static final int POLL_NEW_LINES_INTERVAL = 1000; // frequency to poll new lines in the current file (in ms)

  private static final int POLL_NEW_FILES_INTERVAL = 3000; // frequency to poll new files in the directory (in ms)

  private static final int WAIT_FOR_MORE_MAPPING_INFO = 5000; // amount of ms to wait for additional lines in the
                                                              // mapping file

  String filePrefix = FSUtil.FILE_PREFIX; // NOPMD NOCS (package visible for inner class)

  private final Map<Integer, String> stringRegistry = new HashMap<>(); // NOPMD (no synchronization needed)

  private final OnlineFSReader recordReceiver;

  private final File inputDir;

  private boolean terminated;

  private final boolean ignoreUnknownRecordTypes;

  // This set of classes is used to filter only records of a specific type. The value null means all record types are
  // read.
  private final Set<String> unknownTypesObserved = new HashSet<>();

  /**
   * Creates a new instance of this class.
   * 
   * @param inputDir The File object for the input directory.
   * @param recordReceiver The receiver handling the records.
   * @param ignoreUnknownRecordTypes select only records of this type; null selects all
   */
  public OnlineFSDirectoryReader(final File inputDir, final OnlineFSReader recordReceiver,
      final boolean ignoreUnknownRecordTypes) {

    if ((inputDir == null) || !inputDir.isDirectory()) {
      throw new IllegalArgumentException("Invalid or empty inputDir");
    }
    this.inputDir = inputDir;
    this.recordReceiver = recordReceiver;
    this.ignoreUnknownRecordTypes = ignoreUnknownRecordTypes;
  }

  /**
   * After finding and validating a mapping file in {@link #inputDir}, this method reads monitoring records from text
   * files and passes them to the registered {@link #recordReceiver}. When all files have been read, it waits for new
   * lines in the current file and periodically checks {@link #inputDir} for new files to read.
   */
  @SuppressWarnings("javadoc")
  public final void run() {

    readMappingFile(); // must be the first line to set filePrefix!
    File currentFile = nextFileToRead(null);
    while (!this.terminated) {
      if (currentFile == null) {
        try {
          LOG.debug("No input file found. Waiting " + POLL_NEW_FILES_INTERVAL + " ms...");
          Thread.sleep(POLL_NEW_FILES_INTERVAL);
        } catch (InterruptedException e) {
          // ignore InterruptedException
        }
      } else {
        LOG.info("< Loading " + currentFile.getAbsolutePath());
        processNormalInputFile(currentFile);
      }
      currentFile = nextFileToRead(currentFile);
      if (!this.recordReceiver.isRunning())
        break;
    }
    LOG.info("Ending " + this.getClass() + ".");
    this.recordReceiver.newMonitoringRecord(FSReader.EOF);
  }

  /**
   * Reads the mapping file located in the directory.
   */
  @SuppressWarnings("null")
  private final void readMappingFile() {

    File mappingFile = null;
    while ((mappingFile == null || !mappingFile.exists()) && !this.terminated) {
      mappingFile = new File(this.inputDir.getAbsolutePath() + File.separator + FSUtil.MAP_FILENAME);
      if (!mappingFile.exists()) {
        // No mapping file found. Check whether we find a legacy tpmon.map file!
        mappingFile = new File(this.inputDir.getAbsolutePath() + File.separator + FSUtil.LEGACY_MAP_FILENAME);
        if (mappingFile.exists()) {
          LOG.info("Directory '" + this.inputDir + "' contains no file '" + FSUtil.MAP_FILENAME + "'. Found '"
              + FSUtil.LEGACY_MAP_FILENAME + "' ... switching to legacy mode");
          this.filePrefix = FSUtil.LEGACY_FILE_PREFIX;
        } else {
          try {
            Thread.sleep(POLL_NEW_FILES_INTERVAL);
          } catch (InterruptedException e) {
            // ignore InterruptedException
          }
          LOG.info("Directory '" + this.inputDir + "' contains no file '" + FSUtil.MAP_FILENAME
              + "'. Waiting for file '" + FSUtil.MAP_FILENAME + "' to be inserted.");
        }
      }
    }
    LOG.info("Found mapping file.");
    // found any kind of mapping file
    BufferedReader in = null;
    try {
      in = new BufferedReader(new InputStreamReader(new FileInputStream(mappingFile), FSUtil.ENCODING));
      String line;
      int validLinesRead = 0;
      int waitedFor = 0;
      while (!this.terminated) {
        if ((line = in.readLine()) != null) { // new line found
          if (line.length() == 0) {
            continue; // ignore empty lines
          }
          final int split = line.indexOf('=');
          if (split == -1) {
            LOG.error("Failed to parse line: {" + line + "} from file " + mappingFile.getAbsolutePath()
                + ". Each line must contain ID=VALUE pairs.");
            continue; // continue on errors
          }
          final String key = line.substring(0, split);
          final String value = FSUtil.decodeNewline(line.substring(split + 1));
          // the leading $ is optional
          final Integer id;
          try {
            id = Integer.valueOf((key.charAt(0) == '$') ? key.substring(1) : key); // NOCS
          } catch (final NumberFormatException ex) {
            LOG.error("Error reading mapping file, id must be integer", ex);
            continue; // continue on errors
          }
          final String prevVal = this.stringRegistry.put(id, value);
          if (prevVal != null) {
            LOG.error("Found addional entry for id='" + id + "', old value was '" + prevVal + "' new value is '"
                + value + "'");
          }
          validLinesRead++;
        } else { // no more lines found at the moment
          if (validLinesRead < 1) {
            LOG.warn("Mapping file seems not to contain any valid mapping information. Waiting for more lines...");
          } else if (waitedFor >= WAIT_FOR_MORE_MAPPING_INFO) {
            LOG.info("Mapping file accepted with " + validLinesRead + " valid entries.");
            break;
          }
          try {
            Thread.sleep(POLL_NEW_LINES_INTERVAL);
            waitedFor += POLL_NEW_LINES_INTERVAL;
          } catch (InterruptedException e) {
            // ignore InterruptedException
          }
        }
      }
    } catch (final IOException ex) {
      LOG.error("Error reading mapping file", ex);
    } finally {
      if (in != null) {
        try {
          in.close();
        } catch (final IOException ex) {
          LOG.error("Exception while closing input stream for mapping file", ex);
        }
      }
    }
  }

  /**
   * Reads the records contained in the given normal file and passes them to the registered {@link #recordReceiver}.
   * 
   * @param inputFile The input file which should be processed.
   */
  private final void processNormalInputFile(final File inputFile) {

    long hasNotPolledFilesFor = 0;
    boolean abortDueToUnknownRecordType = false;
    BufferedReader in = null;
    try {
      in = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), FSUtil.ENCODING));
      String line;
      while (!this.terminated) {
        if ((line = in.readLine()) != null) { // NOPMD (assign)
          line = line.trim();
          if (line.length() == 0) {
            continue; // ignore empty lines
          }
          IMonitoringRecord record = null;
          final String[] recordFields = line.split(";");
          try {
            if (recordFields[0].charAt(0) == '$') { // modern record
              if (recordFields.length < 2) {
                LOG.error("Illegal record format: " + line);
                continue; // skip this record
              }
              final Integer id = Integer.valueOf(recordFields[0].substring(1));
              final String classname = this.stringRegistry.get(id);
              if (classname == null) {
                LOG.error("Missing classname mapping for record type id " + "'" + id + "'");
                continue; // skip this record
              }
              Class<? extends IMonitoringRecord> clazz = null;
              try { // NOCS (nested try)
                clazz = AbstractMonitoringRecord.classForName(classname);
              } catch (final MonitoringRecordException ex) { // NOPMD (ExceptionAsFlowControl); need this to distinguish
                                                             // error by
                                                             // abortDueToUnknownRecordType
                if (!this.ignoreUnknownRecordTypes) {
                  // log message will be dumped in the Exception handler below
                  abortDueToUnknownRecordType = true;
                  throw new MonitoringRecordException("Failed to load record type " + classname, ex);
                } else if (!this.unknownTypesObserved.contains(classname)) {
                  LOG.error("Failed to load record type " + classname, ex); // log once for this type
                  this.unknownTypesObserved.add(classname);
                }
                continue; // skip this ignored record
              }
              final long loggingTimestamp = Long.parseLong(recordFields[1]);
              final int skipValues;
              // check for Kieker < 1.6 OperationExecutionRecords
              if ((recordFields.length == 11) && clazz.equals(OperationExecutionRecord.class)) {
                skipValues = 3;
              } else {
                skipValues = 2;
              }
              // Java 1.5 compatibility
              final String[] recordFieldsReduced = new String[recordFields.length - skipValues];
              System.arraycopy(recordFields, skipValues, recordFieldsReduced, 0, recordFields.length - skipValues);
              record = AbstractMonitoringRecord.createFromStringArray(clazz, recordFieldsReduced);
              // in Java 1.6 this could be simplified to
              // record = AbstractMonitoringRecord.createFromStringArray(clazz, Arrays.copyOfRange(recordFields,
              // skipValues, recordFields.length));
              record.setLoggingTimestamp(loggingTimestamp);
            } else { // legacy record
              final String[] recordFieldsReduced = new String[recordFields.length - 1];
              System.arraycopy(recordFields, 1, recordFieldsReduced, 0, recordFields.length - 1);
              record = AbstractMonitoringRecord.createFromStringArray(OperationExecutionRecord.class,
                  recordFieldsReduced);
            }
          } catch (final MonitoringRecordException ex) { // NOPMD (exception as flow control)
            if (abortDueToUnknownRecordType) {
              this.terminated = true; // at least it doesn't hurt to set it
              final IOException newEx = new IOException("Error processing line: " + line);
              newEx.initCause(ex);
              throw newEx; // NOPMD (cause is set above)
            } else {
              LOG.warn("Error processing line: " + line, ex);
              continue; // skip this record
            }
          }
          if (!this.recordReceiver.newMonitoringRecord(record)) {
            this.terminated = true;
            break; // we got the signal to stop processing
          }
        } else { // no more lines in this file
          LOG.debug("No more lines found in file " + inputFile.getAbsolutePath() + ". Waiting "
              + POLL_NEW_LINES_INTERVAL + "ms...");
          Thread.sleep(POLL_NEW_LINES_INTERVAL); // wait for new lines
          hasNotPolledFilesFor += POLL_NEW_LINES_INTERVAL;
          if (hasNotPolledFilesFor > POLL_NEW_FILES_INTERVAL) { // periodically also check for new files
            hasNotPolledFilesFor = 0;
            LOG.debug("Checking for new files in directory" + this.inputDir.getAbsolutePath() + ".");
            if (!this.recordReceiver.isRunning()) {
              this.terminated = true;
              break;
            }
            if (nextFileToRead(inputFile) == null) {
              LOG.error("File '" + inputFile.getAbsolutePath() + "' was removed while processing. Terminating "
                  + this.getClass().getName());
              this.terminated = true;
              break;
            }
            if (!nextFileToRead(inputFile).equals(inputFile)) {
              LOG.debug("New file found to read in directory " + this.inputDir.getAbsolutePath() + ".");
              break;
            }
          }
        }
      }

    } catch (final Exception ex) { // NOCS NOPMD (gonna catch them all)
      LOG.error("Error reading " + inputFile, ex);
    } finally {
      if (in != null) {
        try {
          in.close();
        } catch (final IOException ex) {
          LOG.error("Exception while closing input stream for processing input file", ex);
        }
      }
    }
  }

  /**
   * Determines which file is the next to read after the given file in {@link #inputDir} by lexicographical order.
   * 
   * @param currentFile The file that has last been read.
   * 
   * @return the next file after {@code currentFile} in lexicographical order; the very first file if
   *         {@code currentFile} is null; null If there are no files in {@link #inputDir} or if {@code currentFile} is
   *         not located in {@link #inputDir}; {@code currentFile} if it's the last one in lexicographical order
   */
  private File nextFileToRead(File currentFile) {

    // gather all files with the correct prefix and extension
    File[] inputFiles = this.inputDir.listFiles(new FileFilter() {

      public boolean accept(final File pathname) {

        final String name = pathname.getName();
        return pathname.isFile()
            && name.startsWith(OnlineFSDirectoryReader.this.filePrefix)
            && (name.endsWith(FSUtil.NORMAL_FILE_EXTENSION) || BinaryCompressionMethod.hasValidFileExtension(name));
      }
    });
    if (inputFiles == null) {
      LOG.error("Directory '" + this.inputDir + "' does not exist or an I/O error occured.");
      return null;
    }
    if (inputFiles.length == 0) {
      return null;
    }
    // sort all files lexicographically
    Arrays.sort(inputFiles, new Comparator<File>() {

      public final int compare(final File f1, final File f2) {

        return f1.compareTo(f2); // simplified (we expect no dirs!)
      }
    });
    if (currentFile == null)
      return inputFiles[0];
    // determine the next file after currentFile
    for (int i = 0; i < inputFiles.length; i++) {
      if (inputFiles[i].equals(currentFile)) {
        if (inputFiles.length - 1 == i)
          return inputFiles[i];
        else
          return inputFiles[i + 1];
      }
    }
    return null;
  }
}
