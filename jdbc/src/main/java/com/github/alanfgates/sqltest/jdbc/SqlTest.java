package com.github.alanfgates.sqltest.jdbc;

import org.apache.commons.cli.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
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
  private String reportFile;
  private int waitForServer = 5;
  private int waitForImage = 600;
  private Logger log;

  private int parseArgs(String[] args) {
    CommandLineParser parser = new GnuParser();

    Options opts = new Options();

    opts.addOption(Option.builder()
        .longOpt("wait-for-image")
        .desc("Time to wait in seconds for the docker image to build.  Default is 600 sec.")
        .hasArg()
        .build());

    opts.addOption(Option.builder("c")
        .longOpt("jdbc-driver-class")
        .desc("Class of the JDBC driver")
        .hasArg()
        .required()
        .build());

    opts.addOption(Option.builder("h")
        .longOpt("sqltest-home")
        .desc("Directory where the sqltest source is.  Defaults to .")
        .hasArg()
        .build());

    opts.addOption(Option.builder("j")
        .longOpt("jdbc-url")
        .desc("URL to access the JDBC end point")
        .hasArg()
        .required()
        .build());

    // This is actually ignored, just put here so that it shows up in the usage statement.  The value will be used by
    // start script to add these jars to our classpath
    opts.addOption(Option.builder("J")
        .longOpt("jars")
        .desc("Jars to include in class path for JDBC driver.  Multiple can be included, should be separatored using "
            + File.pathSeparator)
        .hasArgs()
        .valueSeparator(File.pathSeparatorChar)
        .required()
        .build());

    opts.addOption(Option.builder("P")
        .longOpt("jdbc-password")
        .desc("Password to login to the database with, can be included in the URL instead." +
            " If passed here jdbc-user|u must be provided as well")
        .hasArg()
        .build());

    opts.addOption(Option.builder("p")
        .longOpt("port")
        .desc("Port to map. Can give docker mapping (x:y) or single value which will be mapped into docker as x:x")
        .hasArg()
        .required()
        .build());

    opts.addOption(Option.builder("r")
        .longOpt("report-file")
        .desc("File to write report into, defaults to sqltest-<system-under-test>-<system-version>-report")
        .hasArg()
        .build());

    opts.addOption(Option.builder("s")
        .longOpt("spec-version")
        .desc("Spec version to test, defaults to 2016 (also only supported option atm)")
        .hasArg()
        .build());

    opts.addOption(Option.builder("t")
        .longOpt("system-under-test")
        .desc("Name of the system we are testing")
        .hasArg()
        .required()
        .build());

    opts.addOption(Option.builder("u")
        .longOpt("jdbc-user")
        .desc("User to login to the database with, can be included in the URL instead." +
            " If passed here jdbc-password|P must be provided as well")
        .hasArg()
        .build());

    opts.addOption(Option.builder("w")
        .longOpt("wait-for-server")
        .desc("Time to wait in seconds after starting the docker container before beginning the test.  Default is 5 sec.")
        .hasArg()
        .build());

    opts.addOption(Option.builder("v")
        .longOpt("system-version")
        .desc("Version of the system we are testing, must match version number in source code")
        .hasArg()
        .required()
        .build());

    opts.addOption(Option.builder("V")
        .longOpt("verbose")
        .desc("Turn on lots of debug messaging")
        .build());

    CommandLine cmd;
    try {
      cmd = parser.parse(opts, args);
    } catch (ParseException pe) {
      System.err.println("Failed to parse arguments: " + pe.getMessage());
      usage(opts);
      return -1;
    }

    // Required args
    port = cmd.getOptionValue('p');
    if (!port.contains(":")) port = port + ":" + port;
    jdbcUrl = cmd.getOptionValue('j');
    jdbcDriverClass = cmd.getOptionValue('c');
    systemUnderTest = cmd.getOptionValue('t');
    systemVersion = cmd.getOptionValue('v');

    // Optional args
    testHome = ".";
    if (cmd.hasOption('h')) testHome = cmd.getOptionValue('h');
    specVersion = "2016";
    if (cmd.hasOption('s')) specVersion = cmd.getOptionValue('s');
    if (cmd.hasOption('w')) waitForServer = Integer.valueOf(cmd.getOptionValue('w'));
    if (cmd.hasOption("wait-for-image")) waitForImage = Integer.valueOf(cmd.getOptionValue("wait-for-image"));
    if (cmd.hasOption('u')) jdbcUser = cmd.getOptionValue('u');
    if (cmd.hasOption('P')) jdbcPassword = cmd.getOptionValue('P');
    log = new Logger(cmd.hasOption('V'));
    if (cmd.hasOption('r')) reportFile = cmd.getOptionValue('r');
    else reportFile = "sqltest-" + systemUnderTest + "-" + systemVersion + "-report";

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
      docker.buildImage(dockerFileDir, systemUnderTest, systemVersion, waitForImage);
      docker.runImage(port, 30);

      log.log("Waiting for server to start...");
      Thread.sleep(waitForServer * 1000);

      // Beginning our attack run
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
            test.exception = e;
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
    PrintWriter reportWriter = new PrintWriter(reportFile);
    int yes = 0, partial = 0, no = 0;
    for (FeatureInfo feature : features.values()) {
      reportWriter.println(feature.toString());
      reportWriter.println("--------------------------------");
      FeatureInfo.SupportedStatus status = feature.getStatus();
      if (status == FeatureInfo.SupportedStatus.YES) yes++;
      else if (status == FeatureInfo.SupportedStatus.NO) no++;
      else if (status == FeatureInfo.SupportedStatus.PARTIAL) partial++;
      else throw new RuntimeException("huh?");
    }
    reportWriter.println();
    reportWriter.println("--------------------------------");
    reportWriter.println("Summary:");
    reportWriter.println("Yes: " + yes);
    reportWriter.println("Partial: " + partial);
    reportWriter.println("No: " + no);
    reportWriter.close();
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
