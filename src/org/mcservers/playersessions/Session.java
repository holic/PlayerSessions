package org.mcservers.playersessions;

import java.util.UUID;
import java.util.Date;
import java.net.InetSocketAddress;

import org.json.simple.JSONAware;
import org.json.simple.JSONObject;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class Session implements JSONAware {
    protected String hostname;
    protected OfflinePlayer player;
    protected String id;
    protected long start;
    protected long end;
    
    public Session(String hostname, OfflinePlayer player) {
        this(hostname, player, UUID.randomUUID().toString());
    }
    public Session(String hostname, OfflinePlayer player, String id) {
        this(hostname, player, id, System.currentTimeMillis());
    }
    public Session(String hostname, OfflinePlayer player, String id, long start) {
        this.hostname = hostname;
        this.player = player;
        this.id = id;
        this.start = start;
    }
    
    public OfflinePlayer getPlayer() {
        return player;
    }
    public String getId() {
        return id;
    }
    public long elapsed() {
        return getEnd() - start;
    }
    
    public boolean isOpen() {
        return end == 0;
    }
    public long getStart() {
        return start;
    }
    public long getEnd() {
        return isOpen() ? System.currentTimeMillis() : end;
    }
    public long end() {
        return end = getEnd();
    }
    
    
    public String toJSONString() {
        JSONObject json = new JSONObject();
        
        json.put("hostname", hostname);
        json.put("player", player.getName());
        json.put("uid", id);
        json.put("start", start / 1000L);
        json.put("end", getEnd() / 1000L);
        json.put("has_played_before", player.hasPlayedBefore());
        
        Player onlinePlayer = player.getPlayer();
        if(onlinePlayer != null) {
            InetSocketAddress address = onlinePlayer.getAddress();
            if(address != null) {
                json.put("ip", address.getAddress().getHostAddress());
                json.put("port", address.getPort());
            }
        }
        
        return json.toString();
    }
    
}
