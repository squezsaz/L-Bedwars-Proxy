package com.lbedwars.proxy.hologram;

import com.lbedwars.proxy.LBedWarsProxy;
import com.lbedwars.proxy.database.ProxyDatabase;
import com.lbedwars.proxy.language.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HologramManager {
   private final LBedWarsProxy plugin;
   private final List<HologramData> holograms;
   private final File dataFile;
   private int updateTaskId;

   public HologramManager(LBedWarsProxy plugin) {
      this.plugin = plugin;
      this.holograms = new ArrayList<>();
      this.dataFile = new File(plugin.getDataFolder(), "holograms.yml");
      this.updateTaskId = -1;
   }

   public void start() {
      this.loadAll();
      if (this.holograms.isEmpty()) return;
      int interval = this.plugin.getConfig().getInt("holograms.leaderboard.update-interval", 60);
      this.updateTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this.plugin, this::updateAll, 0L, (long) interval * 20L);
   }

   public void stop() {
      if (this.updateTaskId != -1) {
         Bukkit.getScheduler().cancelTask(this.updateTaskId);
         this.updateTaskId = -1;
      }
      this.removeAll();
   }

   public void setHologram(Player player, String stat) {
      if (!isValidStat(stat)) stat = "kills";
      HologramData hd = new HologramData(UUID.randomUUID().toString(), player.getLocation(), stat);
      hd.spawn(this.plugin.getConfig().getInt("holograms.leaderboard.lines", 10));
      this.holograms.add(hd);
      this.saveAll();
      player.sendMessage(this.plugin.getLanguageManager().get("hologram.set", "stat", stat, "id", hd.id));
   }

   public void removeHologramCmd(Player player, String id) {
      HologramData target = null;
      if (id != null) {
         for (HologramData hd : this.holograms) {
            if (hd.id.equalsIgnoreCase(id)) { target = hd; break; }
         }
         if (target == null) {
            player.sendMessage(this.plugin.getLanguageManager().get("hologram.not-found", "id", id));
            return;
         }
      } else {
         double closest = Double.MAX_VALUE;
         Location ploc = player.getLocation();
         for (HologramData hd : this.holograms) {
            double dist = hd.location.distanceSquared(ploc);
            if (dist < closest) { closest = dist; target = hd; }
         }
         if (target == null) {
            player.sendMessage(this.plugin.getLanguageManager().get("hologram.none"));
            return;
         }
      }
      target.remove();
      this.holograms.remove(target);
      this.saveAll();
      player.sendMessage(this.plugin.getLanguageManager().get("hologram.removed", "id", target.id));
   }

   public void listHolograms(Player player) {
      if (this.holograms.isEmpty()) {
         player.sendMessage(this.plugin.getLanguageManager().get("hologram.none"));
         return;
      }
      for (HologramData hd : this.holograms) {
         player.sendMessage(this.plugin.getLanguageManager().get("hologram.list-entry",
            "id", hd.id, "stat", hd.stat, "world", hd.location.getWorld().getName(),
            "x", String.format("%.1f", hd.location.getX()), "y", String.format("%.1f", hd.location.getY()), "z", String.format("%.1f", hd.location.getZ())));
      }
   }

   private void loadAll() {
      if (!this.dataFile.exists()) return;
      YamlConfiguration cfg = YamlConfiguration.loadConfiguration(this.dataFile);
      List<java.util.Map<?, ?>> mapList = cfg.getMapList("holograms");
      for (java.util.Map<?, ?> map : mapList) {
         String id = (String) map.get("id");
         if (id == null) id = java.util.UUID.randomUUID().toString();
         String worldName = (String) map.get("world");
         if (worldName == null) continue;
         World world = Bukkit.getWorld(worldName);
         if (world == null) continue;
         double x = ((Number) map.get("x")).doubleValue();
         double y = ((Number) map.get("y")).doubleValue();
         double z = ((Number) map.get("z")).doubleValue();
         float yaw = map.containsKey("yaw") ? ((Number) map.get("yaw")).floatValue() : 0.0f;
         float pitch = map.containsKey("pitch") ? ((Number) map.get("pitch")).floatValue() : 0.0f;
         String stat = (String) map.get("stat");
         if (stat == null) stat = "kills";
         Location loc = new Location(world, x, y, z, yaw, pitch);
         HologramData hd = new HologramData(id, loc, stat);
         hd.spawn(this.plugin.getConfig().getInt("holograms.leaderboard.lines", 10));
         this.holograms.add(hd);
      }
   }

   private void saveAll() {
      YamlConfiguration cfg = new YamlConfiguration();
      List<java.util.Map<String, Object>> list = new ArrayList<>();
      for (HologramData hd : this.holograms) {
         java.util.Map<String, Object> entry = new java.util.LinkedHashMap<>();
         entry.put("id", hd.id);
         entry.put("world", hd.location.getWorld().getName());
         entry.put("x", hd.location.getX());
         entry.put("y", hd.location.getY());
         entry.put("z", hd.location.getZ());
         entry.put("yaw", (double) hd.location.getYaw());
         entry.put("pitch", (double) hd.location.getPitch());
         entry.put("stat", hd.stat);
         list.add(entry);
      }
      cfg.set("holograms", list);
      try {
         cfg.save(this.dataFile);
      } catch (Exception e) {
         this.plugin.getLogger().warning("Failed to save holograms.yml: " + e.getMessage());
      }
   }

   public void updateAll() {
      if (this.holograms.isEmpty()) return;
      LanguageManager lang = this.plugin.getLanguageManager();
      String lineFormat = lang.get("hologram.line");
      String color1 = lang.contains("hologram.color-1") ? lang.get("hologram.color-1") : "&6";
      String color2 = lang.contains("hologram.color-2") ? lang.get("hologram.color-2") : "&7";
      String color3 = lang.contains("hologram.color-3") ? lang.get("hologram.color-3") : "&c";
      String colorDefault = lang.contains("hologram.color-default") ? lang.get("hologram.color-default") : "&f";

      for (HologramData hd : this.holograms) {
         ProxyDatabase db = this.plugin.getDatabase();
         List<ProxyDatabase.LeaderboardEntry> entries = db.isEnabled() ? db.getLeaderboard(hd.stat, hd.armorStands.size() - 1) : new ArrayList<>();

         String statKey = "hologram.title-" + hd.stat;
         String title;
         if (lang.contains(statKey)) {
            title = lang.get(statKey);
         } else {
            title = lang.get("hologram.title", "stat", hd.stat.replace("_", " "));
         }

         if (hd.armorStands.size() >= 1) {
            hd.armorStands.get(0).setCustomName(title);
         }

         for (int i = 1; i < hd.armorStands.size(); i++) {
            int index = i - 1;
            String lineText;
            if (index < entries.size()) {
               ProxyDatabase.LeaderboardEntry entry = entries.get(index);
               String playerName = entry.getUuid();
               try {
                  org.bukkit.OfflinePlayer off = Bukkit.getOfflinePlayer(java.util.UUID.fromString(entry.getUuid()));
                  if (off.getName() != null) playerName = off.getName();
               } catch (IllegalArgumentException e) {}
               String rankColor = index == 0 ? color1 : (index == 1 ? color2 : (index == 2 ? color3 : colorDefault));
               lineText = lineFormat
                  .replace("{rank}", String.valueOf(index + 1))
                  .replace("{player}", playerName)
                  .replace("{value}", String.valueOf(entry.getValue()));
               lineText = ChatColor.translateAlternateColorCodes('&', rankColor + lineText);
            } else {
               lineText = "§r";
            }
            hd.armorStands.get(i).setCustomName(lineText);
         }
      }
   }

   public static boolean isValidStat(String stat) {
      return stat.equals("kills") || stat.equals("deaths") || stat.equals("final_kills") || stat.equals("final_deaths")
         || stat.equals("wins") || stat.equals("losses") || stat.equals("beds_broken") || stat.equals("games_played");
   }

   private void removeAll() {
      for (HologramData hd : this.holograms) {
         hd.remove();
      }
      this.holograms.clear();
   }

   private static class HologramData {
      final String id;
      final Location location;
      final String stat;
      final List<ArmorStand> armorStands;

      HologramData(String id, Location location, String stat) {
         this.id = id;
         this.location = location;
         this.stat = stat;
         this.armorStands = new ArrayList<>();
      }

      void spawn(int lines) {
         this.remove();
         int totalLines = lines + 2;
         double startY = location.getY() + (totalLines - 1) * 0.3;
         for (int i = 0; i < totalLines; i++) {
            Location lineLoc = location.clone();
            lineLoc.setY(startY - i * 0.3);
            try {
               ArmorStand as = location.getWorld().spawn(lineLoc, ArmorStand.class, stand -> {
                  stand.setVisible(false);
                  stand.setGravity(false);
                  stand.setCanPickupItems(false);
                  stand.setCustomNameVisible(true);
                  stand.setCustomName("§r");
                  stand.setMarker(true);
                  stand.setArms(false);
                  stand.setBasePlate(false);
                  stand.setSmall(true);
                  stand.setCollidable(false);
                  stand.setInvulnerable(true);
                  stand.setSilent(true);
               });
               this.armorStands.add(as);
            } catch (Exception e) {
               // skip
            }
         }
      }

      void remove() {
         for (ArmorStand as : this.armorStands) {
            if (as != null && as.isValid()) {
               as.remove();
            }
         }
         this.armorStands.clear();
      }
   }
}
