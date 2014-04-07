package com.adatao.spark.ddf.analytics

import org.apache.spark.{TaskContext, Partition}
import org.apache.spark.rdd.RDD
import java.util.Random
import com.adatao.ddf.{DDFManager, DDF}
import java.util.{List => JList}
import com.adatao.spark.ddf.SparkDDF
import shark.api.Row
import com.adatao.ddf.exception.DDFException
import java.util
import com.adatao.ddf.content.Schema

private[adatao]
class SeededPartition(val prev: Partition, val seed: Int) extends Partition with Serializable {
  override val index: Int = prev.index
}

private[adatao]
class RandomSplitRDD[T: ClassManifest](
                                        prev: RDD[T],
                                        seed: Long,
                                        lower: Double,
                                        upper: Double,
                                        isTraining: Boolean)
  extends RDD[T](prev) {

  override def getPartitions: Array[Partition] = {
    val rg = new Random(seed)
    firstParent[T].partitions.map(x => new SeededPartition(x, rg.nextInt))
  }

  override def getPreferredLocations(split: Partition): Seq[String] =
    firstParent[T].preferredLocations(split.asInstanceOf[SeededPartition].prev)

  override def compute(splitIn: Partition, context: TaskContext): Iterator[T] = {
    val split = splitIn.asInstanceOf[SeededPartition]
    val rand = new Random(split.seed)
    // This is equivalent to taking a random sample without replacement
    // for for the train set and leave the rest as the test set.
    // The results are stable accross compute() calls due to deterministic RNG.
    if (isTraining) {
      firstParent[T].iterator(split.prev, context).filter(x => {
        val z = rand.nextDouble;
        z < lower || z >= upper
      })
    } else {
      firstParent[T].iterator(split.prev, context).filter(x => {
        val z = rand.nextDouble;
        lower <= z && z < upper
      })
    }
  }
}

object CrossValidation {
  /** Return an Iterator of size k of (train, test) RDD Tuple
    * for which the probability of each element belonging to each split is (trainingSize, 1-trainingSize).
    * The train & test data across k split are shuffled differently (different random seed for each iteration).
    */
  def randomSplit[T](rdd: RDD[T], k: Int, trainingSize: Double, seed: Long)(implicit _cm: ClassManifest[T]): Iterator[(RDD[T], RDD[T])] = {
    require(0 < trainingSize && trainingSize < 1)
    val rg = new Random(seed)
    (1 to k).map(_ => rg.nextInt).map(z =>
      (new RandomSplitRDD(rdd, z, 0, 1.0 - trainingSize, true),
        new RandomSplitRDD(rdd, z, 0, 1.0 - trainingSize, false))).toIterator
  }

  /** Return an Iterator of of size k of (train, test) RDD Tuple
    * for which the probability of each element belonging to either split is ((k-1)/k, 1/k).
    * The location of the test data is shifted consistently between folds
    * so that the resulting test sets are pair-wise disjoint.
    */
  def kFoldSplit[T](rdd: RDD[T], k: Int, seed: Long)(implicit _cm: ClassManifest[T]): Iterator[(RDD[T], RDD[T])] = {
    require(k > 0)
    (for (lower <- 0.0 until 1.0 by 1.0 / k)
    yield (new RandomSplitRDD(rdd, seed, lower, lower + 1.0 / k, true),
        new RandomSplitRDD(rdd, seed, lower, lower + 1.0 / k, false))
      ).toIterator
  }

  def DDFRandomSplit(ddf: DDF, k: Int, trainingSize: Double, seed: Long): JList[JList[DDF]] = {
    var unitType: Class[_] = null
    var splits: Iterator[(RDD[_], RDD[_])] = null

    if (ddf.getRepresentationHandler.has(Array(classOf[RDD[_]], classOf[Array[Double]]): _*)) {
      val rdd = ddf.asInstanceOf[SparkDDF].getRDD(classOf[Array[Double]])
      splits = randomSplit(rdd, k, trainingSize, seed)
      unitType = classOf[Array[Double]]

    } else if (ddf.getRepresentationHandler.has(Array(classOf[RDD[_]], classOf[Array[Object]]): _*)) {
      val rdd = ddf.asInstanceOf[SparkDDF].getRDD(classOf[Array[Object]])
      splits = randomSplit(rdd, k, trainingSize, seed)
      unitType = classOf[Array[Object]]

    } else if (ddf.getRepresentationHandler.has(Array(classOf[RDD[_]], classOf[Row]): _*)) {
      val rdd = ddf.asInstanceOf[SparkDDF].getRDD(classOf[Row])
      splits = randomSplit(rdd, k, trainingSize, seed)
      unitType = classOf[Row]

    } else {
      val rdd = ddf.asInstanceOf[SparkDDF].getRDD(classOf[Array[Object]])
      if (rdd == null) throw new DDFException("Cannot get RDD of Representation Array[Double], Array[Object] or Row")
      splits = randomSplit(rdd, k, trainingSize, seed)
      unitType = classOf[Array[Object]]
    }
    if (splits == null) {
      throw new DDFException("Error getting cross validation for DDF")
    }
    return getDDFCVSetsFromRDDs(splits, ddf.getManager, ddf.getSchema, ddf.getNamespace, unitType)
  }

  def DDFKFoldSplit(ddf: DDF, k: Int, seed: Long): JList[JList[DDF]] = {
    var unitType: Class[_] = null
    var splits: Iterator[(RDD[_], RDD[_])] = null

    if (ddf.getRepresentationHandler.has(Array(classOf[RDD[_]], classOf[Array[Double]]): _*)) {
      val rdd = ddf.asInstanceOf[SparkDDF].getRDD(classOf[Array[Double]])
      splits = kFoldSplit(rdd, k, seed)
      unitType = classOf[Array[Double]]

    } else if (ddf.getRepresentationHandler.has(Array(classOf[RDD[_]], classOf[Array[Object]]): _*)) {
      val rdd = ddf.asInstanceOf[SparkDDF].getRDD(classOf[Array[Object]])
      splits = kFoldSplit(rdd, k, seed)
      unitType = classOf[Array[Object]]

    } else if (ddf.getRepresentationHandler.has(Array(classOf[RDD[_]], classOf[Row]): _*)) {
      val rdd = ddf.asInstanceOf[SparkDDF].getRDD(classOf[Row])
      splits = kFoldSplit(rdd, k, seed)
      unitType = classOf[Row]

    } else {
      val rdd = ddf.asInstanceOf[SparkDDF].getRDD(classOf[Array[Object]])
      if (rdd == null) throw new DDFException("Cannot get RDD of representation Array[Double], Array[Object] or Row")
      splits = kFoldSplit(rdd, k, seed)
      unitType = classOf[Array[Object]]
    }

    if (splits == null) {
      throw new DDFException("Error getting cross validation for DDF")
    }
    return getDDFCVSetsFromRDDs(splits, ddf.getManager, ddf.getSchema, ddf.getNamespace, unitType)
  }

  /**
   * Get set of Cross Validation of DDFs from RDD splits
   * @param splits Iterator of tuple of (train, test) RDD
   * @param unitType unitType of returned DDF
   * @return List of Cross Validation sets
   */
  private def getDDFCVSetsFromRDDs(splits: Iterator[(RDD[_], RDD[_])], manager: DDFManager, schema: Schema, nameSpace: String, unitType: Class[_]): JList[JList[DDF]] = {
    val cvSets: JList[JList[DDF]] = new util.ArrayList[JList[DDF]]()

    for ((train, test) <- splits) {
      val aSet = new util.ArrayList[DDF]();
      val trainDDF = new SparkDDF(manager, train.asInstanceOf[RDD[_]], unitType, nameSpace,
        null, schema)
      val testDDF = new SparkDDF(manager, test.asInstanceOf[RDD[_]], unitType, nameSpace,
        null, schema)

      aSet.add(trainDDF)
      aSet.add(testDDF)
      cvSets.add(aSet)
    }
    return cvSets
  }
}
