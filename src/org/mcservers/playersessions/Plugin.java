package org.mcservers.playersessions;

import java.util.logging.Level;
import java.text.MessageFormat;

import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;


public class Plugin extends JavaPlugin {
    protected Sessions sessions;
    
    @Override
    public void onEnable() {
        sessions = new Sessions(getConfig().getString("api_key"));
        Bukkit.getScheduler().scheduleAsyncRepeatingTask(this, sessions, 20, 20);
        
        // start sessions for online players
        for(Player player : Bukkit.getOnlinePlayers()) {
            sessions.getSession(player, true);
        }
        
        Bukkit.getPluginManager().registerEvents(sessions, this);
        
        final Sessions finalizedSessions = sessions;
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            public void run() {
                finalizedSessions.touchAll();
            }
        }, 20 * 30, 20 * 30);
    }
    
    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        sessions.flush();
    }
    
    
    public void log(String message) {
        log(Level.INFO, message);
    }
    public void log(Level level, String message) {
        getLogger().log(level, message);
    }
    public void log(String message, Object... args) {
        log(MessageFormat.format(message, args));
    }
    public void log(Level level, String message, Object... args) {
        log(level, MessageFormat.format(message, args));
    }
    
    public void debug(String message) {
        log(Level.FINE, message);
    }
    public void debug(String message, Object... args) {
        debug(MessageFormat.format(message, args));
    }
}