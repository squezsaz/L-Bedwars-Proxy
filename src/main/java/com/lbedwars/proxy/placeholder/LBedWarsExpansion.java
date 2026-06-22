package com.lbedwars.proxy.placeholder;

import com.lbedwars.proxy.LBedWarsProxy;
import com.lbedwars.proxy.database.ProxyDatabase;
import com.lbedwars.proxy.model.ArenaStatus;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import java.util.Map;
import java.util.UUID;

public class LBedWarsExpansion extends PlaceholderExpansion {
   private final LBedWarsProxy plugin;

   public LBedWarsExpansion(LBedWarsProxy plugin) {
      this.plugin = plugin;
   }

   public String getIdentifier() {
      return "lbedwars";
   }

   public String getAuthor() {
      return "L-BedWars";
   }

   public String getVersion() {
      return "1.0.0";
   }

   public boolean persist() {
      return true;
   }

   public String onRequest(OfflinePlayer offlinePlayer, String identifier) {
      if (offlinePlayer == null) return "";
      UUID uuid = offlinePlayer.getUniqueId();
      ProxyDatabase db = this.plugin.getDatabase();

      if (identifier.equals("arena_online")) {
         int total = 0;
         for (ArenaStatus s : this.plugin.getArenaSelectionManager().getAllServers()) {
            if (s.isOnline()) total += s.getOnlinePlayers();
         }
         return String.valueOf(total);
      }

      if (identifier.equals("arena_count")) {
         int count = 0;
         for (ArenaStatus s : this.plugin.getArenaSelectionManager().getAllServers()) {
            if (s.isOnline()) count++;
         }
         return String.valueOf(count);
      }

      if (!db.isEnabled()) return "0";
      Map<String, Integer> stats = db.getPlayerStats(uuid);
      if (stats.isEmpty()) return "0";

      return switch (identifier) {
         case "kills" -> String.valueOf(stats.getOrDefault("kills", 0));
         case "deaths" -> String.valueOf(stats.getOrDefault("deaths", 0));
         case "final_kills" -> String.valueOf(stats.getOrDefault("final_kills", 0));
         case "final_deaths" -> String.valueOf(stats.getOrDefault("final_deaths", 0));
         case "wins" -> String.valueOf(stats.getOrDefault("wins", 0));
         case "losses" -> String.valueOf(stats.getOrDefault("losses", 0));
         case "beds_broken" -> String.valueOf(stats.getOrDefault("beds_broken", 0));
         case "games_played" -> String.valueOf(stats.getOrDefault("games_played", 0));
         case "level" -> String.valueOf(stats.getOrDefault("level", 1));
         case "xp" -> String.valueOf(stats.getOrDefault("xp", 0));
         case "kdr" -> {
            int k = stats.getOrDefault("kills", 0);
            int d = stats.getOrDefault("deaths", 0);
            yield d == 0 ? String.valueOf((double) k) : String.format("%.2f", (double) k / (double) d);
         }
         default -> null;
      };
   }
}
