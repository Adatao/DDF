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

package com.adatao.pa.spark.execution;


import java.util.List;

import com.adatao.pa.AdataoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.ddf.DDF;
import com.adatao.pa.spark.SparkThread;
import com.adatao.pa.spark.types.ExecutorResult;
import com.adatao.pa.spark.types.SuccessResult;

@SuppressWarnings("serial")
public class FetchRows extends CExecutor {
  private String dataContainerID;
  int limit = 1000;
  public static Logger LOG = LoggerFactory.getLogger(FetchRows.class);


  static public class FetchRowsResult extends SuccessResult {
    String dataContainerID;
    List<String> data;


    public FetchRowsResult setDataContainerID(String dataContainerID) {
      this.dataContainerID = dataContainerID;
      return this;
    }

    public FetchRowsResult setData(List<String> data) {
      this.data = data;
      return this;
    }

    public List<String> getData() {
      return data;
    }

//    public String toString() {
//      int totalIndent = 14;
//      StringBuilder sb = new StringBuilder();
//      List<String> data = this.getData();
//      for (int i = 0; i < data.size(); i++) {
//        sb.append(com.adatao.pa.spark.Utils.reindent("column", totalIndent));
//      }
//      return(sb.toString());
//    }
  }


  @Override
  public ExecutorResult run(SparkThread sparkThread) throws AdataoException {

    DDF ddf = (DDF) sparkThread.getDDFManager().getDDF(dataContainerID);
    List<String> data;
    try {
      data = ddf.VIEWS.head(limit);
      return new FetchRowsResult().setDataContainerID(dataContainerID).setData(data);
    } catch (Exception e) {
      throw new AdataoException(AdataoException.AdataoExceptionCode.ERR_GENERAL, e.getMessage(), e);
    }
  }

  public FetchRows setDataContainerID(String dataContainerID) {
    this.dataContainerID = dataContainerID;
    return this;
  }

  public FetchRows setLimit(int limit) {
    this.limit = limit;
    return this;
  }
}
