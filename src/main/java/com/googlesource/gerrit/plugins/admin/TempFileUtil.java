package com.googlesource.gerrit.plugins.admin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TempFileUtil {
    private static List<File> allDirsCreated = new ArrayList<>();

    public static synchronized File createTempDirectory() throws IOException {
        File tmp = File.createTempFile("gerrit_test_", "").getCanonicalFile();
        if (!tmp.delete() || !tmp.mkdir()) {
            throw new IOException("Cannot create " + tmp.getPath());
        }
        allDirsCreated.add(tmp);
        return tmp;
    }

    public static synchronized void cleanup() throws IOException {
        for (File dir : allDirsCreated) {
            recursivelyDelete(dir);
        }
        allDirsCreated.clear();
    }

    private static void recursivelyDelete(File dir) throws IOException {
        if (!dir.getPath().equals(dir.getCanonicalPath())) {
            // Directory symlink reaching outside of temporary space.
            return;
        }
        File[] contents = dir.listFiles();
        if (contents != null) {
            for (File d : contents) {
                if (d.isDirectory()) {
                    recursivelyDelete(d);
                } else {
                    deleteNowOrOnExit(d);
                }
            }
        }
        deleteNowOrOnExit(dir);
    }

    private static void deleteNowOrOnExit(File dir) {
        if (!dir.delete()) {
            dir.deleteOnExit();
        }
    }

    private TempFileUtil() {
    }
}
