package www.webserver.com;

import android.os.Environment;
import android.net.Uri;
import org.json.JSONObject;
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class UploadHandler {

    private static final String TEMP_LOCATION_FILE = "chunk_temp_location.json";
    private static final Map<String, File> activeTempFolders = new HashMap<>();
    private static final Object tempLock = new Object();
    private static final ConcurrentHashMap<String, ReentrantLock> tokenLocks = new ConcurrentHashMap<>();

    public static String handlePost(String urlPath, long contentLength, String contentType,
                                    InputStream input, String token) throws IOException {
        return handlePost(urlPath, contentLength, contentType, input, token, false);
    }

    public static String handlePost(String urlPath, long contentLength, String contentType,
                                    InputStream input, String token, boolean overwriteFromQuery) throws IOException {
        String decodedPath = Uri.decode(urlPath);
        decodedPath = sanitizePath(decodedPath);
        int qIdx = decodedPath.indexOf('?');
        if (qIdx != -1) decodedPath = decodedPath.substring(0, qIdx);

        StorageHelper storage = new StorageHelper(GlobalVars.appContext);
        String relativePath = decodedPath;
        if (relativePath.startsWith("/")) relativePath = relativePath.substring(1);
        if (!relativePath.isEmpty() && !relativePath.endsWith("/")) relativePath += "/";

        String boundary = extractBoundary(contentType);
        if (boundary == null) throw new IOException("Missing boundary");

        if (token != null && !token.isEmpty()) {
            ReentrantLock lock = tokenLocks.computeIfAbsent(token, k -> new ReentrantLock());
            lock.lock();
            try {
                return processChunksUpload(input, contentLength, boundary, storage, relativePath, token);
            } finally {
                lock.unlock();
                tokenLocks.remove(token, lock);
            }
        } else {
            return processNormalUpload(input, contentLength, boundary, storage, relativePath, overwriteFromQuery);
        }
    }

    private static String processNormalUpload(InputStream input, long contentLength,
                                              String boundary, StorageHelper storage,
                                              String basePath, boolean initialOverwrite) throws IOException {
        byte[] boundaryDelim = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        int bufferSize = Math.max(GlobalVars.bufferSizeKB * 1024, 4096);
        byte[] buffer = new byte[bufferSize];

        long totalRead = 0;
        boolean finalBoundarySeen = false;
        boolean inPart = false, inHeaders = true, inFileData = false, inFieldData = false;
        String finalName = null;
        boolean overwrite = initialOverwrite;

        OutputStream fileOut = null;
        BufferedOutputStream bufferedOut = null;
        ByteArrayOutputStream headerBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream fieldValueBuffer = new ByteArrayOutputStream();
        byte[] overlap = null;
        String fieldName = null;

        try {
            while (totalRead < contentLength && !finalBoundarySeen) {
                int toRead = (int) Math.min(buffer.length, contentLength - totalRead);
                int bytesRead = input.read(buffer, 0, toRead);
                if (bytesRead == -1) break;
                totalRead += bytesRead;

                byte[] chunk = buffer;
                int chunkLen = bytesRead;
                if (overlap != null) {
                    chunk = new byte[overlap.length + bytesRead];
                    System.arraycopy(overlap, 0, chunk, 0, overlap.length);
                    System.arraycopy(buffer, 0, chunk, overlap.length, bytesRead);
                    chunkLen = chunk.length;
                    overlap = null;
                }

                int pos = 0;
                while (pos < chunkLen && !finalBoundarySeen) {
                    if (!inPart) {
                        int idx = indexOfBoundary(chunk, pos, chunkLen, boundaryDelim);
                        if (idx != -1) {
                            pos = idx + boundaryDelim.length;
                            if (pos + 1 < chunkLen && chunk[pos] == '-' && chunk[pos + 1] == '-') {
                                finalBoundarySeen = true;
                                break;
                            }
                            if (pos < chunkLen && chunk[pos] == '\r') pos++;
                            if (pos < chunkLen && chunk[pos] == '\n') pos++;
                            inPart = true; inHeaders = true; inFileData = false; inFieldData = false;
                            headerBuffer.reset(); fieldValueBuffer.reset(); finalName = null;
                        } else {
                            int keep = Math.min(boundaryDelim.length - 1, chunkLen - pos);
                            if (keep > 0) {
                                overlap = new byte[keep];
                                System.arraycopy(chunk, chunkLen - keep, overlap, 0, keep);
                            }
                            pos = chunkLen;
                        }
                    } else if (inHeaders) {
                        while (pos < chunkLen) {
                            byte b = chunk[pos++];
                            headerBuffer.write(b);
                            if (headerBuffer.size() > 65536) throw new IOException("Part header too large");
                            byte[] hb = headerBuffer.toByteArray();
                            int len = hb.length;
                            if (len >= 4 && hb[len-4]=='\r' && hb[len-3]=='\n' && hb[len-2]=='\r' && hb[len-1]=='\n') {
                                String headers = new String(hb, 0, len-4, StandardCharsets.ISO_8859_1);
                                String disposition = getHeaderValue(headers, "Content-Disposition");
                                if (disposition != null) {
                                    fieldName = getDispParam(disposition, "name");
                                    String dispFilename = getDispParam(disposition, "filename");
                                    if (dispFilename != null) {
                                        String safeName = sanitizeFilename(dispFilename);
                                        finalName = resolveFileName(storage, basePath, safeName, overwrite);
                                        fileOut = storage.openOutputStream(basePath + finalName, "application/octet-stream");
                                        bufferedOut = new BufferedOutputStream(fileOut, bufferSize);
                                        inFileData = true; inHeaders = false; inFieldData = false;
                                        break;
                                    } else if (fieldName != null) {
                                        inFieldData = true; inFileData = false; inHeaders = false;
                                        fieldValueBuffer.reset();
                                        break;
                                    }
                                }
                                inPart = false;
                                break;
                            }
                        }
                    } else if (inFieldData) {
                        int boundaryIdx = indexOfBoundary(chunk, pos, chunkLen, boundaryDelim);
                        if (boundaryIdx != -1) {
                            int endIdx = boundaryIdx;
                            if (endIdx > pos && chunk[endIdx-1] == '\n') {
                                endIdx--;
                                if (endIdx > pos && chunk[endIdx-1] == '\r') endIdx--;
                            }
                            fieldValueBuffer.write(chunk, pos, endIdx - pos);
                            String value = new String(fieldValueBuffer.toByteArray(), StandardCharsets.UTF_8);
                            if ("overwrite".equals(fieldName)) overwrite = "true".equals(value);
                            pos = boundaryIdx + boundaryDelim.length;
                            if (pos + 1 < chunkLen && chunk[pos] == '-' && chunk[pos + 1] == '-') {
                                finalBoundarySeen = true;
                                break;
                            }
                            if (pos < chunkLen && chunk[pos] == '\r') pos++;
                            if (pos < chunkLen && chunk[pos] == '\n') pos++;
                            inPart = false;
                        } else {
                            int safeLen = chunkLen - pos;
                            int boundaryPrefix = boundaryDelim.length - 1;
                            if (safeLen > boundaryPrefix) {
                                fieldValueBuffer.write(chunk, pos, safeLen - boundaryPrefix);
                                overlap = new byte[boundaryPrefix];
                                System.arraycopy(chunk, chunkLen - boundaryPrefix, overlap, 0, boundaryPrefix);
                            } else {
                                overlap = new byte[safeLen];
                                System.arraycopy(chunk, pos, overlap, 0, safeLen);
                            }
                            pos = chunkLen;
                        }
                    } else if (inFileData) {
                        int boundaryIdx = indexOfBoundary(chunk, pos, chunkLen, boundaryDelim);
                        if (boundaryIdx != -1) {
                            int endIdx = boundaryIdx;
                            if (endIdx > pos && chunk[endIdx-1] == '\n') {
                                endIdx--;
                                if (endIdx > pos && chunk[endIdx-1] == '\r') endIdx--;
                            }
                            bufferedOut.write(chunk, pos, endIdx - pos);
                            bufferedOut.flush();
                            bufferedOut.close();
                            bufferedOut = null;
                            fileOut = null;
                            pos = boundaryIdx + boundaryDelim.length;
                            if (pos + 1 < chunkLen && chunk[pos] == '-' && chunk[pos + 1] == '-') {
                                finalBoundarySeen = true;
                                break;
                            }
                            if (pos < chunkLen && chunk[pos] == '\r') pos++;
                            if (pos < chunkLen && chunk[pos] == '\n') pos++;
                            inPart = false;
                            inFileData = false;
                        } else {
                            bufferedOut.write(chunk, pos, chunkLen - pos);
                            pos = chunkLen;
                        }
                    }
                }
            }
            if (finalName == null) throw new IOException("No file part found in upload");
            return finalName;
        } finally {
            if (bufferedOut != null) try { bufferedOut.close(); } catch (IOException ignored) {}
            if (fileOut != null) try { fileOut.close(); } catch (IOException ignored) {}
        }
    }

    private static String processChunksUpload(InputStream input, long contentLength,
                                              String boundary, StorageHelper storage,
                                              String basePath, String folderName) throws IOException {
        String folderPath = basePath + folderName;
        if (!storage.exists(folderPath)) {
            storage.mkdir(folderPath);
        }
        cancelTimeout(folderPath);

        ChunkedUploadManager.TempUploadFolder folder = new ChunkedUploadManager.TempUploadFolder(storage, folderPath);
        ensureTempLocation(folder.getFolderRelPath(), folder.getTotalSize());

        byte[] boundaryDelim = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        int bufferSize = Math.max(GlobalVars.bufferSizeKB * 1024, 4096);
        byte[] buffer = new byte[bufferSize];

        long totalRead = 0;
        boolean finalBoundarySeen = false;
        boolean inPart = false, inHeaders = true, inFileData = false, inFieldData = false;

        String currentChunkFile = null;
        ByteArrayOutputStream chunkDataBuffer = null;

        ByteArrayOutputStream headerBuffer = new ByteArrayOutputStream();
        byte[] overlap = null;

        try {
            while (totalRead < contentLength && !finalBoundarySeen) {
                int toRead = (int) Math.min(buffer.length, contentLength - totalRead);
                int bytesRead = input.read(buffer, 0, toRead);
                if (bytesRead == -1) break;
                totalRead += bytesRead;

                byte[] chunk = buffer;
                int chunkLen = bytesRead;
                if (overlap != null) {
                    chunk = new byte[overlap.length + bytesRead];
                    System.arraycopy(overlap, 0, chunk, 0, overlap.length);
                    System.arraycopy(buffer, 0, chunk, overlap.length, bytesRead);
                    chunkLen = chunk.length;
                    overlap = null;
                }

                int pos = 0;
                while (pos < chunkLen && !finalBoundarySeen) {
                    if (!inPart) {
                        int idx = indexOfBoundary(chunk, pos, chunkLen, boundaryDelim);
                        if (idx != -1) {
                            pos = idx + boundaryDelim.length;
                            if (pos + 1 < chunkLen && chunk[pos] == '-' && chunk[pos + 1] == '-') {
                                finalBoundarySeen = true;
                                break;
                            }
                            if (pos < chunkLen && chunk[pos] == '\r') pos++;
                            if (pos < chunkLen && chunk[pos] == '\n') pos++;
                            inPart = true; inHeaders = true; inFileData = false; inFieldData = false;
                            headerBuffer.reset();
                            if (chunkDataBuffer != null) chunkDataBuffer = null;
                        } else {
                            int keep = Math.min(boundaryDelim.length - 1, chunkLen - pos);
                            if (keep > 0) {
                                overlap = new byte[keep];
                                System.arraycopy(chunk, chunkLen - keep, overlap, 0, keep);
                            }
                            pos = chunkLen;
                        }
                    } else if (inHeaders) {
                        while (pos < chunkLen) {
                            byte b = chunk[pos++];
                            headerBuffer.write(b);
                            if (headerBuffer.size() > 65536) throw new IOException("Part header too large");
                            byte[] hb = headerBuffer.toByteArray();
                            int len = hb.length;
                            if (len >= 4 && hb[len-4]=='\r' && hb[len-3]=='\n' && hb[len-2]=='\r' && hb[len-1]=='\n') {
                                String headers = new String(hb, 0, len-4, StandardCharsets.ISO_8859_1);
                                String disposition = getHeaderValue(headers, "Content-Disposition");
                                if (disposition != null) {
                                    String dispFilename = getDispParam(disposition, "filename");
                                    if (dispFilename != null && dispFilename.startsWith("chunk_") && dispFilename.endsWith(".dat")) {
                                        currentChunkFile = dispFilename;
                                        chunkDataBuffer = new ByteArrayOutputStream();
                                        inFileData = true; inHeaders = false; inFieldData = false;
                                        break;
                                    }
                                }
                                inPart = false;
                                break;
                            }
                        }
                    } else if (inFileData) {
                        int boundaryIdx = indexOfBoundary(chunk, pos, chunkLen, boundaryDelim);
                        if (boundaryIdx != -1) {
                            int endIdx = boundaryIdx;
                            if (endIdx > pos && chunk[endIdx-1] == '\n') {
                                endIdx--;
                                if (endIdx > pos && chunk[endIdx-1] == '\r') endIdx--;
                            }
                            chunkDataBuffer.write(chunk, pos, endIdx - pos);
                            writeChunkWithRetry(currentChunkFile, folder, chunkDataBuffer.toByteArray(), storage);
                            chunkDataBuffer = null;
                            pos = boundaryIdx + boundaryDelim.length;
                            if (pos + 1 < chunkLen && chunk[pos] == '-' && chunk[pos + 1] == '-') {
                                finalBoundarySeen = true;
                                break;
                            }
                            if (pos < chunkLen && chunk[pos] == '\r') pos++;
                            if (pos < chunkLen && chunk[pos] == '\n') pos++;
                            inPart = false;
                            inFileData = false;
                        } else {
                            chunkDataBuffer.write(chunk, pos, chunkLen - pos);
                            pos = chunkLen;
                        }
                    } else {
                        inPart = false;
                    }
                }
            }
        } finally {
            if (chunkDataBuffer != null && currentChunkFile != null) {
                try {
                    writeChunkWithRetry(currentChunkFile, folder, chunkDataBuffer.toByteArray(), storage);
                } catch (IOException ignored) {
                }
                chunkDataBuffer = null;
            }
        }

        return "__INCOMPLETE__";
    }

    public static String handleFinalize(String basePath, String folderName, StorageHelper storage, boolean overwrite) throws IOException {
        String folderPath = basePath + folderName;
        if (!storage.exists(folderPath)) {
            throw new IOException("Upload folder not found");
        }
        ChunkedUploadManager.TempUploadFolder folder = new ChunkedUploadManager.TempUploadFolder(storage, folderPath);

        if (!folder.allChunksOnDisk()) {
            List<Integer> missing = getMissingChunks(folder);
            StringBuilder needed = new StringBuilder();
            for (int i = 0; i < missing.size(); i++) {
                if (i > 0) needed.append(",");
                needed.append(missing.get(i));
            }
            rescheduleTimeout(folderPath, folder, storage, basePath);
            throw new IOException("MISSING:" + needed.toString());
        }

        cancelTimeout(folderPath);
        String safeName = folder.getOriginalName();
        String finalName = resolveFileName(storage, basePath, safeName, overwrite);
        ChunkedUploadManager mgr = new ChunkedUploadManager(storage, basePath);
        mgr.reassemble(folder, finalName, overwrite);
        return "COMPLETE";
    }

    private static List<Integer> getMissingChunks(ChunkedUploadManager.TempUploadFolder folder) {
        List<Integer> missing = new ArrayList<>();
        int total = folder.getTotalChunks();
        long totalSize = folder.getTotalSize();
        File chunkDir;
        if (GlobalVars.rootUri != null) {
            File tempRoot = getTempChunkRootForFolder(folder.getFolderRelPath());
            if (tempRoot == null) {
                for (int i = 0; i < total; i++) missing.add(i);
                return missing;
            }
            chunkDir = new File(tempRoot, folder.getFolderRelPath());
        } else {
            chunkDir = new File(GlobalVars.legacyPath, folder.getFolderRelPath());
        }
        if (!chunkDir.exists()) {
            for (int i = 0; i < total; i++) missing.add(i);
            return missing;
        }
        for (int i = 0; i < total; i++) {
            long expected = (i < total - 1) ? ChunkedUploadManager.CHUNK_SIZE : (totalSize - (long)(total - 1) * ChunkedUploadManager.CHUNK_SIZE);
            String chunkFile = String.format("chunk_%05d.dat", i);
            File f = new File(chunkDir, chunkFile);
            if (!f.exists() || f.length() != expected) {
                missing.add(i);
            }
        }
        return missing;
    }

    private static final long MISSING_CHUNK_TIMEOUT_MS = 30_000L;
    private static final Map<String, TimerTask> pendingFinalizeTimers = new HashMap<>();
    private static final Timer chunkTimer = new Timer("ChunkTimeout", true);

    private static synchronized void rescheduleTimeout(String folderPath,
                                                       ChunkedUploadManager.TempUploadFolder folder,
                                                       StorageHelper storage,
                                                       String basePath) {
        cancelTimeout(folderPath);
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                String token = folderPath.substring(folderPath.lastIndexOf('/') + 1);
                ReentrantLock lock = tokenLocks.get(token);
                if (lock != null) {
                    lock.lock();
                }
                try {
                    synchronized (UploadHandler.class) {
                        pendingFinalizeTimers.remove(folderPath);
                    }
                    storage.delete(folderPath);
                    removeTempLocationMapping(folder.getFolderRelPath());
                } catch (Exception ignored) {
                } finally {
                    if (lock != null) {
                        lock.unlock();
                    }
                }
            }
        };
        pendingFinalizeTimers.put(folderPath, task);
        chunkTimer.schedule(task, MISSING_CHUNK_TIMEOUT_MS);
    }

    static synchronized void cancelTimeout(String folderPath) {
        TimerTask task = pendingFinalizeTimers.remove(folderPath);
        if (task != null) {
            task.cancel();
        }
    }

    private static void ensureTempLocation(String folderRelPath, long totalFileSize) throws IOException {
        synchronized (activeTempFolders) {
            if (activeTempFolders.containsKey(folderRelPath)) return;
            File tempRoot = loadOrSelectTempRoot(folderRelPath, totalFileSize);
            if (tempRoot == null) throw new IOException("No suitable storage with enough free space");
            activeTempFolders.put(folderRelPath, tempRoot);
            saveTempLocationMapping(folderRelPath, tempRoot.getAbsolutePath(), totalFileSize);
        }
    }

    public static File getTempChunkRootForFolder(String folderRelPath) {
        synchronized (activeTempFolders) {
            File cached = activeTempFolders.get(folderRelPath);
            if (cached != null) return cached;
        }
        File persistent = loadTempLocationMapping(folderRelPath);
        if (persistent != null) {
            synchronized (activeTempFolders) {
                activeTempFolders.put(folderRelPath, persistent);
            }
        }
        return persistent;
    }

    private static File loadOrSelectTempRoot(String folderRelPath, long totalFileSize) {
        File existing = loadTempLocationMapping(folderRelPath);
        if (existing != null && existing.exists() && existing.canWrite()) {
            return existing;
        }
        return StorageHelper.selectBestTempStorage(totalFileSize * 2);
    }

    private static File getTempLocationFile() {
        File dir = new File(Environment.getExternalStorageDirectory(), "Web Server");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, TEMP_LOCATION_FILE);
    }

    private static void saveTempLocationMapping(String folderRelPath, String tempRootPath, long totalSize) {
        synchronized (tempLock) {
            try {
                File jsonFile = getTempLocationFile();
                JSONObject root;
                if (jsonFile.exists()) {
                    String content = new String(readAll(new FileInputStream(jsonFile)), StandardCharsets.UTF_8);
                    root = new JSONObject(content);
                } else {
                    root = new JSONObject();
                }
                JSONObject entry = new JSONObject();
                entry.put("path", tempRootPath);
                entry.put("totalSize", totalSize);
                root.put(folderRelPath, entry);
                try (FileOutputStream fos = new FileOutputStream(jsonFile)) {
                    fos.write(root.toString().getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                AppLogger.log("UploadHandler", "Failed to save temp location mapping", e);
            }
        }
    }

    private static File loadTempLocationMapping(String folderRelPath) {
        synchronized (tempLock) {
            try {
                File jsonFile = getTempLocationFile();
                if (!jsonFile.exists()) return null;
                String content = new String(readAll(new FileInputStream(jsonFile)), StandardCharsets.UTF_8);
                JSONObject root = new JSONObject(content);
                if (!root.has(folderRelPath)) return null;
                JSONObject entry = root.getJSONObject(folderRelPath);
                String path = entry.getString("path");
                File dir = new File(path);
                if (dir.exists() && dir.canWrite()) {
                    return dir;
                }
            } catch (Exception e) {
                AppLogger.log("UploadHandler", "Failed to load temp location mapping", e);
            }
        }
        return null;
    }

    public static void removeTempLocationMapping(String folderRelPath) {
        synchronized (activeTempFolders) {
            activeTempFolders.remove(folderRelPath);
        }
        synchronized (tempLock) {
            try {
                File jsonFile = getTempLocationFile();
                if (!jsonFile.exists()) return;
                String content = new String(readAll(new FileInputStream(jsonFile)), StandardCharsets.UTF_8);
                JSONObject root = new JSONObject(content);
                root.remove(folderRelPath);
                try (FileOutputStream fos = new FileOutputStream(jsonFile)) {
                    fos.write(root.toString().getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                AppLogger.log("UploadHandler", "Failed to remove temp location mapping", e);
            }
        }
    }

    private static byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        return baos.toByteArray();
    }

    private static void writeChunkWithRetry(String chunkFileName,
                                            ChunkedUploadManager.TempUploadFolder folder,
                                            byte[] data,
                                            StorageHelper storage) throws IOException {
        int idx = chunkIndexFromName(chunkFileName);
        if (idx < 0 || idx >= folder.getTotalChunks()) {
            throw new IOException("Invalid chunk index: " + chunkFileName);
        }
        IOException lastException = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            OutputStream out = null;
            try {
                out = openChunkOutputDirect(folder, idx, storage);
                out.write(data);
                out.flush();
                return;
            } catch (IOException e) {
                lastException = e;
                AppLogger.log("UploadHandler", "Chunk write attempt " + attempt + " failed: " + e.getMessage());
                try { Thread.sleep(200 * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            } finally {
                if (out != null) {
                    try { out.close(); } catch (IOException ignored) {}
                }
            }
        }
        throw new IOException("Failed to write chunk after 3 attempts", lastException);
    }

    private static OutputStream openChunkOutputDirect(ChunkedUploadManager.TempUploadFolder folder, int index, StorageHelper storage) throws IOException {
        String chunkFile = String.format("chunk_%05d.dat", index);
        String folderRelPath = folder.getFolderRelPath();

        if (GlobalVars.rootUri != null) {
            File tempRoot = getTempChunkRootForFolder(folderRelPath);
            if (tempRoot == null) {
                ensureTempLocation(folderRelPath, folder.getTotalSize());
                tempRoot = getTempChunkRootForFolder(folderRelPath);
            }
            if (tempRoot == null) throw new IOException("No temp location available for chunks");
            File chunkFolder = new File(tempRoot, folderRelPath);
            if (!chunkFolder.exists()) chunkFolder.mkdirs();
            File chunkFileObj = new File(chunkFolder, chunkFile);
            if (chunkFileObj.exists()) chunkFileObj.delete();
            return new FileOutputStream(chunkFileObj);
        }

        String chunkRelPath = folderRelPath + "/" + chunkFile;
        if (storage.exists(chunkRelPath)) storage.delete(chunkRelPath);
        OutputStream out = storage.openOutputStream(chunkRelPath, "application/octet-stream");
        return out;
    }

    private static int chunkIndexFromName(String name) {
        if (name == null || !name.startsWith("chunk_") || !name.endsWith(".dat")) return -1;
        try { return Integer.parseInt(name.substring(6, 11)); } catch (Exception e) { return -1; }
    }

    static String resolveFileName(StorageHelper storage, String basePath, String desiredName, boolean overwrite) throws IOException {
        String fullPath = basePath + desiredName;
        if (!storage.exists(fullPath)) return desiredName;
        if (overwrite) {
            return desiredName;
        }
        int dot = desiredName.lastIndexOf('.');
        String base = dot > 0 ? desiredName.substring(0, dot) : desiredName;
        String ext = dot > 0 ? desiredName.substring(dot) : "";
        int num = 1;
        String newName;
        do {
            newName = base + " " + num + ext;
            if (!storage.exists(basePath + newName)) return newName;
            num++;
        } while (num <= 10);
        throw new IOException("Cannot find a non-existing filename for " + desiredName + " after 10 attempts");
    }

    private static String extractBoundary(String contentType) {
        if (contentType == null) return null;
        for (String part : contentType.split(";")) {
            String t = part.trim();
            if (t.toLowerCase().startsWith("boundary=")) {
                String b = t.substring(9);
                if (b.startsWith("\"") && b.endsWith("\"")) b = b.substring(1, b.length()-1);
                return b;
            }
        }
        return null;
    }

    private static String getHeaderValue(String headers, String headerName) {
        for (String line : headers.split("\r\n")) {
            if (line.toLowerCase().startsWith(headerName.toLowerCase() + ":"))
                return line.substring(headerName.length()+1).trim();
        }
        return null;
    }

    private static String getDispParam(String disposition, String param) {
        for (String part : disposition.split(";")) {
            String t = part.trim();
            if (t.startsWith(param + "=")) {
                String val = t.substring(param.length()+1);
                if (val.startsWith("\"") && val.endsWith("\"")) val = val.substring(1, val.length()-1);
                return val;
            }
        }
        return null;
    }

    private static String sanitizeFilename(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray())
            sb.append((c < 32 || c == 127 || c == '\\' || c == '/' || c == ':' || c == '*' ||
                       c == '?' || c == '"' || c == '<' || c == '>' || c == '|') ? '_' : c);
        String s = sb.toString();
        if (s.length() > 200) s = s.substring(0, 200);
        return s.isEmpty() ? "uploaded_file" : s;
    }

    private static int indexOfBoundary(byte[] buf, int off, int len, byte[] boundary) {
        for (int i = off; i <= len - boundary.length; i++) {
            boolean match = true;
            for (int j = 0; j < boundary.length; j++) {
                if (buf[i + j] != boundary[j]) { match = false; break; }
            }
            if (match) {
                boolean lineStart = (i == 0) || (i >= 1 && buf[i-1] == '\n') || (i >= 2 && buf[i-2] == '\r' && buf[i-1] == '\n');
                if (lineStart) return i;
            }
        }
        return -1;
    }

    private static int indexOf(byte[] buf, int off, int len, byte[] pat) {
        if (pat.length == 0) return off;
        int end = len - pat.length;
        for (int i = off; i <= end; i++) {
            boolean match = true;
            for (int j = 0; j < pat.length; j++)
                if (buf[i + j] != pat[j]) { match = false; break; }
            if (match) return i;
        }
        return -1;
    }

    private static String sanitizePath(String path) {
        if (path == null) return "";
        String[] parts = path.split("/");
        List<String> clean = new ArrayList<>();
        for (String p : parts) {
            if (p.equals("..")) { if (!clean.isEmpty()) clean.remove(clean.size()-1); }
            else if (!p.equals(".") && !p.isEmpty()) clean.add(p);
        }
        return String.join("/", clean);
    }
}