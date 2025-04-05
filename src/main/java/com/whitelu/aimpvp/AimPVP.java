package com.whitelu.aimpvp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;

public class AimPVP extends JavaPlugin implements Listener {
    private static final String PREFIX = "§b[AimPVP] ";
    private static final int DEFAULT_HEALTH = 200;
    private static final int COOLDOWN_SECONDS = 60;
    private static final int WARNING_HEALTH = 20;
    private static final int CRITICAL_HEALTH = 1;
    private static final int ATTACK_SPEED_THRESHOLD = 15;
    private static final int RESISTANCE_LEVEL = 10;
    private static final int REGEN_DELAY_SECONDS = 15;
    private static final int REGEN_RATE_SECONDS = 2;
    private static final int REGEN_AMOUNT = 1;
    private static final int MAX_WARNINGS_PER_SECOND = 4;
    private static final int MAX_WARNING_DURATION = 5;

    private final Map<UUID, Integer> playerHealth = new HashMap<>();
    private final Map<UUID, BossBar> playerBossBars = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Long> lastDamageTimes = new HashMap<>();
    private final Map<UUID, Map<Long, Integer>> attackLog = new HashMap<>();
    private final Map<UUID, Integer> warningDuration = new HashMap<>();
    private final Map<UUID, Long> lastWarningTime = new HashMap<>();
    private Team pvpTeam;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        setupTeam();
        startRegenTask();
    }

    @Override
    public void onDisable() {
        new HashSet<>(playerHealth.keySet()).forEach(uuid -> disablePvpMode(uuid, false));
        if (pvpTeam != null) {
            pvpTeam.unregister();
        }
    }

    private void setupTeam() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        if (scoreboard.getTeam("aimpvp") != null) {
            scoreboard.getTeam("aimpvp").unregister();
        }
        pvpTeam = scoreboard.registerNewTeam("aimpvp");
        pvpTeam.color(NamedTextColor.RED);
        pvpTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
        pvpTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.ALWAYS);
        pvpTeam.setCanSeeFriendlyInvisibles(true);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + "§cThis command can only be used by players!");
            return true;
        }

        if (!command.getName().equalsIgnoreCase("aimpvp")) {
            return false;
        }

        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (cooldowns.containsKey(playerId)) {
            long timeLeft = (cooldowns.get(playerId) + (COOLDOWN_SECONDS * 1000) - currentTime) / 1000;
            if (timeLeft > 0) {
                player.sendMessage(PREFIX + "§c请等待 " + timeLeft + " 秒后再次使用此命令！");
                return true;
            }
        }

        if (playerHealth.containsKey(playerId)) {
            disablePvpMode(playerId, true);
        } else {
            enablePvpMode(playerId);
        }

        cooldowns.put(playerId, currentTime);
        return true;
    }

    private void enablePvpMode(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;

        playerHealth.put(playerId, DEFAULT_HEALTH);
        
        BossBar bossBar = Bukkit.createBossBar("剩余血量", BarColor.RED, BarStyle.SOLID);
        bossBar.addPlayer(player);
        playerBossBars.put(playerId, bossBar);
        updateBossBar(playerId);

        player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, RESISTANCE_LEVEL, false, false));
        
        pvpTeam.addEntry(player.getName());
        player.setGlowing(true);

        Bukkit.broadcast(Component.text(PREFIX).append(
            Component.text(player.getName() + "已启用AimPVP").color(NamedTextColor.GREEN)
        ));
        startActionBarTask(player);
    }

    private void disablePvpMode(UUID playerId, boolean isManual) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.removePotionEffect(PotionEffectType.DAMAGE_RESISTANCE);
            pvpTeam.removeEntry(player.getName());
            player.setGlowing(false);
            player.sendMessage(PREFIX + "§c已退出 AimPVP 模式！");
            
            if (isManual) {
                Bukkit.broadcast(Component.text(PREFIX + player.getName() + "已禁用AimPVP"));
            }
        }

        BossBar bossBar = playerBossBars.remove(playerId);
        if (bossBar != null) {
            bossBar.removeAll();
        }

        playerHealth.remove(playerId);
    }

    private void startActionBarTask(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!playerHealth.containsKey(player.getUniqueId())) {
                    cancel();
                    return;
                }

                int health = playerHealth.get(player.getUniqueId());
                if (health <= WARNING_HEALTH) {
                    player.sendActionBar(Component.text("血量低").color(NamedTextColor.RED));
                } else {
                    player.sendActionBar(Component.text("AimPVP模式中").color(NamedTextColor.AQUA));
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void updateBossBar(UUID playerId) {
        BossBar bossBar = playerBossBars.get(playerId);
        if (bossBar != null) {
            double progress = playerHealth.get(playerId) / (double) DEFAULT_HEALTH;
            bossBar.setProgress(Math.max(0, Math.min(1, progress)));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        disablePvpMode(event.getPlayer().getUniqueId(), false);
    }

    private void startRegenTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                for (UUID playerId : new HashSet<>(playerHealth.keySet())) {
                    Long lastDamage = lastDamageTimes.get(playerId);
                    if (lastDamage == null || currentTime - lastDamage >= REGEN_DELAY_SECONDS * 1000) {
                        Player player = Bukkit.getPlayer(playerId);
                        if (player != null && player.isOnline()) {
                            int health = playerHealth.get(playerId);
                            if (health < DEFAULT_HEALTH) {
                                health = Math.min(DEFAULT_HEALTH, health + REGEN_AMOUNT);
                                playerHealth.put(playerId, health);
                                updateBossBar(playerId);
                                player.sendActionBar(Component.text("恢复中").color(NamedTextColor.GREEN));
                                player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 2, 0), 1, 0, 0, 0, 0);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(this, REGEN_RATE_SECONDS * 20L, REGEN_RATE_SECONDS * 20L);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player victim)) {
            return;
        }

        UUID attackerId = attacker.getUniqueId();
        UUID victimId = victim.getUniqueId();

        if (!playerHealth.containsKey(attackerId) || !playerHealth.containsKey(victimId)) {
            if (playerHealth.containsKey(attackerId) || playerHealth.containsKey(victimId)) {
                event.setCancelled(true);
            }
            return;
        }

        cooldowns.put(victimId, System.currentTimeMillis());
        lastDamageTimes.put(victimId, System.currentTimeMillis());
        lastDamageTimes.put(attackerId, System.currentTimeMillis());

        if (attacker.getInventory().getItemInMainHand().getType().name().endsWith("_HOE")) {
            return;
        }

        int currentHealth = playerHealth.get(victimId);
        if (event.isCritical()) {
            currentHealth -= 2;
        } else {
            currentHealth--;
        }
        playerHealth.put(victimId, currentHealth);
        updateBossBar(victimId);

        long currentTime = System.currentTimeMillis();
        Map<Long, Integer> attacks = attackLog.computeIfAbsent(attackerId, k -> new HashMap<>());
        attacks.put(currentTime, 1);

        int recentAttacks = 0;
        attacks.entrySet().removeIf(entry -> entry.getKey() < currentTime - 1000);
        for (int hits : attacks.values()) {
            recentAttacks += hits;
        }

        if (recentAttacks >= ATTACK_SPEED_THRESHOLD) {
            Long lastWarning = lastWarningTime.get(attackerId);
            if (lastWarning == null || currentTime - lastWarning >= 1000 / MAX_WARNINGS_PER_SECOND) {
                String message = PREFIX + "§c" + attacker.getName() + "攻速异常§e(" + recentAttacks + "次/秒)";
                victim.sendMessage(message);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.hasPermission("aimpvp.highcdnotice")) {
                        player.sendMessage(message);
                    }
                }
                lastWarningTime.put(attackerId, currentTime);

                int duration = warningDuration.getOrDefault(attackerId, 0) + 1;
                warningDuration.put(attackerId, duration);

                if (duration >= MAX_WARNING_DURATION) {
                    Bukkit.getScheduler().runTask(this, () -> {
                        attacker.kick(Component.text("攻击速率异常，多次触发此提醒可能会导致你被封禁").color(NamedTextColor.RED));
                    });
                    warningDuration.remove(attackerId);
                }
            }
        } else {
            warningDuration.remove(attackerId);
        }

        if (currentHealth <= CRITICAL_HEALTH) {
            victim.removePotionEffect(PotionEffectType.DAMAGE_RESISTANCE);
            victim.setHealth(1);
        } else {
            event.setDamage(0);
            victim.playHurtAnimation(0.0f);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        UUID playerId = event.getEntity().getUniqueId();
        disablePvpMode(playerId, false);
        cooldowns.remove(playerId);
    }
} 