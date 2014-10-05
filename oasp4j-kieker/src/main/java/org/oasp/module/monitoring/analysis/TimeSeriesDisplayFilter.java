package org.oasp.module.monitoring.analysis;

import java.util.concurrent.TimeUnit;

import kieker.analysis.IProjectContext;
import kieker.analysis.display.XYPlot;
import kieker.analysis.display.annotation.Display;
import kieker.analysis.plugin.annotation.InputPort;
import kieker.analysis.plugin.annotation.OutputPort;
import kieker.analysis.plugin.annotation.Plugin;
import kieker.analysis.plugin.annotation.Property;
import kieker.analysis.plugin.filter.AbstractFilterPlugin;
import kieker.common.configuration.Configuration;
import kieker.tools.opad.record.NamedDoubleTimeSeriesPoint;

/**
 * This filter plugin receives {@link NamedDoubleTimeSeriesPoint} objects on up to five different input ports to display
 * an according amount of series within a single 'XY-Plot' widget in the Kieker-WebGUI. In Addition it relays all
 * incoming objects to a single output port.
 * 
 * @author jmensing
 * @version 1.0
 */
@Plugin(
    description = "A filter displaying the NamedDoubleTimeSeriesPoints flowing through it as a XYPlot.",
    outputPorts = { @OutputPort(
        name = TimeSeriesDisplayFilter.OUTPUT_PORT_NAME_RELAYED_POINTS,
        eventTypes = { NamedDoubleTimeSeriesPoint.class }, description = "Provides each incoming point.") },
    configuration = {
    @Property(
        name = TimeSeriesDisplayFilter.CONFIG_PROPERTY_NAME_SERIES_A, defaultValue = "series-A",
        description = "The name of series associated with the first input port."),
    @Property(
        name = TimeSeriesDisplayFilter.CONFIG_PROPERTY_NAME_SERIES_B, defaultValue = "series-B",
        description = "The name of series associated with the second input port."),
    @Property(
        name = TimeSeriesDisplayFilter.CONFIG_PROPERTY_NAME_SERIES_C, defaultValue = "series-C",
        description = "The name of series associated with the third input port."),
    @Property(
        name = TimeSeriesDisplayFilter.CONFIG_PROPERTY_NAME_SERIES_D, defaultValue = "series-D",
        description = "The name of series associated with the fourth input port."),
    @Property(
        name = TimeSeriesDisplayFilter.CONFIG_PROPERTY_NAME_SERIES_E, defaultValue = "series-E",
        description = "The name of series associated with the fifth input port."),
    @Property(
        name = TimeSeriesDisplayFilter.CONFIG_PROPERTY_NAME_MAX_ENTRIES, defaultValue = "1000",
        description = "The maximum number of points displayed per series."),
    @Property(
        name = TimeSeriesDisplayFilter.CONFIG_PROPERTY_NAME_DISPLAY_TIMEUNIT, defaultValue = "SECONDS",
        description = "The time unit to display within the graph-widget. (X-Axis only!)") })
public final class TimeSeriesDisplayFilter extends AbstractFilterPlugin {

  /**
   * The name of the input port receiving the incoming points for the first series.
   */
  public static final String INPUT_PORT_NAME_SERIES_A = "inputSeriesA";

  /**
   * The name of the input port receiving the incoming points for the second series.
   */
  public static final String INPUT_PORT_NAME_SERIES_B = "inputSeriesB";

  /**
   * The name of the input port receiving the incoming points for the third series.
   */
  public static final String INPUT_PORT_NAME_SERIES_C = "inputSeriesC";

  /**
   * The name of the input port receiving the incoming points for the fourth series.
   */
  public static final String INPUT_PORT_NAME_SERIES_D = "inputSeriesD";

  /**
   * The name of the input port receiving the incoming points for the fifth series.
   */
  public static final String INPUT_PORT_NAME_SERIES_E = "inputSeriesE";

  /**
   * The name of the output port passing the incoming points.
   */
  public static final String OUTPUT_PORT_NAME_RELAYED_POINTS = "relayedPoints";

  /**
   * The name of the property determining the name for the first series.
   */
  public static final String CONFIG_PROPERTY_NAME_SERIES_A = "seriesA";

  /**
   * The name of the property determining the name for the second series.
   */
  public static final String CONFIG_PROPERTY_NAME_SERIES_B = "seriesB";

  /**
   * The name of the property determining the name for the third series.
   */
  public static final String CONFIG_PROPERTY_NAME_SERIES_C = "seriesC";

  /**
   * The name of the property determining the name for the fourth series.
   */
  public static final String CONFIG_PROPERTY_NAME_SERIES_D = "seriesD";

  /**
   * The name of the property determining the name for the fifth series.
   */
  public static final String CONFIG_PROPERTY_NAME_SERIES_E = "seriesE";

  /**
   * The name of the property determining the maximum number of entries displayed in the graph (per series).
   */
  public static final String CONFIG_PROPERTY_NAME_MAX_ENTRIES = "maxEntries";

  /**
   * The name of the property determining the time unit to display on the graph (x-axis only).
   */
  public static final String CONFIG_PROPERTY_NAME_DISPLAY_TIMEUNIT = "displayTimeUnit";

  private final String[] seriesNames;

  private final int maxEntries;

  private final TimeUnit displayTimeUnit;

  private final XYPlot xyPlot;

  private long firstTimestamp = -1;

  /**
   * Creates a new instance of this class using the given parameters.
   * 
   * @param configuration The configuration for this component.
   * @param projectContext The project context for this component.
   */
  public TimeSeriesDisplayFilter(final Configuration configuration, final IProjectContext projectContext) {

    super(configuration, projectContext);
    String tmpString;
    TimeUnit tmpTimeUnit;
    int tmpInt;

    try {
      tmpTimeUnit = TimeUnit.valueOf(configuration.getStringProperty(CONFIG_PROPERTY_NAME_DISPLAY_TIMEUNIT).trim()
          .toUpperCase());
    } catch (IllegalArgumentException | NullPointerException exc) {
      tmpTimeUnit = TimeUnit.DAYS;
    }
    if (tmpTimeUnit == TimeUnit.DAYS || tmpTimeUnit == TimeUnit.HOURS || tmpTimeUnit == TimeUnit.MINUTES) {
      this.log.warn("Plugin (" + getName() + ") received an invalid value for parameter 'timestampDisplayUnit'.\n"
          + "Valid values are 'SECONDS', 'MILLISECONDS', 'MICROSECONDS' and 'NANOSECONDS'.\n"
          + "Using default value 'SECONDS'.");
      tmpTimeUnit = TimeUnit.SECONDS;
    }
    this.displayTimeUnit = tmpTimeUnit;

    tmpInt = -1;
    tmpString = configuration.getStringProperty(CONFIG_PROPERTY_NAME_MAX_ENTRIES);
    try {
      tmpInt = Integer.parseInt(tmpString);
    } catch (NumberFormatException nfe) {
      this.log.warn("Plugin (" + getName() + ") received an invalid value for parameter 'maxEntries'.\n"
          + "Only positive integers are valid.\n" + "Using default value 100.");
      tmpInt = -1;
    }
    if (tmpInt < 1)
      this.maxEntries = 100;
    else
      this.maxEntries = tmpInt;
    this.xyPlot = new XYPlot(this.maxEntries);

    this.seriesNames = new String[5];
    this.seriesNames[0] = configuration.getStringProperty(CONFIG_PROPERTY_NAME_SERIES_A);
    this.seriesNames[1] = configuration.getStringProperty(CONFIG_PROPERTY_NAME_SERIES_B);
    this.seriesNames[2] = configuration.getStringProperty(CONFIG_PROPERTY_NAME_SERIES_C);
    this.seriesNames[3] = configuration.getStringProperty(CONFIG_PROPERTY_NAME_SERIES_D);
    this.seriesNames[4] = configuration.getStringProperty(CONFIG_PROPERTY_NAME_SERIES_E);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final Configuration getCurrentConfiguration() {

    Configuration config = new Configuration();
    config.setProperty(CONFIG_PROPERTY_NAME_DISPLAY_TIMEUNIT, "" + this.displayTimeUnit);
    config.setProperty(CONFIG_PROPERTY_NAME_MAX_ENTRIES, "" + this.maxEntries);
    config.setProperty(CONFIG_PROPERTY_NAME_SERIES_A, "" + this.seriesNames[0]);
    config.setProperty(CONFIG_PROPERTY_NAME_SERIES_B, "" + this.seriesNames[1]);
    config.setProperty(CONFIG_PROPERTY_NAME_SERIES_C, "" + this.seriesNames[2]);
    config.setProperty(CONFIG_PROPERTY_NAME_SERIES_D, "" + this.seriesNames[3]);
    config.setProperty(CONFIG_PROPERTY_NAME_SERIES_E, "" + this.seriesNames[4]);
    return config;
  }

  /**
   * This method represents the input port for the first series.
   * 
   * @param point The next point.
   */
  @InputPort(
      name = INPUT_PORT_NAME_SERIES_A, eventTypes = { NamedDoubleTimeSeriesPoint.class },
      description = "Receives incoming points to be displayed and forwarded")
  public final void inputEventA(final NamedDoubleTimeSeriesPoint point) {

    updateDisplaysAndDeliver(point, this.seriesNames[0]);
  }

  /**
   * This method represents the input port for the second series.
   * 
   * @param point The next point.
   */
  @InputPort(
      name = INPUT_PORT_NAME_SERIES_B, eventTypes = { NamedDoubleTimeSeriesPoint.class },
      description = "Receives incoming points to be displayed and forwarded")
  public final void inputEventB(final NamedDoubleTimeSeriesPoint point) {

    updateDisplaysAndDeliver(point, this.seriesNames[1]);
  }

  /**
   * This method represents the input port for the third series.
   * 
   * @param point The next point.
   */
  @InputPort(
      name = INPUT_PORT_NAME_SERIES_C, eventTypes = { NamedDoubleTimeSeriesPoint.class },
      description = "Receives incoming points to be displayed and forwarded")
  public final void inputEventC(final NamedDoubleTimeSeriesPoint point) {

    updateDisplaysAndDeliver(point, this.seriesNames[2]);
  }

  /**
   * This method represents the input port for the fourth series.
   * 
   * @param point The next point.
   */
  @InputPort(
      name = INPUT_PORT_NAME_SERIES_D, eventTypes = { NamedDoubleTimeSeriesPoint.class },
      description = "Receives incoming points to be displayed and forwarded")
  public final void inputEventD(final NamedDoubleTimeSeriesPoint point) {

    updateDisplaysAndDeliver(point, this.seriesNames[3]);
  }

  /**
   * This method represents the input port for the fifth series.
   * 
   * @param point The next point.
   */
  @InputPort(
      name = INPUT_PORT_NAME_SERIES_E, eventTypes = { NamedDoubleTimeSeriesPoint.class },
      description = "Receives incoming points to be displayed and forwarded")
  public final void inputEventE(final NamedDoubleTimeSeriesPoint point) {

    updateDisplaysAndDeliver(point, this.seriesNames[4]);
  }

  private void updateDisplaysAndDeliver(NamedDoubleTimeSeriesPoint point, String name) {

    if (this.firstTimestamp == -1) {
      this.firstTimestamp = point.getTime();
      this.xyPlot.setEntry(name, 0, point.getValue());
    } else {
      this.xyPlot.setEntry(name, this.displayTimeUnit.convert((point.getTime() - this.firstTimestamp),
          this.recordsTimeUnitFromProjectContext), point.getValue());
    }
    super.deliver(OUTPUT_PORT_NAME_RELAYED_POINTS, point);
  }

  /**
   * @return the current {@link XYPlot} object
   */
  @Display(name = "XYPlot Display")
  public final XYPlot xyPlotDisplay() {

    return this.xyPlot;
  }

}
