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

package com.adatao.spark.ddf.analytics

import java.lang.String
import io.ddf.types.TupleMatrixVector
import io.ddf.types.Matrix
import io.ddf.types.Vector
import com.adatao.spark.ddf.analytics.RDDImplicits._
import org.apache.spark.rdd.RDD
import java.util.HashMap

/**
 */
object LinearRegressionGD {
    def train(dataPartition: RDD[TupleMatrixVector],
        numIters: Int,
        learningRate: Double,
        ridgeLambda: Double,
        initialWeights: Array[Double]
        ): LinearRegressionModel = {
        dataPartition.cache()
        val numFeatures: Int = dataPartition.map(x => x._1.getColumns()).first()
        val weights = if (initialWeights == null || initialWeights.length != numFeatures)  Utils.randWeights(numFeatures) else Vector(initialWeights)
        var model = LinearRegression.train(
            new LinearRegressionGD.LossFunction(dataPartition.map {row => (row.x, row.y)}, ridgeLambda), numIters, learningRate, weights, numFeatures
        )
        dataPartition.unpersist()
        model
    }
    /**
     * As a client with our own data representation [[RDD(Matrix, Vector]], we need to supply our own LossFunction that
     * knows how to handle that data.
     *
     * NB: We separate this class into a static (companion) object to avoid having Spark serialize too many unnecessary
     * objects, if we were to place this class within [[class LinearRegression]].
     */
    class LossFunction(@transient XYData: RDD[(Matrix, Vector)], ridgeLambda: Double) extends ALinearGradientLossFunction(XYData, ridgeLambda) {
        def compute: Vector => ALossFunction = {
            (weights: Vector) => XYData.map { case (x,y) => this.compute(x, y, weights) }.safeReduce(_.aggregate(_))
        }
    }
}
