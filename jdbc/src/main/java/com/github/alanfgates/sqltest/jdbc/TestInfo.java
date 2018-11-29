package com.github.alanfgates.sqltest.jdbc;

import java.util.Collections;
import java.util.List;

class TestInfo {
  public String feature;
  public String id;
  public Object sql;
  Boolean passed;
  Exception exception;

  List<String> getSql() {
    if (sql instanceof String) return Collections.singletonList(sql.toString());
    else if (sql instanceof List) return (List<String>)sql;
    else throw new RuntimeException("Ack, sql is a " + sql.getClass().getName());
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder("feature: ");
    buf.append(feature)
        .append("\nid: ")
        .append(id)
        .append("\nsql: ");
    for (String s : getSql()) buf.append(s).append("; ");
    if (exception != null) buf.append("\nexception: ").append(exception).append("\n");
    return buf.toString();
  }
}
