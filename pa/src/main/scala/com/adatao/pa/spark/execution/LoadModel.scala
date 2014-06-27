package com.adatao.pa.spark.execution

import com.adatao.basic.ddf.content.PersistenceHandler
import com.adatao.basic.ddf.BasicDDF
import com.adatao.ddf.ml.{IModel, Model}
import org.apache.spark.mllib.clustering.KMeansModel
import com.adatao.pa.AdataoException
import com.adatao.pa.AdataoException.AdataoExceptionCode
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import com.adatao.spark.ddf.analytics.{NQLinearRegressionModel => NQLinearModel}
import com.adatao.pa.spark.types.{SuccessfulResult, FailedResult, ExecutionException, ExecutionResult}

/**
 * author: daoduchuan
 */
class LoadModel(modelName: String) extends AExecutor[IModel] {
  override def runImpl(ctx: ExecutionContext): IModel = {
    val manager = ctx.sparkThread.getDDFManager
    val persistenceHandler = new PersistenceHandler(null)
    val dataFileName = s"${manager.getNamespace}/${modelName}.dat"
    val uri = s"${manager.getEngine}://$dataFileName"

    val modelDDF = persistenceHandler.load(uri).asInstanceOf[BasicDDF]
    val model = Model.deserializeFromDDF(modelDDF)
    manager.addModel(model)
    model
  }
}

