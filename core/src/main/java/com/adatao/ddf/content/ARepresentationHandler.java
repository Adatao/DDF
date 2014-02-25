/**
 * 
 */
package com.adatao.ddf.content;

import java.util.HashMap;

import com.adatao.ddf.ADDFFunctionalGroupHandler;
import com.adatao.ddf.DDF;

/**
 * @author ctn
 * 
 */
public abstract class ARepresentationHandler extends ADDFFunctionalGroupHandler implements IHandleRepresentations {

  public ARepresentationHandler(DDF theDDF) {
    super(theDDF);
  }

  // The various representations for our DDF
  protected HashMap<String, Object> mReps = new HashMap<String, Object>();

  protected String getKeyFor(Class<?> rowType) {
    return rowType.toString();
  }

  /**
   * Gets an existing representation for our {@link DDF} matching the given rowType, if any.
   * 
   * @param rowType
   *          the type of each unit or element in the DDF
   * 
   * @return null if no matching representation available
   */
  @Override
  public Object get(Class<?> rowType) {
    Object obj = mReps.get(getKeyFor(rowType));
    if (obj == null) {
      obj = this.getRepresentationImpl(rowType);
      this.add(obj, rowType);
    }
    if (obj == null) throw new UnsupportedOperationException();
    else return obj;
  }

  @Override
  public Object getDefault() {
    return getDefaultRepresentationImpl();
  }

  protected abstract Object getDefaultRepresentationImpl();

  /**
   * Resets (or clears) all representations
   */
  @Override
  public void reset() {
    mReps.clear();
  }

  /**
   *
   */
  protected abstract Object getRepresentationImpl(Class<?> rowType);

  /**
   * Sets a new and unique representation for our {@link DDF}, clearing out any existing ones
   * 
   * @param rowType
   *          the type of each element in the DDFManager
   */
  @Override
  public void set(Object data, Class<?> rowType) {
    this.reset();
    this.add(data, rowType);
  }

  /**
   * Adds a new and unique representation for our {@link DDF}, keeping any existing ones but
   * replacing the one that matches the given DDFManagerType, rowType tuple.
   * 
   * @param rowType
   *          the type of each element in the DDFManager
   */
  @Override
  public void add(Object data, Class<?> rowType) {
    mReps.put(getKeyFor(rowType), data);
  }

  /**
   * Removes a representation from the set of existing representations.
   * 
   * @param rowType
   */
  @Override
  public void remove(Class<?> rowType) {
    mReps.remove(getKeyFor(rowType));
  }

  /**
   * Returns a String list of current representations, useful for debugging
   */
  public String getList() {
    String result = "";
    int i = 1;

    for (String s : mReps.keySet()) {
      result += (i++) + ". key='" + s + "', value='" + mReps.get(s) + "'\n";
    }

    return result;
  }

  @Override
  public void cleanup() {
    mReps.clear();
    super.cleanup();
    uncacheAll();
  }

  @Override
  public void cacheAll() {
    // TODO Auto-generated method stub

  }

  @Override
  public void uncacheAll() {
    // TODO Auto-generated method stub

  }

  public enum RepresentationType {
    DEFAULT_TYPE, ARRAY_OBJECT, ARRAY_DOUBLE, ARRAY_LABELEDPOINT;
    public static RepresentationType fromString(String s) {
      if (s == null || s.length() == 0) return null;
      s = s.toUpperCase().trim();
      for (RepresentationType t : values()) {
        if (s.equals(t.name())) return t;
      }
      return null;
    }
  }
}
