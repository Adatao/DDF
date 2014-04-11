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
		extends AModelTrainer[NQLinearRegressionModel](dataContainerID, xCols, yCol, mapReferenceLevel) {

    override def train(dataContainerID: String, context: ExecutionContext): NQLinearRegressionModel = {
      val ddfManager = context.sparkThread.getDDFManager();
      val ddf = ddfManager.getDDF(("SparkDDF-spark-" + dataContainerID).replace("-", "_")) match {
        case x: DDF => x
        case _ => throw new IllegalArgumentException("Only accept DDF")
      }
      // project the xCols, and yCol as a new DDF
      // this is costly
      val schema = ddf.getSchema()
      var columnList : java.util.List[java.lang.String] = new java.util.ArrayList[java.lang.String]
      for (col <- xCols) columnList.add(schema.getColumn(col).getName)
      columnList.add(schema.getColumn(yCol).getName)
      val projectDDF = ddf.Views.project(columnList)
      // val (weights, trainingLosses, numSamples) = Regression.train(lossFunction, numIters, learningRate, initialWeights, numFeatures)
      // new LinearRegressionModel(weights, trainingLosses, numSamples)
      val model = projectDDF.ML.train("linearRegressionNQ", xCols.length: java.lang.Integer, ridgeLambda: java.lang.Double)
      // converts DDF model to old PA model
      val rawModel = model.getRawModel.asInstanceOf[com.adatao.spark.ddf.ml.pa.NQLinearRegressionModel]
      val itr = rawModel.weights.iterator
      val paWeights: ArrayBuffer[Double] = ArrayBuffer[Double]()
      while (itr.hasNext) paWeights += itr.next
      val paModel = new NQLinearRegressionModel(Vector.apply(paWeights.toArray), model.getName(), 0, 0, null, projectDDF.getNumRows(), xCols.length, null, null)
      LOG.info("Json model")
      LOG.info(rawModel.weights.toJson)
      LOG.info(paModel.weights.toJson)
      LOG.info(paModel.toString)
      ddfManager.addModel(model)
      // paModel.ddfModel = model
      return paModel
    }

    def train(dataPartition: RDD[(Matrix, Vector)], ctx: ExecutionContext): NQLinearRegressionModel = {
      null
    }
	// Purpose of this function is to handle empty partitions using mapPartitions, :((((((
	//post process, set column mapping to model
	def instrumentModel(model: NQLinearRegressionModel,
		mapping: HashMap[java.lang.Integer, HashMap[String, java.lang.Double]]): NQLinearRegressionModel = {
		model.dummyColumnMapping = mapping
		model
	}
}

class NQLinearRegressionModel(weights: Vector, val resDfId: String, val rss: Double,
	val sst: Double, val stdErrs: Vector,
	numSamples: Long, val numFeatures: Int, val vif: Array[Double], val messages: Array[String])
		extends ALinearModel[Double](weights, numSamples) {
	override def predict(features: Vector): Double = this.linearPredictor(features)
}

/**
 * Entry point for SparkThread executor to execute predictions
 */
class LinearRegressionNormalEquationPredictor(val model: NQLinearRegressionModel, val features: Array[Double]) extends APredictionExecutor[java.lang.Double] {

	def predict: java.lang.Double = model.predict(features).asInstanceOf[java.lang.Double]
}
