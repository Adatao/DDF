package com.adatao.ddf.analytics;

import java.util.List;
import com.adatao.ddf.DDF;
import com.adatao.ddf.analytics.AggregationHandler.AggregateField;
import com.adatao.ddf.analytics.AggregationHandler.AggregationResult;
import com.adatao.ddf.exception.DDFException;
import com.adatao.ddf.misc.IHandleDDFFunctionalGroup;

public interface IHandleAggregation extends IHandleDDFFunctionalGroup {

  public double computeCorrelation(String columnA, String columnB) throws DDFException;

  public AggregationResult aggregate(List<AggregateField> fields) throws DDFException;
  
  public AggregationResult xtabs(List<AggregateField> fields) throws DDFException;
  
  public DDF groupBy(List<String> groupedColumns, List<String> aggregateFunctions) throws DDFException;

}

