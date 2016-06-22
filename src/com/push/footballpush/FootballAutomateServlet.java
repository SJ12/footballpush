/*
 * Decompiled with CFR 0_114.
 * 
 * Could not load the following classes:
 *  com.google.appengine.api.memcache.MemcacheService
 *  com.google.appengine.api.memcache.MemcacheServiceFactory
 *  javax.servlet.ServletException
 *  javax.servlet.http.HttpServlet
 *  javax.servlet.http.HttpServletRequest
 *  javax.servlet.http.HttpServletResponse
 *  org.json.simple.JSONArray
 *  org.json.simple.JSONObject
 *  org.json.simple.parser.JSONParser
 */
package com.push.footballpush;

import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class FootballAutomateServlet
extends HttpServlet {
    public String gamesUri = "http://ws.365scores.com/Data/Games/?lang=10&uc=80&competitions=5694,595&competitors=9818,9819,2375,2379,5061,5050,2372,5054,2377,2378,5028,114,480,5491,105,106,108,104,110,131,132,134,331,341&startdate=%s&enddate=%s&FullCurrTime=true&uid=%s";
    private static final Logger log = Logger.getLogger(FootballAutomateServlet.class.getName());

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        JSONParser parser = new JSONParser();
        try {
            if (req.getParameter("checkforcace") != null) {
                if (!this.checkForCache(resp)) {
                    resp.getWriter().println("Rewriting cache");
                    FootballAutomateServlet.readUrl("http://footballpush.appspot.com/auto");
                } else {
                    resp.getWriter().println("Cache intact");
                    log.info("Cache intact");
                }
            } else if (req.getParameter("check") != null) {
                if (this.checkTime(resp)) {
                    FootballAutomateServlet.readUrl("http://footballpush.appspot.com/footballpush");
                } else {
                    log.info("No matches");
                }
            } else {
                Calendar cal = Calendar.getInstance();
                SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
                String todayDate = dateFormat.format(cal.getTime());
                String yestDate = "";
                String url = String.format(this.gamesUri, yestDate, todayDate, "-1");
                String jsonString = FootballAutomateServlet.readUrl(url);
                Object obj = parser.parse(jsonString);
                JSONObject jsonObject = (JSONObject)obj;
                jsonObject.get((Object)"LastUpdateID");
                JSONArray gamesArray = (JSONArray)jsonObject.get((Object)"Games");
                boolean first = false;
                if (gamesArray == null) {
                    log.info("nothing");
                    FootballAutomateServlet.writeToCache("starttime", "0");
                    FootballAutomateServlet.writeToCache("endtime", "0");
                } else {
                    String lasttime = null;
                    for (Object game : gamesArray) {
                        JSONObject gamesJson = (JSONObject)game;
                        SimpleDateFormat format = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.ENGLISH);
                        String startTime = (String)gamesJson.get((Object)"STime");
                        Date today = new Date();
                        Date date = format.parse(startTime);
                        Calendar c = Calendar.getInstance();
                        cal.setTime(date);
                        cal.add(12, -5);
                        date = cal.getTime();
                        cal.add(12, 130);
                        Date endTime = cal.getTime();
                        SimpleDateFormat format1 = new SimpleDateFormat("MMM dd EEE, hh:mma");
                        format1.setTimeZone(TimeZone.getTimeZone("IST"));
                        String status = format1.format(date);
                        String end = format1.format(endTime);
                        if (!first) {
                            FootballAutomateServlet.writeToCache("starttime", status);
                            first = true;
                        }
                        if (first) {
                            lasttime = end;
                        }
                        resp.getWriter().println(String.valueOf(status) + " " + end);
                    }
                    if (lasttime != null) {
                        FootballAutomateServlet.writeToCache("endtime", lasttime);
                    }
                }
            }
        }
        catch (Exception cal) {
            // empty catch block
        }
    }

    private boolean checkForCache(HttpServletResponse resp) {
        MemcacheService syncCache = MemcacheServiceFactory.getMemcacheService();
        String startTime = (String)syncCache.get((Object)"starttime");
        String endTime = (String)syncCache.get((Object)"endtime");
        log.info("startime : " + startTime + " endtime : " + endTime);
        if (startTime == null || endTime == null) {
            return false;
        }
        return true;
    }

    private static void writeToCache(String key, String value) {
        MemcacheService syncCache = MemcacheServiceFactory.getMemcacheService();
        log.info("Written " + value + " to cache");
        syncCache.put((Object)key, (Object)value);
    }

    private boolean checkTime(HttpServletResponse resp) {
        boolean result = false;
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd EEE, hh:mma");
            dateFormat.setTimeZone(TimeZone.getTimeZone("IST"));
            Calendar cal = Calendar.getInstance();
            Date dt = new Date();
            String currentDateTimeString = dateFormat.format(dt);
            Date currentDateTime = dateFormat.parse(currentDateTimeString);
            MemcacheService syncCache = MemcacheServiceFactory.getMemcacheService();
            Date startDateTime = dateFormat.parse((String)syncCache.get((Object)"starttime"));
            Date endDateTime = dateFormat.parse((String)syncCache.get((Object)"endtime"));
            resp.getWriter().print(String.valueOf((String)syncCache.get((Object)"starttime")) + " " + (String)syncCache.get((Object)"endtime"));
            resp.getWriter().print(String.valueOf(startDateTime.before(currentDateTime)) + " " + endDateTime.after(currentDateTime) + "<br>");
            result = startDateTime.before(currentDateTime) && endDateTime.after(currentDateTime);
        }
        catch (Exception dateFormat) {
            // empty catch block
        }
        return result;
    }

    private static String readUrl(String urlString) {
        BufferedReader reader = null;
        StringBuffer buffer = new StringBuffer();
        try {
            int read;
            URL url = new URL(urlString);
            reader = new BufferedReader(new InputStreamReader(url.openStream()));
            char[] chars = new char[1024];
            while ((read = reader.read(chars)) != -1) {
                buffer.append(chars, 0, read);
            }
            String string = buffer.toString();
            return string;
        }
        catch (Exception e) {
            String string = buffer.toString();
            return string;
        }
        finally {
            if (reader != null) {
                try {
                    reader.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}