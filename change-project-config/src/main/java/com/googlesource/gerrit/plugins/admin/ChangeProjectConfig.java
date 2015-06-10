package com.googlesource.gerrit.plugins.admin;

import org.slf4j.LoggerFactory;

public class ChangeProjectConfig {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ChangeProjectConfig.class);
    private static String localPath, remotePath, projectConfig;

    public static void main(String[] args) {
        localPath = "/home/gerrit/git";
        remotePath = "ssh://admin@localhost:29418/All-Projects";
        projectConfig = "/home/gerrit/config";
        PushCommit pc = new PushCommit(localPath, remotePath, projectConfig);
        pc.init();
    }
    
}
