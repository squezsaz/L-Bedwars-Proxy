package com.lbedwars.proxy.socket;

import com.lbedwars.proxy.LBedWarsProxy;
import com.lbedwars.proxy.language.LanguageManager;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerSocketTask {
   private static ServerSocketTask instance;
   private ServerSocket serverSocket;
   private boolean running;
   private ExecutorService threadPool;
   private final List<CidrEntry> whitelist;
   private final ConcurrentHashMap<String, AtomicInteger> connectionsPerIp;
   private int maxConnectionsPerIp;
   private final LanguageManager lang;

   public static boolean init(int port, String bindAddress, List<String> allowedIps, int maxConnections) {
      if (instance != null) return true;
      try {
         instance = new ServerSocketTask(port, bindAddress, allowedIps, maxConnections);
         return true;
      } catch (IOException e) {
         LBedWarsProxy.getPlugin().getLogger().warning(LBedWarsProxy.getPlugin().getLanguageManager().get("console.socket-open-fail", "port", String.valueOf(port), "error", e.getMessage()));
         return false;
      }
   }

   public static void stop() {
      if (instance != null) {
         instance.running = false;
         try {
            if (instance.serverSocket != null) instance.serverSocket.close();
         } catch (IOException ignored) {}
         if (instance.threadPool != null) instance.threadPool.shutdownNow();
         instance = null;
      }
   }

   private ServerSocketTask(int port, String bindAddress, List<String> allowedIps, int maxConnections) throws IOException {
      this.lang = LBedWarsProxy.getPlugin().getLanguageManager();
      InetAddress bindAddr = bindAddress == null || bindAddress.isEmpty() ? null : InetAddress.getByName(bindAddress);
      this.serverSocket = new ServerSocket(port, 50, bindAddr);
      this.serverSocket.setSoTimeout(2000);
      this.running = true;
      this.whitelist = new ArrayList<>();
      this.connectionsPerIp = new ConcurrentHashMap<>();
      this.maxConnectionsPerIp = maxConnections;
      this.threadPool = Executors.newFixedThreadPool(maxConnections);
      this.parseWhitelist(allowedIps);
      Thread thread = new Thread(this::acceptLoop, "LBedWars-Socket-Accept");
      thread.setDaemon(true);
      thread.start();
      LBedWarsProxy.getPlugin().getLogger().info(this.lang.get("console.socket-listening", "port", String.valueOf(port), "max", String.valueOf(maxConnections)));
   }

   private void acceptLoop() {
      while (this.running) {
         try {
            Socket client = this.serverSocket.accept();

            InetAddress addr = client.getInetAddress();
            String ip = addr.getHostAddress();

            if (!this.isAllowed(ip)) {
               LBedWarsProxy.getPlugin().getLogger().warning(this.lang.get("console.blocked-not-whitelisted", "ip", ip));
               client.close();
               continue;
            }

            AtomicInteger count = this.connectionsPerIp.computeIfAbsent(ip, k -> new AtomicInteger(0));
            if (count.incrementAndGet() > this.maxConnectionsPerIp) {
               LBedWarsProxy.getPlugin().getLogger().warning(this.lang.get("console.blocked-too-many", "ip", ip));
               count.decrementAndGet();
               client.close();
               continue;
            }

            client.setSoTimeout(10000);
            ArenaSocketHandler handler = new ArenaSocketHandler(client, this::onDisconnect);
            this.threadPool.execute(handler);
         } catch (java.net.SocketTimeoutException ignored) {
         } catch (IOException e) {
            if (this.running) {
               LBedWarsProxy.getPlugin().getLogger().warning(this.lang.get("console.socket-accept-error", "error", e.getMessage()));
            }
         }
      }
   }

   private void onDisconnect(InetAddress addr) {
      if (addr != null) {
         String ip = addr.getHostAddress();
         AtomicInteger count = this.connectionsPerIp.get(ip);
         if (count != null) {
            count.decrementAndGet();
         }
      }
   }

   private boolean isAllowed(String ip) {
      if (this.whitelist.isEmpty()) return true;
      for (CidrEntry entry : this.whitelist) {
         if (entry.matches(ip)) return true;
      }
      return false;
   }

   private void parseWhitelist(List<String> ips) {
      for (String entry : ips) {
         entry = entry.trim();
         if (entry.isEmpty()) continue;
         if (entry.contains("/")) {
            String[] parts = entry.split("/");
            try {
               InetAddress base = InetAddress.getByName(parts[0]);
               int prefix = Integer.parseInt(parts[1]);
               this.whitelist.add(new CidrEntry(base, prefix));
            } catch (Exception e) {
               LBedWarsProxy.getPlugin().getLogger().warning(this.lang.get("console.cidr-invalid", "entry", entry));
            }
         } else {
            try {
               InetAddress addr = InetAddress.getByName(entry);
               this.whitelist.add(new CidrEntry(addr, 32));
            } catch (Exception e) {
               LBedWarsProxy.getPlugin().getLogger().warning(this.lang.get("console.ip-invalid", "entry", entry));
            }
         }
      }
   }

   private static class CidrEntry {
      private final byte[] base;
      private final int prefix;

      CidrEntry(InetAddress addr, int prefix) {
         this.base = addr.getAddress();
         this.prefix = prefix;
      }

      boolean matches(String ip) {
         try {
            byte[] target = InetAddress.getByName(ip).getAddress();
            if (target.length != this.base.length) return false;
            int fullBytes = this.prefix / 8;
            int remainderBits = this.prefix % 8;
            for (int i = 0; i < fullBytes && i < target.length; i++) {
               if (target[i] != this.base[i]) return false;
            }
            if (remainderBits > 0 && fullBytes < target.length) {
               int mask = (0xFF << (8 - remainderBits)) & 0xFF;
               if ((target[fullBytes] & mask) != (this.base[fullBytes] & mask)) return false;
            }
            return true;
         } catch (Exception e) {
            return false;
         }
      }
   }
}
