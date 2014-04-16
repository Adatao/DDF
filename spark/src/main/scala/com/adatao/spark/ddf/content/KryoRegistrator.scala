package com.adatao.spark.content

import com.esotericsoftware.kryo.Kryo
import shark.{ KryoRegistrator => SharkKryoRegistrator }
import com.esotericsoftware.kryo.serializers.{ JavaSerializer => KryoJavaSerializer, FieldSerializer }
import com.adatao.ddf.types.Matrix
import com.adatao.ddf.types.Vector
import com.adatao.spark.ddf.analytics._
import com.adatao.spark.ddf.content._
import com.adatao.spark.ddf.ml.ROCComputer
import org.jblas.DoubleMatrix
import com.adatao.spark.ddf.ml.pa.TempCalculationValue

class KryoRegistrator extends SharkKryoRegistrator {
  override def registerClasses(kryo: Kryo) {
    kryo.register(classOf[Vector])
    kryo.register(classOf[Matrix])
    kryo.register(classOf[ALossFunction])
    kryo.register(classOf[DoubleMatrix])
    kryo.register(classOf[LogisticRegressionIRLS.LossFunction])
    kryo.register(classOf[ROCComputer])
    kryo.register(classOf[LossFunction],
      new FieldSerializer[Nothing](kryo, classOf[LossFunction]))
    kryo.register(classOf[TempCalculationValue])
    kryo.register(classOf[LogisticRegressionModel])
    kryo.register(classOf[IRLSLogisticRegressionModel])
    super.registerClasses(kryo)
  }
}