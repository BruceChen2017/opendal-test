package com.example.opendaldemo;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Reproduces the worker-thread class resolution failure:
 * when OpenDAL is loaded by a custom class loader (Spring Boot fat jar, servlet
 * container, plain URLClassLoader), the tokio worker thread cannot resolve
 * org.apache.opendal.* classes. Symptom: a panic on opendal-tokio-worker-N
 * ("complete future must succeed ...") and a CompletableFuture that never
 * completes.
 *
 * Usage:
 *
 *   java -Djava.library.path=<dir-with-libopendal_java.{so,dll}> \
 *        -cp . Repro <opendal-classes-or-jar> <child|flat>
 *
 * Modes:
 *
 *   child  Reproduce: OpenDAL is loaded by a child URLClassLoader whose parent
 *          is the bootstrap loader. Do NOT put OpenDAL on the app classpath
 *          (run with `-cp .` only).
 *
 *   flat   Control: OpenDAL is loaded by the app (system) class loader. Put the
 *          OpenDAL classes/jar on the classpath as well
 *          (`-cp .:<opendal-classes-or-jar>`).
 */
public class Repro {
    public static void main(String[] args) throws Exception {
        if (args.length < 2 || !("child".equals(args[1]) || "flat".equals(args[1]))) {
            System.err.println("usage: Repro <opendal-classes-or-jar> <child|flat>");
            System.exit(2);
        }

        final String opendalPath = args[0];
        final boolean childMode = "child".equals(args[1]);

        final ClassLoader loader = childMode
                ? new URLClassLoader(new URL[] {new File(opendalPath).toURI().toURL()}, null)
                : Repro.class.getClassLoader();
        Thread.currentThread().setContextClassLoader(loader);

        System.out.println("[repro] mode          = " + args[1]);
        System.out.println("[repro] app loader    = " + Repro.class.getClassLoader());
        System.out.println("[repro] system loader = " + ClassLoader.getSystemClassLoader());

        // What a worker thread without Java frames resolves: FindClass falls back to
        // the system class loader.
        try {
            Class.forName("org.apache.opendal.AsyncOperator$AsyncRegistry", false,
                    ClassLoader.getSystemClassLoader());
            System.out.println("[repro] system loader CAN see AsyncRegistry");
        } catch (Throwable t) {
            System.out.println("[repro] system loader CANNOT see AsyncRegistry -> " + t);
        }

        final Class<?> opCls = Class.forName("org.apache.opendal.AsyncOperator", true, loader);
        System.out.println("[repro] AsyncOperator loaded by " + opCls.getClassLoader());

        final Object op = opCls.getMethod("of", String.class, Map.class)
                .invoke(null, "memory", new HashMap<String, String>());
        final Object future = opCls.getMethod("write", String.class, String.class)
                .invoke(op, "test", "hello world");
        System.out.println("[repro] future        = " + future);

        try {
            future.getClass().getMethod("get", long.class, TimeUnit.class)
                    .invoke(future, 10L, TimeUnit.SECONDS);
            System.out.println("[repro] RESULT: completed  => SUCCESS");
        } catch (Exception e) {
            System.out.println("[repro] FAILED: " + e.getCause());
        }

        // Recent builds attach the default executor's worker threads as non-daemon
        // threads, so a plain JVM would hang at exit; exit explicitly here.
        System.exit(0);
    }
}
