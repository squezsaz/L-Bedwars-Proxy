package com.lbedwars.proxy.command;

import com.lbedwars.proxy.LBedWarsProxy;
import com.lbedwars.proxy.database.ProxyDatabase;
import com.lbedwars.proxy.language.LanguageManager;
import com.lbedwars.proxy.party.PartyIntegration;
import com.lbedwars.proxy.model.ArenaStatus;
import com.lbedwars.proxy.queue.QueueManager;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class BedWarsCommand implements CommandExecutor, TabCompleter {
   private final LBedWarsProxy plugin;
   private final LanguageManager lang;

   public BedWarsCommand(LBedWarsProxy plugin) {
      this.plugin = plugin;
      this.lang = plugin.getLanguageManager();
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!(sender instanceof Player)) {
         sender.sendMessage(this.lang.get("command.player-only"));
         return true;
      }

      Player player = (Player)sender;

      if (args.length < 1) {
         player.sendMessage(this.lang.get("command.usage"));
         return true;
      }

      if (args[0].equalsIgnoreCase("join")) {
         if (args.length < 2) {
            player.sendMessage(this.lang.get("command.usage"));
            return true;
         }
         String mode = args[1].toLowerCase();
         if (!mode.equals("solo") && !mode.equals("duo") && !mode.equals("trio") && !mode.equals("quad")) {
            player.sendMessage(this.lang.get("command.invalid-mode"));
            return true;
         }
          ArenaStatus target = this.plugin.getArenaSelectionManager().getBestArena(mode);
          if (target == null) {
             QueueManager qm = this.plugin.getQueueManager();
             if (qm.isEnabled()) {
                qm.addToQueue(player, mode);
                int pos = qm.getPosition(player.getUniqueId());
                int qSize = qm.getQueueSize(mode);
                player.sendMessage(this.lang.get("queue.added", "mode", mode, "position", String.valueOf(pos), "size", String.valueOf(qSize)));
             } else {
                player.sendMessage(this.lang.get("command.no-arena", "mode", mode));
             }
             return true;
          }
          if (PartyIntegration.isLeader(player.getUniqueId())) {
             for (UUID memberUuid : PartyIntegration.getPartyMembers(player.getUniqueId())) {
                if (!memberUuid.equals(player.getUniqueId())) {
                   Player member = Bukkit.getPlayer(memberUuid);
                   if (member != null && member.isOnline()) {
                      this.sendToServer(member, target.getName());
                      member.sendMessage(this.lang.get("party.leader-joined", "server", target.getName(), "player", player.getName()));
                   }
                }
             }
          }
          this.sendToServer(player, target.getName());
          return true;
      }

      if (args[0].equalsIgnoreCase("rejoin")) {
         String server = this.plugin.getArenaSelectionManager().findPlayerRejoinServer(player.getName());
         if (server == null) {
            player.sendMessage(this.lang.get("command.rejoin-no-data"));
            return true;
         }
         this.sendToServer(player, server);
         return true;
      }

      if (args[0].equalsIgnoreCase("stats")) {
         this.handleStats(player, args);
         return true;
      }

      player.sendMessage(this.lang.get("command.usage"));
      return true;
   }

   private void handleStats(Player player, String[] args) {
      ProxyDatabase db = this.plugin.getDatabase();
      if (!db.isEnabled()) {
         player.sendMessage(this.lang.get("stats.db-disabled"));
         return;
      }

      UUID targetUuid = player.getUniqueId();
      String targetName = player.getName();

      if (args.length >= 2) {
         Player target = Bukkit.getPlayer(args[1]);
         if (target != null) {
            targetUuid = target.getUniqueId();
            targetName = target.getName();
         } else {
            try {
               targetUuid = UUID.fromString(args[1]);
               targetName = args[1];
            } catch (IllegalArgumentException e) {
               targetUuid = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
               targetName = args[1];
            }
         }
      }

      Map<String, Integer> stats = db.getPlayerStats(targetUuid);
      if (stats.isEmpty()) {
         player.sendMessage(this.lang.get("stats.no-data", "player", targetName));
         return;
      }

      int kills = stats.getOrDefault("kills", 0);
      int deaths = stats.getOrDefault("deaths", 0);
      double kdr = deaths == 0 ? (double) kills : Math.round((double) kills / (double) deaths * 100.0) / 100.0;

      player.sendMessage(this.lang.get("stats.header", "player", targetName));
      player.sendMessage(this.lang.get("stats.kills", "value", String.valueOf(kills)));
      player.sendMessage(this.lang.get("stats.deaths", "value", String.valueOf(deaths)));
      player.sendMessage(this.lang.get("stats.final-kills", "value", String.valueOf(stats.getOrDefault("final_kills", 0))));
      player.sendMessage(this.lang.get("stats.final-deaths", "value", String.valueOf(stats.getOrDefault("final_deaths", 0))));
      player.sendMessage(this.lang.get("stats.wins", "value", String.valueOf(stats.getOrDefault("wins", 0))));
      player.sendMessage(this.lang.get("stats.losses", "value", String.valueOf(stats.getOrDefault("losses", 0))));
      player.sendMessage(this.lang.get("stats.beds", "value", String.valueOf(stats.getOrDefault("beds_broken", 0))));
      player.sendMessage(this.lang.get("stats.games", "value", String.valueOf(stats.getOrDefault("games_played", 0))));
      player.sendMessage(this.lang.get("stats.kdr", "value", String.valueOf(kdr)));
      player.sendMessage(this.lang.get("stats.level", "value", String.valueOf(stats.getOrDefault("level", 1)), "xp", String.valueOf(stats.getOrDefault("xp", 0))));
   }

   private void sendToServer(Player player, String serverName) {
      try {
         ByteArrayOutputStream b = new ByteArrayOutputStream();
         DataOutputStream out = new DataOutputStream(b);
         out.writeUTF("Connect");
         out.writeUTF(serverName);
         player.sendPluginMessage(this.plugin, "BungeeCord", b.toByteArray());
      } catch (Exception e) {
         player.sendMessage(this.lang.get("command.connect-fail"));
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (args.length == 1) return List.of("join", "rejoin", "stats");
      if (args.length == 2 && args[0].equalsIgnoreCase("join")) return List.of("solo", "duo", "trio", "quad");
      if (args.length == 2 && args[0].equalsIgnoreCase("stats")) {
         List<String> names = new ArrayList<>();
         for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
               names.add(p.getName());
            }
         }
         return names;
      }
      return List.of();
   }
}