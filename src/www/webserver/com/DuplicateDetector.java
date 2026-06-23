package www.webserver.com;

public class DuplicateDetector {

    public static boolean nameExists(StorageHelper storage, String dirPath, String fileName) {
        String fullPath = dirPath.endsWith("/") ? dirPath + fileName : dirPath + "/" + fileName;
        try {
            return storage.exists(fullPath);
        } catch (Exception e) {
            return false;
        }
    }
}