/*
 *  Copyright (C) 2013 Adatao, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.adatao.pa.spark.execution

import java.lang.String
import com.adatao.ML
import com.adatao.ML.ALossFunction
import com.adatao.ML.Utils
import com.adatao.ddf.types.Matrix
import com.adatao.ddf.types.Vector
import com.adatao.spark.RDDImplicits._
import org.apache.spark.rdd.RDD
import org.jblas.DoubleMatrix
import org.jblas.Solve
import com.adatao.pa.spark.DataManager._
import com.adatao.ML.ALinearModel
import java.util.HashMap
import scala.collection.mutable.ListBuffer
import org.jblas.exceptions.LapackArgumentException
import org.jblas.exceptions.LapackSingularityException
import org.jblas.exceptions.LapackException
import scala.collection.TraversableOnce
import scala.collection.Iterator
import scala.collection.immutable.List
import scala.collection.mutable.ArrayBuffer
import org.apache.spark.api.java.JavaRDD
import com.adatao.pa.spark.SharkUtils
import shark.api.JavaSharkContext
import java.util.ArrayList
import com.adatao.ddf.DDF
import scala.collection.mutable.ArrayBuffer
import com.adatao.pa.spark.types.{FailedResult, ExecutionException, SuccessfulResult, ExecutionResult}
import com.adatao.spark.ddf.analytics.NQLinearRegressionModel
import com.adatao.ddf.ml.IModel

/**
 * Author: NhanVLC
 * Original code is from mllib ridge regression
 */
class LinearRegressionNormalEquation(
  dataContainerID: String,
  xCols: Array[Int],
  yCol: Int,
  var ridgeLambda: Double,
  mapReferenceLevel: HashMap[String, String] = null)
  extends AExecutor[IModel] {

  override def runImpl(context: ExecutionContext): IModel = {
    val ddfManager = context.sparkThread.getDDFManager();

    val ddfId = Utils.dcID2DDFID(dataContainerID)
    val ddf = ddfManager.getDDF(ddfId) match {
      case x: DDF => x
      case _ => throw new IllegalArgumentException("Only accept DDF")
    }
    //project first
    val trainedColumns = xCols :+ yCol
    val trainedColumnNames = trainedColumns.map(idx => ddf.getColumnName(idx))
    val projectedDDF = ddf.Views.project(trainedColumnNames: _*)

    projectedDDF.getSchemaHandler().computeFactorLevelsForAllStringColumns()
    projectedDDF.getSchemaHandler().generateDummyCoding()

    //plus bias term
    var numFeatures = xCols.length + 1
    if (projectedDDF.getSchema().getDummyCoding() != null) {
      numFeatures = projectedDDF.getSchema().getDummyCoding().getNumberFeatures
      projectedDDF.getSchema().getDummyCoding().toPrint()
    }
      
    val model = projectedDDF.ML.train("linearRegressionNQ", numFeatures: java.lang.Integer, ridgeLambda: java.lang.Double)

    val rawModel = model.getRawModel.asInstanceOf[com.adatao.spark.ddf.analytics.NQLinearRegressionModel]
    if (projectedDDF.getSchema().getDummyCoding() != null)
      rawModel.setDummy(projectedDDF.getSchema().getDummyCoding())


//    val paModel = new NQLinearRegressionModel(model.getName(), model.getTrainedColumns, rawModel.weights, rawModel.rss,
//      rawModel.sst, rawModel.stdErrs, rawModel.numSamples, rawModel.numFeatures, rawModel.vif, rawModel.messages)

    return model
  }
}

//class NQLinearRegressionModel(val modelID: String, val trainedColumns: Array[String], weights: Vector, val rss: Double,
//  val sst: Double, val stdErrs: Vector,
//  numSamples: Long, val numFeatures: Int, val vif: Array[Double], val messages: Array[String])
//  extends ALinearModel[Double](weights, numSamples) {
//  override def predict(features: Vector): Double = this.linearPredictor(features)
//}

///**
// * Entry point for SparkThread executor to execute predictions
// */
//class LinearRegressionNormalEquationPredictor(val model: NQLinearRegressionModel, val features: Array[Double]) extends APredictionExecutor[java.lang.Double] {
//
//  def predict: java.lang.Double = model.predict(features).asInstanceOf[java.lang.Double]
//}
