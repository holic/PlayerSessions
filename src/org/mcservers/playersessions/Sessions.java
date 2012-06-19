package org.mcservers.playersessions;

import java.util.logging.Logger;

import java.net.URL;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.InputStreamReader;
import java.io.BufferedReader;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;

import org.apache.commons.lang.StringUtils;
import org.json.simple.JSONArray;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;


public class Sessions implements Listener, Runnable {
    protected static final String endpoint = "http://api.mcservers.org/track/sessions";
    protected static final Logger logger;
    
    static {
        logger = Logger.getLogger(Sessions.class.getCanonicalName());
        logger.setLevel(java.util.logging.Level.ALL);
    }
    
    protected final String apiKey;
    protected final URL url;
    
    protected final Map<OfflinePlayer, String> hostnames = new HashMap<OfflinePlayer, String>();
    protected final Map<OfflinePlayer, Session> sessions = new HashMap<OfflinePlayer, Session>();
    protected final Set<Session> updated = new HashSet<Session>();
    
    public Sessions(String apiKey) {
        if(apiKey == null) throw new IllegalArgumentException("API key is required");
        this.apiKey = apiKey;
        
        try {
            this.url = new URL(endpoint);
        }
        catch(MalformedURLException e) {
            // this should never happen
            throw new IllegalArgumentException(e.getMessage());
        }
    }
    
    public Session getSession(OfflinePlayer player) {
        return sessions.get(player);
    }
    public Session getSession(OfflinePlayer player, boolean startIfNull) {
        Session session = getSession(player);
        return startIfNull && session == null
                ? startSession(hostnames.get(player), player)
                : session;
    }
    public Session setSession(OfflinePlayer player, Session session) {
        return session == null
                ? sessions.remove(player)
                : sessions.put(player, session);
    }
    
    public Session startSession(String hostname, OfflinePlayer player) {
        Session session = new Session(hostname, player);
        setSession(player, session);
        return touch(session);
    }
    public Session endSession(OfflinePlayer player) {
        Session session = setSession(player, null);
        session.end();
        return touch(session);
    }
    
    
    public Session touch(Session session) {
        if(session != null) updated.add(session);
        return session;
    }
    public void touchAll() {
        updated.addAll(sessions.values());
    }
    
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void playerLogin(PlayerLoginEvent event) {
        hostnames.put(event.getPlayer(), event.getHostname());
        if(event.getResult() != PlayerLoginEvent.Result.ALLOWED) return;
        touch(startSession(event.getHostname(), event.getPlayer()));
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void playerJoin(PlayerJoinEvent event) {
        touch(getSession(event.getPlayer()));
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void playerQuit(PlayerQuitEvent event) {
        touch(endSession(event.getPlayer()));
    }
    
    public void flush() {
        synchronized(updated) {
            int remaining = 0;
            int failures = 0;
            while(!updated.isEmpty()) {
                remaining = updated.size();
                run();
                if(updated.size() == remaining && ++failures > 10) {
                    logger.warning(String.format("Failed to send remaining sessions onDisable:\n\n%s\n", JSONArray.toJSONString(new ArrayList(updated))));
                    break;
                }
            }
        }
    }
    
    public void run() {
        List<Session> batch;
        synchronized(updated) {
            if(updated.isEmpty()) {
                return;
            }
            else {
                batch = new ArrayList(updated).subList(0, Math.min(50, updated.size()));
                updated.removeAll(batch);
            }
        }

        long start = System.currentTimeMillis();
        String json = JSONArray.toJSONString(batch);
        logger.finer(String.format("\nJSON:\n%s\n", json));

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-API-Key", apiKey);

            conn.setDoOutput(true);
            conn.setDoInput(true);

            OutputStreamWriter or = new OutputStreamWriter(conn.getOutputStream());
            or.write(json);
            or.flush();
            or.close();
            
            List<String> header = new ArrayList<String>();
            int i = 0;
            while(conn.getHeaderField(i) != null) {
                header.add(conn.getHeaderFieldKey(i) == null
                        ? conn.getHeaderField(i)
                        : conn.getHeaderFieldKey(i) + ": " + conn.getHeaderField(i));
                i++;
            }
            
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getResponseCode() >= 400
                    ? conn.getErrorStream()
                    : conn.getInputStream()));
            
            List<String> body = new ArrayList<String>();
            String line;
            while((line = br.readLine()) != null) {
                body.add(line);
            }
            br.close();

            int responseCode = conn.getResponseCode();
            String response = StringUtils.join(header, "\n") + "\n\n" + StringUtils.join(body, "\n");

            logger.fine(String.format("Got response (%d)\n%s", responseCode, response));
            
            if(responseCode == 200) {
                logger.fine(String.format("Successfully sent %d sessions", batch.size()));
            }
            else {
                logger.warning(String.format("Bad response code from API (%s), requeueing %d sessions...", conn.getResponseMessage(), batch.size()));
                // requeue events
                synchronized(updated) {
                    updated.addAll(batch);
                }
            }
        }
        catch(IOException e) {
            logger.warning(String.format("Connection or stream failure, requeueing %d sessions...", batch.size()));
            e.printStackTrace();

            // requeue events
            synchronized(updated) {
                updated.addAll(batch);
            }
        }
        finally {
            if(conn != null) {
                conn.disconnect();
            }
        }

        logger.fine(String.format("Response took %d ms", System.currentTimeMillis() - start));
    }
}
