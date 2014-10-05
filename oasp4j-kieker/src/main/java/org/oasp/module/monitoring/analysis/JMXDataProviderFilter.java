package org.oasp.module.monitoring.analysis;

import java.lang.management.ManagementFactory;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import kieker.analysis.IProjectContext;
import kieker.analysis.plugin.annotation.InputPort;
import kieker.analysis.plugin.annotation.OutputPort;
import kieker.analysis.plugin.annotation.Plugin;
import kieker.analysis.plugin.annotation.Property;
import kieker.analysis.plugin.filter.AbstractFilterPlugin;
import kieker.common.configuration.Configuration;
import kieker.tools.opad.record.NamedDoubleTimeSeriesPoint;

/**
 * This filter provides the value field of the last relayed {@link NamedDoubleTimeSeriesPoint} object to JMX via an
 * MBean interface.
 * 
 * 
 * @author jmensing
 * @version 1.0
 */
@Plugin(description = "A filter to provide continuos data for the JMX interface.", outputPorts = { @OutputPort(
    name = JMXDataProviderFilter.OUTPUT_PORT_NAME_RELAYED_DATA, eventTypes = { NamedDoubleTimeSeriesPoint.class },
    description = "Provides the incoming data.") }, configuration = {
@Property(
    name = JMXDataProviderFilter.CONFIG_PROPERTY_NAME_VALUE_NAME, defaultValue = "<unknown-data>",
    description = "The name for the value to display in JMX tools."),
@Property(
    name = JMXDataProviderFilter.CONFIG_PROPERTY_NAME_DOMAIN,
    defaultValue = "org.oasp.module.monitoring.analysis",
    description = "The domain name for this class to display in JMX tools.") })
public class JMXDataProviderFilter extends AbstractFilterPlugin implements JMXDataProviderFilterMBean {

  /**
   * The name of the input port receiving the incoming data.
   */
  public static final String INPUT_PORT_NAME_DATA = "inputData";

  /**
   * The name of the output port passing the incoming data.
   */
  public static final String OUTPUT_PORT_NAME_RELAYED_DATA = "relayedData";

  /**
   * The name of the property determining the name of the value provided by this filter.
   */
  public static final String CONFIG_PROPERTY_NAME_VALUE_NAME = "valueName";

  /**
   * The name of the property determining the domain for this MBean.
   */
  public static final String CONFIG_PROPERTY_NAME_DOMAIN = "domain";

  private double value = -1;

  private final String valueName;

  private String domain;

  /**
   * Creates a new instance of this class using the given parameters.
   * 
   * @param configuration The configuration for this component.
   * @param projectContext The project context for this component.
   */
  public JMXDataProviderFilter(Configuration configuration, IProjectContext projectContext) {

    super(configuration, projectContext);
    this.valueName = configuration.getStringProperty(CONFIG_PROPERTY_NAME_VALUE_NAME);
    this.domain = configuration.getStringProperty(CONFIG_PROPERTY_NAME_DOMAIN);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Configuration getCurrentConfiguration() {

    Configuration config = new Configuration();
    config.setProperty(CONFIG_PROPERTY_NAME_VALUE_NAME, this.valueName);
    config.setProperty(CONFIG_PROPERTY_NAME_DOMAIN, this.domain);
    return config;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getValueName() {

    return this.valueName;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public double getValue() {

    return this.value;
  }

  /**
   * This method represents the input port of this filter.
   * 
   * @param tsp The next time-series-point.
   */
  @InputPort(
      name = INPUT_PORT_NAME_DATA,
      eventTypes = { NamedDoubleTimeSeriesPoint.class },
      description = "Receives objects of type NamedDoubleTimeSeriesPoint, provides them for JMX and forwards them.")
  public final void inputEvent(final NamedDoubleTimeSeriesPoint tsp) {

    this.value = tsp.getDoubleValue();
    super.deliver(OUTPUT_PORT_NAME_RELAYED_DATA, tsp);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean init() {

    MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
    try {
      ObjectName name = new ObjectName(this.domain + ":type=JMXDataProviderFilter, valueName=" + this.valueName);
      mbs.registerMBean(this, name);
    } catch (InstanceAlreadyExistsException e) {
      this.log.warn(getPluginName() + " instance already exists.");
      return true;
    } catch (Exception e) {
      this.log.error(getPluginName() + ".init() failed. Exception: \n" + e.fillInStackTrace());
      return false;
    }
    return true;
  }
}
