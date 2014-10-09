package com.adatao.spark.content

import io.ddf.content.Schema
import io.ddf.DDF
import scala.collection.mutable.ArrayBuffer
import scala.collection.JavaConversions._

import org.apache.spark.rdd.RDD
import io.ddf.content.Schema.ColumnType
import io.spark.ddf.{SparkDDF, SparkDDFManager}
import org.apache.commons.lang.math.NumberUtils

/**
  */
object LoadFileUtils {

  def loadFile(manager: SparkDDFManager, fileURL: String, schema: Schema, separator: String): DDF = {
    val rdd: RDD[String] = manager.getSparkContext.textFile(fileURL)

    val rddArrObj: RDD[Array[Object]] = rdd.map {
      row => {
        val arrStr = row.split(separator)
        val cols = schema.getColumns
        val arrObj = ArrayBuffer[Object]()
        var idx = 0
        for (col <- cols) {
          arrObj += (col.getType match {
            case ColumnType.INT => {
              if (NumberUtils.isNumber(arrStr(idx))) {
                arrStr(idx).toInt.asInstanceOf[Object]
              } else {
                null
              }
            }
            case ColumnType.DOUBLE => {
              if (NumberUtils.isNumber(arrStr(idx))) {
                arrStr(idx).toDouble.asInstanceOf[Object]
              } else {
                null
              }
            }
            case ColumnType.STRING => try {
              arrStr(idx).asInstanceOf[Object]
            } catch {
              case e: Throwable => null
            }
          })
          idx += 1
        }
        arrObj.toArray
      }
    }

    val ddf = new SparkDDF(manager, rddArrObj, classOf[Array[Object]], manager.getNamespace, null, schema)
    val tableName = ddf.getSchemaHandler.newTableName()
    schema.setTableName(tableName)
    ddf.setName(tableName)
    return ddf
  }
}

/*
if (NumberUtils.isNumber (str) ) {
number = Double.parseDouble (str)
s.merge (number)
}
else {
result (i) = null
}
*/