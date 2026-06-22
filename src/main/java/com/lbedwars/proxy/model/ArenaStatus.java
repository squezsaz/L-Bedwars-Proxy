package com.lbedwars.proxy.model;

public class ArenaStatus {
   private final String name;
   private String mode;
   private String state;
   private int onlinePlayers;
   private int maxPlayers;
   private String mapName;
   private int gameTime;
   private int spectators;
   private java.util.List<String> playerList;
   private boolean online;
   private long lastUpdate;

   public ArenaStatus(String name, String mode) {
      this.name = name;
      this.mode = mode;
      this.state = "OFFLINE";
      this.online = false;
      this.mapName = "";
      this.playerList = new java.util.ArrayList<>();
   }

   public String getName() { return name; }
   public String getMode() { return mode; }
   public void setMode(String mode) { this.mode = mode; }
   public String getState() { return state; }
   public void setState(String state) { this.state = state; }
   public int getOnlinePlayers() { return onlinePlayers; }
   public void setOnlinePlayers(int onlinePlayers) { this.onlinePlayers = onlinePlayers; }
   public int getMaxPlayers() { return maxPlayers; }
   public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }
   public String getMapName() { return mapName; }
   public void setMapName(String mapName) { this.mapName = mapName; }
   public boolean isOnline() { return online; }
   public void setOnline(boolean online) { this.online = online; }
   public int getGameTime() { return gameTime; }
   public void setGameTime(int gameTime) { this.gameTime = gameTime; }
   public int getSpectators() { return spectators; }
   public void setSpectators(int spectators) { this.spectators = spectators; }
   public java.util.List<String> getPlayerList() { return playerList; }
   public void setPlayerList(java.util.List<String> playerList) { this.playerList = playerList; }
   public long getLastUpdate() { return lastUpdate; }
   public void setLastUpdate(long lastUpdate) { this.lastUpdate = lastUpdate; }

   public boolean isJoinable() {
      return online && (state.equals("WAITING") || state.equals("STARTING"));
   }
}
