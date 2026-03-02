package net.ohw.menus;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class SpawnSave implements Listener {

    private final JavaPlugin plugin;

    public SpawnSave(JavaPlugin plugin) {
        this.plugin = plugin;
        
        // 鎖定時間計時器 (每 5 秒強制設回中午)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // 如果你的世界名字不是 world，請改這裡
            if (Bukkit.getWorld("world") != null) {
                Bukkit.getWorld("world").setTime(6000L);
            }
        }, 0L, 100L);
    }

    // --- 1. 防止地圖被破壞 (羊毛碎裂動畫) ---
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE) return;

        Block block = e.getBlock();
        if (block.getType() == Material.WOOL) {
            e.setExpToDrop(0);
            final BlockState state = block.getState();
            // 延遲 2 tick 讓碎裂效果跑出來
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                state.update(true, false);
            }, 2L);
        } else {
            // 禁止破壞原地圖
            e.setCancelled(true);
        }
    }

    // --- 2. 防止亂放方塊 (只允許羊毛) ---
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE) return;
        
        if (e.getBlockPlaced().getType() != Material.WOOL) {
            e.setCancelled(true);
        }
    }

    // --- 3. 禁止下雨 (永遠晴天) ---
    @EventHandler
    public void onWeatherChange(WeatherChangeEvent e) {
        if (e.toWeatherState()) {
            e.setCancelled(true);
            e.getWorld().setStorm(false);
            e.getWorld().setThundering(false);
        }
    }

    // --- 4. 終極農田保護 (防踩踏、防乾涸) ---
    @EventHandler
    public void onSoilTrample(PlayerInteractEvent e) {
        // 物理踩踏動作 (Action.PHYSICAL)
        if (e.getAction() == Action.PHYSICAL) {
            if (e.getClickedBlock() != null && e.getClickedBlock().getType() == Material.SOIL) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityTrample(EntityInteractEvent e) {
        if (e.getBlock().getType() == Material.SOIL) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onCropFade(BlockFadeEvent e) {
        // 防止耕地變回泥土
        if (e.getBlock().getType() == Material.SOIL) {
            e.setCancelled(true);
        }
    }
}