package com.googlesource.gerrit.plugins.admin;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;

public class ChangeProjectConfig {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ChangeProjectConfig.class);
    private static String localPath, remotePath, projectConfig;

    public static void main(String[] args) throws InterruptedException {

        localPath = System.getenv("GERRIT_GIT_LOCALPATH");
        remotePath = System.getenv("GERRIT_GIT_REMOTEPATH");
        projectConfig = System.getenv("GERRIT_GIT_PROJECT_CONFIG");

        String myLocalPath = (localPath == null) ? "/home/gerrit/git" : localPath;
        String myRemotePath = (remotePath == null) ? "ssh://admin@localhost:29418/All-Projects" : remotePath;
        String myProjectConfig = (projectConfig == null) ? "/home/gerrit/config" : projectConfig;
        PushCommit gitPushCommit = new PushCommit(myLocalPath, myRemotePath, myProjectConfig);

        for (int i = 1; i < 10; i++) {
            logger.info(i + " attempts.");
            if (sshdAvailable(29418)) {
                gitPushCommit.init();
                break;
            }
            Thread.sleep(5000);
        }
    }

    private static boolean sshdAvailable(int port) {
        JSch jsch = new JSch();
        try {
            Session s = jsch.getSession("admin", "localhost", port);
            s.setConfig("StrictHostKeyChecking", "no");
            logger.info("Establishing Connection...");
            s.connect();
            logger.info("Connection established.");
            s.disconnect();
            return true;
        } catch (JSchException jschEx) {
            if (jschEx.getMessage().contains("Auth fail")) {
                logger.info("Connection established with auth failed.");
                return true;
            } else {
                logger.info("Connection refused.");
                return false;
            }
        }
    }
}
