package com.tbd.forkfront.engine;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import android.os.Process;

public class SaveMetadata {
    private final String role;
    private final String race;
    private final String gender;
    private final String alignment;
    private final boolean wizard;
    private final boolean hasMetadata;

    public SaveMetadata(String role, String race, String gender,
            String alignment, boolean wizard, boolean hasMetadata) {
        this.role = role;
        this.race = race;
        this.gender = gender;
        this.alignment = alignment;
        this.wizard = wizard;
        this.hasMetadata = hasMetadata;
    }

    public static SaveMetadata load(File saveDir, String plname) {
        File metaFile = null;
        if (saveDir != null && saveDir.isDirectory()) {
            String regularized = plname.replaceAll("[^a-zA-Z0-9]", "_");
            String[] suffixes = { plname + ".meta", regularized + ".meta" };
            File[] entries = saveDir.listFiles();
            if (entries != null) {
                outer:
                for (File f : entries) {
                    String name = f.getName();
                    for (String suffix : suffixes) {
                        if (name.endsWith(suffix)
                                && name.length() > suffix.length()
                                && Character.isDigit(name.charAt(0))) {
                            metaFile = f;
                            break outer;
                        }
                    }
                }
            }
        }

        if (metaFile == null) {
            android.util.Log.d("NH_SaveMetadata",
                "No meta file found for " + plname + " in " + saveDir);
            return new SaveMetadata("", "", "", "", false, false);
        }

        Map<String, String> values = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(
                new FileReader(metaFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int idx = line.indexOf('=');
                if (idx > 0) {
                    values.put(line.substring(0, idx), line.substring(idx + 1));
                }
            }
        } catch (IOException e) {
            return new SaveMetadata("", "", "", "", false, false);
        }

        return new SaveMetadata(
            values.get("role"),
            values.get("race"),
            values.get("gender"),
            values.get("alignment"),
            "1".equals(values.get("wizard")),
            true
        );
    }

    public String getRole() { return role != null ? role : ""; }
    public String getRace() { return race != null ? race : ""; }
    public String getGender() { return gender != null ? gender : ""; }
    public String getAlignment() { return alignment != null ? alignment : ""; }
    public boolean isWizard() { return wizard; }
    public boolean hasMetadata() { return hasMetadata; }
}
