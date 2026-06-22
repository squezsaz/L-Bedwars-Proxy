package com.lbedwars.proxy;

import com.lbedwars.proxy.command.AdminCommand;
import com.lbedwars.proxy.command.BedWarsCommand;
import com.lbedwars.proxy.database.ProxyDatabase;
import com.lbedwars.proxy.hologram.HologramManager;
import com.lbedwars.proxy.language.LanguageManager;
import com.lbedwars.proxy.manager.ArenaSelectionManager;
import com.lbedwars.proxy.party.PartyIntegration;
import com.lbedwars.proxy.placeholder.LBedWarsExpansion;
import com.lbedwars.proxy.queue.QueueManager;
import com.lbedwars.proxy.socket.ServerSocketTask;
import com.lbedwars.proxy.socket.TimeOutTask;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Method;
import java.util.List;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class LBedWarsProxy extends JavaPlugin {
   private static LBedWarsProxy instance;
   private LanguageManager languageManager;
   private ArenaSelectionManager arenaSelectionManager;
   private ProxyDatabase database;
   private AdminCommand adminCommand;
   private HologramManager hologramManager;
   private QueueManager queueManager;
   private int timeoutTaskId;
   private boolean folia;
   private Object foliaTimeoutTask;

   public void onEnable() {
      instance = this;
      this.saveDefaultConfig();
      new Metrics(this, 31956);
      this.folia = this.detectFolia();
      this.languageManager = new LanguageManager(this);
      this.arenaSelectionManager = new ArenaSelectionManager();
      this.queueManager = new QueueManager(this);
      this.arenaSelectionManager.setOnArenaBecameAvailable(mode -> {
         if (this.queueManager != null) {
            this.queueManager.checkQueue(mode);
         }
      });
      PartyIntegration.init();
      this.database = new ProxyDatabase(this);
      Bukkit.getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
      this.registerCommands();
      this.registerListeners();
      this.startTimeoutTask();
      this.registerPlaceholderExpansion();
      this.hologramManager = new HologramManager(this);
      this.hologramManager.start();
      int port = this.getConfig().getInt("socket-port", 5000);
      String bindAddress = this.getConfig().getString("bind-address", "0.0.0.0");
      List<String> allowedIps = this.getConfig().getStringList("allowed-ips");
      int maxConnections = this.getConfig().getInt("max-connections", 50);
      ServerSocketTask.init(port, bindAddress, allowedIps, maxConnections);
      this.getLogger().info(this.languageManager.get("console.enabled", "servers", "0", "port", String.valueOf(port)));
   }

   public void onDisable() {
      if (this.hologramManager != null) this.hologramManager.stop();
      ServerSocketTask.stop();
      if (this.database != null) this.database.close();
      if (this.folia) {
         if (this.foliaTimeoutTask != null) cancelFoliaTask(this.foliaTimeoutTask);
      } else {
         if (this.timeoutTaskId != -1) Bukkit.getScheduler().cancelTask(this.timeoutTaskId);
      }
   }

   private void registerCommands() {
      BedWarsCommand cmd = new BedWarsCommand(this);
      this.getCommand("bw").setExecutor(cmd);
      this.getCommand("bw").setTabCompleter(cmd);
      this.adminCommand = new AdminCommand(this);
      this.getCommand("bwa").setExecutor(this.adminCommand);
      this.getCommand("bwa").setTabCompleter(this.adminCommand);
   }

   private void registerListeners() {
      Bukkit.getPluginManager().registerEvents(this.adminCommand, this);
   }

   private void startTimeoutTask() {
      Runnable task = new TimeOutTask(this.arenaSelectionManager);
      if (this.folia) {
         this.foliaTimeoutTask = this.scheduleFoliaTask(task, 100L);
      } else {
         this.timeoutTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, task, 200L, 100L);
      }
   }

   private void registerPlaceholderExpansion() {
      if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
         new LBedWarsExpansion(this).register();
         this.getLogger().info("PlaceholderAPI expansion registered.");
      }
   }

   public void connectToServer(Player player, String serverName) {
      try {
         ByteArrayOutputStream b = new ByteArrayOutputStream();
         DataOutputStream out = new DataOutputStream(b);
         out.writeUTF("Connect");
         out.writeUTF(serverName);
         player.sendPluginMessage(this, "BungeeCord", b.toByteArray());
      } catch (Exception e) {
         player.sendMessage(this.languageManager.get("command.connect-fail"));
      }
   }

   private boolean detectFolia() {
      try {
         Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
         return true;
      } catch (ClassNotFoundException e) {
         return false;
      }
   }

   private Object scheduleFoliaTask(Runnable task, long interval) {
      try {
         Method getScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler");
         Object scheduler = getScheduler.invoke(null);
         Method runAtFixedRate = scheduler.getClass().getMethod("runAtFixedRate", JavaPlugin.class, Runnable.class, long.class, long.class);
         return runAtFixedRate.invoke(scheduler, this, task, 1L, interval);
      } catch (Exception e) {
         this.getLogger().warning(this.languageManager.get("console.folia-fallback"));
         this.timeoutTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, task, 200L, interval);
         return null;
      }
   }

   private void cancelFoliaTask(Object foliaTask) {
      try {
         foliaTask.getClass().getMethod("cancel").invoke(foliaTask);
      } catch (Exception ignored) {}
   }

   public static LBedWarsProxy getPlugin() { return instance; }
   public LanguageManager getLanguageManager() { return this.languageManager; }
   public ArenaSelectionManager getArenaSelectionManager() { return this.arenaSelectionManager; }
   public ProxyDatabase getDatabase() { return this.database; }
   public QueueManager getQueueManager() { return this.queueManager; }
   public HologramManager getHologramManager() { return this.hologramManager; }
}
