/**
 * Unit tests for {@link ch.cern.dirq.QueueSimple}.
 *
 * @author Massimo Paladin <massimo.paladin@gmail.com>
 * @author Lionel Cons <lionel.cons@cern.ch>
 *
 * Copyright (C) CERN 2012-2013
 */

package ch.cern.dirq.test;

import java.io.IOException;

import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.Test;

public class TestDirqLaunch {
    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void testMulti() throws IOException {
        String[] args = {
            "--count", "1000",
            "--path", tempDir.getRoot().getPath(),
            "--debug", "simple",
        };
        System.out.println("################ TestDirq simple ################## BEGIN");
        new TestDirq().mainSimple(args);
        System.out.println("################ TestDirq simple ################## END");
    }
}
