package com.googlesource.gerrit.plugins.admin;

import static org.junit.Assert.*;

import com.jcraft.jsch.Session;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.*;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class JGitClient {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(JGitClient.class);

    private String localPath, remotePath, projectConfig;
    private Git git;
    private Repository repo;
    /*
    private Repository localRepo;
    private SshSessionFactory sshSessionFactory;
    private TransportConfigCallback tccb;
    */

    private final static String COMMIT_MESSAGE = "Change project permissions to allow access for Non-Interactive Users";

    JGitClient() {
        this.localPath = "/Users/chmoulli/Temp/createuserplugin/target/mytest";
        this.remotePath = "ssh://admin@localhost:29418/All-Projects";
        this.projectConfig = "/Users/chmoulli/Temp/createuserplugin/config/project.config";
    }

    public static void main(String[] args) {
        JGitClient cl = new JGitClient();
        try {

            // Init Git Gerrit AllProjects repository & fetch the ref/meta/config branch
            cl.initGitAndRepo();
            cl.fetch();
            cl.checkout();

            // Replace the old with the new file
            File newFile = new File(cl.projectConfig);
            File oldFile = new File(cl.localPath + "/project.config");
            FileUtils.copyFile(newFile, oldFile);

            StoredConfig config = cl.repo.getConfig();
            config.setString("branch", "meta/config", "remote", "origin");
            config.setString("branch", "meta/config", "merge", "refs/heads/meta/config");
            config.save();

            // Check Diff
            // cl.checkDiff();

            // Commit the modification
            cl.commitChange(COMMIT_MESSAGE);

            // cl.checkStatus();

            // Push the modification
            cl.pushChange();

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InvalidRemoteException e) {
            e.printStackTrace();
        } catch (TransportException e) {
            e.printStackTrace();
        } catch (GitAPIException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void initGitAndRepo() throws Exception {

        File gitworkDir = new File(localPath);
        FileUtils.deleteDirectory(gitworkDir);

        File gitDir = new File(gitworkDir, ".git");

        // Create a Git project
        InitCommand initCommand = Git.init();
        initCommand.setDirectory(gitworkDir);
        initCommand.setBare(false);
        git = initCommand.call();

        assertTrue(gitDir.exists());
        assertNotNull(git);

        // Save remote origin, user & email
        repo = git.getRepository();
        StoredConfig config = repo.getConfig();
        config.setString("user", null, "name", "Administrator");
        config.setString("user", null, "email", "admin@example.com");
        config.setString("remote", "origin", "url", remotePath);
        config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
        config.save();
    }

    public void fetch() throws GitAPIException {

        final SshSessionFactory sshSessionFactory = new JschConfigSessionFactory() {
            @Override
            protected void configure(OpenSshConfig.Host host, Session session) {
                session.setConfig("StrictHostKeyChecking", "no");
            }
        };

        TransportConfigCallback tccb = new TransportConfigCallback() {
            @Override
            public void configure(Transport transport) {
                SshTransport sshTransport = (SshTransport) transport;
                sshTransport.setSshSessionFactory(sshSessionFactory);
            }
        };

        RefSpec refspec = new RefSpec("refs/meta/config:refs/remotes/origin/meta/config");
        git.fetch()
           .setTransportConfigCallback(tccb)
           .setRemote("origin")
           .setRefSpecs(refspec)
           .call();
    }

    public void checkout() throws GitAPIException {
        git.checkout()
           .setName("meta/config")
           .setCreateBranch(true)
           .setStartPoint("origin/meta/config")
           .call();
    }

    public void checkDiff() throws GitAPIException, IOException {
        List<DiffEntry> diffEntries = git.diff().call();
        for(DiffEntry diffEntry : diffEntries) {
            logger.info("New Id : " + diffEntry.getNewId().name());
            logger.info("Old Id : " + diffEntry.getOldId().name());
/*            DiffFormatter formatter = new DiffFormatter(System.out);
            formatter.setRepository(repo);
            formatter.format(diffEntry);*/
        }
    }

    public void checkStatus() throws GitAPIException {
        Status status = git.status().call();
        Set<String> modified = status.getModified();
        for(String modif : modified) {
            logger.info("Modification : " + modif);
        }
    }

    public void commitChange(String message) throws GitAPIException {
        RevCommit revCommit = git.commit().setMessage(message).setAll(true).call();
        logger.info("Commit Message : " + revCommit.getFullMessage());
    }

    public void pushChange() {

        final SshSessionFactory sshSessionFactory = new JschConfigSessionFactory() {
            @Override
            protected void configure(OpenSshConfig.Host host, Session session) {
                session.setConfig("StrictHostKeyChecking", "no");
            }
        };

        TransportConfigCallback tccb = new TransportConfigCallback() {
            @Override
            public void configure(Transport transport) {
                SshTransport sshTransport = (SshTransport) transport;
                sshTransport.setSshSessionFactory(sshSessionFactory);
            }
        };

        RefSpec refSpec = new RefSpec("meta/config:meta/config");
        try {
            Iterable<PushResult> results = git.push().setRemote("origin")
                                                      .setTransportConfigCallback(tccb)
                                                      .setRefSpecs(refSpec)
                                                      .setOutputStream(System.out)
                                                      .call();
            for(PushResult result : results) {
                Collection<RemoteRefUpdate> updates = result.getRemoteUpdates();
                for(RemoteRefUpdate update : updates) {
                    logger.info("Response from remote : " +update.getRemoteName() + " & status : " + update.getStatus());
                }
            }
        } catch (GitAPIException e) {
            e.printStackTrace();
        }
    }
}
