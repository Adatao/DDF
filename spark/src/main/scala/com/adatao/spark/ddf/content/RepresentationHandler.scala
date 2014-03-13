/**
 *
 */
package com.adatao.spark.ddf.content

import com.adatao.ddf.content.IHandleRepresentations
import com.adatao.ddf.content.Schema.ColumnType
import java.lang.Class
import scala.reflect.Manifest
import org.apache.spark.rdd.RDD
import scala.collection.JavaConversions._
import shark.api.Row
import com.adatao.ddf.{ DDF, DDFManager }
import org.apache.spark.mllib.regression.LabeledPoint
import com.adatao.spark.ddf.content.RepresentationHandler._
import com.adatao.ddf.exception.DDFException
import org.apache.spark.mllib.regression.LabeledPoint
import com.adatao.ddf.content.{ RepresentationHandler ⇒ RH }
import com.adatao.ddf.content.RepresentationHandler.NativeTable

/**
 * RDD-based SparkRepresentationHandler
 *
 */

class RepresentationHandler(mDDF: DDF) extends RH(mDDF) {

	override def getDefaultDataType: Array[Class[_]] = Array(classOf[RDD[_]], classOf[Row])

	/**
	 * Converts from an RDD[Row] to any representation
	 */
	override def createRepresentation(typeSpecs: Array[Class[_]]): Object = this.fromRDDRow(typeSpecs)

	protected def fromRDDRow(typeSpecs: Array[Class[_]]): Object = {
		val schemaHandler = mDDF.getSchemaHandler
    val numCols = schemaHandler.getNumColumns.toInt
		val srcRdd = this.toRDDRow
    val mappers: Array[Object => Double] = (schemaHandler.getColumns.map(column => getDoubleMapper(column.getType))).toArray

		RH.getKeyFor(typeSpecs) match {
			case RDD_ARRAY_OBJECT ⇒ rowsToArraysObject(srcRdd, numCols, Array.fill[Object => Object](numCols)(obj => obj))
			case RDD_ARRAY_DOUBLE ⇒ rowsToArraysDouble(srcRdd, numCols, mappers)
			case RDD_LABELED_POINT ⇒ rowsToLabeledPoints(srcRdd, numCols, mappers)
			case RH.NATIVE_TABLE ⇒ rowsToNativeTable(mDDF, srcRdd, numCols)
			case _ ⇒ throw new DDFException(String.format("TypeSpecs %s not supported", RH.getKeyFor(typeSpecs)))
		}
	}

	/**
	 * Converts to an RDD[Row] from any representation
	 */
	protected def toRDDRow: RDD[Row] = {
		val rdd = this.get(classOf[RDD[_]], classOf[Row]).asInstanceOf[RDD[Row]]
		if (rdd != null) return rdd

		// Try to convert from any known representation

		val nativeTable = this.get(classOf[NativeTable]).asInstanceOf[NativeTable]
		if (nativeTable != null) {
			return null // TODO
		}

		val arraysObject = this.get(classOf[RDD[_]], classOf[Array[_]], classOf[Object]).asInstanceOf[RDD[Array[Object]]]
		if (arraysObject != null) {
			return null // TODO
		}

		val arraysDouble = this.get(classOf[RDD[_]], classOf[Array[_]], classOf[Double]).asInstanceOf[RDD[Array[Double]]]
		if (arraysDouble != null) {
			return null // TODO
		}

		val labeledPoints = this.get(classOf[RDD[_]], classOf[LabeledPoint]).asInstanceOf[RDD[LabeledPoint]]
		if (labeledPoints != null) {
			return null // TODO
		}

		null.asInstanceOf[RDD[Row]]
	}

	/**
	 * Sets a new and unique representation for our {@link DDF}, clearing out any existing ones
	 */
	def set[T](data: RDD[T])(implicit m: Manifest[T]) = {
		this.reset
		this.add(data)
	}

	/**
	 * Adds a new and unique representation for our {@link DDF}, keeping any existing ones
	 */
	def add[T](data: RDD[T])(implicit m: Manifest[T]): Unit = this.add(data, classOf[RDD[_]], m.erasure)

	private def forAllReps[T](f: RDD[_] ⇒ Any) {
		mReps.foreach {
			kv ⇒ if (kv._2 != null) f(kv._2.asInstanceOf[RDD[_]])
		}
	}

	override def cacheAll = {
		forAllReps({
			rdd: RDD[_] ⇒
				if (rdd != null) {
					mLog.info(this.getClass() + ": Persisting " + rdd)
					rdd.persist
				}
		})
	}

	override def uncacheAll = {
		forAllReps({
			rdd: RDD[_] ⇒
				if (rdd != null) {
					mLog.info(this.getClass() + ": Unpersisting " + rdd)
					rdd.unpersist(false)
				}
		})
	}
}

object RepresentationHandler {
	/**
	 * Supported Representations
	 */
	val RDD_ARRAY_DOUBLE = RH.getKeyFor(Array(classOf[RDD[_]], classOf[Array[Double]]))
	val RDD_ARRAY_OBJECT = RH.getKeyFor(Array(classOf[RDD[_]], classOf[Array[Object]]))
	val RDD_LABELED_POINT = RH.getKeyFor(Array(classOf[RDD[_]], classOf[LabeledPoint]))

	/**
	 *
	 */
	def rowsToArraysObject(rdd: RDD[Row], numCols: Int, mappers: Array[Object => Object]): RDD[Array[Object]] = {
		rdd.map { row ⇒ rowToArray(row, classOf[Object], new Array[Object](numCols), mappers) }
	}

	def rowsToArraysDouble(rdd: RDD[Row], numCols: Int, mappers: Array[Object => Double]): RDD[Array[Double]] = {
		rdd.map { row ⇒ rowToArray(row, classOf[Double], new Array[Double](numCols), mappers) }
	}

	def rowsToLabeledPoints(rdd: RDD[Row], numCols: Int, mappers: Array[Object => Double]): RDD[LabeledPoint] = {
		rdd.map(row ⇒ {
			val features = rowToArray(row, classOf[Double], new Array[Double](numCols - 1), mappers)
			val label = mappers(numCols - 1)(row.getPrimitive(numCols - 1)).asInstanceOf[Double]
			new LabeledPoint(label, features)
		})
	}

	def rowToArray[T](row: Row, columnClass: Class[_], array: Array[T], mappers: Array[Object ⇒ T]): Array[T] = {
		var i = 0
		while (i < array.size) {
			array(i) = mappers(i)(row.getPrimitive(i))
			i += 1
		}
		array
	}

  def getDoubleMapper(colType: ColumnType): Object => Double = colType match {
    case ColumnType.DOUBLE => {
      case obj => obj.asInstanceOf[Double]
    }

    case ColumnType.INT => {
      case obj => obj.asInstanceOf[Int].toDouble
    }

    case ColumnType.STRING => {
      case _   => throw new DDFException("Cannot convert string to double")
    }
  }

	def rowsToNativeTable(ddf: DDF, rdd: RDD[Row], numCols: Int): NativeTable = null // TODO
}