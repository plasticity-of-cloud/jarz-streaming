package jdk.tools.jmodz;

import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

public class JmodzToolTest {
    
    @Test
    public void testHelpMessage() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(output);
        PrintWriter err = new PrintWriter(System.err);
        
        int exitCode = JmodzTool.run(out, err, "--help");
        out.flush();
        
        String result = output.toString();
        assert exitCode == 0 : "Expected exit code 0, got " + exitCode;
        assert result.contains("Usage: jmodz <command>") : "Missing usage message";
        assert result.contains("create") : "Missing create command";
        assert result.contains("extract") : "Missing extract command";
    }
}
