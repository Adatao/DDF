package com.adatao.pa.spark.execution

import com.adatao.pa.spark.DataManager.{ DataFrame, MetaInfo }
import org.apache.spark.api.java.JavaRDD
import io.ddf.DDF
import io.ddf.ml.IModel
import io.spark.ddf.SparkDDF

/**
 * Created with IntelliJ IDEA.
 * User: daoduchuan
 * Date: 29/9/13
 * Time: 5:59 PM
 * To change this template use File | Settings | File Templates.
 */
class Residuals(dataContainerID: String, val modelID: String, val xCols: Array[Int], val yCol: Int)
  extends AExecutor[ResidualsResult] {
  override def runImpl(ctx: ExecutionContext): ResidualsResult = {

    val ddfManager = ctx.sparkThread.getDDFManager();
    val ddfId = dataContainerID
    val ddf: DDF = ddfManager.getDDF(ddfId);
    // first, compute RDD[(ytrue, ypred)]

    val mymodel: IModel = ddfManager.getModel(modelID)
    val trainedCols = (xCols :+ yCol).map(ddf.getSchema.getColumnName(_))

    val projectedDDF = ddf.VIEWS.project(trainedCols: _*)
    val predictionDDF = projectedDDF.getMLSupporter().applyModel(mymodel, true, true)

    val residualsDDF = ddf.getMLMetricsSupporter().residuals(predictionDDF)
    require(residualsDDF != null)

    //return dataframe
    val metaInfo = Array(new MetaInfo("residual", "java.lang.Double"))
    val uid = residualsDDF.getName()

    new ResidualsResult(uid, metaInfo)
  }
}

class ResidualsResult(val dataContainerID: String, val metaInfo: Array[MetaInfo])
