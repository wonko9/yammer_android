package com.yammer.v1;

public interface Block<Param, Return> {
  public Return call(Param _value);
}