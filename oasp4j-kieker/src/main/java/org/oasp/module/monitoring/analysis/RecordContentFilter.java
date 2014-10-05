package org.oasp.module.monitoring.analysis;

import kieker.analysis.IProjectContext;
import kieker.analysis.plugin.annotation.InputPort;
import kieker.analysis.plugin.annotation.OutputPort;
import kieker.analysis.plugin.annotation.Plugin;
import kieker.analysis.plugin.annotation.Property;
import kieker.analysis.plugin.filter.AbstractFilterPlugin;
import kieker.common.configuration.Configuration;
import kieker.common.record.controlflow.OperationExecutionRecord;

/**
 * This filter plugin receives {@link OperationExecutionRecord} objects and relays them to one out of two output ports
 * depending on a configurable filter. After setting {@code type}, {@code compare}, {@code arrayIndex} and
 * {@code filterKey} the filter accesses the content of each {@link OperationExecutionRecord} object to determine if it
 * matches the filter requirement or not.
 * 
 * @author jmensing
 * @version 1.0
 */
@Plugin(
    description = "Filters OperationExecutionRecords by their content. The filter mechanism is configurable.",
    outputPorts = {
    @OutputPort(
        name = RecordContentFilter.OUTPUT_PORT_NAME_TRUE, eventTypes = { OperationExecutionRecord.class },
        description = "Forwards records matching the filter requirement."),
    @OutputPort(
        name = RecordContentFilter.OUTPUT_PORT_NAME_FALSE, eventTypes = { OperationExecutionRecord.class },
        description = "Forwards records not matching the filter requirement.") }, configuration = {
    @Property(
        name = RecordContentFilter.CONFIG_PROPERTY_NAME_TYPE, defaultValue = "STRING",
        description = "The data-type of the data used for filtering. " + "Use STRING, BOOLEAN or NUMBER."),
    @Property(
        name = RecordContentFilter.CONFIG_PROPERTY_NAME_COMPARE, defaultValue = "EQ",
        description = "The logic operator used for the filter mechanism. " + "Use GT, LT, GE, LE, EQ or NE."),
    @Property(
        name = RecordContentFilter.CONFIG_PROPERTY_NAME_ARRAY_INDEX, defaultValue = "0",
        description = "The index of the data used for filtering. "
            + "The Data is read from the array returned by the toArray-Method of the record."),
    @Property(
        name = RecordContentFilter.CONFIG_PROPERTY_NAME_FILTER_KEY, defaultValue = "",
        description = "The filter-key used for filtering."), })
public class RecordContentFilter extends AbstractFilterPlugin {

  /**
   * The name of the input port receiving new records.
   */
  public static final String INPUT_PORT_NAME_RECORDS = "recordsIn";

  /**
   * The name of the output port relaying records matching the filter requirement.
   */
  public static final String OUTPUT_PORT_NAME_TRUE = "recordsOutTrue";

  /**
   * The name of the output port relaying records not matching the filter requirement.
   */
  public static final String OUTPUT_PORT_NAME_FALSE = "recordsOutFalse";

  /**
   * The name of the property determining the data type of the data used by the filter.
   */
  public static final String CONFIG_PROPERTY_NAME_TYPE = "type";

  /**
   * The name of the property determining the compare operator used by the filter.
   */
  public static final String CONFIG_PROPERTY_NAME_COMPARE = "compare";

  /**
   * The name of the property determining the index of the data within the array representation of the record used by
   * the filter.
   */
  public static final String CONFIG_PROPERTY_NAME_ARRAY_INDEX = "arrayIndex";

  /**
   * The name of the property determining the filter key used by the filter.
   */
  public static final String CONFIG_PROPERTY_NAME_FILTER_KEY = "filterKey";

  private enum Type {
    BOOLEAN, NUMBER, STRING
  }

  /**
   * GT:>, LT:<, GE:>=, LE:<=, EQ:==, NE:!=
   */
  private enum Compare {
    GT, LT, GE, LE, EQ, NE;
  }

  /**
   * Determines the type of {@link #filterKey} and the data from {@link OperationExecutionRecord}.
   */
  private final Type type;

  /**
   * Determines which compare operator is used by the filter. For type = NUMBER any value is valid. For type = BOOLEAN
   * only EQ and NE are valid. For type = STRING GT and LE are not defined; EQ matches if strings are equal; NE matches
   * if strings are not equal; GE matches if {@link #filterKey} is contained; LT matches if {@link #filterKey} is not
   * contained.
   */
  private final Compare compare;

  /**
   * Determines at which index of the array representation of the {@link OperationExecutionRecord}, the data for
   * filtering is to be found.
   */
  private final int arrayIndex;

  /**
   * Determines the value, the data from the {@link OperationExecutionRecord} is compared to.
   */
  private final Object filterKey;

  /**
   * Creates a new instance of this class using the given parameters.
   * 
   * @param configuration The configuration for this component.
   * @param projectContext The project context for this component.
   */
  public RecordContentFilter(Configuration configuration, IProjectContext projectContext) {

    super(configuration, projectContext);
    Type tmpType;
    Compare tmpCompare;
    int tmpInt;
    Object tmpKey;

    try {
      tmpType = Type.valueOf(configuration.getStringProperty(CONFIG_PROPERTY_NAME_TYPE).trim().toUpperCase());
    } catch (IllegalArgumentException | NullPointerException exc) {
      this.log.warn("Plugin (" + getName() + ") received an invalid value for parameter 'type'.\n"
          + "Valid values are 'BOOLEAN', 'NUMBER' or 'STRING'.\n" + "Using default value 'STRING'.");
      tmpType = Type.STRING;
    }
    this.type = tmpType;

    try {
      tmpCompare = Compare.valueOf(configuration.getStringProperty(CONFIG_PROPERTY_NAME_COMPARE).trim()
          .toUpperCase());
    } catch (IllegalArgumentException | NullPointerException exc) {
      this.log.warn("Plugin (" + getName() + ") received an invalid value for parameter 'compare'.\n"
          + "Valid values are 'GT', 'LT', 'GE', 'LE', 'EQ' oder 'NE'.\n" + "Using default value 'EQ'.");
      tmpCompare = Compare.EQ;
    }

    tmpInt = -1;
    String tmpString = configuration.getStringProperty(CONFIG_PROPERTY_NAME_ARRAY_INDEX);
    try {
      tmpInt = Integer.parseInt(tmpString);
    } catch (NumberFormatException nfe) {
      this.log.warn("Plugin (" + getName()
          + ") received an invalid value for parameter 'arrayIndex'. Using default value '0'");
      tmpInt = 0;
    }
    this.arrayIndex = tmpInt;

    switch (this.type) {
      case BOOLEAN:
        tmpString = configuration.getStringProperty(CONFIG_PROPERTY_NAME_FILTER_KEY).trim().toLowerCase();
        if (!tmpString.equals(Boolean.TRUE.toString()) && !tmpString.equals(Boolean.FALSE.toString())) {
          this.log.warn("Plugin (" + getName() + ") received an invalid value for parameter 'filterKey'.\n"
              + "When 'type' 'BOOLEAN' has been chosen, only 'true' and 'false' are valid values.\n"
              + "Using default value 'true'.");
          tmpKey = Boolean.TRUE;
        } else {
          tmpKey = Boolean.parseBoolean(tmpString);
        }
        break;
      case NUMBER:
        double tmpDouble;
        tmpString = configuration.getStringProperty(CONFIG_PROPERTY_NAME_FILTER_KEY);
        try {
          tmpDouble = Double.parseDouble(tmpString);
        } catch (NumberFormatException | NullPointerException e) {
          this.log.warn("Plugin (" + getName() + ") received an invalid value for parameter 'filterKey'.\n"
              + "When 'type' 'NUMBER' has been chosen, only numbers are valid values.\n"
              + "Using default value 0.0.");
          tmpDouble = 0.0;
        }
        tmpKey = new Double(tmpDouble);
        break;
      default: // STRING
        tmpKey = configuration.getStringProperty(CONFIG_PROPERTY_NAME_FILTER_KEY);
        break;
    }
    this.filterKey = tmpKey;

    if (this.type == Type.BOOLEAN && (tmpCompare != Compare.EQ && tmpCompare != Compare.NE)) {
      this.log
          .warn("Plugin "
              + getName()
              + " received an invalid value for parameter 'compare'.\n"
              + "When 'type' 'BOOLEAN' has been chosen, only 'EQ' and 'NE' are valid values for parameter 'compare'.\n"
              + "Using default value 'EQ'.");
      tmpCompare = Compare.EQ;
    }
    if (this.type == Type.STRING && (tmpCompare == Compare.GT || tmpCompare == Compare.LE)) {
      this.log
          .warn("Plugin "
              + getName()
              + " received an invalid value for parameter 'compare'.\n"
              + "When 'type' 'STRING' has been chosen, only 'LT', 'GE', 'EQ' and 'NE' are valid values for 'compare'.\n"
              + "Using default value 'EQ'.");
      tmpCompare = Compare.EQ;
    }
    this.compare = tmpCompare;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Configuration getCurrentConfiguration() {

    final Configuration config = new Configuration();
    config.setProperty(CONFIG_PROPERTY_NAME_TYPE, this.type.name());
    config.setProperty(CONFIG_PROPERTY_NAME_COMPARE, this.compare.name());
    config.setProperty(CONFIG_PROPERTY_NAME_ARRAY_INDEX, Integer.toString(this.arrayIndex));
    switch (this.type) {
      case BOOLEAN:
        config.setProperty(CONFIG_PROPERTY_NAME_FILTER_KEY, Boolean.toString((Boolean) this.filterKey));
        break;
      case NUMBER:
        config.setProperty(CONFIG_PROPERTY_NAME_FILTER_KEY, Double.toString((Double) this.filterKey));
        break;
      default:
        config.setProperty(CONFIG_PROPERTY_NAME_FILTER_KEY, (String) this.filterKey);
        break;
    }
    return config;
  }

  /**
   * This method represents the input port for the incoming {@link OperationExecutionRecord} objects.
   * 
   * @param record The new incoming OperationExecutionRecord.
   */
  @InputPort(
      name = INPUT_PORT_NAME_RECORDS, eventTypes = { OperationExecutionRecord.class },
      description = "Receives OperationExecutionRecords to be filtered and forwarded accordingly.")
  public final void inputRecords(final OperationExecutionRecord record) {

    switch (this.type) {
      case BOOLEAN:
        if (!((record.toArray()[this.arrayIndex]) instanceof Boolean)) {
          this.log.warn("Data at arrayIndex [" + this.arrayIndex
              + "] cannot be compared, because it is not of Type '" + this.type + "' but of '"
              + (record.toArray()[this.arrayIndex]).getClass() + "'. The record is NOT being forwarded!");
          break;
        }
        switch (this.compare) {
          case EQ:
            if (((boolean) record.toArray()[this.arrayIndex]) == (boolean) (this.filterKey))
              super.deliver(OUTPUT_PORT_NAME_TRUE, record);
            else
              super.deliver(OUTPUT_PORT_NAME_FALSE, record);
            break;
          case NE:
            if (((boolean) record.toArray()[this.arrayIndex]) == (boolean) (this.filterKey))
              super.deliver(OUTPUT_PORT_NAME_FALSE, record);
            else
              super.deliver(OUTPUT_PORT_NAME_TRUE, record);
            break;
          default:
            this.log.warn("Operator '" + this.compare + "' is invalid for type '" + this.type
                + "'. The record is NOT being forwarded!");
            break;
        }
        break;
      case NUMBER:
        if (!((record.toArray()[this.arrayIndex]) instanceof Number)) {
          this.log.warn("Data at arrayIndex [" + this.arrayIndex
              + "] cannot be compared, because it is not of Type '" + this.type + "' but of '"
              + (record.toArray()[this.arrayIndex]).getClass() + "'. The record is NOT being forwarded!");
          break;
        }
        switch (this.compare) {
          case EQ:// equals
            if (((Number) record.toArray()[this.arrayIndex]).doubleValue() == (double) (this.filterKey))
              super.deliver(OUTPUT_PORT_NAME_TRUE, record);
            else
              super.deliver(OUTPUT_PORT_NAME_FALSE, record);
            break;
          case NE:// not equals
            if (((Number) record.toArray()[this.arrayIndex]).doubleValue() == (double) (this.filterKey))
              super.deliver(OUTPUT_PORT_NAME_FALSE, record);
            else
              super.deliver(OUTPUT_PORT_NAME_TRUE, record);
            break;
          case GE:// greater equals
            if (((Number) record.toArray()[this.arrayIndex]).doubleValue() < (double) (this.filterKey))
              super.deliver(OUTPUT_PORT_NAME_FALSE, record);
            else
              super.deliver(OUTPUT_PORT_NAME_TRUE, record);
            break;
          case GT:// greater than
            if (((Number) record.toArray()[this.arrayIndex]).doubleValue() <= (double) (this.filterKey))
              super.deliver(OUTPUT_PORT_NAME_FALSE, record);
            else
              super.deliver(OUTPUT_PORT_NAME_TRUE, record);
            break;
          case LE:// less equals
            if (((Number) record.toArray()[this.arrayIndex]).doubleValue() > (double) (this.filterKey))
              super.deliver(OUTPUT_PORT_NAME_FALSE, record);
            else
              super.deliver(OUTPUT_PORT_NAME_TRUE, record);
            break;
          case LT:// less than
            if (((Number) record.toArray()[this.arrayIndex]).doubleValue() >= (double) (this.filterKey))
              super.deliver(OUTPUT_PORT_NAME_FALSE, record);
            else
              super.deliver(OUTPUT_PORT_NAME_TRUE, record);
            break;
        }
        break;
      case STRING:
        if (!((record.toArray()[this.arrayIndex]) instanceof String)) {
          this.log.warn("Data at arrayIndex [" + this.arrayIndex
              + "] cannot be compared, because it is not of Type '" + this.type + "' but of '"
              + (record.toArray()[this.arrayIndex]).getClass() + "'. The record is NOT being forwarded!");
          break;
        }
        switch (this.compare) {
          case EQ:// exactly the same String
            if (((String) record.toArray()[this.arrayIndex]).equals(this.filterKey))
              super.deliver(OUTPUT_PORT_NAME_TRUE, record);
            else
              super.deliver(OUTPUT_PORT_NAME_FALSE, record);
            break;
          case NE:// not exactly the same String
            if (((String) record.toArray()[this.arrayIndex]).equals(this.filterKey))
              super.deliver(OUTPUT_PORT_NAME_FALSE, record);
            else
              super.deliver(OUTPUT_PORT_NAME_TRUE, record);
            break;
          case GE:// contains the String
            if (((String) record.toArray()[this.arrayIndex]).contains((String) this.filterKey))
              super.deliver(OUTPUT_PORT_NAME_TRUE, record);
            else
              super.deliver(OUTPUT_PORT_NAME_FALSE, record);
            break;
          case LT:// does not contain the String
            if (((String) record.toArray()[this.arrayIndex]).contains((String) this.filterKey))
              super.deliver(OUTPUT_PORT_NAME_FALSE, record);
            else
              super.deliver(OUTPUT_PORT_NAME_TRUE, record);
            break;
          default:
            this.log.error("Operator '" + this.compare + "' is invalid for type '" + this.type
                + "'. The record is NOT being forwarded!");
            break;
        }
        break;
    }
  }
}
