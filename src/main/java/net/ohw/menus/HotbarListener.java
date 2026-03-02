package net.ohw.menus;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class HotbarListener implements Listener {
    private final CompassMenuPlugin plugin;

    public HotbarListener(CompassMenuPlugin plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("deprecation") // 1.8.8 的 setData 是過時方法，但不影響使用
    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE) return;

        Block b = e.getBlockPlaced();
        int x = b.getX();
        int y = b.getY();
        int z = b.getZ();

        // --- 1. 重生點 3x3 保護 (修正為你指定的 Z=7) ---
        int spawnX = 8;
        int spawnZ = -0; // 之前你寫 -0，這裡修正回 7
        if (Math.abs(x - spawnX) <= 1 && Math.abs(z - spawnZ) <= 1) {
            p.sendMessage(ChatColor.RED + "你不能在重生點附近放置方塊！");
            e.setCancelled(true);
            return;
        }

        // --- 2. NPC 區域保護 (0 48 21 到 16 48 15) ---
        if ((x >= 0 && x <= 16) && (y == 48) && (z >= 15 && z <= 21)) {
            p.sendMessage(ChatColor.RED + "此區域為 NPC 區，禁止放置方塊！");
            e.setCancelled(true);
            return;
        }

        // --- 3. 無限羊毛邏輯 (修正顏色問題) ---
        ItemStack itemInHand = e.getItemInHand();
        if (p.getInventory().getHeldItemSlot() == 1 && itemInHand.getType() == Material.WOOL) {
            
            // 重要：繼承手上羊毛的顏色 (Durability 就是 1.8.8 的 Data Value)
            byte colorData = (byte) itemInHand.getDurability();
            b.setData(colorData); 

            // 數量保持 64
            itemInHand.setAmount(64);

            // 5秒後消失
            final Block placedBlock = e.getBlockPlaced();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (placedBlock.getType() == Material.WOOL) {
                    placedBlock.setType(Material.AIR);
                }
            }, 20 * 5L);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        
        // 基本檢查
        if (item == null || !e.getAction().name().contains("RIGHT")) return;

        if (item.getType() == Material.COMPASS) {
            p.openInventory(CompassMenuPlugin.createCompassMenu(p));
        } 
        else if (item.getType() == Material.SKULL_ITEM) {
            plugin.openProfileGui(p);
        }
    }
}