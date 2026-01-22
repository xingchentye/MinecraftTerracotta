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

/**
 * EasyTier 下载器类。
 * 负责 EasyTier 可执行文件的下载、校验和解压。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public class EasyTierDownloader {
    /**
     * 日志记录器
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(EasyTierDownloader.class);

    /**
     * 下载基础 URL
     */
    private static final String BASE_URL = "https://hk.gh-proxy.org/https://github.com/EasyTier/EasyTier/releases/download/v2.4.5/";
    
    /**
     * Windows x64 平台 SHA256 校验和
     */
    private static final String SHA256_WIN_X64 = "5d67089df8367bf449a1f60dd4edc4f67da5b88db20f92d69a0c2bcddd1e07c5";

    /**
     * Windows x86 平台 SHA256 校验和
     */
    private static final String SHA256_WIN_X86 = "4afa5694cdfc28b252ba67b412e338fc541cd25f9b4aaa88f39d7790491bcf261";

    /**
     * Linux x64 平台 SHA256 校验和
     */
    private static final String SHA256_LINUX_X64 = "d33d1fe6e06fae6155ca7a6ea214657de8d29c4edd5e16fb51f128bef29d3aec";

    /**
     * Linux ARM64 平台 SHA256 校验和
     */
    private static final String SHA256_LINUX_ARM64 = "df08c842f2ab2b8e9922f13c686a1d0f5a5219775cfdabb3e4a8599c6772201f";

    /**
     * macOS x64 平台 SHA256 校验和
     */
    private static final String SHA256_MACOS_X64 = "282abe285e7802c74e2ef1dfb0186c794c371d8350f4f5b1d6ade12031b82333";

    /**
     * macOS ARM64 平台 SHA256 校验和
     */
    private static final String SHA256_MACOS_ARM64 = "ddf94b070f84d899504ad154666ea3f0369be6cc4da375a2d98143a312daef01";

    /**
     * 下载目录
     */
    private final Path downloadDir;

    /**
     * 构造函数。
     *
     * @param downloadDir 下载目录
     */
    public EasyTierDownloader(Path downloadDir) {
        this.downloadDir = downloadDir;
    }

    /**
     * 下载并解压 EasyTier。
     * 如果已存在有效安装，则跳过下载。
     *
     * @param progressCallback 进度回调函数，接收 0.0 到 1.0 之间的进度值
     * @return 解压后的目录路径
     * @throws IOException 当下载或解压失败时抛出
     * @throws InterruptedException 当下载过程被中断时抛出
     */
    public Path downloadAndExtract(Consumer<Double> progressCallback) throws IOException, InterruptedException {
        String filename = com.multiplayer.ender.logic.PlatformHelper.getDownloadFilename("2.4.5");
        if (filename == null) {
            throw new IOException("Unsupported platform");
        }
        
        String url = BASE_URL + filename;
        Path zipPath = downloadDir.resolve(filename);
        Path extractDir = downloadDir; 
        
        String expectedChecksum = getChecksum(filename);

        if (Files.exists(extractDir) && Files.list(extractDir).count() > 0) {
             
             try (var stream = Files.walk(extractDir, 3)) {
                 boolean hasExecutable = stream.anyMatch(p -> p.getFileName().toString().equals(com.multiplayer.ender.logic.PlatformHelper.getExecutableName()));
                 if (hasExecutable) {
                     LOGGER.info("EasyTier directory exists and valid. Skipping download.");
                     if (progressCallback != null) progressCallback.accept(1.0);
                     
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
        
        
        try (var stream = Files.list(extractDir)) {
             Path firstChild = stream.findFirst().orElse(null);
             if (firstChild != null && Files.isDirectory(firstChild)) {
                 return firstChild;
             }
        }
        return extractDir;
    }
    
    /**
     * 根据文件名获取对应的校验和。
     *
     * @param filename 文件名
     * @return 对应的 SHA256 校验和，如果未知则返回 null
     */
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

    /**
     * 下载文件。
     *
     * @param url 下载 URL
     * @param target 目标路径
     * @param progressCallback 进度回调
     * @throws IOException 当下载失败时抛出
     * @throws InterruptedException 当下载被中断时抛出
     */
    private void downloadFile(String url, Path target, Consumer<Double> progressCallback) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
        
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        
        if (response.statusCode() != 200) {
            throw new IOException("Download failed with status " + response.statusCode());
        }

        long contentLength = response.headers().firstValueAsLong("content-length").orElse(-1L);
        long estimatedLength = contentLength > 0 ? contentLength : 15 * 1024 * 1024; 

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
                    if (contentLength == -1 && progress > 0.99) progress = 0.99; 
                    progressCallback.accept(progress);
                }
            }
        }
    }

    /**
     * 计算文件的 SHA256 校验和。
     *
     * @param file 文件路径
     * @return 十六进制字符串形式的校验和
     * @throws IOException 当读取文件失败时抛出
     */
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

    /**
     * 解压 ZIP 文件。
     *
     * @param zipFile ZIP 文件路径
     * @param targetDir 目标解压目录
     * @throws IOException 当解压失败时抛出
     */
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

    /**
     * 创建新文件对象，并检查路径遍历攻击。
     *
     * @param destinationDir 目标目录
     * @param zipEntry ZIP 条目
     * @return 安全的文件对象
     * @throws IOException 当条目位于目标目录之外时抛出
     */
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

