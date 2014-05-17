package com.adatao.pa.spark.execution;


import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.adatao.ddf.DDF;
import com.adatao.ddf.DDFManager;
import com.adatao.pa.AdataoException;
import com.adatao.pa.AdataoException.AdataoExceptionCode;
import com.adatao.pa.spark.SparkThread;
import com.adatao.pa.spark.Utils;
import com.adatao.pa.spark.types.ExecutorResult;
import com.google.common.collect.Lists;

public class TransformHive extends CExecutor {

  private String dataContainerID;
  private String transformExpression;
  public static Logger LOG = LoggerFactory.getLogger(TransformHive.class);


  public TransformHive(String dataContainerID, String transformExpression) {
    this.dataContainerID = dataContainerID;
    this.transformExpression = transformExpression;
  }

  @Override
  public ExecutorResult run(SparkThread sparkThread) throws AdataoException {
    try {
      
      DDFManager manager = sparkThread.getDDFManager();
      DDF ddf = manager.getDDF(("SparkDDF-spark-" + dataContainerID).replace("-", "_"));

      DDF newddf = ddf.Transform.transformUDF(transformExpression);
      
      manager.addDDF(newddf);

      return new Utils.DataFrameResult(newddf);

    } catch (Exception e) {
      
      if (e instanceof shark.api.QueryExecutionException) {
        throw new AdataoException(AdataoExceptionCode.ERR_LOAD_TABLE_FAILED, e.getMessage(), null);
      } else {
        LOG.error("Cannot transform the DDF", e);
        return null;
      }
      }
  }

  public String getDataContainerID() {
    return dataContainerID;
  }

  public TransformHive setDataContainerID(String dataContainerID) {
    this.dataContainerID = dataContainerID;
    return this;
  }
}