package ch.cern.dirq.test;

import java.io.IOException;

import org.junit.Test;

/**
 * Unit test for {@link ch.cern.dirq.QueueSimple}.
 *
 * @author Massimo Paladin - massimo.paladin@gmail.com
 *         <br />Copyright (C) CERN 2012-2013
 */
public class TestDirqLaunch {

    /**
     * Multi test.
     *
     * @throws Exception
     */
    @Test
    public void testMulti() throws IOException {
        System.out.println("################ TestDirq simple ################## BEGIN");
        new TestDirq().mainSimple();
        System.out.println("################ TestDirq simple ################## END");
    }
}
