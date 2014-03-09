/**
 * 
 */
package com.adatao.ddf.content;


import java.util.Iterator;
import java.util.List;
import com.adatao.ddf.misc.ADDFFunctionalGroupHandler;
import com.adatao.ddf.DDF;
import com.adatao.ddf.exception.DDFException;
import com.google.common.base.Joiner;

/**
 * 
 */
public class ViewHandler extends ADDFFunctionalGroupHandler implements IHandleViews {

  public ViewHandler(DDF theDDF) {
    super(theDDF);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> Iterator<T> getRowIterator(Class<T> rowType) {
    if (rowType == null) rowType = (Class<T>) this.getDDF().getRepresentationHandler().getDefaultUnitType();
    Class<?> containerType = this.getDDF().getRepresentationHandler().getDefaultContainerType();
    Object repr = this.getDDF().getRepresentationHandler().get(containerType, rowType);
    return (repr instanceof Iterable<?>) ? ((Iterable<T>) repr).iterator() : null;
  }

  @Override
  public DDF getRandomSample(int numSamples, boolean withReplacement, int seed) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public DDF getRandomSample(double percent, boolean withReplacement, int seed) {
    // TODO Auto-generated method stub
    return null;
  }


  public static class ElementIterator<C> implements Iterator<C> {

    private Iterator<?> mRowIterator;
    private int mColumnIndex;


    public ElementIterator(Iterator<?> rowIterator, int columnIndex) {
      mRowIterator = rowIterator;
      mColumnIndex = columnIndex;
    }

    @Override
    public boolean hasNext() {
      return mRowIterator.hasNext();
    }

    @SuppressWarnings("unchecked")
    @Override
    public C next() {
      Object row = mRowIterator.next();
      if (row == null) return null;

      if (row.getClass().isArray()) {
        C[] x = (C[]) row;
        return x[mColumnIndex];

      } else if (List.class.isAssignableFrom(row.getClass())) {
        return ((List<C>) row).get(mColumnIndex);

      } else {
        return null;
      }
    }

    @Override
    public void remove() {
      // Not supported
    }
  }


  /**
   * The base implementation supports the case where the dataType is an Array or List
   */
  @SuppressWarnings("unchecked")
  @Override
  public <C> Iterator<C> getElementIterator(Class<?> dataType, Class<C> columnType, int columnIndex) {
    if (dataType == null) {
      Object data = this.getDDF().getRepresentationHandler().getDefault();
      if (data == null) return null;
      dataType = data.getClass();
    }

    if (columnType == null) columnType = (Class<C>) this.getDDF().getRepresentationHandler().getDefaultColumnType();

    if (Iterable.class.isAssignableFrom(dataType) || dataType.isArray()) {
      Iterator<?> rowIterator = this.getRowIterator(dataType);
      return new ElementIterator<C>(rowIterator, columnIndex);

    } else {
      return null;
    }
  }

  @Override
  public <C> Iterator<C> getElementIterator(Class<?> dataType, Class<C> columnType, String columnName) {
    return this.getElementIterator(dataType, columnType, this.getDDF().getColumnIndex(columnName));
  }

  @Override
  public Iterator<?> getRowIterator() {
    return this.getRowIterator(null);
  }

  @Override
  public Iterator<?> getElementIterator(int columnIndex) {
    return this.getElementIterator(null, null, columnIndex);
  }

  @Override
  public Iterator<?> getElementIterator(String columnName) {
    return this.getElementIterator(null, null, this.getDDF().getColumnIndex(columnName));
  }

  @Override
  public DDF firstNRows(int numRows) throws DDFException {
    return this.getDDF().executeSqlOnTable(String.format("SELECT * FROM %%s LIMIT %d", numRows),
        String.format("Unable to fetch %d rows from table %%s", numRows));
  }

  @Override
  public DDF project(String[] columnNames) throws DDFException {
    String selectedColumns = Joiner.on(",").join(columnNames);
    return this.getDDF().executeSqlOnTable(String.format("SELECT %s FROM %%s", selectedColumns),
        String.format("Unable to project columns %s from table %%s", selectedColumns));
  }
}
