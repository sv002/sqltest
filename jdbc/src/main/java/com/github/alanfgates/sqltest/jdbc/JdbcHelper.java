package com.github.alanfgates.sqltest.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

class JdbcHelper {

  private final Logger log;
  private Set<String> loadedDrivers;

  JdbcHelper(Logger log) {
    this.log = log;
    loadedDrivers = new HashSet<>();
  }

  /**
   * Get a connection
   * @param driverClass class of the JDBC driver
   * @param url URL of the JDBC end point
   * @param user can be null if user is included in URL, if this is null password should be too
   * @param password can be null if user is included in URL, if this is null user should be too
   * @return connection
   * @throws ClassNotFoundException if the JDBC driver could not be found
   * @throws SQLException if the connection attempt fails
   */
  Connection connect(String driverClass, String url, String user, String password)
      throws ClassNotFoundException, SQLException {
    if (loadedDrivers.add(driverClass)) {
      Class.forName(driverClass);
    }

    assert (user == null && password == null) || (user != null && password != null) :
        "User and password must both be in the URL or both be passed separately.";

    log.debug("Going to connect using url <" + url + "> with user " + user);
    return user == null ? DriverManager.getConnection(url) : DriverManager.getConnection(url, user, password);

  }

}
