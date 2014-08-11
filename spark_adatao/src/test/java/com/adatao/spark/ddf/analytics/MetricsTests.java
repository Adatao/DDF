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
import io.ddf.ml.IModel;

public class MetricsTests {

  @Test
  public void testConfusionMatrix() throws DDFException {
    DDFManager manager = DDFManager.get("spark");
    try {
      manager.sql2txt("drop table if exists airline");
    } catch (Exception e) {
      System.out.println(e);
    }

    manager.sql2txt("create table airline (Year int,Month int,DayofMonth int,"
      + "DayOfWeek int,DepTime int,CRSDepTime int,ArrTime int,"
      + "CRSArrTime int,UniqueCarrier string, FlightNum int, "
      + "TailNum string, ActualElapsedTime int, CRSElapsedTime int, "
      + "AirTime int, ArrDelay int, DepDelay int, Origin string, "
      + "Dest string, Distance int, TaxiIn int, TaxiOut int, Cancelled int, "
      + "CancellationCode string, Diverted string, CarrierDelay int, "
      + "WeatherDelay int, NASDelay int, SecurityDelay int, LateAircraftDelay int ) "
      + "ROW FORMAT DELIMITED FIELDS TERMINATED BY ','");

    manager.sql2txt("load data local inpath '../resources/test/airline.csv' into table airline");

    DDF ddf = manager.sql2ddf("select " +
          "distance, depdelay, if (arrdelay > 10.89, 1, 0) as delayed from airline");
    Assert.assertEquals(3, ddf.getSummary().length);
    IModel logModel = ddf.ML.train("logisticRegressionWithSGD", 10, 0.1);
    Long[][] cm = ddf.ML.getConfusionMatrix(logModel, 0.5);
    Assert.assertEquals(0, cm[0][0].longValue());
    Assert.assertEquals(13, cm[0][1].longValue());
    Assert.assertEquals(0, cm[1][0].longValue());
    Assert.assertEquals(18, cm[1][1].longValue());
    manager.shutdown();
  }


}