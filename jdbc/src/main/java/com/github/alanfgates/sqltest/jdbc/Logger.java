package com.github.alanfgates.sqltest.jdbc;

class Logger {

  private final boolean verbose;

  Logger(boolean verbose) {
    this.verbose = verbose;
  }

  void log(String msg) {
    log(msg, null, false);
  }

  void log(String msg, Exception e) {
    log(msg, e, false);
  }

  void debug(String msg) {
    log(msg, null, true);
  }

  void debug(String msg, Exception e) {
    log(msg, e, true);
  }

  private void log(String msg, Exception e, boolean isDebug) {
    if (!isDebug || verbose) {
      System.out.print(msg);
      if (e != null) System.out.print(" " + e.getMessage());
      System.out.println();
    }
  }

}
