// This file will test how many threads a machine can run at any given time
// For safety measures of the machine, this number will break off at a 1000 threads to prevent system freeze

public class MaximumThreads {
    private static Object s = new Object();
    private static int count = 0;

    public static void main(String[] argv) {
        for (; ; ) {
            new Thread(new Runnable() {
                public void run() {
                    synchronized (s) {
                        if (count >= 5000) {
                            System.out.println("Your machine can run up to 5000 threads.");
                            System.exit(0);
                        }
                        count += 1;
                        System.err.println("New thread #" + count);
                    }
                    for (; ; ) {
                        try {
                            Thread.sleep(1000);
                        } catch (Exception e) {
                            System.err.println(e);
                        }
                    }
                }
            }).start();
        }
    }
}