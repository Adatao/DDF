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
package com.adatao.pa.spark.types

import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfterAll
import org.scalatest.junit.JUnitRunner

import com.adatao.ML.ATimedAlgorithmTest
import com.adatao.pa.spark.execution.Sql2DataFrame.Sql2DataFrameResult
import com.adatao.pa.spark.execution.Sql2ListString.Sql2ListStringResult

import com.adatao.pa.thrift.Server

@RunWith(classOf[JUnitRunner])
abstract class ATestBase extends ATimedAlgorithmTest {
	override def beforeEach {
		LOG.info("%s started".format(this.getCurrentTestName))
		super.beforeEach()
	}
}

/**
 * @author ctn
 *
 */
@RunWith(classOf[JUnitRunner])
abstract class ABigRClientTest extends ATimedAlgorithmTest with BeforeAndAfterAll {
	var bigRClient: BigRClient = null
	
	override def beforeAll = {
	

    bigRClient = BigRThriftServerUtils.startServer
    Server.makeFirstConnection(BigRThriftServerUtils.HOST, BigRThriftServerUtils.PORT);
    bigRClient.connect("{clientID:testuser}")
	}
//
	override def afterAll = {
	
    bigRClient.disconnect
    BigRThriftServerUtils.stopServer
    Thread.sleep(600)
    }
  override def beforeEach = {
  
  }

  override def afterEach = {
  }

	def loadFile(fileUrls: List[String], hasHeader: Boolean, fieldSeparator: String): String 
		= BigRClientTestUtils.loadFile(bigRClient, fileUrls, hasHeader, fieldSeparator, 5)
	
	def loadFile(fileUrl: String, hasHeader: Boolean, fieldSeparator: String): String
		= BigRClientTestUtils.loadFile(bigRClient, fileUrl, hasHeader, fieldSeparator, 5)
	
	def runSQLCmd(cmdStr: String): Sql2ListStringResult = BigRClientTestUtils.runSQLCmd(bigRClient, cmdStr)
	
	def runSQL2RDDCmd(cmdStr: String, cache: Boolean): Sql2DataFrameResult = BigRClientTestUtils.runSQL2RDDCmd(bigRClient, cmdStr, cache)
	
	def createTableMtcars = BigRClientTestUtils.createTableMtcars(bigRClient)
	
	def createTableCarowner = BigRClientTestUtils.createTableCarowner(bigRClient)
	
	def createTableAirline = BigRClientTestUtils.createTableAirline(bigRClient)
	
	def createTableAdmission = BigRClientTestUtils.createTableAdmission(bigRClient)
	
	def createTableAdmission2 = BigRClientTestUtils.createTableAdmission2(bigRClient)
		
	def createTableAirQuality = BigRClientTestUtils.createTableAirQuality(bigRClient)

	def createTableTest =BigRClientTestUtils.createTableTest(bigRClient)

	def createTableKmeans = BigRClientTestUtils.createTableKmeans(bigRClient)

  def createTableGraph = BigRClientTestUtils.createTableGraph(bigRClient)

	def createTableGraph1 = BigRClientTestUtils.createTableGraph1(bigRClient)

  def createTableGraph2 = BigRClientTestUtils.createTableGraph2(bigRClient)

  def createTableAirlineWithNA = BigRClientTestUtils.createTableAirlineWithNA(bigRClient)
	
	def createTableRatings = BigRClientTestUtils.createTableRatings(bigRClient)
	
	def createTableSample = BigRClientTestUtils.createTableSample(bigRClient)
	
	def projectDDF(dcID: String, xCols: Array[Int], yCol: Int): String = BigRClientTestUtils.projectDDF(bigRClient, dcID: String, xCols: Array[Int], yCol: Int)
}
