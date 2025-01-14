///*
// *  Copyright (C) 2013 Adatao, Inc.
// *
// *  Licensed under the Apache License, Version 2.0 (the "License");
// *  you may not use this file except in compliance with the License.
// *  You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// *  Unless required by applicable law or agreed to in writing, software
// *  distributed under the License is distributed on an "AS IS" BASIS,
// *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// *  See the License for the specific language governing permissions and
// *  limitations under the License.
// */
//
//package com.adatao.ML.spark
//
//// this is somewhat unfortunate deps but let's just live with it for now
//import com.adatao.pa.spark.SparkThread
//import org.scalatest.Suite
//import org.scalatest.BeforeAndAfterAll
//import org.apache.spark.SparkContext
//import org.apache.spark.api.java.JavaSparkContext
//import io.ddf.DDFManager
//import io.spark.ddf.SparkDDFManager
//
///**
// * After spark/core/src/test/scala/spark/SharedSparkContext.scala
// * Shares a `SparkContext` between all tests in a suite and closes it at the end.
// *
// * @author: aht
// */
//trait SharedSparkContext extends BeforeAndAfterAll { self: Suite =>
//
//  @transient private var _sc: SparkContext = _
//
//  def sc = _sc
//
//  override def beforeAll() {
//    //_sc = new JavaSparkContext(System.getenv("SPARK_MASTER"), "BigR",
//	//			System.getenv("SPARK_HOME"), System.getenv("RSERVER_JAR").split(","))
//    val ddfManager = DDFManager.get("spark").asInstanceOf[SparkDDFManager]
//    _sc = ddfManager.getJavaSharkContext()
//    super.beforeAll()
//  }
//
//  override def afterAll() {
//    _sc.stop
//    super.afterAll()
//  }
//}
