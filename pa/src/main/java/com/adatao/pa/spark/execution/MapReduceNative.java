package com.adatao.pa.spark.execution;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.ddf.DDF;
import io.ddf.DDFManager;
import com.adatao.pa.AdataoException;
import com.adatao.pa.AdataoException.AdataoExceptionCode;
import com.adatao.pa.spark.SparkThread;
import com.adatao.pa.spark.Utils;
import com.adatao.pa.spark.types.ExecutorResult;

@SuppressWarnings("serial")
public class MapReduceNative extends CExecutor {

  private String dataContainerID;
  private String mapFuncDef;
  private String reduceFuncDef;
  private boolean mapsideCombine;
  public static Logger LOG = LoggerFactory.getLogger(MapReduceNative.class);


  public MapReduceNative(String dataContainerID, String mapFuncDef, String reduceFuncDef, boolean mapsideCombine) {
    this.dataContainerID = dataContainerID;
    this.mapFuncDef = mapFuncDef;
    this.reduceFuncDef = reduceFuncDef;
    this.mapsideCombine = mapsideCombine;
  }

  public MapReduceNative(String dataContainerID, String mapFuncDef, String reduceFuncDef) {
    this(dataContainerID, mapFuncDef, reduceFuncDef, true);
  }

  @Override
  public ExecutorResult run(SparkThread sparkThread) throws AdataoException {
    try {

      DDFManager manager = sparkThread.getDDFManager();
      DDF ddf = manager.getDDF(dataContainerID);
      DDF newddf = ddf.Transform.transformMapReduceNative(mapFuncDef, reduceFuncDef, mapsideCombine);
      LOG.info("Transformed DDF name " + newddf.getName());

      manager.addDDF(newddf);
      LOG.info(manager.getDDFs().keySet().toString());

      return new Utils.DataFrameResult(newddf);

    } catch (Exception e) {
      throw new AdataoException(AdataoExceptionCode.ERR_GENERAL, e.getMessage(), e);
    }
  }

  public String getDataContainerID() {
    return dataContainerID;
  }

  public MapReduceNative setDataContainerID(String dataContainerID) {
    this.dataContainerID = dataContainerID;
    return this;
  }
}
