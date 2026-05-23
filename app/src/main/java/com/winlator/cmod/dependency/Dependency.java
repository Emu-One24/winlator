package com.winlator.cmod.dependency;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Dependency {
    private String name;
    private String description;
    private String provider;
    private String license;
    private String licenseUrl;
    private final List<String> dependencies = new ArrayList<>();
    private final List<Step> steps = new ArrayList<>();

    public static class BundleEntry {
        private String value;
        private String data;

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }

        public String getData() { return data; }
        public void setData(String data) { this.data = data; }
    }

    public static class Step {
        private String action;
        private String fileName;
        private String url;
        private String rename;
        private String fileChecksum;
        private long fileSize;
        private String arguments;
        private String version;
        private String dll;
        private String type;
        private String dest;
        private String key;
        private String value;
        private String data;
        private String name;
        private String source;
        private String font;
        private final Map<String, String> environment = new HashMap<>();
        private final List<BundleEntry> bundle = new ArrayList<>();
        private final List<String> fonts = new ArrayList<>();
        private final List<String> dlls = new ArrayList<>();
        private final List<String> replace = new ArrayList<>();
        private final List<String> forArchs = new ArrayList<>();

        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getRename() { return rename; }
        public void setRename(String rename) { this.rename = rename; }

        public String getFileChecksum() { return fileChecksum; }
        public void setFileChecksum(String fileChecksum) { this.fileChecksum = fileChecksum; }

        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }

        public String getArguments() { return arguments; }
        public void setArguments(String arguments) { this.arguments = arguments; }

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }

        public String getDll() { return dll; }
        public void setDll(String dll) { this.dll = dll; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getDest() { return dest; }
        public void setDest(String dest) { this.dest = dest; }

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }

        public String getData() { return data; }
        public void setData(String data) { this.data = data; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getFont() { return font; }
        public void setFont(String font) { this.font = font; }

        public Map<String, String> getEnvironment() { return environment; }
        public List<BundleEntry> getBundle() { return bundle; }
        public List<String> getFonts() { return fonts; }
        public List<String> getDlls() { return dlls; }
        public List<String> getReplace() { return replace; }
        public List<String> getForArchs() { return forArchs; }
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getLicense() { return license; }
    public void setLicense(String license) { this.license = license; }

    public String getLicenseUrl() { return licenseUrl; }
    public void setLicenseUrl(String licenseUrl) { this.licenseUrl = licenseUrl; }

    public List<String> getDependencies() { return dependencies; }
    public List<Step> getSteps() { return steps; }

    public static Dependency parse(InputStream in) throws IOException {
        Dependency dep = new Dependency();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line;
            String currentSection = null; // "Dependencies" or "Steps"
            Step currentStep = null;
            boolean inEnvironmentBlock = false;
            boolean inBundleBlock = false;
            boolean inFontsBlock = false;
            boolean inDllsBlock = false;
            boolean inReplaceBlock = false;
            boolean inForBlock = false;
            BundleEntry currentBundleEntry = null;

            while ((line = reader.readLine()) != null) {
                // Remove comments and trim
                int commentIndex = line.indexOf('#');
                if (commentIndex >= 0) {
                    line = line.substring(0, commentIndex);
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;

                // Check indentation
                int indent = 0;
                while (indent < line.length() && line.charAt(indent) == ' ') {
                    indent++;
                }

                // Split key and value
                int colonIndex = trimmed.indexOf(':');
                String key = colonIndex >= 0 ? trimmed.substring(0, colonIndex).trim() : trimmed;
                String val = colonIndex >= 0 ? trimmed.substring(colonIndex + 1).trim() : "";

                // Remove surrounding quotes from value if present
                if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                    val = val.substring(1, val.length() - 1);
                } else if (val.startsWith("'") && val.endsWith("'") && val.length() >= 2) {
                    val = val.substring(1, val.length() - 1);
                }

                // Reset block states if we match a known top-level step key
                if (key.equals("action") || key.equals("file_name") || key.equals("file") || key.equals("url") ||
                    key.equals("rename") || key.equals("file_checksum") || key.equals("file_size") ||
                    key.equals("arguments") || key.equals("version") || key.equals("dll") ||
                    key.equals("type") || key.equals("dest") || key.equals("key") ||
                    key.equals("value") || key.equals("data") || key.equals("source") ||
                    key.equals("bundle") || key.equals("fonts") || key.equals("dlls") || key.equals("environment") ||
                    key.equals("replace") || key.equals("for") || key.equals("font") ||
                    key.equals("name")) {
                    inEnvironmentBlock = false;
                    inBundleBlock = false;
                    inFontsBlock = false;
                    inDllsBlock = false;
                    inReplaceBlock = false;
                    inForBlock = false;
                    currentBundleEntry = null;
                }

                // Handle nested environment key-values
                if (inEnvironmentBlock && currentStep != null) {
                    if (colonIndex >= 0 && !key.startsWith("-")) {
                        currentStep.getEnvironment().put(key, val);
                    }
                    continue;
                }

                // Handle nested bundle list
                if (inBundleBlock && currentStep != null) {
                    if (trimmed.startsWith("-")) {
                        currentBundleEntry = new BundleEntry();
                        currentStep.getBundle().add(currentBundleEntry);
                        
                        String subtrimmed = trimmed.substring(1).trim();
                        int subColon = subtrimmed.indexOf(':');
                        if (subColon >= 0) {
                            String subkey = subtrimmed.substring(0, subColon).trim();
                            String subval = subtrimmed.substring(subColon + 1).trim();
                            if (subval.startsWith("\"") && subval.endsWith("\"") && subval.length() >= 2) {
                                subval = subval.substring(1, subval.length() - 1);
                            } else if (subval.startsWith("'") && subval.endsWith("'") && subval.length() >= 2) {
                                subval = subval.substring(1, subval.length() - 1);
                            }
                            if (subkey.equals("value")) {
                                currentBundleEntry.setValue(subval);
                            } else if (subkey.equals("data")) {
                                currentBundleEntry.setData(subval);
                            }
                        }
                    } else if (colonIndex >= 0 && currentBundleEntry != null) {
                        if (key.equals("value")) {
                            currentBundleEntry.setValue(val);
                        } else if (key.equals("data")) {
                            currentBundleEntry.setData(val);
                        }
                    }
                    continue;
                }

                // Handle nested fonts list
                if (inFontsBlock && currentStep != null) {
                    if (trimmed.startsWith("-") && !trimmed.startsWith("- action:")) {
                        String fontFile = trimmed.substring(1).trim();
                        if (fontFile.startsWith("\"") && fontFile.endsWith("\"") && fontFile.length() >= 2) {
                            fontFile = fontFile.substring(1, fontFile.length() - 1);
                        } else if (fontFile.startsWith("'") && fontFile.endsWith("'") && fontFile.length() >= 2) {
                            fontFile = fontFile.substring(1, fontFile.length() - 1);
                        }
                        currentStep.getFonts().add(fontFile);
                        continue;
                    } else if (!trimmed.startsWith("-")) {
                        continue; // still in block, non-list line
                    }
                    inFontsBlock = false; // fall through to - action: handler
                }

                // Handle nested dlls list
                if (inDllsBlock && currentStep != null) {
                    if (trimmed.startsWith("-") && !trimmed.startsWith("- action:")) {
                        String dllFile = trimmed.substring(1).trim();
                        if (dllFile.startsWith("\"") && dllFile.endsWith("\"") && dllFile.length() >= 2) {
                            dllFile = dllFile.substring(1, dllFile.length() - 1);
                        } else if (dllFile.startsWith("'") && dllFile.endsWith("'") && dllFile.length() >= 2) {
                            dllFile = dllFile.substring(1, dllFile.length() - 1);
                        }
                        currentStep.getDlls().add(dllFile);
                        continue;
                    } else if (!trimmed.startsWith("-")) {
                        continue;
                    }
                    inDllsBlock = false;
                }

                // Handle nested replace list
                if (inReplaceBlock && currentStep != null) {
                    if (trimmed.startsWith("-") && !trimmed.startsWith("- action:")) {
                        String replaceFont = trimmed.substring(1).trim();
                        if (replaceFont.startsWith("\"") && replaceFont.endsWith("\"") && replaceFont.length() >= 2) {
                            replaceFont = replaceFont.substring(1, replaceFont.length() - 1);
                        } else if (replaceFont.startsWith("'") && replaceFont.endsWith("'") && replaceFont.length() >= 2) {
                            replaceFont = replaceFont.substring(1, replaceFont.length() - 1);
                        }
                        if (!replaceFont.isEmpty()) currentStep.getReplace().add(replaceFont);
                        continue;
                    } else if (!trimmed.startsWith("-")) {
                        continue;
                    }
                    inReplaceBlock = false;
                }

                // Handle nested for (arch) list
                if (inForBlock && currentStep != null) {
                    if (trimmed.startsWith("-") && !trimmed.startsWith("- action:")) {
                        String arch = trimmed.substring(1).trim();
                        if (arch.startsWith("\"") && arch.endsWith("\"") && arch.length() >= 2) {
                            arch = arch.substring(1, arch.length() - 1);
                        } else if (arch.startsWith("'") && arch.endsWith("'") && arch.length() >= 2) {
                            arch = arch.substring(1, arch.length() - 1);
                        }
                        if (!arch.isEmpty()) currentStep.getForArchs().add(arch);
                        continue;
                    } else if (!trimmed.startsWith("-")) {
                        continue;
                    }
                    inForBlock = false;
                }

                // Check if this line starts a new list item in Steps
                if (trimmed.startsWith("- action:")) {
                    currentSection = "Steps";
                    currentStep = new Step();
                    dep.steps.add(currentStep);
                    inEnvironmentBlock = false;
                    inBundleBlock = false;
                    inFontsBlock = false;
                    inDllsBlock = false;
                    inReplaceBlock = false;
                    inForBlock = false;
                    currentBundleEntry = null;

                    String actionVal = trimmed.substring(trimmed.indexOf(':') + 1).trim();
                    if (actionVal.startsWith("\"") && actionVal.endsWith("\"") && actionVal.length() >= 2) {
                        actionVal = actionVal.substring(1, actionVal.length() - 1);
                    }
                    currentStep.setAction(actionVal);
                    continue;
                }

                // If starting a new item in steps (alternate syntax)
                if (trimmed.equals("-")) {
                    if ("Steps".equals(currentSection)) {
                        currentStep = new Step();
                        dep.steps.add(currentStep);
                        inEnvironmentBlock = false;
                        inBundleBlock = false;
                        inFontsBlock = false;
                        currentBundleEntry = null;
                    }
                    continue;
                }

                // If starting a list item in Dependencies
                if (trimmed.startsWith("-") && "Dependencies".equals(currentSection)) {
                    String depName = trimmed.substring(1).trim();
                    if (depName.startsWith("\"") && depName.endsWith("\"") && depName.length() >= 2) {
                        depName = depName.substring(1, depName.length() - 1);
                    }
                    dep.dependencies.add(depName);
                    continue;
                }

                // Parse standard top-level fields
                if (indent == 0 && colonIndex >= 0) {
                    switch (key) {
                        case "Name":
                            dep.setName(val);
                            break;
                        case "Description":
                            dep.setDescription(val);
                            break;
                        case "Provider":
                            dep.setProvider(val);
                            break;
                        case "License":
                            dep.setLicense(val);
                            break;
                        case "License_url":
                            dep.setLicenseUrl(val);
                            break;
                        case "Dependencies":
                            currentSection = "Dependencies";
                            break;
                        case "Steps":
                            currentSection = "Steps";
                            break;
                    }
                    continue;
                }

                // Parse nested keys inside Steps list items
                if ("Steps".equals(currentSection) && currentStep != null) {
                    switch (key) {
                        case "action":
                            currentStep.setAction(val);
                            break;
                        case "file_name":
                            currentStep.setFileName(val);
                            break;
                        case "url":
                            currentStep.setUrl(val);
                            break;
                        case "rename":
                            currentStep.setRename(val);
                            break;
                        case "file_checksum":
                            currentStep.setFileChecksum(val);
                            break;
                        case "file_size":
                            try {
                                currentStep.setFileSize(Long.parseLong(val));
                            } catch (NumberFormatException e) {
                                currentStep.setFileSize(0);
                            }
                            break;
                        case "file":
                            // alias for file_name used in register_font and replace_font steps
                            currentStep.setFileName(val);
                            break;
                        case "font":
                            currentStep.setFont(val);
                            break;
                        case "arguments": {
                            // Handle inline YAML list syntax: [] means no args, [x y] means "x y"
                            String argsVal = val;
                            if (argsVal.startsWith("[") && argsVal.endsWith("]")) {
                                argsVal = argsVal.substring(1, argsVal.length() - 1).trim();
                            }
                            currentStep.setArguments(argsVal.isEmpty() ? null : argsVal);
                            break;
                        }
                        case "version":
                            currentStep.setVersion(val);
                            break;
                        case "dll":
                            currentStep.setDll(val);
                            break;
                        case "type":
                            currentStep.setType(val);
                            break;
                        case "dest":
                            currentStep.setDest(val);
                            break;
                        case "key":
                            currentStep.setKey(val);
                            break;
                        case "value":
                            currentStep.setValue(val);
                            break;
                        case "data":
                            currentStep.setData(val);
                            break;
                        case "source":
                            currentStep.setSource(val);
                            break;
                        case "name":
                            currentStep.setName(val);
                            break;
                        case "environment":
                            inEnvironmentBlock = true;
                            break;
                        case "bundle":
                            inBundleBlock = true;
                            break;
                        case "fonts":
                            inFontsBlock = true;
                            break;
                        case "dlls":
                            inDllsBlock = true;
                            break;
                        case "replace":
                            inReplaceBlock = true;
                            break;
                        case "for":
                            inForBlock = true;
                            break;
                    }
                }
            }
        }
        return dep;
    }
}
