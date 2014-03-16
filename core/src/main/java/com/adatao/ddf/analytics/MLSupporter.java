package com.adatao.ddf.analytics;


import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.adatao.ddf.content.APersistenceHandler;
import com.adatao.ddf.content.ISerializable;
import com.google.common.base.Joiner;
import scala.actors.threadpool.Arrays;
import com.adatao.ddf.DDF;
import com.adatao.ddf.exception.DDFException;
import com.adatao.ddf.misc.ADDFFunctionalGroupHandler;
import com.adatao.ddf.misc.Config;
import com.adatao.ddf.util.Utils.ClassMethod;
import com.adatao.ddf.util.Utils.MethodInfo;
import com.adatao.ddf.util.Utils.MethodInfo.ParamInfo;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gson.annotations.Expose;

/**
 */
public abstract class MLSupporter extends ADDFFunctionalGroupHandler implements ISupportML {

  private Boolean sIsNonceInitialized = false;


  /**
   * For ML reflection test-case only
   */
  @Deprecated
  public MLSupporter() {
    super(null);
  }

  public MLSupporter(DDF theDDF) {
    super(theDDF);
    this.initialize();
  }

  private void initialize() {
    if (sIsNonceInitialized) return;

    synchronized (sIsNonceInitialized) {
      if (sIsNonceInitialized) return;
      sIsNonceInitialized = true;

      this.initializeConfiguration();
    }
  }

  /**
   * Optional: put in any hard-coded mapping configuration here
   */
  private void initializeConfiguration() {
    // if (Strings.isNullOrEmpty(Config.getValue(ConfigConstant.ENGINE_NAME_BASIC.toString(), "kmeans"))) {
    // Config.set(ConfigConstant.ENGINE_NAME_BASIC.toString(), "kmeans",
    // String.format("%s#%s", MLSupporter.class.getName(), "dummyKMeans"));
    // }
  }

  // //// ISupportML //////

  public static final String DEFAULT_TRAIN_METHOD_NAME = "train";

  public static final String DEFAULT_PREDICT_METHOD_NAME = "predict";
  /**
   * Runs a training algorithm on the entire DDF dataset.
   * 
   * @param trainMethodName
   * @param args
   * @return
   * @throws DDFException
   */
  public Object train(String trainMethodName, Object... paramArgs) throws DDFException {
    /**
     * Example signatures we must support:
     * <p/>
     * Unsupervised Training
     * <p/>
     * <code>
     * Kmeans.train(data: RDD[Array[Double]], k: Int, maxIterations: Int, runs: Int, initializationMode: String)
     * </code>
     * <p/>
     * Supervised Training
     * <p/>
     * <code>
     * LogisticRegressionWithSGD.train(input: RDD[LabeledPoint], numIterations: Int, stepSize: Double, miniBatchFraction:
     * Double, initialWeights: Array[Double])
     * 
     * SVM.train(input: RDD[LabeledPoint], numIterations: Int, stepSize: Double, regParam: Double, miniBatchFraction:
     * Double)
     * </code>
     */
    // Build the argument type array
    if (paramArgs == null) paramArgs = new Object[0];
    // Class<?>[] argTypes = new Class<?>[paramArgs.length];
    // for (int i = 0; i < paramArgs.length; i++) {
    // argTypes[i] = (paramArgs[i] == null ? null : paramArgs[i].getClass());
    // }
    String originalTrainMethodName = trainMethodName;
    // Locate the training method
    String mappedName = Config.getValueWithGlobalDefault(this.getEngine(), trainMethodName);
    if (!Strings.isNullOrEmpty(mappedName)) trainMethodName = mappedName;

    MLClassMethod trainMethod = new MLClassMethod(trainMethodName, DEFAULT_TRAIN_METHOD_NAME, paramArgs);
    if (trainMethod.getMethod() == null) {
      throw new DDFException(String.format("Cannot locate method specified by %s", trainMethodName));
    }
    // Now we need to map the DDF and its column specs to the input format expected by the method we're invoking
    Object[] allArgs = this.buildArgsForMethod(trainMethod.getMethod(), paramArgs);

    // Invoke the training method
    Object result = trainMethod.classInvoke(allArgs);
    return result;
  }

  protected abstract <T, U> DDF predictImpl(DDF ddf,Class<T> returnType, Class<U> predictInputType, Object model)
      throws DDFException;

  @Override
  public DDF predict(Object model) throws DDFException {
    DDF ddf = this.getDDF();
    MLPredictMethod mlPredictMethod = new MLPredictMethod(model);

    Class<?> predictInputType = mlPredictMethod.getInputType();

    Class<?> predictReturnType = mlPredictMethod.getPredictReturnType();
    return this.predictImpl(ddf, predictReturnType, predictInputType, model);
  }

  @SuppressWarnings("unchecked")
  private Object[] buildArgsForMethod(Method method, Object[] paramArgs) throws DDFException{
    MethodInfo methodInfo = new MethodInfo(method);
    List<ParamInfo> paramInfos = methodInfo.getParamInfos();
    if (paramInfos == null || paramInfos.size() == 0) return new Object[0];

    Object firstParam = this.convertDDF(paramInfos.get(0));

    if (paramArgs == null || paramArgs.length == 0) {
      return new Object[] { firstParam };

    } else {
      List<Object> result = Lists.newArrayList();
      result.add(firstParam);
      result.addAll(Arrays.asList(paramArgs));
      return result.toArray(new Object[0]);
    }
  }

  /**
   * Override this to return the approriate DDF representation matching that specified in {@link ParamInfo}. The base
   * implementation simply returns the DDF.
   * 
   * @param paramInfo
   * @return
   */
  protected Object convertDDF(ParamInfo paramInfo) throws DDFException{
    return this.getDDF();
  }


  /**
   * 
   */
  private class MLClassMethod extends ClassMethod {

    public MLClassMethod(String classHashMethodName, String defaultMethodName, Object[] args) throws DDFException {
      super(classHashMethodName, defaultMethodName, args);
    }

    /**
     * Override to search for methods that may contain initial arguments that precede our argTypes. These initial
     * arguments might be feature/target column specifications in a train() method. Thus we do the argType matching from
     * the end back to the beginning.
     */
    @Override
    protected void findAndSetMethod(Class<?> theClass, String methodName, Class<?>... argTypes)
        throws NoSuchMethodException, SecurityException {

      if (argTypes == null) argTypes = new Class<?>[0];

      Method foundMethod = null;

      // Scan all methods
      for (Method method : theClass.getDeclaredMethods()) {
        if (!methodName.equalsIgnoreCase(method.getName())) continue;


        // Scan all method arg types, starting from the end, and see if they match with our supplied arg types
        Class<?>[] methodArgTypes = method.getParameterTypes();

        // Check that the number of args are correct:
        // the # of args in the method must be = 1 + the # of args supplied
        // ASSUMING that one of the args in the method is for input data

        if((methodArgTypes.length - 1) == argTypes.length){
          foundMethod = method;
          break;
        }
      }

      if (foundMethod != null) {
        foundMethod.setAccessible(true);
        this.setMethod(foundMethod);
      }
    }
  }
}
