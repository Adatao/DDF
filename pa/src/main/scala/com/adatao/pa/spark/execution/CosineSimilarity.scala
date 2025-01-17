package com.adatao.pa.spark.execution

import com.adatao.spark.ddf.content.RepresentationHandler
import org.apache.spark.graphx._
import com.twitter.algebird.{BloomFilterMonoid, BF}
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.linalg.Vector
import breeze.linalg.{norm, SparseVector}
import scala.collection.mutable.ArrayBuffer
import org.apache.spark.sql.catalyst.expressions.{GenericMutableRow, Row}
import com.adatao.pa.spark.Utils.DataFrameResult
import io.ddf.content.Schema.Column
import io.ddf.content.Schema
import com.adatao.spark.ddf.{SparkDDF, SparkDDFManager}
import org.slf4j.LoggerFactory
import io.ddf.{DDFManager, DDF}
import org.apache.spark.sql.columnar.{ColumnAccessor, CachedBatch}
import java.nio.ByteBuffer
import com.twitter.algebird.BloomFilterMonoid
import org.apache.spark.sql.columnar.CachedBatch
import scala.Tuple2
import org.apache.spark.SparkContext._

/**
 * author: daoduchuan
 */
class CosineSimilarity(dataContainerID1: String, dataContainerID2: String, val threshold: Double, val filterDup: Boolean = true) extends AExecutor[DataFrameResult] {

  override def runImpl(context: ExecutionContext): DataFrameResult = {
    val manager = context.sparkThread.getDDFManager
    val sparkCtx: SparkContext = manager.asInstanceOf[SparkDDFManager].getSparkContext
    val ddf1 = manager.getDDF(dataContainerID1)
    val ddf2 = manager.getDDF(dataContainerID2)

    val (ddf11, ddf22) = if(filterDup) {
      CosineSimilarity.symmetricDifference2DDFs(ddf1, ddf2, ddf1.getColumnNames.get(0), manager)
    } else {
      (ddf1, ddf2)
    }

    val rdd1 = ddf11.asInstanceOf[SparkDDF].getRDD(classOf[Row])
    val rdd2 = ddf22.asInstanceOf[SparkDDF].getRDD(classOf[Row])
    val matrix1 = CosineSimilarity.rddRow2Matrix(rdd1)
    val matrix2 = CosineSimilarity.rddRow2Matrix(rdd2)

    val result: RDD[Row] = CosineSimilarity.cosineSim(matrix1, matrix2, threshold, sparkCtx)

    val col1 = new Column("caller_1", Schema.ColumnType.LONG)
    val col2 = new Column("caller_2", Schema.ColumnType.LONG)
    val col3 = new Column("score", Schema.ColumnType.DOUBLE)

    val schema = new Schema(null, Array(col1, col2, col3))

    val newDDF = manager.newDDF(manager, result, Array(classOf[RDD[_]], classOf[Row]), manager.getNamespace, null, schema)

    manager.addDDF(newDDF)
    new DataFrameResult(newDDF)
  }
}

object CosineSimilarity {
  val LOG = LoggerFactory.getLogger(this.getClass)
  //Calculate the symmetricDifference of vertices iin graph1 and graph2
  def cosineSim(matrix1: RDD[(Long, SparseVector[Double])], matrix2: RDD[(Long, SparseVector[Double])], threshold: Double,
                sparkCtx: SparkContext): RDD[Row] = {
    val nrow1 = matrix1.count
    val nrow2 = matrix2. count
    LOG.info(">>> matrix1.count = " + nrow1)
    LOG.info(">>> matrix2.count = " + nrow2)
    var switchedOrder = false
    val (distMatrix, localMatrix) = if(nrow1 >= nrow2) {
      (matrix1, matrix2.collect())
    } else {
      switchedOrder = true
      (matrix2, matrix1.collect())
    }

    val broadcastMatrix: Broadcast[Array[(Long, SparseVector[Double])]] = sparkCtx.broadcast(localMatrix)
    distMatrix.mapPartitions {
      (iter: Iterator[(Long, SparseVector[Double])]) => {
        //val arr: ArrayBuffer[Tuple3[String, String, Double]] =  ArrayBuffer[Tuple3[String, String, Double]]()
        val arr: ArrayBuffer[Row] = ArrayBuffer[Row]()
        while(iter.hasNext) {
          val (num1, vector1) = iter.next()
          val mat: Array[(Long, SparseVector[Double])] = broadcastMatrix.value
          var i = 0
          while(i < mat.size) {
            val vector2 = mat(i)._2
            val num2 = mat(i)._1
            val mul: Double = vector2.dot(vector1)

            val cosine = mul / (CosineSimilarity.normVector(vector1) * CosineSimilarity.normVector(vector2))
            //            println(s">>>> vector2 = ${vector2.toString()}")
            //            println(s">>>> vector1 = ${vector1.toString()}")
            //            println(s">>> mul = $mul")
            //            println(s">>> num1 = $num1 , num2=$num2")
            //            println(">>>> cosine = " + cosine)
            //only append to result if cosine > threshold
            if(cosine > threshold) {
              if(!switchedOrder) {
                arr append Row(num1, num2, cosine)
              } else {
                arr append Row(num2, num1, cosine)
              }
            }
            i += 1
          }
        }
        arr.toIterator
      }
    }
  }

  def symmetricDifference(graph1: Graph[String, Double], graph2: Graph[String, Double], sparkCtx: SparkContext) = {
    val vertices1 = graph1.vertices
    val vertices2 = graph2.vertices
    //Create a BloomFilter for all vertices in graph1
    val vertice1BF = CosineSimilarity.createBloomFilter(vertices1)

    //Create a BloomFilter for all vertices in graph2
    val vertice2BF = CosineSimilarity.createBloomFilter(vertices2)
    LOG.info("vertice1BF.size = " + vertice1BF.size.estimate)
    LOG.info("vertice2BF.size = " + vertice2BF.size.estimate)
    LOG.info("vertices1.size = " + vertices1.count())
    LOG.info("vertices2.size = " + vertices2.count())

    val broadcastVBF1 = sparkCtx.broadcast(vertice1BF)
    val broadcastVBF2 = sparkCtx.broadcast(vertice2BF)

    //Filter number that in graph2 but not in graph1
    val filteredGraph2 = graph2.subgraph(epred = (edge => !broadcastVBF1.value.contains(edge.srcAttr).isTrue))

    //Filter number that in graph1 but not in graph2
    val filteredGraph1 = graph1.subgraph(epred = (edge => !broadcastVBF2.value.contains(edge.srcAttr).isTrue ))
    Tuple2(filteredGraph1, filteredGraph2)
  }

  def symmetricDifference2Graphs(ddf1: DDF, ddf2: DDF, colName: String, sparkCtx: SparkContext) = {
    val BF1 = createBloomFilterFromDDF(ddf1, colName)
    val BF2 = createBloomFilterFromDDF(ddf2, colName)
    val broadcastedBF1 = sparkCtx.broadcast(BF1)
    val broadcastedBF2 = sparkCtx.broadcast(BF2)

    val colIdx1 = ddf1.getColumnIndex(colName)
    val colIdx2 = ddf2.getColumnIndex(colName)
    //filter out caller in ddf1 that exists in ddf2
    val rddRow1 = ddf1.asInstanceOf[SparkDDF].getRDD(classOf[Row]).filter {
      row => !broadcastedBF2.value.contains(row.getLong(colIdx1).toString).isTrue
    }

    //filter out caller in ddf2 that exists in ddf1
    val rddRow2 = ddf2.asInstanceOf[SparkDDF].getRDD(classOf[Row]).filter {
      row => !broadcastedBF1.value.contains(row.getLong(colIdx2).toString).isTrue
    }

    (graphFromRDDRow(rddRow1, sparkCtx), graphFromRDDRow(rddRow2, sparkCtx))
  }

  def symmetricDifference2DDFs(ddf1: DDF, ddf2: DDF, colName: String, manager: DDFManager): Tuple2[DDF, DDF] = {
    val sparkCtx: SparkContext = manager.asInstanceOf[SparkDDFManager].getSparkContext
    val BF1 = createBloomFilterFromDDF(ddf1, colName)
    val BF2 = createBloomFilterFromDDF(ddf2, colName)
    val broadcastedBF1 = sparkCtx.broadcast(BF1)
    val broadcastedBF2 = sparkCtx.broadcast(BF2)

    val colIdx1 = ddf1.getColumnIndex(colName)
    val colIdx2 = ddf2.getColumnIndex(colName)
    //filter out caller in ddf1 that exists in ddf2
    val rddRow1 = ddf1.asInstanceOf[SparkDDF].getRDD(classOf[Row]).filter {
      row => !broadcastedBF2.value.contains(row.getLong(colIdx1).toString).isTrue
    }

    //filter out caller in ddf2 that exists in ddf1
    val rddRow2 = ddf2.asInstanceOf[SparkDDF].getRDD(classOf[Row]).filter {
      row => !broadcastedBF1.value.contains(row.getLong(colIdx2).toString).isTrue
    }
    val col1 = ddf1.getSchemaHandler.getColumns.get(0)
    val col2 = ddf1.getSchemaHandler.getColumns.get(1)
    val col3 = ddf1.getSchemaHandler.getColumns.get(2)
    val schema1 = new Schema(null, Array(col1, col2, col3))
    val schema2 = new Schema(null, Array(col1, col2, col3))

    val newDDF1 = manager.newDDF(manager, rddRow1, Array(classOf[RDD[_]], classOf[Row]), manager.getNamespace, null, schema1)
    val newDDF2 = manager.newDDF(manager, rddRow2, Array(classOf[RDD[_]], classOf[Row]), manager.getNamespace, null, schema2)
    (newDDF1, newDDF2)
  }

  //Create graph from RDD row
  //assuming caller is col0, callee is col1, tfidf is col2

  def graphFromRDDRow(rdd: RDD[Row], sparkCtx: SparkContext): Graph[String, Double] = {
    val rddRow = rdd.filter {
      row => !(row.isNullAt(0) || row.isNullAt(1) || row.isNullAt(2))
    }
    val rddVertices1: RDD[String] = rddRow.map{row => {row.getString(0)}}
    val rddVertices2: RDD[String] = rddRow.map{row => {row.getString(1)}}
    val edges = rddRow.map {
      row => Edge(GraphTFIDF.hash(row.getString(0)), GraphTFIDF.hash(row.getString(1)), row.getDouble(2))
    }
    val vertices: RDD[(Long, String)] = sparkCtx.union(rddVertices1, rddVertices2).map{str => (GraphTFIDF.hash(str), str)}

    val graph: Graph[String, Double] = Graph(vertices, edges)
    val partitionedGraph = graph.partitionBy(PartitionStrategy.EdgePartition1D)
    partitionedGraph
  }

  def createBloomFilterFromDDF(ddf: DDF, colName: String): BF = {
    val sparkDDF = ddf.asInstanceOf[SparkDDF]
    sparkDDF.cacheTable()
    val rddCachedBatch = sparkDDF.getRDD(classOf[CachedBatch])
    val nrow = sparkDDF.getRDD(classOf[Row]).count()
    val width = nrow * 20
    val numHashes = scala.math.round(scala.math.log(2) * width / nrow).toInt
    val bloomFilterMonoid = BloomFilterMonoid(numHashes, width.toInt, 17)
    val colIdx = ddf.getColumnIndex(colName)
    val bfRDD: RDD[BF] = rddCachedBatch.map{
      cachedBatch => {
        val buffer = ByteBuffer.wrap(cachedBatch.buffers(colIdx))
        val columnAccessor = ColumnAccessor(buffer)
        val mutableRow = new GenericMutableRow(1)
        var bf = bloomFilterMonoid.zero
        while(columnAccessor.hasNext) {
          columnAccessor.extractTo(mutableRow, 0)
          bf = bf + mutableRow.getLong(0).toString
        }
        bf
      }
    }
    bfRDD.reduce{case (bf1, bf2) => bf1 ++ bf2}
  }

  //val numHashes =
  def createBloomFilter(vertexRDD: VertexRDD[String]): BF = {
    //http://hur.st/bloomfilter?n=1000000&p=0.2
    val n = vertexRDD.count()
    val width = n * 20
    val numHashes = scala.math.round(scala.math.log(2) * width / n).toInt
    val bloomFilterMonoid = BloomFilterMonoid(numHashes, width.toInt, 17)
    val bfRDD: RDD[BF] = vertexRDD.mapPartitions {
      (iterator: Iterator[(Long, String)]) => {
        var bf = bloomFilterMonoid.zero
        while(iterator.hasNext) {

          val (id, item) = iterator.next()
          bf = bf + item
        }
        Iterator(bf)
      }
    }
    bfRDD.reduce{case (bf1, bf2) => bf1 ++ bf2}
  }

  def tfIDFGraph2Matrix(graph: Graph[String, Double]): RDD[(String, SparseVector[Double])] = {
    val vertices: VertexRDD[Tuple2[String, Seq[Tuple2[Int, Double]]]] = graph.aggregateMessages (
      (edgeCtx: EdgeContext[String, Double, Tuple2[String, Seq[Tuple2[Int, Double]]]]) => {
        val tfidf = edgeCtx.attr
        val id = scala.math.abs(edgeCtx.dstAttr.hashCode)
        edgeCtx.sendToSrc(edgeCtx.srcAttr, Seq((id, tfidf)))
      },
      (seq1: Tuple2[String, Seq[Tuple2[Int, Double]]], seq2: Tuple2[String, Seq[Tuple2[Int, Double]]]) => Tuple2(seq1._1, seq1._2 ++ seq2._2)
    )

    LOG.info(">>>> tfIDFGraph2Matrix, vertices.count = " + vertices.count())
    val parseMatrix = vertices.map {
      case (id, (num, elements)) => {
        val (indices, values) = elements.sortBy(_._1).unzip
        Tuple2(num, new SparseVector[Double](indices.toArray, values.toArray, Int.MaxValue))
      }
    }
    parseMatrix
  }

  def normVector(vector: SparseVector[Double]): Double = {
    val data = vector.data

    var i = 0
    var norm = 0.0
    while( i < data.size) {
      val value = data(i)
      norm += value * value
      i += 1
    }
    scala.math.sqrt(norm)
  }

  def rddRow2Matrix(rdd: RDD[Row]): RDD[(Long, SparseVector[Double])] = {
    val pairRDD = rdd.map{row => {
      val num = row.getLong(0)
      val idx = scala.math.abs(row.getLong(1).hashCode)
      (num, (idx, row.getDouble(2)))
    }}.groupByKey()
    pairRDD.map {
      case (num, elements) => {
        val (indices, values) = elements.toSeq.sortBy(_._1).unzip
        Tuple2(num, new SparseVector[Double](indices.toArray, values.toArray, Int.MaxValue))
      }
    }
  }
}
