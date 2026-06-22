package com.lbedwars.proxy.socket;

import com.lbedwars.proxy.LBedWarsProxy;
import com.lbedwars.proxy.language.LanguageManager;
import com.lbedwars.proxy.manager.ArenaSelectionManager;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Scanner;
import java.util.function.Consumer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class ArenaSocketHandler implements Runnable {
   private static final int PROTOCOL_VERSION = 1;
   private final Socket socket;
   private final ArenaSelectionManager arenaSelectionManager;
   private final Consumer<InetAddress> disconnectCallback;
   private final LanguageManager lang;
   private final String token;
   private boolean authenticated;
   private String serverName;

   public ArenaSocketHandler(Socket socket, Consumer<InetAddress> disconnectCallback) {
      this.socket = socket;
      this.arenaSelectionManager = LBedWarsProxy.getPlugin().getArenaSelectionManager();
      this.disconnectCallback = disconnectCallback;
      this.lang = LBedWarsProxy.getPlugin().getLanguageManager();
      this.token = LBedWarsProxy.getPlugin().getConfig().getString("socket-token", "");
   }

   public void run() {
      try {
         Scanner scanner = new Scanner(this.socket.getInputStream(), "UTF-8");
         PrintWriter writer = new PrintWriter(new OutputStreamWriter(this.socket.getOutputStream(), "UTF-8"), true);

         if (!this.doHandshake(scanner, writer)) {
            this.socket.close();
            return;
         }

         while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.isEmpty()) continue;
            this.handleMessage(line, writer);
         }
      } catch (java.net.SocketTimeoutException e) {
         LBedWarsProxy.getPlugin().getLogger().warning(this.lang.get("console.socket-timeout", "ip", this.socket.getInetAddress().getHostAddress()));
      } catch (IOException e) {
         if (this.authenticated) {
            LBedWarsProxy.getPlugin().getLogger().info(this.lang.get("console.server-disconnected", "name", this.serverName != null ? this.serverName : this.socket.getInetAddress().getHostAddress()));
         }
      } finally {
         if (this.disconnectCallback != null) {
            this.disconnectCallback.accept(this.socket.getInetAddress());
         }
         try {
            this.socket.close();
         } catch (IOException ignored) {}
      }
   }

   private boolean doHandshake(Scanner scanner, PrintWriter writer) throws IOException {
      if (!scanner.hasNextLine()) return false;

      String hello = scanner.nextLine();
      if (hello == null || !hello.contains("\"HELLO\"") || !hello.contains("\"protocol\"")) {
         writer.println("{\"type\":\"ERROR\",\"message\":\"" + this.lang.get("console.expected-hello") + "\"}");
         return false;
      }

      int protocol = extractInt(hello, "protocol");
      if (protocol != PROTOCOL_VERSION) {
         writer.println("{\"type\":\"ERROR\",\"message\":\"Unsupported protocol version: " + protocol + "\"}");
         LBedWarsProxy.getPlugin().getLogger().warning(this.lang.get("console.unsupported-protocol", "ip", this.socket.getInetAddress().getHostAddress(), "protocol", String.valueOf(protocol)));
         return false;
      }

      writer.println("{\"type\":\"HELLO_OK\",\"protocol\":" + PROTOCOL_VERSION + "}");

      if (!scanner.hasNextLine()) return false;
      String auth = scanner.nextLine();
      if (auth == null || !auth.contains("\"AUTH\"")) {
         writer.println("{\"type\":\"ERROR\",\"message\":\"" + this.lang.get("console.expected-auth") + "\"}");
         return false;
      }

      String authToken = extractString(auth, "token");
      String authServer = extractString(auth, "server");
      if (this.token.isEmpty() || !this.token.equals(authToken)) {
         writer.println("{\"type\":\"AUTH_FAIL\"}");
         LBedWarsProxy.getPlugin().getLogger().warning(this.lang.get("console.auth-failed", "ip", this.socket.getInetAddress().getHostAddress(), "name", authServer != null ? authServer : "?"));
         return false;
      }

      this.serverName = authServer;
      this.authenticated = true;
      writer.println("{\"type\":\"AUTH_OK\"}");
      LBedWarsProxy.getPlugin().getLogger().info(this.lang.get("console.auth-ok", "name", this.serverName, "ip", this.socket.getInetAddress().getHostAddress()));
      return true;
   }

   private void handleMessage(String json, PrintWriter writer) {
      if (!this.authenticated) return;
      if (!json.contains("\"type\":\"UPDATE\"")) return;

      try {
         com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();

         if (!root.has("data") || !root.has("signature")) return;

         String data = root.get("data").toString();
         String signature = root.get("signature").getAsString();

         if (!this.verifyHmac(data, signature)) {
            LBedWarsProxy.getPlugin().getLogger().warning(this.lang.get("console.hmac-invalid", "name", this.serverName));
            return;
         }

         com.google.gson.JsonObject dataObj = com.google.gson.JsonParser.parseString(data).getAsJsonObject();

          String serverName = dataObj.get("server_name").getAsString();
          String arenaGroup = dataObj.get("arena_group").getAsString();
          String arenaStatus = dataObj.get("arena_status").getAsString();
          int maxPlayers = dataObj.get("arena_max_players").getAsInt();
          int currentPlayers = dataObj.get("arena_current_players").getAsInt();
          String mapName = dataObj.has("arena_map") ? dataObj.get("arena_map").getAsString() : serverName;
          int gameTime = dataObj.has("arena_time") ? dataObj.get("arena_time").getAsInt() : 0;
          int spectators = dataObj.has("arena_spectators") ? dataObj.get("arena_spectators").getAsInt() : 0;

           java.util.List<String> playerList = new java.util.ArrayList<>();
           if (dataObj.has("arena_players")) {
              com.google.gson.JsonArray arr = dataObj.get("arena_players").getAsJsonArray();
              for (int i = 0; i < arr.size(); i++) {
                 playerList.add(arr.get(i).getAsString());
              }
           }

           java.util.List<String> rejoinPlayers = new java.util.ArrayList<>();
           if (dataObj.has("rejoin_players")) {
              com.google.gson.JsonArray arr = dataObj.get("rejoin_players").getAsJsonArray();
              for (int i = 0; i < arr.size(); i++) {
                 rejoinPlayers.add(arr.get(i).getAsString());
              }
           }

           this.arenaSelectionManager.updateFromSocket(serverName, arenaGroup, currentPlayers, maxPlayers, arenaStatus, mapName, gameTime, spectators, playerList, rejoinPlayers);
      } catch (com.google.gson.JsonSyntaxException | IllegalStateException | NullPointerException e) {
         LBedWarsProxy.getPlugin().getLogger().warning(this.lang.get("console.json-invalid", "name", this.serverName != null ? this.serverName : "unknown"));
      }
   }

   private boolean verifyHmac(String data, String signature) {
      if (this.token.isEmpty()) return true;
      String expected = this.hmac(data);
      return expected.equals(signature);
   }

   private String hmac(String data) {
      try {
         Mac mac = Mac.getInstance("HmacSHA256");
         SecretKeySpec keySpec = new SecretKeySpec(this.token.getBytes("UTF-8"), "HmacSHA256");
         mac.init(keySpec);
         byte[] raw = mac.doFinal(data.getBytes("UTF-8"));
         StringBuilder hex = new StringBuilder();
         for (byte b : raw) {
            hex.append(String.format("%02x", b & 0xFF));
         }
         return hex.toString();
      } catch (Exception e) {
         return "";
      }
   }

   private static String extractString(String json, String key) {
      String search = "\"" + key + "\":\"";
      int start = json.indexOf(search);
      if (start == -1) return "";
      start += search.length();
      int end = json.indexOf('"', start);
      return end == -1 ? "" : json.substring(start, end);
   }

   private static int extractInt(String json, String key) {
      String search = "\"" + key + "\":";
      int start = json.indexOf(search);
      if (start == -1) return 0;
      start += search.length();
      StringBuilder num = new StringBuilder();
      for (int i = start; i < json.length(); i++) {
         char c = json.charAt(i);
         if (Character.isDigit(c) || c == '-') {
            num.append(c);
         } else if (num.length() > 0) {
            break;
         }
      }
      return num.length() == 0 ? 0 : Integer.parseInt(num.toString());
   }
}
