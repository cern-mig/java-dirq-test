package ch.cern.dirq.test;

import junit.framework.TestCase;

/**
 * Unit test for {@link ch.cern.dirq.QueueSimple}.
 * 
 * @author Massimo Paladin - massimo.paladin@gmail.com
 * <br />Copyright CERN 2010-2012
 */
public class TestDirqLaunch extends TestCase {

	/**
	 * Create the test case.
	 * 
	 * @param name name of the test case
	 */
	public TestDirqLaunch(String name) {
		super(name);
	}

	/** 	
	* Multi test.
	* 
	* @throws Exception
	*/  	
	public void testMulti() throws Exception {	  	
		System.out.println("################ TestDirq simple ################## BEGIN");
		new TestDirq().mainSimple();
		System.out.println("################ TestDirq simple ################## END");
	}
}