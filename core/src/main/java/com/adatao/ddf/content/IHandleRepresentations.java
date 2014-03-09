package com.adatao.ddf.content;

import com.adatao.ddf.exception.DDFException;
import com.adatao.ddf.misc.IHandleDDFFunctionalGroup;

/**
 * <p>
 * Handles the underlying, implementation-specific representation(s) of a DDF. Note that a single
 * DDF may have simultaneously multiple representations, all of which are expected to be equivalent
 * in terms of relevant content value. Contrast this with, e.g., {@link IHandleViews}, which results
 * in entirely new DDFs with different columns or rows. These new DDFs are logically referred to as
 * new "views".
 * </p>
 * <p>
 * For example, a DDF may initially be represented as an RDD[TablePartition]. But in order to send
 * it to a machine-learning algorithm, an RDD[LabeledPoint] representation is needed. An
 * {@link IHandleRepresentations} is expected to help perform this transformation, and it may still
 * hold on to both representations, until they are somehow invalidated.
 * </p>
 * <p>
 * In another example, a DDF may be mutated in a transformation from one RDD to a new RDD. It should
 * automatically keep track of the reference to the new RDD, so that to the client of DDF, it
 * properly appears to have been mutated. This makes it possible to for DDF to support R-style
 * replacement functions. The underlying RDDs are of course immutable.
 * </p>
 * <p>
 * As a final example, a DDF may be set up to accept incoming streaming data. The client has a
 * constant reference to this DDF, but the underlying data is constantly being updated, such that
 * each query against this DDF would result in different data being returned/aggregated.
 * </p>
 * 
 */
public interface IHandleRepresentations extends IHandleDDFFunctionalGroup {

  /**
   * Retrieves a representation of type type.
   * 
   * @param unitType
   * @return a pointer to the specified
   */
  public Object get(Class<?> containerType, Class<?> unitType);

  /**
   * Retrieves a default representation for this specific engine
   * @param unitType
   * @return
   */
  public Object get(Class<?> unitType) throws DDFException;
  /**
   * Clears out all current representations.
   */
  public void reset();

  /**
   * Clears all current representations and set it to the supplied one.
   * 
   * @param data
   */
  public void set(Object data, Class<?> containerType, Class<?> unitType);

  /**
   * Adds a representation to the set of existing representations.
   * 
   * @param data
   */
  public void add(Object data, Class<?> containerType, Class<?> unitType);

  /**
   * Removes a representation from the set of existing representations.
   * 
   * @param dataType
   */

  public void remove(Class<?> containerType, Class<?> dataType);

  /**
   * Cache all representations, e.g., in an in-memory context
   */
  public void cacheAll();

  /**
   * Uncache all representations, e.g., in an in-memory context
   */
  public void uncacheAll();

  /**
   * Returns the default representation for this engine
   * 
   * @return
   */
  public Object getDefault();

  public Class<?> getDefaultUnitType();

  public Class<?> getDefaultContainerType();

  public Class<?> getDefaultColumnType();
}
