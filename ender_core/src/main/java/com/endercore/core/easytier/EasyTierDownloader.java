package com.endercore.core.easytier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.function.Consumer;

public class EasyTierDownloader {
    private static final Logger LOGGER = LoggerFactory.getLogger(EasyTierDownloader.class);
    private static final String BASE_URL = "https://hk.gh-proxy.org/https://github.com/EasyTier/EasyTier/releases/download/v2.4.5/";
    
    // Checksums
    private static final String SHA256_WIN_X64 = "5d67089df8367bf449a1f60dd4edc4f67da5b88db20f92d69a0c2bcddd1e07c5";
    private static final String SHA256_WIN_X86 = "4afa5694cdfc28b252ba67b412e338fc541cd25f9b4aaa88f39d7790491bcf261";
    private static final String SHA256_LINUX_X64 = "d33d1fe6e06fae6155ca7a6ea214657de8d29c4edd5e16fb51f128bef29d3aec";
    private static final String SHA256_LINUX_ARM64 = "df08c842f2ab2b8e9922f13c686a1d0f5a5219775cfdabb3e4a8599c6772201f";
    private static final String SHA256_MACOS_X64 = "282abe285e7802c74e2ef1dfb0186c794c371d8350f4f5b1d6ade12031b82333";
    private static final String SHA256_MACOS_ARM64 = "ddf94b070f84d899504ad154666ea3f0369be6cc4da375a2d98143a312daef01";

    private final Path downloadDir;

    public EasyTierDownloader(Path downloadDir) {
        this.downloadDir = downloadDir;
    }

    public Path downloadAndExtract(Consumer<Double> progressCallback) throws IOException, InterruptedException {
        String filename = com.multiplayer.ender.logic.PlatformHelper.getDownloadFilename("2.4.5");
        if (filename == null) {
            throw new IOException("Unsupported platform");
        }
        
        String url = BASE_URL + filename;
        Path zipPath = downloadDir.resolve(filename);
        Path extractDir = downloadDir; // Use base dir instead of "easytier" subdir
        
        String expectedChecksum = getChecksum(filename);

        if (Files.exists(extractDir) && Files.list(extractDir).count() > 0) {
             // 检查是否已解压且包含可执行文件
             try (var stream = Files.walk(extractDir, 3)) {
                 boolean hasExecutable = stream.anyMatch(p -> p.getFileName().toString().equals(com.multiplayer.ender.logic.PlatformHelper.getExecutableName()));
                 if (hasExecutable) {
                     LOGGER.info("EasyTier directory exists and valid. Skipping download.");
                     if (progressCallback != null) progressCallback.accept(1.0);
                     // Return the inner folder which actually contains the executable
                     try (var innerStream = Files.list(extractDir)) {
                         Path firstChild = innerStream.findFirst().orElse(null);
                         if (firstChild != null && Files.isDirectory(firstChild)) {
                             return firstChild;
                         }
                     }
                     return extractDir;
                 }
             } catch (Exception e) {
                 LOGGER.warn("Failed to check existing EasyTier installation", e);
             }
        }

        Files.createDirectories(downloadDir);

        LOGGER.info("Downloading EasyTier from {}", url);
        downloadFile(url, zipPath, progressCallback);

        LOGGER.info("Verifying checksum...");
        String checksum = calculateSHA256(zipPath);
        LOGGER.info("Downloaded file checksum: {}", checksum);
        
        if (expectedChecksum != null && !checksum.equalsIgnoreCase(expectedChecksum)) {
            LOGGER.error("Checksum mismatch! Expected: {}, Got: {}", expectedChecksum, checksum);
            throw new IOException("Checksum verification failed");
        }

        LOGGER.info("Extracting to {}", extractDir);
        unzip(zipPath, extractDir);

        Files.deleteIfExists(zipPath);
        
        // Return the inner folder
        try (var stream = Files.list(extractDir)) {
             Path firstChild = stream.findFirst().orElse(null);
             if (firstChild != null && Files.isDirectory(firstChild)) {
                 return firstChild;
             }
        }
        return extractDir;
    }
    
    private String getChecksum(String filename) {
        switch (filename) {
            case "easytier-windows-x86_64-v2.4.5.zip": return SHA256_WIN_X64;
            case "easytier-windows-i686-v2.4.5.zip": return SHA256_WIN_X86;
            case "easytier-linux-x86_64-v2.4.5.zip": return SHA256_LINUX_X64;
            case "easytier-linux-aarch64-v2.4.5.zip": return SHA256_LINUX_ARM64;
            case "easytier-macos-x86_64-v2.4.5.zip": return SHA256_MACOS_X64;
            case "easytier-macos-aarch64-v2.4.5.zip": return SHA256_MACOS_ARM64;
            default: return null;
        }
    }

    private void downloadFile(String url, Path target, Consumer<Double> progressCallback) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
        
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        
        if (response.statusCode() != 200) {
            throw new IOException("Download failed with status " + response.statusCode());
        }

        long contentLength = response.headers().firstValueAsLong("content-length").orElse(-1L);
        long estimatedLength = contentLength > 0 ? contentLength : 15 * 1024 * 1024; // Fallback to 15MB if content-length is missing

        try (InputStream is = response.body();
             OutputStream os = Files.newOutputStream(target)) {
            
            byte[] buffer = new byte[8192];
            long totalBytesRead = 0;
            int bytesRead;
            
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
                
                if (progressCallback != null) {
                    double progress = (double) totalBytesRead / estimatedLength;
                    if (contentLength == -1 && progress > 0.99) progress = 0.99; // Cap at 99% if estimating
                    progressCallback.accept(progress);
                }
            }
        }
    }

    private String calculateSHA256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream fis = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, n);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new IOException("Failed to calculate checksum", e);
        }
    }

    private void unzip(Path zipFile, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File newFile = newFile(targetDir.toFile(), zipEntry);
                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory " + newFile);
                    }
                } else {
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory " + parent);
                    }
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
        }
    }

    private File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());
        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }
        return destFile;
    }
}

