package org.oasp.module.monitoring.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import kieker.analysis.IProjectContext;
import kieker.analysis.plugin.annotation.InputPort;
import kieker.analysis.plugin.annotation.OutputPort;
import kieker.analysis.plugin.annotation.Plugin;
import kieker.analysis.plugin.annotation.Property;
import kieker.analysis.plugin.filter.AbstractFilterPlugin;
import kieker.common.configuration.Configuration;
import kieker.tools.opad.record.NamedDoubleRecord;
import kieker.tools.opad.record.NamedDoubleTimeSeriesPoint;

/**
 * This filter plugin receives {@link NamedDoubleRecord} objects and aggregates their values over a configurable time
 * window. Periodically amount, minimum, maximum, average and median values are calculated and passed to five different
 * output ports.
 * 
 * @author jmensing
 * @version 1.0
 */
@Plugin(
    description = "A filter that processes NamedDoubleRecords, aggregates and processes them as NamedDoubleTimeSeriesPoint.",
    outputPorts = {
    @OutputPort(
        name = RecordResponseTimeProcessFilter.OUTPUT_PORT_NAME_COUNT,
        eventTypes = { NamedDoubleTimeSeriesPoint.class },
        description = "Provides the number of records being processed."),
    @OutputPort(
        name = RecordResponseTimeProcessFilter.OUTPUT_PORT_NAME_MIN,
        eventTypes = { NamedDoubleTimeSeriesPoint.class },
        description = "Provides the minimum response time of all records being processed."),
    @OutputPort(
        name = RecordResponseTimeProcessFilter.OUTPUT_PORT_NAME_MAX,
        eventTypes = { NamedDoubleTimeSeriesPoint.class },
        description = "Provides the maximum response time of all records being processed."),
    @OutputPort(
        name = RecordResponseTimeProcessFilter.OUTPUT_PORT_NAME_MEDIAN,
        eventTypes = { NamedDoubleTimeSeriesPoint.class },
        description = "Provides the median response time of all records being processed."),
    @OutputPort(
        name = RecordResponseTimeProcessFilter.OUTPUT_PORT_NAME_AVG,
        eventTypes = { NamedDoubleTimeSeriesPoint.class },
        description = "Provides the avg of response time of all records being processed.") },
    configuration = {
    @Property(
        name = RecordResponseTimeProcessFilter.CONFIG_PROPERTY_NAME_MAX_RECORDS, defaultValue = "100000",
        description = "The maximum number of records stored and taken into account for processing."),
    @Property(
        name = RecordResponseTimeProcessFilter.CONFIG_PROPERTY_NAME_TIME_WINDOW,
        defaultValue = "3600",
        description = "The amount of seconds into the past determining the time window in which records are considered for the calculations."),
    @Property(
        name = RecordResponseTimeProcessFilter.CONFIG_PROPERTY_NAME_DELIVER_PERIOD, defaultValue = "5000",
        description = "Determines the frequency results are forwarded in milliseconds.") })
public class RecordResponseTimeProcessFilter extends AbstractFilterPlugin {
  /**
   * The name of the input port receiving new records.
   */
  public static final String INPUT_PORT_NAME_RECORDS = "inputRecords";

  /**
   * The name of the output port providing the number of records being processed.
   */
  public static final String OUTPUT_PORT_NAME_COUNT = "outputCount";

  /**
   * The name of the output port providing the minimum response time of all records being processed.
   */
  public static final String OUTPUT_PORT_NAME_MIN = "outputMin";

  /**
   * The name of the output port providing the maximum response time of all records being processed.
   */
  public static final String OUTPUT_PORT_NAME_MAX = "outputMax";

  /**
   * The name of the output port providing the median response time of all records being processed.
   */
  public static final String OUTPUT_PORT_NAME_MEDIAN = "outputMedian";

  /**
   * The name of the output port providing the average response time of all records being processed.
   */
  public static final String OUTPUT_PORT_NAME_AVG = "outputAvg";

  /**
   * The name of the property determining the maximum number of records taken into account for processing.
   */
  public static final String CONFIG_PROPERTY_NAME_MAX_RECORDS = "maxRecords";

  /**
   * The name of the property determining time window in which records are processed (in seconds).
   */
  public static final String CONFIG_PROPERTY_NAME_TIME_WINDOW = "timeWindowInS";

  /**
   * The name of the property determining the frequency for deliveries (in milliseconds).
   */
  public static final String CONFIG_PROPERTY_NAME_DELIVER_PERIOD = "deliverPeriodInMS";

  private final int maxRecords;

  private final int timeWindow; // in seconds

  private final int deliverPeriod; // in milliseconds

  private long lastDeliveryTimeStamp = -1;

  private List<NamedDoubleRecord> records = new ArrayList<>();

  /**
   * Creates a new instance of this class using the given parameters.
   * 
   * @param configuration The configuration for this component.
   * @param projectContext The project context for this component.
   */
  public RecordResponseTimeProcessFilter(Configuration configuration, IProjectContext projectContext) {

    super(configuration, projectContext);
    String tmpString = configuration.getStringProperty(CONFIG_PROPERTY_NAME_MAX_RECORDS);
    int tmpInt = -1;
    try {
      tmpInt = Integer.parseInt(tmpString);
    } catch (NumberFormatException nfe) {
      tmpInt = -1;
    }
    if (tmpInt <= 0) {
      this.log.warn("Plugin (" + getName() + ") received an invalid value for parameter 'maxRecords'.\n"
          + "Only positive integers are valid.\n" + "Using default value 100000.");
      this.maxRecords = 100000;
    } else {
      this.maxRecords = tmpInt;
    }
    tmpString = configuration.getStringProperty(CONFIG_PROPERTY_NAME_TIME_WINDOW);
    tmpInt = -1;
    try {
      tmpInt = Integer.parseInt(tmpString);
    } catch (NumberFormatException nfe) {
      tmpInt = -1;
    }
    if (tmpInt <= 0) {
      this.log.warn("Plugin (" + getName() + ") received an invalid value for parameter 'timeWindow'.\n"
          + "Only positive integers are valid.\n" + "Using default value 3600.");
      this.timeWindow = 3600;
    } else {
      this.timeWindow = tmpInt;
    }
    tmpString = configuration.getStringProperty(CONFIG_PROPERTY_NAME_DELIVER_PERIOD);
    tmpInt = -1;
    try {
      tmpInt = Integer.parseInt(tmpString);
    } catch (NumberFormatException nfe) {
      tmpInt = -1;
    }
    if (tmpInt <= 0) {
      this.log.warn("Plugin (" + getName() + ") received an invalid value for parameter 'deliverPeriod'.\n"
          + "Only positive integers are valid.\n" + "Using default value 5000.");
      this.deliverPeriod = 5000;
    } else {
      this.deliverPeriod = tmpInt;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Configuration getCurrentConfiguration() {

    Configuration config = new Configuration();
    config.setProperty(CONFIG_PROPERTY_NAME_MAX_RECORDS, Integer.toString(this.maxRecords));
    config.setProperty(CONFIG_PROPERTY_NAME_TIME_WINDOW, Integer.toString(this.timeWindow));
    config.setProperty(CONFIG_PROPERTY_NAME_DELIVER_PERIOD, Integer.toString(this.deliverPeriod));
    return config;
  }

  /**
   * This method represents the input port for the incoming {@link NamedDoubleRecord} objects.
   * 
   * @param record The new incoming record. It's {@link NamedDoubleRecord#getTimestamp()} must return a value in
   *        nanoseconds!
   */
  @InputPort(
      name = INPUT_PORT_NAME_RECORDS, eventTypes = { NamedDoubleRecord.class },
      description = "Receives NamedDoubleRecords and forwards aggregated values as NamedDoubleTimeSeriesPoints.")
  public final void inputRecords(final NamedDoubleRecord record) {

    if (record.getValue() < 0.0) // ignore invalid response times
      return;
    this.records.add(record);
    if (this.lastDeliveryTimeStamp == -1) {
      this.lastDeliveryTimeStamp = record.getTimestamp();
    } else {
      if (record.getTimestamp() - this.lastDeliveryTimeStamp > (long) this.deliverPeriod * 1000000) {
        doDeliver(record);
        this.lastDeliveryTimeStamp = record.getTimestamp();
      }
    }
  }

  private void doDeliver(final NamedDoubleRecord currentRecord) {

    // remove records outside the chosen time window
    Iterator<NamedDoubleRecord> iter = this.records.iterator(); // required for removing while iterating
    while (iter.hasNext()) {
      NamedDoubleRecord rec = iter.next();
      if (currentRecord.getTimestamp() - rec.getTimestamp() > (long) this.timeWindow * 1000000000) {
        iter.remove();
      }
    }
    // remove records exceeding maxRecords
    int tooManyRecords = this.records.size() - this.maxRecords;
    if (tooManyRecords > 0) {
      for (int i = tooManyRecords; i > 0; i--) {
        this.records.remove(0);
      }
      this.log.warn("Plugin '" + getName() + "' discarded " + tooManyRecords
          + " records due too exceedance of property 'maxRecords=" + this.maxRecords + "'");
    }
    // calculate min, max, median and average response times
    double min = Double.MAX_VALUE;
    double max = -1;
    double median = -1;
    double sum = 0;
    double avg = -1;
    int count = 0;
    double[] responseTimes = new double[this.records.size()];
    int i = 0;
    for (NamedDoubleRecord rec : this.records) {
      if (rec.getValue() < min)
        min = rec.getValue();
      if (rec.getValue() > max)
        max = rec.getValue();
      sum += rec.getValue();
      responseTimes[i] = rec.getValue();
      i++;
    }
    count = this.records.size();
    avg = sum / count;
    Arrays.sort(responseTimes);
    median = responseTimes[count / 2];

    super.deliver(OUTPUT_PORT_NAME_COUNT, new NamedDoubleTimeSeriesPoint(this.records.get(this.records.size() - 1)
        .getTimestamp(), (double) count, "count"));
    super.deliver(OUTPUT_PORT_NAME_MAX, new NamedDoubleTimeSeriesPoint(this.records.get(this.records.size() - 1)
        .getTimestamp(), max, "max"));
    super.deliver(OUTPUT_PORT_NAME_MIN, new NamedDoubleTimeSeriesPoint(this.records.get(this.records.size() - 1)
        .getTimestamp(), min, "min"));
    super.deliver(OUTPUT_PORT_NAME_AVG, new NamedDoubleTimeSeriesPoint(this.records.get(this.records.size() - 1)
        .getTimestamp(), avg, "avg"));
    super.deliver(OUTPUT_PORT_NAME_MEDIAN, new NamedDoubleTimeSeriesPoint(this.records
        .get(this.records.size() - 1).getTimestamp(), median, "median"));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void terminate(final boolean error) {

    // deliver a last time before terminating, so all records are considered
    doDeliver(this.records.get(this.records.size() - 1));
    super.terminate(error);
  }
}
