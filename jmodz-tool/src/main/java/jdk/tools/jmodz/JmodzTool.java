package jdk.tools.jmodz;

import java.io.PrintWriter;
import java.util.Optional;
import java.util.spi.ToolProvider;

/**
 * Main entry point for jmodz tool - ZSTD-compressed equivalent of jmod
 */
public class JmodzTool {
    
    public static void main(String... args) throws Exception {
        JmodzTask t = new JmodzTask();
        int rc = t.run(args);
        System.exit(rc);
    }

    /**
     * Entry point that does <i>not</i> call System.exit.
     *
     * @param out output stream
     * @param err error output stream
     * @param args command line arguments
     * @return an exit code. 0 means success, non-zero means an error occurred.
     */
    public static int run(PrintWriter out, PrintWriter err, String... args) {
        JmodzTask t = new JmodzTask();
        t.setLog(out, err);
        return t.run(args);
    }

    public static class JmodzToolProvider implements ToolProvider {
        @Override
        public String name() {
            return "jmodz";
        }

        @Override
        public Optional<String> description() {
            return Optional.of("Create and manipulate ZSTD-compressed JMOD files");
        }

        @Override
        public int run(PrintWriter out, PrintWriter err, String... args) {
            return JmodzTool.run(out, err, args);
        }
    }
}
