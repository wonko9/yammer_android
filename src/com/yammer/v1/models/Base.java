package com.yammer.v1.models;

import android.provider.BaseColumns;

abstract class Base implements BaseColumns {

  static final String equalClause(String _field, String _value) {
    return _field + "=\"" + _value + '"';
  }
  
  static final String equalClause(String _field, long _value) {
    return _field + "= " + _value;
  }
  
}
