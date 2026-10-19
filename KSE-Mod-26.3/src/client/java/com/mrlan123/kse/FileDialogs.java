package com.mrlan123.kse;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.GraphicsEnvironment;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * "Open file" dialog for importing custom sounds. Tries the best available
 * option and falls back step by step; the caller shows a "type the path"
 * screen when nothing is available. Must be called off the render thread.
 */
public final class FileDialogs {
    public enum Status { OK, CANCELLED, UNAVAILABLE }

    public record Result(Status status, Path path) {
        static Result ok(String p) { return new Result(Status.OK, Path.of(p)); }
        static final Result CANCELLED = new Result(Status.CANCELLED, null);
        static final Result UNAVAILABLE = new Result(Status.UNAVAILABLE, null);
    }

    private record Proc(int exit, String out, String err) { }

    private FileDialogs() { }

    public static Result pickAudioFile() {
        // Minecraft's bundled tinyfd gives the nicest native dialog where it is available.
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String[] patterns = {"*.wav", "*.mp3", "*.ogg", "*.oga", "*.aiff", "*.aif", "*.au"};
            PointerBuffer filters = stack.mallocPointer(patterns.length);
            for (String p : patterns) {
                filters.put(stack.UTF8(p));
            }
            filters.flip();
            // Looked up by name so the mod compiles and loads even where tinyfd is not shipped.
            Class<?> tfd = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
            Object res = tfd.getMethod("tinyfd_openFileDialog",
                            CharSequence.class, CharSequence.class, PointerBuffer.class, CharSequence.class, boolean.class)
                    .invoke(null, "Select a sound file", null, filters,
                            "Audio files (wav, mp3, ogg, aiff, au)", false);
            String path = res == null ? null : res.toString();
            return (path == null || path.isBlank()) ? Result.CANCELLED : Result.ok(path);
        } catch (Throwable t) {
            System.err.println("[KSE] tinyfd unavailable, using fallback: " + t);
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        Result r = Result.UNAVAILABLE;
        try {
            if (os.contains("win")) {
                r = windows();
            } else if (os.contains("mac")) {
                r = mac();
            } else {
                r = linux();
            }
        } catch (Throwable t) {
            System.err.println("[KSE] native file dialog failed: " + t);
        }
        if (r.status() != Status.UNAVAILABLE) {
            return r;
        }
        return swing();
    }

    private static Result windows() throws Exception {
        String script = "[Console]::OutputEncoding=[Text.Encoding]::UTF8;"
                + "Add-Type -AssemblyName System.Windows.Forms;"
                + "$f=New-Object System.Windows.Forms.OpenFileDialog;"
                + "$f.Title='Select a sound file';"
                + "$f.Filter='Audio files|*.wav;*.mp3;*.ogg;*.oga;*.aiff;*.aif;*.au|All files|*.*';"
                + "if($f.ShowDialog() -eq 'OK'){Write-Output $f.FileName}";
        return fromOutput(run(List.of("powershell", "-NoProfile", "-STA", "-Command", script)));
    }

    private static Result mac() throws Exception {
        Proc p = run(List.of("osascript", "-e",
                "POSIX path of (choose file with prompt \"Select a sound file\")"));
        if (p.exit() != 0) {
            return p.err().contains("-128") ? Result.CANCELLED : Result.UNAVAILABLE;
        }
        return fromOutput(p);
    }

    private static Result linux() throws Exception {
        try {
            return fromOutput(run(List.of("zenity", "--file-selection", "--title=Select a sound file",
                    "--file-filter=Audio files | *.wav *.mp3 *.ogg *.oga *.aiff *.aif *.au")));
        } catch (IOException notInstalled) {
            // fall through to kdialog
        }
        try {
            return fromOutput(run(List.of("kdialog", "--getopenfilename", System.getProperty("user.home", "."),
                    "*.wav *.mp3 *.ogg *.oga *.aiff *.aif *.au|Audio files")));
        } catch (IOException notInstalled) {
            return Result.UNAVAILABLE;
        }
    }

    private static Result swing() {
        if (GraphicsEnvironment.isHeadless()) {
            return Result.UNAVAILABLE;
        }
        try {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select a sound file");
            chooser.setFileFilter(new FileNameExtensionFilter(
                    "Audio files (*.wav, *.mp3, *.ogg, *.aiff, *.aif, *.au)",
                    "wav", "mp3", "ogg", "oga", "aiff", "aif", "au"));
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION
                    && chooser.getSelectedFile() != null) {
                return new Result(Status.OK, chooser.getSelectedFile().toPath());
            }
            return Result.CANCELLED;
        } catch (Throwable t) {
            System.err.println("[KSE] Swing file dialog failed: " + t);
            return Result.UNAVAILABLE;
        }
    }

    private static Result fromOutput(Proc p) {
        String line = p.out().strip();
        int nl = line.indexOf('\n');
        if (nl >= 0) {
            line = line.substring(0, nl).strip();
        }
        return line.isEmpty() ? Result.CANCELLED : Result.ok(line);
    }

    private static Proc run(List<String> cmd) throws IOException, InterruptedException {
        boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
        Process proc = new ProcessBuilder(cmd)
                .redirectInput(ProcessBuilder.Redirect.from(new File(win ? "NUL" : "/dev/null")))
                .start();
        byte[] out = readAll(proc.getInputStream());
        byte[] err = readAll(proc.getErrorStream());
        int exit = proc.waitFor();
        return new Proc(exit, new String(out, StandardCharsets.UTF_8), new String(err, StandardCharsets.UTF_8));
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        in.transferTo(bos);
        return bos.toByteArray();
    }
}
