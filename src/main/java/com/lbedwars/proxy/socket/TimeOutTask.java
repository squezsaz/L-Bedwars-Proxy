package com.lbedwars.proxy.socket;

import com.lbedwars.proxy.LBedWarsProxy;
import com.lbedwars.proxy.language.LanguageManager;
import com.lbedwars.proxy.manager.ArenaSelectionManager;
import com.lbedwars.proxy.model.ArenaStatus;
import java.util.ArrayList;
import java.util.List;

public class TimeOutTask implements Runnable {
   private final ArenaSelectionManager arenaSelectionManager;
   private final LanguageManager lang;

   public TimeOutTask(ArenaSelectionManager arenaSelectionManager) {
      this.arenaSelectionManager = arenaSelectionManager;
      this.lang = LBedWarsProxy.getPlugin().getLanguageManager();
   }

   public void run() {
      boolean enabled = LBedWarsProxy.getPlugin().getConfig().getBoolean("health-check.enabled", true);
      int timeoutMs = LBedWarsProxy.getPlugin().getConfig().getInt("health-check.timeout-seconds", 15) * 1000;
      if (!enabled) return;

      long threshold = System.currentTimeMillis() - timeoutMs;
      List<ArenaStatus> toRemove = new ArrayList<>();

      for (ArenaStatus status : this.arenaSelectionManager.getAllServers()) {
         if (!status.isOnline()) continue;
          if (status.getLastUpdate() > 0 && status.getLastUpdate() < threshold) {
            status.setOnline(false);
            status.setState("UNKNOWN");
            String secStr = String.valueOf(timeoutMs / 1000);
            LBedWarsProxy.getPlugin().getLogger().info(this.lang.get("console.arena-timed-out", "name", status.getName(), "seconds", secStr));
         }
      }

      if (!toRemove.isEmpty()) {
         for (ArenaStatus s : toRemove) {
            s.setOnline(false);
         }
      }
   }
}
