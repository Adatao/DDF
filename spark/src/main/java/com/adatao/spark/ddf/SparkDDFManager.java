package com.adatao.spark.ddf;


import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.filter.CompareFilter.CompareOp;
import org.apache.hadoop.hbase.filter.FilterList;
import org.apache.hadoop.hbase.filter.SingleColumnValueFilter;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableInputFormat;
import org.apache.hadoop.hbase.util.Base64;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.phoenix.parse.AliasedNode;
import org.apache.phoenix.parse.ColumnParseNode;
import org.apache.phoenix.parse.EqualParseNode;
import org.apache.phoenix.parse.GreaterThanOrEqualParseNode;
import org.apache.phoenix.parse.GreaterThanParseNode;
import org.apache.phoenix.parse.LiteralParseNode;
import org.apache.phoenix.parse.NamedTableNode;
import org.apache.phoenix.parse.NotEqualParseNode;
import org.apache.phoenix.parse.ParseNode;
import org.apache.phoenix.parse.SQLParser;
import org.apache.phoenix.parse.SelectStatement;
import org.apache.phoenix.parse.TableNode;
import org.apache.phoenix.schema.PDataType;
import org.apache.spark.SparkContext;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.rdd.RDD;
import scala.Tuple2;
import shark.SharkContext;
import shark.SharkEnv;
import shark.api.JavaSharkContext;
import shark.memstore2.TablePartition;
import com.adatao.ddf.DDF;
import com.adatao.ddf.DDFManager;
import com.adatao.ddf.content.Schema;
import com.adatao.ddf.exception.DDFException;
import com.adatao.ddf.util.DDFUtils;

/**
 * An Apache-Spark-based implementation of DDFManager
 */
public class SparkDDFManager extends DDFManager {

  @Override
  public String getEngine() {
    return "spark";
  }


  private static final String DEFAULT_SPARK_APPNAME = "DDFClient";
  private static final String DEFAULT_SPARK_MASTER = "local[4]";



  public SparkDDFManager(SparkContext sparkContext) throws DDFException {
    this.initialize(sparkContext, null);
  }

  /**
   * Use system environment variables to configure the SparkContext creation.
   * 
   * @throws DDFException
   */
  public SparkDDFManager() throws DDFException {
    this.initialize(null, new HashMap<String, String>());
  }

  public SparkDDFManager(Map<String, String> params) throws DDFException {
    this.initialize(null, params);
  }

  private void initialize(SparkContext sparkContext, Map<String, String> params) throws DDFException {
    this.setSparkContext(sparkContext == null ? this.createSparkContext(params) : sparkContext);

    //it will never go here
    //TODO remove later
    if (sparkContext instanceof SharkContext) {
    	this.setSharkContext((SharkContext) sparkContext);
    	sparkContext.conf().set("spark.kryo.registrator", "com.adatao.spark.content.KryoRegistrator");
    	mSharkContext.conf().set("spark.kryo.registrator", "com.adatao.spark.content.KryoRegistrator");
    	mSparkContext.conf().set("spark.kryo.registrator", "com.adatao.spark.content.KryoRegistrator");
    	
    	mLog.info(">>>>>>>>>>>>> setting Kryo for mSharkContext: " + mSharkContext.conf().get("spark.kryo.registrator"));
    }
  }



  public String getDDFEngine() {
    return "spark";
  }


  private SparkContext mSparkContext;


  public SparkContext getSparkContext() {
    return mSparkContext;
  }

  private void setSparkContext(SparkContext sparkContext) {
    this.mSparkContext = sparkContext;
  }


  private SharkContext mSharkContext;


  public SharkContext getSharkContext() {
    return mSharkContext;
  }

  private JavaSharkContext mJavaSharkContext;
  
  
  public JavaSharkContext getJavaSharkContext() {
    return mJavaSharkContext;
  }

  public void setJavaSharkContext(JavaSharkContext javaSharkContext) {
    this.mJavaSharkContext = javaSharkContext;
  }

  /**
   * Also calls setSparkContext() to the same sharkContext
   * 
   * @param sharkContext
   */
  private void setSharkContext(SharkContext sharkContext) {
    this.mSharkContext = sharkContext;
    this.setSparkContext(sharkContext);
  }


  private Map<String, String> mSparkContextParams;


  public Map<String, String> getSparkContextParams() {
    return mSparkContextParams;
  }

  private void setSparkContextParams(Map<String, String> mSparkContextParams) {
    this.mSparkContextParams = mSparkContextParams;
  }



  public void shutdown() {
    if (this.getSharkContext() != null) {
      this.getSharkContext().stop();
    } else if (this.getSparkContext() != null) {
      this.getSparkContext().stop();
    }
  }


  private static final String[][] SPARK_ENV_VARS = new String[][] { 
    // @formatter:off
    { "SPARK_APPNAME", "spark.appname" },
    { "SPARK_MASTER", "spark.master" }, 
    { "SPARK_HOME", "spark.home" }, 
    { "SPARK_SERIALIZER", "spark.serializer" },
    { "SPARK_SERIALIZER", "spark.serializer" },
    { "HIVE_HOME", "hive.home" },
    { "HADOOP_HOME", "hadoop.home" },
    { "DDFSPARK_JAR", "ddfspark.jar" } 
    // @formatter:on
  };


  /**
   * Takes an existing params map, and reads both environment as well as system property settings to merge into it. The
   * merge priority is as follows: (1) already set in params, (2) in system properties (e.g., -Dspark.home=xxx), (3) in
   * environment variables (e.g., export SPARK_HOME=xxx)
   * 
   * @param params
   * @return
   */
  private Map<String, String> mergeSparkParamsFromSettings(Map<String, String> params) {
    if (params == null) params = new HashMap<String, String>();

    Map<String, String> env = System.getenv();

    for (String[] varPair : SPARK_ENV_VARS) {
      if (params.containsKey(varPair[0])) continue; // already set in params

      // Read setting either from System Properties, or environment variable.
      // Env variable has lower priority if both are set.
      String value = System.getProperty(varPair[1], env.get(varPair[0]));
      if (value != null && value.length() > 0) params.put(varPair[0], value);
    }

    // Some well-known defaults
    if (!params.containsKey("SPARK_MASTER")) params.put("SPARK_MASTER", DEFAULT_SPARK_MASTER);
    if (!params.containsKey("SPARK_APPNAME")) params.put("SPARK_APPNAME", DEFAULT_SPARK_APPNAME);

    return params;
  }

  /**
   * Side effect: also sets SharkContext and SparkContextParams in case the client wants to examine or use those.
   * 
   * @param params
   * @return
   * @throws DDFException
   */
  private SparkContext createSparkContext(Map<String, String> params) throws DDFException {
    this.setSparkContextParams(this.mergeSparkParamsFromSettings(params));
    String ddfSparkJar = params.get("DDFSPARK_JAR");
    String[] jobJars = ddfSparkJar != null ? ddfSparkJar.split(",") : new String[] {};

    mJavaSharkContext = new JavaSharkContext(params.get("SPARK_MASTER"), params.get("SPARK_APPNAME"),
        params.get("SPARK_HOME"), jobJars, params);
    this.setSharkContext(SharkEnv.initWithJavaSharkContext(mJavaSharkContext).sharkCtx());

    //set up serialization from System property
	mSharkContext.conf().set("spark.kryo.registrator", System.getProperty("spark.kryo.registrator", "com.adatao.spark.content.KryoRegistrator"));
	mSparkContext.conf().set("spark.kryo.registrator", System.getProperty("spark.kryo.registrator", "com.adatao.spark.content.KryoRegistrator"));
    
	return this.getSparkContext();
  }
  
  public DDF loadTable(String fileURL, String fieldSeparator) throws DDFException {
    JavaRDD<String> fileRDD = mJavaSharkContext.textFile(fileURL);
    String[] metaInfos = getMetaInfo(fileRDD, fieldSeparator);
    SecureRandom rand = new SecureRandom();
    String tableName = "tbl" + String.valueOf(Math.abs(rand.nextLong()));
    String cmd = "CREATE TABLE " + tableName + "(" + StringUtils.join(metaInfos, ", ") + ") ROW FORMAT DELIMITED FIELDS TERMINATED BY '" + fieldSeparator + "'";
    sql2txt(cmd);
    sql2txt("LOAD DATA LOCAL INPATH '" + fileURL + "' " +
        "INTO TABLE " + tableName);
    return sql2ddf("SELECT * FROM " + tableName);
  }
  
  /**
   * Given a String[] vector of data values along one column, try to infer what the data type should be.
   * 
   * TODO: precompile regex
   * 
   * @param vector
   * @return string representing name of the type "integer", "double", "character", or "logical" The algorithm will
   *         first scan the vector to detect whether the vector contains only digits, ',' and '.', <br>
   *         if true, then it will detect whether the vector contains '.', <br>
   *         &nbsp; &nbsp; if true then the vector is double else it is integer <br>
   *         if false, then it will detect whether the vector contains only 'T' and 'F' <br>
   *         &nbsp; &nbsp; if true then the vector is logical, otherwise it is characters
   */
  public static String determineType(String[] vector, Boolean doPreferDouble) {
    boolean isNumber = true;
    boolean isInteger = true;
    boolean isLogical = true;
    boolean allNA = true;

    for (String s : vector) {
      if (s == null || s.startsWith("NA") || s.startsWith("Na") || s.matches("^\\s*$")) {
        // Ignore, don't set the type based on this
        continue;
      }

      allNA = false;

      if (isNumber) {
        // match numbers: 123,456.123 123 123,456 456.123 .123
        if (!s.matches("(^|^-)((\\d+(,\\d+)*)|(\\d*))\\.?\\d+$")) {
          isNumber = false;
        }
        // match double
        else if (isInteger && s.matches("(^|^-)\\d*\\.{1}\\d+$")) {
          isInteger = false;
        }
      }

      // NOTE: cannot use "else" because isNumber changed in the previous
      // if block
      if (isLogical) {
        if (!s.toLowerCase().matches("^t|f|true|false$")) {
          isLogical = false;
        }
      }
    }

    // String result = "Unknown";
    String result = "string";

    if (!allNA) {
      if (isNumber) {
        if (!isInteger || doPreferDouble) {
          result = "double";
        }
        else {
          result = "int";
        }
      }
      else {
        if (isLogical) {
          result = "boolean";
        }
        else {
          result = "string";
        }
      }
    }
    return result;
  }
  
  /**
   * TODO: check more than a few lines in case some lines have NA
   * 
   * @param fileRDD
   * @return
   */
  public String[] getMetaInfo(JavaRDD<String> fileRDD, String fieldSeparator) {
    String[] headers = null;
    int sampleSize = 5;

    // sanity check
    if (sampleSize < 1) {
      mLog.info("DATATYPE_SAMPLE_SIZE must be bigger than 1");
      return null;
    }

    List<String> sampleStr = fileRDD.take(sampleSize);
    sampleSize = sampleStr.size(); // actual sample size
    mLog.info("Sample size: " + sampleSize);

    // create sample list for getting data type
    String[] firstSplit = sampleStr.get(0).split(fieldSeparator);

    // get header
    boolean hasHeader = false;
    if (hasHeader) {
      headers = firstSplit;
    }
    else {
      headers = new String[firstSplit.length];
      for (int i = 0; i < headers.length;) {
        headers[i] = "V" + (++i);
      }
    }

    String[][] samples = hasHeader ? (new String[firstSplit.length][sampleSize - 1])
        : (new String[firstSplit.length][sampleSize]);

    String[] metaInfoArray = new String[firstSplit.length];
    int start = hasHeader ? 1 : 0;
    for (int j = start; j < sampleSize; j++) {
      firstSplit = sampleStr.get(j).split(fieldSeparator);
      for (int i = 0; i < firstSplit.length; i++) {
        samples[i][j - start] = firstSplit[i];
      }
    }

    boolean doPreferDouble = true;
    for (int i = 0; i < samples.length; i++) {
      String[] vector = samples[i];
      metaInfoArray[i] = headers[i] + " " + determineType(vector, doPreferDouble);
    }

    return metaInfoArray;
  }
  
  /*
   * return Scan object
   */
  public Scan parseQuery(String command) {
    SQLParser parser;
    command = command.replace(":", "_");
    try {
      // make sure input query for SQLParser doesn't contain semi colon
      parser = new SQLParser(new StringReader(command));
      SelectStatement stm = parser.parseQuery();
      Scan scan = new Scan();

      // get table name, assumption select from one table
      // TODO assumption one table
      List<TableNode> lst = stm.getFrom();
      NamedTableNode temp = (NamedTableNode) lst.get(0);
      // tableName = temp.getName().getTableName();

      // get selected columns, just simply list all columns here and add to scan object
      String cf = "";
      String cq = "";
      List<AliasedNode> nodes = stm.getSelect();
      Iterator<AliasedNode> it = nodes.iterator();
      while (it.hasNext()) {
        AliasedNode node = it.next();
        String column = node.getNode().getAlias();
        System.out.println(">>>>>>>>>>>>>>> column  = " + column);
        if (column.contains("_")) {
          cf = column.split("_")[0];
          cq = column.split("_")[1];
          scan.addColumn(Bytes.toBytes(cf), Bytes.toBytes(cq));
        } else {
          cf = column;
          scan.addFamily(Bytes.toBytes(cf));
        }
      }

      // get where conditions here
      ParseNode where = stm.getWhere();
      if (where != null && where.getChildren() != null && where.getChildren().size() > 0) {
        List<ParseNode> children = where.getChildren();
        Iterator<ParseNode> it2 = children.iterator();
        FilterList list = new FilterList(FilterList.Operator.MUST_PASS_ONE);
        // check what type of where condition
        while (it2.hasNext()) {
          ParseNode child = it2.next();
          list = parseFilter(child, list);
        }
        // set filter
        if (list != null && list.getFilters() != null && list.getFilters().size() > 0) {
          scan.setFilter(list);
        }
      }
      System.out.println(">>>> scan = " + scan.toString());
      return scan;
      // get table
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    // parser.parseStatement();
    catch (SQLException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return null;
  }
  
//TODO use must pass one first, if we dont' have OR then change to must pass all
 public FilterList parseFilter(ParseNode filterNode, FilterList list) {
   CompareOp op = null;// getOperator(filter);

   if (filterNode instanceof GreaterThanParseNode) {
     op = CompareOp.GREATER;
   } else if (filterNode instanceof GreaterThanOrEqualParseNode) {
     op = CompareOp.GREATER_OR_EQUAL;
   } else if (filterNode instanceof NotEqualParseNode) {
     op = CompareOp.NOT_EQUAL;
   } else if (filterNode instanceof EqualParseNode) {
     op = CompareOp.EQUAL;
   }

   String whereColumnName = "";
   String wherevalue = "";
   List<ParseNode> children = filterNode.getChildren();
   Iterator<ParseNode> it = children.iterator();
   while (it.hasNext()) {
     ParseNode node = it.next();
     if (node instanceof ColumnParseNode) {
       whereColumnName = ((ColumnParseNode) node).getName();
       System.out.println("where column name = " + whereColumnName);
     } else if (node instanceof LiteralParseNode) {
       String atype = ((LiteralParseNode) node).getType().name();
       System.out.println("atype = " + atype);
       wherevalue = ((LiteralParseNode) node).getValue().toString();
     }
     // current node is still compound node
     else {
       list = parseFilter(node, list);
     }
   }

   // parse
   if (whereColumnName != null && !whereColumnName.equals("") && wherevalue != null && !wherevalue.equals("")) {
     String wherecf = whereColumnName.split("_")[0];
     String wherecq = whereColumnName.split("_")[1];

     System.out.println(">>>>>>>>>> column family : " + wherecf + "\t column qualifier:" + wherecq + "\t wherevalue: "
         + wherevalue + "\t operator = " + op.toString());

     SingleColumnValueFilter filter1 = new SingleColumnValueFilter(Bytes.toBytes(wherecf), Bytes.toBytes(wherecq), op,
         Bytes.toBytes(wherevalue));
     list.addFilter(filter1);
   }
   return list;
 }
  
  public String parseColumnTypes(String command) {
    SQLParser parser;
    String columnsNameType = "";
    // get selected columns, just simply list all columns here and add to scan object
    HashMap<String, String> colTypes = new HashMap<String, String>();
    // replace : to _
    command = command.replace(":", "_");

    try {
      // make sure input query for SQLParser doesn't contain semi colon
      parser = new SQLParser(new StringReader(command));
      SelectStatement stm = parser.parseQuery();
      List<AliasedNode> nodes = stm.getSelect();
      Iterator<AliasedNode> it = nodes.iterator();
      while (it.hasNext()) {
        AliasedNode node = it.next();
        String column = node.getNode().getAlias().toLowerCase();
        System.out.println(">>>>>>>>>>>>>>> column  = " + column);
        if (column.contains("_")) {
          colTypes.put(column, "string");
        } else {
          colTypes.put(column, "string");
        }
      }

      // put column type from where condition, this will override pre-set selected column's types
      ParseNode where = stm.getWhere();
      if (where != null && where.getChildren() != null && where.getChildren().size() > 0) {
        List<ParseNode> children = where.getChildren();
        Iterator<ParseNode> it2 = children.iterator();
        while (it2.hasNext()) {
          ParseNode child = it2.next();
          colTypes = parseFilterForColumnType(child, colTypes);
        }
      }

      // from colTypes, generate columnsNameType
      // TODO
      Iterator<String> cit = colTypes.keySet().iterator();
      while (cit.hasNext()) {
        String ctemp = cit.next();
        String ctype = colTypes.get(ctemp);
        columnsNameType += ctemp + " " + ctype + ",";
      }
      // remove last comma
      if (!columnsNameType.equals("") && columnsNameType.contains(",")) {
        columnsNameType = columnsNameType.substring(0, columnsNameType.length() - 1);
      }
      System.out.println(">>>>>>>>>>>>>. columnsNameType=" + columnsNameType);
      return columnsNameType;
      // get table
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    // parser.parseStatement();
    catch (SQLException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return null;
  }
  
  // TODO use must pass one first, if we dont' have OR then change to must pass all
  public HashMap<String, String> parseFilterForColumnType(ParseNode filterNode, HashMap<String, String> colTypes) {
    String whereColumnName = "";
    String whereColumnType = "";
    List<ParseNode> children = filterNode.getChildren();
    Iterator<ParseNode> it = children.iterator();
    while (it.hasNext()) {
      ParseNode node = it.next();
      if (node instanceof ColumnParseNode) {
        whereColumnName = ((ColumnParseNode) node).getName();
        System.out.println("where column name = " + whereColumnName);
      } else if (node instanceof LiteralParseNode) {
        // TODO support other column type
        PDataType type = ((LiteralParseNode) node).getType();
        if (type == PDataType.INTEGER || type == PDataType.DOUBLE || type == PDataType.FLOAT || type == PDataType.LONG
            || type == PDataType.DECIMAL || type == PDataType.SMALLINT || type == PDataType.TINYINT) {
          whereColumnType = "double";
        }
      }
      // current node is still compound node
      else {
        colTypes = parseFilterForColumnType(filterNode, colTypes);
      }
    }
    // parse
    if (whereColumnName != null && !whereColumnName.equals("") && whereColumnType != null
        && !whereColumnType.equals("")) {
      String wherecf = whereColumnName.split("_")[0];
      String wherecq = whereColumnName.split("_")[1];
      System.out.println(">>>>>>>>>> column family : " + wherecf + "\t column qualifier:" + wherecq
          + "\t whereColumnType: " + whereColumnType);
      if (colTypes.containsKey(whereColumnName.toLowerCase())) colTypes.remove(whereColumnName.toLowerCase());
      colTypes.put(whereColumnName.toLowerCase(), whereColumnType);

    }
    return colTypes;
  }
  
  private String parseHbaseTable(String command) {
    String tableName = "";
    SQLParser parser;
    command = command.replace(":", "_");
    try {
      parser = new SQLParser(new StringReader(command));
      SelectStatement stm = parser.parseQuery();
      Scan scan = new Scan();
      // get table name, assumption select from one table
      // TODO assumption one table
      List<TableNode> lst = stm.getFrom();
      NamedTableNode temp = (NamedTableNode) lst.get(0);
      tableName = temp.getName().getTableName().trim();

    } catch (Exception ex) {

    }
    return tableName;
  }
  
  // Experimental stuffs
  public DDF loadTable(String tableName, String columnsNameType2, String command) throws DDFException {
    
    Scan scan = parseQuery(command);
    String columnsNameType = parseColumnTypes(command);
    String hbTableName = parseHbaseTable(command);
    
    // JavaRDD<String> fileRDD = mJavaSharkContext.textFile(fileURL);
    Configuration config = HBaseConfiguration.create();
    // config.set("hbase.zookeeper.znode.parent", "hostname1");
    // config.set("hbase.zookeeper.quorum","hostname1");
//    config.set("hbase.zookeeper.property.clientPort","2222");
    config.set("hbase.master", "localhost:");
    // config.set("fs.defaultFS","hdfs://hostname1/");
    // config.set("dfs.namenode.rpc-address","localhost:8020");
    config.set(TableInputFormat.INPUT_TABLE, tableName);
    // set SCAN in conf
    try {
      config.set(TableInputFormat.SCAN, convertScanToString(scan));
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    RDD<Tuple2<ImmutableBytesWritable, Result>> hBaseRDD = getSparkContext().newAPIHadoopRDD(config,
        TableInputFormat.class, ImmutableBytesWritable.class, Result.class);
    //convert to RDD[Row] or RDD[Array[Double]]
//    RDD<double[]> result = convertRDD(hBaseRDD);
    RDD<Object[]> result = convertRDD2(hBaseRDD);
    
//    generateObjectName(Object obj, String sourceName, String operation, String desiredName)
    System.out.println(">>>>>>>>>>>> tableName =  " + tableName);
    this.sql2txt("drop table if exists " + tableName);
    Schema schema = new Schema(tableName, columnsNameType); 
    DDF ddf =  new SparkDDF(this, result, Object[].class, "", tableName, schema);
    String newname = "SparkDDF_spark_" + UUID.randomUUID().toString();
    ddf.setName(newname);
    ddf.getRepresentationHandler().get(RDD.class, TablePartition.class);
    
    return ddf;
  }
  
  private String getValue(String filter, String whereColumn, CompareOp op) {
    String value = "";
    if(op == CompareOp.EQUAL) value = filter.substring(filter.indexOf("=")+1, filter.length());
    if(op == CompareOp.GREATER) value = filter.substring(filter.indexOf(">")+1, filter.length());
    if(op == CompareOp.LESS) value = filter.substring(filter.indexOf("<")+1, filter.length());
    return value;
  }

  private CompareOp getOperator(String filter) {
    if(filter.contains("=")) return CompareOp.EQUAL;
    if(filter.contains(">")) return CompareOp.GREATER;
    if(filter.contains("<")) return CompareOp.LESS;
    return null;
  }

  private String getProjectionColumn(String filter, List<String> columns) {
    Iterator it = columns.iterator();
    String c = "";
    String cf = "";
    String cq = "";
    while (it.hasNext()) {
      c = (String) it.next();
      if(filter.contains(c)) {
        return c;
      }
    }
    return "";
  }

  public RDD<double[]> convertRDD(RDD<Tuple2<ImmutableBytesWritable, Result>> hbaseRDD) {
    RDD<double[]> result = hbaseRDD.map(new ConvertMapper(), null);
    return result;
  }
  
  public RDD<Object[]> convertRDD2(RDD<Tuple2<ImmutableBytesWritable, Result>> hbaseRDD) {
    RDD<Object[]> result = hbaseRDD.map(new ConvertMapper2(), null);
    return result;
  }
  
//  GetSummaryMapper extends Function<Object[], Summary[]>
  
  private static class ConvertMapper extends Function <Tuple2<ImmutableBytesWritable, Result>, double[]> {
    public ConvertMapper() {
      System.out.println(">>>>>>>>>>>>>> ConvertMapper");
    }
    @Override
    public double[] call(Tuple2<ImmutableBytesWritable, Result> input) throws Exception {
      ImmutableBytesWritable key = input._1;
      Result res = input._2;
      List<org.apache.hadoop.hbase.KeyValue> keyValues = res.list();
      int size = keyValues.size();
      double[] arrResult = new double[size];
      int i = 0;
      Iterator<KeyValue> it = keyValues.iterator();
      while(it.hasNext()) {
        KeyValue kv = it.next();
        System.out.println(">>> family=" + Bytes.toString(kv.getFamily()));
        System.out.println(">>> qualifier=" + Bytes.toString(kv.getQualifier()));
        double a = Double.parseDouble(Bytes.toString(kv.getValue()));
        arrResult[i] = a;
        System.out.println(">>>>>>a=" + a);
        i++;
      }
      return arrResult;
    }
  }
  
  private static class ConvertMapper2 extends Function <Tuple2<ImmutableBytesWritable, Result>, Object[]> {
    public ConvertMapper2() {
      System.out.println(">>>>>>>>>>>>>> ConvertMapper");
    }
    @Override
    public Object[] call(Tuple2<ImmutableBytesWritable, Result> input) throws Exception {
      ImmutableBytesWritable key = input._1;
      Result res = input._2;
      List<org.apache.hadoop.hbase.KeyValue> keyValues = res.list();
      int size = keyValues.size();
      Object[] arrResult = new Object[size];
      int i = 0;
      Iterator<KeyValue> it = keyValues.iterator();
      while(it.hasNext()) {
        KeyValue kv = it.next();
        System.out.println(">>> family=" + Bytes.toString(kv.getFamily()));
        System.out.println(">>> qualifier=" + Bytes.toString(kv.getQualifier()));
        String a = Bytes.toString(kv.getValue());
        arrResult[i] = a;
        System.out.println(">>>>>>a=" + a);
        i++;
      }
      return arrResult;
    }
  }


  static String convertScanToString(Scan scan) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(out);
    scan.write(dos);
    return Base64.encodeBytes(out.toByteArray());
  }
  
}
