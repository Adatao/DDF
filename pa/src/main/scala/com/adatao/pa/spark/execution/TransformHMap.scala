package com.adatao.pa.spark.execution


import java.lang.{ Integer => JInt }
import java.lang.{Double => JDouble}
import com.adatao.pa.AdataoException
import com.adatao.pa.AdataoException.AdataoExceptionCode
import scala.collection.JavaConversions._
import io.ddf.content.Schema
import io.ddf.DDF
import io.ddf.content.Schema.Column
import java.util
import scala.util
import com.adatao.pa.spark.Utils.DataFrameResult
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.catalyst.expressions.Row
import com.adatao.spark.ddf.{SparkDDFManager, SparkDDF}

/**
 * author: daoduchuan
 */
class TransformHMap(dataContainerID: String, keyValMapID: String) extends AExecutor[DataFrameResult] {

  override def runImpl(ctx: ExecutionContext): DataFrameResult = {
    val ddf = ctx.sparkThread.getDDFManager.getDDF(dataContainerID)
    val keyValMap = ctx.sparkThread.getDDFManager.asInstanceOf[SparkDDFManager].getMap(keyValMapID)
    val keyValueMap = keyValMap.toMap

    LOG.info(">>> keyValueMap = " + keyValueMap.keySet.mkString(", "))
    val rddRow = ddf.getRepresentationHandler.get(classOf[RDD[_]], classOf[Row]).asInstanceOf[RDD[Row]]
    val numCols = ddf.getNumColumns
    LOG.info(">>>> numCols = " + numCols)
    val colTypes = ddf.getSchemaHandler.getColumns.map{col => col.getType}
    val newRDD = rddRow.map{
      row => {
        var idx = 0
        val arr = new Array[Double](numCols)
        var isNull = false
        while(!isNull && idx < numCols) {
          val value = row.get(idx)
          if(keyValueMap.get(idx) != None) {
            arr(idx) = keyValueMap.get(idx).get.get(value.toString)
          } else {
            arr(idx) =
              if(row.isNullAt(idx)) {
                isNull = true
                0.0
              } else if(colTypes(idx) == Schema.ColumnType.INT) {
                row.getInt(idx).toDouble
              } else if(colTypes(idx) == Schema.ColumnType.DOUBLE) {
                row.getDouble(idx)
              } else if(colTypes(idx) == Schema.ColumnType.FLOAT) {
                row.getFloat(idx).toDouble
              } else if(colTypes(idx) == Schema.ColumnType.LONG) {
                row.getLong(idx).toDouble
              } else {
                0.0
              }
          }
          idx += 1
        }
        if(isNull) null else Row(arr: _*)
      }
    }.filter(row => row != null)

    val manager = ctx.sparkThread.getDDFManager

    val columns = ddf.getSchemaHandler.getColumns
    columns.map{col => col.setType(Schema.ColumnType.DOUBLE)}
    val schema = new Schema(columns)
    val newDDF = manager.newDDF(manager, newRDD, Array(classOf[RDD[_]], classOf[Row]), manager.getNamespace, null, schema)
    manager.addDDF(newDDF)
    new DataFrameResult(newDDF)
  }
}
