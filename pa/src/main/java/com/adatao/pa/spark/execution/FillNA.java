package com.adatao.pa.spark.execution;


import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.adatao.ddf.DDF;
import com.adatao.ddf.etl.IHandleMissingData.FillMethod;
import com.adatao.ddf.exception.DDFException;
import com.adatao.ddf.types.AggregateTypes.AggregateFunction;
import com.adatao.pa.AdataoException;
import com.adatao.pa.AdataoException.AdataoExceptionCode;
import com.adatao.pa.spark.SparkThread;
import com.adatao.pa.spark.Utils;
import com.adatao.pa.spark.types.ExecutorResult;
import com.adatao.pa.spark.types.IExecutor;


public class FillNA implements IExecutor {
  public static Logger LOG = LoggerFactory.getLogger(FillNA.class);
  private String value;
  private FillMethod method;
  private long limit;
  private String func;
  Map<String, String> columnsToValues;
  private List<String> columns;
  private String dataContainerID;


  public FillNA(String value, FillMethod method, long limit, String function, Map<String, String> columnsToValues,
      List<String> columns, String dataContainerID) {
    this.value = value;
    this.method = method;
    this.limit = limit;
    this.func = function;
    this.columnsToValues = columnsToValues;
    this.columns = columns;
    this.dataContainerID = dataContainerID;
  }

  // public DDF fillNA(String value, FillMethod method, long limit, AggregateFunction function, Map<String, String>
  // columnsToValues, List<String> columns) throws DDFException;
  @Override
  public ExecutorResult run(SparkThread sparkThread) throws AdataoException {
    try {

      DDF ddf = sparkThread.getDDFManager().getDDF(("SparkDDF-spark-" + dataContainerID).replace("-", "_"));
      DDF newddf = ddf.getMissingDataHandler().fillNA(value, method, limit, AggregateFunction.fromString(function),
          columnsToValues, columns);

      return new Utils.DataFrameResult(newddf);

    } catch (Exception e) {

      if (e instanceof shark.api.QueryExecutionException) {
        throw new AdataoException(AdataoExceptionCode.ERR_LOAD_TABLE_FAILED, e.getMessage(), null);
      } else {
        LOG.error("Cannot fill NAs in the DDF", e);
        return null;
      }
    }
  }

  public String getDataContainerID() {
    return dataContainerID;
  }

  public FillNA setDataContainerID(String dataContainerID) {
    this.dataContainerID = dataContainerID;
    return this;
  }

}
