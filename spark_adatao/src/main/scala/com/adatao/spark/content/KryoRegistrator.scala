package com.adatao.spark.content

import com.esotericsoftware.kryo.Kryo
import org.apache.spark.serializer.{KryoRegistrator => SparkKryoRegistrator}

import com.esotericsoftware.kryo.serializers.{ JavaSerializer => KryoJavaSerializer, FieldSerializer }
import io.ddf.types.Matrix
import io.ddf.types.Vector
import io.spark.ddf.analytics._
import io.spark.ddf.content._
import io.spark.ddf.ml.ROCComputer
import org.jblas.DoubleMatrix
import org.rosuda.REngine.REXP
import org.rosuda.REngine.RList
import io.ddf.ml.RocMetric
import io.ddf.types.Vector
import io.ddf.types.Matrix
import io.spark.ddf.ml.ROCComputer
import io.ddf.ml.RocMetric

class KryoRegistrator extends SparkKryoRegistrator {
  override def registerClasses(kryo: Kryo) {
    kryo.register(classOf[Vector])
    kryo.register(classOf[Matrix])
    kryo.register(classOf[DoubleMatrix])
    kryo.register(classOf[ROCComputer])
    kryo.register(classOf[RocMetric])
    kryo.register(classOf[REXP])
    kryo.register(classOf[RList], new FieldSerializer(kryo, classOf[RList]))
  }
}
