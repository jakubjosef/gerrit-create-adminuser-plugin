package com.googlesource.gerrit.plugins.admin;


import com.jcraft.jsch.Session;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.*;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class PushCommit {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ChangeProjectConfig.class);
    private final static String COMMIT_MESSAGE = "Change project permissions to allow access for NonInteractive Users";

    private String localPath, remotePath, projectConfig;
    private Git git;
    private Repository repo;

    PushCommit(String localPath, String remotePath, String projectConfig) {
        this.localPath = localPath;
        this.remotePath = remotePath;
        this.projectConfig = projectConfig;
    }

    public void init() {

        try {
            // Init Git Gerrit AllProjects repository & fetch the ref/meta/config branch
            initGitAndRepo();
            fetch();
            checkout();

            // Replace the old with the new file
            File newFile = new File(projectConfig);
            File oldFile = new File(localPath + "/project.config");
            FileUtils.copyFile(newFile, oldFile);

            StoredConfig config = repo.getConfig();
            config.setString("branch", "meta/config", "remote", "origin");
            config.setString("branch", "meta/config", "merge", "refs/heads/meta/config");
            config.save();

            // Check Diff
            // checkDiff();

            // Commit the modification
            commitChange(COMMIT_MESSAGE);

            // Push the modification
            pushChange();

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

        // Save remote origin, user & email
        repo = git.getRepository();
        StoredConfig config = repo.getConfig();
        config.setString("core", null, "bare", "false");
        config.setString("core", null, "ignorecase", "true");

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
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider("admin", ""))
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
        for (DiffEntry diffEntry : diffEntries) {
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
        for (String modif : modified) {
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

        RefSpec refSpec = new RefSpec("meta/config:refs/meta/config");
        try {
            Iterable<PushResult> results = git.push().setRemote("origin")
                    .setTransportConfigCallback(tccb)
                    .setRefSpecs(refSpec)
                    .setProgressMonitor(new TextProgressMonitor())
                    .call();
            for (PushResult result : results) {
                Collection<RemoteRefUpdate> updates = result.getRemoteUpdates();
                for (RemoteRefUpdate update : updates) {
                    RemoteRefUpdate.Status status = update.getStatus();
                    logger.info("Response from remote : " + update.getRemoteName() + " & status : " + status);
                }
            }
        } catch (GitAPIException e) {
            e.printStackTrace();
        } catch (JGitInternalException e) {
            throw new IllegalStateException("Unable to push into remote Git repository", e);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
