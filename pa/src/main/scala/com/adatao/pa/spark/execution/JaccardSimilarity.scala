package com.adatao.pa.spark.execution

import com.adatao.pa.spark.Utils.DataFrameResult
import com.adatao.spark.ddf.{SparkDDFManager, SparkDDF}
import org.apache.spark.sql.catalyst.expressions.Row
import org.apache.spark.rdd.RDD
import com.twitter.algebird.{MinHasher, MinHasher32, MinHashSignature}
import org.apache.spark.SparkContext._
import org.apache.spark.broadcast.Broadcast
import scala.collection.mutable.ArrayBuffer
import io.ddf.content.Schema.Column
import io.ddf.content.Schema
import org.slf4j.LoggerFactory

/**
 * author: daoduchuan
 */
class JaccardSimilarity(dataContainerID1: String, dataContainerID2: String,
                          val threshold: Double, val maxHashes: Int = 50)
    extends AExecutor[DataFrameResult] {

  override def runImpl(context: ExecutionContext): DataFrameResult = {
    assert(threshold > 0.02, "threshold must be > 0.02")
    //JaccardSimilarity.initializeMinHashes(threshold, maxHashes)
    val manager = context.sparkThread.getDDFManager
    val sparkCtx = manager.asInstanceOf[SparkDDFManager].getSparkContext
    val ddf1 = manager.getDDF(dataContainerID1)
    val ddf2 = manager.getDDF(dataContainerID2)

    val rdd1 = ddf1.asInstanceOf[SparkDDF].getRDD(classOf[Row])
    val rdd2 = ddf2.asInstanceOf[SparkDDF].getRDD(classOf[Row])

    val rddMinHash1 = JaccardSimilarity.rddRow2rddMinHash(rdd1, threshold, maxHashes)
    val rddMinHash2 = JaccardSimilarity.rddRow2rddMinHash(rdd2, threshold, maxHashes)
    val rddRow =  JaccardSimilarity.lsh(rddMinHash1, rddMinHash2, threshold, maxHashes)

    val col1 = new Column("caller_1", Schema.ColumnType.LONG)
    val col2 = new Column("caller_2", Schema.ColumnType.LONG)
    val col3 = new Column("jc_score", Schema.ColumnType.DOUBLE)

    val schema = new Schema(null, Array(col1, col2, col3))

    val newDDF = manager.newDDF(manager, rddRow, Array(classOf[RDD[_]], classOf[Row]), manager.getNamespace, null, schema)
    manager.addDDF(newDDF)
    newDDF.asInstanceOf[SparkDDF].cacheTable()
    new DataFrameResult(newDDF)
  }
}

object JaccardSimilarity {
  val LOG = LoggerFactory.getLogger(this.getClass)

  def initializeMinHashes(threshold: Double, maxHashes: Int) = {
    require(threshold > 0.02, "threshold must be > 0.02")
    val (hash, band) = com.twitter.algebird.MinHasher.pickHashesAndBands(threshold, maxHashes)
    LOG.info(s">>> initialize minHasher with numHashes = $hash, numBands = $band")
    new MinHasher32(hash, band)
  }

  def rddRow2rddMinHash(rdd: RDD[Row], threshold: Double, maxHashes: Int): RDD[(Long, MinHashSignature)] = {
    val minHasher = initializeMinHashes(threshold, maxHashes)
    val pairRDD: RDD[(Long, Long)] = rdd.map{
      row => {
        if(!row.isNullAt(0) && !row.isNullAt(1)) {
          (row.getLong(0), row.getLong(1))
        } else {
          null
        }
      }
    }

    pairRDD.filter(row => row != null).groupByKey().map {
      case (number, elements) => {
        val minhash = elements.map {
          value => minHasher.init(value)
        }.reduce((h1, h2) => minHasher.plus(h1, h2))
        (number, minhash)
      }
    }
  }

  def lsh(rddSignature1: RDD[(Long, MinHashSignature)], rddSignature2: RDD[(Long, MinHashSignature)], threshold: Double, maxHashes: Int) = {
    val buckets1: RDD[(Long, Iterable[Item])] = hashSignature(rddSignature1, threshold, maxHashes)
    val buckets2: RDD[(Long, Iterable[Item])] = hashSignature(rddSignature2, threshold, maxHashes)
    val minHasher = initializeMinHashes(threshold, maxHashes)
    //group buckets1 and buckets2 by bucket id
    //filter out bucket with empty list in one of the lists
    val rdd: RDD[(Long, (Iterable[Item], Iterable[Item]))] = buckets1.join(buckets2).filter {
      case (bucket, (items1, items2)) => (items1.size > 0) || (items2.size > 0)
    }

    // find similar items in each bucket ussing Jaccard Similarity
    val rddPair: RDD[(Long, (Long, Double))] = rdd.flatMap {
      case (bucket, (items1, items2)) => {
        val iterator1= items1.toIterator
        val iterator2 = items2.toIterator
        val similarItems: ArrayBuffer[(Long, (Long, Double))] = new ArrayBuffer[(Long, (Long, Double))]()
        while(iterator1.hasNext) {
          val item1 = iterator1.next()
          while(iterator2.hasNext) {

            val item2 = iterator2.next()
            val score = minHasher.similarity(item1.signature, item2.signature)
            if(score > threshold) {
              similarItems.append((item1.id, (item2.id, score)))
            }
          }
        }
        similarItems
      }
    }

    //filter out similar pair
    //because 2 similar pair can be hash to a same buckets multiple times
    //eg: filter out result like this, and only take 1 row
    //-3351804022671213759,-3351804022671224659,1.0
    //-3351804022671213759,-3351804022671224659,1.0
    //-3351804022671213759,-3351804022671224659,1.0
    //-3351804022671213759,-3351804022671224659,1.0
    //-3351804022671213759,-3351804022671224659,1.0
    val rddPair2 = rddPair.groupByKey().flatMap {
      case (num1, iter) => {
        val sorted = iter.toList.sortBy(_._1)
        var i = 0

        var num2: Long = 0L
        var lastNum2: Long = 0L
        var arrBuffer = new ArrayBuffer[Row]()
        //filter out duplicate
        while(i < sorted.size) {
          num2 = sorted(i)._1
          val score = sorted(i)._2
          if(i > 0) {
            if(num2 != lastNum2) {
              arrBuffer += Row(num1, num2, score)
            }
          } else {
            arrBuffer += Row(num1, num2, score)
          }

          lastNum2 = num2
          i += 1
        }
        arrBuffer.toIterator
      }
    }
    rddPair2
  }
  //apply LSH to RDD[(Long, MinHashSignature)]
  //return rdd of bucket and items that belong to each bucket
  def hashSignature(rddSignature: RDD[(Long, MinHashSignature)], threshold: Double, maxHashes: Int): RDD[(Long, Iterable[Item])] = {
    val minHasher = initializeMinHashes(threshold, maxHashes)
    rddSignature.flatMap{
      case (id, sig) => {
        val buckets = minHasher.buckets(sig)
        buckets.map{bucket => (bucket, Item(id, sig))}
      }
    }.groupByKey()
  }
  case class Item(id: Long, signature: MinHashSignature)
}


