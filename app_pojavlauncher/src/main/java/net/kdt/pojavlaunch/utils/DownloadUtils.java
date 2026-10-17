package net.kdt.pojavlaunch.utils;

import android.util.Log;

import androidx.annotation.Nullable;

import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.concurrent.Callable;

import net.kdt.pojavlaunch.*;
import org.apache.commons.io.*;

@SuppressWarnings("IOStreamConstructor")
public class DownloadUtils {
    public static final String USER_AGENT = Tools.APP_NAME;
    private static final int CONNECT_TIME_OUT = 30000;
    private static final int READ_TIME_OUT = 120000;

    public static void download(String url, OutputStream os) throws IOException {
        download(new URL(url), os);
    }

    public static void download(URL url, OutputStream os) throws IOException {
        InputStream is = null;
        try {
            // System.out.println("Connecting: " + url.toString());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(CONNECT_TIME_OUT);
            conn.setReadTimeout(READ_TIME_OUT);
            conn.setDoInput(true);
            conn.connect();
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Server returned HTTP " + conn.getResponseCode()
                        + ": " + conn.getResponseMessage());
            }
            is = conn.getInputStream();
            IOUtils.copy(is, os);
        } catch (IOException e) {
            throw new IOException("Unable to download from " + url, e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static String downloadString(String url) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        download(url, bos);
        bos.close();
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    public static void downloadFile(String url, File out) throws IOException {
        downloadToFile(url, out, null, null);
    }

    public static void downloadFileMonitored(String urlInput, File outputFile, @Nullable byte[] buffer,
                                             Tools.DownloaderFeedback monitor) throws IOException {
        downloadToFile(urlInput, outputFile, buffer, monitor);
    }

    public static boolean isZipOrJarExtension(@Nullable File file) {
        if (file == null) return false;
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".jar") || name.endsWith(".zip") || name.endsWith(".aar") || name.endsWith(".mrpack");
    }

    public static boolean isValidZipOrJar(@Nullable File file) {
        if (file == null || !file.isFile() || file.length() < 22) return false;
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(file)) {
            return zip.size() >= 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Download with HTTP Range resume to a sibling .part file and atomic rename on completion
     * so an interrupted transfer never leaves a truncated file at outputFile.
     */
    private static void downloadToFile(String urlInput, File outputFile, @Nullable byte[] buffer,
                                       @Nullable Tools.DownloaderFeedback monitor) throws IOException {
        downloadToFileInternal(urlInput, outputFile, buffer, monitor, true);
    }

    private static void downloadToFileInternal(String urlInput, File outputFile, @Nullable byte[] buffer,
                                               @Nullable Tools.DownloaderFeedback monitor, boolean allowRange) throws IOException {
        FileUtils.ensureParentDirectory(outputFile);
        File partFile = new File(outputFile.getParentFile(), outputFile.getName() + ".part");
        long existing = (allowRange && partFile.isFile()) ? partFile.length() : 0;
        if (!allowRange && partFile.exists()) {
            partFile.delete();
        }
        HttpURLConnection conn = (HttpURLConnection) new URL(urlInput).openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setConnectTimeout(CONNECT_TIME_OUT);
        conn.setReadTimeout(READ_TIME_OUT);
        conn.setInstanceFollowRedirects(true);
        if (existing > 0) {
            conn.setRequestProperty("Range", "bytes=" + existing + "-");
        }
        InputStream readStr = null;
        try {
            conn.connect();
            int code = conn.getResponseCode();
            if (code == 416) {
                conn.disconnect();
                partFile.delete();
                if (allowRange) {
                    downloadToFileInternal(urlInput, outputFile, buffer, monitor, false);
                    return;
                }
                throw new IOException("Server returned HTTP 416 for " + urlInput);
            }
            boolean append = existing > 0 && code == HttpURLConnection.HTTP_PARTIAL;
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                throw new IOException("Server returned HTTP " + code + ": " + conn.getResponseMessage());
            }
            if (!append) existing = 0;
            int bodyLength = conn.getContentLength();
            int total = bodyLength < 0 ? -1 : (append ? (int) Math.min(existing + bodyLength, Integer.MAX_VALUE) : bodyLength);
            readStr = conn.getInputStream();
            if (buffer == null) buffer = new byte[65536];
            try (FileOutputStream fos = new FileOutputStream(partFile, append)) {
                int current;
                long overall = existing;
                while ((current = readStr.read(buffer)) != -1) {
                    overall += current;
                    fos.write(buffer, 0, current);
                    if (monitor != null) {
                        monitor.updateProgress((int) Math.min(overall, Integer.MAX_VALUE), total);
                    }
                }
            }
            if (isZipOrJarExtension(outputFile) && !isValidZipOrJar(partFile)) {
                partFile.delete();
                if (allowRange && existing > 0) {
                    conn.disconnect();
                    downloadToFileInternal(urlInput, outputFile, buffer, monitor, false);
                    return;
                }
                throw new IOException("Corrupt or truncated ZIP/JAR downloaded from " + urlInput);
            }
            if (outputFile.exists() && !outputFile.delete()) {
                org.apache.commons.io.FileUtils.deleteQuietly(outputFile);
            }
            if (!partFile.renameTo(outputFile)) {
                org.apache.commons.io.FileUtils.copyFile(partFile, outputFile);
                partFile.delete();
            }
        } catch (IOException e) {
            if (partFile.length() < 1) {
                partFile.delete();
            }
            throw new IOException("Unable to download from " + urlInput, e);
        } finally {
            if (readStr != null) {
                try { readStr.close(); } catch (IOException ignored) {}
            }
            conn.disconnect();
        }
    }

    public static <T> T downloadStringCached(String url, String cacheName, ParseCallback<T> parseCallback) throws IOException, ParseException{
        File cacheDestination = new File(Tools.DIR_CACHE, "string_cache/"+cacheName);
        if(cacheDestination.isFile() &&
                cacheDestination.canRead() &&
                System.currentTimeMillis() < (cacheDestination.lastModified() + 86400000)) {
            try {
                String cachedString = Tools.read(new FileInputStream(cacheDestination));
                return parseCallback.process(cachedString);
            }catch(IOException e) {
                Log.i("DownloadUtils", "Failed to read the cached file", e);
            }catch (ParseException e) {
                Log.i("DownloadUtils", "Failed to parse the cached file", e);
            }
        }
        String urlContent = DownloadUtils.downloadString(url);
        // if we download the file and fail parsing it, we will yeet outta there
        // and not cache the unparseable sting. We will return this after trying to save the downloaded
        // string into cache
        T parseResult = parseCallback.process(urlContent);

        boolean tryWriteCache;
        if(cacheDestination.exists()) {
            tryWriteCache = cacheDestination.canWrite();
        } else {
            tryWriteCache = FileUtils.ensureParentDirectorySilently(cacheDestination);
        }

        if(tryWriteCache) try {
            Tools.write(cacheDestination.getAbsolutePath(), urlContent);
        }catch(IOException e) {
            Log.i("DownloadUtils", "Failed to cache the string", e);
        }
        return parseResult;
    }

    private static <T> T downloadFile(Callable<T> downloadFunction) throws IOException{
        try {
            return downloadFunction.call();
        } catch (IOException e){
            throw e;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean verifyFile(File file, @Nullable String sha1) {
        if (file == null || !file.isFile() || file.length() <= 0) return false;
        if (isZipOrJarExtension(file) && !isValidZipOrJar(file)) return false;
        return sha1 == null || Tools.compareSHA1(file, sha1);
    }

    public static <T> T ensureSha1(File outputFile, @Nullable String sha1, Callable<T> downloadFunction) throws IOException {
        // Skip if needed
        if(sha1 == null) {
            // Verify non-empty and valid ZIP/JAR header even when SHA1 is unknown.
            if(verifyFile(outputFile, null)) return null;
            if(outputFile.exists()) outputFile.delete();
            return downloadFile(downloadFunction);
        }

        int attempts = 0;
        boolean fileOkay = verifyFile(outputFile, sha1);
        T result = null;
        while (attempts < 5 && !fileOkay){
            attempts++;
            if (outputFile.exists()) outputFile.delete();
            downloadFile(downloadFunction);
            fileOkay = verifyFile(outputFile, sha1);
        }
        if(!fileOkay) throw new SHA1VerificationException("SHA1 verifcation failed after 5 download attempts");
        return result;
    }

    /**
     * Get the content length for a given URL.
     * @param url the URL to get the length for
     * @return the length in bytes or -1 if not available
     * @throws IOException if an I/O error occurs.
     */
    public static long getContentLength(String url) throws IOException {
        HttpURLConnection urlConnection = (HttpURLConnection) new URL(url).openConnection();
        urlConnection.setRequestMethod("HEAD");
        urlConnection.setRequestProperty("User-Agent", USER_AGENT);
        urlConnection.setConnectTimeout(CONNECT_TIME_OUT);
        urlConnection.setReadTimeout(READ_TIME_OUT);
        urlConnection.setDoInput(false);
        urlConnection.setDoOutput(false);
        urlConnection.connect();
        int responseCode = urlConnection.getResponseCode();
        if(responseCode >= 200 && responseCode <= 299) return urlConnection.getContentLength();
        return -1;
    }

    public interface ParseCallback<T> {
        T process(String input) throws ParseException;
    }
    public static class ParseException extends Exception {
        public ParseException(Exception e) {
            super(e);
        }
    }

    public static class SHA1VerificationException extends IOException {
        public SHA1VerificationException(String message) {
            super(message);
        }
    }
}

