package com.adatao.spark.ddf;

import org.apache.spark.rdd.RDD;
import com.adatao.ddf.DDF;
import com.adatao.ddf.DDFManager;
import com.adatao.ddf.content.Schema;
import com.adatao.ddf.exception.DDFException;

/**
 * An Apache-Spark-based implementation of DDF
 */
@SuppressWarnings("serial")
public class SparkDDF extends DDF {

  public <T> SparkDDF(DDFManager manager, RDD<T> rdd, Class<T> rowType, String namespace, String name, Schema schema)
      throws DDFException {

    super(manager);
    if (rdd == null) throw new DDFException("Non-null RDD is required to instantiate a new SparkDDF");
    this.initialize(manager, rdd, rowType, namespace, name, schema);
  }

  /**
   * Signature without RDD, useful for creating a dummy DDF used by DDFManager
   * 
   * @param manager
   */
  public SparkDDF(DDFManager manager) throws DDFException {
    super(manager);
  }

  @SuppressWarnings("unchecked")
  public <T> RDD<T> getRDD(Class<T> rowType) throws DDFException {
    Object obj = this.getRepresentationHandler().get(rowType);
    if (obj instanceof RDD<?>) return (RDD<T>) obj;
    else throw new DDFException("Unable to get RDD with element type " + rowType);
  }
}
