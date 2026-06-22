package com.lbedwars.proxy.party;

import com.alessiodp.parties.api.Parties;
import com.alessiodp.parties.api.interfaces.PartiesAPI;
import com.alessiodp.parties.api.interfaces.Party;
import com.alessiodp.parties.api.interfaces.PartyPlayer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class PartyIntegration {
   private static PartiesAPI api;
   private static boolean enabled;

   public static boolean init() {
      try {
         Plugin p = Bukkit.getPluginManager().getPlugin("Parties");
         if (p == null || !p.isEnabled()) return false;
         api = Parties.getApi();
         enabled = true;
         Bukkit.getLogger().info("[L-BedWarsProxy] Parties API hooked successfully.");
         return true;
      } catch (Throwable t) {
         enabled = false;
         return false;
      }
   }

   public static boolean isEnabled() {
      return enabled;
   }

   public static boolean isInParty(UUID uuid) {
      if (!enabled || api == null) return false;
      try {
         PartyPlayer player = api.getPartyPlayer(uuid);
         return player != null && player.isInParty();
      } catch (Throwable t) {
         return false;
      }
   }

   public static boolean isLeader(UUID uuid) {
      if (!enabled || api == null) return false;
      try {
         PartyPlayer player = api.getPartyPlayer(uuid);
         if (player == null || !player.isInParty()) return false;
         Party party = api.getParty(player.getPartyId());
         return party != null && party.getLeader() != null && party.getLeader().equals(uuid);
      } catch (Throwable t) {
         return false;
      }
   }

   public static List<UUID> getPartyMembers(UUID uuid) {
      if (!enabled || api == null) return List.of(uuid);
      try {
         PartyPlayer player = api.getPartyPlayer(uuid);
         if (player == null || !player.isInParty()) return List.of(uuid);
         Party party = api.getParty(player.getPartyId());
         if (party == null) return List.of(uuid);
         List<UUID> members = new ArrayList<>();
         for (UUID member : party.getMembers()) {
            members.add(member);
         }
         return members;
      } catch (Throwable t) {
         return List.of(uuid);
      }
   }
}