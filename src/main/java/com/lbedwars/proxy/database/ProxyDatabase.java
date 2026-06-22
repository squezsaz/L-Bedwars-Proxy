package com.lbedwars.proxy.database;

import com.lbedwars.proxy.LBedWarsProxy;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ProxyDatabase {
   private final LBedWarsProxy plugin;
   private HikariDataSource dataSource;
   private final String prefix;
   private boolean enabled;
   private boolean tablesReady;

   public ProxyDatabase(LBedWarsProxy plugin) {
      this.plugin = plugin;
      this.prefix = plugin.getConfig().getString("database.table-prefix", "");
      this.enabled = plugin.getConfig().contains("database.host");
      if (this.enabled) {
         this.connect();
      }
   }

   private void connect() {
      try {
         String host = this.plugin.getConfig().getString("database.host", "localhost");
         int port = this.plugin.getConfig().getInt("database.port", 3306);
         String database = this.plugin.getConfig().getString("database.database", "lbedwars");
         String username = this.plugin.getConfig().getString("database.username", "root");
         String password = this.plugin.getConfig().getString("database.password", "");
         int poolSize = this.plugin.getConfig().getInt("database.pool-size", 5);
         HikariConfig config = new HikariConfig();
         config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&characterEncoding=utf8");
         config.setUsername(username);
         config.setPassword(password);
         config.setMaximumPoolSize(poolSize);
         config.setMinimumIdle(1);
         config.setConnectionTimeout(5000L);
         config.addDataSourceProperty("cachePrepStmts", "true");
         config.addDataSourceProperty("prepStmtCacheSize", "100");
          this.dataSource = new HikariDataSource(config);
          this.tablesReady = true;
         this.plugin.getLogger().info("Database connected: " + host + ":" + port + "/" + database);
      } catch (Exception e) {
         this.plugin.getLogger().warning("Database connection failed: " + e.getMessage() + " - stats/leaderboard disabled");
      }
   }

   public boolean isEnabled() {
      return this.enabled && this.tablesReady;
   }

   public Map<String, Integer> getPlayerStats(UUID uuid) {
      Map<String, Integer> stats = new LinkedHashMap<>();
      if (!this.isEnabled()) return stats;
      String sql = "SELECT kills, deaths, final_kills, final_deaths, wins, losses, beds_broken, games_played, level, xp FROM " + this.prefix + "player_stats WHERE uuid = ?";
      try (Connection conn = this.dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
         ps.setString(1, uuid.toString());
         try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
               stats.put("kills", rs.getInt("kills"));
               stats.put("deaths", rs.getInt("deaths"));
               stats.put("final_kills", rs.getInt("final_kills"));
               stats.put("final_deaths", rs.getInt("final_deaths"));
               stats.put("wins", rs.getInt("wins"));
               stats.put("losses", rs.getInt("losses"));
               stats.put("beds_broken", rs.getInt("beds_broken"));
               stats.put("games_played", rs.getInt("games_played"));
               stats.put("level", rs.getInt("level"));
               stats.put("xp", rs.getInt("xp"));
            }
         }
      } catch (SQLException e) {
         this.plugin.getLogger().warning("Database error: " + e.getMessage());
      }
      return stats;
   }

   public List<LeaderboardEntry> getLeaderboard(String stat, int limit) {
      List<LeaderboardEntry> result = new ArrayList<>();
      if (!this.isEnabled()) return result;
      String column = stat.replace("-", "_");
      String sql = "SELECT uuid, " + column + " FROM " + this.prefix + "player_stats ORDER BY " + column + " DESC LIMIT ?";
      try (Connection conn = this.dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
         ps.setInt(1, limit);
         try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
               result.add(new LeaderboardEntry(rs.getString("uuid"), rs.getInt(column)));
            }
         }
      } catch (SQLException e) {
         this.plugin.getLogger().warning("Database error: " + e.getMessage());
      }
      return result;
   }

   public void close() {
      if (this.dataSource != null && !this.dataSource.isClosed()) {
         this.dataSource.close();
      }
   }

   public static class LeaderboardEntry {
      private final String uuid;
      private final int value;

      public LeaderboardEntry(String uuid, int value) {
         this.uuid = uuid;
         this.value = value;
      }

      public String getUuid() {
         return this.uuid;
      }

      public int getValue() {
         return this.value;
      }
   }
}