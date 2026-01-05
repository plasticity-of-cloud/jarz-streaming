package jdk.incubator.jarz.tools;

import jdk.incubator.jarz.tools.JarzArgumentParser.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for JAR-compatible command-line argument parser.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
class JarzArgumentParserTest {
    
    @Test
    void testCreateOperation() {
        ParsedArgs args = JarzArgumentParser.parse(new String[]{"-cf", "test.jarz", "file1", "file2"});
        
        assertThat(args.getOperation()).isEqualTo(Operation.CREATE);
        assertThat(args.getArchiveFile()).isEqualTo("test.jarz");
        assertThat(args.getInputFiles()).containsExactly("file1", "file2");
        assertThat(args.isVerbose()).isFalse();
    }
    
    @Test
    void testCreateWithVerbose() {
        ParsedArgs args = JarzArgumentParser.parse(new String[]{"-cvf", "test.jarz", "file1"});
        
        assertThat(args.getOperation()).isEqualTo(Operation.CREATE);
        assertThat(args.getArchiveFile()).isEqualTo("test.jarz");
        assertThat(args.isVerbose()).isTrue();
    }
    
    @Test
    void testExtractOperation() {
        ParsedArgs args = JarzArgumentParser.parse(new String[]{"-xf", "test.jarz"});
        
        assertThat(args.getOperation()).isEqualTo(Operation.EXTRACT);
        assertThat(args.getArchiveFile()).isEqualTo("test.jarz");
    }
    
    @Test
    void testListOperation() {
        ParsedArgs args = JarzArgumentParser.parse(new String[]{"-tf", "test.jarz"});
        
        assertThat(args.getOperation()).isEqualTo(Operation.LIST);
        assertThat(args.getArchiveFile()).isEqualTo("test.jarz");
    }
    
    @Test
    void testUpdateOperation() {
        ParsedArgs args = JarzArgumentParser.parse(new String[]{"-uf", "test.jarz", "newfile"});
        
        assertThat(args.getOperation()).isEqualTo(Operation.UPDATE);
        assertThat(args.getArchiveFile()).isEqualTo("test.jarz");
        assertThat(args.getInputFiles()).containsExactly("newfile");
    }
    
    @Test
    void testConvertOperation() {
        ParsedArgs args = JarzArgumentParser.parse(new String[]{"--convert", "input.jar", "output.jarz"});
        
        assertThat(args.getOperation()).isEqualTo(Operation.CONVERT);
        assertThat(args.getInputFiles()).containsExactly("input.jar", "output.jarz");
    }
    
    @Test
    void testLongOptions() {
        ParsedArgs args = JarzArgumentParser.parse(new String[]{
            "--create", "--file", "test.jarz", "--verbose", "--main-class", "Main", "file1"
        });
        
        assertThat(args.getOperation()).isEqualTo(Operation.CREATE);
        assertThat(args.getArchiveFile()).isEqualTo("test.jarz");
        assertThat(args.isVerbose()).isTrue();
        assertThat(args.getMainClass()).isEqualTo("Main");
        assertThat(args.getInputFiles()).containsExactly("file1");
    }
    
    @Test
    void testManifestOption() {
        ParsedArgs args = JarzArgumentParser.parse(new String[]{"-cfm", "test.jarz", "manifest.mf", "file1"});
        
        assertThat(args.getOperation()).isEqualTo(Operation.CREATE);
        assertThat(args.getArchiveFile()).isEqualTo("test.jarz");
        assertThat(args.getManifestFile()).isEqualTo("manifest.mf");
        assertThat(args.getInputFiles()).containsExactly("file1");
    }
    
    @Test
    void testDirectoryChange() {
        ParsedArgs args = JarzArgumentParser.parse(new String[]{"-cf", "test.jarz", "-C", "classes", ".", "file1"});
        
        assertThat(args.getOperation()).isEqualTo(Operation.CREATE);
        assertThat(args.getDirectoryChanges()).hasSize(1);
        
        DirectoryChange dirChange = args.getDirectoryChanges().get(0);
        assertThat(dirChange.getDirectory()).isEqualTo("classes");
        assertThat(dirChange.getFiles()).containsExactly(".", "file1");
        assertThat(args.getInputFiles()).isEmpty(); // Files after -C are part of directory change
    }
    
    @Test
    void testMultiReleaseOption() {
        ParsedArgs args = JarzArgumentParser.parse(new String[]{
            "-cf", "test.jarz", "--release", "11", "file1"
        });
        
        assertThat(args.getOperation()).isEqualTo(Operation.CREATE);
        assertThat(args.getReleaseVersion()).isEqualTo("11");
    }
    
    @Test
    void testModuleOptions() {
        ParsedArgs args = JarzArgumentParser.parse(new String[]{
            "-cf", "test.jarz", "--module-version", "1.0", "--hash-modules", ".*", "file1"
        });
        
        assertThat(args.getModuleVersion()).isEqualTo("1.0");
        assertThat(args.getHashModulesPattern()).isEqualTo(".*");
    }
    
    @Test
    void testCompressionOptions() {
        ParsedArgs args = JarzArgumentParser.parse(new String[]{"-c0f", "test.jarz", "file1"});
        
        assertThat(args.getOperation()).isEqualTo(Operation.CREATE);
        assertThat(args.isNoCompress()).isTrue();
    }
    
    @Test
    void testNoManifestOption() {
        ParsedArgs args = JarzArgumentParser.parse(new String[]{"-cMf", "test.jarz", "file1"});
        
        assertThat(args.getOperation()).isEqualTo(Operation.CREATE);
        assertThat(args.isNoManifest()).isTrue();
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"-h", "--help"})
    void testHelpOption(String helpFlag) {
        assertThatThrownBy(() -> JarzArgumentParser.parse(new String[]{helpFlag}))
            .isInstanceOf(HelpRequestedException.class);
    }
    
    @Test
    void testVersionOption() {
        assertThatThrownBy(() -> JarzArgumentParser.parse(new String[]{"--version"}))
            .isInstanceOf(VersionRequestedException.class);
    }
    
    @Test
    void testNoOperation() {
        assertThatThrownBy(() -> JarzArgumentParser.parse(new String[]{"-f", "test.jarz"}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No operation specified");
    }
    
    @Test
    void testNoArchiveFile() {
        assertThatThrownBy(() -> JarzArgumentParser.parse(new String[]{"-c"}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Archive file must be specified");
    }
    
    @Test
    void testInvalidFlag() {
        assertThatThrownBy(() -> JarzArgumentParser.parse(new String[]{"-z"}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown flag: -z");
    }
    
    @Test
    void testMissingArgumentForOption() {
        assertThatThrownBy(() -> JarzArgumentParser.parse(new String[]{"-cf"}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("requires an argument");
    }
    
    @Test
    void testEqualsStyleLongOptions() {
        ParsedArgs args = JarzArgumentParser.parse(new String[]{
            "--create", "--file=test.jarz", "--main-class=Main", "file1"
        });
        
        assertThat(args.getOperation()).isEqualTo(Operation.CREATE);
        assertThat(args.getArchiveFile()).isEqualTo("test.jarz");
        assertThat(args.getMainClass()).isEqualTo("Main");
        assertThat(args.getInputFiles()).containsExactly("file1");
    }
}
