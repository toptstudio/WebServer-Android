package www.webserver.com;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class RangeHelper {

    public static boolean handleRangeRequest(OutputStream rawOut, String relPath, StorageHelper storage, String rangeHeader) throws IOException {
        if (rangeHeader == null || !rangeHeader.startsWith("bytes=")) return false;

        long length = storage.length(relPath);
        if (length <= 0) return false;

        String rangeValue = rangeHeader.substring(6).trim();
        int dash = rangeValue.indexOf('-');
        if (dash < 0) return false;

        long start = 0, end = length - 1;
        try {
            String startStr = rangeValue.substring(0, dash).trim();
            String endStr = rangeValue.substring(dash + 1).trim();
            if (!startStr.isEmpty()) start = Long.parseLong(startStr);
            if (!endStr.isEmpty()) end = Long.parseLong(endStr);
        } catch (NumberFormatException e) {
            return false;
        }

        if (start < 0 || end >= length || start > end) {
            String resp = "HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */" + length + "\r\nConnection: close\r\n\r\n";
            rawOut.write(resp.getBytes(StandardCharsets.ISO_8859_1));
            rawOut.flush();
            return true;
        }

        long contentLength = end - start + 1;
        String mime = getMimeType(relPath);
        if (mime.startsWith("text/") || mime.startsWith("application/json") || mime.startsWith("application/xml") || mime.startsWith("application/javascript"))
            mime += "; charset=UTF-8";

        String hdr = "HTTP/1.1 206 Partial Content\r\n" +
                     "Content-Type: " + mime + "\r\n" +
                     "Content-Range: bytes " + start + "-" + end + "/" + length + "\r\n" +
                     "Accept-Ranges: bytes\r\n" +
                     "Content-Length: " + contentLength + "\r\n" +
                     "Connection: close\r\n\r\n";
        rawOut.write(hdr.getBytes(StandardCharsets.ISO_8859_1));

        int bufferSize = Math.max(GlobalVars.bufferSizeKB * 1024, 4096);
        byte[] buffer = new byte[bufferSize];
        try (InputStream is = storage.openInputStream(relPath)) {
            long skipped = is.skip(start);
            if (skipped < start) throw new IOException("Failed to skip to start position");
            long remaining = contentLength;
            int bytesRead;
            while (remaining > 0 && (bytesRead = is.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                rawOut.write(buffer, 0, bytesRead);
                remaining -= bytesRead;
            }
        }
        rawOut.flush();
        return true;
    }

    private static String getMimeType(String fileName) {
        String name = fileName.toLowerCase();
        int dot = name.lastIndexOf('.');
        if (dot == -1) return "application/octet-stream";
        String ext = name.substring(dot + 1);
        String mime = MimeMap.MAP.get(ext);
        return mime != null ? mime : "application/octet-stream";
    }
}