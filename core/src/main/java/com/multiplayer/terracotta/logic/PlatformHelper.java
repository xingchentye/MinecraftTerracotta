package com.multiplayer.terracotta.logic;

import java.util.Locale;

public class PlatformHelper {

    public enum OS {
        WINDOWS, LINUX, MACOS, ANDROID, UNKNOWN
    }

    public enum Arch {
        X86_64, ARM64, UNKNOWN
    }

    public static OS getOS() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        
        if (System.getProperty("java.vendor").toLowerCase().contains("android") || osName.contains("android")) {
            return OS.ANDROID;
        }
        
        if (osName.contains("win")) {
            return OS.WINDOWS;
        } else if (osName.contains("mac")) {
            return OS.MACOS;
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            return OS.LINUX;
        }
        
        return OS.UNKNOWN;
    }

    public static Arch getArch() {
        String osArch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        
        if (osArch.equals("amd64") || osArch.equals("x86_64")) {
            return Arch.X86_64;
        } else if (osArch.equals("aarch64") || osArch.contains("arm64")) {
            return Arch.ARM64;
        }
        
        return Arch.UNKNOWN;
    }

    public static String getDownloadFilename(String version) {
        if (version != null && version.startsWith("v")) {
            version = version.substring(1);
        }

        OS os = getOS();
        Arch arch = getArch();

        if (os == OS.ANDROID) {
            if (arch == Arch.X86_64) return "terracotta-" + version + "-android-x86_64.so";
            if (arch == Arch.ARM64) return "terracotta-" + version + "-android-arm64v8a.so";
        } else if (os == OS.WINDOWS) {
            if (arch == Arch.X86_64) return "terracotta-" + version + "-windows-x86_64-pkg.tar.gz";
        }
        
        return null;
    }

    public static String getExecutableName(String version) {
        if (version != null && version.startsWith("v")) {
            version = version.substring(1);
        }

        OS os = getOS();
        Arch arch = getArch();

        if (os == OS.WINDOWS) {
            if (arch == Arch.X86_64) return "terracotta-" + version + "-windows-x86_64.exe";
            return "terracotta.exe";
        }
        return "terracotta"; 
    }

    public static String getExecutableName() {
        return getExecutableName("0.0.0");
    }
}

