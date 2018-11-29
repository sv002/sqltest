package com.github.alanfgates.sqltest.jdbc;

import java.util.ArrayList;
import java.util.List;

class FeatureInfo {

  enum SupportedStatus { YES, PARTIAL, NO }

  private final List<TestInfo> tests;
  private final String id;
  private SupportedStatus status;

  FeatureInfo(String id) {
    this.id = id;
    tests = new ArrayList<>();
  }

  SupportedStatus getStatus() {
    if (status == null) {
      int passCount = 0;
      for (TestInfo ti : tests) if (ti.passed) passCount++;
      status = passCount == tests.size() ? SupportedStatus.YES :
          (passCount == 0 ? SupportedStatus.NO : SupportedStatus.PARTIAL);
    }
    return status;
  }

  void addTest(TestInfo ti) {
    assert ti.id.equals(id) : "Attempt to add test of wrong id to FeatureInfo, test id = " + ti.id +
        " FeatureInfo id " + id;
    tests.add(ti);
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder("Feature id: ")
        .append(id)
        .append("\nStatus: ")
        .append(getStatus().toString())
        .append("\n");
    for (TestInfo test : tests) buf.append(test.toString());
    return buf.toString();
  }
}
