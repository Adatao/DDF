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

/**
 *
 */
package com.adatao.pa.spark.execution

import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import com.adatao.ML.LinearRegressionModel
import com.adatao.ML.LogisticRegressionModel
import com.adatao.pa.spark.types.ABigRClientTest
import com.adatao.pa.spark.types.ExecutionResult
import java.util.HashMap
import com.adatao.pa.spark.execution.FetchRows.FetchRowsResult
import java.lang.Integer
import com.adatao.pa.spark.execution.FiveNumSummary._

/**
 * Tests for regression algorithms, having to do with the BigR environment
 *
 * @author ctn, aht, khangich, nhanvlc
 *
 */
class RegressionSuite extends ABigRClientTest {


	test("Single-variable linear regression - normal equation - no regularization") {
		val dataContainerId = this.loadFile(List("resources/mtcars", "server/resources/mtcars"), false, " ")
		val lambda = 0.0
		val executor = new LinearRegressionNormalEquation(dataContainerId, Array(5), 0, lambda)
		val r = bigRClient.execute[NQLinearRegressionModel](executor)

		assert(r.isSuccess)

		val model = r.result
		//println(model.weights(0) + " " + model.weights(1))
		//println(model.stdErrs(0) + " " + model.stdErrs(1))
		//println(model.nFeatures + " " + model.nRows)
		//println(model.rss + " " + model.sst)

		assert(truncate(model.weights(0), 6) === 37.285126)
		assert(truncate(model.weights(1), 6) === -5.344472)
		assert(truncate(model.stdErrs(0), 6) === 1.877627)
		assert(truncate(model.stdErrs(1), 6) === 0.559101)
		assert(truncate(model.rss, 6) === 278.321938)
		assert(truncate(model.sst, 6) === 1126.047188)
		assert(model.numFeatures == 1)
		assert(model.numSamples == 32)
		assert(truncate(model.vif(0), 6) == 1)
	}

	//smoke test
	test("Single-variable linear regression - normal equation categorical - no regularization") {
		val dataContainerId = this.loadFile(List("resources/airline.csv", "server/resources/airline.csv"), false, ",")
		val lambda = 1.0
		//this will cause Infinity, fail
		//		val executor = new LinearRegressionNormalEquation(dataContainerId, Array(5,10), 0, lambda)
		val executor = new LinearRegressionNormalEquation(dataContainerId, Array(5, 9), 0, lambda)
		val r = bigRClient.execute[NQLinearRegressionModel](executor)

		assert(r.isSuccess)

		val model = r.result
		//println(model.weights(0) + " " + model.weights(1))
		//println(model.stdErrs(0) + " " + model.stdErrs(1))
		//println(model.nFeatures + " " + model.nRows)
		//println(model.rss + " " + model.sst)

	}

	test("Multiple-variable linear regression - normal equation - no regularization") {
		val dataContainerId = this.loadFile(List("resources/mtcars", "server/resources/mtcars"), false, " ")
		val lambda = 0.0
		val executor = new LinearRegressionNormalEquation(dataContainerId, Array(3, 5), 0, lambda)
		val r = bigRClient.execute[NQLinearRegressionModel](executor)

		assert(r.isSuccess)

		val model = r.result
		//println(model.weights(0) + " " + model.weights(1) + " " + model.weights(2))
		//println(model.stdErrs(0) + " " + model.stdErrs(1) + " " + model.stdErrs(2))
		//println(model.nFeatures + " " + model.nRows)
		//println(model.rss + " " + model.sst)
		//println(model.vif(0) + " "  + model.vif(1))

		assert(truncate(model.weights(0), 6) === 37.227270)
		assert(truncate(model.weights(1), 6) === -0.031773)
		assert(truncate(model.weights(2), 6) === -3.877831)
		assert(truncate(model.stdErrs(0), 6) === 1.598788)
		assert(truncate(model.stdErrs(1), 6) === 0.009030)
		assert(truncate(model.stdErrs(2), 6) === 0.632733)
		assert(truncate(model.rss, 6) === 195.047755)
		assert(truncate(model.sst, 6) === 1126.047188)
		assert(model.numFeatures == 2)
		assert(model.numSamples == 32)
		assert(truncate(model.vif(0), 6) == 1.766625)
		assert(truncate(model.vif(1), 6) == 1.766625)
	}

	test("Single-variable linear regression") {
		val dataContainerId = this.loadFile(List("resources/mtcars", "server/resources/mtcars"), false, " ")
		val lambda = 0.0
		val executor = new LinearRegression(dataContainerId, Array(5), 0, 40, 0.05, lambda, Array(38, -3))
		val r = bigRClient.execute[LinearRegressionModel](executor)

		assert(r.isSuccess)

		val model = r.result

		if (model.dummyColumnMapping != null) println(">>>>>>>>>>>>>>>> model.dummyColumnMapping  =" + model.dummyColumnMapping)
		assert(truncate(model.weights(0), 4) === 37.3180)
		assert(truncate(model.weights(1), 4) === -5.3539)
		assert(truncate(model.trainingLosses(0), 4) === 40.9919)
		assert(truncate(model.trainingLosses(1), 4) === 9.9192)
	}

	test("Single-variable linear regression on Shark") {
		createTableMtcars

		val loader = new Sql2DataFrame("select * from mtcars", true)
		val r0 = bigRClient.execute[Sql2DataFrame.Sql2DataFrameResult](loader).result
		assert(r0.isSuccess)

		val dataContainerId = r0.dataContainerID

		val lambda = 0.0
		val executor = new LinearRegression(dataContainerId, Array(5), 0, 40, 0.05, lambda, Array(38, -3))
		val r = bigRClient.execute[LinearRegressionModel](executor)

		assert(r.isSuccess)

		val model = r.result
		assert(truncate(model.weights(0), 4) === 37.3180)
		assert(truncate(model.weights(1), 4) === -5.3539)
		assert(truncate(model.trainingLosses(0), 4) === 40.9919)
		assert(truncate(model.trainingLosses(1), 4) === 9.9192)
	}

	test("Categorical variables linear regression normal equation on Shark") {
		createTableAirline

		val loader = new Sql2DataFrame("select * from airline", true)
		val r0 = bigRClient.execute[Sql2DataFrame.Sql2DataFrameResult](loader).result
		assert(r0.isSuccess)

		val dataContainerId = r0.dataContainerID

		val lambda = 0.0
		val executor = new LinearRegressionNormalEquation(dataContainerId, Array(14), 7, lambda)
		//		val executor = new LinearRegression(dataContainerId, Array(16, 1), 7, 40, 0.05, lambda, null)
		val r = bigRClient.execute[NQLinearRegressionModel](executor)

		assert(r.isSuccess)

		val model = r.result
		println(">>>>>>>>>>>>>>>>> final model =" + model.toString)

		if (model.dummyColumnMapping != null) println(">>>>>>>>>>>>>>>> model.dummyColumnMapping  =" + model.dummyColumnMapping)
		//		assert(model.weights.length === 3)
		assert(model.dummyColumnMapping != null)
	}


	//we don't support dummyCoding for normal dataframe yet
	test(" categorical variables linear regression on normal dataframe") {

		val dataContainerId = this.loadFile(List("resources/airline.csv"), false, ",")
		val lambda = 0.0
		
		val cmd= new GetMultiFactor(dataContainerId, Array(3, 16, 17))
		val result= bigRClient.execute[Array[(Int, java.util.Map[String, java.lang.Integer])]](cmd).result

		val executor = new LinearRegressionNormalEquation(dataContainerId, Array(3, 16, 17), 0, lambda)
		val r = bigRClient.execute[NQLinearRegressionModel](executor)

		assert(r.isSuccess)

		val model = r.result

		assert(model.weights.length === 11)
		assert(model.dummyColumnMapping != null)
	}
	
	test(" categorical variables linear regression on as.factor(Int column)") {

		
		val loader = new Sql2DataFrame("select * from airline", true)
		val r0 = bigRClient.execute[Sql2DataFrame.Sql2DataFrameResult](loader).result
		assert(r0.isSuccess)

		val dataContainerId = r0.dataContainerID
		val lambda = 0.0
		
		val cmd= new GetMultiFactor(dataContainerId, Array(0))
		val result= bigRClient.execute[Array[(Int, java.util.Map[String, java.lang.Integer])]](cmd).result

		val executor = new LinearRegressionNormalEquation(dataContainerId, Array(0), 15, lambda)
		val r = bigRClient.execute[NQLinearRegressionModel](executor)

		assert(r.isSuccess)

		val model = r.result

	}

	//temporarily don't support normal dataframe just yet
	/*test("Categorical variables linear regression on normal dataframe") {

		val dataContainerId = this.loadFile(List("resources/airline.csv"), false, ",")
		val lambda = 0.0
		val executor = new LinearRegression(dataContainerId, Array(3, 16, 17), 0, 1, 0.00005, lambda, null)
		val r = bigRClient.execute[LinearRegressionModel](executor)

		assert(r.isSuccess)

		val model = r.result

		assert(model.weights.length === 14)
		assert(model.dummyColumnMapping != null)
	}*/

	test("Categorical multiple variables linear regression on Shark") {
		createTableAirline

		val loader = new Sql2DataFrame("select * from airline", true)
		val r0 = bigRClient.execute[Sql2DataFrame.Sql2DataFrameResult](loader).result
		assert(r0.isSuccess)

		val dataContainerId = r0.dataContainerID

		val lambda = 0.0


		val executor = new LinearRegression(dataContainerId, Array(3, 16, 17), 2, 50, 0.01, lambda, null)
		val r = bigRClient.execute[LinearRegressionModel](executor)

		assert(r.isSuccess)

		val model = r.result
		println(">>>>>>>>>>>>>>>>> final model =" + model.toString)

		if (model.dummyColumnMapping != null) println(">>>>>>>>>>>>>>>> model.dummyColumnMapping  =" + model.dummyColumnMapping)
		assert(model.weights.length === 12)
		assert(model.dummyColumnMapping != null)
	}

	test("lm categorical and reference level on shark") {
		createTableAirline

		val loader = new Sql2DataFrame("select * from airline", true)
		val r0 = bigRClient.execute[Sql2DataFrame.Sql2DataFrameResult](loader).result
		assert(r0.isSuccess)

		val dataContainerId = r0.dataContainerID

		val lambda = 1.0

		var mapReferenceLevel: HashMap[String, String] = new  HashMap[String, String]()
		mapReferenceLevel.put("v17", "ISP")
		mapReferenceLevel.put("v18", "LAS")

		val executor = new LinearRegressionNormalEquation(dataContainerId, Array(3, 16, 17), 2, lambda, mapReferenceLevel)
		val r = bigRClient.execute[NQLinearRegressionModel](executor)

		assert(r.isSuccess)

		val model = r.result
		println(">>>>>>>>>>>>>>>>> final model =" + model.toString)

		if (model.dummyColumnMapping != null) println(">>>>>>>>>>>>>>>> model.dummyColumnMapping  =" + model.dummyColumnMapping)
		assert(model.weights.length === 12)
		assert(model.dummyColumnMapping != null)
//		//check reference level if equal 0.0

		assert(model.dummyColumnMapping.get(16).get("ISP") === 0.0)
		assert(model.dummyColumnMapping.get(17).get("LAS") === 0.0)

	}

	test("glm categorical and reference level on shark") {
		createTableAdmission

		val loader = new Sql2DataFrame("select * from admission", true)
		val r0 = bigRClient.execute[Sql2DataFrame.Sql2DataFrameResult](loader).result
		assert(r0.isSuccess)

		val dataContainerId = r0.dataContainerID
		val lambda = 1.0

		var cmd1 = new GetFactor().setDataContainerID(dataContainerId).setColumnName("v4")
		bigRClient.execute[GetFactor.GetFactorResult](cmd1)

		var mapReferenceLevel: HashMap[String, String] = new  HashMap[String, String]()
		mapReferenceLevel.put("v4", "4")
		val executor = new LogisticRegressionIRLS(dataContainerId, Array(3), 0, 25, 1e-8, lambda, null, mapReferenceLevel, false)
		val r = bigRClient.execute[IRLSLogisticRegressionModel](executor)

		assert(r.isSuccess)

		val model = r.result
		//check reference level if equal 0.0
		assert(model.dummyColumnMapping.get(3).get("4") === 0.0)
		println(">>>>>>>>>>>>>>>>> final model =" + model.toString)
	}


	test("Single-variable linear regression with null initialWeights") {
		val dataContainerId = this.loadFile(List("resources/mtcars", "server/resources/mtcars"), false, " ")
		val lambda = 0.0
		val executor = new LinearRegression(dataContainerId, Array(5), 0, 1, 0.05, lambda, null)
		val r = bigRClient.execute[LinearRegressionModel](executor)

		assert(r.isSuccess)
	}

	test("Multiple-variable linear regression") {
		val dataContainerId = this.loadFile(List("resources/mtcars", "server/resources/mtcars"), false, " ")
		val lambda = 0.0
		val executor = new LinearRegression(dataContainerId, Array(3, 5), 0, 1, 0.00005, lambda, Array(37.3, -0.04, -3.9))
		val r = bigRClient.execute[LinearRegressionModel](executor)

		assert(r.isSuccess)

		val model = r.result

		assertEquals(37.227, model.weights(0), 0.1);
		assertEquals(-0.031, model.weights(1), 0.1);
		assertEquals(-3.877, model.weights(2), 0.1);

	}

	test("Single-variable linear regression with regularization") {
		val dataContainerId = this.loadFile(List("resources/mtcars", "server/resources/mtcars"), false, " ")
		val lambda = 1.0
		val executor = new LinearRegression(dataContainerId, Array(5), 0, 40, 0.05, lambda, Array(38, -3))
		val r = bigRClient.execute[LinearRegressionModel](executor)

		assert(r.isSuccess)

		val model = r.result
		println(">>>model=" + model)

		assertEquals(model.weights(0), 33.2946, 0.1)
		assertEquals(model.weights(1), -4.2257, 0.1)
		assertEquals(model.trainingLosses(0), 86.3981, 0.1)
		assertEquals(model.trainingLosses(1), 54.1295, 0.1)

//		assert(truncate(model.weights(1), 4) === -4.2257)
//		assert(truncate(model.trainingLosses(0), 4) === 86.3981)
//		assert(truncate(model.trainingLosses(1), 4) === 54.1295)
		//		assert(truncate(model.trainingLosses(0), 4) === 63.6950)
		//		assert(truncate(model.trainingLosses(1), 4) === 32.0936)
	}

	
	//TO DO: recheck this: assert(truncate(model.nullDeviance, 6) === 68.0292)
	test("Multiple-variable logistic regression IRLS - no regularization") {
		val dataContainerId = this.loadFile(List("resources/flu.table.noheader", "server/resources/flu.table.noheader"), false, " ")
		val lambda = 0.0
		val executor = new LogisticRegressionIRLS(dataContainerId, Array(1, 2), 0, 25, 1e-8, lambda, null, null, false)
		val r = bigRClient.execute[IRLSLogisticRegressionModel](executor)

		assert(r.isSuccess)

		val model = r.result
		/*println(model.weights(0) + " " + model.weights(1) + " " + model.weights(2))
	    println(model.stderrs(0) + " " + model.stderrs(1) + " " + model.stderrs(2))
	    println(model.numSamples + " " + model.numFeatures)
	    println(model.deviance + " " + model.nullDeviance)
	    println(model.numIters)*/

		assert(truncate(model.weights(0), 6) === -21.584582)
		assert(truncate(model.weights(1), 6) === 0.221777)
		assert(truncate(model.weights(2), 6) === 0.203507)
		assert(truncate(model.deviance, 6) === 32.416312)
		//assert(truncate(model.nullDeviance, 6) === 68.0292)
		assert(truncate(model.stderrs(0), 6) === 6.417354)
		assert(truncate(model.stderrs(1), 6) === 0.074349)
		assert(truncate(model.stderrs(2), 6) === 0.062723)
		assert(model.numFeatures == 2)
		assert(model.numSamples == 50)
		assert(model.numIters == 6)
	}

	test("Categorical variable logistic regression IRLS - no regularization - Shark ") {
		createTableAdmission

		val loader = new Sql2DataFrame("select * from admission", true)
		val r0 = bigRClient.execute[Sql2DataFrame.Sql2DataFrameResult](loader).result
		assert(r0.isSuccess)

		System.setProperty("bigr.lm.maxNumFeatures", "50")
		
		val dataContainerId = r0.dataContainerID

		var cmd1 = new GetFactor().setDataContainerID(dataContainerId).setColumnName("v4")
		bigRClient.execute[GetFactor.GetFactorResult](cmd1)

		val lambda = 0.0
		val executor = new LogisticRegressionIRLS(dataContainerId, Array(3), 0, 25, 1e-8, lambda, null, null, false)
		val r = bigRClient.execute[IRLSLogisticRegressionModel](executor)

		assert(r.isSuccess)

		val model = r.result

		/*println(model.weights(0) + " " + model.weights(1) + " " + model.weights(2) + " " + model.weights(3))
		println(model.stderrs(0) + " " + model.stderrs(1) + " " + model.stderrs(2) + " " + model.stderrs(3))
		println(model.numSamples + " " + model.numFeatures)
		println(model.deviance + " " + model.nullDeviance)
		println(model.numIters)*/

		println(">>>>>> model.weights=" + model.weights)
		assert(truncate(model.weights(0), 6) === -1.200395)
		assert(truncate(model.weights(1), 6) === 0.614668)
		assert(truncate(model.weights(2), 6) === 1.364698)
		assert(truncate(model.weights(3), 6) === -0.322032)
//		assert(truncate(model.deviance, 6) === 474.966718)
//		assert(truncate(model.nullDeviance, 6) === 499.976518)
		assert(truncate(model.stderrs(0), 6) === 0.215562)
		assert(truncate(model.stderrs(1), 6) === 0.274399)
		assert(truncate(model.stderrs(2), 6) === 0.335387)
		assert(truncate(model.stderrs(3), 6) === 0.384677)

		assert(model.numFeatures == 3)
		assert(model.numSamples == 400)
		assert(model.numIters == 4)

		assert(model.dummyColumnMapping != null)
	}
	
	//24/7 bug
	test("Categorical variable logistic regression IRLS - normal dataframe") {
		createTableAdmission

		val dataContainerId = this.loadFile(List("resources/airline-transform.3.csv", "server/resources/airline-transform.3.csv"), false, ",")


		val lambda = 0.0
		val executor = new LogisticRegressionIRLS(dataContainerId, Array(2, 14), 12, 10, 1e-8, lambda, null, null, false)
		val r = bigRClient.execute[IRLSLogisticRegressionModel](executor)

		assert(r.isSuccess)

		val model = r.result


		assert(model.numFeatures == 3)
		assert(model.numSamples == 400)
		assert(model.numIters == 4)

		assert(model.dummyColumnMapping != null)
	}

	test("Single-variable logistic regression") {
		val dataContainerId = this.loadFile(List("resources/mtcars", "server/resources/mtcars"), false, " ")
		val lambda = 0.0
		val executor = new LogisticRegression(dataContainerId, Array(5), 7, 40, 0.05, lambda, Array(38, -3))
		val r = bigRClient.execute[LogisticRegressionModel](executor)

		assert(r.isSuccess)

		val model = r.result
		assert(truncate(model.weights(0), 4) === 36.8605)
		assert(truncate(model.weights(1), 4) === -7.1806)
		assert(truncate(model.trainingLosses(0), 4) === 15.1505)
		assert(truncate(model.trainingLosses(1), 4) === 14.9196)
	}

	test("Single-variable logistic regression on Shark") {
		createTableMtcars

		val loader = new Sql2DataFrame("select * from mtcars", true)
		val r0 = bigRClient.execute[Sql2DataFrame.Sql2DataFrameResult](loader)
		assert(r0.isSuccess)

		val dataContainerId = r0.result.dataContainerID

		val lambda = 0.0
		val executor = new LogisticRegression(dataContainerId, Array(5), 7, 40, 0.05, lambda, Array(38, -3))
		val r = bigRClient.execute[LogisticRegressionModel](executor)

		assert(r.isSuccess)

		val model = r.result
		assert(truncate(model.weights(0), 4) === 36.8605)
		assert(truncate(model.weights(1), 4) === -7.1806)
		//		assert(truncate(model.trainingLosses(0), 4) === 15.1505)
		//		assert(truncate(model.trainingLosses(1), 4) === 14.9196)
	}

	test("Single-variable logistic regression with null initialWeights") {
		val dataContainerId = this.loadFile(List("resources/mtcars", "server/resources/mtcars"), false, " ")
		val lambda = 0.0
		val executor = new LogisticRegression(dataContainerId, Array(5), 7, 1, 0.05, lambda, null)
		val r = bigRClient.execute[LinearRegressionModel](executor)

		assert(r.isSuccess)
	}

	test("Single-variable logistic regression with regularization") {
		val dataContainerId = this.loadFile(List("resources/mtcars", "server/resources/mtcars"), false, " ")
		val lambda = 1.0
		val executor = new LogisticRegression(dataContainerId, Array(5), 7, 40, 0.05, lambda, Array(38, -3))
		val r = bigRClient.execute[LogisticRegressionModel](executor)

		assert(r.isSuccess)

		val model = r.result
		println(">>>model=" + model)

		assertEquals(model.weights(0), 32.3613, 0.1)
		assertEquals(model.weights(1), -6.5206, 0.1)
		assertEquals(model.trainingLosses(0), 60.5567, 0.1)
	}

	test("Multiple-variable logistic regression") {
		val dataContainerId = this.loadFile(List("resources/admission.csv", "server/resources/admission.csv"), false, " ")
		val lambda = 0.0
		val executor = new LogisticRegression(dataContainerId, Array(2, 3), 0, 1, 0.1, lambda, Array(-3.0, 1.5, -0.9))
		val r = bigRClient.execute[LogisticRegressionModel](executor)

		assert(r.isSuccess)

		val model = r.result

		assertEquals(true, r.isSuccess);

		assertEquals(-3.0251, model.weights(0), 0.0001);
		assertEquals(1.4117, model.weights(1), 0.0001);
		assertEquals(-0.9493, model.weights(2), 0.0001);
	}

	test("Test Infinity bug on airline data") {
		val dataContainerId = this.loadFile(List("resources/airline-transform.3.csv", "server/resources/airline-transform.3.csv"), false, ",")
		val lambda = 0.0
		val executor = new LogisticRegression(dataContainerId, Array(0, 6, 7), 12, 50, 0.1, lambda, Array(0.00000000001, 0.00000000001, 0.00000000001, 0.00000000001))
		val r = bigRClient.execute[LogisticRegressionModel](executor)

		assert(r.isSuccess)

		val model = r.result

		assertEquals(true, r.isSuccess);
		println(">>>>>>>>> " + model.trainingLosses)

	}

	test("Single variable linear regression on Shark") {
		createTableMtcars

		val loader = new Sql2DataFrame("select * from mtcars", true)
		val r0 = bigRClient.execute[Sql2DataFrame.Sql2DataFrameResult](loader).result
		assert(r0.isSuccess)

		val dataContainerId = r0.dataContainerID

		val lambda = 0.0
		val executor = new LinearRegression(dataContainerId, Array(5), 0, 40, 0.05, lambda, Array(38, -3))
		val r = bigRClient.execute[LinearRegressionModel](executor)

		assert(r.isSuccess)

		val model = r.result
		assert(truncate(model.weights(0), 4) === 37.3180)
		assert(truncate(model.weights(1), 4) === -5.3539)
		assert(truncate(model.trainingLosses(0), 4) === 40.9919)
		assert(truncate(model.trainingLosses(1), 4) === 9.9192)
	}
	
	test("Single-variable linear regression on Shark, binned var") {
		createTableAirline

		val loader = new Sql2DataFrame("select * from airline", true)
		val r0 = bigRClient.execute[Sql2DataFrame.Sql2DataFrameResult](loader).result
		assert(r0.isSuccess)

		val dataContainerId = r0.dataContainerID
		
		val cmd = new Binning(dataContainerId, "v19", binningType = "equalFreq", numBins = 5, includeLowest = false, right = false)
		val result = bigRClient.execute[BinningResult](cmd)
		
		val cmd3 = new FetchRows().setDataContainerID(result.result.dataContainerID).setLimit(100)
		val res3 = bigRClient.execute[FetchRowsResult](cmd3)
		println(">>>>>>> res3=" + res3.result.data)
		
		val lambda = 0.0
		val executor = new LinearRegressionNormalEquation(result.result.dataContainerID, Array(1,18), 14, lambda)
		val r = bigRClient.execute[NQLinearRegressionModel](executor)
		assert(r.isSuccess)
		val model = r.result
		
		println(">>>>model=" + model)
	}

	test("test MaxFeatures") {
		createTableMtcars
		val df= this.runSQL2RDDCmd("select * from mtcars", true)
		assert(df.isSuccess)

		val dcID = df.dataContainerID
		LOG.info("Get dataContainerID= " + dcID)
		val cmd= new GetMultiFactor(dcID, Array(7, 8 ,9, 10))
		val result= bigRClient.execute[Array[(Int, java.util.Map[String, java.lang.Integer])]](cmd).result

		val lambda = 0.1
		val executor = new LinearRegressionNormalEquation(dcID, Array(6, 7, 8, 9, 10), 0, lambda)
		System.setProperty("bigr.lm.maxNumFeatures", "10")
		try{
			val r= bigRClient.execute[NQLinearRegressionModel](executor)
			assert(false)
			assert(!r.isSuccess)
		}
		catch {
			case e => {
				assert(e.isInstanceOf[java.lang.Exception])
			}
		}

		System.setProperty("bigr.lm.maxNumFeatures", "20")
		val r1= bigRClient.execute[NQLinearRegressionModel](executor)
		assert(r1.isSuccess)
	}
	
//	GOOD, result are identical with glm.gd
	test("Multiple-variable logistic regression on sparse matrix, no sparse column") {
		
		//load data
		createTableAdmission
		val df= this.runSQL2RDDCmd("select * from admission", true)
		val dataContainerId = df.dataContainerID
		val lambda = 0.0
		
		//minimum threshold range for sparse columns
		System.getProperty("sparse.max.range", "100000")
		var cmd2 = new FiveNumSummary(dataContainerId)
		val summary = bigRClient.execute[Array[ASummary]](cmd2).result
		assert(summary.size > 0)
		
		//construct columnSummary parameter
		var columnsSummary =  new HashMap[String, Array[Double]]
		var hmin = new Array[Double] (summary.size)
		var hmax = new Array[Double] (summary.size)
		//convert columnsSummary to HashMap
		var i = 0
		while(i < summary.size) {
			hmin(i) = summary(i).min
			hmax(i) = summary(i).max			
			i += 1
		}
		columnsSummary.put("min", hmin)
		columnsSummary.put("max", hmax)
		
		
		val executor = new LogisticRegressionCRS(dataContainerId, Array(2, 3), 0, columnsSummary, 1, 0.1, lambda, Array(-3.0, 1.5, -0.9))
		val r = bigRClient.execute[LogisticRegressionModel](executor)
		assert(r.isSuccess)

		
		//assertion, expect to produce similarly identical result with glm.gd non-sparse
		val model = r.result
		println("model=" + model)
		
		assertEquals(true, r.isSuccess);
		assertEquals(-3.0251, model.weights(0), 0.0001);
		assertEquals(1.4117, model.weights(1), 0.0001);
		assertEquals(-0.9493, model.weights(2), 0.0001);
	}

	test("Multiple-variable logistic regression on sparse matrix, case one with sparse column") {
		
		//load data		
		createTableAdmission
		val df= this.runSQL2RDDCmd("select * from admission", true)
		val dataContainerId = df.dataContainerID
		val lambda = 0.0
		System.setProperty("sparse.max.range", "10")
		val iterations = 2
		
		//get summary
		var cmd2 = new FiveNumSummary(dataContainerId)
		val summary = bigRClient.execute[Array[ASummary]](cmd2).result
		assert(summary.size > 0)

		//construct columnSummary parameter
		var columnsSummary =  new HashMap[String, Array[Double]]
		var hmin = new Array[Double] (summary.size)
		var hmax = new Array[Double] (summary.size)
		//convert columnsSummary to HashMap
		var i = 0
		while(i < summary.size) {
			hmin(i) = summary(i).min
			hmax(i) = summary(i).max			
			i += 1
		}
		columnsSummary.put("min", hmin)
		columnsSummary.put("max", hmax)

		
		val startTime = System.currentTimeMillis()
		val executor = new LogisticRegressionCRS(dataContainerId, Array(1, 2, 3), 0, columnsSummary, iterations, 0.1, lambda, Array(-3.0, 1.5, -0.9))
		val r = bigRClient.execute[LogisticRegressionModel](executor)
		assert(r.isSuccess)

		
		val model = r.result
		assertEquals(true, r.isSuccess);
		val endTime = System.currentTimeMillis()
		println(">>>>>>>>>>>>>>>>>> finish: " + (endTime-startTime))
//		println("model=" + model)

	}
	
	ignore("Multiple-variable logistic regression on sparse matrix, case one with sparse column on adwo data") {
		val lambda = 0.0
		
		val MAX_ROWS = Integer.parseInt(System.getProperty("training.max.record", "10000000"))
		
		val loader = new Sql2DataFrame("select if(prob>=0.5, 1, 0) as clicked, advertise_id from (select rand() as prob, advertise_id from adwo_week_show limit " + MAX_ROWS +") t", true)
		val r0 = bigRClient.execute[Sql2DataFrame.Sql2DataFrameResult](loader).result
		assert(r0.isSuccess)
		System.getProperty("sparse.max.range", "50")
		println(">>>>>>>>>> finish loading shark data")
		val dataContainerId = r0.dataContainerID

		
		//max advertise_id
		var cmd2 = new FiveNumSummary(dataContainerId)
		val summary = bigRClient.execute[Array[ASummary]](cmd2).result

		//construct columnSummary parameter
		var columnsSummary =  new HashMap[String, Array[Double]]
		var hmin = new Array[Double] (summary.size)
		var hmax = new Array[Double] (summary.size)
		//convert columnsSummary to HashMap
		var i = 0
		while(i < summary.size) {
			hmin(i) = summary(i).min
			hmax(i) = summary(i).max			
			i += 1
		}
		columnsSummary.put("min", hmin)
		columnsSummary.put("max", hmax)
		
		val executor = new LogisticRegressionCRS(dataContainerId, Array(1), 0, columnsSummary, 10, 0.1, lambda, Array(-3.0, 1.5))
		val r = bigRClient.execute[LogisticRegressionModel](executor)

		assert(r.isSuccess)

		val model = r.result

		assertEquals(true, r.isSuccess);
		
//		println("model=" + model)

	}

}