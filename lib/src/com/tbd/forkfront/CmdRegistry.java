package com.tbd.forkfront;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of all available NetHack commands with metadata for UI display and discovery.
 */
public class CmdRegistry {
    
    public enum Category {
        MOVEMENT("Movement"),
        COMBAT("Combat"),
        INVENTORY("Inventory"),
        MAGIC("Magic"),
        INTERACTION("Interaction"),
        INFORMATION("Information"),
        MISC("Miscellaneous"),
        SYSTEM("System");

        private final String displayName;
        Category(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    public static class CmdInfo {
        private final String command;
        private final String displayName;
        private final String description;
        private final int iconResId; // Placeholder for icon resources
        private final Category category;
        private final boolean contextual;

        public CmdInfo(String command, String displayName, String description, Category category, boolean contextual) {
            this.command = command;
            this.displayName = displayName;
            this.description = description;
            this.category = category;
            this.contextual = contextual;
            this.iconResId = 0; // To be populated later
        }

        public String getCommand() { return command; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public Category getCategory() { return category; }
        public boolean isContextual() { return contextual; }
        public int getIconResId() { return iconResId; }

        @Override
        public String toString() { return displayName; }
    }

    public interface OnCommandListener {
        void onCommandExecute(CmdInfo cmd);
    }

    private static final Map<String, CmdInfo> COMMANDS = new HashMap<>();
    private static final List<CmdInfo> ALL_COMMANDS = new ArrayList<>();

    static {
        // --- MOVEMENT ---
        add("y", "Move North-West", "Move one step diagonally NW", Category.MOVEMENT, false);
        add("k", "Move North", "Move one step North", Category.MOVEMENT, false);
        add("u", "Move North-East", "Move one step diagonally NE", Category.MOVEMENT, false);
        add("h", "Move West", "Move one step West", Category.MOVEMENT, false);
        add("l", "Move East", "Move one step East", Category.MOVEMENT, false);
        add("b", "Move South-West", "Move one step diagonally SW", Category.MOVEMENT, false);
        add("j", "Move South", "Move one step South", Category.MOVEMENT, false);
        add("n", "Move South-East", "Move one step diagonally SE", Category.MOVEMENT, false);
        add(".", "Wait", "Wait one turn", Category.MOVEMENT, false);
        add("<", "Up Stairs", "Go up the stairs", Category.MOVEMENT, true);
        add(">", "Down Stairs", "Go down the stairs", Category.MOVEMENT, true);

        // --- COMBAT ---
        add("F", "Force Fight", "Fight a monster even if you don't see it", Category.COMBAT, false);
        add("m", "Move Without Picking Up", "Move to a square without picking up items", Category.COMBAT, false);
        add("t", "Throw", "Throw an item", Category.COMBAT, false);
        add("f", "Fire", "Fire from your quiver", Category.COMBAT, false);
        add("a", "Apply", "Apply a tool (wands, etc.)", Category.COMBAT, false);
        add("z", "Zap Wand", "Zap a wand", Category.COMBAT, false);
        add(String.valueOf((char)4), "Kick", "Kick something", Category.COMBAT, true);

        // --- INVENTORY ---
        add("i", "Inventory", "Show your full inventory", Category.INVENTORY, false);
        add("d", "Drop Item", "Drop an item from your inventory", Category.INVENTORY, false);
        add("w", "Wield Weapon", "Wield a weapon", Category.INVENTORY, false);
        add("W", "Wear Armor", "Wear a piece of armor", Category.INVENTORY, false);
        add("T", "Take Off Armor", "Take off a piece of armor", Category.INVENTORY, false);
        add("P", "Put On Accessory", "Put on a ring or amulet", Category.INVENTORY, false);
        add("R", "Remove Accessory", "Remove a ring or amulet", Category.INVENTORY, false);
        add("q", "Quaff Potion", "Quaff a potion", Category.INVENTORY, false);
        add("e", "Eat Food", "Eat some food", Category.INVENTORY, false);
        add("x", "Swap Weapons", "Exchange primary and secondary weapons", Category.INVENTORY, false);

        // --- INTERACTION ---
        add("o", "Open Door", "Open a closed door", Category.INTERACTION, true);
        add("c", "Close Door", "Close an open door", Category.INTERACTION, true);
        add(",", "Pick Up", "Pick up items on current square", Category.INTERACTION, true);
        add("s", "Search", "Search for secret doors and traps", Category.INTERACTION, false);
        add("E", "Engrave", "Engrave on the floor", Category.INTERACTION, false);
        add("C", "Call/Name", "Name an object or creature", Category.INTERACTION, false);
        add("D", "Dip", "Dip an object into something", Category.INTERACTION, false);
        add("#loot", "Loot", "Loot a container or altar", Category.INTERACTION, true);
        add("#sit", "Sit", "Sit down", Category.INTERACTION, true);
        add("#chat", "Chat", "Talk to a monster", Category.INTERACTION, true);
        add("#force", "Force", "Force a lock", Category.INTERACTION, true);

        // --- MAGIC ---
        add("Z", "Cast Spell", "Cast a spell", Category.MAGIC, false);
        add("r", "Read Scroll", "Read a scroll or book", Category.MAGIC, false);
        add("#enhance", "Enhance Skills", "Improve your weapon skills", Category.MAGIC, false);
        add("#pray", "Pray", "Pray to your god", Category.MAGIC, false);

        // --- INFORMATION ---
        add(";", "Look Around", "Show what is on a map square", Category.INFORMATION, false);
        add(":", "Look at Floor", "Show what is on the floor here", Category.INFORMATION, false);
        add("\\", "Discoveries", "Show known item types", Category.INFORMATION, false);
        add("O", "Options", "View/change game options", Category.INFORMATION, false);
        add("^", "Show Traps", "Show known traps on the level", Category.INFORMATION, false);
        add("?", "Help", "Show help files", Category.INFORMATION, false);
        add("/", "Identify Symbol", "Identify a symbol on the screen", Category.INFORMATION, false);

        // --- SYSTEM ---
        add("S", "Save Game", "Save the game and exit", Category.SYSTEM, false);
        add("#quit", "Quit", "Quit without saving", Category.SYSTEM, false);
        add("#version", "Version", "Show version information", Category.SYSTEM, false);
    }

    private static void add(String command, String name, String desc, Category cat, boolean contextual) {
        CmdInfo info = new CmdInfo(command, name, desc, cat, contextual);
        COMMANDS.put(command, info);
        ALL_COMMANDS.add(info);
    }

    public static CmdInfo get(String command) {
        return COMMANDS.get(command);
    }

    public static List<CmdInfo> getAll() {
        return Collections.unmodifiableList(ALL_COMMANDS);
    }

    public static List<CmdInfo> getByCategory(Category category) {
        List<CmdInfo> result = new ArrayList<>();
        for (CmdInfo info : ALL_COMMANDS) {
            if (info.getCategory() == category) {
                result.add(info);
            }
        }
        return result;
    }

    /**
     * Returns commands sorted by category, then alphabetically within each category.
     */
    public static List<CmdInfo> getAllSorted() {
        List<CmdInfo> sorted = new ArrayList<>(ALL_COMMANDS);
        Collections.sort(sorted, (a, b) -> {
            // First sort by category
            int catCompare = a.getCategory().compareTo(b.getCategory());
            if (catCompare != 0) return catCompare;
            // Then alphabetically by display name within category
            return a.getDisplayName().compareToIgnoreCase(b.getDisplayName());
        });
        return sorted;
    }

    /**
     * Returns true if the command should appear in the command palette widget.
     * Filters out directional movement (covered by d-pad), save/quit
     * (covered by sidebar), and version (non-gameplay).
     */
    public static boolean isPaletteVisible(CmdInfo cmd) {
        String c = cmd.getCommand();
        if ("y".equals(c) || "k".equals(c) || "u".equals(c) || "h".equals(c)
                || "l".equals(c) || "b".equals(c) || "j".equals(c)
                || "n".equals(c) || "S".equals(c) || "#quit".equals(c)
                || "#version".equals(c)) {
            return false;
        }
        return true;
    }

    /**
     * Returns all palette-visible commands sorted by category and name.
     */
    public static List<CmdInfo> getPaletteSorted() {
        List<CmdInfo> result = new ArrayList<>();
        for (CmdInfo cmd : getAllSorted()) {
            if (isPaletteVisible(cmd)) {
                result.add(cmd);
            }
        }
        return result;
    }
}
