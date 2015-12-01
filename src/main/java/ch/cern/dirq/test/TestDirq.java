package ch.cern.dirq.test;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.CliFactory;
import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

import ch.cern.dirq.Queue;
import ch.cern.dirq.QueueSimple;

/**
 * Test suite used to compare and stress test different implementations
 * of directory queue across multiple programming languages.
 * <p/>
 * Used in parallel with analog implementations in Perl and Python
 * in order to validate the algorithm and assess interoperability.
 *
 * @author Lionel Cons &lt;lionel.cons@cern.ch&gt;
 * @author Massimo Paladin &lt;massimo.paladin@gmail.com&gt;
 * Copyright (C) CERN 2012-2015
 */
public class TestDirq {

    private static final List<String> TESTS =
        Arrays.asList("add", "count", "get", "iterate", "purge", "remove", "simple");
    private static final String DATE_FORMAT = "yyyy/MM/dd-kk:mm:ss";
    private static final int IRWIN_HALL_COUNT = 6;
    private static final long SECOND = 1000;

    private static Long pid;

    private List<String> tests;
    private TestDirQArgs options;

    static {
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        String jvmName = runtimeBean.getName();
        pid = Long.valueOf(jvmName.split("@")[0]);
    }

    /**
     * TestDirq command line options.
     */
    @CommandLineInterface(application = "java-dirq-test")
    private interface TestDirQArgs {

        @Option(shortName = "c", longName = "count", defaultValue = "-1",
                description = "set the elements count")
        int getCount();

        @Option(shortName = "d", longName = "debug",
                description = "show debugging information")
        boolean isDebug();

        @Option(longName = "header",
                description = "set header for added elements")
        boolean isHeader();

        @Option(helpRequest = true, longName = "help",
                description = "display help")
        boolean getHelp();

        @Option(shortName = "l", longName = "list",
                description = "tests: add count size get iterate purge remove simple")
        boolean getList();

        @Option(longName = "granularity", defaultValue = "-1",
                description = "time granularity for intermediate directories (QueueSimple)")
        int getGranularity();

        @Option(longName = "maxlock", defaultValue = "-1",
                description = "maximum time for a locked element (or 0 to disable purging)")
        int getMaxlock();

        @Option(longName = "maxtemp", defaultValue = "-1",
                description = "maxmum time for a temporary element (or 0 to disable purging)")
        int getMaxtemp();

        @Option(shortName = "p", longName = "path", defaultValue = "",
                description = "set the queue path")
        String getPath();

        @Option(shortName = "r", longName = "random",
                description = "randomize the body size")
        boolean isRandom();

        @Option(longName = "rndhex", defaultValue = "-1",
                description = "set the random hexadecimal digit for the queue")
        int getRndhex();

        @Option(shortName = "s", longName = "size", defaultValue = "-1",
                description = "set the body size for added elements")
        int getSize();

        @Option(longName = "sleep", defaultValue = "0",
                description = "sleep this amount of seconds before starting")
        int getSleep();

        @Option(longName = "type", defaultValue = "simple",
                description = "DirQ type (simple|normal)")
        String getType();

        @Option(longName = "umask", defaultValue = "-1",
                description = "set the umask for the queue")
        int getUmask();

        @Unparsed(name = "test", defaultValue = "")
        String getTest();
    }

    private TestDirQArgs parseArguments(final String[] args) {
        TestDirQArgs parsed = null;
        try {
            parsed = CliFactory.parseArguments(TestDirQArgs.class, args);
            if (parsed.getList()) {
                System.out.print("Available tests:");
                for (String test: TESTS) {
                    System.out.print(" " + test);
                }
                System.out.println("");
            } else {
                if (parsed.getTest().equals("")) {
                    throw new ArgumentValidationException("missing test name");
                }
                if (!TESTS.contains(parsed.getTest())) {
                    throw new ArgumentValidationException("invalid test name: "
                                                          + parsed.getTest());
                }
                if (parsed.getType().equals("normal")) {
                    throw new ArgumentValidationException("unsupported DirQ type: "
                                                          + parsed.getType());
                } else if (!parsed.getType().equals("simple")) {
                    throw new ArgumentValidationException("unexpected DirQ type: "
                                                          + parsed.getType());
                }
                tests = new ArrayList<String>();
                tests.add(parsed.getTest());
            }
        } catch (ArgumentValidationException e) {
            throw new RuntimeException(e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        return parsed;
    }

    private QueueSimple newDirq() throws IOException {
        if (!options.getType().equals("simple")) {
            throw new IllegalArgumentException("only DirQ simple is supported");
        }
        QueueSimple queue = new QueueSimple(options.getPath());
        if (options.getGranularity() > -1) {
            queue.setGranularity(options.getGranularity());
        }
        if (options.getRndhex() > -1) {
            queue.setRndHex(options.getRndhex());
        }
        if (options.getUmask() > -1) {
            queue.setUmask(options.getUmask());
        }
        return queue;
    }

    private void testCount() throws IOException {
        Queue queue = newDirq();
        int count = queue.count();
        debug(String.format("queue has %d elements", count));
    }

    private void testPurge() throws IOException {
        debug("purging the queue...");
        QueueSimple queue = newDirq();
        if (options.getMaxlock() > -1) {
            queue.setMaxLock(options.getMaxlock());
        }
        if (options.getMaxtemp() > -1) {
            queue.setMaxTemp(options.getMaxtemp());
        }
        queue.purge();
    }

    private void testGet() throws IOException {
        debug("getting all elements in the queue (one pass)...");
        Queue queue = newDirq();
        int done = 0;
        for (String element: queue) {
            if (!queue.lock(element)) {
                continue;
            }
            queue.get(element);
            queue.unlock(element);
            done++;
        }
        debug(String.format("got %d elements", done));
    }

    private void testIterate() throws IOException {
        debug("iterating all elements in the queue (one pass)...");
        Queue queue = newDirq();
        int done = 0;
        for (String element: queue) {
            if (!queue.lock(element)) {
                continue;
            }
            queue.unlock(element);
            done++;
        }
        debug(String.format("iterated %d elements", done));
    }

    private String newBody(final int size, final boolean random) {
        int asize;
        if (random) {
            // see Irwin-Hall in http://en.wikipedia.org/wiki/Normal_distribution
            double rnd = 0;
            for (int i = 0; i < IRWIN_HALL_COUNT * 2; i++) {
                rnd += Math.random();
            }
            rnd -= IRWIN_HALL_COUNT;
            rnd /= IRWIN_HALL_COUNT;
            rnd *= size;
            asize = size + (int) rnd;
        } else {
            asize = size;
        }
        if (asize < 1) {
            return "";
        }
        char[] charArray = new char[asize];
        Arrays.fill(charArray, 'A');
        return new String(charArray);
    }

    /**
     * Test add action on a directory queue.
     *
     * @throws QueueException
     */
    private void testAdd() throws IOException {
        boolean random = options.isRandom();
        int size = options.getSize();
        int count = options.getCount();
        if (count > -1) {
            debug(String.format("adding %d elements to the queue...", count));
        } else {
            debug("adding elements to the queue forever...");
        }
        Queue queue = newDirq();
        int done = 0;
        String element;
        while (count == -1 || done < count) {
            done++;
            if (size > -1) {
                element = newBody(size, random);
            } else {
                element = "Element " + done + " ;-)\n";
            }
            queue.add(element);
        }
        debug(String.format("added %d elements", done));
    }

    /**
     * Test remove action on a directory queue.
     *
     * @throws QueueException
     */
    private void testRemove() throws IOException {
        int count = options.getCount();
        if (count > -1) {
            debug(String.format("removing %d elements from the queue...", count));
        } else {
            debug("removing all elements from the queue (one pass)...");
        }
        Queue queue = newDirq();
        int done = 0;
        if (count > -1) {
            // loop to iterate until enough are removed
            while (done < count) {
                for (String element: queue) {
                    if (!queue.lock(element)) {
                        continue;
                    }
                    done++;
                    queue.remove(element);
                    if (done == count) {
                        break;
                    }
                }
            }
        } else {
            // one pass only
            for (String element: queue) {
                if (!queue.lock(element)) {
                    continue;
                }
                queue.remove(element);
                done++;
            }
            debug(String.format("removed %d elements", done));
        }
    }

    private void testSimple() throws IOException {
        File path = new File(options.getPath());
        if (path.exists()) {
            die("directory exists: " + path);
        }
        if (options.getCount() == -1) {
            die("missing option: --count");
        }
        testAdd();
        testCount();
        testPurge();
        testGet();
        testRemove();
        testPurge();
        String[] children = path.list();
        int num = children == null ? 0 : children.length;
        if (num != 1) {
            throw new IllegalArgumentException("unexpected subdirs number: " + num);
        }
        recursiveDelete(path);
    }

    private void runTest(final String name) throws IOException {
        long t1 = System.currentTimeMillis();
        if (name.equals("add")) {
            testAdd();
        } else if (name.equals("count")) {
            testCount();
        } else if (name.equals("get")) {
            testGet();
        } else if (name.equals("iterate")) {
            testIterate();
        } else if (name.equals("purge")) {
            testPurge();
        } else if (name.equals("remove")) {
            testRemove();
        } else if (name.equals("simple")) {
            testSimple();
        } else {
            throw new IllegalArgumentException("unexpected test name: " + name);
        }
        long t2 = System.currentTimeMillis();
        debug(String.format("done in %.4f seconds", (t2 - t1) / (double) SECOND));
    }

    /**
     * Delete recursively given path.
     *
     * @param path path to be removed
     * @return return true if removal succeed
     */
    public static boolean recursiveDelete(final File path) {
        if (path.isDirectory()) {
            String[] children = path.list();
            if (children == null) {
                return false;
            }
            for (int i = 0; i < children.length; i++) {
                if (!recursiveDelete(new File(path, children[i]))) {
                    return false;
                }
            }
        }
        return path.delete();
    }

    /**
     * Allow to run a set of tests from unit tests.
     *
     * @throws IOException
     */
    public void mainSimple(final String[] args) throws IOException {
        options = parseArguments(args);
        File path = new File(options.getPath());
        recursiveDelete(path);
        try {
            testSimple();
        } catch (IOException e) {
            recursiveDelete(path);
            throw e;
        }
        recursiveDelete(path);
    }

    /**
     * Execute tests with given command line.
     *
     * @param args command line arguments
     * @throws InterruptedException
     * @throws QueueException
     */
    public void doMain(final String[] args) throws InterruptedException, IOException {
        options = parseArguments(args);
        if (!options.getList()) {
            if (options.getPath().length() == 0) {
                die("Option is mandatory: -p/--path");
            }
            if (options.getSleep() > 0) {
                Thread.sleep(options.getSleep() * SECOND);
            }
            for (String test: tests) {
                runTest(test);
            }
        }
    }

    /**
     * Die with given message.
     *
     * @param message message printed before dieing
     */
    private static void die(final String message) {
        throw new RuntimeException(message);
    }

    /**
     * Debug the given message.
     *
     * @param message message logged
     */
    private void debug(final String message) {
        if (options.isDebug()) {
            String now = new SimpleDateFormat(DATE_FORMAT).format(new Date());
            System.out.printf("# %s TestDirq[%d]: %s%n", now, pid, message);
            System.out.flush();
        }
    }

    /**
     * Main called from command line.
     *
     * @param args given command line arguments
     * @throws QueueException
     * @throws InterruptedException
     */
    public static void main(final String[] args) throws InterruptedException, IOException {
        new TestDirq().doMain(args);
    }

}
