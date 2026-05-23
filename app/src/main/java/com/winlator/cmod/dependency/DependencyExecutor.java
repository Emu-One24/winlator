package com.winlator.cmod.dependency;

import android.app.Activity;
import android.util.Log;

import com.winlator.cmod.core.WineRegistryEditor;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.core.Callback;
import com.winlator.cmod.core.PreloaderDialog;
import com.winlator.cmod.core.TarCompressorUtils;
import com.winlator.cmod.xenvironment.components.GuestProgramLauncherComponent;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class DependencyExecutor {
    private static final String TAG = "DependencyExecutor";

    public static void execute(
        final Activity activity,
        final PreloaderDialog preloaderDialog,
        final GuestProgramLauncherComponent launcher,
        final Container container,
        final File imageFsRootDir,
        final List<Dependency> dependencies
    ) {
        File tmpDir = new File(imageFsRootDir, "usr/tmp");
        if (!tmpDir.exists()) tmpDir.mkdirs();

        for (Dependency dep : dependencies) {
            preloaderDialog.showOnUiThread("Installing " + dep.getName() + "...");
            for (Dependency.Step step : dep.getSteps()) {
                executeStep(activity, preloaderDialog, launcher, container, tmpDir, dep, step);
            }
        }
    }

    private static void executeStep(
        final Activity activity,
        final PreloaderDialog preloaderDialog,
        final GuestProgramLauncherComponent launcher,
        final Container container,
        final File tmpDir,
        final Dependency dep,
        final Dependency.Step step
    ) {
        String action = step.getAction();
        if (action == null) return;

        // Skip step if it targets a specific arch and we're not on it
        if (!step.getForArchs().isEmpty()) {
            boolean is64bit = new java.io.File(tmpDir.getParentFile(), "home/xuser/.wine/drive_c/windows/syswow64").exists();
            String currentArch = is64bit ? "win64" : "win32";
            if (!step.getForArchs().contains(currentArch)) {
                Log.d(TAG, "Skipping step (arch mismatch: need " + step.getForArchs() + ", have " + currentArch + ")");
                return;
            }
        }

        Log.d(TAG, "Executing action: " + action + " for " + dep.getName());

        switch (action) {
            case "uninstall": {
                final CountDownLatch latch = new CountDownLatch(1);
                String wineCommand = "wine uninstaller --uninstall \"" + step.getFileName() + "\"";
                preloaderDialog.showOnUiThread("Uninstalling " + step.getFileName() + "...");
                launcher.execWineCommand(wineCommand, step.getEnvironment(), (status) -> latch.countDown());
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                break;
            }

            case "set_windows":
            case "windows":
            case "use_windows": {
                final CountDownLatch latch = new CountDownLatch(1);
                String wineCommand = "wine winecfg -v " + step.getVersion();
                preloaderDialog.showOnUiThread("Setting Windows version to " + step.getVersion() + "...");
                launcher.execWineCommand(wineCommand, step.getEnvironment(), (status) -> latch.countDown());
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                break;
            }

            case "install_exe": {
                String originalFileName = step.getFileName();
                String localFileName = step.getRename() != null && !step.getRename().isEmpty() ? step.getRename() : originalFileName;
                File localFile = new File(tmpDir, localFileName);
                File installerFile = null;

                try {
                    if (originalFileName != null && originalFileName.endsWith(".zip")) {
                        preloaderDialog.showOnUiThread("Downloading zip for " + dep.getName() + "...");
                        File tempZip = new File(tmpDir, "temp_" + System.currentTimeMillis() + ".zip");
                        downloadFile(step.getUrl(), tempZip, dep.getName(), preloaderDialog);
                        
                        preloaderDialog.showOnUiThread("Extracting " + dep.getName() + "...");
                        unzipFile(tempZip, tmpDir);
                        tempZip.delete();
                        
                        installerFile = new File(tmpDir, step.getFileName());
                    } else {
                        preloaderDialog.showOnUiThread("Downloading " + dep.getName() + "...");
                        downloadFile(step.getUrl(), localFile, dep.getName(), preloaderDialog);
                        installerFile = localFile;
                    }

                    if (installerFile != null && installerFile.exists()) {
                        final CountDownLatch latch = new CountDownLatch(1);
                        String resolution = container != null ? container.getScreenSize() : "1280x720";
                        String wineCommand = "wine explorer /desktop=shell," + resolution + " Z:\\usr\\tmp\\" + installerFile.getName();
                        String args = step.getArguments();
                        if (args == null || args.isEmpty()) {
                            args = getAutoSilentArguments(installerFile.getName());
                        }
                        if (args != null && !args.isEmpty()) {
                            wineCommand += " " + args;
                        }
                        
                        preloaderDialog.showOnUiThread("Installing " + dep.getName() + "...");
                        launcher.execWineCommand(wineCommand, step.getEnvironment(), (status) -> latch.countDown());
                        try {
                            latch.await();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        waitForInstallerToExit();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (installerFile != null && installerFile.exists()) {
                        installerFile.delete();
                    }
                }
                break;
            }

            case "install_msi": {
                String originalFileName = step.getFileName();
                String localFileName = step.getRename() != null && !step.getRename().isEmpty() ? step.getRename() : originalFileName;
                File localFile = new File(tmpDir, localFileName);
                File installerFile = null;

                try {
                    if (originalFileName != null && originalFileName.endsWith(".zip")) {
                        preloaderDialog.showOnUiThread("Downloading zip for " + dep.getName() + "...");
                        File tempZip = new File(tmpDir, "temp_" + System.currentTimeMillis() + ".zip");
                        downloadFile(step.getUrl(), tempZip, dep.getName(), preloaderDialog);
                        
                        preloaderDialog.showOnUiThread("Extracting " + dep.getName() + "...");
                        unzipFile(tempZip, tmpDir);
                        tempZip.delete();
                        
                        installerFile = new File(tmpDir, step.getFileName());
                    } else {
                        preloaderDialog.showOnUiThread("Downloading " + dep.getName() + "...");
                        downloadFile(step.getUrl(), localFile, dep.getName(), preloaderDialog);
                        installerFile = localFile;
                    }

                    if (installerFile != null && installerFile.exists()) {
                        final CountDownLatch latch = new CountDownLatch(1);
                        String resolution = container != null ? container.getScreenSize() : "1280x720";
                        String wineCommand = "wine explorer /desktop=shell," + resolution + " msiexec /i Z:\\usr\\tmp\\" + installerFile.getName() + " /qn";
                        if (step.getArguments() != null && !step.getArguments().isEmpty()) {
                            wineCommand += " " + step.getArguments();
                        }
                        
                        preloaderDialog.showOnUiThread("Installing MSI " + dep.getName() + "...");
                        launcher.execWineCommand(wineCommand, step.getEnvironment(), (status) -> latch.countDown());
                        try {
                            latch.await();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        waitForInstallerToExit();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (installerFile != null && installerFile.exists()) {
                        installerFile.delete();
                    }
                }
                break;
            }

            case "override_dll":
            case "dll": {
                // Apply via wine reg add — works regardless of whether container is available
                if (step.getDll() != null && step.getType() != null) {
                    final CountDownLatch latch = new CountDownLatch(1);
                    String wineCommand = "wine reg add \"HKCU\\Software\\Wine\\DllOverrides\" /v \"" + step.getDll() + "\" /t REG_SZ /d \"" + step.getType() + "\" /f";
                    preloaderDialog.showOnUiThread("Overriding DLL " + step.getDll() + "...");
                    launcher.execWineCommand(wineCommand, step.getEnvironment(), (status) -> latch.countDown());
                    try { latch.await(); } catch (InterruptedException e) { e.printStackTrace(); }
                }
                for (Dependency.BundleEntry entry : step.getBundle()) {
                    if (entry.getValue() != null && entry.getData() != null) {
                        final CountDownLatch latch = new CountDownLatch(1);
                        String wineCommand = "wine reg add \"HKCU\\Software\\Wine\\DllOverrides\" /v \"" + entry.getValue() + "\" /t REG_SZ /d \"" + entry.getData() + "\" /f";
                        launcher.execWineCommand(wineCommand, step.getEnvironment(), (status) -> latch.countDown());
                        try { latch.await(); } catch (InterruptedException e) { e.printStackTrace(); }
                    }
                }
                // Also write directly to user.reg if container is available
                if (container != null) {
                    File userRegFile = new File(container.getRootDir(), ".wine/user.reg");
                    try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
                        if (step.getDll() != null && step.getType() != null) {
                            registryEditor.setStringValue("Software\\Wine\\DllOverrides", step.getDll(), step.getType());
                        }
                        for (Dependency.BundleEntry entry : step.getBundle()) {
                            if (entry.getValue() != null && entry.getData() != null) {
                                registryEditor.setStringValue("Software\\Wine\\DllOverrides", entry.getValue(), entry.getData());
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            }

            case "register_dll":
            case "register": {
                // Handle single dll: field
                String singleDll = step.getDll() != null ? step.getDll() : step.getFileName();
                if (singleDll != null) {
                    final CountDownLatch latch = new CountDownLatch(1);
                    preloaderDialog.showOnUiThread("Registering " + singleDll + "...");
                    launcher.execWineCommand("wine regsvr32 /s " + singleDll, step.getEnvironment(), (status) -> latch.countDown());
                    try { latch.await(); } catch (InterruptedException e) { e.printStackTrace(); }
                }
                // Handle dlls: list
                for (String dllEntry : step.getDlls()) {
                    if (dllEntry == null || dllEntry.isEmpty()) continue;
                    final CountDownLatch latch = new CountDownLatch(1);
                    preloaderDialog.showOnUiThread("Registering " + dllEntry + "...");
                    launcher.execWineCommand("wine regsvr32 /s " + dllEntry, step.getEnvironment(), (status) -> latch.countDown());
                    try { latch.await(); } catch (InterruptedException e) { e.printStackTrace(); }
                }
                break;
            }

            case "write_registry":
            case "set_register_key":
            case "registry": {
                final CountDownLatch latch = new CountDownLatch(1);
                String key = step.getKey();
                String value = step.getValue();
                String data = step.getData();
                String regType = mapRegistryType(step.getType());
                
                String wineCommand = "wine reg add \"" + key + "\"";
                if (value != null) {
                    wineCommand += " /v \"" + value + "\" /t " + regType + " /d \"" + data + "\" /f";
                } else {
                    wineCommand += " /ve /t " + regType + " /d \"" + data + "\" /f";
                }
                
                preloaderDialog.showOnUiThread("Writing registry key...");
                launcher.execWineCommand(wineCommand, step.getEnvironment(), (status) -> latch.countDown());
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                break;
            }

            case "cab_extract":
            case "get_from_cab": {
                // If there's a URL, download it first
                String fileName = step.getFileName() != null ? step.getFileName() : step.getSource();
                if (fileName != null && step.getUrl() != null && step.getUrl().startsWith("http")) {
                    File localFile = new File(tmpDir, fileName);
                    if (!localFile.exists()) {
                        try {
                            preloaderDialog.showOnUiThread("Downloading " + fileName + "...");
                            downloadFile(step.getUrl(), localFile, dep.getName(), preloaderDialog);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }

                String source = step.getSource() != null ? step.getSource() : step.getFileName();
                String dest = step.getDest();
                if (source == null || dest == null) return;

                File destFolder = new File(tmpDir, dest);
                destFolder.mkdirs();

                final CountDownLatch latch = new CountDownLatch(1);
                String sourceWine = source.replace('/', '\\');
                String destWine = dest.replace('/', '\\');
                String wineCommand = "wine extrac32 /y /e /l \"Z:\\usr\\tmp\\" + destWine + "\" \"Z:\\usr\\tmp\\" + sourceWine + "\"";
                
                preloaderDialog.showOnUiThread("Extracting cabinet files...");
                launcher.execWineCommand(wineCommand, step.getEnvironment(), (status) -> latch.countDown());
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                break;
            }

            case "copy_file":
            case "copy_dll":
            case "copy": {
                if (container == null) { Log.w(TAG, "copy step skipped: container is null"); break; }
                String sourceDirRel = step.getUrl() != null ? step.getUrl() : step.getSource();
                String wildcard = step.getFileName();
                String destRel = step.getDest();
                if (sourceDirRel == null || wildcard == null || destRel == null) return;

                File srcDir = new File(tmpDir, sourceDirRel);
                File destDir = resolveDestinationPath(container, destRel);
                
                preloaderDialog.showOnUiThread("Copying " + wildcard + "...");
                copyFilesWithWildcard(srcDir, destDir, wildcard);
                break;
            }

            case "delete_file":
            case "delete_dlls":
            case "delete": {
                if (container == null) { Log.w(TAG, "delete step skipped: container is null"); break; }
                String targetRel = step.getFileName() != null ? step.getFileName() : step.getSource();
                if (targetRel == null) return;

                File driveC = new File(container.getRootDir(), ".wine/drive_c");
                preloaderDialog.showOnUiThread("Cleaning file settings...");
                deleteFilesWithWildcard(driveC, targetRel);
                break;
            }

            case "download_archive":
            case "download": {
                String fileName = step.getFileName();
                String localFileName = step.getRename() != null && !step.getRename().isEmpty() ? step.getRename() : fileName;
                File localFile = new File(tmpDir, localFileName);
                
                try {
                    preloaderDialog.showOnUiThread("Downloading " + dep.getName() + "...");
                    downloadFile(step.getUrl(), localFile, dep.getName(), preloaderDialog);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            }

            case "archive_extract":
            case "unzip":
            case "extract": {
                String fileName = step.getFileName() != null ? step.getFileName() : step.getRename();
                if (fileName == null && step.getUrl() != null) {
                    fileName = step.getUrl().substring(step.getUrl().lastIndexOf('/') + 1);
                }
                if (fileName == null) return;

                File archiveFile = new File(tmpDir, fileName);
                
                // If it doesn't exist but has a URL, download it first
                if (!archiveFile.exists() && step.getUrl() != null && step.getUrl().startsWith("http")) {
                    try {
                        preloaderDialog.showOnUiThread("Downloading " + dep.getName() + "...");
                        downloadFile(step.getUrl(), archiveFile, dep.getName(), preloaderDialog);
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }
                }

                String dest = step.getDest();
                File destDir = dest != null ? new File(tmpDir, dest) : tmpDir;
                destDir.mkdirs();

                preloaderDialog.showOnUiThread("Unpacking " + dep.getName() + "...");
                if (fileName.endsWith(".zip")) {
                    try {
                        unzipFile(archiveFile, destDir);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else if (fileName.endsWith(".xz") || fileName.endsWith(".tar.xz")) {
                    TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, archiveFile, destDir);
                } else if (fileName.endsWith(".zst") || fileName.endsWith(".tar.zst")) {
                    TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, archiveFile, destDir);
                }
                break;
            }

            case "install_fonts":
            case "install_cab_fonts": {
                if (container == null) { Log.w(TAG, "install_fonts step skipped: container is null"); break; }
                String sourceDirRel = step.getUrl() != null ? step.getUrl() : step.getSource();
                if (sourceDirRel == null) return;

                File srcDir = new File(tmpDir, sourceDirRel);
                File fontsDir = new File(container.getRootDir(), ".wine/drive_c/windows/Fonts");
                fontsDir.mkdirs();

                preloaderDialog.showOnUiThread("Installing fonts...");
                for (String font : step.getFonts()) {
                    File fontSrc = new File(srcDir, font);
                    if (fontSrc.exists()) {
                        try {
                            com.winlator.cmod.core.FileUtils.copy(fontSrc, new File(fontsDir, font));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                break;
            }

            case "register_font": {
                final CountDownLatch latch = new CountDownLatch(1);
                String fontName = step.getName() != null ? step.getName() : step.getFileName();
                String fontFile = step.getFileName() != null ? step.getFileName() : step.getUrl();
                if (fontName == null || fontFile == null) return;

                String wineCommand = "wine reg add \"HKLM\\Software\\Microsoft\\Windows NT\\CurrentVersion\\Fonts\" /v \"" + fontName + " (TrueType)\" /d \"" + fontFile + "\" /f";
                preloaderDialog.showOnUiThread("Registering font " + fontName + "...");
                launcher.execWineCommand(wineCommand, step.getEnvironment(), (status) -> latch.countDown());
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                break;
            }

            case "replace_font": {
                // Map each replacement font name → target font via FontSubstitutes registry
                String targetFont = step.getFont();
                if (targetFont == null || targetFont.isEmpty()) break;
                for (String replacement : step.getReplace()) {
                    if (replacement == null || replacement.isEmpty()) continue;
                    final CountDownLatch latch = new CountDownLatch(1);
                    String wineCommand = "wine reg add \"HKLM\\Software\\Microsoft\\Windows NT\\CurrentVersion\\FontSubstitutes\" /v \"" + replacement + "\" /t REG_SZ /d \"" + targetFont + "\" /f";
                    preloaderDialog.showOnUiThread("Substituting font " + replacement + "...");
                    launcher.execWineCommand(wineCommand, step.getEnvironment(), (status) -> latch.countDown());
                    try { latch.await(); } catch (InterruptedException e) { e.printStackTrace(); }
                }
                break;
            }

            case "run_wine":
            case "run_winecommand":
            case "wine":
            case "winecommand": {
                final CountDownLatch latch = new CountDownLatch(1);
                String exe = step.getFileName() != null ? step.getFileName() : step.getSource();
                if (exe == null) exe = step.getUrl();
                if (exe == null) return;

                String wineCommand = exe.toLowerCase().startsWith("wine") ? exe : "wine " + exe;
                if (step.getArguments() != null && !step.getArguments().isEmpty()) {
                    wineCommand += " " + step.getArguments();
                }

                preloaderDialog.showOnUiThread("Running Wine command...");
                launcher.execWineCommand(wineCommand, step.getEnvironment(), (status) -> latch.countDown());
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                waitForInstallerToExit();
                break;
            }

            case "run_script":
            case "script": {
                String scriptFile = step.getFileName() != null ? step.getFileName() : step.getSource();
                if (scriptFile == null) return;

                final CountDownLatch latch = new CountDownLatch(1);
                String command;
                if (scriptFile.endsWith(".bat") || scriptFile.endsWith(".cmd")) {
                    command = "wine cmd /c Z:\\usr\\tmp\\" + scriptFile;
                } else {
                    command = "/bin/sh /usr/tmp/" + scriptFile;
                }

                preloaderDialog.showOnUiThread("Running script " + scriptFile + "...");
                launcher.execWineCommand(command, step.getEnvironment(), (status) -> latch.countDown());
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                waitForInstallerToExit();
                break;
            }
        }
    }

    private static void downloadFile(String fileUrl, File destFile, String depName, PreloaderDialog preloaderDialog) throws IOException {
        HttpURLConnection conn = null;
        int redirects = 0;
        while (redirects < 5) {
            URL url = new URL(fileUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(true);
            conn.connect();

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                responseCode == 307 || responseCode == 308) {
                String newUrl = conn.getHeaderField("Location");
                if (newUrl != null) {
                    fileUrl = newUrl;
                    redirects++;
                    conn.disconnect();
                    continue;
                }
            }
            break;
        }

        if (conn == null) throw new IOException("Could not connect");
        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP error code: " + responseCode);
        }

        long fileLength = conn.getContentLength();
        try (BufferedInputStream in = new BufferedInputStream(conn.getInputStream());
             FileOutputStream out = new FileOutputStream(destFile)) {
            byte[] data = new byte[4096];
            long total = 0;
            int count;
            long lastUpdateTime = 0;
            while ((count = in.read(data)) != -1) {
                total += count;
                out.write(data, 0, count);
                
                long now = System.currentTimeMillis();
                if (now - lastUpdateTime > 200) {
                    int percent = (int) (total * 100 / (fileLength > 0 ? fileLength : 1));
                    String msg = "Downloading " + depName + "... (" + percent + "%)";
                    preloaderDialog.showOnUiThread(msg);
                    lastUpdateTime = now;
                }
            }
        } finally {
            conn.disconnect();
        }
    }

    private static void unzipFile(File zipFile, File destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new java.io.FileInputStream(zipFile)))) {
            ZipEntry ze;
            byte[] buffer = new byte[4096];
            while ((ze = zis.getNextEntry()) != null) {
                File file = new File(destDir, ze.getName());
                if (ze.isDirectory()) {
                    if (!file.isDirectory()) file.mkdirs();
                } else {
                    File parent = file.getParentFile();
                    if (parent != null && !parent.isDirectory()) parent.mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static File resolveDestinationPath(Container container, String dest) {
        File driveC = new File(container.getRootDir(), ".wine/drive_c");
        String norm = dest.toLowerCase().trim();
        if (norm.equals("win32") || norm.equals("syswow64")) {
            File syswow64 = new File(driveC, "windows/syswow64");
            if (syswow64.exists()) return syswow64;
            return new File(driveC, "windows/system32");
        } else if (norm.equals("win64") || norm.equals("system32")) {
            return new File(driveC, "windows/system32");
        }
        
        // Remove direct prefixes like drive_c/ or C:\ or c:\
        String cleaned = dest.replace('\\', '/');
        if (cleaned.startsWith("drive_c/")) {
            cleaned = cleaned.substring(8);
        } else if (cleaned.startsWith("c:/") || cleaned.startsWith("C:/")) {
            cleaned = cleaned.substring(3);
        }
        return new File(driveC, cleaned);
    }

    private static void copyFilesWithWildcard(File srcDir, File destDir, String wildcard) {
        if (!srcDir.exists() || !srcDir.isDirectory()) return;
        if (!destDir.exists()) destDir.mkdirs();
        
        File[] files = srcDir.listFiles();
        if (files == null) return;
        
        final java.util.regex.Pattern pattern = wildcardToRegexPattern(wildcard);
        for (File file : files) {
            if (file.isFile() && pattern.matcher(file.getName()).matches()) {
                try {
                    com.winlator.cmod.core.FileUtils.copy(file, new File(destDir, file.getName()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void deleteFilesWithWildcard(File baseDir, String relativePathWithWildcard) {
        String normalized = relativePathWithWildcard.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        String dirPath = lastSlash >= 0 ? normalized.substring(0, lastSlash) : "";
        String wildcard = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
        
        // Remove drive_c/ prefix from target directory path
        if (dirPath.startsWith("drive_c/")) {
            dirPath = dirPath.substring(8);
        }
        
        File dir = new File(baseDir, dirPath);
        if (!dir.exists() || !dir.isDirectory()) return;
        
        File[] files = dir.listFiles();
        if (files == null) return;
        
        java.util.regex.Pattern pattern = wildcardToRegexPattern(wildcard);
        for (File file : files) {
            if (file.isFile() && pattern.matcher(file.getName()).matches()) {
                file.delete();
            }
        }
    }

    private static java.util.regex.Pattern wildcardToRegexPattern(String wildcard) {
        if (wildcard == null) return java.util.regex.Pattern.compile(".*");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < wildcard.length(); i++) {
            char c = wildcard.charAt(i);
            if (c == '*') sb.append(".*");
            else if (c == '?') sb.append(".");
            else if ("\\.[]{}()+-^$|#".indexOf(c) >= 0) sb.append("\\").append(c);
            else sb.append(c);
        }
        return java.util.regex.Pattern.compile(sb.toString(), java.util.regex.Pattern.CASE_INSENSITIVE);
    }

    private static String mapRegistryType(String type) {
        if (type == null) return "REG_SZ";
        String upper = type.toUpperCase();
        if (upper.equals("STRING") || upper.equals("REG_SZ")) return "REG_SZ";
        if (upper.equals("DWORD") || upper.equals("REG_DWORD")) return "REG_DWORD";
        if (upper.equals("BINARY") || upper.equals("REG_BINARY")) return "REG_BINARY";
        if (upper.equals("EXPANDSTRING") || upper.equals("REG_EXPAND_SZ")) return "REG_EXPAND_SZ";
        return "REG_SZ";
    }

    private static String getAutoSilentArguments(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.contains("adobeair") || lower.contains("air")) {
            return "-silent -eulaAccepted";
        } else if (lower.contains("vc_redist") || lower.contains("vcredist") || lower.contains("dotnet") || lower.contains("ndp") || lower.contains("msxml")) {
            return "/quiet /norestart";
        } else if (lower.contains("dxwebsetup") || lower.contains("directx") || lower.contains("dxredist") || lower.contains("d3d") ||
                   lower.matches(".*32\\.exe") || lower.contains("font") || lower.contains("msft")) {
            return "/q";
        } else if (lower.contains("physx")) {
            return "/quiet /passive /norestart";
        } else if (lower.contains("oalinst") || lower.contains("openal")) {
            return "/S";
        } else if (lower.contains("k-lite") || lower.contains("klite") || lower.contains("codec_pack")) {
            // K-Lite is an Inno Setup installer — use /silent and /suppressmsgboxes to avoid VCL handle errors
            return "/silent /norestart /suppressmsgboxes";
        } else {
            // Generic NSIS silent install
            return "/verysilent /norestart";
        }
    }

    private static String getProcessName(int pid) {
        try {
            java.io.File file = new java.io.File("/proc/" + pid + "/stat");
            if (file.exists()) {
                try (java.io.FileInputStream fr = new java.io.FileInputStream(file);
                     java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(fr))) {
                    String line = br.readLine();
                    if (line != null) {
                        int start = line.indexOf('(');
                        int end = line.lastIndexOf(')');
                        if (start >= 0 && end > start) {
                            return line.substring(start + 1, end);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static java.util.ArrayList<Integer> listGuestPids() {
        java.util.ArrayList<Integer> guestPids = new java.util.ArrayList<>();
        java.io.File proc = new java.io.File("/proc");
        String[] allPids = proc.list((dir, name) -> name.matches("[0-9]+"));
        if (allPids == null) return guestPids;

        String appPkg = "com.winlator.cmod";
        for (String pidStr : allPids) {
            try {
                int pid = Integer.parseInt(pidStr);
                java.io.File exeSymlink = new java.io.File("/proc/" + pid + "/exe");
                if (exeSymlink.exists()) {
                    String targetPath = exeSymlink.getCanonicalPath();
                    if (targetPath.contains(appPkg) && !targetPath.contains("app_process")) {
                        guestPids.add(pid);
                    }
                }
            } catch (Exception ignored) {}
        }
        return guestPids;
    }

    private static void waitForInstallerToExit() {
        Log.d(TAG, "Waiting for installer and its child processes to exit...");
        long startTime = System.currentTimeMillis();
        long timeout = 5 * 60 * 1000; // 5 minutes max timeout

        while (System.currentTimeMillis() - startTime < timeout) {
            java.util.ArrayList<Integer> pids = listGuestPids();
            boolean installerRunning = false;

            for (int pid : pids) {
                String processName = getProcessName(pid);
                if (processName != null) {
                    String lower = processName.toLowerCase();
                    if (!lower.contains("wineserver") &&
                        !lower.contains("services.exe") &&
                        !lower.contains("winedevice.exe") &&
                        !lower.contains("plugplay.exe") &&
                        !lower.contains("rpcss.exe") &&
                        !lower.contains("svchost.exe") &&
                        !lower.contains("conhost.exe")) {
                        Log.d(TAG, "Installer process still active: " + processName + " (PID: " + pid + ")");
                        installerRunning = true;
                        break;
                    }
                }
            }

            if (!installerRunning) {
                Log.d(TAG, "No active installer processes detected.");
                break;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }
}
