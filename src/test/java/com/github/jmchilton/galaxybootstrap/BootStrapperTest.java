package com.github.jmchilton.galaxybootstrap;

import com.github.jmchilton.galaxybootstrap.BootStrapper.GalaxyDaemon;
import com.github.jmchilton.galaxybootstrap.GalaxyData.User;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Scanner;

import org.testng.annotations.Test;

public class BootStrapperTest {

  /**
   * Tests default BootStrapper is the latest stable.
   * @throws InterruptedException
   * @throws IOException
   */
  @Test(enabled=false)
  public void testSetup() throws InterruptedException, IOException {
    final BootStrapper bootStrapper = new BootStrapper();

    bootStrapper.setupGalaxy();
    
    // test to make sure we have checked out the latest commit of Galaxy
    String expectedLatestCommit = getTipGitCommitHash(bootStrapper.getPath());
    String actualCommit = getCurrentGitCommitHash(bootStrapper.getPath());
    assert expectedLatestCommit != null;
    assert expectedLatestCommit.equalsIgnoreCase(actualCommit);
    
    bootStrapper.deleteGalaxyRoot();
  }


  /**
   * Tests setup of Galaxy for the latest release.
   * @throws IOException
   * @throws InterruptedException
   */
  @Test(enabled=false)
  public void testSpecificRelease() throws InterruptedException, IOException {
    String release = "master";
    final BootStrapper bootStrapper = new BootStrapper(
            DownloadProperties.forRelease(release));

    testSetupGalaxyFor(bootStrapper);

    bootStrapper.deleteGalaxyRoot();
  }
  
  /**
   * Tests to make sure downloading Galaxy at a specific commit works
   * @throws IOException 
   * @throws InterruptedException 
   */
  @Test(enabled=false)
  public void testSpecificCommit() throws InterruptedException, IOException {
    // release_17.05 on 2017-10-19 at https://github.com/galaxyproject/galaxy
    final String expectedCommit = "e5e98cc2d46e6d71fc162831bb65046a33551dce";
    final BootStrapper bootStrapper = new BootStrapper(
      DownloadProperties.forLatestReleaseAtCommit(expectedCommit));
    
    testSetupGalaxyFor(bootStrapper);
    
    String actualCommit = getCurrentGitCommitHash(bootStrapper.getPath());

    assert expectedCommit.equalsIgnoreCase(actualCommit);
    
    bootStrapper.deleteGalaxyRoot();
  }
  
  /**
   * Tests to make sure downloading Galaxy from github master branch works.
   * @throws IOException 
   * @throws InterruptedException 
   */
  @Test(timeOut=3000000)
  public void testGithubMasterBranch() throws InterruptedException, IOException {
    final BootStrapper bootStrapper = new BootStrapper(DownloadProperties.gitGithubMaster());

    testSetupGalaxyFor(bootStrapper);

    bootStrapper.deleteGalaxyRoot();
  }

  /**
   * Tests Galaxy for a specific setup.
   * @param bootStrapper  The BootStrapper used for setting up Galaxy.
   * @throws InterruptedException
   * @throws IOException
   */
  private void testSetupGalaxyFor(BootStrapper bootStrapper) throws InterruptedException, IOException {
    bootStrapper.setupGalaxy();
    
    final GalaxyProperties galaxyProperties = 
      new GalaxyProperties()
            .assignFreePort()
            .configureNestedShedTools();
    final GalaxyData galaxyData = new GalaxyData();
    final User adminUser = new User("admin@localhost");
    final User normalUser = new User("user@localhost");
    galaxyData.getUsers().add(adminUser);
    galaxyData.getUsers().add(normalUser);
    galaxyProperties.setAdminUser("admin@localhost");
    galaxyProperties.setAppProperty("allow_library_path_paste", "true");
    galaxyProperties.prepopulateSqliteDatabase();
    final int port = galaxyProperties.getPort();
    assert IoUtils.available(port);
    final GalaxyDaemon daemon = bootStrapper.run(galaxyProperties, galaxyData);
    final File shedToolsFile = new File(bootStrapper.getRoot(), "shed_tool_conf.xml");    
    final String shedToolsContents = Files.toString(shedToolsFile, Charsets.UTF_8);
    final URL shedToolConfResource = getClass().getResource("shed_tool_conf.xml");
    final String expectedShedToolsContents = Resources.toString(shedToolConfResource, Charsets.UTF_8);
    assert shedToolsContents.equals(expectedShedToolsContents);
    assert new File(bootStrapper.getRoot(), "shed_tools").isDirectory();
    assert daemon.waitForUp();
    assert !IoUtils.available(port);
    daemon.stop();
    assert daemon.waitForDown();    
  }

  /**
   * Given the git root directory gets the current commit hash code checked out.
   * @param gitDir  The root git directory.
   * @return  The current commit hash checked out.
   */
  private String getCurrentGitBranchName(String gitDir) {
    String branchName = null;
    final String bashScript
            = "cd " + gitDir + "; git rev-parse --abbrev-ref HEAD";
    Process p = IoUtils.execute("bash", "-c", bashScript);

    branchName = convertStreamToString(p.getInputStream());
    branchName = branchName.replace("\n", "");

    return branchName;
  }

  /**
   * Given the git root directory gets the current commit hash code checked out.
   * @param gitDir  The root git directory.
   * @return  The current commit hash checked out.
   */
  private String getCurrentGitCommitHash(String gitDir) {
    String hash = null;
    final String bashScript
            = "cd " + gitDir + "; git rev-parse HEAD";
    Process p = IoUtils.execute("bash", "-c", bashScript);

    hash = convertStreamToString(p.getInputStream());
    hash = hash.replace("\n", "");

    return hash;
  }

  /**
   * Given the git root directory gets the tip commit hash code.
   * @param gitDir  The root git directory.
   * @return  The tip commit hash.
   */
  private String getTipGitCommitHash(String gitDir) {
    String hash = null;
    final String bashScript
            = "cd " + gitDir + "; git rev-parse $(git rev-parse --abbrev-ref HEAD)";
    Process p = IoUtils.execute("bash", "-c", bashScript);

    hash = convertStreamToString(p.getInputStream());
    hash = hash.replace("\n", "");

    return hash;
  }

  /**
   * Given the mercurial root directory gets the current revision hash code checked out.
   * @param mercurialDir  The root mercurial directory.
   * @return  The current revision hash checked out.
   */
  @Deprecated
  private String getCurrentMercurialRevisionHash(String mercurialDir) {
    String hash = null;
    final String bashScript
            = "cd " + mercurialDir + "; hg parent --template '{node}'";
    Process p = IoUtils.execute("bash", "-c", bashScript);

    hash = convertStreamToString(p.getInputStream());

    return hash;
  }
  @Deprecated
  /**
   * Given the mercurial root directory gets the tip revision hash code.
   * @param mercurialDir  The root mercurial directory.
   * @return  The tip revision hash.
   */
  private String getTipMercurialRevisionHash(String mercurialDir) {
    String hash = null;
    final String bashScript
            = "cd " + mercurialDir + "; hg tip --template '{node}'";
    Process p = IoUtils.execute("bash", "-c", bashScript);

    hash = convertStreamToString(p.getInputStream());

    return hash;
  }

  /**
   * Given an input stream, converts to a String containing all data.
   * @param is  The input stream to read from.
   * @return  A String containing all data from the input stream.
   */
  private String convertStreamToString(InputStream is) {
    Scanner s = new Scanner(is);
    Scanner d = s.useDelimiter("\\A");
    String string = d.hasNext() ? d.next() : "";
    s.close();
    
    return string;
  }
}
