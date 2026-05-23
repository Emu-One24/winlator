package com.winlator.cmod.core;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;

import com.winlator.cmod.R;
import com.winlator.cmod.contents.ContentProfile;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.xenvironment.ImageFs;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WineInfo implements Parcelable {
    public static final WineInfo MAIN_WINE_VERSION = new WineInfo("proton","9.0", "x86_64");
    private static final Pattern pattern = Pattern.compile("(?i)(wine|proton).*?([0-9\\.]+).*?(x86|x86_64|arm64ec)");
    public final String version;
    public final String type;
    public String subversion;
    public final String path;
    private String arch;

    public WineInfo(String type, String version, String arch) {
        this.type = type;
        this.version = version;
        this.subversion = null;
        this.arch = arch;
        this.path = null;
    }

    public WineInfo(String type, String version, String subversion, String arch, String path) {
        this.type = type;
        this.version = version;
        this.subversion = subversion != null && !subversion.isEmpty() ? subversion : null;
        this.arch = arch;
        this.path = path;
    }

    public WineInfo(String type, String version, String arch, String path) {
        this.type = type;
        this.version = version;
        this.arch = arch;
        this.path = path;
    }

    private WineInfo(Parcel in) {
        type = in.readString();
        version = in.readString();
        subversion = in.readString();
        arch = in.readString();
        path = in.readString();
    }

    public String getArch() {
        return arch;
    }

    public void setArch(String arch) {
        this.arch = arch;
    }

    public boolean isWin64() {
        return arch.equals("x86_64") || arch.equals("arm64ec");
    }

    public boolean isArm64EC() { return arch.equals("arm64ec"); }

    public String identifier() {
        if (type.equals("proton"))
            return "proton-" + fullVersion() + "-"+ arch;
        else
            return "wine-" + fullVersion() + "-" + arch;
    }

    public String fullVersion() {
        return version+(subversion != null ? "-"+subversion : "");
    }

    @NonNull
    @Override
    public String toString() {
        if (type.equals("proton"))
            return "Proton "+fullVersion()+(this == MAIN_WINE_VERSION ? " (Custom)" : "");
        else
            return "Wine "+fullVersion()+(this == MAIN_WINE_VERSION ? " (Custom)" : "");
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<WineInfo> CREATOR = new Parcelable.Creator<WineInfo>() {
        public WineInfo createFromParcel(Parcel in) {
            return new WineInfo(in);
        }

        public WineInfo[] newArray(int size) {
            return new WineInfo[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(type);
        dest.writeString(version);
        dest.writeString(subversion);
        dest.writeString(arch);
        dest.writeString(path);
    }

    @NonNull
    public static WineInfo fromIdentifier(Context context, ContentsManager contentsManager, String identifier) {
        if (identifier == null) identifier = MAIN_WINE_VERSION.identifier();

        Log.d("WineInfo", "Creating WineInfo from identifier " + identifier);

        ImageFs imageFs = context != null ? ImageFs.find(context) : null;
        String path = "";

        if (identifier.equals(MAIN_WINE_VERSION.identifier())) {
            String mainPath = (imageFs != null && imageFs.getRootDir() != null) ? imageFs.getRootDir().getPath() + "/opt/" + MAIN_WINE_VERSION.identifier() : "";
            return new WineInfo(MAIN_WINE_VERSION.type, MAIN_WINE_VERSION.version, MAIN_WINE_VERSION.arch, mainPath);
        }

        ContentProfile wineProfile = (contentsManager != null && context != null) ? contentsManager.getProfileByEntryName(identifier) : null;

        if (wineProfile != null && (wineProfile.type == ContentProfile.ContentType.CONTENT_TYPE_WINE || wineProfile.type == ContentProfile.ContentType.CONTENT_TYPE_PROTON)) {
            if (identifier.length() >= 2) {
                identifier = identifier.substring(0, identifier.length() - 2).toLowerCase();
            }
        }

        Matcher matcher = pattern.matcher(identifier);

        if (matcher.find()) {
            if (context != null) {
                try {
                    String[] wineVersions = context.getResources().getStringArray(R.array.wine_entries);
                    for (String wineVersion : wineVersions) {
                        if (wineVersion.contains(identifier)) {
                            path = (imageFs != null && imageFs.getRootDir() != null) ? imageFs.getRootDir().getPath() + "/opt/" + identifier : "";
                            break;
                        }
                    }
                } catch (Exception e) {
                    Log.e("WineInfo", "Error reading wine_entries resource", e);
                }
            }

            if (wineProfile != null && context != null && (wineProfile.type == ContentProfile.ContentType.CONTENT_TYPE_WINE || wineProfile.type == ContentProfile.ContentType.CONTENT_TYPE_PROTON)) {
                File installDir = ContentsManager.getInstallDir(context, wineProfile);
                if (installDir != null) path = installDir.getPath();
            }

            String grp1 = matcher.group(1);
            String grp2 = matcher.group(2);
            String grp3 = matcher.group(3);

            return new WineInfo(
                grp1 != null ? grp1.toLowerCase() : MAIN_WINE_VERSION.type,
                grp2 != null ? grp2 : MAIN_WINE_VERSION.version,
                grp3 != null ? grp3.toLowerCase() : MAIN_WINE_VERSION.arch,
                path
            );
        }
        else {
            String mainPath = (imageFs != null && imageFs.getRootDir() != null) ? imageFs.getRootDir().getPath() + "/opt/" + MAIN_WINE_VERSION.identifier() : "";
            return new WineInfo(MAIN_WINE_VERSION.type, MAIN_WINE_VERSION.version, MAIN_WINE_VERSION.arch, mainPath);
        }
    }

    public static boolean isMainWineVersion(String wineVersion) {
        return wineVersion == null ||wineVersion.equals(MAIN_WINE_VERSION.identifier());
    }
}
