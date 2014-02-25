package com.adatao.spark.ddf;

import org.apache.spark.rdd.RDD;

import com.adatao.ddf.DDF;
import com.adatao.ddf.DDFManager;
import com.adatao.ddf.content.Schema;
import com.adatao.ddf.exception.DDFException;

/**
 * DDF extends DDF in order to provide some utility methods that are specific to Spark framework.
 * 
 * @author Cuong Kien Bui
 * @version 0.1
 */
public class SparkDDF extends DDF {

  public <T> SparkDDF(DDFManager manager, RDD<T> rdd, Class<?> elementType, String namespace, String name, Schema schema)
      throws DDFException {

    if (rdd == null) throw new DDFException("Non-null RDD is required to instantiate a new DDF");
    this.initialize(manager, rdd, elementType, namespace, name, schema);
  }


  /**
   * Signature without RDD, useful for creating a dummy DDF used by DDFManager
   * 
   * @param manager
   */
  /* package-private */SparkDDF(DDFManager manager) throws DDFException {
    this.initialize(manager, (Object) null, null, null, null, null);
  }


  @SuppressWarnings("unchecked")
  public <T> RDD<T> getRDD(Class<T> elementType) throws DDFException {
    Object obj = this.getRepresentationHandler().get(elementType);
    if (obj instanceof RDD<?>) return (RDD<T>) obj;
    else throw new DDFException("Unable to get RDD with element type " + elementType);
  }
}