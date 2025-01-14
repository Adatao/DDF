package com.adatao.spark.ddf.analytics

import io.spark.ddf.SparkDDF
import io.ddf.content.Schema
import scala.collection.JavaConversions._
import org.junit.Assert._
import com.adatao.spark.ddf.ATestSuite

import io.ddf.DDF
import com.adatao.spark.ddf.etl.{TransformDummy, TransformationHandler}
import io.ddf.types.TupleMatrixVector
import com.adatao.spark.ddf.etl.TransformationHandler._
import io.ddf.types.Vector
import org.apache.spark.sql.columnar.{NativeColumnAccessor, ColumnAccessor, CachedBatch}
import java.nio.ByteBuffer
import org.apache.spark.sql.catalyst.expressions.{GenericMutableRow, GenericRow}

class TransformSuite extends ATestSuite {

  createTableAirlineSmall()
  test("dummy coding") {
    val ddf: DDF = manager.sql2ddf("select * from airline")

    val ddf2 = (ddf.getTransformationHandler()).dummyCoding(Array("origin"), "arrdelay")

    val rdd = ddf2.asInstanceOf[SparkDDF].getRDD(classOf[TupleMatrixVector])
    val a = rdd.collect()
    val rdd2 = ddf.asInstanceOf[SparkDDF].getRDD(classOf[CachedBatch])

    val collection = rdd2.collect()
    collection.map{
      case CachedBatch(buffers, row) => {
        println("buffer.count = " + buffers.size)
        println("buffers(0). count = " + buffers(0).size)
        println("row = " + row.mkString(", "))
        val columnAccessors = buffers.map{buffer => ColumnAccessor(ByteBuffer.wrap(buffer))}

        val colAccessor = columnAccessors(3).asInstanceOf[NativeColumnAccessor[_]]
        val colType = colAccessor.columnType
        val buffer = colAccessor.buffer
        println(">>>> colType = " + colType.toString())
        val mutableRow = new GenericMutableRow(1)
        while(colAccessor.hasNext) {
           colAccessor.extractSingle(mutableRow, 0)
           val value = mutableRow.get(0)
           println(">>>> value = " + value)
        }
      }
    }

    var m = a(0)._1

    assertEquals(m.getRows(), 16, 0.0)
    assertEquals(m.getColumns(), 3, 0.0)
    //first row
    assertEquals(m(0, 0), 1.0, 0.0)
    assertEquals(m(0, 1), 0.0, 0.0)
    assertEquals(m(0, 2), 1.0, 0.0)

    //second row
    assertEquals(m(1, 2), 1.0, 0.0)

    //second partition
    m = a(1)._1
    //first row
    assertEquals(m(0, 0), 1.0, 0.0)
    assertEquals(m(0, 1), 0.0, 0.0)
    assertEquals(m(0, 2), 0.0, 0.0)

    assertEquals(m(3, 0), 1.0, 0.0)
    assertEquals(m(3, 1), 1.0, 0.0)
    assertEquals(m(3, 2), 0.0, 0.0)

    //arrdelay
    val n = a(0)._2
    assertEquals(n(0), -14.0, 0.0)
  }
  test("test ytrueypred") {
    //createTableAirline
    class DummyModel(weights: Vector, numSamples: Long) extends ALinearModel[Double](weights, numSamples) {
      override def predict(features: Vector) = {
        this.linearPredictor(features)
      }
    }

    val ddf = manager.sql2ddf("select * from airline")
    val dummyCodingDDF = ddf.getTransformationHandler.dummyCoding(Array("year", "month", "dayofmonth"), "arrdelay")

    val model = new DummyModel(Vector(Array(0.5,0.1, 0.2, 0.3)), 100)
    val rddMatrixVector = dummyCodingDDF.asInstanceOf[SparkDDF].getRDD(classOf[TupleMatrixVector])
    assert(rddMatrixVector.count() > 0)
    val rdd = model.yTrueYPred(rddMatrixVector)
    assert(rdd.count() == 31)
  }

  test("test DummyCoding handling NA") {
    createTableAirlineWithNA()
    val ddf = manager.sql2ddf("select * from airlineWithNA")
    val ddf2 = (ddf.getTransformationHandler()).dummyCoding(Array("year"), "arrdelay")
    val rdd = ddf2.asInstanceOf[SparkDDF].getRDD(classOf[TupleMatrixVector])
    rdd.count
  }

  test("Test TransformDummy with NA") {
    createTableKmeans()
    val ddf = manager.sql2ddf("select * from kmeans")
    val ddf2 = (ddf.getTransformationHandler()).dummyCoding(Array("v1", "v3"), "v2")
    val rdd = ddf2.asInstanceOf[SparkDDF].getRDD(classOf[TupleMatrixVector])
    rdd.count
  }

  test("check validity of TransformDummy") {
    createTableTransformTest()
    val ddf3 = manager.sql2ddf("select * from transformTest")
    val ddf4 = (ddf3.getTransformationHandler()).dummyCoding(Array("v1"), "v2")
    val rdd2 = ddf4.asInstanceOf[SparkDDF].getRDD(classOf[TupleMatrixVector])
    LOG.info(">>>> rdd2.count = " + rdd2.count)
    val tupleMatrix = rdd2.collect()
    val matrix1 = tupleMatrix(0)._1
    val vector1 = tupleMatrix(0)._2
    assert(matrix1.rows == 6)
    assert(matrix1(0, 1) == 10.0)
    assert(matrix1(1, 1) == 5.0)
    assert(matrix1(2, 1) == 8.0)
    assert(matrix1(3, 1) == 8.0)
    assert(matrix1(4, 1) == -10.0)
    assert(matrix1(4, 1) == -10.0)

    assert(vector1.rows == 6)
    assert(vector1(0) == -5.0)
    assert(vector1(1) == -10.0)
    assert(vector1(2) == -9.0)
    assert(vector1(3) == -10.0)
    assert(vector1(4) == 4.0)
    assert(vector1(5) == 8.0)

    LOG.info(">>> matrix1 = " + matrix1.toString())
    LOG.info(">>> vector1 = " + vector1.toString())

    val matrix2 = tupleMatrix(1)._1
    val vector2 = tupleMatrix(1)._2
    LOG.info(">>> matrix2 = " + matrix2.toString())
    LOG.info(">>> vector2 = " + vector2.toString())
    assert(matrix2.rows == 21)
    assert(matrix2(0, 1) == -9.0)
    assert(matrix2(1, 1) == 4.0)
    assert(matrix2(2, 1) == 10.0)
    assert(matrix2(3, 1) == 10.0)
    assert(matrix2(4, 1) == -10.0)

    assert(vector2.rows == 21)
    assert(vector2(0) == 8.0)
    assert(vector2(1) == 3.0)
    assert(vector2(2) == 4.0)
    assert(vector2(3) == 6.0)
    assert(vector2(4) == -10.0)
    assert(vector2(5) == 6.0)
  }

  test("check validity of TransformDummy with blow up factor") {
    createTableTransformTest()
    TransformDummy.blowUpFactor= 4
    val ddf3 = manager.sql2ddf("select * from transformTest")
    val ddf4 = (ddf3.getTransformationHandler()).dummyCoding(Array("v1"), "v2")
    val rdd2 = ddf4.asInstanceOf[SparkDDF].getRDD(classOf[TupleMatrixVector])
    LOG.info(">>>> rdd2.count = " + rdd2.count)
    val tupleMatrix = rdd2.collect()
    LOG.info(">>> tupleMatrix.size = " + tupleMatrix.size)
    val matrix0 = tupleMatrix(0)._1
    val vector0 = tupleMatrix(0)._2
    assert(matrix0.rows == 1)
    assert(matrix0(0, 0) == 1.0)
    assert(matrix0(0, 1) == 10.0)
    assert(vector0.rows == 1)
    assert(vector0(0) == -5.0)
    
    val matrix1 = tupleMatrix(1)._1
    val vector1 = tupleMatrix(1)._2
    assert(matrix1.rows == 1)
    assert(matrix1(0, 0) == 1.0)
    assert(matrix1(0, 1) == 5.0)
    assert(vector1(0) == -10.0) 
    
    val matrix2 = tupleMatrix(2)._1
    val vector2 = tupleMatrix(2)._2
    assert(matrix2(0, 0) == 1.0)
    assert(matrix2(0, 1) == 8.0)
    assert(matrix2.rows == 1)
    assert(vector2(0) == -9.0)
    LOG.info(">>> matrix1 = " + matrix1.toString())
    LOG.info(">>> vector1 = " + vector1.toString())
    LOG.info(">>> matrix2 = " + matrix2.toString())
    LOG.info(">>> vector2 = " + vector2.toString())
    
    val matrix3 = tupleMatrix(3)._1
    val vector3 = tupleMatrix(3)._2
    assert(matrix3.rows == 1)
    assert(matrix3(0, 0) == 1.0)
    assert(matrix3(0, 1) == 8.0)
    assert(vector3(0) == -10.0)    

    val matrix4 = tupleMatrix(4)._1
    val vector4 = tupleMatrix(4)._2
    assert(matrix4.rows == 2)
    assert(vector4.rows == 2)
    assert(matrix4(0, 0) == 1.0)
    assert(matrix4(0, 1) == -10.0)
    assert(matrix4(1, 0) == 1.0)
    assert(matrix4(1, 1) == -10.0)
    assert(vector4(0) == 4.0)
    assert(vector4(1) == 8.0)
  }
}
