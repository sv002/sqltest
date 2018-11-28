package com.github.alanfgates.sqltest.jdbc;

import java.util.ArrayList;
import java.util.List;

class FeatureInfo {

  enum SupportedStatus { YES, PARTIAL, NO }

  private final List<TestInfo> tests;
  private final String id;

  FeatureInfo(String id) {
    this.id = id;
    tests = new ArrayList<>();
  }

  SupportedStatus getStatus() {
    int passCount = 0;
    for (TestInfo ti : tests) if (ti.passed) passCount++;
    return passCount == tests.size() ? SupportedStatus.YES :
        (passCount == 0 ? SupportedStatus.NO : SupportedStatus.PARTIAL);
  }

  void addTest(TestInfo ti) {
    assert ti.id == id : "Attempt to add test of wrong id to FeatureInfo, test id = " + ti.id +
        " FeatureInfo id " + id;
    tests.add(ti);
  }

}
