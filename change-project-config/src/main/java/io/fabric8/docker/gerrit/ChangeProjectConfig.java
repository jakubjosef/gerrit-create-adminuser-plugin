package io.fabric8.docker.gerrit;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.eclipse.jgit.util.StringUtils;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ChangeProjectConfig {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ChangeProjectConfig.class);
    
    // default env variables
    public static final String DEFAULT_GERRIT_GIT_LOCALPATH = "/home/gerrit/git";
    public static final String DEFAULT_GERRIT_GIT_REMOTEPATH = "ssh://admin@localhost:29418/All-Projects";
    private static final String DEFAULT_GERRIT_GIT_PROJECT_CONFIG = "/home/gerrit/configs/project.config";

    private static String localPath, remotePath, projectConfig, adminPassword,
            adminPrivateKeyPath, adminPrivateKeyPassword;
    private static  int counter = 0;

    public static void main(String[] args) throws InterruptedException, JSchException {


        if (haveAdmin(System.getenv("GERRIT_ADMIN_FULLNAME"))) {

            Lock lock = new ReentrantLock();
            Condition cond = lock.newCondition();

            localPath = lookupFromEnvironmentVariables("GERRIT_GIT_LOCALPATH", DEFAULT_GERRIT_GIT_LOCALPATH);
            remotePath = lookupFromEnvironmentVariables("GERRIT_GIT_REMOTEPATH", DEFAULT_GERRIT_GIT_REMOTEPATH);
            projectConfig = lookupFromEnvironmentVariables("GERRIT_GIT_PROJECT_CONFIG", DEFAULT_GERRIT_GIT_PROJECT_CONFIG);
            adminPrivateKeyPath = System.getenv("GERRIT_ADMIN_PRIVATE_KEY");
            adminPrivateKeyPassword = System.getenv("GERRIT_ADMIN_PRIVATE_KEY_PASSWORD");


            String myLocalPath = (localPath == null) ? "/home/gerrit/git" : localPath;
            String myRemotePath = (remotePath == null) ? "ssh://admin@localhost:29418/All-Projects" : remotePath;
            String myProjectConfig = (projectConfig == null) ? "/home/gerrit/config" : projectConfig;

            logger.info("Using these settings from the environment: localPath='{}', remotePath='{}', projectConfig='{}', " +
                    "adminPrivateKeyPath='{}'", myLocalPath, myRemotePath, myProjectConfig, adminPrivateKeyPath);


            PushCommit gitPushCommit = new PushCommit(myLocalPath, myRemotePath, myProjectConfig);

            lock.lock();
            while (!sshdAvailable(29418))
                cond.await(5, TimeUnit.SECONDS);

            gitPushCommit.init();
            lock.unlock();
        }
    }

    private static String lookupFromEnvironmentVariables(String env, String defaultValue) {
        String rc = System.getenv(env);
        if (rc == null || rc.isEmpty()) {
            return defaultValue;
        }
        return rc;
    }

    private static boolean haveAdmin(String adminFullname) {
        return (adminFullname != null) && !adminFullname.isEmpty();
    }

    private static boolean sshdAvailable(int port) throws JSchException {
        JSch jsch = new JSch();

        usePrivateKey(jsch);

        try {
            counter++;
            logger.info(counter + " attempt.");

            logger.info("Establishing Connection with Gerrit SSHD ...");
            Session s = jsch.getSession("admin", "localhost", port);
            s.setConfig("StrictHostKeyChecking", "no");
            s.connect();
            
            logger.info("Connection established.");
            s.disconnect();
            
            return true;
        } catch (JSchException jschEx) {
            if (jschEx.getMessage().contains("Auth fail")) {
                logger.info("Connection established with auth failed.", jschEx);
                return true;
            } else {
                logger.info("Connection refused.", jschEx);
                return false;
            }
        }
    }

    private static void usePrivateKey(JSch jsch) throws JSchException {

        if(!StringUtils.isEmptyOrNull(adminPrivateKeyPath)) {
            logger.info("Using specified location for admin SSH private keys: " + adminPrivateKeyPath);

            if (!StringUtils.isEmptyOrNull(adminPrivateKeyPassword)) {
                logger.info("Using the associated password with the private key (see the env variables)");
                jsch.addIdentity(adminPrivateKeyPath, adminPrivateKeyPassword);
            }
            else {
                logger.info("Using private key specified.");
                jsch.addIdentity(adminPrivateKeyPath);
            }
        }
        else {
            logger.info("Using default location for admin SSH private keys");
        }
    }
}
