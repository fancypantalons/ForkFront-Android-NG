package com.tbd.forkfront;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

        public CmdInfo(String command, String displayName, String description, Category category) {
            this.command = command;
            this.displayName = displayName;
            this.description = description;
            this.category = category;
            this.iconResId = 0; // To be populated later
        }

        public String getCommand() { return command; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public Category getCategory() { return category; }
        public int getIconResId() { return iconResId; }

        /**
         * Returns the command string with control characters converted to caret notation.
         * Printable commands are returned as-is.
         */
        public String getDisplayCommand() {
            if (command.length() == 1) {
                char c = command.charAt(0);
                if (c < 32) {
                    return "^" + (char) (c + 64);
                }
                if (c == 127) {
                    return "^?";
                }
            }
            return command;
        }

        public String getDisplayLabel() {
            return displayName + " (" + getDisplayCommand() + ")";
        }

        @Override
        public String toString() { return displayName; }
    }

    public interface OnCommandListener {
        void onCommandExecute(CmdInfo cmd);
    }

    private static final Map<String, CmdInfo> COMMANDS = new HashMap<>();
    private static final List<CmdInfo> ALL_COMMANDS = new ArrayList<>();

    private static final Set<String> HIDDEN_FROM_PALETTE =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    "y", "k", "u", "h", "l", "b", "j", "n",
                    "S", "#quit", "#version")));

    private static final List<CmdInfo> ALL_SORTED;
    private static final List<CmdInfo> PALETTE_SORTED;
    private static final Map<Category, List<CmdInfo>> BY_CATEGORY;

    static {
        // --- MOVEMENT ---
        add("y", "Move North-West", "Move one step diagonally NW", Category.MOVEMENT);
        add("k", "Move North", "Move one step North", Category.MOVEMENT);
        add("u", "Move North-East", "Move one step diagonally NE", Category.MOVEMENT);
        add("h", "Move West", "Move one step West", Category.MOVEMENT);
        add("l", "Move East", "Move one step East", Category.MOVEMENT);
        add("b", "Move South-West", "Move one step diagonally SW", Category.MOVEMENT);
        add("j", "Move South", "Move one step South", Category.MOVEMENT);
        add("n", "Move South-East", "Move one step diagonally SE", Category.MOVEMENT);
        add(".", "Wait", "Wait one turn", Category.MOVEMENT);
        add("<", "Up Stairs", "Go up the stairs", Category.MOVEMENT);
        add(">", "Down Stairs", "Go down the stairs", Category.MOVEMENT);
        add("_", "Travel", "Travel to a location", Category.MOVEMENT);
        add("M", "Move Far", "Move far in a direction", Category.MOVEMENT);

        // --- COMBAT ---
        add("F", "Force Fight", "Fight a monster even if you don't see it", Category.COMBAT);
        add("m", "Move Without Picking Up", "Move to a square without picking up items", Category.COMBAT);
        add("t", "Throw", "Throw an item", Category.COMBAT);
        add("f", "Fire", "Fire from your quiver", Category.COMBAT);
        add("a", "Apply", "Apply a tool (wands, etc.)", Category.COMBAT);
        add("z", "Zap Wand", "Zap a wand", Category.COMBAT);
        add(String.valueOf((char)4), "Kick", "Kick something", Category.COMBAT);

        // --- INVENTORY ---
        add("i", "Inventory", "Show your full inventory", Category.INVENTORY);
        add("d", "Drop Item", "Drop an item from your inventory", Category.INVENTORY);
        add("D", "Drop Many", "Drop several objects", Category.INVENTORY);
        add("w", "Wield Weapon", "Wield a weapon", Category.INVENTORY);
        add("W", "Wear Armor", "Wear a piece of armor", Category.INVENTORY);
        add("T", "Take Off Armor", "Take off a piece of armor", Category.INVENTORY);
        add("A", "Take Off Many", "Take off several pieces of armor", Category.INVENTORY);
        add("P", "Put On Accessory", "Put on a ring or amulet", Category.INVENTORY);
        add("R", "Remove Accessory", "Remove a ring or amulet", Category.INVENTORY);
        add("Q", "Quiver", "Ready ammunition for firing", Category.INVENTORY);
        add("q", "Quaff Potion", "Quaff a potion", Category.INVENTORY);
        add("e", "Eat Food", "Eat some food", Category.INVENTORY);
        add("x", "Swap Weapons", "Exchange primary and secondary weapons", Category.INVENTORY);
        add("(", "Show Tools", "Show tools in inventory", Category.INVENTORY);
        add(")", "Show Gold", "Show amount of gold carried", Category.INVENTORY);
        add("[", "Show Armor", "Show armor being worn", Category.INVENTORY);

        // --- INTERACTION ---
        add("o", "Open Door", "Open a closed door", Category.INTERACTION);
        add("c", "Close Door", "Close an open door", Category.INTERACTION);
        add(",", "Pick Up", "Pick up items on current square", Category.INTERACTION);
        add("s", "Search", "Search for secret doors and traps", Category.INTERACTION);
        add("E", "Engrave", "Engrave on the floor", Category.INTERACTION);
        add("C", "Call/Name", "Name an object or creature", Category.INTERACTION);
        add("#dip", "Dip", "Dip an object into something", Category.INTERACTION);
        add("#loot", "Loot", "Loot a container or altar", Category.INTERACTION);
        add("#sit", "Sit", "Sit down", Category.INTERACTION);
        add("#chat", "Chat", "Talk to a monster", Category.INTERACTION);
        add("#force", "Force", "Force a lock", Category.INTERACTION);
        add("#ride", "Ride", "Ride or dismount a steed", Category.INTERACTION);
        add("#tip", "Tip", "Tip over a container", Category.INTERACTION);
        add("#untrap", "Untrap", "Untrap something", Category.INTERACTION);

        // --- MAGIC ---
        add("Z", "Cast Spell", "Cast a spell", Category.MAGIC);
        add("r", "Read Scroll", "Read a scroll or book", Category.MAGIC);
        add("#enhance", "Enhance Skills", "Improve your weapon skills", Category.MAGIC);
        add("#pray", "Pray", "Pray to your god", Category.MAGIC);
        add("#invoke", "Invoke", "Invoke an artifact's special power", Category.MAGIC);
        add("#teleport", "Teleport", "Teleport to another location", Category.MAGIC);

        // --- INFORMATION ---
        add(";", "Look Around", "Show what is on a map square", Category.INFORMATION);
        add(":", "Look at Floor", "Show what is on the floor here", Category.INFORMATION);
        add("\\", "Discoveries", "Show known item types", Category.INFORMATION);
        add("O", "Options", "View/change game options", Category.INFORMATION);
        add("^", "Show Traps", "Show known traps on the level", Category.INFORMATION);
        add("?", "Help", "Show help files", Category.INFORMATION);
        add("/", "Identify Symbol", "Identify a symbol on the screen", Category.INFORMATION);
        add("V", "History", "Show long version and history", Category.INFORMATION);
        add("X", "Attributes", "Show your attributes", Category.INFORMATION);
        add("#annotate", "Annotate", "Add a note to the current level", Category.INFORMATION);
        add("#conduct", "Conduct", "Show voluntary challenges", Category.INFORMATION);
        add("#overview", "Overview", "Show dungeon overview", Category.INFORMATION);

        // --- MISC ---
        add("#jump", "Jump", "Jump to a location", Category.MOVEMENT);
        add("#turn", "Turn", "Turn undead", Category.MISC);
        add("#wipe", "Wipe", "Wipe your face", Category.MISC);

        // --- SYSTEM ---
        add("S", "Save Game", "Save the game and exit", Category.SYSTEM);
        add("#quit", "Quit", "Quit without saving", Category.SYSTEM);
        add("#version", "Version", "Show version information", Category.SYSTEM);

        // Precompute immutable views
        List<CmdInfo> sorted = new ArrayList<>(ALL_COMMANDS);
        Collections.sort(sorted, (a, b) -> {
            int catCompare = a.getCategory().compareTo(b.getCategory());
            if (catCompare != 0) return catCompare;
            return a.getDisplayName().compareToIgnoreCase(b.getDisplayName());
        });
        ALL_SORTED = Collections.unmodifiableList(sorted);

        List<CmdInfo> palette = new ArrayList<>();
        for (CmdInfo cmd : ALL_SORTED) {
            if (isPaletteVisible(cmd)) {
                palette.add(cmd);
            }
        }
        PALETTE_SORTED = Collections.unmodifiableList(palette);

        Map<Category, List<CmdInfo>> byCat = new HashMap<>();
        for (CmdInfo cmd : ALL_COMMANDS) {
            byCat.computeIfAbsent(cmd.getCategory(), k -> new ArrayList<>()).add(cmd);
        }
        Map<Category, List<CmdInfo>> byCatUnmod = new HashMap<>();
        for (Category cat : Category.values()) {
            List<CmdInfo> list = byCat.getOrDefault(cat, Collections.emptyList());
            byCatUnmod.put(cat, Collections.unmodifiableList(new ArrayList<>(list)));
        }
        BY_CATEGORY = Collections.unmodifiableMap(byCatUnmod);
    }

    private static void add(String command, String name, String desc, Category cat) {
        CmdInfo info = new CmdInfo(command, name, desc, cat);
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
        return BY_CATEGORY.getOrDefault(category, Collections.emptyList());
    }

    /**
     * Returns commands sorted by category, then alphabetically within each category.
     */
    public static List<CmdInfo> getAllSorted() {
        return ALL_SORTED;
    }

    /**
     * Returns true if the command should appear in the command palette widget.
     * Filters out directional movement (covered by d-pad), save/quit
     * (covered by sidebar), and version (non-gameplay).
     */
    public static boolean isPaletteVisible(CmdInfo cmd) {
        return !HIDDEN_FROM_PALETTE.contains(cmd.getCommand());
    }

    /**
     * Returns all palette-visible commands sorted by category and name.
     */
    public static List<CmdInfo> getPaletteSorted() {
        return PALETTE_SORTED;
    }
}
