package www.webserver.com;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import androidx.documentfile.provider.DocumentFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class StorageHelper {
    private final Context context;
    private final boolean useSaf;
    private String realPath;

    private static final String TAG = "StorageHelper";
    private static final long MAX_ROOT_READ_SIZE = 100L * 1024 * 1024;

    public StorageHelper(Context context) {
        this.context = context;
        this.useSaf = (GlobalVars.rootUri != null);
        this.realPath = null;
        if (useSaf) {
            this.realPath = tryGetRealPath(GlobalVars.rootUri);
            if (this.realPath == null && RootChecker.isDeviceRooted()) {
                this.realPath = resolveRealPathWithRoot(GlobalVars.rootUri);
            }
        }
    }

    public boolean isLegacyMode() {
        return !useSaf || realPath != null;
    }

    public String getRealPath() {
        return realPath;
    }

    private static String tryGetRealPath(Uri treeUri) {
        String docId = DocumentsContract.getTreeDocumentId(treeUri);
        if (docId == null) return null;
        String[] parts = docId.split(":");
        if (parts.length < 2) return null;
        String type = parts[0];
        String id = parts[1];
        if ("primary".equalsIgnoreCase(type)) {
            return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + id;
        }
        File[] externalDirs = GlobalVars.appContext.getExternalFilesDirs(null);
        for (File dir : externalDirs) {
            if (dir == null) continue;
            String abs = dir.getAbsolutePath();
            int idx = abs.indexOf("/Android/data");
            if (idx > 0) {
                String base = abs.substring(0, idx);
                if (abs.contains(type)) {
                    return base + "/" + id;
                }
            }
        }
        String candidate = "/storage/" + type + "/" + id;
        return new File(candidate).exists() ? candidate : null;
    }

    private static String resolveRealPathWithRoot(Uri treeUri) {
        if (!RootChecker.isDeviceRooted()) return null;
        String docId = DocumentsContract.getTreeDocumentId(treeUri);
        if (docId == null) return null;
        String folderName = docId.contains(":") ? docId.substring(docId.indexOf(':') + 1) : docId;
        if (folderName.isEmpty()) return null;

        String[] searchRoots = {"/storage", "/mnt/media_rw", "/data/data/com.termux/files/home/storage"};
        for (String root : searchRoots) {
            String path = findFolderWithRoot(root, folderName);
            if (path != null) return path;
        }
        return null;
    }

    private static String findFolderWithRoot(String rootPath, String folderName) {
        String cmd = "find " + escapeShellArg(rootPath) + " -maxdepth 3 -type d -name " + escapeShellArg(folderName) + " 2>/dev/null | head -1";
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            p.waitFor();
            reader.close();
            if (line != null && !line.isEmpty() && new File(line).isDirectory()) {
                return line;
            }
        } catch (Exception ignored) {}
        return null;
    }

    public List<StorageItem> listDirectory(String relativePath) throws IOException {
        if (realPath != null) {
            File dir = new File(realPath, relativePath).getCanonicalFile();
            if (!dir.exists() || !dir.isDirectory()) return new ArrayList<>();
            File[] files = dir.listFiles();
            List<StorageItem> items = new ArrayList<>();
            if (files != null) {
                for (File f : files) {
                    String name = f.getName();
                    items.add(new StorageItem(name, f.isDirectory(),
                            f.isDirectory() ? 0 : f.length(), f.lastModified()));
                }
            }
            Collections.sort(items);
            return items;
        }
        if (useSaf) {
            DocumentFile root = getSafRoot();
            DocumentFile dir = navigateToSaf(root, relativePath);
            if (dir == null) return new ArrayList<>();
            List<StorageItem> items = new ArrayList<>();
            try {
                for (DocumentFile f : dir.listFiles()) {
                    if (!f.canRead()) continue;
                    String name = f.getName();
                    if (name == null || name.isEmpty()) continue;
                    items.add(new StorageItem(name, f.isDirectory(), f.isDirectory() ? 0 : f.length(), f.lastModified()));
                }
            } catch (SecurityException e) {
                AppLogger.log(TAG, "SAF permission lost", e);
                return new ArrayList<>();
            }
            Collections.sort(items);
            return items;
        } else if (GlobalVars.legacyPath != null) {
            File dir = getSafeFile(relativePath);
            List<StorageItem> items = new ArrayList<>();
            File[] files = dir.listFiles();
            if (files != null && files.length > 0) {
                for (File f : files) {
                    if (!f.canRead()) continue;
                    String name = f.getName();
                    if (name == null) continue;
                    items.add(new StorageItem(name, f.isDirectory(), f.isDirectory() ? 0 : f.length(), f.lastModified()));
                }
                if (!items.isEmpty()) {
                    Collections.sort(items);
                    return items;
                }
            }
            if (RootChecker.isDeviceRooted()) {
                String cmd = "cd " + escapeShellArg(dir.getAbsolutePath()) + " && ls -1p | while read f; do " +
                             "if [ -d \"$f\" ]; then echo \"d|$f\"; else sz=$(wc -c < \"$f\"); echo \"f|$f|$sz\"; fi; done";
                List<String> out = runCommandWithTimeout(cmd, 10000);
                if (out != null) {
                    for (String line : out) {
                        if (line.startsWith("d|")) {
                            String name = line.substring(2);
                            if (!name.startsWith(".")) items.add(new StorageItem(name, true, 0, 0));
                        } else if (line.startsWith("f|")) {
                            String[] parts = line.substring(2).split("\\|");
                            if (parts.length == 2) {
                                String name = parts[0];
                                if (!name.startsWith(".")) {
                                    long size = 0;
                                    try { size = Long.parseLong(parts[1]); } catch (NumberFormatException ignored) {}
                                    items.add(new StorageItem(name, false, size, 0));
                                }
                            }
                        }
                    }
                    Collections.sort(items);
                }
            }
            return items;
        }
        throw new IOException("No storage root configured");
    }

    public InputStream openInputStream(String relativePath) throws IOException {
        if (realPath != null) {
            File f = new File(realPath, relativePath).getCanonicalFile();
            return new FileInputStream(f);
        }
        if (useSaf) {
            DocumentFile root = getSafRoot();
            String parent = "", name = relativePath;
            if (relativePath != null && relativePath.contains("/")) {
                int last = relativePath.lastIndexOf('/');
                parent = relativePath.substring(0, last);
                name = relativePath.substring(last + 1);
            }
            DocumentFile dir = navigateToSaf(root, parent);
            if (dir == null) throw new IOException("Parent not found: " + relativePath);
            DocumentFile file = dir.findFile(name);
            if (file == null || !file.isFile()) throw new IOException("File not found: " + relativePath);
            return context.getContentResolver().openInputStream(file.getUri());
        } else if (GlobalVars.legacyPath != null) {
            File f = getSafeFile(relativePath);
            if (f.canRead()) return new FileInputStream(f);
            if (RootChecker.isDeviceRooted()) {
                if (f.length() > MAX_ROOT_READ_SIZE) {
                    throw new IOException("File too large for root access");
                }
                return new RootInputStream(f);
            }
            throw new IOException("Permission denied");
        }
        throw new IOException("No storage configured");
    }

    public OutputStream openOutputStream(String relativePath, String mimeType) throws IOException {
        if (realPath != null) {
            File f = new File(realPath, relativePath).getCanonicalFile();
            f.getParentFile().mkdirs();
            return new FileOutputStream(f);
        }
        if (useSaf) {
            if (GlobalVars.rootUri != null) {
                try {
                    context.getContentResolver().takePersistableUriPermission(
                        GlobalVars.rootUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    );
                } catch (SecurityException ignored) {}
            }
            DocumentFile root = getSafRoot();
            String parent = "", name = relativePath;
            if (relativePath.contains("/")) {
                int last = relativePath.lastIndexOf('/');
                parent = relativePath.substring(0, last);
                name = relativePath.substring(last + 1);
            }
            DocumentFile dir = navigateToSaf(root, parent);
            if (dir == null) {
                DocumentFile current = root;
                for (String part : parent.split("/")) {
                    if (part.isEmpty()) continue;
                    DocumentFile child = current.findFile(part);
                    if (child == null) child = current.createDirectory(part);
                    current = child;
                }
                dir = current;
            }
            DocumentFile file = dir.createFile(mimeType, name);
            if (file == null) throw new IOException("Cannot create file: " + relativePath);
            return context.getContentResolver().openOutputStream(file.getUri());
        } else if (GlobalVars.legacyPath != null) {
            return new FileOutputStream(getSafeFile(relativePath));
        }
        throw new IOException("No storage configured");
    }

    public long length(String relativePath) {
        try {
            if (realPath != null) {
                File f = new File(realPath, relativePath).getCanonicalFile();
                return f.length();
            }
            if (useSaf) {
                DocumentFile root = getSafRoot();
                DocumentFile target = navigateToSaf(root, relativePath);
                return target != null ? target.length() : 0;
            } else if (GlobalVars.legacyPath != null) {
                File f = getSafeFile(relativePath);
                if (f.canRead()) return f.length();
                if (RootChecker.isDeviceRooted()) {
                    String out = runCommandSingleWithTimeout("stat -c%s " + escapeShellArg(f.getAbsolutePath()), 5000);
                    if (out != null) return Long.parseLong(out.trim());
                }
                return 0;
            }
        } catch (Exception e) { AppLogger.log(TAG, "length", e); }
        return 0;
    }

    public boolean exists(String relativePath) {
        try {
            if (realPath != null) {
                File f = new File(realPath, relativePath).getCanonicalFile();
                return f.exists();
            }
            if (useSaf) {
                DocumentFile root = getSafRoot();
                DocumentFile target = navigateToSaf(root, relativePath);
                return target != null;
            } else if (GlobalVars.legacyPath != null) {
                File f = getSafeFile(relativePath);
                if (f.exists()) return true;
                if (RootChecker.isDeviceRooted()) {
                    String res = runCommandSingleWithTimeout("test -e " + escapeShellArg(f.getAbsolutePath()) + " && echo yes", 5000);
                    return "yes".equals(res);
                }
                return false;
            }
        } catch (Exception e) { AppLogger.log(TAG, "exists", e); }
        return false;
    }

    public boolean isDirectory(String relativePath) {
        try {
            if (realPath != null) {
                File f = new File(realPath, relativePath).getCanonicalFile();
                return f.isDirectory();
            }
            if (useSaf) {
                DocumentFile root = getSafRoot();
                DocumentFile target = navigateToSaf(root, relativePath);
                return target != null && target.isDirectory();
            } else if (GlobalVars.legacyPath != null) {
                File f = getSafeFile(relativePath);
                if (f.isDirectory()) return true;
                if (RootChecker.isDeviceRooted()) {
                    String res = runCommandSingleWithTimeout("test -d " + escapeShellArg(f.getAbsolutePath()) + " && echo yes", 5000);
                    return "yes".equals(res);
                }
                return false;
            }
        } catch (Exception e) { AppLogger.log(TAG, "isDirectory", e); }
        return false;
    }

    public void deleteForced(String relativePath) throws IOException {
        if (realPath != null) {
            File target = new File(realPath, relativePath).getCanonicalFile();
            if (target.exists()) {
                if (RootChecker.isDeviceRooted()) {
                    runRootCommand("rm -rf " + escapeShellArg(target.getAbsolutePath()));
                    if (target.exists()) throw new IOException("Root delete failed for " + relativePath);
                } else {
                    deleteFileRecursive(target);
                }
            }
            return;
        }
        if (useSaf) {
            DocumentFile root = getSafRoot();
            DocumentFile target = navigateToSaf(root, relativePath);
            if (target == null) return;

            try {
                if (DocumentsContract.deleteDocument(context.getContentResolver(), target.getUri())) {
                    return;
                }
            } catch (Exception ignored) {}

            if (!deleteRecursiveSaf(target)) {
                String path = tryGetPathFromUri(target.getUri());
                if (path != null) {
                    deleteFileRecursive(new File(path));
                    return;
                }
                throw new IOException("SAF delete failed for " + relativePath);
            }
            return;
        }
        if (GlobalVars.legacyPath != null) {
            File f = getSafeFile(relativePath);
            if (f.exists()) deleteFileRecursive(f);
            return;
        }
        throw new IOException("No storage configured");
    }

    public boolean delete(String relativePath) {
        try {
            deleteForced(relativePath);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void deleteFileRecursive(File file) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteFileRecursive(child);
            }
        }
        if (!file.delete()) {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            if (!file.delete() && file.exists()) {
                throw new IOException("Cannot delete " + file.getAbsolutePath());
            }
        }
    }

    private boolean deleteRecursiveSaf(DocumentFile doc) {
        if (doc.isDirectory()) {
            try {
                for (DocumentFile child : doc.listFiles()) deleteRecursiveSaf(child);
            } catch (SecurityException e) {
                AppLogger.log(TAG, "SAF permission lost during delete", e);
            }
        }
        return doc.delete();
    }

    public boolean rename(String oldRelativePath, String newName) {
        return rename(oldRelativePath, newName, false);
    }

    public boolean rename(String oldRelativePath, String newName, boolean overwrite) {
        if (oldRelativePath == null || newName == null) return false;
        newName = sanitizeForSaf(newName);
        if (newName.isEmpty()) return false;
        try {
            if (realPath != null || GlobalVars.legacyPath != null) {
                File oldFile = realPath != null
                        ? new File(realPath, oldRelativePath).getCanonicalFile()
                        : getSafeFile(oldRelativePath);
                String parentPath = oldRelativePath.contains("/") ? oldRelativePath.substring(0, oldRelativePath.lastIndexOf('/')) : "";
                String newRelativePath = parentPath.isEmpty() ? newName : parentPath + "/" + newName;
                File newFile = realPath != null
                        ? new File(realPath, newRelativePath).getCanonicalFile()
                        : new File(GlobalVars.legacyPath, newRelativePath).getCanonicalFile();
                if (newFile.exists()) {
                    if (!overwrite) return false;
                    deleteFileRecursive(newFile);
                }
                if (realPath != null && RootChecker.isDeviceRooted()) {
                    runRootCommand("mv " + escapeShellArg(oldFile.getAbsolutePath()) + " " + escapeShellArg(newFile.getAbsolutePath()));
                    return newFile.exists();
                }
                return oldFile.renameTo(newFile);
            }
            if (useSaf) {
                DocumentFile root = getSafRoot();
                DocumentFile target = navigateToSaf(root, oldRelativePath);
                if (target == null) return false;
                String parentPath = oldRelativePath.contains("/") ? oldRelativePath.substring(0, oldRelativePath.lastIndexOf('/')) : "";
                DocumentFile parent = parentPath.isEmpty() ? root : navigateToSaf(root, parentPath);
                if (parent == null) return false;
                if (overwrite) {
                    DocumentFile existing = parent.findFile(newName);
                    if (existing != null) {
                        if (existing.isDirectory()) deleteRecursiveSaf(existing);
                        else existing.delete();
                    }
                }
                try {
                    Uri resultUri = DocumentsContract.renameDocument(context.getContentResolver(), target.getUri(), newName);
                    if (resultUri != null) return true;
                } catch (Exception ignored) {}
                if (target.isDirectory()) {
                    DocumentFile newDir = parent.createDirectory(newName);
                    if (newDir == null) return false;
                    if (!copyDirectory(target, newDir)) { deleteRecursiveSaf(newDir); return false; }
                    if (!deleteRecursiveSaf(target)) { deleteRecursiveSaf(newDir); return false; }
                } else {
                    DocumentFile newFile = parent.createFile(target.getType() != null ? target.getType() : "application/octet-stream", newName);
                    if (newFile == null) return false;
                    if (!copyFile(target, newFile)) { newFile.delete(); return false; }
                    if (!target.delete()) { newFile.delete(); return false; }
                }
                return true;
            }
        } catch (Exception e) { AppLogger.log(TAG, "rename", e); }
        return false;
    }

    public boolean mkdir(String relativePath) {
        try {
            if (realPath != null) {
                File dir = new File(realPath, relativePath).getCanonicalFile();
                return dir.mkdirs();
            }
            if (useSaf) {
                DocumentFile root = getSafRoot();
                DocumentFile target = root;
                for (String part : relativePath.split("/")) {
                    if (part.isEmpty()) continue;
                    DocumentFile child = target.findFile(part);
                    if (child == null) {
                        child = target.createDirectory(part);
                        if (child == null) return false;
                    } else if (!child.isDirectory()) return false;
                    target = child;
                }
                return true;
            } else if (GlobalVars.legacyPath != null) {
                File dir = getSafeFile(relativePath);
                if (dir.mkdirs()) return true;
                if (RootChecker.isDeviceRooted()) {
                    runCommandWithTimeout("mkdir -p " + escapeShellArg(dir.getAbsolutePath()), 10000);
                    return dir.exists();
                }
                return false;
            }
        } catch (Exception e) { AppLogger.log(TAG, "mkdir", e); }
        return false;
    }

    private DocumentFile getSafRoot() throws IOException {
        if (realPath != null) throw new IOException("Real path available, SAF bypassed");
        if (!isSafRootValid()) throw new IOException("Root URI invalid");
        DocumentFile root = DocumentFile.fromTreeUri(context, GlobalVars.rootUri);
        if (root == null) throw new IOException("Cannot access root URI");
        return root;
    }

    private DocumentFile navigateToSaf(DocumentFile root, String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) return root;
        String[] parts = relativePath.split("/");
        DocumentFile target = root;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (part.equals(".")) continue;
            if (part.equals("..")) {
                DocumentFile parent = target.getParentFile();
                if (parent != null) target = parent;
                else return null;
                continue;
            }
            DocumentFile child = target.findFile(part);
            if (child == null) return null;
            target = child;
        }
        return target;
    }

    private boolean isSafRootValid() {
        try {
            DocumentFile root = DocumentFile.fromTreeUri(context, GlobalVars.rootUri);
            if (root == null) return false;
            return root.canRead();
        } catch (SecurityException e) {
            try {
                context.getContentResolver().takePersistableUriPermission(GlobalVars.rootUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                DocumentFile root = DocumentFile.fromTreeUri(context, GlobalVars.rootUri);
                return root != null && root.canRead();
            } catch (Exception ignored) { return false; }
        }
    }

    private String tryGetPathFromUri(Uri uri) {
        if (uri == null) return null;
        String docId = DocumentsContract.getDocumentId(uri);
        if (docId == null) return null;
        String[] parts = docId.split(":");
        if (parts.length < 2) return null;
        String type = parts[0];
        String id = parts[1];
        if ("primary".equalsIgnoreCase(type)) {
            return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + id;
        }
        return "/storage/" + type + "/" + id;
    }

    private File getSafeFile(String relativePath) throws IOException {
        if (realPath != null) {
            File root = new File(realPath).getCanonicalFile();
            File target = new File(root, relativePath).getCanonicalFile();
            if (!target.toPath().startsWith(root.toPath())) {
                throw new SecurityException("Path traversal attempt: " + relativePath);
            }
            return target;
        }
        if (!isLegacyMode()) throw new IOException("Not in legacy mode");
        File root = new File(GlobalVars.legacyPath).getCanonicalFile();
        File target = new File(root, relativePath).getCanonicalFile();
        if (!target.toPath().startsWith(root.toPath())) {
            throw new SecurityException("Path traversal attempt: " + relativePath);
        }
        return target;
    }

    private static String sanitizeForSaf(String name) {
        String s = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (s.isEmpty() || s.equals(".") || s.equals("..")) s = "renamed_file";
        if (s.length() > 200) s = s.substring(0, 200);
        return s;
    }

    private boolean copyFile(DocumentFile source, DocumentFile dest) {
        try (InputStream in = context.getContentResolver().openInputStream(source.getUri());
             OutputStream out = context.getContentResolver().openOutputStream(dest.getUri())) {
            byte[] buf = new byte[8192]; int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            return true;
        } catch (IOException e) { return false; }
    }

    private boolean copyDirectory(DocumentFile sourceDir, DocumentFile destDir) {
        try {
            for (DocumentFile child : sourceDir.listFiles()) {
                if (child.isDirectory()) {
                    DocumentFile newChildDir = destDir.createDirectory(child.getName());
                    if (newChildDir == null) return false;
                    if (!copyDirectory(child, newChildDir)) return false;
                } else {
                    DocumentFile newFile = destDir.createFile(child.getType() != null ? child.getType() : "application/octet-stream", child.getName());
                    if (newFile == null) return false;
                    if (!copyFile(child, newFile)) return false;
                }
            }
        } catch (SecurityException e) {
            AppLogger.log(TAG, "SAF permission lost during copy", e);
            return false;
        }
        return true;
    }

    private static String escapeShellArg(String arg) {
        return "'" + arg.replace("'", "'\\''") + "'";
    }

    private static void runRootCommand(String command) throws IOException {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            p.waitFor();
            if (p.exitValue() != 0) throw new IOException("Root command failed: " + command);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private List<String> runCommandWithTimeout(String command, long timeoutMs) {
        if (!RootChecker.isDeviceRooted()) return null;
        final Process[] procHolder = new Process[1];
        final List<String> result = new ArrayList<>();
        final AtomicBoolean finished = new AtomicBoolean(false);
        Thread worker = new Thread(() -> {
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
                procHolder[0] = p;
                StreamGobbler outGobbler = new StreamGobbler(p.getInputStream());
                StreamGobbler errGobbler = new StreamGobbler(p.getErrorStream());
                outGobbler.start();
                errGobbler.start();
                p.waitFor();
                outGobbler.join(1000);
                errGobbler.join(1000);
                result.addAll(outGobbler.getLines());
                finished.set(true);
            } catch (Exception ignored) {}
        });
        worker.start();
        try {
            worker.join(timeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (!finished.get()) {
            if (procHolder[0] != null) procHolder[0].destroyForcibly();
            worker.interrupt();
            return null;
        }
        return result.isEmpty() ? null : result;
    }

    private String runCommandSingleWithTimeout(String command, long timeoutMs) {
        List<String> lines = runCommandWithTimeout(command, timeoutMs);
        return (lines != null && !lines.isEmpty()) ? lines.get(0) : null;
    }

    private static class RootInputStream extends InputStream {
        private final Process process;
        private final InputStream input;

        RootInputStream(File file) throws IOException {
            try {
                process = new ProcessBuilder("su", "-c", "cat " + escapeShellArg(file.getAbsolutePath())).start();
                input = process.getInputStream();
            } catch (IOException e) {
                throw new IOException("Cannot start root process", e);
            }
        }

        @Override
        public int read() throws IOException {
            int b = input.read();
            if (b == -1) {
                closeProcess();
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int bytesRead = input.read(b, off, len);
            if (bytesRead == -1) {
                closeProcess();
            }
            return bytesRead;
        }

        @Override
        public void close() throws IOException {
            super.close();
            closeProcess();
        }

        private void closeProcess() {
            try { input.close(); } catch (IOException ignored) {}
            process.destroy();
        }
    }

    private static class StreamGobbler extends Thread {
        private final InputStream is;
        private final List<String> lines = new ArrayList<>();
        StreamGobbler(InputStream is) { this.is = is; }
        @Override public void run() {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = br.readLine()) != null) lines.add(line);
            } catch (IOException ignored) {}
        }
        List<String> getLines() { return lines; }
    }

    public static File selectBestTempStorage(long requiredSpace) {
        List<File> candidates = new ArrayList<>();
        File internal = Environment.getExternalStorageDirectory();
        if (internal != null && internal.canWrite()) candidates.add(internal);
        File[] externalDirs = GlobalVars.appContext.getExternalFilesDirs(null);
        for (File dir : externalDirs) {
            if (dir != null && !dir.equals(internal) && dir.canWrite()) {
                String path = dir.getAbsolutePath();
                int idx = path.indexOf("/Android/");
                if (idx > 0) {
                    File root = new File(path.substring(0, idx));
                    if (root.exists() && root.canWrite()) candidates.add(root);
                } else {
                    candidates.add(dir);
                }
            }
        }
        candidates.sort((a, b) -> Long.compare(b.getFreeSpace(), a.getFreeSpace()));
        for (File cand : candidates) {
            if (cand.getFreeSpace() >= requiredSpace) {
                File tempDir = new File(cand, "Web Server");
                if (!tempDir.exists()) tempDir.mkdirs();
                return tempDir;
            }
        }
        File fallback = new File(internal, "Web Server");
        if (!fallback.exists()) fallback.mkdirs();
        return fallback;
    }
}