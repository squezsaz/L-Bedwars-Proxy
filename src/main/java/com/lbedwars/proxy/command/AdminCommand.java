package com.lbedwars.proxy.command;

import com.lbedwars.proxy.LBedWarsProxy;
import com.lbedwars.proxy.database.ProxyDatabase;
import com.lbedwars.proxy.hologram.HologramManager;
import com.lbedwars.proxy.language.LanguageManager;
import com.lbedwars.proxy.manager.ArenaSelectionManager;
import com.lbedwars.proxy.model.ArenaStatus;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public class AdminCommand implements CommandExecutor, TabCompleter, Listener {
    private static final int PAGE_SIZE = 36;
    private final LBedWarsProxy plugin;
    private final LanguageManager lang;
    private final ArenaSelectionManager arenaSelectionManager;
    private final Map<UUID, GuiState> guiStates;

    public AdminCommand(LBedWarsProxy plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        this.arenaSelectionManager = plugin.getArenaSelectionManager();
        this.guiStates = new HashMap<>();
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(this.lang.get("command.player-only"));
            return true;
        }
        if (!player.hasPermission("lbedwarsproxy.admin")) {
            player.sendMessage(this.lang.get("admin.no-permission"));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(this.lang.get("admin.usage"));
            return true;
        }
        if (args[0].equalsIgnoreCase("gui")) {
            String filter = "all";
            if (args.length >= 2) {
                filter = args[1].toLowerCase();
                if (!isValidFilter(filter)) {
                    player.sendMessage(this.lang.get("admin.invalid-filter"));
                    return true;
                }
            }
            openGui(player, filter, 0);
            return true;
        }
        if (args[0].equalsIgnoreCase("tp")) {
            if (args.length < 2) {
                player.sendMessage(this.lang.get("admin.tp-usage"));
                return true;
            }
            String targetName = args[1];
            ArenaStatus target = this.arenaSelectionManager.findPlayerServer(targetName);
            if (target == null) {
                player.sendMessage(this.lang.get("admin.tp-not-found", "player", targetName));
                return true;
            }
            sendSpectate(player, target.getName(), targetName);
            return true;
        }
        if (args[0].equalsIgnoreCase("leaderboard")) {
            String stat = "kills";
            if (args.length >= 2) {
                stat = args[1].toLowerCase();
                if (!isValidStat(stat)) {
                    player.sendMessage(this.lang.get("admin.invalid-stat"));
                    return true;
                }
            }
            openLeaderboard(player, stat, 0);
            return true;
        }
        if (args[0].equalsIgnoreCase("hologram")) {
            if (args.length < 2) {
                player.sendMessage(this.lang.get("admin.hologram-usage"));
                return true;
            }
            if (args[1].equalsIgnoreCase("set")) {
                String stat = "kills";
                if (args.length >= 3) {
                    stat = args[2].toLowerCase();
                    if (!com.lbedwars.proxy.hologram.HologramManager.isValidStat(stat)) {
                        player.sendMessage(this.lang.get("admin.invalid-stat"));
                        return true;
                    }
                }
                this.plugin.getHologramManager().setHologram(player, stat);
                return true;
            }
            if (args[1].equalsIgnoreCase("remove")) {
                String id = args.length >= 3 ? args[2] : null;
                this.plugin.getHologramManager().removeHologramCmd(player, id);
                return true;
            }
            if (args[1].equalsIgnoreCase("list")) {
                this.plugin.getHologramManager().listHolograms(player);
                return true;
            }
            player.sendMessage(this.lang.get("admin.hologram-usage"));
            return true;
        }
        player.sendMessage(this.lang.get("admin.usage"));
        return true;
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("gui", "tp", "leaderboard", "hologram");
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("gui")) return List.of("all", "solo", "duo", "trio", "quad");
            if (args[0].equalsIgnoreCase("leaderboard")) return List.of("kills", "wins", "final_kills", "beds_broken");
            if (args[0].equalsIgnoreCase("hologram")) return List.of("set", "remove", "list");
            if (args[0].equalsIgnoreCase("tp")) {
                List<String> players = new ArrayList<>();
                for (ArenaStatus s : this.arenaSelectionManager.getAllServers()) {
                    if (!s.isOnline()) continue;
                    for (String p : s.getPlayerList()) {
                        if (p.toLowerCase().startsWith(args[1].toLowerCase())) {
                            players.add(p);
                        }
                    }
                }
                return players;
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("hologram") && args[1].equalsIgnoreCase("set")) {
            return List.of("kills", "wins", "final_kills", "deaths", "final_deaths", "beds_broken", "losses", "games_played");
        }
        return List.of();
    }

    private boolean isValidFilter(String filter) {
        return filter.equals("all") || filter.equals("solo") || filter.equals("duo") || filter.equals("trio") || filter.equals("quad");
    }

    private boolean isValidStat(String stat) {
        return stat.equals("kills") || stat.equals("wins") || stat.equals("final_kills") || stat.equals("beds_broken") || stat.equals("final_deaths") || stat.equals("deaths");
    }

    private void openGui(Player player, String filter, int page) {
        Collection<ArenaStatus> all = arenaSelectionManager.getAllServers();
        List<ArenaStatus> filtered = all.stream()
                .filter(a -> filter.equals("all") || a.getMode().equalsIgnoreCase(filter))
                .filter(ArenaStatus::isOnline)
                .collect(Collectors.toList());

        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) PAGE_SIZE));
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        int count = filtered.size() - page * PAGE_SIZE;
        int rows = Math.min(6, Math.max(1, (count + 8) / 9 + (totalPages > 1 ? 1 : 0)));
        int invSize = rows * 9;

        String title = ChatColor.DARK_PURPLE + "Arenas (" + filter + ")";
        Inventory inv = Bukkit.createInventory(null, invSize, title);
        guiStates.put(player.getUniqueId(), new GuiState("gui", filter, page));

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, filtered.size());
        int slot = 0;

        for (int i = start; i < end; i++) {
            ArenaStatus arena = filtered.get(i);
            inv.setItem(slot++, buildArenaItem(arena));
        }

        if (totalPages > 1) {
            if (page > 0) {
                ItemStack prev = new ItemStack(Material.ARROW);
                ItemMeta meta = prev.getItemMeta();
                meta.setDisplayName(ChatColor.GREEN + "Previous Page");
                prev.setItemMeta(meta);
                inv.setItem(invSize - 9, prev);
            }
            if (page < totalPages - 1) {
                ItemStack next = new ItemStack(Material.ARROW);
                ItemMeta meta = next.getItemMeta();
                meta.setDisplayName(ChatColor.GREEN + "Next Page");
                next.setItemMeta(meta);
                inv.setItem(invSize - 1, next);
            }
        }

        player.openInventory(inv);
    }

    private void openLeaderboard(Player player, String stat, int page) {
        ProxyDatabase db = this.plugin.getDatabase();
        if (!db.isEnabled()) {
            player.sendMessage(this.lang.get("stats.db-disabled"));
            return;
        }

        int limit = 18;
        int start = page * limit;
        List<ProxyDatabase.LeaderboardEntry> entries = db.getLeaderboard(stat, start + limit);

        List<ProxyDatabase.LeaderboardEntry> pageEntries;
        if (start < entries.size()) {
            pageEntries = entries.subList(start, Math.min(start + limit, entries.size()));
        } else {
            pageEntries = List.of();
        }

        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) limit));
        int rows = Math.min(6, Math.max(1, (int) Math.ceil(pageEntries.size() / 9.0) + (totalPages > 1 ? 1 : 0)));
        int invSize = Math.max(9, rows * 9);

        String statDisplay = stat.replace("_", " ");
        String title = ChatColor.GOLD + "Leaderboard: " + statDisplay;
        Inventory inv = Bukkit.createInventory(null, invSize, title);
        guiStates.put(player.getUniqueId(), new GuiState("leaderboard", stat, page));

        int slot = 0;
        for (int i = 0; i < pageEntries.size(); i++) {
            ProxyDatabase.LeaderboardEntry entry = pageEntries.get(i);
            int rank = start + i + 1;
            inv.setItem(slot++, buildLeaderboardItem(rank, entry, stat));
        }

        if (totalPages > 1) {
            if (page > 0) {
                ItemStack prev = new ItemStack(Material.ARROW);
                ItemMeta meta = prev.getItemMeta();
                meta.setDisplayName(ChatColor.GREEN + "Previous Page");
                prev.setItemMeta(meta);
                inv.setItem(invSize - 9, prev);
            }
            if (page < totalPages - 1) {
                ItemStack next = new ItemStack(Material.ARROW);
                ItemMeta meta = next.getItemMeta();
                meta.setDisplayName(ChatColor.GREEN + "Next Page");
                next.setItemMeta(meta);
                inv.setItem(invSize - 1, next);
            }
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) return;

        ItemStack item = event.getCurrentItem();
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasDisplayName()) return;

        String displayName = meta.getDisplayName();
        GuiState state = guiStates.get(player.getUniqueId());
        if (state == null) return;

        if (title.startsWith(ChatColor.DARK_PURPLE + "Arenas")) {
            event.setCancelled(true);
            if (displayName.equals(ChatColor.GREEN + "Previous Page")) {
                openGui(player, state.filter, state.page - 1);
            } else if (displayName.equals(ChatColor.GREEN + "Next Page")) {
                openGui(player, state.filter, state.page + 1);
            } else if (item.getType() == Material.RED_WOOL || item.getType() == Material.GREEN_WOOL || item.getType() == Material.GRAY_WOOL) {
                Collection<ArenaStatus> all = arenaSelectionManager.getAllServers();
                for (ArenaStatus arena : all) {
                    String boldName = ChatColor.stripColor(displayName.replace(ChatColor.BOLD.toString(), ""));
                    if (arena.getName().equals(boldName)) {
                        sendToServer(player, arena.getName());
                        player.closeInventory();
                        return;
                    }
                }
            }
        } else if (title.startsWith(ChatColor.GOLD + "Leaderboard")) {
            event.setCancelled(true);
            if (displayName.equals(ChatColor.GREEN + "Previous Page")) {
                openLeaderboard(player, state.filter, state.page - 1);
            } else if (displayName.equals(ChatColor.GREEN + "Next Page")) {
                openLeaderboard(player, state.filter, state.page + 1);
            }
        }
    }

    private void sendToServer(Player player, String serverName) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            player.sendPluginMessage(this.plugin, "BungeeCord", b.toByteArray());
        } catch (Exception e) {
            player.sendMessage(this.lang.get("admin.connect-fail", "server", serverName));
        }
    }

    private void sendSpectate(Player player, String targetServer, String targetPlayer) {
        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Forward");
            out.writeUTF(targetServer);
            out.writeUTF("lbedwars:spectate");
            String msg = player.getName() + "\0" + targetPlayer;
            byte[] dataBytes = msg.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            out.writeShort(dataBytes.length);
            out.write(dataBytes);
            player.sendPluginMessage(this.plugin, "BungeeCord", out.toByteArray());
        } catch (Exception e) {
            player.sendMessage(this.lang.get("admin.connect-fail", "server", targetServer));
            return;
        }
        sendToServer(player, targetServer);
    }

    private ItemStack buildArenaItem(ArenaStatus arena) {
        Material mat;
        ChatColor stateColor;
        boolean isPlaying = arena.getState().equals("PLAYING");
        boolean isWaiting = arena.getState().equals("WAITING") || arena.getState().equals("STARTING");

        if (isPlaying) {
            mat = Material.RED_WOOL;
            stateColor = ChatColor.RED;
        } else if (isWaiting) {
            mat = Material.GREEN_WOOL;
            stateColor = ChatColor.GREEN;
        } else {
            mat = Material.GRAY_WOOL;
            stateColor = ChatColor.GRAY;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(stateColor.toString() + ChatColor.BOLD + arena.getName());

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Map: " + ChatColor.WHITE + arena.getMapName());
        lore.add(ChatColor.GRAY + "Mode: " + ChatColor.WHITE + arena.getMode());
        lore.add(ChatColor.GRAY + "State: " + stateColor + arena.getState());
        lore.add(ChatColor.GRAY + "Players: " + ChatColor.WHITE + arena.getOnlinePlayers() + "/" + arena.getMaxPlayers());

        if (arena.getSpectators() > 0) {
            lore.add(ChatColor.GRAY + "Spectators: " + ChatColor.WHITE + arena.getSpectators());
        }

        if (isPlaying && arena.getGameTime() > 0) {
            int totalSec = arena.getGameTime();
            int min = totalSec / 60;
            int sec = totalSec % 60;
            lore.add(ChatColor.GRAY + "Time: " + ChatColor.WHITE + String.format("%02d:%02d", min, sec));
        }

        lore.add("");
        lore.add(ChatColor.YELLOW + "Click to join");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildLeaderboardItem(int rank, ProxyDatabase.LeaderboardEntry entry, String stat) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();

        String playerName = entry.getUuid();
        try {
           org.bukkit.OfflinePlayer off = Bukkit.getOfflinePlayer(java.util.UUID.fromString(entry.getUuid()));
           if (off.getName() != null) {
              playerName = off.getName();
              meta.setOwningPlayer(off);
           }
        } catch (IllegalArgumentException e) {
        }

        ChatColor rankColor = rank == 1 ? ChatColor.GOLD : (rank == 2 ? ChatColor.GRAY : (rank == 3 ? ChatColor.DARK_RED : ChatColor.WHITE));
        meta.setDisplayName(rankColor + "#" + rank + " " + ChatColor.WHITE + playerName);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Stat: " + ChatColor.WHITE + stat.replace("_", " ") + ": " + rankColor + entry.getValue());
        meta.setLore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private static class GuiState {
        final String type;
        final String filter;
        final int page;
        GuiState(String type, String filter, int page) {
            this.type = type;
            this.filter = filter;
            this.page = page;
        }
    }
}