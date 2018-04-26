import java.io.File;

public class SystemInfo {
    private int availableCores;
    private long availableMemory;

    // Construct all important operating system information
    public SystemInfo() {
        this.availableCores = Runtime.getRuntime().availableProcessors();
        this.availableMemory = Runtime.getRuntime().freeMemory();
    }

    // Returns total number of processors or cores available to the JVM
    // This is helpful for increasing or decreasing the number of threads used to run the program efficiently
    public int getNumCores() {
        return this.availableCores;
    }

    // Returns total amount of free memory available to the JVM
    public long getFreeMem() {
        return this.availableMemory;
    }

    public void getSystemInfo() {
        /* This will return Long.MAX_VALUE if there is no preset limit */
        long maxMemory = Runtime.getRuntime().maxMemory();
        /* Maximum amount of memory the JVM will attempt to use */
        System.out.println("Maximum memory (bytes): " + (maxMemory == Long.MAX_VALUE ? "no limit" : maxMemory));

        /* Total memory currently available to the JVM */
        System.out.println("Total memory available to JVM (bytes): " + Runtime.getRuntime().totalMemory());

        /* Get a list of all filesystem roots on this system */
        File[] roots = File.listRoots();

        /* For each filesystem root, print some info */
        for (File root : roots) {
            System.out.println("File system root: " + root.getAbsolutePath());
            System.out.println("Total space (bytes): " + root.getTotalSpace());
            System.out.println("Free space (bytes): " + root.getFreeSpace());
            System.out.println("Usable space (bytes): " + root.getUsableSpace());
        }
    }
}
