package com.adatao.spark.ddf.analytics;


import junit.framework.Assert;
import org.apache.spark.rdd.RDD;
import org.junit.Test;
import io.ddf.DDF;
import io.ddf.DDFManager;
import io.ddf.exception.DDFException;
import io.ddf.misc.Config;
import io.ddf.misc.Config.ConfigConstant;
import com.google.common.base.Strings;


public class MLTests {

  private void initializeConfiguration() {
    if (Strings.isNullOrEmpty(Config.getValue(ConfigConstant.ENGINE_NAME_SPARK.toString(), "kmeans"))) {
      Config.set(ConfigConstant.ENGINE_NAME_SPARK.toString(), "kmeans2",
          String.format("%s#%s", this.getClass().getName(), "dummyKMeans"));
    }
  }

  public static Object dummyKMeans(RDD<Object> arg1, int arg2, Double arg3) {

    return null;
  }

//  //dff is null, no longer valid test
//  public void testTrain() throws DDFException {
//    this.initializeConfiguration();
//
//
//    DDF ddf = DDFManager.get("spark").newDDF();
//    Assert.assertNotNull("DDF cannot be null", ddf);
//
//    // @huan, see {@link MLSupporter#convertDDF}
//
//    // This uses the fully qualified class#method mechanism
//    Object model = ddf.ML.train("com.adatao.spark.ddf.analytics.MLTests#dummyKMeans", 1, 2.2);
//    Assert.assertNotNull("Model cannot be null", model);
//
//    // This uses the mapping config to go from "kmeans" to "com.adatao.spark.ddf.analytics.MLTests#dummyKMeans"
//    model = ddf.ML.train("kmeans", 1, 2.2);
//    Assert.assertNotNull("Model cannot be null", model);
//  }


}
