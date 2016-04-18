/*
 * Copyright (C) 2014-2016  Gnat008
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.gnat008.perworldinventory.data;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.onarandombox.multiverseinventories.MultiverseInventories;
import com.onarandombox.multiverseinventories.ProfileTypes;
import com.onarandombox.multiverseinventories.api.profile.ProfileType;
import com.onarandombox.multiverseinventories.api.profile.PlayerProfile;
import com.onarandombox.multiverseinventories.api.profile.WorldGroupProfile;
import com.onarandombox.multiverseinventories.api.share.Sharables;
import me.gnat008.perworldinventory.PerWorldInventory;
import me.gnat008.perworldinventory.config.defaults.ConfigValues;
import me.gnat008.perworldinventory.data.serializers.InventorySerializer;
import me.gnat008.perworldinventory.data.serializers.PotionEffectSerializer;
import me.gnat008.perworldinventory.groups.Group;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import uk.co.tggl.pluckerpluck.multiinv.MultiInv;
import uk.co.tggl.pluckerpluck.multiinv.api.MIAPIPlayer;
import uk.co.tggl.pluckerpluck.multiinv.inventory.MIItemStack;

import java.io.File;
import java.util.*;

public class DataConverter {
    private static final ProfileType[] MV_PROFILETYPES = { ProfileTypes.SURVIVAL, ProfileTypes.CREATIVE, ProfileTypes.ADVENTURE };

    private FileSerializer serializer;
    private PerWorldInventory plugin;

    private static DataConverter converter = null;

    private DataConverter(PerWorldInventory plugin) {
        this.plugin = plugin;
        this.serializer = new FileSerializer(plugin);
    }

    public static DataConverter getInstance(PerWorldInventory plugin) {
        if (converter == null) {
            converter = new DataConverter(plugin);
        }

        return converter;
    }

    public static void disable() {
        converter = null;
    }

    public void convertMultiVerseData() {
        plugin.getPrinter().printToConsole("Beginning data conversion. This may take awhile...", false);
        MultiverseInventories mvinventories = (MultiverseInventories) plugin.getServer().getPluginManager().getPlugin("Multiverse-Inventories");
        List<WorldGroupProfile> mvgroups = mvinventories.getGroupManager().getGroups();
        Gson gson = new Gson();

        for (WorldGroupProfile mvgroup : mvgroups) {
            //Ensure that the group exists first, otherwise you just get nulls
            Group pwiGroup = plugin.getGroupManager().getGroup(mvgroup.getName());
            List<String> worlds = new ArrayList<>(mvgroup.getWorlds());

            if (pwiGroup == null)
                plugin.getGroupManager().addGroup(mvgroup.getName(), worlds);
            else
                pwiGroup.addWorlds(worlds);

            for (ProfileType profileType : MV_PROFILETYPES) {
                GameMode gameMode = GameMode.valueOf(profileType.getName());

                for (OfflinePlayer player1 : Bukkit.getOfflinePlayers()) {
                    try {
                        PlayerProfile playerData = mvgroup.getPlayerData(profileType, player1);
                        if (playerData != null) {
                            JsonObject writable = serializeMVIToNewFormat(playerData);

                            File file = serializer.getFile(gameMode, plugin.getGroupManager().getGroup(mvgroup.getName()), player1.getUniqueId());
                            if (!file.getParentFile().exists())
                                file.getParentFile().mkdir();
                            if (!file.exists())
                                file.createNewFile();
                            serializer.writeData(file, gson.toJson(writable));
                        }
                    } catch (Exception ex) {
                        plugin.getPrinter().printToConsole("Error importing inventory for player: " + player1.getName() +
                            " For group: " + mvgroup.getName() + " For gamemode: " + gameMode.name(), true);
                        ex.printStackTrace();
                    }
                }
            }
        }

        plugin.getGroupManager().saveGroupsToDisk();
        plugin.getPrinter().printToConsole("Data conversion complete! Disabling Multiverse-Inventories...", false);
        plugin.getServer().getPluginManager().disablePlugin(mvinventories);
        plugin.getPrinter().printToConsole("Multiverse-Inventories disabled! Don't forget to remove the .jar!", false);
    }

    public void convertMultiInvData() {
        plugin.getPrinter().printToConsole("Beginning data conversion. This may take awhile...", false);
        MultiInv multiinv = (MultiInv) plugin.getServer().getPluginManager().getPlugin("MultiInv");
        /*MultiInvAPI mvAPI = new MultiInvAPI(multiinv);

        for (String world : mvAPI.getGroups().values()) {
            System.out.println("World: " + world);
            for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
                System.out.println("OfflinePlayer: " + offlinePlayer.getName());
                MIAPIPlayer player = mvAPI.getPlayerInstance(offlinePlayer, world, GameMode.SURVIVAL);
                if (player != null && player.getInventory() != null && player.getInventory().getInventoryContents() != null) {
                    System.out.println("MIAPIPlayer: " + player.getPlayername());
                    try {
                        plugin.getSerializer().writePlayerDataToFile(offlinePlayer, serializeMIToNewFormat(player), mvAPI.getGroups().get(world), GameMode.SURVIVAL);
                    } catch (Exception ex) {
                        plugin.getPrinter().printToConsole("Error importing inventory for player '" + offlinePlayer.getName() + ": " + ex.getMessage(), true);
                        ex.printStackTrace();
                    }
                }
            }
        }*/

        plugin.getGroupManager().saveGroupsToDisk();
        plugin.getPrinter().printToConsole("Data conversion complete! Disabling MultiInv...", false);
        plugin.getServer().getPluginManager().disablePlugin(multiinv);
        plugin.getPrinter().printToConsole("MultiInv disabled! Don't forget to remove the .jar!", false);
    }

    private JsonObject serializeMVIToNewFormat(PlayerProfile data) {
        JsonObject root = new JsonObject();
        root.addProperty("data-format", 1);

        JsonObject inv = new JsonObject();
        if (data.get(Sharables.INVENTORY) != null) {
            JsonArray inventory = InventorySerializer.serializeInventory(data.get(Sharables.INVENTORY));
            inv.add("inventory", inventory);
        }
        if (data.get(Sharables.ARMOR) != null) {
            JsonArray armor = InventorySerializer.serializeInventory(data.get(Sharables.ARMOR));
            inv.add("armor", armor);
        }
        if (data.get(Sharables.ENDER_CHEST) != null) {
            JsonArray enderChest = InventorySerializer.serializeInventory(data.get(Sharables.ENDER_CHEST));
            root.add("ender-chest", enderChest);
        }

        JsonObject stats = new JsonObject();
        if (data.get(Sharables.EXHAUSTION) != null)
            stats.addProperty("exhaustion", data.get(Sharables.EXHAUSTION));
        if (data.get(Sharables.EXPERIENCE) != null)
            stats.addProperty("exp", data.get(Sharables.EXPERIENCE));
        if (data.get(Sharables.FOOD_LEVEL) != null)
            stats.addProperty("food", data.get(Sharables.FOOD_LEVEL));
        if (data.get(Sharables.HEALTH) != null)
            stats.addProperty("health", data.get(Sharables.HEALTH));
        if (data.get(Sharables.LEVEL) != null)
            stats.addProperty("level", data.get(Sharables.LEVEL));
        if (data.get(Sharables.POTIONS) != null) {
            PotionEffect[] effects = data.get(Sharables.POTIONS);
            Collection<PotionEffect> potionEffects = new LinkedList<>();
            Collections.addAll(potionEffects, effects);
            stats.addProperty("potion-effects", PotionEffectSerializer.serialize(potionEffects));
        }
        if (data.get(Sharables.SATURATION) != null)
            stats.addProperty("saturation", data.get(Sharables.SATURATION));

        root.add("inventory", inv);
        root.add("stats", stats);

        return root;
    }

    private JsonObject serializeMIToNewFormat(MIAPIPlayer player) {
        JsonObject root = new JsonObject();
        root.addProperty("data-format", 1);

        JsonObject inventory = new JsonObject();

        List<ItemStack> items = new ArrayList<>();
        for (MIItemStack item : player.getInventory().getInventoryContents()) {
            if (item == null || item.getItemStack() == null) {
                items.add(new ItemStack(Material.AIR));
            } else {
                items.add(item.getItemStack());
            }
        }
        ItemStack[] invArray = new ItemStack[items.size()];
        invArray = items.toArray(invArray);
        JsonArray inv = InventorySerializer.serializeInventory(invArray);
        inventory.add("inventory", inv);

        List<ItemStack> armorList = new ArrayList<>();
        for (MIItemStack item : player.getInventory().getArmorContents()) {
            if (item == null || item.getItemStack() == null) {
                items.add(new ItemStack(Material.AIR));
            } else {
                armorList.add(item.getItemStack());
            }
        }
        ItemStack[] armorArray = new ItemStack[armorList.size()];
        armorArray = armorList.toArray(armorArray);
        JsonArray armor = InventorySerializer.serializeInventory(armorArray);
        inventory.add("armor", armor);

        List<ItemStack> enderChestList = new ArrayList<>();
        for (MIItemStack item : player.getEnderchest().getInventoryContents()) {
            if (item == null || item.getItemStack() == null) {
                items.add(new ItemStack(Material.AIR));
            } else {
                enderChestList.add(item.getItemStack());
            }
        }
        ItemStack[] endArray = new ItemStack[enderChestList.size()];
        endArray = enderChestList.toArray(endArray);
        JsonArray enderChest = InventorySerializer.serializeInventory(endArray);
        root.add("ender-chest", enderChest);

        JsonObject stats = new JsonObject();
        stats.addProperty("exp", player.getXp());
        stats.addProperty("food", player.getFoodlevel());
        stats.addProperty("gamemode", player.getGm().toString());
        stats.addProperty("health", player.getHealth());
        stats.addProperty("level", player.getXpLevel());
        stats.addProperty("saturation", player.getSaturation());

        root.add("inventory", inventory);
        root.add("stats", stats);

        return root;
    }
}
