package www.webserver.com;

public class StorageItem implements Comparable<StorageItem> {
    public final String name;
    public final boolean isDirectory;
    public final long size;
    public final long lastModified;

    public StorageItem(String name, boolean isDirectory, long size, long lastModified) {
        this.name = name;
        this.isDirectory = isDirectory;
        this.size = size;
        this.lastModified = lastModified;
    }

    @Override
    public int compareTo(StorageItem o) {
        if (this.isDirectory && !o.isDirectory) return -1;
        if (!this.isDirectory && o.isDirectory) return 1;
        return this.name.compareToIgnoreCase(o.name);
    }
}
