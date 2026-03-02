package net.ohw.menus;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;

public class CompassMenuPlugin extends JavaPlugin implements Listener, PluginMessageListener {

    private static CompassMenuPlugin instance;
    private DatabaseManager db;

    public static final String MENU_TITLE = ChatColor.DARK_AQUA + "伺服器選單";
    public static final String PROFILE_TITLE = ChatColor.DARK_GRAY + "玩家資訊";
    public static final String[] SERVERS = {"pvp", "shop", "bridge", "smp-1"};
    
    public static Map<String, Integer> serverCount = new HashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(new InventoryListener(), this);
        Bukkit.getPluginManager().registerEvents(new HotbarListener(this), this);
        this.getCommand("menu").setExecutor(new MenuCommand(this));
        
        Bukkit.getPluginManager().registerEvents(new SpawnSave(this), this);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(new InventoryListener(), this);
        Bukkit.getPluginManager().registerEvents(new HotbarListener(this), this);
        Bukkit.getPluginManager().registerEvents(new SpawnSave(this), this);

        this.getCommand("menu").setExecutor(new MenuCommand(this));
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        this.getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", this);

        db = new DatabaseManager("172.27.160.60", 3306, "s1_ohw", "u1_aiMuI1XC2r", "YLl4O6a66=Os7l=yz3AKgSiA");
        try {
            db.connect();
            getLogger().info("MySQL 連線成功！");
        } catch (SQLException e) {
            getLogger().severe("無法連線至 MySQL！原因: " + e.getMessage());
        }
        
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) return;
            for (String server : SERVERS) updateServerCount(server);
        }, 20L, 100L);
    }

    public void openProfileGui(Player p) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            int level = 1, coins = 0;
            try (PreparedStatement ps = db.getConnection().prepareStatement("SELECT level, coins FROM player_stats WHERE uuid = ?")) {
                ps.setString(1, p.getUniqueId().toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    level = rs.getInt("level");
                    coins = rs.getInt("coins");
                } else {
                    try (PreparedStatement insert = db.getConnection().prepareStatement("INSERT INTO player_stats (uuid, level, coins) VALUES (?, 1, 0)")) {
                        insert.setString(1, p.getUniqueId().toString());
                        insert.executeUpdate();
                    }
                }
            } catch (SQLException e) { e.printStackTrace(); }

            final int fLevel = level;
            final int fCoins = coins;
            Bukkit.getScheduler().runTask(this, () -> p.openInventory(createProfileMenu(p, fLevel, fCoins)));
        });
    }

    public static Inventory createProfileMenu(Player p, int level, int coins) {
        Inventory inv = Bukkit.createInventory(null, 27, PROFILE_TITLE);
        ItemStack filler = new ItemStack(Material.STAINED_GLASS_PANE, 1, (short) 15);
        ItemMeta fMeta = filler.getItemMeta(); fMeta.setDisplayName(" "); filler.setItemMeta(fMeta);
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        ItemStack nickItem = new ItemStack(Material.NAME_TAG);
        ItemMeta nickMeta = nickItem.getItemMeta();
        nickMeta.setDisplayName(ChatColor.GOLD + "更改暱稱");
        nickItem.setItemMeta(nickMeta);
        inv.setItem(11, nickItem);

        ItemStack head = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwner(p.getName());
        meta.setDisplayName(ChatColor.YELLOW + "你的個人檔案");
        
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "等級: " + ChatColor.GOLD + level);
        lore.add(ChatColor.GRAY + "金幣: " + ChatColor.GOLD + coins);
        
        String rank = ChatColor.WHITE + "Player";
        try {
            LuckPerms api = Bukkit.getServicesManager().getRegistration(LuckPerms.class).getProvider();
            User user = api.getUserManager().getUser(p.getUniqueId());
            if (user != null) {
                String prefix = user.getCachedData().getMetaData().getPrefix();
                if (prefix != null) rank = ChatColor.translateAlternateColorCodes('&', prefix);
            }
        } catch (Exception ignored) {}
        
        lore.add(ChatColor.GRAY + "目前 Rank: " + rank);
        meta.setLore(lore); head.setItemMeta(meta); inv.setItem(13, head);
        return inv;
    }

    // --- 大廳環境與事件處理 ---

    // 修改後的挖掘邏輯
@EventHandler(priority = EventPriority.HIGHEST)
public void onBlockBreak(BlockBreakEvent e) {
    Player p = e.getPlayer();
    if (p.getGameMode() == org.bukkit.GameMode.CREATIVE) return;

    // 如果是羊毛，允許挖掘動畫，但「取消掉落」並「瞬間還原」
    if (e.getBlock().getType() == Material.WOOL) {
        e.setExpToDrop(0);
        
        // 關鍵：不要 Cancel，而是紀錄方塊資料
        final org.bukkit.block.BlockState state = e.getBlock().getState();
        final Material type = state.getType();
        final short data = state.getRawData();

        // 在下一 tick 立刻把方塊變回去，這樣玩家會看到方塊碎掉的動畫
        Bukkit.getScheduler().runTaskLater(this, () -> {
            e.getBlock().setType(type);
            e.getBlock().setData((byte) data);
        }, 1L);
    } else {
        // 其他方塊直接禁止，這會顯示「敲不動」的動畫
        e.setCancelled(true);
    }
}

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        p.teleport(new org.bukkit.Location(p.getWorld(), 8.5, 48, -0.5, 0, 0));
        p.setGameMode(org.bukkit.GameMode.SURVIVAL);
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);

        // Slot 0: 羅盤
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta cMeta = compass.getItemMeta();
        cMeta.setDisplayName(ChatColor.YELLOW + "伺服器傳送門 " + ChatColor.GRAY + "(右鍵)");
        compass.setItemMeta(cMeta);
        p.getInventory().setItem(0, compass);

        // Slot 1: 羊毛
        ItemStack wool = new ItemStack(Material.WOOL, 64);
        ItemMeta woolMeta = wool.getItemMeta();
        woolMeta.setDisplayName(ChatColor.YELLOW + "無限羊毛");
        wool.setItemMeta(woolMeta);
        p.getInventory().setItem(1, wool);

        // Slot 7: 頭顱
        ItemStack head = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta headMeta = (SkullMeta) head.getItemMeta();
        headMeta.setOwner(p.getName());
        headMeta.setDisplayName(ChatColor.YELLOW + "個人資訊 " + ChatColor.GRAY + "(右鍵)");
        head.setItemMeta(headMeta);
        p.getInventory().setItem(7, head);
        
        p.setHealth(20.0);
        p.setFoodLevel(20);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player) e.setCancelled(true);
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent e) {
        e.setCancelled(true); // 物品會彈回背包
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent e) {
        e.setCancelled(true); // 地上不准有掉落物
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked().getGameMode() != org.bukkit.GameMode.CREATIVE) e.setCancelled(true);
    }

    // --- BungeeCord 通訊 ---
    private void updateServerCount(String server) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("PlayerCount");
        out.writeUTF(server);
        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            Player player = Bukkit.getOnlinePlayers().iterator().next();
            player.sendPluginMessage(this, "BungeeCord", out.toByteArray());
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("BungeeCord")) return;
        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        if (in.readUTF().equals("PlayerCount")) {
            serverCount.put(in.readUTF(), in.readInt());
        }
    }

    public static Inventory createCompassMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, MENU_TITLE);
        for (int i = 0; i < 54; i++) inv.setItem(i, createGrayGlass());
        inv.setItem(20, createServerItem("pvp", Material.WOOD_SWORD, "點擊進入激戰區"));
        inv.setItem(21, createServerItem("shop", Material.DIAMOND, "購買生存時各種道具物品"));
        inv.setItem(23, createServerItem("bridge", Material.SANDSTONE, "經典 Bridge 遊戲"));
        inv.setItem(24, createServerItem("smp-1", Material.GRASS, "SMP 1"));
        return inv;
    }

    private static ItemStack createServerItem(String id, Material m, String l) {
        int c = serverCount.getOrDefault(id, 0);
        ItemStack i = new ItemStack(m, (c <= 0) ? 1 : Math.min(c, 64));
        ItemMeta mt = i.getItemMeta();
        mt.setDisplayName(ChatColor.GOLD + id.toUpperCase());
        List<String> lo = new ArrayList<>();
        lo.add(ChatColor.GRAY + l); lo.add(ChatColor.AQUA + "人數: " + ChatColor.WHITE + c);
        mt.setLore(lo); i.setItemMeta(mt);
        return i;
    }

    private static ItemStack createGrayGlass() {
        ItemStack g = new ItemStack(Material.STAINED_GLASS_PANE, 1, (short) 7);
        ItemMeta m = g.getItemMeta(); m.setDisplayName(" "); g.setItemMeta(m);
        return g;
    }

    public static CompassMenuPlugin getInstance() { return instance; }
    @Override
    public void onDisable() {}
}