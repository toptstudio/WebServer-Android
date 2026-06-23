package www.webserver.com;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StaticTreeProvider {
    private static final Map<String, List<String>> TREE = new HashMap<>();

    static {
        TREE.put("/", Arrays.asList(
            "acct", "apex", "bin", "cache", "config", "data", "data_mirror",
            "dev", "etc", "init", "mnt", "odm", "oem", "proc", "product",
            "sbin", "sdcard", "storage", "sys", "system", "system_ext", "vendor"
        ));
        TREE.put("/data", Arrays.asList("app", "data", "local", "system", "user"));
        TREE.put("/data/local", Collections.singletonList("tmp"));
        TREE.put("/data/user", Collections.singletonList("0"));
        TREE.put("/data_mirror", Arrays.asList("cur_profiles", "data_ce", "data_de", "ref_profiles"));
        TREE.put("/data_mirror/cur_profiles", Collections.singletonList("0"));
        TREE.put("/data_mirror/data_ce", Collections.singletonList("null"));
        TREE.put("/data_mirror/data_ce/null", Arrays.asList("0", "10", "999"));
        TREE.put("/data_mirror/data_de", Collections.singletonList("null"));
        TREE.put("/data_mirror/data_de/null", Arrays.asList("0", "10", "999"));
        TREE.put("/data_mirror/ref_profiles", Collections.singletonList("0"));
        TREE.put("/dev", Arrays.asList(
            "android_pipe", "blkio", "block", "bus", "cpuctl", "cpuset",
            "fd", "fscklogs", "input", "pts", "snd", "socket", "usb-ffs"
        ));
        TREE.put("/dev/bus", Collections.singletonList("usb"));
        TREE.put("/mnt", Arrays.asList(
            "androidwritable", "appfuse", "asec", "media_rw", "obb",
            "pass_through", "product", "runtime", "sdcard", "secure", "user", "vendor"
        ));
        TREE.put("/odm", Arrays.asList("app", "bin", "etc", "firmware", "framework", "lib", "lib64", "overlay", "priv-app", "usr"));
        TREE.put("/proc", Arrays.asList(
            "acpi", "asound", "bus", "cgroups", "devices", "dma", "driver",
            "fs", "iomem", "irq", "keys", "kmsg", "misc", "scsi", "self",
            "sys", "tty", "uid"
        ));
        TREE.put("/proc/bus", Arrays.asList("input", "pci"));
        TREE.put("/proc/sys", Arrays.asList("abi", "debug", "dev", "fs", "kernel", "net", "user", "vm"));
        TREE.put("/proc/sys/net", Arrays.asList("core", "ipv4", "ipv6", "netfilter", "unix"));
        TREE.put("/product", Arrays.asList("app", "etc", "lib", "lib64", "media", "overlay", "priv-app"));
        TREE.put("/storage", Arrays.asList("emulated", "self"));
        TREE.put("/storage/emulated", Arrays.asList("0", "10", "999", "legacy"));
        TREE.put("/storage/self", Collections.singletonList("primary"));
        TREE.put("/sys", Arrays.asList("block", "bus", "class", "dev", "devices", "firmware", "fs", "kernel", "module", "power"));
        TREE.put("/sys/bus", Arrays.asList("acpi", "clockevents", "clocksource", "container", "cpu", "hdaudio", "hid", "i2c", "machinecheck", "nvmem", "pci", "platform", "pnp", "scsi", "serio", "usb", "virtio", "workqueue"));
        TREE.put("/sys/class", Arrays.asList("android_usb", "bdi", "block", "bsg", "dma", "dmi", "drm", "hidraw", "ieee80211", "input", "konepure", "leds", "mem", "misc", "net", "pci_bus", "power_supply", "ppp", "rc", "rtc", "savu", "scsi_device", "scsi_disk", "scsi_generic", "scsi_host", "sound", "spi_host", "spi_transport", "thermal", "tty", "udc", "virtio-ports", "zram-control"));
        TREE.put("/sys/dev", Arrays.asList("block", "char"));
        TREE.put("/sys/devices", Arrays.asList("breakpoint", "cpu", "msr", "platform", "pnp0", "software", "system", "tracepoint", "virtual"));
        TREE.put("/sys/devices/system", Arrays.asList("clockevents", "clocksource", "container", "cpu", "machinecheck"));
        TREE.put("/sys/devices/virtual", Arrays.asList("android_usb", "bdi", "block", "dmi", "drm", "mem", "misc", "net", "ppp", "sound", "thermal", "tty", "workqueue"));
        TREE.put("/sys/firmware", Arrays.asList("acpi", "devicetree", "dmi"));
        TREE.put("/sys/fs", Arrays.asList("bpf", "cgroup", "ext4", "f2fs", "fuse", "selinux"));
        TREE.put("/system", Arrays.asList("apex", "app", "bin", "etc", "fonts", "framework", "lib", "priv-app", "product", "usr", "vendor", "xbin"));
        TREE.put("/system/etc", Arrays.asList("bluetooth", "init", "permissions", "ppp", "security", "selinux", "sysconfig", "textclassifier"));
        TREE.put("/system/product", Arrays.asList("app", "etc", "lib", "media", "overlay", "priv-app"));
        TREE.put("/system/product/media", Collections.singletonList("audio"));
        TREE.put("/system/usr", Arrays.asList("hyphen-data", "icu", "idc", "keychars", "keylayout", "share"));
        TREE.put("/system_ext", Arrays.asList("bin", "etc", "framework", "lib", "lib64", "priv-app"));
        TREE.put("/vendor", Arrays.asList("bin", "etc", "lib", "odm", "usr"));
        TREE.put("/vendor/etc", Arrays.asList("permissions", "selinux", "wifi"));
        TREE.put("/vendor/odm", Collections.singletonList("etc"));
    }

    public static List<String> getChildren(String path) {
        return TREE.get(path);
    }
}
