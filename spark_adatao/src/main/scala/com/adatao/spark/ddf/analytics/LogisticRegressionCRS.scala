package com.adatao.spark.ddf.analytics

import java.util.HashMap
import scala.Array.canBuildFrom
import scala.util.Random
import org.apache.spark.rdd.RDD
import org.jblas.DoubleMatrix
import org.jblas.MatrixFunctions
import io.ddf.types.Matrix
import io.ddf.types.MatrixSparse
import io.ddf.types.TupleMatrixVector
import io.ddf.types.Vector
import io.ddf.content.Schema.DummyCoding

class LogisticRegressionCRS {

}

object LogisticRegressionCRS {
  /**
   * This is the signature to be used by clients that can represent their data using [[Matrix]] and [[Vector]]
   */

  def train[XYDataType](
    lossFunction: LossFunction,
    numIters: Int,
    learningRate: Double,
    initialWeights: Vector,
    numFeatures: Int /* used only if initialWeights is null */ )(implicit m: Manifest[XYDataType]): LogisticRegressionModel = {

    val (finalWeights, trainingErrors, numSamples) = GradientDescent.runBatch(lossFunction, getWeights(Option(initialWeights), numFeatures), learningRate, numIters)
    new LogisticRegressionModel(finalWeights, trainingErrors, numSamples)
  }

  def train(XYData: RDD[TupleMatrixVector],
    numIters: java.lang.Integer,
    learningRate: java.lang.Double,
    ridgeLambda: java.lang.Double,
    initialWeights: scala.Array[Double],
    columnsSummary: HashMap[String, Array[Double]]): LogisticRegressionModel = {

    var (sparseColumns, sparseColumnsPaddingIndex, sumAllRange) = buildParameters(columnsSummary)
    
    val transformer: TransformSparseMatrix = new TransformSparseMatrix(sparseColumns, sparseColumnsPaddingIndex, sumAllRange)
    //convert to MatrixSparse
    val XYSparse = transformer.transform(XYData)
    
    val numFeatures = XYSparse.map(xy => xy._1.crs.numColumns()).first()
    
    var weights = null.asInstanceOf[Vector]
    if (sumAllRange > 0)
      weights = randWeights(numFeatures + sumAllRange) //Vector(initialWeights)
    else weights = if (initialWeights == null || initialWeights.length != numFeatures) randWeights(numFeatures) else Vector(initialWeights)

    val snumIters: Int = numIters.asInstanceOf[Int]
    val slearningRate: Double = learningRate.asInstanceOf[Double]
    val sridgeLambda: Double = ridgeLambda.asInstanceOf[Double]
    val snumFeatures: Int = numFeatures.asInstanceOf[Int]

    val lossFunction = new LossFunction(XYSparse, ridgeLambda)

    //convert to Vector
    var a: Vector = null
    if (weights != null && weights.size > 0) {
      var i = 0
      a = new Vector(weights.size)
      while (i < weights.size) {
        a.put(i, weights(i))
        i += 1
      }
    }

    //depend on length of weights
    val model: LogisticRegressionModel = train(lossFunction, snumIters, slearningRate, a, snumFeatures)
    model
  }

  /*build sparse columns: 
   * column index, Array(min, max)
   * for example, ["1", [120, 10000]]
   * build column start index map
   */
  def buildParameters(columnsSummary: HashMap[String, Array[Double]]): (HashMap[Int, Array[Double]], HashMap[Int, Int], Int) = {
    val SPARSE_RANGE = Integer.parseInt(System.getProperty("sparse.max.range", "10000"))

    //build sparse columns: column index, Array(min, max)
    //for example, ["1", [120, 10000]]
    //build column start index map
    var sparseColumns = new HashMap[Int, Array[Double]]()
    var sparseColumnsPaddingIndex = new HashMap[Int, Int]()
    //get new number of sparse columns
    var i = 0
    var sumAllRange = 0
    while (i < columnsSummary.get("min").size) {
      val range = if (columnsSummary.get("max")(i) > columnsSummary.get("min")(i)) columnsSummary.get("max")(i) - columnsSummary.get("min")(i) else 0
      if (range >= SPARSE_RANGE) {
        sparseColumns.put(i, Array(columnsSummary.get("min")(i), columnsSummary.get("max")(i)))
        sparseColumnsPaddingIndex.put(i, sumAllRange)
        sumAllRange += range.asInstanceOf[Int] + 1
      }
      i += 1
    }
    (sparseColumns, sparseColumnsPaddingIndex, sumAllRange)
  }

  private def getWeights(initialWeights: Option[Vector], numFeatures: Int /* used only if initialWeights is null */ ): Vector = {
    initialWeights match {
      case Some(vector) ⇒ vector
      case None ⇒ Vector(Seq.fill(numFeatures)(Random.nextDouble).toArray)
    }
  }
  def randWeights(numFeatures: Int) = Vector(Seq.fill(numFeatures)(Random.nextDouble).toArray)
}


//class LossFunction(@transient XYData: RDD[TupleMatrixVector], ridgeLambda: Double) extends ALogisticGradientLossFunction(XYData, ridgeLambda) {
//    def compute: Vector ⇒ ALossFunction = {
////      (weights: Vector) ⇒ XYData.map { case (x,y) ⇒ this.compute(x, y, weights) }.reduce(_.aggregate(_))
//      (weights: Vector) ⇒ XYData.map { case a ⇒ this.compute(a.x, a.y, weights) }.reduce(_.aggregate(_))
//    }
//}

class LossFunction(@transient XYData: RDD[(MatrixSparse, Vector)], ridgeLambda: Double) extends ALossFunction {
  def compute: Vector ⇒ ALossFunction = {
    //      (weights: Vector) ⇒ XYData.map { case (x, y) ⇒ this.compute(x, y, weights) }.safeReduce(_.aggregate(_))
    (weights: Vector) ⇒ XYData.map { case (x, y) ⇒ this.compute(x, y, weights) }.reduce(_.aggregate(_))
  }

  def computeHypothesis(X: MatrixSparse, weights: Vector): (DoubleMatrix, DoubleMatrix) = {
    val startTime = System.currentTimeMillis()
    val linearPredictor = this.computeLinearPredictor(X, weights)
    (linearPredictor, ALossFunction.sigmoid(linearPredictor))
  }

  def computeLoss(Y: Vector, weights: Vector, errors: DoubleMatrix, linearPredictor: DoubleMatrix, hypothesis: DoubleMatrix) = {

    val startTime = System.currentTimeMillis()
    val YT = Y.transpose()
    val lossA: Double = Y.dot(ALossFunction.safeLogOfSigmoid(hypothesis, linearPredictor)) // Y' x log(h)
    val lossB: Double = Vector.fill(Y.length, 1.0).subi(Y).dot(
      ALossFunction.safeLogOfSigmoid(Vector.fill(Y.length, 1.0).subi(hypothesis), linearPredictor.neg)) // (1-Y') x log(1-h)
    var J = -(lossA + lossB)
    if (ridgeLambda != 0.0) J += (ridgeLambda / 2) * weights.dot(weights)
    J
  }

  final def computeLinearPredictor(X: MatrixSparse, weights: Vector): DoubleMatrix = {
    X.mmul(weights)
  }

  protected def computeGradients(X: MatrixSparse, weights: Vector, errors: DoubleMatrix): Vector = {
    val startTime = System.currentTimeMillis()
    //      val gradients = Vector(errors.transpose().mmul(X)) // (h - Y) x X = errors.transpose[1 x m] * X[m x n] = [1 x n] => Vector[n]
    //mmul2 is reverse order of mmul, meaning errors * X
    val temp = X.mmul2(errors.transpose())
    val gradients = Vector(temp.getData()) // (h - Y) x X = errors.transpose[1 x m] * X[m x n] = [1 x n] => Vector[n]
    if (ridgeLambda != 0.0) gradients.addi(weights.mul(ridgeLambda)) // regularization term, (h - Y) x X + L*weights
    gradients
  }

  def compute(X: MatrixSparse, Y: Vector, theWeights: Vector): ALossFunction = {

    val (linearPredictor, hypothesis) = this.computeHypothesis(X, theWeights)
    val errors = hypothesis.sub(Y)

    gradients = this.computeGradients(X, theWeights, errors)

    loss = this.computeLoss(Y, theWeights, errors, linearPredictor, hypothesis)

    weights = theWeights
    numSamples = Y.rows

    this
  }

  //not used
  def compute(X: Matrix, Y: Vector, weights: Vector): ALossFunction = {
    null
  }
}



/*
 * transform RDD[(Matrix, Vector)] to RDD[(MatrixSparse, Vector)] 
 * input Matrix, output: MatrixSparse
 * example:
 * Input matrix: 2 rows, 3 columns and we want to represent 2th column in sparse format
 * header: mobile_hight, advertise_id, mobile_width
 * [10, 20, 30]
 * [15, 134790, 25]
 * 
 * output matrix: 2 rows, new sparse columns
 * [10, 0, 30, 0, 0, 0, ....., 1] all are zero and the 23th column will be represent = 1
 * [15, 0, 25, 0, 0, 0, ......., 1] all are zero and the (134790 + 3)th column will be represent = 1
 * when we represent this the weight for each advertise_id will represent by calling: w[23], and w[134793] respectively
 * if this presentation is suitable then all the formular in logistic regression will be the same with formular in the dense matrix case
  
 * 
 */
class TransformSparseMatrix(sparseColumns: HashMap[Int, Array[Double]], sparsePaddingIndex: HashMap[Int, Int], sumAllRange: Int) extends Serializable {
  def transform(dataPartition: RDD[TupleMatrixVector]): (RDD[(MatrixSparse, Vector)]) = {
    dataPartition.map(transformSparse(this.sparseColumns))
  }

  def transformSparse(sparseColumns: HashMap[Int, Array[Double]])(inputRows: TupleMatrixVector): (MatrixSparse, Vector) = {
    val X = inputRows._1
    val Y = inputRows._2

    val maxOriginColumns = X.getColumns()
    var Xprime: MatrixSparse = null
    if (sparseColumns != null && sparseColumns.size() > 0) {
      Xprime = new MatrixSparse(X.getRows(), maxOriginColumns + sumAllRange)
    } else {
      Xprime = new MatrixSparse(X.getRows(), maxOriginColumns)
    }

    val defaultValue = 1.0
    //fill in
    var row = 0
    var column = 0
    var newColumn = 0
    while (row < X.getRows()) {
      column = 0
      while (column < X.getColumns()) {
        val currentCell = X.get(row, column)
        //TODO double check adjustedColumnIndex is correct or not
        val adjustedColumnIndex = column + 1
        if (sparseColumns != null && sparseColumns.size() > 0 && sparseColumns.containsKey(adjustedColumnIndex)) {
          //check if this column is sparse column
          if (!sparseColumns.containsKey(adjustedColumnIndex)) {
            //set as normal
            Xprime.crs.set(row, column, currentCell)
          } //sparse column meaning, the cell value indicate the new  column index
          else {
            //based 0, new column index = number original columns + padding index + internal column index
            newColumn = maxOriginColumns
            newColumn += sparsePaddingIndex.get(adjustedColumnIndex)
            newColumn += currentCell.asInstanceOf[Int] - 1
            //offset by minmum cell value
            newColumn = newColumn - sparseColumns.get(adjustedColumnIndex).min.asInstanceOf[Int] + 1
            Xprime.crs.set(row, newColumn, defaultValue)
          }
        } else {
          //set as normal
          Xprime.crs.set(row, column, currentCell)
        }
        column += 1
      }
      row += 1
    }
    (Xprime, Y)
  }
}


