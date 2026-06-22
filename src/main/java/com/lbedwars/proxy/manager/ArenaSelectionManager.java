package com.lbedwars.proxy.manager;

import com.lbedwars.proxy.model.ArenaStatus;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class ArenaSelectionManager {
   private final ConcurrentHashMap<String, ArenaStatus> servers;
   private final CopyOnWriteArrayList<ArenaStatus> serverList;
   private final ConcurrentHashMap<String, String> rejoinPlayers;
   private Consumer<String> onArenaBecameAvailable;

   public ArenaSelectionManager() {
      this.servers = new ConcurrentHashMap<>();
      this.serverList = new CopyOnWriteArrayList<>();
      this.rejoinPlayers = new ConcurrentHashMap<>();
   }

   public void setOnArenaBecameAvailable(Consumer<String> callback) {
      this.onArenaBecameAvailable = callback;
   }

   public void updateFromSocket(String serverName, String mode, int onlinePlayers, int maxPlayers, String state, String mapName, int gameTime, int spectators, java.util.List<String> playerList, java.util.List<String> rejoinPlayerList) {
      ArenaStatus status = this.servers.get(serverName);
      boolean wasUnavailable = status == null || !status.isJoinable();
      if (status == null) {
         status = new ArenaStatus(serverName, mode);
         this.servers.put(serverName, status);
         this.serverList.add(status);
      }
      String oldState = status.getState();
      status.setMode(mode);
      status.setState(state);
      status.setOnlinePlayers(onlinePlayers);
      status.setMaxPlayers(maxPlayers);
      status.setMapName(mapName);
      status.setGameTime(gameTime);
      status.setSpectators(spectators);
      status.setPlayerList(playerList);
      status.setOnline(true);
      status.setLastUpdate(System.currentTimeMillis());

      this.rejoinPlayers.entrySet().removeIf(e -> e.getValue().equals(serverName));
      for (String name : rejoinPlayerList) {
         this.rejoinPlayers.put(name, serverName);
      }

      if (this.onArenaBecameAvailable != null && wasUnavailable && status.isJoinable()) {
         this.onArenaBecameAvailable.accept(mode);
      }
   }

   public ArenaStatus getBestArena(String mode) {
      ArenaStatus best = null;
      int bestScore = -1;

      for (ArenaStatus status : serverList) {
         if (!status.isOnline() || !status.getMode().equalsIgnoreCase(mode)) continue;
         if (!status.isJoinable()) continue;

         int score;
         String state = status.getState();
         if (state.equals("WAITING")) {
            score = 100 + status.getOnlinePlayers();
         } else if (state.equals("STARTING")) {
            score = 50 + status.getOnlinePlayers();
         } else {
            continue;
         }

         if (score > bestScore) {
            bestScore = score;
            best = status;
         }
      }

      return best;
   }

   public ArenaStatus getServer(String name) {
      return servers.get(name);
   }

   public Collection<ArenaStatus> getAllServers() {
      return Collections.unmodifiableCollection(serverList);
   }

   public ArenaStatus findPlayerServer(String playerName) {
      for (ArenaStatus status : serverList) {
         if (!status.isOnline()) continue;
         if (status.getPlayerList().contains(playerName)) {
            return status;
         }
      }
      return null;
   }

   public String findPlayerRejoinServer(String playerName) {
      return this.rejoinPlayers.get(playerName);
   }
}
