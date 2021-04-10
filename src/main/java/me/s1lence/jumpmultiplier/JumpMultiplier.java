package me.s1lence.jumpmultiplier;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

import static org.bukkit.Material.WATER_BUCKET;

public final class JumpMultiplier extends JavaPlugin implements Listener {
    public double multiplier = 1;
    public Setup setup = new Setup();
    public Timer timer = new Timer();
    boolean displayMessage;
    List<String> jumped_players = new ArrayList<String>();
    class updateMultiplier extends TimerTask {
        public void run() {
            if (multiplier * getConfig().getDouble("Multiplier") > getConfig().getDouble("Limit")) {
                multiplier = getConfig().getDouble("Limit");
                timer.cancel();
                timer.purge();
            } else {
                multiplier *= getConfig().getDouble("Multiplier");
            }
            sendAll("Current Multiplier: " + Math.round(multiplier * 100.0) / 100.0);
        }
    }

    class Jump extends TimerTask {
        private Timer timer;
        private Random random;
        private Player player;

        public Jump(Timer timer, Random random, Player player) {
            this.timer = timer;
            this.random = random;
            this.player = player;
        }

        @Override
        public void run() {
            jumpPlayer(player);
            int lowerBoundTime = (int) (getConfig().getDouble("lowerBoundTime") * 60000);
            int upperBoundTime = (int) (getConfig().getDouble("upperBoundTime") * 60000);
            timer.schedule(new Jump(timer, random, player),
                    (random.nextInt(upperBoundTime - lowerBoundTime + 1) + lowerBoundTime));

        }


    }

    public void jumpPlayer(Player player) {
        double DECELERATION_RATE = 0.98D;
        double GRAVITY_CONSTANT = 0.08D;
        double VANILA_ANTICHEAT_THRESHOLD = 9.5D; // actual 10D
        Vector speed = new Vector(0, multiplier, 0);
        if(!checkIfPlayerInAir(player)) {
            new BukkitRunnable() {
                boolean teleported = false;
                boolean displayed = false;
                double velY = speed.getY();
                Location locCached = new Location(null, 0, 0, 0);

                @Override
                public void run() {
                    if (velY > VANILA_ANTICHEAT_THRESHOLD) {
                        Location loc = player.getEyeLocation().add(0, 1, 0);
                        while (loc.getY() < 256 && !teleported) {
                            if (loc.getBlock().getType() != Material.AIR) {
                                player.getLocation(locCached).setY(loc.getY() - 1);
                                player.teleport(locCached);
                                teleported = true;
                                jumped_players.remove(player.getName());
                                this.cancel();
                                break;
                            }
                            loc.add(0, 1, 0);
                        }
                        if (!teleported) {
                            player.getLocation(locCached).setY(locCached.getY() + velY);
                            player.teleport(locCached);
                            player.setVelocity(new Vector(0, VANILA_ANTICHEAT_THRESHOLD, 0));
                        }
                    } else {
                        player.setVelocity(new Vector(0, velY, 0));
                        jumped_players.remove(player.getName());
                        this.cancel();
                    }

                    velY -= GRAVITY_CONSTANT;
                    velY *= DECELERATION_RATE;
                    if (!displayed && displayMessage) {
                        sendAll(player.getName() + " was YEEEETED");
                        displayed = true;
                    }
                }
            }.runTaskTimer(this, 0, 1);
        }
    }

    public void sendAll(String msg) {
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage("[§4§oJM§r] " + msg));

    }

    public boolean checkIfPlayerInAir(Player p){
        Location loc = p.getLocation();
        for(int i = 1; i<=5; i++){
            loc.subtract(0,1,0);
            if(loc.getBlock().getType() != Material.AIR && loc.getBlock().getType() != Material.VOID_AIR){
                return false;
            }
        }
        return true;
    }

    class Setup {
        public void run() {
            setup.giveItems();
            timer.schedule(new updateMultiplier(), 0, (int) (getConfig().getDouble("Period") * 60000));
            Collection<? extends Player> players = Bukkit.getOnlinePlayers();
            players.forEach(p -> {
                Random random = new Random();
                Timer timer = new Timer();
                int lowerBoundTime = (int) (getConfig().getDouble("lowerBoundTime") * 60000);
                int upperBoundTime = (int) (getConfig().getDouble("upperBoundTime") * 60000);
                timer.schedule(new Jump(timer, random, p), (random.nextInt(upperBoundTime - lowerBoundTime + 1) + lowerBoundTime));
            })
            ;

        }

        public void giveItems() {
            if (getConfig().getBoolean("GiveItems")) {
                ItemStack waterBucketStack = new ItemStack(WATER_BUCKET);
                Collection<? extends Player> players = Bukkit.getOnlinePlayers();
                for (Player p : players) {
                    if (!p.getInventory().contains(WATER_BUCKET)) {
                        p.getInventory().addItem(waterBucketStack);
                    }
                }
            }
        }
    }

    @EventHandler
    public void respawn(PlayerRespawnEvent e) {
        if (getConfig().getBoolean("GiveItems")) {
            ItemStack waterBucketStack = new ItemStack(WATER_BUCKET);
            e.getPlayer().getInventory().addItem(waterBucketStack);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("jmstart")) {
            if (args.length > 0) {
                multiplier = Double.parseDouble(args[0]);
            }
            displayMessage = getConfig().getBoolean("displayMessage");
            setup.run();
        } else if (command.getName().equals("lost")) {
            setup.giveItems();
        } else if (command.getName().equals("jmstop")) {
            multiplier = 1;
            onDisable();
        }
        return false;
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        getConfig().options().copyDefaults();
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        System.out.println("Jump multiplier plugin started");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        System.out.println("Jump multiplier plugin shutdown");
        timer.cancel();
        timer.purge();
    }
}
