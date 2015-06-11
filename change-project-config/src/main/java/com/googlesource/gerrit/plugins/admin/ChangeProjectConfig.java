package com.googlesource.gerrit.plugins.admin;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ChangeProjectConfig {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ChangeProjectConfig.class);
    private static String localPath, remotePath, projectConfig;
    private static  int counter = 0;

    public static void main(String[] args) throws InterruptedException {

        Lock lock = new ReentrantLock();
        Condition cond = lock.newCondition();

        localPath = System.getenv("GERRIT_GIT_LOCALPATH");
        remotePath = System.getenv("GERRIT_GIT_REMOTEPATH");
        projectConfig = System.getenv("GERRIT_GIT_PROJECT_CONFIG");

        String myLocalPath = (localPath == null) ? "/home/gerrit/git" : localPath;
        String myRemotePath = (remotePath == null) ? "ssh://admin@localhost:29418/All-Projects" : remotePath;
        String myProjectConfig = (projectConfig == null) ? "/home/gerrit/config" : projectConfig;
        PushCommit gitPushCommit = new PushCommit(myLocalPath, myRemotePath, myProjectConfig);

        lock.lock();
        while(! sshdAvailable(29418))
            cond.await(5, TimeUnit.SECONDS);
        
        gitPushCommit.init();
        lock.unlock();
    }

    private static boolean sshdAvailable(int port) {
        JSch jsch = new JSch();
        try {
            counter++;
            logger.info(counter + " attempts.");

            logger.info("Establishing Connection...");
            Session s = jsch.getSession("admin", "localhost", port);
            s.setConfig("StrictHostKeyChecking", "no");
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
