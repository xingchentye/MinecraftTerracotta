package com.multiplayer.ender.logic;

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
        OS os = getOS();
        Arch arch = getArch();
        
        if (os == OS.WINDOWS) {
            if (arch == Arch.X86_64) return "easytier-windows-x86_64-v2.4.5.zip";
            return "easytier-windows-i686-v2.4.5.zip";
        } else if (os == OS.LINUX) {
            if (arch == Arch.X86_64) return "easytier-linux-x86_64-v2.4.5.zip";
            if (arch == Arch.ARM64) return "easytier-linux-aarch64-v2.4.5.zip";
        } else if (os == OS.MACOS) {
            if (arch == Arch.X86_64) return "easytier-macos-x86_64-v2.4.5.zip";
            if (arch == Arch.ARM64) return "easytier-macos-aarch64-v2.4.5.zip";
        }
        return null;
    }

    public static String getExecutableName(String version) {
        OS os = getOS();
        if (os == OS.WINDOWS) {
            return "easytier-core.exe";
        }
        return "easytier-core"; 
    }

    public static String getExecutableName() {
        return getExecutableName("2.4.5");
    }

    public static int findAvailablePort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (java.io.IOException e) {
            return -1;
        }
    }
}

