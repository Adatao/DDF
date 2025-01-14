package com.adatao.pa.spark.execution

import com.adatao.pa.spark.types.ABigRClientTest
import com.adatao.pa.spark.execution.Subset.SubsetResult
import com.adatao.pa.spark.execution.FetchRows.FetchRowsResult

/**
 * Created with IntelliJ IDEA.
 * User: daoduchuan
 * Date: 17/10/13
 * Time: 12:06 AM
 * To change this template use File | Settings | File Templates.
 */
class SubsetSuite extends ABigRClientTest{
	test("test DataFrame") {
    createTableSample
    val df = this.runSQL2RDDCmd("select * from stable", true)
    assert(df.isSuccess)
    val dcID = df.dataContainerID
		val jsCreateVectors= String.format("{filter: {type: Operator, " + "name: lt, " +
				"operands: [{type: Column, index: 0}," +
				"{type: DoubleVal, value: 7000.0}]}," +
				"columns: [{type: Column, index: 1}, {type: Column, name: V3}]," +
				"dataContainerID: %s}"
				,dcID)

		val result= bigRClient.execute[SubsetResult]("Subset", jsCreateVectors)
		assert(result.isSuccess)

		val subset=result.result.getDataContainerID
		LOG.info("Create vectorID: " + subset)

		val params= String.format("{dataContainerID: %s}", subset)
		val result1= bigRClient.execute[FetchRowsResult]("FetchRows", params)
		assert(result1.isSuccess)

		val data=result1.result.getData
		assert(data.size == 2)

	}
	test("test Shark"){
		createTableMtcars
		val df= this.runSQL2RDDCmd("select * from mtcars", true)
		assert(df.isSuccess)

		val jsCreatVectors= String.format("{filter: {type: Operator, name: gt, "
			+ "operands: [{type: Column, name: mpg}," + "{type: DoubleVal, value: 20.0}]},"
			+ "columns: [{type: Column, index: 1}, {type: Column, name: hp}]," + "dataContainerID: %s}", df.dataContainerID)

		val result=bigRClient.execute[SubsetResult]("Subset", jsCreatVectors)
		assert(result.isSuccess)

		val subset= result.result.getDataContainerID

		val result2=bigRClient.execute[FetchRowsResult]("FetchRows", String.format("{dataContainerID: %s}", subset))
		assert(result2.isSuccess)

		val data=result2.result.getData
		assert(data.size== 14)

	}

}
