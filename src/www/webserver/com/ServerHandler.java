package www.webserver.com;

import android.util.Log;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

class ServerHandler extends Thread {
    private OutputStream rawOut;
    private Socket toClient;
    private final Server owner;
    private boolean rejected = false;

    private static final int MAX_CONNECTIONS = 20;
    private static final AtomicInteger activeConnections = new AtomicInteger(0);
    private boolean requestAuthenticated = false;

    private static final long LARGE_FILE_THRESHOLD = 10_485_760L;
    private static final int MAX_JSON_ITEMS = 1000;

    ServerHandler(Socket socket, Server owner) {
        this.toClient = socket;
        this.owner = owner;
        try { this.toClient.setSoTimeout(900000); } catch (Exception ignored) {}
        if (activeConnections.incrementAndGet() > MAX_CONNECTIONS) {
            try { this.toClient.close(); } catch (IOException ignored) {}
            activeConnections.decrementAndGet();
            rejected = true;
        }
    }

    @Override
    public void run() {
        if (rejected) return;
        try {
            this.rawOut = this.toClient.getOutputStream();
            try { handleRequest(); } catch (SocketException e) {
            } catch (Exception e) {
                AppLogger.log("WebServer", "Unhandled exception", e);
                try { sendJsonError("Internal error: " + e.getMessage()); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            AppLogger.log("WebServer", "Fatal error", e);
        } finally {
            owner.removeClient(this.toClient);
            try { this.toClient.close(); } catch (IOException ignored) {}
            if (!rejected) {
                activeConnections.decrementAndGet();
            }
        }
    }

    private static String cleanPath(String fullPath) {
        if (fullPath == null) return "";
        int qIdx = fullPath.indexOf('?');
        String pathOnly = (qIdx == -1) ? fullPath : fullPath.substring(0, qIdx);
        pathOnly = Uri.decode(pathOnly);
        pathOnly = pathOnly.replaceAll("/+", "/");
        if (pathOnly.startsWith("/")) pathOnly = pathOnly.substring(1);
        if (pathOnly.endsWith("/") && pathOnly.length() > 1) pathOnly = pathOnly.substring(0, pathOnly.length() - 1);
        return pathOnly;
    }

    private static String cleanPathKeepTrailing(String fullPath) {
        if (fullPath == null) return "";
        int qIdx = fullPath.indexOf('?');
        String pathOnly = (qIdx == -1) ? fullPath : fullPath.substring(0, qIdx);
        pathOnly = Uri.decode(pathOnly);
        boolean hadTrailing = pathOnly.endsWith("/");
        pathOnly = pathOnly.replaceAll("/+", "/");
        if (pathOnly.startsWith("/")) pathOnly = pathOnly.substring(1);
        if (hadTrailing && !pathOnly.endsWith("/")) pathOnly += "/";
        if (pathOnly.equals("/")) pathOnly = "";
        return pathOnly;
    }

    private static String getParentRelativePath(String relPath) {
        if (relPath == null || relPath.isEmpty() || relPath.equals("/")) return "";
        if (relPath.endsWith("/")) relPath = relPath.substring(0, relPath.length() - 1);
        int lastSlash = relPath.lastIndexOf('/');
        return lastSlash <= 0 ? "" : relPath.substring(0, lastSlash);
    }

    private static boolean isRootPath(String relPath) {
        return relPath == null || relPath.isEmpty() || relPath.equals("/");
    }

    private String extractBasePath(String fullPath) {
        if (fullPath == null) return "";
        String pathOnly = fullPath.contains("?") ? fullPath.substring(0, fullPath.indexOf("?")) : fullPath;
        pathOnly = Uri.decode(pathOnly);
        if (pathOnly.startsWith("/")) pathOnly = pathOnly.substring(1);
        if (!pathOnly.isEmpty() && !pathOnly.endsWith("/")) pathOnly += "/";
        return pathOnly;
    }

    private void handleRequest() throws IOException {
        InputStream rawIn = new BufferedInputStream(this.toClient.getInputStream());
        ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
        int totalHeaderBytes = 0;
        while (totalHeaderBytes < 65536) {
            int b = rawIn.read();
            if (b == -1) break;
            headerBuf.write(b);
            totalHeaderBytes++;
            byte[] arr = headerBuf.toByteArray();
            int len = arr.length;
            if (len >= 4 && arr[len-4]=='\r' && arr[len-3]=='\n' && arr[len-2]=='\r' && arr[len-1]=='\n') break;
            if (len >= 2 && arr[len-2]=='\n' && arr[len-1]=='\n') break;
        }
        if (totalHeaderBytes >= 65536) throw new IOException("Header too large");

        byte[] headerBytes = headerBuf.toByteArray();
        String headerStr = new String(headerBytes, "ISO-8859-1");
        String[] lines = headerStr.split("\\r?\\n");
        String method = "GET";
        String requestedPath = "";
        long contentLength = 0;
        String contentType = "";
        String authHeader = null;
        String rangeHeader = null;

        if (lines.length > 0) {
            String[] parts = lines[0].split(" ");
            if (parts.length >= 2) {
                method = parts[0];
                requestedPath = parts[1].replaceAll("[/]+", "/");
            }
        }
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            String lower = line.toLowerCase();
            if (lower.startsWith("content-length:")) {
                try { contentLength = Long.parseLong(line.substring(15).trim()); } catch (NumberFormatException ignored) {}
            } else if (lower.startsWith("content-type:")) {
                contentType = line.substring(13).trim();
            } else if (lower.startsWith("authorization:")) {
                authHeader = line.substring(14).trim();
            } else if (lower.startsWith("range:")) {
                rangeHeader = line.substring(6).trim();
            }
        }

        boolean authRequired = AuthManager.isAuthRequired();
        boolean authOk = AuthManager.checkAuthorization(authHeader);
        requestAuthenticated = authRequired && authOk;

        if (GlobalVars.hostHtmlEnabled) {
            if (authRequired && !authOk) { send401(); return; }

            if (!method.equals("GET")) {
                send401Json("Write operations are not allowed in Host HTML mode");
                return;
            }
            Map<String, String> query = parseQueryParams(requestedPath);
            if (query.containsKey("json") || query.containsKey("download") || query.containsKey("action") || query.containsKey("token")) {
                sendJsonError("API not available in Host HTML mode");
                return;
            }

            StorageHelper storage = new StorageHelper(GlobalVars.appContext);
            String relativePath = cleanPath(requestedPath);

            if (relativePath.isEmpty() || storage.isDirectory(relativePath)) {
                String indexPath = findIndexFile(storage, relativePath);
                if (indexPath != null) {
                    serveFileStream(storage, indexPath, rangeHeader, true);
                } else {
                    serveError();
                }
            } else {
                serveFileStream(storage, relativePath, rangeHeader, false);
            }
            return;
        }

        if (!authRequired && !method.equals("GET")) {
            sendJsonError("Write operations are disabled.\nSet a password to enable uploads.");
            return;
        }

        Map<String,String> query = parseQueryParams(requestedPath);

        String downloadType = query.get("download");
        String itemName = query.get("path");
        if (downloadType != null && itemName != null) {
            String dirPath = cleanPath(requestedPath);
            if (!dirPath.isEmpty() && !dirPath.endsWith("/")) dirPath += "/";
            String fullPath = dirPath + itemName;
            if (downloadType.equals("file")) {
                serveFileDirectly(fullPath);
                return;
            } else if (downloadType.equals("zip")) {
                serveFolderAsZip(fullPath);
                return;
            }
        }

        // ==============================================================
        //  SERVER-SIDE HASH PREFLIGHT  (works on HTTP and HTTPS)
        // ==============================================================
        if (method.equals("POST") && contentLength > 0
                && contentType != null
                && contentType.toLowerCase().startsWith("application/octet-stream")) {

            if (authRequired && !authOk) { send401Json("Authentication required"); return; }

            String fileName = query.get("name");
            long totalSize = query.containsKey("size") ? Long.parseLong(query.get("size")) : 0;
            String mode = query.get("mode");
            String overwriteStr = query.get("overwrite");
            boolean overwrite = "true".equals(overwriteStr);

            if (fileName == null || fileName.isEmpty() || totalSize <= 0) {
                sendJsonError("Missing file name or size");
                return;
            }

            StorageHelper storage = new StorageHelper(GlobalVars.appContext);
            String relPath = extractBasePath(requestedPath);

            byte[] bodyBytes;
            try {
                ByteArrayOutputStream bodyBuf = new ByteArrayOutputStream();
                byte[] tmp = new byte[8192];
                long remaining = contentLength;
                while (remaining > 0) {
                    int r = rawIn.read(tmp, 0, (int) Math.min(tmp.length, remaining));
                    if (r == -1) break;
                    bodyBuf.write(tmp, 0, r);
                    remaining -= r;
                }
                bodyBytes = bodyBuf.toByteArray();
            } catch (IOException e) {
                sendJsonError("Failed to read request body");
                return;
            }

            boolean isFullFile = (bodyBytes.length == totalSize);

            // 1. Identical check
            IdenticalFileChecker checker = new IdenticalFileChecker(storage, relPath);
            String identicalName = checker.checkIdenticalBySamples(bodyBytes, totalSize, isFullFile);
            if (identicalName != null) {
                sendJsonStatus("identical", null);
                return;
            }

            // 2. Duplicate name check
            if (DuplicateDetector.nameExists(storage, relPath, fileName)) {
                if (!overwrite) {
                    sendJsonDuplicate(fileName);
                    return;
                } else {
                    if (!storage.delete(relPath + fileName)) {
                        sendJsonError("Failed to delete existing file");
                        return;
                    }
                }
            }

            // 3. Token / chunk setup
            String token = null;
            String neededChunks = null;
            int completed = 0;

            if ("chunks".equals(mode)) {
                long freeSpace = getAvailableSpace(storage, relPath);
                if (freeSpace >= 0 && freeSpace < (double) totalSize * 2.2) {
                    sendJsonError("Not enough storage");
                    return;
                }

                String folderName = UUID.randomUUID().toString().replace("-", "");
                String folderPath = relPath + folderName;

                if (!storage.exists(folderPath)) {
                    if (!storage.mkdir(folderPath)) { sendJsonError("Cannot create upload folder"); return; }
                    int totalChunks = (int)((totalSize + ChunkedUploadManager.CHUNK_SIZE - 1) / ChunkedUploadManager.CHUNK_SIZE);
                    ChunkedUploadManager mgr = new ChunkedUploadManager(storage, relPath);
                    mgr.createTempFolder(folderName, fileName, totalSize, totalChunks);
                    StringBuilder needed = new StringBuilder();
                    for (int i = 0; i < totalChunks; i++) {
                        if (i > 0) needed.append(",");
                        needed.append(i);
                    }
                    neededChunks = needed.toString();
                    completed = 0;
                } else {
                    UploadHandler.cancelTimeout(folderPath);
                    ChunkedUploadManager.TempUploadFolder folder = new ChunkedUploadManager.TempUploadFolder(storage, folderPath);
                    folder.setOriginalName(fileName);
                    List<Integer> completeChunks = folder.getCompletedChunks();
                    int totalChunks = folder.getTotalChunks();
                    completed = completeChunks.size();
                    if (completed == totalChunks) {
                        String safeName = folder.getOriginalName();
                        String finalName = UploadHandler.resolveFileName(storage, relPath, safeName, false);
                        ChunkedUploadManager mgr = new ChunkedUploadManager(storage, relPath);
                        mgr.reassemble(folder, finalName, false);
                        sendJsonStatus("complete", null);
                        return;
                    }
                    List<Integer> needed = new ArrayList<>();
                    for (int i = 0; i < totalChunks; i++) {
                        if (!completeChunks.contains(i)) needed.add(i);
                    }
                    StringBuilder neededStr = new StringBuilder();
                    for (int i = 0; i < needed.size(); i++) {
                        if (i > 0) neededStr.append(',');
                        neededStr.append(needed.get(i));
                    }
                    neededChunks = neededStr.toString();
                }
                token = folderName;
            }

            String json = "{\"token\":\"" + (token != null ? token : "") + "\",\"needed\":\"" +
                          (neededChunks != null ? neededChunks : "") + "\",\"completed\":" + completed +
                          ",\"status\":\"ok\"}";
            byte[] respBytes = json.getBytes(StandardCharsets.UTF_8);
            String hdr = "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=UTF-8\r\nContent-Length: " +
                         respBytes.length + "\r\nConnection: close\r\n\r\n";
            rawOut.write(hdr.getBytes(StandardCharsets.UTF_8));
            rawOut.write(respBytes);
            rawOut.flush();
            return;
        }

        // ---------- Finalize ----------
        if (method.equals("POST") && "finalize".equals(query.get("action")) && query.get("token") != null) {
            if (authRequired && !authOk) { send401Json("Authentication required"); return; }
            String token = query.get("token");
            String basePath = cleanPath(requestedPath);
            if (!basePath.isEmpty() && !basePath.endsWith("/")) basePath += "/";
            boolean overwriteFinal = "true".equals(query.get("overwrite"));
            handleFinalize(token, basePath, overwriteFinal);
            return;
        }

        // ---------- Multipart / form actions ----------
        if (method.equals("POST") && contentLength > 0) {
            String ctLower = (contentType != null) ? contentType.toLowerCase() : "";

            if (ctLower.startsWith("multipart/form-data")) {
                if (authRequired && !authOk) { send401Json("Authentication required"); return; }

                String token = query.get("token");
                String newname = query.get("newname");
                String overwriteStr = query.get("overwrite");

                if (token != null && !token.isEmpty() && newname != null && !newname.isEmpty()) {
                    try {
                        StorageHelper st = new StorageHelper(GlobalVars.appContext);
                        String basePath = cleanPath(requestedPath);
                        if (!basePath.isEmpty() && !basePath.endsWith("/")) basePath += "/";
                        String folderPath = basePath + token;
                        if (st.exists(folderPath)) {
                            ChunkedUploadManager.TempUploadFolder folder =
                                new ChunkedUploadManager.TempUploadFolder(st, folderPath);
                            folder.setOriginalName(newname);
                        }
                    } catch (Exception ignored) {}
                }

                try {
                    String result = UploadHandler.handlePost(requestedPath, contentLength, contentType, rawIn, token,
                        "true".equals(overwriteStr));
                    if (token == null || token.isEmpty()) {
                        sendJsonStatus("ok", null);
                    } else {
                        if ("__INCOMPLETE__".equals(result)) {
                            sendJsonStatus("ok", null);
                        } else {
                            sendJsonStatus("complete", null);
                        }
                    }
                } catch (IOException e) {
                    sendJsonError("Upload error: " + e.getMessage());
                }
                return;
            }

            if (authRequired && !authOk) { send401Json("Authentication required"); return; }
            if (contentLength > 1_048_576) { sendJsonError("Request body too large"); return; }

            byte[] formBuf = new byte[(int)contentLength];
            long formTotal = 0;
            while (formTotal < contentLength) {
                int r = rawIn.read(formBuf, (int)formTotal, (int)(contentLength - formTotal));
                if (r == -1) break;
                formTotal += r;
            }
            String body = new String(formBuf, 0, (int)formTotal, StandardCharsets.UTF_8);
            Map<String, String> params = parseFormData(body);
            String act   = params.get("action");
            String name  = params.get("name");
            String newName = params.get("newname");
            boolean overwrite = "true".equals(params.get("overwrite"));

            StorageHelper storage2 = new StorageHelper(GlobalVars.appContext);
            String decodedFolder = cleanPath(requestedPath);
            if (!decodedFolder.isEmpty() && !decodedFolder.endsWith("/")) decodedFolder += "/";

            boolean success = false;
            String errorMsg = "";
            try {
                if ("delete".equals(act) && name != null) {
                    storage2.deleteForced(decodedFolder + name);
                    success = true;
                } else if ("rename".equals(act) && name != null && newName != null) {
                    String cleanNewName = UrlUtils.sanitizeFolderName(newName);
                    if (cleanNewName.isEmpty()) {
                        errorMsg = "Invalid folder name";
                    } else {
                        if (!overwrite && storage2.exists(decodedFolder + cleanNewName)) {
                            errorMsg = "EXISTS";
                        } else {
                            success = storage2.rename(decodedFolder + name, cleanNewName, overwrite);
                            if (!success) errorMsg = "Rename failed";
                        }
                    }
                } else if ("mkdir".equals(act) && name != null) {
                    String cleanName = UrlUtils.sanitizeFolderName(name);
                    if (cleanName.isEmpty()) {
                        errorMsg = "Invalid folder name";
                    } else {
                        success = storage2.mkdir(decodedFolder + cleanName);
                        if (!success) errorMsg = "Create folder failed";
                    }
                } else if ("mkfile".equals(act) && name != null) {
                    String cleanName = UrlUtils.sanitizeFolderName(name);
                    if (cleanName.isEmpty()) {
                        errorMsg = "Invalid file name";
                    } else {
                        String filePath = decodedFolder + cleanName;
                        if (storage2.exists(filePath)) {
                            errorMsg = "EXISTS";
                        } else {
                            try {
                                OutputStream out = storage2.openOutputStream(filePath, "text/plain");
                                out.close();
                                success = true;
                            } catch (IOException e) {
                                errorMsg = "Create file failed: " + e.getMessage();
                            }
                        }
                    }
                }
            } catch (Exception e) { errorMsg = e.getMessage(); }

            if (errorMsg.equals("EXISTS")) { sendJsonDuplicate(newName != null ? newName : name); }
            else if (success) { sendJsonStatus("ok", null); }
            else { sendJsonError(errorMsg); }
            return;
        }

        // ---------- GET requests ----------
        if (authRequired && !authOk) { send401(); return; }

        StorageHelper storage = new StorageHelper(GlobalVars.appContext);
        String relativePath = cleanPath(requestedPath);

        if ("1".equals(query.get("json"))) {
            if (!storage.exists(relativePath)) { serveError(); return; }
            sendJsonDirectory(storage, relativePath, query);
            return;
        }

        String cleanPathForDir = cleanPathKeepTrailing(requestedPath);
        if (cleanPathForDir.isEmpty()) cleanPathForDir = "/";

        if (!storage.exists(relativePath)) { serveError(); return; }

        if (storage.isDirectory(relativePath)) {
            if (GlobalVars.hostHtmlEnabled && storage.exists(relativePath + "/index.html")) {
                serveFileStream(storage, relativePath + "/index.html", rangeHeader, false);
            } else {
                boolean showActions = authRequired && authOk;
                String encodedPath = cleanPathForDir;
                if (encodedPath.isEmpty()) encodedPath = "/";
                else if (!encodedPath.startsWith("/")) encodedPath = "/" + encodedPath;
                serveDirectory(storage, relativePath, encodedPath, showActions);
            }
        } else {
            serveFileStream(storage, relativePath, rangeHeader, false);
        }
    }

    private void handleFinalize(String folderName, String basePath, boolean overwrite) throws IOException {
        StorageHelper storage = new StorageHelper(GlobalVars.appContext);
        try {
            UploadHandler.handleFinalize(basePath, folderName, storage, overwrite);
            sendJsonStatus("ok", null);
        } catch (IOException e) {
            String msg = e.getMessage();
            if (msg != null && msg.startsWith("MISSING:")) {
                String missingList = msg.substring(8);
                String json = "{\"status\":\"incomplete\",\"needed\":\"" + missingList + "\"}";
                byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
                String hdr = "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=UTF-8\r\nContent-Length: " +
                             jsonBytes.length + "\r\nConnection: close\r\n\r\n";
                rawOut.write(hdr.getBytes(StandardCharsets.UTF_8));
                rawOut.write(jsonBytes);
                rawOut.flush();
            } else {
                sendJsonError("Reassembly failed: " + e.getMessage());
            }
        }
    }

    private void sendJsonDirectory(StorageHelper storage, String relPath, Map<String, String> query) throws IOException {
        List<StorageItem> items;
        try {
            items = storage.listDirectory(relPath);
        } catch (Exception e) {
            sendJsonError("Cannot list directory");
            return;
        }

        int offset = 0;
        int limit = MAX_JSON_ITEMS;
        try { if (query.containsKey("offset")) offset = Integer.parseInt(query.get("offset")); } catch (Exception ignored) {}
        try { if (query.containsKey("limit")) limit = Integer.parseInt(query.get("limit")); } catch (Exception ignored) {}
        if (offset < 0) offset = 0;
        if (limit <= 0 || limit > MAX_JSON_ITEMS) limit = MAX_JSON_ITEMS;

        int totalItems = items.size();
        int end = Math.min(offset + limit, totalItems);
        List<StorageItem> page = (offset < totalItems) ? items.subList(offset, end) : new ArrayList<>();

        JSONArray arr = new JSONArray();
        for (StorageItem item : page) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("name", item.name);
                obj.put("isDirectory", item.isDirectory);
                obj.put("size", item.size);
                obj.put("lastModified", item.lastModified);
                arr.put(obj);
            } catch (JSONException ignored) {}
        }

        boolean parentExists = false;
        if (!isRootPath(relPath)) {
            String parentRelPath = getParentRelativePath(relPath);
            parentExists = storage.exists(parentRelPath);
        }

        JSONObject response = new JSONObject();
        try {
            response.put("status", "ok");
            response.put("items", arr);
            response.put("path", relPath);
            response.put("authenticated", requestAuthenticated);
            response.put("parentExists", parentExists);
            response.put("offset", offset);
            response.put("limit", limit);
            response.put("total", totalItems);
            if (end < totalItems) {
                response.put("truncated", true);
            }
        } catch (JSONException ignored) {}

        byte[] jsonBytes = response.toString().getBytes(StandardCharsets.UTF_8);
        String hdr = "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=UTF-8\r\nContent-Length: " +
                     jsonBytes.length + "\r\nConnection: close\r\n\r\n";
        rawOut.write(hdr.getBytes(StandardCharsets.UTF_8));
        rawOut.write(jsonBytes);
        rawOut.flush();
    }

    private void sendJsonStatus(String status, String message) throws IOException {
        String json = message != null ? "{\"status\":\"" + status + "\",\"message\":\"" + escapeJson(message) + "\"}"
                                     : "{\"status\":\"" + status + "\"}";
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        String hdr = "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=UTF-8\r\nContent-Length: " +
                     jsonBytes.length + "\r\nConnection: close\r\n\r\n";
        rawOut.write(hdr.getBytes(StandardCharsets.UTF_8));
        rawOut.write(jsonBytes);
        rawOut.flush();
    }

    private void sendJsonError(String message) throws IOException { sendJsonStatus("error", message); }

    private void sendJsonDuplicate(String name) throws IOException {
        String json = "{\"status\":\"duplicate\",\"name\":\"" + escapeJson(name) + "\"}";
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        String hdr = "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=UTF-8\r\nContent-Length: " +
                     jsonBytes.length + "\r\nConnection: close\r\n\r\n";
        rawOut.write(hdr.getBytes(StandardCharsets.UTF_8));
        rawOut.write(jsonBytes);
        rawOut.flush();
    }

    private void send401Json(String message) throws IOException {
        String json = message != null ? "{\"status\":\"error\",\"message\":\"" + escapeJson(message) + "\"}"
                                     : "{\"status\":\"error\"}";
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        String hdr = "HTTP/1.1 401 Unauthorized\r\nWWW-Authenticate: Basic realm=\"WebServer\"\r\nContent-Type: application/json; charset=UTF-8\r\nContent-Length: " +
                     jsonBytes.length + "\r\nConnection: close\r\n\r\n";
        rawOut.write(hdr.getBytes(StandardCharsets.UTF_8));
        rawOut.write(jsonBytes);
        rawOut.flush();
    }

    private static Map<String, String> parseQueryParams(String urlOrQuery) {
        Map<String, String> map = new HashMap<>();
        String query = urlOrQuery;
        int q = urlOrQuery.indexOf('?');
        if (q != -1) query = urlOrQuery.substring(q + 1);
        if (query.isEmpty()) return map;
        String placeholder = "\u0000\u0000";
        query = query.replace("%26", placeholder);
        for (String pair : query.split("&")) {
            pair = pair.replace(placeholder, "%26");
            String[] kv = pair.split("=", 2);
            try {
                map.put(URLDecoder.decode(kv[0], "UTF-8"), kv.length > 1 ? URLDecoder.decode(kv[1], "UTF-8") : "");
            } catch (Exception ignored) {}
        }
        return map;
    }

    private static final int MAX_DIR_ITEMS = 500;

    private void serveDirectory(StorageHelper storage, String relPath, String encodedPath, boolean showActions) throws IOException {
        List<StorageItem> items = storage.listDirectory(relPath);
        boolean truncated = items.size() > MAX_DIR_ITEMS;
        if (truncated) items = items.subList(0, MAX_DIR_ITEMS);

        String absoluteBase = encodedPath;
        if (absoluteBase.endsWith("/") && absoluteBase.length() > 1) {
            absoluteBase = absoluteBase.substring(0, absoluteBase.length() - 1);
        }

        this.rawOut.write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\n".getBytes(StandardCharsets.UTF_8));
        this.rawOut.write("Content-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'\r\n".getBytes(StandardCharsets.UTF_8));
        this.rawOut.write("Connection: close\r\n\r\n".getBytes(StandardCharsets.UTF_8));

        PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(this.rawOut, StandardCharsets.UTF_8)));
        String ip = (GlobalVars.ip != null) ? GlobalVars.ip : "0.0.0.0";
        WebUiBuilder.writeHtmlPrologue(out, ip, GlobalVars.port);
        WebUiBuilder.writeDirectoryHeader(out, showActions);

        if (!isRootPath(relPath)) {
            String parentRelPath = getParentRelativePath(relPath);
            if (storage.exists(parentRelPath)) {
                String parentEncoded = getParentPathEncoded(encodedPath);
                int colspan = 2;
                if (showActions) colspan++;
                colspan++;
                WebUiBuilder.writeParentLink(out, parentEncoded, colspan);
            }
        }

        for (StorageItem item : items) {
            WebUiBuilder.writeFileRow(out, item, absoluteBase, showActions);
        }
        if (truncated) {
            int colspan = 2;
            if (showActions) colspan++;
            colspan++;
            WebUiBuilder.writeTruncatedNote(out, colspan);
        }

        if (showActions) {
            WebUiBuilder.writeUploadForm(out);
        } else {
            out.print("</table>\n");
        }

        WebUiBuilder.writeFooter(out, ip, GlobalVars.port);
        WebUiBuilder.writeJavaScript(out, absoluteBase);
        out.flush();
    }

    private String getParentPathEncoded(String encodedPath) {
        if (encodedPath == null || encodedPath.isEmpty() || encodedPath.equals("/")) return "/";
        if (encodedPath.endsWith("/")) encodedPath = encodedPath.substring(0, encodedPath.length() - 1);
        int lastSlash = encodedPath.lastIndexOf('/');
        return lastSlash <= 0 ? "/" : encodedPath.substring(0, lastSlash);
    }

    public static String escapeHtmlStatic(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static String escapeJsStatic(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"")
                   .replace("<", "\\x3c").replace(">", "\\x3e").replace("\n", "\\n");
    }

    public static String formatSizeStatic(long bytes) {
        if (bytes >= 1073741824) return String.format("%.2f GB", bytes / 1073741824.0);
        if (bytes >= 1048576) return String.format("%.2f MB", bytes / 1048576.0);
        if (bytes >= 1024) return String.format("%.2f KB", bytes / 1024.0);
        return bytes + " B";
    }

    private void send401() throws IOException {
        String body = "Authentication required";
        String resp = "HTTP/1.1 401 Unauthorized\r\nWWW-Authenticate: Basic realm=\"WebServer\"\r\nContent-Length: " + body.length() + "\r\nConnection: close\r\n\r\n" + body;
        rawOut.write(resp.getBytes(StandardCharsets.UTF_8)); rawOut.flush();
    }

    private Map<String, String> parseFormData(String body) {
        Map<String, String> map = new HashMap<>();
        String placeholder = "\u0000\u0000";
        body = body.replace("%26", placeholder);
        for (String pair : body.split("&")) {
            pair = pair.replace(placeholder, "%26");
            String[] kv = pair.split("=", 2);
            try { map.put(URLDecoder.decode(kv[0], "UTF-8"), kv.length > 1 ? URLDecoder.decode(kv[1], "UTF-8") : ""); } catch (Exception ignored) {}
        }
        return map;
    }

    private void serveFileStream(StorageHelper storage, String relPath, String rangeHeader, boolean forceInline) throws IOException {
        if (rangeHeader != null && !rangeHeader.isEmpty()) {
            if (RangeHelper.handleRangeRequest(rawOut, relPath, storage, rangeHeader)) {
                return;
            }
        }

        long length;
        try { length = storage.length(relPath); } catch (Exception e) { serveError(); return; }
        String mime = getMimeType(relPath);
        if (mime.startsWith("text/") || mime.equals("application/json") || mime.equals("application/xml") || mime.equals("application/javascript")) mime += "; charset=UTF-8";
        StringBuilder hdr = new StringBuilder();
        hdr.append("HTTP/1.1 200 OK\r\n");
        hdr.append("Server: WebServer/4.0\r\n");
        hdr.append("Accept-Ranges: bytes\r\n");
        hdr.append("Content-Length: ").append(length).append("\r\n");
        if (forceInline || mime.startsWith("text/html") || mime.startsWith("text/htm")) {
            hdr.append("Content-Disposition: inline\r\n");
        }
        hdr.append("Connection: close\r\n");
        hdr.append("Content-Type: ").append(mime).append("\r\n\r\n");
        rawOut.write(hdr.toString().getBytes(StandardCharsets.ISO_8859_1));
        int bufferSize = Math.max(GlobalVars.bufferSizeKB * 1024, 4096);
        byte[] buffer = new byte[bufferSize];
        try (InputStream is = storage.openInputStream(relPath); BufferedOutputStream bos = new BufferedOutputStream(rawOut, bufferSize)) {
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) bos.write(buffer, 0, bytesRead);
            bos.flush();
        }
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: if (c < ' ') sb.append(String.format("\\u%04x", (int)c)); else sb.append(c);
            }
        }
        return sb.toString();
    }

    private void serveError() { WebUiBuilder.write404ErrorPage(rawOut); }

    private String getMimeType(String fileName) {
        String name = fileName.toLowerCase();
        int dot = name.lastIndexOf('.');
        if (dot == -1) return "application/octet-stream";
        String ext = name.substring(dot + 1);
        String mime = MimeMap.MAP.get(ext);
        return mime != null ? mime : "application/octet-stream";
    }

    private String sanitizeForHeader(String input) {
        if (input == null) return "download";
        return input.replace("\"", "").replace("\r", "").replace("\n", "").replace("\\", "");
    }

    private void serveFileDirectly(String filePath) throws IOException {
        if (filePath.startsWith("/")) filePath = filePath.substring(1);
        StorageHelper storage = new StorageHelper(GlobalVars.appContext);
        if (!storage.exists(filePath) || storage.isDirectory(filePath)) { serveError(); return; }
        String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
        long length = storage.length(filePath);
        String mime = getMimeType(fileName);
        String safeFileName = sanitizeForHeader(fileName);
        String header = "HTTP/1.1 200 OK\r\nContent-Type: " + mime + "\r\nContent-Disposition: attachment; filename=\"" + safeFileName + "\"\r\nContent-Length: " + length + "\r\nAccept-Ranges: bytes\r\nConnection: close\r\n\r\n";
        rawOut.write(header.getBytes(StandardCharsets.ISO_8859_1));
        int bufferSize = Math.max(GlobalVars.bufferSizeKB * 1024, 4096);
        byte[] buffer = new byte[bufferSize];
        try (InputStream is = storage.openInputStream(filePath); BufferedOutputStream bos = new BufferedOutputStream(rawOut, bufferSize)) {
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) bos.write(buffer, 0, bytesRead);
            bos.flush();
        }
    }

    private void serveFolderAsZip(String folderPath) throws IOException {
        if (folderPath.startsWith("/")) folderPath = folderPath.substring(1);
        if (!folderPath.endsWith("/")) folderPath += "/";
        StorageHelper storage = new StorageHelper(GlobalVars.appContext);
        if (!storage.exists(folderPath) || !storage.isDirectory(folderPath)) {
            serveError();
            return;
        }
        String zipName = folderPath.replace('/', '_');
        if (zipName.endsWith("_")) zipName = zipName.substring(0, zipName.length() - 1);
        if (zipName.isEmpty()) zipName = "download";
        zipName += ".zip";
        zipName = sanitizeForHeader(zipName);

        String realFolderPath = null;
        String baseReal = storage.getRealPath();
        if (baseReal != null) {
            realFolderPath = new File(baseReal, folderPath).getAbsolutePath();
            if (!new File(realFolderPath).exists()) realFolderPath = null;
        }
        if (realFolderPath == null && GlobalVars.legacyPath != null) {
            realFolderPath = new File(GlobalVars.legacyPath, folderPath).getAbsolutePath();
            if (!new File(realFolderPath).exists()) realFolderPath = null;
        }

        if (realFolderPath != null) {
            String header = "HTTP/1.1 200 OK\r\nContent-Type: application/zip\r\nContent-Disposition: attachment; filename=\"" + zipName + "\"\r\nConnection: close\r\n\r\n";
            rawOut.write(header.getBytes(StandardCharsets.ISO_8859_1));
            ZipOutputStream zos = null;
            try {
                zos = new ZipOutputStream(rawOut);
                zipDirectory(new File(realFolderPath), "", zos, new HashSet<>(), 10000, 0L);
            } catch (SocketException ignored) {
            } catch (Exception e) {
                AppLogger.log("WebServer", "ZIP stream error", e);
            } finally {
                if (zos != null) {
                    try { zos.finish(); } catch (Exception ignored) {}
                    try { zos.close(); } catch (Exception ignored) {}
                }
            }
            return;
        }

        String header = "HTTP/1.1 200 OK\r\nContent-Type: application/zip\r\nContent-Disposition: attachment; filename=\"" + zipName + "\"\r\nConnection: close\r\n\r\n";
        rawOut.write(header.getBytes(StandardCharsets.ISO_8859_1));
        ZipOutputStream zos = null;
        byte[] buffer = new byte[8192];
        int entryCount = 0;
        final int MAX_ZIP_ENTRIES = 10000;
        final long MAX_ZIP_TOTAL = 10L * 1024 * 1024 * 1024;
        long zipTotalBytes = 0;
        try {
            zos = new ZipOutputStream(rawOut);
            Deque<ZipStackItem> stack = new ArrayDeque<>();
            stack.push(new ZipStackItem(folderPath, ""));
            while (!stack.isEmpty()) {
                ZipStackItem item = stack.pop();
                String currentPath = item.basePath + item.subPath;
                List<StorageItem> children = storage.listDirectory(currentPath);
                for (StorageItem child : children) {
                    if (entryCount >= MAX_ZIP_ENTRIES) throw new IOException("Too many files for ZIP download");
                    String childPath = currentPath + child.name;
                    String entryName = item.subPath + child.name;
                    if (child.isDirectory) {
                        zos.putNextEntry(new ZipEntry(entryName + "/"));
                        zos.closeEntry();
                        stack.push(new ZipStackItem(folderPath, entryName + "/"));
                    } else {
                        zos.putNextEntry(new ZipEntry(entryName));
                        try (InputStream is = storage.openInputStream(childPath)) {
                            int len;
                            while ((len = is.read(buffer)) != -1) {
                                zos.write(buffer, 0, len);
                                zipTotalBytes += len;
                                if (zipTotalBytes > MAX_ZIP_TOTAL) throw new IOException("ZIP file size limit exceeded");
                            }
                        }
                        zos.closeEntry();
                        entryCount++;
                    }
                }
            }
        } catch (SocketException ignored) {
        } catch (Exception e) {
            AppLogger.log("WebServer", "ZIP stream error", e);
        } finally {
            if (zos != null) {
                try { zos.finish(); } catch (Exception ignored) {}
                try { zos.close(); } catch (Exception ignored) {}
            }
        }
    }

    private void zipDirectory(File directory, String basePath, ZipOutputStream zos, Set<String> visited, int maxEntries, long currentTotal) throws IOException {
        final long MAX_ZIP_TOTAL = 10L * 1024 * 1024 * 1024;
        if (maxEntries <= 0) throw new IOException("Too many files in ZIP");
        if (currentTotal > MAX_ZIP_TOTAL) throw new IOException("ZIP file size limit exceeded");
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (isSymlink(file)) continue;
                String entryName = basePath + file.getName();
                if (file.isDirectory()) {
                    String canonical = file.getCanonicalPath();
                    if (visited.contains(canonical)) continue;
                    visited.add(canonical);
                    zos.putNextEntry(new ZipEntry(entryName + "/"));
                    zos.closeEntry();
                    zipDirectory(file, entryName + "/", zos, visited, maxEntries - 1, currentTotal);
                } else {
                    zos.putNextEntry(new ZipEntry(entryName));
                    try (FileInputStream fis = new FileInputStream(file)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = fis.read(buffer)) != -1) {
                            zos.write(buffer, 0, len);
                            currentTotal += len;
                            if (currentTotal > MAX_ZIP_TOTAL) throw new IOException("ZIP file size limit exceeded");
                        }
                    }
                    zos.closeEntry();
                    maxEntries--;
                    if (maxEntries <= 0) throw new IOException("Too many files in ZIP");
                }
            }
        }
    }

    private boolean isSymlink(File file) {
        try {
            return !file.getCanonicalPath().equals(file.getAbsolutePath());
        } catch (IOException e) {
            return true;
        }
    }

    private String findIndexFile(StorageHelper storage, String dirRelPath) {
        String[] indexNames = {"index.html", "index.htm", "index.php", "index.phtml", "index.shtml", "index.jsp", "index.asp", "index.aspx"};
        String base = dirRelPath.isEmpty() ? "" : dirRelPath + "/";
        for (String name : indexNames) {
            String path = base + name;
            if (storage.exists(path) && !storage.isDirectory(path)) {
                return path;
            }
        }
        return null;
    }

    private static long getAvailableSpace(StorageHelper storage, String relativePath) {
        if (GlobalVars.legacyPath != null) {
            try {
                File dir = new File(GlobalVars.legacyPath, relativePath);
                if (!dir.exists()) dir = new File(GlobalVars.legacyPath);
                return dir.getFreeSpace();
            } catch (Exception e) { return -1; }
        }
        return -1;
    }

    private static class ZipStackItem {
        String basePath, subPath;
        ZipStackItem(String basePath, String subPath) { this.basePath = basePath; this.subPath = subPath; }
    }
}