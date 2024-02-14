package ch.cern.dirq.test;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;

import ch.cern.dirq.Queue;
import ch.cern.dirq.QueueSimple;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Test suite used to compare and stress test different implementations
 * of directory queue across multiple programming languages.
 * <br>
 * Used in parallel with analog implementations in Perl and Python
 * in order to validate the algorithm and assess interoperability.
 *
 * @author Lionel Cons &lt;lionel.cons@cern.ch&gt;
 * @author Massimo Paladin &lt;massimo.paladin@gmail.com&gt;
 * Copyright (C) CERN 2012-2024
 */
@Command(name = "java-dirq-test",
         description = "directory queue test suite (Java)")
public class TestDirq implements Callable<Integer> {

    private static final List<String> TESTS =
        Arrays.asList("add", "count", "get", "iterate", "purge", "remove", "simple");
    private static final String DATE_FORMAT = "yyyy/MM/dd-kk:mm:ss";
    private static final int IRWIN_HALL_COUNT = 6;
    private static final long SECOND = 1000;

    private static Long pid;

    private List<String> tests;

    static {
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        String jvmName = runtimeBean.getName();
        pid = Long.valueOf(jvmName.split("@")[0]);
    }

    @Option(names = {"-c", "--count"},
            description = "set the elements count")
    private Integer optCount = -1;
    @Option(names = {"-d", "--debug"},
            description = "show debugging information")
    private boolean optDebug;
    @Option(names = {"--header"},
            description = "set header for added elements")
    private boolean optHeader;
    @Option(names = {"-h", "--help"}, usageHelp = true,
            description = "show some help")
    private boolean optHelp;
    @Option(names = {"-l", "--list"},
            description = "list the available tests")
    private boolean optList;
    @Option(names = {"--granularity"},
            description = "time granularity for intermediate directories (QueueSimple)")
    private Integer optGranularity = -1;
    @Option(names = {"--maxlock"},
            description = "maximum time for a locked element (or 0 to disable purging)")
    private Integer optMaxLock = -1;
    @Option(names = {"--maxtemp"},
            description = "maxmum time for a temporary element (or 0 to disable purging)")
    private Integer optMaxTemp = -1;
    @Option(names = {"-p", "--path"},
            description = "set the queue path")
    private String optPath = "";
    @Option(names = {"-r", "--random"},
            description = "randomize the body size")
    private boolean optRandom;
    @Option(names = {"--rndhex"},
            description = "set the random hexadecimal digit for the queue")
    private Integer optRndHex = -1;
    @Option(names = {"-s", "--size"},
            description = "set the body size for added elements")
    private Integer optSize = -1;
    @Option(names = {"--sleep"},
            description = "sleep this amount of seconds before starting")
    private Integer optSleep = 0;
    @Option(names = {"--type"},
            description = "directory queue type (simple|normal")
    private String optType = "simple";
    @Option(names = {"--umask"},
            description = "set the umask for the queue")
    private Integer optUmask = -1;
    @Parameters(index = "0", description = "test to execute")
    private String optTest;

    @Override
    public Integer call() throws Exception {
        if (optList) {
            System.out.print("Available tests:");
            for (String test: TESTS) {
                System.out.print(" " + test);
            }
            System.out.println("");
            return 0;
        }
        try {
            if (!TESTS.contains(optTest)) {
                die("invalid test name: " + optTest);
            }
            if (optType.equals("normal")) {
                die("unsupported directory queue type: " + optType);
            } else if (!optType.equals("simple")) {
                die("invalid directory queue type: " + optType);
            }
            if (optPath.equals("")) {
                die("missing path option");
            }
            if (optSleep > 0) {
                Thread.sleep(optSleep * SECOND);
            }
            runTest(optTest);
        } catch (Exception e) {
            if (optDebug) {
                e.printStackTrace();
            } else {
                System.err.printf("java-dirq-test: %s%n", e.getMessage());
            }
            return 1;
       }
        return 0;
    }

    public static void main(final String... args) {
        int exitCode = new CommandLine(new TestDirq()).execute(args);
        System.exit(exitCode);
    }

    private QueueSimple newDirq() throws IOException {
        if (!optType.equals("simple")) {
            throw new IllegalArgumentException("only DirQ simple is supported");
        }
        QueueSimple queue = new QueueSimple(optPath);
        if (optGranularity > -1) {
            queue.setGranularity(optGranularity);
        }
        if (optRndHex > -1) {
            queue.setRndHex(optRndHex);
        }
        if (optUmask > -1) {
            queue.setUmask(optUmask);
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
        if (optMaxLock > -1) {
            queue.setMaxLock(optMaxLock);
        }
        if (optMaxTemp > -1) {
            queue.setMaxTemp(optMaxTemp);
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
     * @throws IOException
     */
    private void testAdd() throws IOException {
        boolean random = optRandom;
        int size = optSize;
        int count = optCount;
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
     * @throws IOException
     */
    private void testRemove() throws IOException {
        int count = optCount;
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
        File path = new File(optPath);
        if (path.exists()) {
            die("directory exists: " + path);
        }
        if (optCount == -1) {
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
        if (optDebug) {
            String now = new SimpleDateFormat(DATE_FORMAT).format(new Date());
            System.out.printf("# %s TestDirq[%d]: %s%n", now, pid, message);
            System.out.flush();
        }
    }

}
