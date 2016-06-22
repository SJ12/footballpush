package com.push.footballpush;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class DbOperations
extends HttpServlet {
    String appkey = null; 
    String sub_link_add = null;
    String db_name = null;
    String comments_name = null; 
    private static final Logger log = Logger.getLogger(DbOperations.class.getName());

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (req.getParameter("football") != null) {
            this.appkey = "fa0d179a-9e40-4d3f-a0f6-cba551dfd3a8";
            this.sub_link_add = "?football=1";
            this.db_name = "Football_shouts";
            this.comments_name = "Football_comments";
        } else if (req.getParameter("cricket") != null) {
            this.appkey = "132cadde-50e7-470f-ae8e-c7d9216511da";
            this.sub_link_add = "?cricket=1";
            this.db_name = "Cricket_shouts";
            this.comments_name = "Cricket_comments";
        }
        resp.setContentType("text/html; charset=UTF-8");
        resp.getWriter().println(String.format("<!DOCTYPE html><html><head><meta name='txtweb-appkey' content='%s'> </head><body>", this.appkey));
        if (req.getParameter("delete") != null) {
            this.deleteRecord(req);
        } else if (req.getParameter("fetch") != null) {
            this.getShouts(resp);
        } else if (req.getParameter("shout") != null) {
            String name = req.getParameter("name");
            if (name != null) {
                String status = this.makeShout(req.getParameter("shout"), name);
                if (status.startsWith("ERROR")) {
                    resp.getWriter().println(status);
                }
                this.insertCommentLink(resp, name);
            } else {
                resp.getWriter().println("Error occurred. Please try after some time<br><br>");
            }
            try {
                Thread.sleep(1000);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
            this.getShouts(resp);
        } else if (req.getParameter("signup") != null) {
            String signUp = this.signUp(req.getParameter("signup"), req.getParameter("txtweb-mobile"));
            if (signUp.startsWith("ERROR")) {
                resp.getWriter().println(signUp);
                this.insertSignupLink(resp);
            } else {
                this.insertCommentLink(resp, signUp);
            }
            this.getShouts(resp);
        } else {
            String name = this.checkUser(req.getParameter("txtweb-mobile"));
            if (name != null) {
                this.insertCommentLink(resp, name);
            } else {
                this.insertSignupLink(resp);
            }
            this.getShouts(resp);
        }
    }

    void deleteRecord(HttpServletRequest req) throws IOException {
        long ONE_HOUR_IN_MILLIS = 3600000;
        System.out.println("Here");
        long t = new Date().getTime();
        Date BeforeOneHour = new Date(t - (long)Integer.parseInt(req.getParameter("delete")) * 3600000);
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Query q = new Query(this.db_name);
        q.setFilter((Query.Filter)Query.FilterOperator.LESS_THAN.of("Time", (Object)BeforeOneHour));
        PreparedQuery pq = datastore.prepare(q);
        for (Entity result : pq.asIterable()) {
            Key key = result.getKey();
            System.out.println("Deleting " + result.getProperty("message"));
            datastore.delete(new Key[]{key});
        }
    }

    private void insertSignupLink(HttpServletResponse resp) throws IOException {
        String sub_link = String.format("<form action='http://footballpush.appspot.com/shouts%s' method='get' class='txtweb-form'>", this.sub_link_add);
        sub_link = String.valueOf(sub_link) + "Name";
        sub_link = String.valueOf(sub_link) + "<input type='text' name='signup'>";
        sub_link = String.valueOf(sub_link) + "</form>";
        sub_link = String.valueOf(sub_link) + "to signup<br><br>";
        resp.getWriter().println(sub_link);
    }

    private void insertCommentLink(HttpServletResponse resp, String name) throws IOException {
        String name_add = "&name=" + name;
        String sub_link = String.format("<form action='http://footballpush.appspot.com/shouts%s%s' method='get' class='txtweb-form'>", this.sub_link_add, name_add);
        sub_link = String.valueOf(sub_link) + "message";
        sub_link = String.valueOf(sub_link) + "<input type='text' name='shout'>";
        sub_link = String.valueOf(sub_link) + String.format("<input type='hidden' name='name' value=%s>", name);
        sub_link = String.valueOf(sub_link) + "</form>";
        sub_link = String.valueOf(sub_link) + "to share your comment<br><br>";
        resp.getWriter().println(sub_link);
    }

    private String checkUser(String hash) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Query q = new Query("Users");
        q.setFilter((Query.Filter)Query.FilterOperator.EQUAL.of("hash", (Object)hash));
        PreparedQuery pq = datastore.prepare(q);
        Iterator iterator = pq.asIterable().iterator();
        if (iterator.hasNext()) {
            Entity result = (Entity)iterator.next();
            return (String)result.getProperty("name");
        }
        return null;
    }

    private String makeShout(String message, String name) {
        if (message.length() < 10) {
            return "ERROR: comment must be of more than 10 chars in length<br><br>";
        }
        String comments = "";
        Date date = new Date();
        String DATE_FORMAT_NOW = "yyyy-MM-dd HH:mm:ss";
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT_NOW);
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        MemcacheService syncCache = MemcacheServiceFactory.getMemcacheService();
        String dateString = sdf.format(date);
        Entity lastEntry = new Entity(this.db_name);
        lastEntry.setProperty("Name", (Object)name);
        lastEntry.setProperty("Time", (Object)date);
        lastEntry.setProperty("message", (Object)message);
        datastore.put(lastEntry);
        comments = syncCache.get((Object)this.comments_name) != null ? (String)syncCache.get((Object)this.comments_name) : this.getShoutsfromDb();
        comments = String.valueOf(name.replace("<", "").replace(">", "")) + ":<br>" + message + "<br>time:" + dateString + "<br>~<br>" + comments;
        syncCache.put((Object)this.comments_name, (Object)comments);
        return "SUCCESS";
    }

    private String getName(String hash) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Query q = new Query("Users");
        q.setFilter((Query.Filter)Query.FilterOperator.EQUAL.of("hash", (Object)hash));
        PreparedQuery pq = datastore.prepare(q);
        Iterator iterator = pq.asIterable().iterator();
        if (iterator.hasNext()) {
            Entity result = (Entity)iterator.next();
            return (String)result.getProperty("name");
        }
        return null;
    }

    public String signUp(String username, String hash) {
        if ((username = username.trim().replace("<", "").replace(">", "")).length() < 3 || username.length() > 10) {
            return "ERROR: Name should of length 3-10 chars<br><br>";
        }
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Entity user = new Entity("Users");
        user.setProperty("hash", (Object)hash);
        user.setProperty("name", (Object)username);
        datastore.put(user);
        return username;
    }

    private void getShouts(HttpServletResponse resp) {
        try {
            String[] commentsArray;
            resp.getWriter().println("RECENT COMMENTS:<br><br>");
            String comments = "";
            MemcacheService syncCache = MemcacheServiceFactory.getMemcacheService();
            Date date = new Date();
            String DATE_FORMAT_NOW = "yyyy-MM-dd HH:mm:ss";
            SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT_NOW);
            comments = syncCache.get((Object)this.comments_name) != null ? (String)syncCache.get((Object)this.comments_name) : this.getShoutsfromDb();
            String[] arrstring = commentsArray = comments.split("<br>~<br>");
            int n = arrstring.length;
            int n2 = 0;
            while (n2 < n) {
                String x = arrstring[n2];
                String[] times = x.split("time:");
                Date date2 = sdf.parse(times[1]);
                long duration = date.getTime() - date2.getTime();
                long diffInSeconds = TimeUnit.MILLISECONDS.toSeconds(duration);
                long diffInMinutes = TimeUnit.MILLISECONDS.toMinutes(duration);
                long diffInHours = TimeUnit.MILLISECONDS.toHours(duration);
                long diffInDays = TimeUnit.MILLISECONDS.toDays(duration);
                String time = diffInDays > 0 ? String.valueOf(diffInDays) + "d ago" : (diffInHours > 0 ? String.valueOf(diffInHours) + "h ago" : (diffInMinutes > 0 ? String.valueOf(diffInMinutes) + "m ago" : String.valueOf(diffInSeconds) + "s ago"));
                resp.getWriter().println(String.valueOf(times[0]) + time + "<br>-<br>");
                ++n2;
            }
        }
        catch (Exception e) {
            log.log(Level.SEVERE, e.getMessage(), e);
            e.printStackTrace();
        }
    }

    private String getShoutsfromDb() {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Query q = new Query(this.db_name).addSort("Time", Query.SortDirection.DESCENDING);
        SimpleDateFormat format1 = new SimpleDateFormat("MMM dd EEE, hh:mma");
        format1.setTimeZone(TimeZone.getTimeZone("IST"));
        PreparedQuery pq = datastore.prepare(q);
        String comments = "";
        Date date = new Date();
        String DATE_FORMAT_NOW = "yyyy-MM-dd HH:mm:ss";
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT_NOW);
        for (Entity result : pq.asIterable()) {
            long duration = date.getTime() - ((Date)result.getProperty("Time")).getTime();
            long diffInSeconds = TimeUnit.MILLISECONDS.toSeconds(duration);
            long diffInMinutes = TimeUnit.MILLISECONDS.toMinutes(duration);
            long diffInHours = TimeUnit.MILLISECONDS.toHours(duration);
            String time = diffInHours > 0 ? String.valueOf(diffInHours) + "h ago" : (diffInMinutes > 0 ? String.valueOf(diffInMinutes) + "m ago" : String.valueOf(diffInSeconds) + "s ago");
            String timeString = sdf.format(((Date)result.getProperty("Time")).getTime());
            String message = (String)result.getProperty("message");
            String name = (String)result.getProperty("Name");
            comments = String.valueOf(comments) + name.replace("<", "").replace(">", "") + ":<br>" + message + "<br>" + "time:" + timeString + "<br>~<br>";
        }
        MemcacheService syncCache = MemcacheServiceFactory.getMemcacheService();
        syncCache.put((Object)this.comments_name, (Object)comments);
        return comments;
    }
}