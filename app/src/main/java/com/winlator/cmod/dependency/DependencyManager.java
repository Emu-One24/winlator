package com.winlator.cmod.dependency;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DependencyManager {
    private final Context context;
    private final Map<String, Dependency> dependencies = new HashMap<>();

    public DependencyManager(Context context) {
        this.context = context;
        loadDependencies();
    }

    private void loadDependencies() {
        AssetManager assetManager = context.getAssets();
        try {
            String[] files = assetManager.list("dependencies");
            if (files != null) {
                for (String file : files) {
                    if (file.endsWith(".yml") || file.endsWith(".yaml")) {
                        String id = file.substring(0, file.lastIndexOf('.'));
                        try (InputStream in = assetManager.open("dependencies/" + file)) {
                            Dependency dep = Dependency.parse(in);
                            if (dep.getName() == null) {
                                dep.setName(id);
                            }
                            dependencies.put(id, dep);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Map<String, Dependency> getDependencies() {
        return dependencies;
    }

    public Dependency getDependency(String id) {
        return dependencies.get(id);
    }

    public List<Dependency> resolveDependencies(List<String> selectedIds) {
        Set<String> resolved = new LinkedHashSet<>();
        for (String id : selectedIds) {
            resolveTransitive(id, resolved, new LinkedHashSet<>());
        }
        
        List<Dependency> result = new ArrayList<>();
        for (String id : resolved) {
            Dependency dep = dependencies.get(id);
            if (dep != null) {
                result.add(dep);
            }
        }
        return result;
    }

    private void resolveTransitive(String id, Set<String> resolved, Set<String> visiting) {
        if (resolved.contains(id)) return;
        if (visiting.contains(id)) {
            return;
        }

        visiting.add(id);
        Dependency dep = dependencies.get(id);
        if (dep != null) {
            for (String depRequirement : dep.getDependencies()) {
                resolveTransitive(depRequirement, resolved, visiting);
            }
        }
        visiting.remove(id);
        resolved.add(id);
    }

    public List<String> getProtonFixesForExecutable(String exeName) {
        List<String> fixes = new ArrayList<>();
        if (exeName == null) return fixes;
        
        String lowerExe = exeName.toLowerCase();
        if (lowerExe.equals("tesv.exe") || lowerExe.equals("skyrimse.exe")) {
            fixes.add("d3dx9");
            fixes.add("vcrun2015");
            fixes.add("corefonts");
        } else if (lowerExe.equals("witcher3.exe")) {
            fixes.add("vcrun2012");
            fixes.add("d3dx11");
            fixes.add("d3dcompiler_43");
        } else if (lowerExe.equals("gta5.exe")) {
            fixes.add("vcrun2015");
            fixes.add("d3dx11");
            fixes.add("d3dcompiler_43");
        } else if (lowerExe.contains("borderlands")) {
            fixes.add("physx");
            fixes.add("vcrun2010");
            fixes.add("d3dx9");
        } else if (lowerExe.equals("terraria.exe") || lowerExe.equals("stardew valley.exe")) {
            fixes.add("dotnet40");
        } else if (lowerExe.equals("ra2.exe") || lowerExe.equals("cnc95.exe")) {
            fixes.add("cnc-ddraw");
        } else if (lowerExe.equals("fallout.exe") || lowerExe.equals("fallout2.exe")) {
            fixes.add("directmusic");
            fixes.add("directplay");
        }
        return fixes;
    }
}
