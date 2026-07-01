package dtm.bulder.printer;

import dtm.bulder.printer.formater.Formatter;
import dtm.bulder.printer.formater.JsonFormatter;
import dtm.bulder.printer.formater.RawFormater;
import dtm.bulder.printer.formater.XMLFormater;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class Printer {

    private static final Printer PRINTER = new Printer();

    private final Map<FormaterType, Formatter> FORMATERS = new ConcurrentHashMap<>();

    private final AtomicReference<FormaterType> FORMATER_TYPE =
            new AtomicReference<>(FormaterType.RAW);

    private final BlockingQueue<PrintTask> printQueue =
            new LinkedBlockingQueue<>();

    private final AtomicBoolean closing =
            new AtomicBoolean(false);

    private final AtomicInteger exitCode =
            new AtomicInteger(0);

    private final Object enqueueLock = new Object();

    private final Thread printerThread;

    private Printer() {
        printerThread = new Thread(this::processQueue, "formater-printer-thread");
        printerThread.setDaemon(true);
        printerThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(
                this::closeAndDrain,
                "formater-printer-shutdown-hook"
        ));
    }

    public static Printer getInstance() {
        return PRINTER;
    }

    public FormaterType getFormaterType() {
        return FORMATER_TYPE.get();
    }

    public void setFormaterType(FormaterType formaterType) {
        if (formaterType == null) {
            return;
        }

        FORMATER_TYPE.set(formaterType);
    }

    public void println(Severity severity, Object object, Object... args) {
        synchronized (enqueueLock) {
            if (closing.get()) {
                return;
            }

            printQueue.offer(new PrintTask(severity, object, getFormaterType(), args));
        }
    }

    public void println(Object object, Object... args) {
        synchronized (enqueueLock) {
            if (closing.get()) {
                return;
            }

            printQueue.offer(new PrintTask(Severity.INFO, object, getFormaterType(), args));
        }
    }

    private void processQueue() {
        while (true) {
            try {
                PrintTask task = printQueue.take();

                if (task.poison) {
                    break;
                }

                try {
                    printNow(task.severity, task.object, task.formaterType, task.args);
                } catch (Throwable throwable) {
                    throwable.printStackTrace(System.err);
                    requestExitAfterDrain(1);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                requestExitAfterDrain(1);
                break;
            }
        }

        int code = exitCode.get();

        if (code != 0) {
            exitFromAnotherThread(code);
        }
    }

    private void exitFromAnotherThread(int code) {
        Thread exitThread = new Thread(() -> System.exit(code), "printer-exit-thread");
        exitThread.setDaemon(false);

        exitThread.start();
    }

    private void closeAndDrain() {
        requestExitAfterDrain(0);

        if (Thread.currentThread() == printerThread) {
            return;
        }

        try {
            printerThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void requestExitAfterDrain(int code) {
        synchronized (enqueueLock) {
            if (code != 0) {
                exitCode.compareAndSet(0, code);
            }

            if (closing.compareAndSet(false, true)) {
                printQueue.offer(PrintTask.poison());
            }
        }
    }

    private void printNow(Severity severity, Object object, FormaterType formaterType, Object... args) {
        Formatter formatter = getFormater(formaterType);

        if (formatter == null) {
            System.err.println("No formater for " + formaterType);
            requestExitAfterDrain(1);
            return;
        }

        String message = formatter.format(severity, object, args);
        System.out.println(message);
    }

    private Formatter getFormater(FormaterType formaterType) {
        if (formaterType == null) {
            return null;
        }

        return FORMATERS.computeIfAbsent(formaterType, type -> {
            return switch (type) {
                case JSON -> new JsonFormatter();
                case RAW -> new RawFormater();
                case XML -> new XMLFormater();
            };
        });
    }

    private static final class PrintTask {

        private final Severity severity;
        private final Object object;
        private final FormaterType formaterType;
        private final Object[] args;
        private final boolean poison;

        private PrintTask(Severity severity, Object object, FormaterType formaterType, Object[] args) {
            this.object = object;
            this.severity = severity;
            this.formaterType = formaterType;
            this.args = args != null ? args : new Object[0];
            this.poison = false;
        }

        private PrintTask() {
            this.object = null;
            this.severity = Severity.INFO;
            this.formaterType = null;
            this.args = new Object[0];
            this.poison = true;
        }

        private static PrintTask poison() {
            return new PrintTask();
        }
    }
}
