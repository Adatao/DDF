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
 * Autogenerated by Thrift Compiler (0.9.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.adatao.pa.thrift.generated;

import java.util.BitSet;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import org.apache.thrift.protocol.TTupleProtocol;
import org.apache.thrift.scheme.IScheme;
import org.apache.thrift.scheme.SchemeFactory;
import org.apache.thrift.scheme.StandardScheme;
import org.apache.thrift.scheme.TupleScheme;
import com.adatao.pa.spark.types.ExecutorResult;

@SuppressWarnings({ "serial", "unchecked", "rawtypes" })
public class JsonResult implements org.apache.thrift.TBase<JsonResult, JsonResult._Fields>, java.io.Serializable, Cloneable {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("JsonResult");

  private static final org.apache.thrift.protocol.TField SID_FIELD_DESC = new org.apache.thrift.protocol.TField("sid", org.apache.thrift.protocol.TType.STRING, (short)1);
  private static final org.apache.thrift.protocol.TField RESULT_FIELD_DESC = new org.apache.thrift.protocol.TField("result", org.apache.thrift.protocol.TType.STRING, (short)2);

  private static final Map<Class<? extends IScheme>, SchemeFactory> schemes = new HashMap<Class<? extends IScheme>, SchemeFactory>();
  
  static {
    schemes.put(StandardScheme.class, new JsonResultStandardSchemeFactory());
    schemes.put(TupleScheme.class, new JsonResultTupleSchemeFactory());
  }

  
  public String sid; // required
  public String result; // required

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    SID((short)1, "sid"),
    RESULT((short)2, "result");

    private static final Map<String, _Fields> byName = new HashMap<String, _Fields>();

    static {
      for (_Fields field : EnumSet.allOf(_Fields.class)) {
        byName.put(field.getFieldName(), field);
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, or null if its not found.
     */
    public static _Fields findByThriftId(int fieldId) {
      switch(fieldId) {
        case 1: // SID
          return SID;
        case 2: // RESULT
          return RESULT;
        default:
          return null;
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, throwing an exception
     * if it is not found.
     */
    public static _Fields findByThriftIdOrThrow(int fieldId) {
      _Fields fields = findByThriftId(fieldId);
      if (fields == null) throw new IllegalArgumentException("Field " + fieldId + " doesn't exist!");
      return fields;
    }

    /**
     * Find the _Fields constant that matches name, or null if its not found.
     */
    public static _Fields findByName(String name) {
      return byName.get(name);
    }

    private final short _thriftId;
    private final String _fieldName;

    _Fields(short thriftId, String fieldName) {
      _thriftId = thriftId;
      _fieldName = fieldName;
    }

    public short getThriftFieldId() {
      return _thriftId;
    }

    public String getFieldName() {
      return _fieldName;
    }
  }

  // isset id assignments
  public static final Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.SID, new org.apache.thrift.meta_data.FieldMetaData("sid", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    tmpMap.put(_Fields.RESULT, new org.apache.thrift.meta_data.FieldMetaData("result", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    metaDataMap = Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(JsonResult.class, metaDataMap);
  }

  public JsonResult() {
  }

  public JsonResult(
    String sid,
    String result)
  {
    this();
    this.sid = sid;
    this.result = result;
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public JsonResult(JsonResult other) {
    if (other.isSetSid()) {
      this.sid = other.sid;
    }
    if (other.isSetResult()) {
      this.result = other.result;
    }
  }

  public JsonResult deepCopy() {
    return new JsonResult(this);
  }

  @Override
  public void clear() {
    this.sid = null;
    this.result = null;
  }

  public String getSid() {
    return this.sid;
  }

  public JsonResult setSid(String sid) {
    this.sid = sid;
    return this;
  }

  public void unsetSid() {
    this.sid = null;
  }

  /** Returns true if field sid is set (has been assigned a value) and false otherwise */
  public boolean isSetSid() {
    return this.sid != null;
  }

  public void setSidIsSet(boolean value) {
    if (!value) {
      this.sid = null;
    }
  }

  public String getResult() {
    return this.result;
  }

  public JsonResult setResult(String result) {
    this.result = result;
    return this;
  }

  public JsonResult setResult(ExecutorResult result) {
    this.result = result.toJson();
    return this;
  }
  
  public void unsetResult() {
    this.result = null;
  }

  /** Returns true if field result is set (has been assigned a value) and false otherwise */
  public boolean isSetResult() {
    return this.result != null;
  }

  public void setResultIsSet(boolean value) {
    if (!value) {
      this.result = null;
    }
  }

  public void setFieldValue(_Fields field, Object value) {
    switch (field) {
    case SID:
      if (value == null) {
        unsetSid();
      } else {
        setSid((String)value);
      }
      break;

    case RESULT:
      if (value == null) {
        unsetResult();
      } else {
        setResult((String)value);
      }
      break;

    }
  }

  public Object getFieldValue(_Fields field) {
    switch (field) {
    case SID:
      return getSid();

    case RESULT:
      return getResult();

    }
    throw new IllegalStateException();
  }

  /** Returns true if field corresponding to fieldID is set (has been assigned a value) and false otherwise */
  public boolean isSet(_Fields field) {
    if (field == null) {
      throw new IllegalArgumentException();
    }

    switch (field) {
    case SID:
      return isSetSid();
    case RESULT:
      return isSetResult();
    }
    throw new IllegalStateException();
  }

  @Override
  public boolean equals(Object that) {
    if (that == null)
      return false;
    if (that instanceof JsonResult)
      return this.equals((JsonResult)that);
    return false;
  }

  public boolean equals(JsonResult that) {
    if (that == null)
      return false;

    boolean this_present_sid = true && this.isSetSid();
    boolean that_present_sid = true && that.isSetSid();
    if (this_present_sid || that_present_sid) {
      if (!(this_present_sid && that_present_sid))
        return false;
      if (!this.sid.equals(that.sid))
        return false;
    }

    boolean this_present_result = true && this.isSetResult();
    boolean that_present_result = true && that.isSetResult();
    if (this_present_result || that_present_result) {
      if (!(this_present_result && that_present_result))
        return false;
      if (!this.result.equals(that.result))
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return 0;
  }

  public int compareTo(JsonResult other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;
    JsonResult typedOther = (JsonResult)other;

    lastComparison = Boolean.valueOf(isSetSid()).compareTo(typedOther.isSetSid());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetSid()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.sid, typedOther.sid);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetResult()).compareTo(typedOther.isSetResult());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetResult()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.result, typedOther.result);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    return 0;
  }

  public _Fields fieldForId(int fieldId) {
    return _Fields.findByThriftId(fieldId);
  }

  public void read(org.apache.thrift.protocol.TProtocol iprot) throws org.apache.thrift.TException {
    schemes.get(iprot.getScheme()).getScheme().read(iprot, this);
  }

  public void write(org.apache.thrift.protocol.TProtocol oprot) throws org.apache.thrift.TException {
    schemes.get(oprot.getScheme()).getScheme().write(oprot, this);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("JsonResult(");
    boolean first = true;

    sb.append("sid:");
    if (this.sid == null) {
      sb.append("null");
    } else {
      sb.append(this.sid);
    }
    first = false;
    if (!first) sb.append(", ");
    sb.append("result:");
    if (this.result == null) {
      sb.append("null");
    } else {
      sb.append(this.result);
    }
    first = false;
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    // check for sub-struct validity
  }

  private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
    try {
      write(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(out)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
    try {
      read(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(in)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private static class JsonResultStandardSchemeFactory implements SchemeFactory {
    public JsonResultStandardScheme getScheme() {
      return new JsonResultStandardScheme();
    }
  }

  private static class JsonResultStandardScheme extends StandardScheme<JsonResult> {

    public void read(org.apache.thrift.protocol.TProtocol iprot, JsonResult struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // SID
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.sid = iprot.readString();
              struct.setSidIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 2: // RESULT
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.result = iprot.readString();
              struct.setResultIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          default:
            org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
        }
        iprot.readFieldEnd();
      }
      iprot.readStructEnd();

      // check for required fields of primitive type, which can't be checked in the validate method
      struct.validate();
    }

    public void write(org.apache.thrift.protocol.TProtocol oprot, JsonResult struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (struct.sid != null) {
        oprot.writeFieldBegin(SID_FIELD_DESC);
        oprot.writeString(struct.sid);
        oprot.writeFieldEnd();
      }
      if (struct.result != null) {
        oprot.writeFieldBegin(RESULT_FIELD_DESC);
        oprot.writeString(struct.result);
        oprot.writeFieldEnd();
      }
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class JsonResultTupleSchemeFactory implements SchemeFactory {
    public JsonResultTupleScheme getScheme() {
      return new JsonResultTupleScheme();
    }
  }

  private static class JsonResultTupleScheme extends TupleScheme<JsonResult> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, JsonResult struct) throws org.apache.thrift.TException {
      TTupleProtocol oprot = (TTupleProtocol) prot;
      BitSet optionals = new BitSet();
      if (struct.isSetSid()) {
        optionals.set(0);
      }
      if (struct.isSetResult()) {
        optionals.set(1);
      }
      oprot.writeBitSet(optionals, 2);
      if (struct.isSetSid()) {
        oprot.writeString(struct.sid);
      }
      if (struct.isSetResult()) {
        oprot.writeString(struct.result);
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, JsonResult struct) throws org.apache.thrift.TException {
      TTupleProtocol iprot = (TTupleProtocol) prot;
      BitSet incoming = iprot.readBitSet(2);
      if (incoming.get(0)) {
        struct.sid = iprot.readString();
        struct.setSidIsSet(true);
      }
      if (incoming.get(1)) {
        struct.result = iprot.readString();
        struct.setResultIsSet(true);
      }
    }
  }

}

