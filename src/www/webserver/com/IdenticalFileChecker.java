package www.webserver.com;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class IdenticalFileChecker {

    private static final long SMALL_FILE_THRESHOLD = 10_485_760L;
    private static final long FIXED_SLICE_SIZE       = 1_048_576L;    // 1 MB
    private static final String HASH_ALGORITHM = "SHA-256";

    private final StorageHelper storage;
    private final String directoryPath;

    public IdenticalFileChecker(StorageHelper storage, String directoryPath) {
        this.storage = storage;
        this.directoryPath = directoryPath.endsWith("/") ? directoryPath : directoryPath + "/";
    }

    /**
     * Original method – kept for backward compatibility.
     */
    public String findIdenticalFile(String clientHash, long totalSize) {
        if (clientHash == null || clientHash.isEmpty() || totalSize <= 0) return null;
        boolean isSmall = totalSize <= SMALL_FILE_THRESHOLD;
        try {
            for (StorageItem item : storage.listDirectory(directoryPath)) {
                if (item.isDirectory) continue;
                if (item.size != totalSize) continue;
                String existingHash;
                if (isSmall) {
                    existingHash = computeFullFileHash(item.name);
                } else {
                    existingHash = computeThreePointHash(item.name);
                }
                if (existingHash != null && clientHash.equals(existingHash)) {
                    return item.name;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * New server‑side check using received sample bytes (or full file bytes).
     *
     * @param sampleBytes  the raw bytes received from the client
     * @param totalSize    the total file size declared by the client
     * @param isFullFile   true if sampleBytes is the entire file; false if it's a 3‑slice sample
     * @return matching filename or null
     */
    public String checkIdenticalBySamples(byte[] sampleBytes, long totalSize, boolean isFullFile) {
        if (sampleBytes == null || sampleBytes.length == 0 || totalSize <= 0) return null;
        String receivedHash = bytesToHex(hashBytes(sampleBytes));

        try {
            for (StorageItem item : storage.listDirectory(directoryPath)) {
                if (item.isDirectory) continue;
                if (item.size != totalSize) continue;
                String existingHash;
                if (isFullFile) {
                    existingHash = computeFullFileHash(item.name);
                } else {
                    existingHash = computeFixedThreeSliceHash(item.name);
                }
                if (existingHash != null && receivedHash.equals(existingHash)) {
                    return item.name;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String computeFullFileHash(String fileName) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] buf = new byte[Math.max(GlobalVars.bufferSizeKB * 1024, 4096)];
            try (InputStream is = storage.openInputStream(directoryPath + fileName)) {
                int n;
                while ((n = is.read(buf)) != -1) {
                    digest.update(buf, 0, n);
                }
            }
            return bytesToHex(digest.digest());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Computes a SHA‑256 hash from three fixed 1 MB slices of the file.
     * Used when the client sent a 3‑slice sample (files > 10 MB).
     */
    private String computeFixedThreeSliceHash(String fileName) {
        long fileSize = storage.length(directoryPath + fileName);
        if (fileSize <= 0) return null;

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }

        byte[] buf = new byte[Math.max(GlobalVars.bufferSizeKB * 1024, 4096)];

        // Three fixed offsets: head, middle, tail
        long[] offsets = new long[3];
        offsets[0] = 0;                                                          // head
        offsets[1] = Math.max(0, fileSize / 2 - FIXED_SLICE_SIZE / 2);          // middle
        offsets[2] = fileSize - FIXED_SLICE_SIZE;                               // tail

        for (long offset : offsets) {
            if (offset < 0) offset = 0;
            if (offset + FIXED_SLICE_SIZE > fileSize) {
                // read whatever remains (should only happen for very small files,
                // but this function is only called for >10MB files, so not really)
                continue;
            }
            readSliceAt(storage, directoryPath + fileName, offset, FIXED_SLICE_SIZE, digest, buf);
        }

        return bytesToHex(digest.digest());
    }

    private void readSliceAt(StorageHelper storage, String relPath, long offset, long size,
                             MessageDigest digest, byte[] buf) {
        File realFile = resolveRealFile(relPath);
        if (realFile != null && realFile.exists()) {
            try (RandomAccessFile raf = new RandomAccessFile(realFile, "r")) {
                raf.seek(offset);
                long remaining = size;
                while (remaining > 0) {
                    int chunk = (int) Math.min(buf.length, remaining);
                    int r = raf.read(buf, 0, chunk);
                    if (r == -1) break;
                    digest.update(buf, 0, r);
                    remaining -= r;
                }
            } catch (Exception ignored) {}
        } else {
            try (InputStream is = storage.openInputStream(relPath)) {
                skipFully(is, offset, buf);
                long remaining = size;
                while (remaining > 0) {
                    int r = is.read(buf, 0, (int) Math.min(buf.length, remaining));
                    if (r == -1) break;
                    digest.update(buf, 0, r);
                    remaining -= r;
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Original three‑point hash used by the old preflight.  Kept for
     * compatibility with findIdenticalFile (client‑side hash).
     */
    private String computeThreePointHash(String fileName) {
        long fileSize = storage.length(directoryPath + fileName);
        if (fileSize <= 0) return null;

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        byte[] buf = new byte[Math.max(GlobalVars.bufferSizeKB * 1024, 4096)];
        try (InputStream is = storage.openInputStream(directoryPath + fileName)) {
            // head
            readExact(is, digest, Math.min(FIXED_SLICE_SIZE, fileSize), buf);
            // middle
            long midStart = Math.max(0, fileSize / 2 - FIXED_SLICE_SIZE / 2);
            if (midStart > 0) {
                skipFully(is, midStart - Math.min(FIXED_SLICE_SIZE, fileSize), buf);
                readExact(is, digest, FIXED_SLICE_SIZE, buf);
            }
            // tail
            long endStart = fileSize - FIXED_SLICE_SIZE;
            if (endStart > 0 && endStart > midStart + FIXED_SLICE_SIZE) {
                skipFully(is, endStart - (midStart + FIXED_SLICE_SIZE), buf);
                readExact(is, digest, FIXED_SLICE_SIZE, buf);
            }
        } catch (Exception e) {
            return null;
        }
        return bytesToHex(digest.digest());
    }

    private static void readExact(InputStream is, MessageDigest digest, long size, byte[] buf) throws IOException {
        long remaining = size;
        while (remaining > 0) {
            int r = is.read(buf, 0, (int) Math.min(buf.length, remaining));
            if (r == -1) break;
            digest.update(buf, 0, r);
            remaining -= r;
        }
    }

    private static void skipFully(InputStream is, long toSkip, byte[] buffer) throws IOException {
        long remaining = toSkip;
        while (remaining > 0) {
            int toRead = (int) Math.min(buffer.length, remaining);
            int r = is.read(buffer, 0, toRead);
            if (r == -1) throw new EOFException("Unexpected end of stream while skipping");
            remaining -= r;
        }
    }

    private File resolveRealFile(String relPath) {
        if (GlobalVars.legacyPath != null) {
            return new File(GlobalVars.legacyPath, relPath);
        }
        String realBase = storage.getRealPath();
        if (realBase != null) {
            return new File(realBase, relPath);
        }
        return null;
    }

    private static byte[] hashBytes(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            digest.update(data);
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            return new byte[0];
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}