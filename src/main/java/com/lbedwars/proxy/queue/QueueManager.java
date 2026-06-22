package com.lbedwars.proxy.queue;

import com.lbedwars.proxy.LBedWarsProxy;
import com.lbedwars.proxy.language.LanguageManager;
import com.lbedwars.proxy.manager.ArenaSelectionManager;
import com.lbedwars.proxy.model.ArenaStatus;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

public class QueueManager {
   private final LBedWarsProxy plugin;
   private final LinkedHashMap<UUID, QueueEntry> queue;
   private final List<String> priorityPermissions;

   public QueueManager(LBedWarsProxy plugin) {
      this.plugin = plugin;
      this.queue = new LinkedHashMap<>();
      this.priorityPermissions = plugin.getConfig().getStringList("queue.priority-permissions");
   }

   public boolean isEnabled() {
      return this.plugin.getConfig().getBoolean("queue.enabled", false);
   }

   public boolean addToQueue(Player player, String mode) {
      if (!this.isEnabled()) return false;
      QueueEntry existing = this.queue.get(player.getUniqueId());
      if (existing != null) {
         if (existing.mode.equals(mode)) return true;
         this.queue.remove(player.getUniqueId());
      }
      boolean priority = this.hasPriority(player);
      this.queue.put(player.getUniqueId(), new QueueEntry(player.getUniqueId(), player.getName(), mode, priority, System.currentTimeMillis()));
      return true;
   }

   public boolean isQueued(UUID uuid) {
      return this.queue.containsKey(uuid);
   }

   public void removeFromQueue(UUID uuid) {
      this.queue.remove(uuid);
   }

   public int getPosition(UUID uuid) {
      int pos = 1;
      QueueEntry entry = this.queue.get(uuid);
      if (entry == null) return -1;
      for (QueueEntry e : this.queue.values()) {
         if (e.uuid.equals(uuid)) break;
         pos++;
      }
      return pos;
   }

   public void checkQueue(String mode) {
      if (!this.isEnabled()) return;
      ArenaSelectionManager asm = this.plugin.getArenaSelectionManager();
      ArenaStatus arena = asm.getBestArena(mode);
      if (arena == null) return;
      int slots = arena.getMaxPlayers() - arena.getOnlinePlayers();
      if (slots <= 0) return;

      Iterator<java.util.Map.Entry<UUID, QueueEntry>> it = this.queue.entrySet().iterator();
      while (it.hasNext() && slots > 0) {
         java.util.Map.Entry<UUID, QueueEntry> entry = it.next();
         QueueEntry qe = entry.getValue();
         if (!qe.mode.equalsIgnoreCase(mode)) continue;

         Player player = Bukkit.getPlayer(qe.uuid);
         if (player == null || !player.isOnline()) {
            it.remove();
            continue;
         }

         this.connectPlayer(player, arena.getName());
         player.sendMessage(this.plugin.getLanguageManager().get("queue.connected", "server", arena.getName()));
         it.remove();
         slots--;
      }
   }

   private void connectPlayer(Player player, String serverName) {
      try {
         ByteArrayOutputStream b = new ByteArrayOutputStream();
         DataOutputStream out = new DataOutputStream(b);
         out.writeUTF("Connect");
         out.writeUTF(serverName);
         player.sendPluginMessage(this.plugin, "BungeeCord", b.toByteArray());
      } catch (Exception e) {
         player.sendMessage(this.plugin.getLanguageManager().get("command.connect-fail"));
      }
   }

   private boolean hasPriority(Player player) {
      for (String perm : this.priorityPermissions) {
         if (player.hasPermission(perm)) return true;
      }
      return false;
   }

   public int getQueueSize() {
      return this.queue.size();
   }

   public int getQueueSize(String mode) {
      int count = 0;
      for (QueueEntry e : this.queue.values()) {
         if (e.mode.equalsIgnoreCase(mode)) count++;
      }
      return count;
   }

   private static class QueueEntry {
      final UUID uuid;
      final String name;
      final String mode;
      final boolean priority;
      final long joinTime;

      QueueEntry(UUID uuid, String name, String mode, boolean priority, long joinTime) {
         this.uuid = uuid;
         this.name = name;
         this.mode = mode;
         this.priority = priority;
         this.joinTime = joinTime;
      }
   }
}
