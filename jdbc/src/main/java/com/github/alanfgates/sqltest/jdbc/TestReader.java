package com.github.alanfgates.sqltest.jdbc;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class TestReader {

  private final Logger log;
  private final File basedir;

  TestReader(Logger log, String basedirName) {
    this.log = log;
    this.basedir = new File(basedirName);
  }

  List<TestInfo> readTests() throws IOException {
    List<File> testFiles = findTestFiles();
    List<TestInfo> tests = new ArrayList<>();

    for (File testFile : testFiles) {
      log.debug("Going to process test file: " + testFile.getAbsolutePath());
      ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
      ObjectReader reader = mapper.readerFor(TestInfo.class);
      MappingIterator<TestInfo> iter = reader.readValues(testFile);
      tests.addAll(iter.readAll());
    }
    return tests;
  }

  private List<File> findTestFiles() throws IOException {
    if (!basedir.exists()) {
      throw new IOException("No such file or directory: " + basedir.getAbsolutePath());
    }
    if (!basedir.isDirectory()) {
      throw new IOException("Was expecting " + basedir.getAbsolutePath() + " to be a directory");
    }

    List<File> testFiles = new ArrayList<>();
    findTestFiles(basedir, testFiles);
    return testFiles;
  }

  private void findTestFiles(File current, List<File> results) {
    log.debug("Looking for tests in " + current.getAbsolutePath());
    if (current.isDirectory()) {
      for (File f : current.listFiles()) {
        findTestFiles(f, results);
      }
    } else {
      if (current.getName().endsWith(".tests.yml")) {
        log.debug("Adding test file " + current.getAbsolutePath() + " to list of test files");
        results.add(current);
      } else {
        log.debug("Ignoring " + current.getAbsolutePath());
      }
    }


  }
}
