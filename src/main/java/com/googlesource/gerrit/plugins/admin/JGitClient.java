package com.googlesource.gerrit.plugins.admin;

import static org.junit.Assert.*;

import com.jcraft.jsch.Session;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.*;

import java.io.File;
import java.io.IOException;

public class JGitClient {

    private String localPath, remotePath;
    private Repository localRepo;
    private Git git;
    private SshSessionFactory sshSessionFactory;
    private TransportConfigCallback tccb;

    JGitClient() {}

    public static void main(String[] args) {
        JGitClient cl = new JGitClient();
        try {
            cl.init();
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

    public void init() throws Exception {

        localPath = "/Users/chmoulli/Temp/createuserplugin/target/mytest";
        File gitworkDir = new File(localPath);
        FileUtils.deleteDirectory(gitworkDir);

        File gitDir = new File(gitworkDir, ".git");

        // remotePath = "http://admin:secret@localhost:8080/All-Projects";
        remotePath = "ssh://admin@localhost:29418/All-Projects";

        // Create a git init project
        InitCommand initCommand = Git.init();
        initCommand.setDirectory(gitworkDir);
        initCommand.setBare(false);
        Git git = initCommand.call();

        assertTrue(gitDir.exists());
        assertNotNull(git);

        // Save origin, user & email
        Repository repo = git.getRepository();
        StoredConfig config = repo.getConfig();
        config.setString("user",null,"name","Administrator");
        config.setString("user", null, "email", "admin@example.com");
        config.setString("remote", "origin", "url", remotePath);
        config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
        config.save();

        // Fetch ref
        RefSpec refspec = new RefSpec("refs/meta/config:refs/remotes/origin/meta/config");
        git.fetch().setRemote("origin").setRefSpecs(refspec).call();

        // Checkout
        git.checkout().setName("meta/config")
                .setCreateBranch(true)
                .setStartPoint("origin/meta/config")
                .call();

        final SshSessionFactory sshSessionFactory = new JschConfigSessionFactory() {
            @Override
            protected void configure( OpenSshConfig.Host host, Session session ) {
                session.setConfig("StrictHostKeyChecking", "no");
            }
        };

        TransportConfigCallback tccb = new TransportConfigCallback() {
            @Override
            public void configure( Transport transport ) {
                SshTransport sshTransport = ( SshTransport )transport;
                sshTransport.setSshSessionFactory( sshSessionFactory );
            }
        };
    }
}
