package www.webserver.com;

import android.util.Log;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.JSONException;
import org.json.JSONObject;

public class ChunkedUploadManager {
    private static final String TAG = "ChunkedUpload";
    public static final long CHUNK_SIZE = 10L * 1024L * 1024L;

    private final StorageHelper storage;
    private final String basePath;

    public ChunkedUploadManager(StorageHelper storage, String basePath) {
        this.storage = storage;
        this.basePath = basePath;
    }

    public TempUploadFolder createTempFolder(String uuid, String originalName, long totalSize, int totalChunks) throws IOException {
        String folderRelPath = basePath + uuid;
        if (!storage.exists(folderRelPath)) {
            if (!storage.mkdir(folderRelPath)) {
                throw new IOException("Cannot create backup folder: " + folderRelPath);
            }
        }
        JSONObject manifest = new JSONObject();
        try {
            manifest.put("originalName", originalName);
            manifest.put("totalChunks", totalChunks);
            manifest.put("totalSize", totalSize);
        } catch (JSONException e) {}

        String manifestPath = folderRelPath + "/manifest.json";
        try (OutputStream os = storage.openOutputStream(manifestPath, "application/json")) {
            os.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
            syncIfPossible(os);
        }

        return new TempUploadFolder(storage, folderRelPath);
    }

    public void reassemble(TempUploadFolder folder, String finalName, boolean overwrite) throws IOException {
        String finalRelPath = basePath + finalName;
        if (storage.exists(finalRelPath)) {
            if (!overwrite) {
                finalName = UploadHandler.resolveFileName(storage, basePath, finalName, false);
                finalRelPath = basePath + finalName;
            } else {
                if (!storage.delete(finalRelPath)) {
                    throw new IOException("Cannot overwrite file: " + finalRelPath);
                }
            }
        }

        File chunkSourceDir;
        if (GlobalVars.rootUri != null) {
            File tempRoot = UploadHandler.getTempChunkRootForFolder(folder.getFolderRelPath());
            if (tempRoot == null) {
                throw new IOException("No temp location found for chunks (upload may have been interrupted)");
            }
            chunkSourceDir = new File(tempRoot, folder.getFolderRelPath());
        } else {
            if (GlobalVars.legacyPath == null)
                throw new IOException("No storage path configured");
            chunkSourceDir = new File(GlobalVars.legacyPath, folder.getFolderRelPath());
        }

        if (!chunkSourceDir.exists()) {
            throw new IOException("Chunk source directory missing: " + chunkSourceDir);
        }

        int totalChunks = folder.getTotalChunks();
        long totalSize = folder.getTotalSize();

        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < totalChunks; i++) {
            long expected = (i < totalChunks - 1) ? CHUNK_SIZE : (totalSize - (long)(totalChunks - 1) * CHUNK_SIZE);
            String chunkFile = String.format("chunk_%05d.dat", i);
            File chunkFileObj = new File(chunkSourceDir, chunkFile);
            if (!chunkFileObj.exists() || chunkFileObj.length() != expected) {
                missing.add(i);
            }
        }
        if (!missing.isEmpty()) {
            StringBuilder sb = new StringBuilder("Missing chunks: ");
            for (int i = 0; i < missing.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(missing.get(i));
            }
            throw new IOException(sb.toString());
        }

        int bufSize = Math.max(GlobalVars.bufferSizeKB * 1024, 4096);
        try (OutputStream os = storage.openOutputStream(finalRelPath, "application/octet-stream");
             BufferedOutputStream bos = new BufferedOutputStream(os, bufSize)) {
            byte[] buf = new byte[bufSize];
            for (int i = 0; i < totalChunks; i++) {
                String chunkFile = String.format("chunk_%05d.dat", i);
                File chunkFileObj = new File(chunkSourceDir, chunkFile);
                try (InputStream is = new FileInputStream(chunkFileObj)) {
                    int bytesRead;
                    while ((bytesRead = is.read(buf)) != -1) {
                        bos.write(buf, 0, bytesRead);
                    }
                }
            }
            bos.flush();
        }

        if (!deleteRecursive(chunkSourceDir)) {
            Log.w(TAG, "Could not delete chunk directory: " + chunkSourceDir);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new DeleteRetryRunnable(chunkSourceDir), 500);
        }
        UploadHandler.removeTempLocationMapping(folder.getFolderRelPath());
        deleteUploadFolder(folder);
    }

    private static class DeleteRetryRunnable implements Runnable {
        private final File chunkSourceDir;
        DeleteRetryRunnable(File chunkSourceDir) { this.chunkSourceDir = chunkSourceDir; }
        @Override public void run() {
            if (!deleteRecursiveStatic(chunkSourceDir)) {
                Log.e(TAG, "Final cleanup failed: " + chunkSourceDir);
            }
        }
    }

    private boolean deleteRecursive(File file) {
        return deleteRecursiveStatic(file);
    }

    private static boolean deleteRecursiveStatic(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursiveStatic(child);
                }
            }
        }
        return file.delete();
    }

    private void deleteUploadFolder(TempUploadFolder folder) {
        String folderPath = folder.getFolderRelPath();
        try {
            storage.deleteForced(folderPath);
        } catch (Exception e) {
            AppLogger.log(TAG, "Failed to delete upload folder: " + folderPath, e);
        }
    }

    public static class TempUploadFolder {
        private final StorageHelper storage;
        private final String folderRelPath;
        private final Object manifestLock = new Object();
        private JSONObject manifest;
        private final long totalSize;

        public TempUploadFolder(StorageHelper storage, String folderRelPath) throws IOException {
            this.storage = storage;
            this.folderRelPath = folderRelPath;
            long parsedSize = -1;
            String folderName = folderRelPath.substring(folderRelPath.lastIndexOf('/') + 1);
            int lastUnderscore = folderName.lastIndexOf('_');
            if (lastUnderscore != -1) {
                try {
                    parsedSize = Long.parseLong(folderName.substring(lastUnderscore + 1));
                } catch (NumberFormatException ignored) {}
            }
            synchronized (manifestLock) {
                loadManifest();
                if (parsedSize <= 0) {
                    parsedSize = manifest.optLong("totalSize", 0);
                }
                this.totalSize = parsedSize;
                if (this.totalSize <= 0) {
                    throw new IOException("Cannot determine totalSize for upload folder: " + folderRelPath);
                }
            }
        }

        public String getFolderRelPath() { return folderRelPath; }
        public long getTotalSize() { return totalSize; }
        public int getTotalChunks() { synchronized(manifestLock) { return manifest.optInt("totalChunks", 0); } }
        public String getOriginalName() { synchronized(manifestLock) { return manifest.optString("originalName", "uploaded_file"); } }

        public void setOriginalName(String name) {
            synchronized(manifestLock) {
                try { manifest.put("originalName", name); } catch (JSONException e) {}
                saveManifest();
            }
        }

        public void setTotalChunks(int totalChunks) {
            synchronized(manifestLock) {
                try { manifest.put("totalChunks", totalChunks); } catch (JSONException e) {}
                saveManifest();
            }
        }

        public List<Integer> getCompletedChunks() {
            synchronized(manifestLock) {
                int totalChunks = getTotalChunks();
                if (totalChunks <= 0) return Collections.emptyList();
                File chunkDir;
                if (GlobalVars.rootUri != null) {
                    File tempRoot = UploadHandler.getTempChunkRootForFolder(folderRelPath);
                    if (tempRoot == null) return Collections.emptyList();
                    chunkDir = new File(tempRoot, folderRelPath);
                } else {
                    chunkDir = new File(GlobalVars.legacyPath, folderRelPath);
                }
                if (!chunkDir.exists()) return Collections.emptyList();
                List<Integer> complete = new ArrayList<>();
                File[] files = chunkDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isFile() && f.getName().startsWith("chunk_") && f.getName().endsWith(".dat")) {
                            int idx = chunkIndexFromName(f.getName());
                            if (idx >= 0 && idx < totalChunks) {
                                long expected = (idx < totalChunks - 1) ? CHUNK_SIZE : (totalSize - (long)(totalChunks - 1) * CHUNK_SIZE);
                                if (f.length() == expected) {
                                    complete.add(idx);
                                }
                            }
                        }
                    }
                }
                Collections.sort(complete);
                return complete;
            }
        }

        public boolean allChunksOnDisk() {
            synchronized(manifestLock) {
                int total = getTotalChunks();
                File chunkDir;
                if (GlobalVars.rootUri != null) {
                    File tempRoot = UploadHandler.getTempChunkRootForFolder(folderRelPath);
                    if (tempRoot == null) return false;
                    chunkDir = new File(tempRoot, folderRelPath);
                } else {
                    chunkDir = new File(GlobalVars.legacyPath, folderRelPath);
                }
                if (!chunkDir.exists()) return false;
                for (int i = 0; i < total; i++) {
                    String chunkFile = String.format("chunk_%05d.dat", i);
                    File chunkFileObj = new File(chunkDir, chunkFile);
                    if (!chunkFileObj.exists()) return false;
                    long expected = (i < total - 1) ? CHUNK_SIZE : (totalSize - (long)(total - 1) * CHUNK_SIZE);
                    if (chunkFileObj.length() != expected) return false;
                }
                return true;
            }
        }

        private void loadManifest() {
            String manifestPath = folderRelPath + "/manifest.json";
            try {
                if (storage.exists(manifestPath)) {
                    try (InputStream is = storage.openInputStream(manifestPath)) {
                        String content = new String(readAll(is), StandardCharsets.UTF_8);
                        manifest = new JSONObject(content);
                    }
                } else {
                    manifest = new JSONObject();
                }
            } catch (Exception e) {
                manifest = new JSONObject();
            }
        }

        public void saveManifest() {
            String manifestPath = folderRelPath + "/manifest.json";
            synchronized(manifestLock) {
                try (OutputStream os = storage.openOutputStream(manifestPath, "application/json")) {
                    try { manifest.put("totalSize", totalSize); } catch (JSONException ignored) {}
                    os.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
                    syncIfPossible(os);
                } catch (IOException e) {
                    Log.e(TAG, "Failed to save manifest", e);
                }
            }
        }

        private byte[] readAll(InputStream is) throws IOException {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] data = new byte[4096];
            int n;
            while ((n = is.read(data)) != -1) buf.write(data, 0, n);
            return buf.toByteArray();
        }

        private static int chunkIndexFromName(String name) {
            if (name == null || !name.startsWith("chunk_") || !name.endsWith(".dat")) return -1;
            try {
                return Integer.parseInt(name.substring(6, 11));
            } catch (Exception e) {
                return -1;
            }
        }
    }

    private static void syncIfPossible(OutputStream os) {
        if (os instanceof FileOutputStream) {
            try {
                ((FileOutputStream) os).getFD().sync();
            } catch (IOException ignored) {}
        }
    }
}