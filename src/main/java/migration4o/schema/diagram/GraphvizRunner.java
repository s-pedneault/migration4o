package migration4o.schema.diagram;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Utility for executing Graphviz dot command.
 */
public class GraphvizRunner {

    private GraphvizRunner() {
    }

    public static boolean isDotAvailable() {
        ProcessBuilder builder = new ProcessBuilder("dot", "-V");
        builder.redirectErrorStream(true);

        try {
            Process process = builder.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    public static boolean renderSvg(Path dotFile, Path svgFile) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder("dot", "-Tsvg", dotFile.toAbsolutePath().toString(), "-o", svgFile.toAbsolutePath().toString());
        builder.redirectErrorStream(true);

        Process process = builder.start();
        int exitCode = process.waitFor();
        return exitCode == 0;
    }
}
