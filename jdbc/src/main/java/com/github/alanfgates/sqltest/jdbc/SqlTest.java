package com.github.alanfgates.sqltest.jdbc;

import org.apache.commons.cli.*;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class SqlTest {
  private String jdbcUrl;
  private String port;
  private String systemUnderTest;
  private String systemVersion;
  private String testHome;
  private String specVersion;
  private String jdbcDriverClass;
  private String jdbcUser;
  private String jdbcPassword;
  private int waitForServer = 5;
  private int waitForImage = 600;
  private Logger log;

  @SuppressWarnings("static-access")
  private int parseArgs(String[] args) {
    CommandLineParser parser = new GnuParser();

    Options opts = new Options();

    opts.addOption(OptionBuilder
        .withLongOpt("wait-for-image")
        .withDescription("Time to wait in seconds for the docker image to build.  Default is 600 sec.")
        .hasArg()
        .create());

    opts.addOption(OptionBuilder
        .withLongOpt("jdbc-driver-class")
        .withDescription("Class of the JDBC driver")
        .hasArg()
        .isRequired()
        .create('c'));

    opts.addOption(OptionBuilder
        .withLongOpt("sqltest-home")
        .withDescription("Directory where the sqltest source is.  Defaults to .")
        .hasArg()
        .create('h'));

    opts.addOption(OptionBuilder
        .withLongOpt("jdbc-url")
        .withDescription("URL to access the JDBC end point")
        .hasArg()
        .isRequired()
        .create('j'));

    opts.addOption(OptionBuilder
        .withLongOpt("jdbc-password")
        .withDescription("Password to login to the database with, can be included in the URL instead." +
            " If passed here jdbc-user|u must be provided as well")
        .hasArg()
        .create('P'));

    opts.addOption(OptionBuilder
        .withLongOpt("port")
        .withDescription("Port to map. Can give docker mapping (x:y) or single value which will be mapped into docker as x:x")
        .hasArg()
        .isRequired()
        .create('p'));

    opts.addOption(OptionBuilder
        .withLongOpt("spec-version")
        .withDescription("Spec version to test, defaults to 2016 (also only supported option atm)")
        .hasArg()
        .create('s'));

    opts.addOption(OptionBuilder
        .withLongOpt("system-under-test")
        .withDescription("Name of the system we are testing")
        .hasArg()
        .isRequired()
        .create('t'));

    opts.addOption(OptionBuilder
        .withLongOpt("jdbc-user")
        .withDescription("User to login to the database with, can be included in the URL instead." +
            " If passed here jdbc-password|P must be provided as well")
        .hasArg()
        .create('u'));

    opts.addOption(OptionBuilder
        .withLongOpt("wait-for-server")
        .withDescription("Time to wait in seconds after starting the docker container before beginning the test.  Default is 5 sec.")
        .hasArg()
        .create('w'));

    opts.addOption(OptionBuilder
        .withLongOpt("system-version")
        .withDescription("Version of the system we are testing, must match version number in source code")
        .hasArg()
        .isRequired()
        .create('v'));

    opts.addOption(OptionBuilder
        .withLongOpt("verbose")
        .withDescription("Turn on lots of debug messaging")
        .create('V'));

    CommandLine cmd;
    try {
      cmd = parser.parse(opts, args);
    } catch (ParseException pe) {
      System.err.println("Failed to parse arguments: " + pe.getMessage());
      usage(opts);
      return -1;
    }

    testHome = ".";
    if (cmd.hasOption('h')) testHome = cmd.getOptionValue('h');
    specVersion = "2016";
    if (cmd.hasOption('s')) specVersion = cmd.getOptionValue('s');
    if (cmd.hasOption('w')) waitForServer = Integer.valueOf(cmd.getOptionValue('w'));
    if (cmd.hasOption("wait-for-image")) waitForImage = Integer.valueOf(cmd.getOptionValue("wait-for-image"));
    if (cmd.hasOption('u')) jdbcUser = cmd.getOptionValue('u');
    if (cmd.hasOption('P')) jdbcPassword = cmd.getOptionValue('P');
    log = new Logger(cmd.hasOption('V'));

    port = cmd.getOptionValue('p');
    if (!port.contains(":")) port = port + ":" + port;
    jdbcUrl = cmd.getOptionValue('j');
    jdbcDriverClass = cmd.getOptionValue('c');
    systemUnderTest = cmd.getOptionValue('t');
    systemVersion = cmd.getOptionValue('v');
    return 0;
  }

  private void usage(Options opts) {
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp("docker-test", opts);
  }

  private void test() throws IOException, InterruptedException, SQLException, ClassNotFoundException {
    SortedMap<String, FeatureInfo> features = new TreeMap<>();

    // Read the tests
    String testDir = testHome + File.separator + "standards" + File.separator + specVersion;
    TestReader testReader = new TestReader(log, testDir);
    List<TestInfo> tests = testReader.readTests();

    // Set up docker
    try (DockerHelper docker = new DockerHelper(log)) {
      String dockerFileDir = testHome + File.separator + "dbs" + File.separator + systemUnderTest +
          File.separator + "v" + systemVersion.replace('.', '_');
      docker.buildImage(dockerFileDir, systemUnderTest, waitForImage);
      docker.runImage(port, 30);

      log.log("Waiting for server to start...");
      Thread.sleep(waitForServer * 1000);

      // Get a JDBC connection
      JdbcHelper jdbc = new JdbcHelper(log);
      try (Connection conn = jdbc.connect(jdbcDriverClass, jdbcUrl, jdbcUser, jdbcPassword)) {


        // Beginning our attack run
        String running = null;
        for (TestInfo test : tests) {
          log.log("Running test " + test.id + " as part of feature " + test.feature);
          try (Statement stmt = conn.createStatement()) {
            List<String> sqls = test.getSql();
            for (String sql : sqls) {
              log.debug("Going to execute <" + sql + ">");
              running = sql;
              if (stmt.execute(sql)) {
                // Just make sure something was returned
                ResultSet rs = stmt.getResultSet();
                rs.next();
              }
            }
            test.passed = true;
            log.log("Test " + test.id + " passed");
          } catch (SQLException e) {
            log.log("While running " + running + " caught SQLException state: " + e.getSQLState() + " error code: "
                + e.getErrorCode(), e);
            test.passed = false;
            log.log("Test " + test.id + " failed");
          }
          FeatureInfo feature = features.computeIfAbsent(test.feature, s -> new FeatureInfo(test.feature));
          feature.addTest(test);
        }
      }
    }

    // Report
    for (Map.Entry<String, FeatureInfo> e : features.entrySet()) {
      System.out.println("Feature id: " + e.getKey() + " Status: " + e.getValue().getStatus().toString());
    }
  }

  public static void main(String[] args) {
    SqlTest m = new SqlTest();
    try {
      if (m.parseArgs(args) == 0) m.test();
    } catch (Exception e) {
      System.err.println("Testing failed: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
