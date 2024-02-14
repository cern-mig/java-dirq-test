/**
 * Unit tests for {@link ch.cern.dirq.QueueSimple}.
 *
 * @author Lionel Cons &lt;lionel.cons@cern.ch&gt;
 * @author Massimo Paladin &lt;massimo.paladin@gmail.com&gt;
 * Copyright (C) CERN 2012-2024
 */

package ch.cern.dirq.test;

import java.io.File;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.Test;

import picocli.CommandLine;

public class TestDirqLaunch {
    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void testMulti() throws IOException {
        String tempPath = tempDir.getRoot().getPath();
        File tempFile = new File(tempPath);
        Assert.assertTrue(tempFile.exists());
        tempFile.delete();
        Assert.assertFalse(tempFile.exists());
        System.out.println("################ TestDirq simple ################## BEGIN");
        int exitCode = new CommandLine(new TestDirq()).execute(
            "--debug",
            "--count", "1000",
            "--path", tempPath,
            "simple"
        );
        System.out.println("################ TestDirq simple ################## END");
        Assert.assertTrue(exitCode == 0);
    }
}
