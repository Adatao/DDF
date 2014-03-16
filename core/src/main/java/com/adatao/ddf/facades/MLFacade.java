/**
 * 
 */
package com.adatao.ddf.facades;


import com.adatao.ddf.DDF;
import com.adatao.ddf.analytics.ISupportML;
import com.adatao.ddf.exception.DDFException;

/**
 * A helper class to group together the various ML functions that would otherwise crowd up DDF.java
 */
public class MLFacade implements ISupportML {

  private DDF mDDF;
  private ISupportML mMLSupporter;


  public MLFacade(DDF ddf, ISupportML mlSupporter) {
    mDDF = ddf;
    mMLSupporter = mlSupporter;
  }



  @Override
  public DDF getDDF() {
    return mDDF;
  }

  @Override
  public void setDDF(DDF theDDF) {
    mDDF = theDDF;
  }

  public ISupportML getMLSupporter() {
    return mMLSupporter;
  }

  public void setMLSupporter(ISupportML mlSupporter) {
    mMLSupporter = mlSupporter;
  }


  @Override
  public IModel train(String trainMethodName, Object... params) throws DDFException {
    return this.getMLSupporter().train(trainMethodName, params);
  }

  @Override
  public DDF getYTrueYPredict(IModel model) throws DDFException {
    return this.getMLSupporter().getYTrueYPredict(model);
  }

  @Override
  public DDF predict(IModel model) throws DDFException {
    return this.getMLSupporter().predict(model);
  }
  // //// Convenient facade ML algorithm names //////

  public Object kMeans(int[] featureColumnIndexes, int numCentroids, int maxIters, int runs, String initMode)
      throws DDFException {
    return this.train("kmeans", featureColumnIndexes, numCentroids, maxIters, runs, initMode);
  }

  public Object linearRegressionWithSGD(int[] featureColumnIndexes, int targetColumnIndex, int stepSize,
      double miniBatchFraction) throws DDFException {
    return this.train("LinearRegressionWithSGD", featureColumnIndexes, targetColumnIndex, stepSize, miniBatchFraction);
  }

}
