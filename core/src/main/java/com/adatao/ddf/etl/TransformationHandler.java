package com.adatao.ddf.etl;

import java.util.List;
import com.adatao.ddf.DDF;
import com.adatao.ddf.analytics.Summary;
import com.adatao.ddf.content.Schema.Column;
import com.adatao.ddf.content.Schema.ColumnClass;
import com.adatao.ddf.exception.DDFException;
import com.adatao.ddf.misc.ADDFFunctionalGroupHandler;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

public class TransformationHandler extends ADDFFunctionalGroupHandler implements IHandleTransformations {

  public TransformationHandler(DDF theDDF) {
    super(theDDF);
    // TODO Auto-generated constructor stub
  }

  @Override
  public DDF transformScaleMinMax() throws DDFException {
    Summary[] summaryArr = this.getDDF().getSummary();
    List<Column> columns = this.getDDF().getSchema().getColumns();

    // Compose a transformation query
    StringBuffer sqlCmdBuffer = new StringBuffer("SELECT ");

    for (int i = 0; i < columns.size(); i++) {
      Column col = columns.get(i);
      if (!col.isNumeric() || col.getColumnClass() == ColumnClass.FACTOR) {
        sqlCmdBuffer.append(col.getName()).append(" ");
      } else {
        // subtract min, divide by (max - min)
        sqlCmdBuffer.append(String.format("((%s - %s) / %s) as %s ", col.getName(), summaryArr[i].min(),
            (summaryArr[i].max() - summaryArr[i].min()), col.getName()));
      }
      sqlCmdBuffer.append(",");
    }
    sqlCmdBuffer.setLength(sqlCmdBuffer.length() - 1);
    sqlCmdBuffer.append("FROM ").append(this.getDDF().getTableName());

    DDF newddf = this.getManager().sql2ddf(sqlCmdBuffer.toString());
    this.getManager().addDDF(newddf);

    return newddf;
  }

  @Override
  public DDF transformScaleStandard() throws DDFException {
    Summary[] summaryArr = this.getDDF().getSummary();
    List<Column> columns = this.getDDF().getSchema().getColumns();

    // Compose a transformation query
    StringBuffer sqlCmdBuffer = new StringBuffer("SELECT ");

    for (int i = 0; i < columns.size(); i++) {
      Column col = columns.get(i);
      if (!col.isNumeric() || col.getColumnClass() == ColumnClass.FACTOR) {
        sqlCmdBuffer.append(col.getName());
      } else {
        // subtract mean, divide by stdev
        sqlCmdBuffer.append(String.format("((%s - %s) / %s) as %s ", col.getName(), summaryArr[i].mean(),
            summaryArr[i].stdev(), col.getName()));
      }
      sqlCmdBuffer.append(",");
    }
    sqlCmdBuffer.setLength(sqlCmdBuffer.length() - 1);
    sqlCmdBuffer.append("FROM ").append(this.getDDF().getTableName());

    DDF newddf = this.getManager().sql2ddf(sqlCmdBuffer.toString());
    
//    this.getManager().addDDF(newddf);
//    return newddf;
    
    DDF curDDF = this.getDDF();
    curDDF.getSchema().setTableName(newddf.getTableName());

    curDDF.getRepresentationHandler().reset();
    curDDF.getRepresentationHandler().add(newddf.getRepresentationHandler().getDefault());
    return curDDF;

  }

  public DDF transformNativeRserve(String transformExpression) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public DDF transformMapReduceNative(String mapFuncDef, String reduceFuncDef, boolean mapsideCombine) {
    // TODO Auto-generated method stub
    return null;
  }
  
  public DDF transformUDF(String RExp, List<String> columns) throws DDFException {
    
    String columnList;
    if(columns != null) {
      columnList = Joiner.on( "," ).skipNulls().join(columns);
    } else {
      columnList = "*";
    }    
    
    String sqlCmd = String.format("SELECT %s, %s FROM %s", columnList, RToSqlUdf(RExp), this.getDDF().getTableName());
    DDF newddf = this.getManager().sql2ddf(sqlCmd);
    DDF curDDF = this.getDDF();
    curDDF.getSchema().setTableName(newddf.getTableName());

    curDDF.getRepresentationHandler().reset();
    curDDF.getRepresentationHandler().add(newddf.getRepresentationHandler().getDefault());
    
    return curDDF;
  }
  
  public DDF transformUDF(String RExp) throws DDFException {
    return transformUDF(RExp, null);
  }
  
  /**
   * Parse R transform expression to Hive equivalent
   * @param transformExpr: e.g: "foobar = arrtime - crsarrtime, speed = distance / airtime"
   * @return "(arrtime - crsarrtime) as foobar, (distance / airtime) as speed
   */
  
  private String RToSqlUdf(String RExp) {
    List<String> udfs = Lists.newArrayList();
    for (String str : RExp.split(",")) {
      String[] udf = str.trim().split("[=~]");
      if (udf.length ==1) {
        udfs.add(String.format("(%s)", udf[0]));
      } else {
        udfs.add(String.format("(%s) as %s",udf[1], udf[0]));
      }
    }
    return Joiner.on( "," ).join(udfs);
  }

  
}
