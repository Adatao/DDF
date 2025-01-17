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


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.ddf.DDFManager;
import io.ddf.ml.IModel;
import com.adatao.pa.AdataoException;
import com.adatao.pa.AdataoException.AdataoExceptionCode;
import com.adatao.pa.spark.SparkThread;
import com.adatao.pa.spark.types.ExecutorResult;
import com.adatao.pa.spark.types.FailResult;
import com.adatao.pa.spark.types.SuccessResult;

@SuppressWarnings("serial")
public class GetModel extends CExecutor {
  String modelName;

  public static Logger LOG = LoggerFactory.getLogger(GetModel.class);

  public GetModel(String modelName) {
    this.modelName = modelName;
  }

  @Override
  public ExecutorResult run(SparkThread sparkThread) throws AdataoException {
    if (modelName == null) {
      return new FailResult().setMessage("modelName string is empty");
    }
    try {
      DDFManager ddfManager = sparkThread.getDDFManager();
      IModel model = ddfManager.getModelByName(modelName);

      if (model != null) {
        LOG.info("Succesfully getting model from name = " + modelName);
      } else LOG.error("Can not get model from name = " + modelName);

      return new ModelResult(model);

    } catch (Exception e) {
      throw new AdataoException(AdataoExceptionCode.ERR_GENERAL, e.getMessage(), e);
    }
  }

	static public class ModelResult extends SuccessResult {

		public String id;

    public ModelResult(IModel model) {
      this.id = model.getName();// .substring(15).replace("_", "-");
      //TODO change NQLinearRegressionModel to be generic
      //NQLinearRegressionModel rawModel = (NQLinearRegressionModel) model.getRawModel();
    }
  }

}
