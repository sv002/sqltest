package com.github.alanfgates.sqltest.jdbc;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Random;
import java.util.concurrent.TimeUnit;

class DockerHelper implements Closeable {

  private final Logger log;
  private String imageName;
  private String containerName;
  private boolean started;

  DockerHelper(Logger log) {
    this.log = log;
    started = false;
  }

  @Override
  public void close() throws IOException {
    if (started) stopContainer();
    started = false;
  }

  private static class ProcessResults {
    final String stdout;
    final String stderr;
    final int rc;

    ProcessResults(String stdout, String stderr, int rc) {
      this.stdout = stdout;
      this.stderr = stderr;
      this.rc = rc;
    }
  }


  void buildImage(String dockerFileDir, String systemUnderTest, String systemVersion, long secondsToWait)
      throws IOException, InterruptedException {
    buildImageName(systemUnderTest, systemVersion);
    if (runCmdAndPrintStreams(new String[] {"docker", "build", "--tag", imageName, dockerFileDir}, secondsToWait) != 0) {
      throw new IOException("Failed to build docker image");
    }
  }

  void runImage(String portMapping, long secondsToWait)
      throws IOException, InterruptedException {
    buildContainerName();
    if (runCmdAndPrintStreams(new String[] {"docker", "run", "--name", containerName, "-p", portMapping, "-d",
        imageName}, secondsToWait) != 0) {
      throw new IOException("Failed to run docker image");
    }
    started = true;
  }

  private void stopContainer() throws IOException {
    try {
      if (runCmdAndPrintStreams(new String[]{"docker", "stop", containerName}, 30) != 0) {
        throw new IOException("Failed to stop docker container");
      }
    } catch (InterruptedException e) {
      throw new IOException(e);
    }
  }
  private void buildImageName(String systemUnderTest, String systemVersion) {
    if (imageName == null) {
      imageName = "sqltest-" + systemUnderTest + "-" + systemVersion + "-" + System.getProperty("user.name");
    }
  }

  private void buildContainerName() {
    if (containerName == null) {
      assert imageName != null : "You should have already built the image!";
      containerName = imageName + "-" + new Random().nextInt(Integer.MAX_VALUE);
    }
  }

  private int runCmdAndPrintStreams(String[] cmd, long secondsToWait) throws InterruptedException, IOException {
    ProcessResults results = runCmd(cmd, secondsToWait);
    log.debug("Stdout from proc: " + results.stdout);
    log.debug("Stderr from proc: " + results.stderr);
    return results.rc;
  }

  private ProcessResults runCmd(String[] cmd, long secondsToWait) throws IOException, InterruptedException {
    log.debug("Going to run: " + StringUtils.join(cmd, " "));
    Process proc = Runtime.getRuntime().exec(cmd);
    if (!proc.waitFor(secondsToWait, TimeUnit.SECONDS)) {
      throw new RuntimeException("Process " + cmd[0] + " failed to run in " + secondsToWait +
          " seconds");
    }
    BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
    final StringBuilder lines = new StringBuilder();
    reader.lines()
        .forEach(s -> lines.append(s).append('\n'));

    reader = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
    final StringBuilder errLines = new StringBuilder();
    reader.lines()
        .forEach(s -> errLines.append(s).append('\n'));
    return new ProcessResults(lines.toString(), errLines.toString(), proc.exitValue());
  }

}
