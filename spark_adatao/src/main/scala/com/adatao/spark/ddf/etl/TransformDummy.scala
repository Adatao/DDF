package com.adatao.spark.ddf.etl

import io.ddf.types.{Matrix, Vector}
import java.util.{Map => JMap, HashMap, BitSet}
import io.ddf.types.TupleMatrixVector
import org.apache.spark.rdd.RDD
import org.jblas.DoubleMatrix
import org.apache.hadoop.io._
import org.apache.hadoop.hive.serde2.io.{ShortWritable, DoubleWritable}
import java.nio.ByteOrder
import java.nio.ByteBuffer
import org.apache.spark.sql.columnar._
import java.util
import io.ddf.exception.DDFException
import org.slf4j.LoggerFactory
import io.spark.ddf.ml.TransformRow
import org.apache.spark.sql.catalyst.expressions.GenericMutableRow
import scala.collection.JavaConversions._


/**
 * author: daoduchuan
 */
object TransformDummy {
  val DEFAULT_BLOWUP_FACTOR = "5"
  var blowUpFactor = System.getProperty("pa.blowup.factor", DEFAULT_BLOWUP_FACTOR).toInt
  val LOG = LoggerFactory.getLogger(this.getClass)

  def schemaRDD2MatrixVector(rddCachedBatch: RDD[CachedBatch], xCols: Array[Int], yCol: Int,
                             categoricalMap: HashMap[Integer,
                               HashMap[String, java.lang.Double]] = null): RDD[TupleMatrixVector] = {
    val cachedColumnBuffers = rddCachedBatch.map {
      cachedBatch => {
        val buffers = cachedBatch.buffers
        buffers.map(arrByte => {
          ByteBuffer.wrap(arrByte)
        })
      }
    }
    cachedColumnBuffers.flatMap {
      arrayByteBuffer => {
        tablePartitionToMatrixVectorMapper(xCols, yCol, categoricalMap)(arrayByteBuffer)
      }
    }.filter(xy ⇒ (xy._1.columns > 0) && (xy._2.rows > 0))
  }

  def getNrowFromColumnIterator(columnIterators: Array[ByteBuffer]): Int = {

    val columnAccessor = ColumnAccessor(columnIterators(0))
    var count = 0
    val mutableRow = new GenericMutableRow(1)
    while (columnAccessor.hasNext) {
      columnAccessor.extractTo(mutableRow, 0)
      count += 1
    }
    LOG.info(">>>> rows count = " + count)
    count
  }

  def buildNullBitmap(usedColumnIterators: Array[ByteBuffer]): BitSet = {
    val nullBitmap: BitSet = new BitSet()
    //LOG.info(">>>>> numRows = " + numRows)

    usedColumnIterators.foreach {
      buffer => {
        val bytebuffer = buffer.duplicate().order(ByteOrder.nativeOrder())
        // read from beginning of ByteBuffer to get null position
        bytebuffer.rewind()
        bytebuffer.getInt()
        val nullCount = bytebuffer.getInt()
        LOG.info(">>>>> nullCount = " + nullCount)
        for (i <- 0 until nullCount) {
          val idx = bytebuffer.getInt
          nullBitmap.set(idx)
        }
      }
    }
    nullBitmap
  }

  def fillConstantColumn[T <: DoubleMatrix](matrices: Array[T], col: Int, value: Double) = {

    var matID = 0
    while (matID < matrices.size) {
      var row = 0
      val matrix = matrices(matID)
      while (row < matrix.rows) {
        matrix.put(row, col, value)
        row += 1
      }
      matID += 1
    }
  }

  def fillNumericColumn[M <: DoubleMatrix](matrices: Array[M],
                                           col: Int,
                                           columnIterator: ByteBuffer,
                                           nullBitmap: BitSet) = {
    val columnAccessor = ColumnAccessor(columnIterator)
    //temporary Row to hold the value from ColumnAccessor.extractSingle
    val mutableRow = new GenericMutableRow(1)
    var j = 0 // current ColumnIterator row counter
    // For performance, branching outside the tight loop
    val toDouble: (Object => Double) = columnAccessor.asInstanceOf[NativeColumnAccessor[_]].columnType match {
        case INT => (x: Object) => x.asInstanceOf[Int].toDouble
        case LONG => (x: Object) => x.asInstanceOf[Long].toDouble
        case FLOAT => (x: Object) => x.asInstanceOf[Float].toDouble
        case DOUBLE => (x: Object) => x.asInstanceOf[Double]
        case BOOLEAN => (x: Object) => {
          if (x.asInstanceOf[BooleanWritable].get()) 1 else 0
        }
        case BYTE => (x: Object) => x.asInstanceOf[ByteWritable].get().toDouble
        case SHORT => (x: Object) => x.asInstanceOf[ShortWritable].get().toDouble
        case typ => throw new IllegalArgumentException(s"cannot not convert column type ${typ} to double.")
      }

    var matID = 0
    while (matID < matrices.size) {
      var row = 0 // current matrix row counter
      val matrix = matrices(matID)

      while (row < matrix.rows) {
        columnAccessor.extractTo(mutableRow, 0)
        if (!nullBitmap.get(j)) {
          matrix.put(row, col, toDouble(mutableRow.apply(0).asInstanceOf[Object]))
          row += 1
        }
        j += 1
      }
      matID += 1
    }
  }

  // for ColumnIterator that returns Object and must be converted to Double
  def fillColumnWithConversion[M <: DoubleMatrix](
                                                   matrices: Array[M],
                                                   col: Int,
                                                   columnIterator: ByteBuffer,
                                                   nullBitmap: BitSet,
                                                   convert: (Object) => Double) = {
    //val byteBuffer = columnAccessor.buffer
    val columnAccessor = ColumnAccessor(columnIterator)

    val mutableRow = new GenericMutableRow(1)
    LOG.info(">>>> fillColumnWithConversion columnType = " + columnAccessor.asInstanceOf[NativeColumnAccessor[_]].columnType.toString())
    var j = 0 // current ColumnIterator row counter
    var matID = 0
    while (matID < matrices.size) {
      var row = 0
      val matrix = matrices(matID)
      while (row < matrix.rows) {
        columnAccessor.extractTo(mutableRow, 0)
        if (!nullBitmap.get(j)) {
          matrix.put(row, col, convert(mutableRow.apply(0).asInstanceOf[Object]))
          row += 1
        }
        j += 1
      }
      matID += 1
    }
  }

  def splitMatrixVector(numRows: Int, numCols: Int): (Array[Matrix], Array[Vector]) = {
    val nRowPerMatrix = numRows / blowUpFactor
    var arrVector = Array.fill[Vector](blowUpFactor)(new Vector(nRowPerMatrix))
    var arrMatrix = Array.fill[Matrix](blowUpFactor)(new Matrix(nRowPerMatrix, numCols))

    if(numRows % blowUpFactor != 0) {
      val lastVector = new Vector(numRows % blowUpFactor)
      val lastMatrix = new Matrix(numRows % blowUpFactor, numCols)
      arrVector = arrVector :+ lastVector
      arrMatrix = arrMatrix :+ lastMatrix
    }
    (arrMatrix, arrVector)
  }

  def tablePartitionToMatrixVectorMapper(xCols: Array[Int],
                                         yCol: Int,
                                         categoricalMap: HashMap[java.lang.Integer,
                                           HashMap[java.lang.String, java.lang.Double]])
                                        (columnIterators: Array[ByteBuffer]): Array[TupleMatrixVector] = {
    LOG.info(">>> blow up factor = " + blowUpFactor)
    // get the list of used columns
    val xyCol = xCols :+ yCol

    if (columnIterators.size == 0) {
      Array(new TupleMatrixVector(new Matrix(0, 0), Vector(0)))
    } else {
      val usedColumnIterators: Array[ByteBuffer] = xyCol.map {
        colId ⇒ columnIterators(colId)
      }
      val numElements = this.getNrowFromColumnIterator(usedColumnIterators)
      //TODO: handle number of rows in long
      val nullBitmap = buildNullBitmap(usedColumnIterators)
      val numRows = numElements - nullBitmap.cardinality()
      LOG.info(">>> numRows = " + numRows)
      val numXCols = xCols.length + 1

      var numDummyCols = 0
      if (categoricalMap != null) {
        xCols.foreach {
          xCol => {
            if (categoricalMap.containsKey(xCol)) {
              numDummyCols += categoricalMap.get(xCol).keySet().size - 2
            }
          }
        }
      }

//      val Y = new Vector(numRows)
//      val X = new Matrix(numRows, numXCols + numDummyCols) // this allocation won't be feasible for sparse features
      val (matrices, vectors) = splitMatrixVector(numRows, numXCols + numDummyCols)

      LOG.info("tablePartitiontoMapper: numRows = {}, null bitmap cardinality = {}, xCols = {}, nunNewFeatures = {}",
        numRows.toString, nullBitmap.cardinality().toString, util.Arrays.toString(xCols), numDummyCols.toString)

      // fill in the first X column with bias value
      fillConstantColumn(matrices, 0, 1.0)

      // fill Y
      val yColumnIter = usedColumnIterators.last

      fillNumericColumn(vectors, 0, yColumnIter, nullBitmap) // TODO: has caller checked that yCol is numeric?

      // fill the rest of X, column by column (except for the dummy columns, which is filled at a later pass)
      var i = 1 // column index in X matrix
      while (i < numXCols) {
        val columnIterator = usedColumnIterators(i - 1)
        val xColId = xCols(i - 1)
        val columnAccessor = ColumnAccessor(columnIterator).asInstanceOf[NativeColumnAccessor[_]]
        val columnType = columnAccessor.columnType

        columnType match {
          case INT | LONG | FLOAT | DOUBLE | BOOLEAN | BYTE | SHORT => {
            LOG.info("extracting numeric column id {}, columnType {}", xColId, columnType.toString)

            if (categoricalMap != null && categoricalMap.containsKey(xColId)) {
              val columnMap = categoricalMap.get(xColId)
              LOG.info(s">>>> columnMap = null??? ${columnMap == null}")
              LOG.info("extracting categorical column id {} using mapping {}", xColId, columnMap)

              fillColumnWithConversion(matrices, i, columnIterator, nullBitmap, (current: Object) => {
                // invariant: columnMap.contains(x)
                val k = current.toString
                columnMap.get(k)
              })

            } else {
              fillNumericColumn(matrices, i, columnIterator, nullBitmap)
            }
          }
          case STRING => {
            if (categoricalMap != null && categoricalMap.containsKey(xColId)) {
              val columnMap = categoricalMap.get(xColId)
              LOG.info("extracting STRING column id {} using mapping {}", xColId, columnMap)

              fillColumnWithConversion(matrices, i, columnIterator, nullBitmap, (current: Object) => {
                // invariant: columnMap.contains(x)
                val k = current.toString
                columnMap.get(k)
              })

            } else {
              throw new RuntimeException("got STRING column but no categorical map")
            }
          }
          case _ => {
            throw new RuntimeException("don't know how to vectorize this column type: xColId = " + xColId + ", " + columnType.getClass.toString)
          }
        }

        i += 1
      }

      //new TupleMatrixVector(X, Y)
      (matrices zip vectors).map{case (mat, vec) => new TupleMatrixVector(mat, vec)}
    }
  }

  def instrument[InputType](xCols: Array[Int], mapping: HashMap[java.lang.Integer, HashMap[String, java.lang.Double]])(inputRow: TupleMatrixVector): TupleMatrixVector = {

    //so we need to do minus one for original column
    var oldX = inputRow._1
    var oldY = inputRow._2

    //add dummy columns
    val numCols = oldX.columns
    var numRows = oldX.rows
    var newColumnMap = new Array[Int](numCols)

    //row transformer
    var trRow = new TransformRow(xCols, mapping)

    //for each row
    var indexRow = 0
    var currentRow = null.asInstanceOf[Matrix]
    var newRowValues = null.asInstanceOf[DoubleMatrix]
    while (indexRow < oldX.rows) {

      //for each rows
      currentRow = Matrix(oldX.getRow(indexRow))
      newRowValues = trRow.transform(currentRow)
      //add new row
      oldX.putRow(indexRow, newRowValues)

      //convert oldX to new X
      indexRow += 1
    }
    new TupleMatrixVector(oldX, oldY)
  }
}
