package com.multiplayer.ender.logic;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 下载管理工具类。
 * 提供异步文件下载和 tar.gz 格式解压功能。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public class DownloadManager {
    /**
     * 日志记录器
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadManager.class);

    /**
     * 异步下载文件。
     *
     * @param url 下载地址
     * @param targetDir 目标目录
     * @param filename 保存的文件名
     * @param progressCallback 进度回调函数，接收 0.0 到 1.0 之间的 double 值
     * @return 包含下载文件路径的 CompletableFuture
     */
    public static CompletableFuture<Path> download(String url, Path targetDir, String filename, Consumer<Double> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                
                String finalUrl = url;
                if (url.startsWith("https://github.com") && !url.contains("gh-proxy")) {
                     finalUrl = "https://hk.gh-proxy.org/" + url;
                }
                
                if (!Files.exists(targetDir)) {
                    Files.createDirectories(targetDir);
                }

                Path targetPath = targetDir.resolve(filename);
                LOGGER.info("开始下载: {}", finalUrl);

                HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(finalUrl)).GET().build();

                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() != 200) {
                    throw new IOException("下载失败，HTTP状态码: " + response.statusCode());
                }

                long contentLength = response.headers().firstValueAsLong("content-length").orElse(-1L);
                
                try (InputStream in = response.body();
                     OutputStream out = Files.newOutputStream(targetPath)) {
                    
                    byte[] buffer = new byte[8192];
                    long totalBytesRead = 0;
                    int bytesRead;
                    
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;
                        
                        if (contentLength > 0 && progressCallback != null) {
                            double progress = (double) totalBytesRead / contentLength;
                            progressCallback.accept(progress);
                        }
                    }
                }
                
                if (progressCallback != null) {
                    progressCallback.accept(1.0);
                }
                
                LOGGER.info("下载完成: {}", targetPath);
                return targetPath;
            } catch (Exception e) {
                LOGGER.error("下载过程中发生错误", e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 解压 .tar.gz 格式的归档文件。
     *
     * @param tarGzPath .tar.gz 文件路径
     * @param outputDir 解压输出目录
     * @throws IOException 当解压过程中发生 I/O 错误时抛出
     */
    public static void extractTarGz(Path tarGzPath, Path outputDir) throws IOException {
        LOGGER.info("正在解压: {}", tarGzPath);
        try (InputStream fi = Files.newInputStream(tarGzPath);
             InputStream bi = new BufferedInputStream(fi);
             InputStream gzi = new GzipCompressorInputStream(bi);
             TarArchiveInputStream tai = new TarArchiveInputStream(gzi)) {

            TarArchiveEntry entry;
            while ((entry = tai.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                
                Path curfile = outputDir.resolve(entry.getName());
                Path parent = curfile.getParent();
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectories(parent);
                }
                
                try (OutputStream out = Files.newOutputStream(curfile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = tai.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                }
            }
        }
    }
}

