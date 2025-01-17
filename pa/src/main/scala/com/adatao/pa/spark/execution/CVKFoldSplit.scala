/*
 *  Copyright (C) 2013 Adatao, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.adatao.pa.spark.execution

import scala.collection.JavaConversions._

import com.adatao.spark.ddf.analytics.Utils
//  @author aht

/*
* Return an Array of Tuple (train, test) of dataContainerID
* */
class CVKFoldSplit(val dataContainerID: String, val numSplits: Int, val seed: Long) extends AExecutor[Array[Array[String]]] {
  override def runImpl(ctx: ExecutionContext): Array[Array[String]] = {

    val ddf = ctx.sparkThread.getDDFManager().getDDF(dataContainerID)
    val cvSets = ddf.ML.CVKFold(numSplits, seed)
    val result = cvSets.map {
      set =>
        {
          val train = set(0).getName
          val test = set(1).getName
          Array(train, test)
        }
    }
    result.toArray
  }
}

