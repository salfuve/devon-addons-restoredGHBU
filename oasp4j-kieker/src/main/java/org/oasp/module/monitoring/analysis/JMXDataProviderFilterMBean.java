package org.oasp.module.monitoring.analysis;

/**
 * This is the MBean interface required by {@link JMXDataProviderFilter} to provide data to JMX.
 * 
 * @author jmensing
 * @version 1.0
 */
public interface JMXDataProviderFilterMBean {

  /**
   * @return The name of the value provided by this filter for JMX.
   */
  public String getValueName();

  /**
   * @return The value provided by this filter for JMX.
   */
  public double getValue();

}