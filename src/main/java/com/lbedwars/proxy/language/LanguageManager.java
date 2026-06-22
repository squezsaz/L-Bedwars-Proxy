package com.lbedwars.proxy.language;

import com.lbedwars.proxy.LBedWarsProxy;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public class LanguageManager {
   private final Map<String, String> messages;
   private final String lang;

   public LanguageManager(LBedWarsProxy plugin) {
      this.messages = new HashMap<>();
      this.lang = plugin.getConfig().getString("language", "en");

      File file = new File(plugin.getDataFolder(), "messages_" + this.lang + ".yml");
      if (!file.exists()) {
         plugin.saveResource("messages_" + this.lang + ".yml", false);
      }

      YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
      this.flatten("", config);
   }

   private void flatten(String prefix, ConfigurationSection section) {
      Set<String> keys = section.getKeys(false);
      for (String key : keys) {
         String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
         if (section.isConfigurationSection(key)) {
            this.flatten(fullKey, section.getConfigurationSection(key));
         } else {
            this.messages.put(fullKey, section.getString(key, "&cMissing message: " + fullKey));
         }
      }
   }

   public String get(String key) {
      String msg = this.messages.get(key);
      if (msg == null) {
         return ChatColor.translateAlternateColorCodes('&', "&cMissing message: " + key);
      }
      return ChatColor.translateAlternateColorCodes('&', msg);
   }

    public boolean contains(String key) {
       return this.messages.containsKey(key);
    }

    public String get(String key, String... replacements) {
      String msg = this.messages.get(key);
      if (msg == null) {
         return ChatColor.translateAlternateColorCodes('&', "&cMissing message: " + key);
      }
      for (int i = 0; i + 1 < replacements.length; i += 2) {
         msg = msg.replace("{" + replacements[i] + "}", replacements[i + 1]);
      }
      return ChatColor.translateAlternateColorCodes('&', msg);
   }
}
