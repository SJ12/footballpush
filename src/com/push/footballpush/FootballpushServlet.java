package com.push.footballpush;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.URL;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.push.footballpush.twilio_sms;

public class FootballpushServlet
extends HttpServlet {
    private static String[][] games;
    private static String[][] competitions;
    private static final Logger log;
    public String gamesUri = "http://ws.365scores.com/Data/Games/?lang=10&uc=80&competitions=5694,595&competitors=2375,2379,5061,5050,2372,5054,2377,2378,5028,114,480,5491,105,106,108,104,110,131,132,134,331,341&startdate=%s&enddate=%s&FullCurrTime=true&uid=%s";
    public String matchUri = "http://ws.365scores.com/Data/Games/GameCenter/?games=%s";
    public String resultsurl = "";
    public static int[] favTeams;

    static {
        log = Logger.getLogger(FootballpushServlet.class.getName());
        favTeams = new int[]{2375, 2379, 5061, 5050, 2372, 5054, 2377, 2378, 5028, 114, 480, 5491, 105, 106, 108, 104, 110, 131, 132, 134, 331, 341};
    }

    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html; charset=UTF-8");
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
        Calendar cal = Calendar.getInstance();
        if (req.getParameter("results") != null) {
            cal.add(5, -1);
        }
        String yestDate = dateFormat.format(cal.getTime());
        this.resultsurl = String.format(this.gamesUri, yestDate, "", "-1");
        if (req.getParameter("results") != null) {
            this.getResults("all", "");
        } else {
            this.getNotifications();
        }
    }

    private void getResults(String gameID, String type) {
        try {
            String url = String.format(this.matchUri, String.valueOf(gameID));
            games = gameID.equalsIgnoreCase("all") ? FootballpushServlet.getGames(this.resultsurl) : FootballpushServlet.getGame(url);
            String message = "";
            String[][] arrstring = games;
            int n = arrstring.length;
            int n2 = 0;
            while (n2 < n) {
                String[] game = arrstring[n2];
                message = String.valueOf(message) + game[3] + ":\n" + game[1] + " " + game[4] + " - " + game[5] + " " + game[2] + "\n" + game[6];
                if (gameID.equalsIgnoreCase("all")) {
                    this.sendMessage(message);
                } else if (gameID.equalsIgnoreCase(game[0])) {
                    String smsMessage = message;
                    if (game[11].equalsIgnoreCase("1")) {
                        smsMessage = String.valueOf(smsMessage) + "\n\n" + game[8];
                    }
                    this.sendMessage(String.valueOf(type) + "\n\n" + smsMessage);
                    this.postComment(String.valueOf(type) + "\n\n" + message);
                    break;
                }
                message = "";
                ++n2;
            }
        }
        catch (Exception url) {
            // empty catch block
        }
    }

    private void getNotifications() {
        log.info("inside getNotifications");
        String uid = null;
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Query q = new Query("LastEntry");
        PreparedQuery pq = datastore.prepare(q);
        for (Entity result : pq.asIterable()) {
            uid = (String)result.getProperty("uid");
        }
        String url = String.format(this.gamesUri, "", "", uid);
        try {
            String jsonString = FootballpushServlet.readUrl(url);
            JSONParser parser = new JSONParser();
            Object obj = parser.parse(jsonString);
            JSONObject jsonObject = (JSONObject)obj;
            Number updateID = (Number)jsonObject.get((Object)"LastUpdateID");
            log.info("updateID:" + updateID);
            Entity lastEntry = new Entity("LastEntry", 117882041);
            lastEntry.setProperty("uid", (Object)updateID.toString());
            datastore.put(lastEntry);
            JSONArray notsArray = (JSONArray)jsonObject.get((Object)"Notifications");
            log.info("Nots: " + (Object)notsArray + " " + notsArray.size());
            for (Object not : notsArray) {
                JSONObject notJson = (JSONObject)not;
                Number type = (Number)notJson.get((Object)"Type");
                Number gameID = (Number)notJson.get((Object)"EntID");
                log.info("type:" + type + " gameID:" + gameID);
                JSONArray paramsArray = (JSONArray)notJson.get((Object)"Params");
                this.handlePush(paramsArray, type, gameID);
            }
        }
        catch (Exception jsonString) {
            // empty catch block
        }
    }

    private void handlePush(JSONArray paramsArray, Number type, Number gameID) {
        String state;
        String TeamNum;
        String player;
        String time;
        log.info("inside handlePush");
        String[] teams = this.getGamefromID(gameID);
        String message = null;
        int sms = 0;
        int matchbwFavTeams = Integer.parseInt(teams[11]);
        if (type.intValue() == 15) {
            sms = matchbwFavTeams;
            String homeScore = (String)((JSONObject)paramsArray.get(0)).get((Object)"Value");
            String awayScore = (String)((JSONObject)paramsArray.get(1)).get((Object)"Value");
            log.info(String.valueOf(homeScore) + " " + awayScore + teams);
            message = "GOAL DISALLOWED!\n";
            message = String.valueOf(message) + "The Goal has been ruled out.\n\nUpdated Score:\n" + teams[1] + " " + homeScore + " - " + awayScore + " " + teams[2] + "\n\n";
        }
        if (type.intValue() == 10) {
            sms = matchbwFavTeams;
            TeamNum = (String)((JSONObject)paramsArray.get(0)).get((Object)"Value");
            time = (String)((JSONObject)paramsArray.get(1)).get((Object)"Value");
            player = (String)((JSONObject)paramsArray.get(2)).get((Object)"Value");
            String homeScore = (String)((JSONObject)paramsArray.get(3)).get((Object)"Value");
            String awayScore = (String)((JSONObject)paramsArray.get(4)).get((Object)"Value");
            log.info(String.valueOf(TeamNum) + " " + time + " " + player + " " + homeScore + " " + awayScore + teams);
            message = this.getGoalMessage(teams, TeamNum, time, player, homeScore, awayScore);
            message = String.valueOf(message) + "\n\nScore:\n" + teams[1] + " " + homeScore + " - " + awayScore + " " + teams[2] + "\n\n";
        }
        if (type.intValue() == 11) {
            TeamNum = (String)((JSONObject)paramsArray.get(0)).get((Object)"Value");
            time = (String)((JSONObject)paramsArray.get(1)).get((Object)"Value");
            player = (String)((JSONObject)paramsArray.get(2)).get((Object)"Value");
            message = this.getYellowCardMessage(teams[Integer.parseInt(TeamNum)], time, player);
            message = String.valueOf(message) + "\n\nScore:\n" + teams[1] + " " + teams[4] + " - " + teams[5] + " " + teams[2] + "\n\n";
        }
        if (type.intValue() == 12) {
            sms = 1;
            TeamNum = (String)((JSONObject)paramsArray.get(0)).get((Object)"Value");
            time = (String)((JSONObject)paramsArray.get(1)).get((Object)"Value");
            player = (String)((JSONObject)paramsArray.get(2)).get((Object)"Value");
            message = this.getRedCardMessage(teams[Integer.parseInt(TeamNum)], time, player);
            message = String.valueOf(message) + "\n\nScore:\n" + teams[1] + " " + teams[4] + " - " + teams[5] + " " + teams[2] + "\n\n";
        }
        if (type.intValue() == 9 && (state = (String)((JSONObject)paramsArray.get(0)).get((Object)"Value")).equalsIgnoreCase("Halftime")) {
            message = this.getHalfTimeMessage(teams);
            this.getResults(String.valueOf(gameID.intValue()), message);
            message = null;
        }
        if (type.intValue() == 32) {
            sms = 1;
            message = this.getKickOffMessage(teams);
        }
        if (type.intValue() == 33) {
            message = this.getMatchEndMessage(teams);
            this.getResults(String.valueOf(gameID.intValue()), message);
            message = null;
        }
        log.info(message);
        if (message != null) {
            message = String.valueOf(message) + "(" + teams[3] + ")";
            if (sms == 1) {
                this.sendMessage(message);
            }
            this.postComment(message);
        }
    }

    private String getRedCardMessage(String team, String time, String player) {
        String message = "RED CARD\n";
        message = String.valueOf(message) + player + " is SENT OFF in " + this.getTimeEndString(time) + " and " + team + " are a man down!";
        return message;
    }

    public String getKickOffMessage(String[] teams) {
        String message = "KICK OFF";
        message = String.valueOf(message) + "\nWe're underway between " + teams[1] + " and " + teams[2];
        if (teams[10] != null) {
            message = String.valueOf(message) + " at " + teams[10];
        }
        message = String.valueOf(message) + "!\n\n" + teams[9];
        return message;
    }

    private String getHalfTimeMessage(String[] teams) {
        String message = "HALF TIME\n";
        int diff = Integer.parseInt(teams[4]) - Integer.parseInt(teams[5]);
        // if (diff == 0) {
        //     message = String.valueOf(message) + "Nothing to seperate the teams at the end of first half.";
        // } else if (diff == 1) {
        //     message = String.valueOf(message) + teams[1] + " with a narrow lead at the end of first half.";
        // } else if (diff == -1) {
        //     message = String.valueOf(message) + teams[2] + " with a narrow lead at the end of first half.";
        // } else if (diff > 1) {
        //     message = String.valueOf(message) + teams[1] + " lead by " + Math.abs(diff) + "-goals at the end of first half.";
        // } else if (diff < -1) {
        //     message = String.valueOf(message) + teams[2] + " lead by " + Math.abs(diff) + "-goals at the end of first half.";
        // }
        return message;
    }

    public String getMatchEndMessage(String[] teams) {
        Random rn = new Random();
        HashMap<Integer, String> Draw = new HashMap<Integer, String>();
        Draw.put(0, "Its all square and teams will share the points");
        Draw.put(1, String.valueOf(teams[1]) + " and " + teams[2] + " settle for a draw");
        Draw.put(2, "None able to score that crucial winner and its a draw");
        HashMap<Integer, String> HomeWinByTwoGoals = new HashMap<Integer, String>();
        HomeWinByTwoGoals.put(0, "Comfortable victory for " + teams[1]);
        HomeWinByTwoGoals.put(1, "Dominating display by " + teams[1]);
        HomeWinByTwoGoals.put(2, "Referee blows the whistle and its victory for " + teams[1]);
        HashMap<Integer, String> AwayWinByTwoGoals = new HashMap<Integer, String>();
        AwayWinByTwoGoals.put(0, "Comprehensive away win for " + teams[2]);
        AwayWinByTwoGoals.put(1, "This is a great away result for " + teams[2]);
        AwayWinByTwoGoals.put(2, String.valueOf(teams[2]) + " win by 2 goals away from home");
        HashMap<Integer, String> HomeWinByMoreGoals = new HashMap<Integer, String>();
        HomeWinByMoreGoals.put(0, "Massive win for " + teams[1] + " against " + teams[2]);
        HomeWinByMoreGoals.put(1, "Thats it! and its a superb result for " + teams[1]);
        HomeWinByMoreGoals.put(2, "Pee..Pee..Pee.. and its a great result for " + teams[1] + " as they thrash " + teams[2]);
        HashMap<Integer, String> AwayWinByMoreGoals = new HashMap<Integer, String>();
        AwayWinByMoreGoals.put(0, "Superb away win for " + teams[2] + " as " + teams[1] + " are beaten");
        AwayWinByMoreGoals.put(1, "Peach of a performance by " + teams[2] + " as " + teams[1] + " are thrashed");
        AwayWinByMoreGoals.put(2, "Shocking result as " + teams[1] + " are blown away by " + teams[2]);
        HashMap<Integer, String> HomeWinBySingleGoal = new HashMap<Integer, String>();
        HomeWinBySingleGoal.put(0, "Victory for " + teams[1] + " in a close encounter");
        HomeWinBySingleGoal.put(1, String.valueOf(teams[1]) + " hold on for a narrow win vs " + teams[2]);
        HomeWinBySingleGoal.put(2, String.valueOf(teams[1]) + " overcome a tough competition from " + teams[2] + " for a narrow win");
        HashMap<Integer, String> AwayWinBySingleGoal = new HashMap<Integer, String>();
        AwayWinBySingleGoal.put(0, "Thats it! " + teams[2] + " hold on for a great away win vs " + teams[1]);
        AwayWinBySingleGoal.put(1, String.valueOf(teams[2]) + " fought hard and earns a superb away win vs " + teams[1]);
        AwayWinBySingleGoal.put(2, "A well-deserved away win for " + teams[2]);
        String message = "FULL TIME\n";
        // int diff = Integer.parseInt(teams[4]) - Integer.parseInt(teams[5]);
        // if (diff == 0) {
        //     message = String.valueOf(message) + (String)Draw.get(rn.nextInt(Draw.size()));
        // } else if (diff == 2) {
        //     message = String.valueOf(message) + (String)HomeWinByTwoGoals.get(rn.nextInt(Draw.size()));
        // } else if (diff == -2) {
        //     message = String.valueOf(message) + (String)AwayWinByTwoGoals.get(rn.nextInt(Draw.size()));
        // } else if (diff > 2) {
        //     message = String.valueOf(message) + (String)HomeWinByMoreGoals.get(rn.nextInt(Draw.size()));
        // } else if (diff < -2) {
        //     message = String.valueOf(message) + (String)AwayWinByMoreGoals.get(rn.nextInt(Draw.size()));
        // } else if (diff == 1) {
        //     message = String.valueOf(message) + (String)HomeWinBySingleGoal.get(rn.nextInt(Draw.size()));
        // } else if (diff == -1) {
        //     message = String.valueOf(message) + (String)AwayWinBySingleGoal.get(rn.nextInt(Draw.size()));
        // }
        // if (teams[10] != null) {
        //     message = String.valueOf(message) + " at " + teams[10] + ".";
        // }
        return message;
    }

    private String getYellowCardMessage(String team, String time, String player) {
        String message = "YELLOW CARD shown to " + player + " (" + team + ") in " + this.getTimeEndString(time);
        return message;
    }

    public String getGoalMessage(String[] teams, String TeamNum, String time, String player, String homeScore, String awayScore) {
        String team = teams[Integer.parseInt(TeamNum)];
        int homescore = Integer.parseInt(homeScore);
        int awayscore = Integer.parseInt(awayScore);
        String message = "GOAL!\n";
        int diff = 0;
        diff = TeamNum.equalsIgnoreCase("1") ? homescore - awayscore : awayscore - homescore;
        if (diff == 0) {
            message = String.valueOf(message) + team + " scores the equaliser";
        } else if (diff == 1) {
            message = String.valueOf(message) + team + " take the lead";
        } else if (diff > 1) {
            message = String.valueOf(message) + team + " extend their lead to " + Math.abs(diff) + "-goals";
        } else if (diff < 0) {
            message = String.valueOf(message) + team + " pulls one back";
        }
        message = String.valueOf(message) + " in " + this.getTimeEndString(time);
        if (teams[10] != null) {
            if (diff == 0) {
                message = String.valueOf(message) + " Game ON at " + teams[10] + "!";
            }
            if (diff == 3) {
                message = String.valueOf(message) + " They are running riot at " + teams[10] + "!";
            }
            if (diff == 4) {
                message = String.valueOf(message) + " This has turned out into a rather one-sided contest.";
            }
            if (diff > 4) {
                message = String.valueOf(message) + " This is absolute carnage.";
            }
        }
        return message;
    }

    public String getTimeEndString(String time) {
        String addtional = time;
        int inttime = Integer.parseInt(time);
        int rem = inttime % 10;
        if (inttime > 10 && inttime < 20) {
            addtional = "th";
        } else {
            switch (rem) {
                case 1: {
                    addtional = "st";
                    break;
                }
                case 2: {
                    addtional = "nd";
                    break;
                }
                case 3: {
                    addtional = "rd";
                    break;
                }
                case 0: 
                case 4: 
                case 5: 
                case 6: 
                case 7: 
                case 8: 
                case 9: {
                    addtional = "th";
                }
            }
        }
        return String.valueOf(time) + addtional + " min.";
    }

    private void postComment(String message) {
        try {
            String commentUrl = "http://footballpush.appspot.com/shouts?football=1&name=LiveScores&shout=" + URLEncoder.encode(message, "UTF-8");
            FootballpushServlet.readUrl(commentUrl);
        }
        catch (Exception e) {
            log.log(Level.SEVERE, e.getMessage(), e);
            e.printStackTrace();
        }
    }

    private void sendMessage(String message) {
        try {
            log.info("inside sendMessage........");

            // using twilio sms
            new twilio_sms("+919620950489", message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String[] getGamefromID(Number gameID) {
        String[] gameFound;
        String Uri = String.format(this.matchUri, String.valueOf(gameID));
        gameFound = null;
        try {
            String[][] arrstring = FootballpushServlet.games = FootballpushServlet.getGame(Uri);
            int n = arrstring.length;
            int n2 = 0;
            while (n2 < n) {
                String[] game = arrstring[n2];
                log.info("Game: " + game[1] + " - " + game[2]);
                if (Integer.parseInt(game[0]) == gameID.intValue()) {
                    gameFound = game;
                    break;
                }
                ++n2;
            }
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return gameFound;
    }

    private static String getCompetitionfromID(Number compID) {
        String compFound = null;
        log.info("inside getCompetitionfromID" + compID);
        String[][] arrstring = competitions;
        int n = arrstring.length;
        int n2 = 0;
        while (n2 < n) {
            String[] comp = arrstring[n2];
            log.info("Competition: " + comp[1]);
            if (Integer.parseInt(comp[0]) == compID.intValue()) {
                compFound = comp[1];
                break;
            }
            ++n2;
        }
        return compFound;
    }

    private static String readUrl(String urlString) {
        BufferedReader reader = null;
        log.warning(urlString);
        StringBuffer buffer = new StringBuffer();
        try {
            int read;
            URL url = new URL(urlString);
            reader = new BufferedReader(new InputStreamReader(url.openStream(), "UTF-8"));
            char[] chars = new char[1024];
            while ((read = reader.read(chars)) != -1) {
                buffer.append(chars, 0, read);
            }
            String string = buffer.toString();
            return string;
        }
        catch (Exception e) {
            log.info("Ex: " + e);
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

    public static String[][] getGames(String url) throws FileNotFoundException, IOException {
        JSONParser parser = new JSONParser();
        String[][] activeGames = null;
        try {
            String jsonString = FootballpushServlet.readUrl(url);
            Object obj = parser.parse(jsonString);
            JSONObject jsonObject = (JSONObject)obj;
            jsonObject.get((Object)"LastUpdateID");
            JSONArray gamesArray = (JSONArray)jsonObject.get((Object)"Games");
            activeGames = new String[gamesArray.size()][];
            int gameNum = 0;
            int compNum = 0;
            JSONArray competitionsArray = (JSONArray)jsonObject.get((Object)"Competitions");
            competitions = new String[competitionsArray.size()][];
            for (Object comp : competitionsArray) {
                String[] compInfo = new String[2];
                JSONObject compsJson = (JSONObject)comp;
                Number compID = (Number)compsJson.get((Object)"ID");
                String competition = (String)compsJson.get((Object)"Name");
                compInfo[0] = compID.toString();
                String stage = "";
                try {
                    JSONArray seasonJsonArray = (JSONArray)compsJson.get((Object)"Seasons");
                    for (Object season : seasonJsonArray) {
                        JSONObject seasonJson = (JSONObject)season;
                        JSONArray stagesJsonArray = (JSONArray)seasonJson.get((Object)"Stages");
                        Iterator iterator = stagesJsonArray.iterator();
                        while (iterator.hasNext()) {
                            Object stageObj = iterator.next();
                            JSONObject stageJson = (JSONObject)stageObj;
                            stage = " - " + (String)stageJson.get((Object)"Name");
                        }
                    }
                }
                catch (Exception e) {
                    log.info("Ex " + e);
                    stage = "";
                }
                compInfo[1] = String.valueOf(competition) + stage;
                FootballpushServlet.competitions[compNum++] = compInfo;
            }
            for (Object game : gamesArray) {
                Object[] gameInfo = new String[7];
                JSONObject gamesJson = (JSONObject)game;
                Number gameID = (Number)gamesJson.get((Object)"ID");
                Number compID = (Number)gamesJson.get((Object)"Comp");
                gameInfo[0] = gameID.toString();
                JSONArray compsArray = (JSONArray)gamesJson.get((Object)"Comps");
                JSONArray scores = (JSONArray)gamesJson.get((Object)"Scrs");
                JSONArray eventsArray = (JSONArray)gamesJson.get((Object)"Events");
                int teamNum = 1;
                for (Object comp2 : compsArray) {
                    JSONObject compsJson = (JSONObject)comp2;
                    String team = (String)compsJson.get((Object)"Name");
                    gameInfo[teamNum++] = team;
                }
                gameInfo[3] = FootballpushServlet.getCompetitionfromID(compID).toUpperCase();
                gameInfo[4] = String.valueOf(((Number)scores.get(0)).intValue());
                gameInfo[5] = String.valueOf(((Number)scores.get(1)).intValue());
                String penScore = "";
                int penHomeScore = ((Number)scores.get(8)).intValue();
                int penAwayScore = ((Number)scores.get(9)).intValue();
                if (penHomeScore > 0) {
                    penScore = penHomeScore > penAwayScore ? " (" + gameInfo[1] + " won " + penHomeScore + "-" + penAwayScore + " on penalties)" : " (" + gameInfo[2] + " won " + penAwayScore + "-" + penHomeScore + " on penalties)";
                }
                JSONArray aggScore = null;
                if (gamesJson.get((Object)"AggregatedScore") != null) {
                    aggScore = (JSONArray)gamesJson.get((Object)"AggregatedScore");
                }
                String aggregatedScore = "";
                if (aggScore != null) {
                    int aggAwayScore;
                    int aggHomeScore = ((Number)aggScore.get(0)).intValue();
                    aggregatedScore = aggHomeScore > (aggAwayScore = ((Number)aggScore.get(1)).intValue()) ? " (" + (String)gameInfo[1] + " won " + aggHomeScore + "-" + aggAwayScore + " on Aggregate)" : " (" + (String)gameInfo[2] + " won " + aggAwayScore + "-" + aggHomeScore + " on Aggregate)";
                }
                String goals = "";
                if (eventsArray != null) {
                    for (Object event : eventsArray) {
                        String time;
                        Number comp3;
                        Object team;
                        String player;
                        JSONObject eventsJson = (JSONObject)event;
                        Number type = (Number)eventsJson.get((Object)"Type");
                        if (type.intValue() == 0) {
                            player = (String)eventsJson.get((Object)"Player");
                            time = String.valueOf(((Number)eventsJson.get((Object)"GT")).intValue());
                            comp3 = (Number)eventsJson.get((Object)"Comp");
                            team = "";
                            team = gameInfo[comp3.intValue()];
                            int subType = ((Number)eventsJson.get((Object)"SType")).intValue();
                            String goalMode = "";
                            if (subType == 1) {
                                goalMode = "(OWN GOAL)";
                            }
                            if (subType == 2) {
                                goalMode = "(PEN)";
                            }
                            goals = String.valueOf(goals) + time + "' - " + player + goalMode + " (" + (String)team + ")\n";
                        }
                        if (type.intValue() != 2) continue;
                        player = (String)eventsJson.get((Object)"Player");
                        time = String.valueOf(((Number)eventsJson.get((Object)"GT")).intValue());
                        comp3 = (Number)eventsJson.get((Object)"Comp");
                        team = "";
                        team = gameInfo[comp3.intValue()];
                        goals = String.valueOf(goals) + time + "' - " + player + " (RED CARD - " + (String)team + ")\n";
                    }
                }
                gameInfo[6] = goals;
                if (penScore != "") {
                    gameInfo[2] = String.valueOf(gameInfo[2]) + penScore;
                }
                if (aggregatedScore != "") {
                    gameInfo[2] = String.valueOf(gameInfo[2]) + aggregatedScore;
                }
                System.out.println(Arrays.toString(gameInfo));
                activeGames[gameNum++] = (String[]) gameInfo;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println(Arrays.toString((Object[])activeGames));
        return activeGames;
    }

    public static String[][] getGame(String url) throws FileNotFoundException, IOException {
        JSONParser parser = new JSONParser();
        String[][] activeGames = null;
        HashMap map = new HashMap();
        try {
            String jsonString = FootballpushServlet.readUrl(url);
            Object obj = parser.parse(jsonString);
            JSONObject jsonObject = (JSONObject)obj;
            jsonObject.get((Object)"LastUpdateID");
            JSONArray gamesArray = (JSONArray)jsonObject.get((Object)"Games");
            activeGames = new String[gamesArray.size()][];
            int gameNum = 0;
            int compNum = 0;
            JSONArray competitionsArray = (JSONArray)jsonObject.get((Object)"Competitions");
            competitions = new String[competitionsArray.size()][];
            for (Object comp : competitionsArray) {
                String[] compInfo = new String[2];
                JSONObject compsJson = (JSONObject)comp;
                Number compID = (Number)compsJson.get((Object)"ID");
                String competition = (String)compsJson.get((Object)"Name");
                compInfo[0] = compID.toString();
                String stage = "";
                try {
                    JSONArray seasonJsonArray = (JSONArray)compsJson.get((Object)"Seasons");
                    for (Object season : seasonJsonArray) {
                        JSONObject seasonJson = (JSONObject)season;
                        JSONArray stagesJsonArray = (JSONArray)seasonJson.get((Object)"Stages");
                        for (Object stageObj : stagesJsonArray) {
                            JSONObject stageJson = (JSONObject)stageObj;
                            stage = " - " + (String)stageJson.get((Object)"Name");
                        }
                    }
                }
                catch (Exception e) {
                    log.info("Ex " + e);
                    stage = "";
                }
                compInfo[1] = String.valueOf(competition) + stage;
                FootballpushServlet.competitions[compNum++] = compInfo;
            }
            for (Object game : gamesArray) {
                String[] gameInfo = new String[12];
                JSONObject gamesJson = (JSONObject)game;
                Number gameID = (Number)gamesJson.get((Object)"ID");
                Number compID = (Number)gamesJson.get((Object)"Comp");
                String status = String.valueOf((Number)gamesJson.get((Object)"STID"));
                status = FootballpushServlet.getMatchStatus(gamesJson, status);
                gameInfo[0] = gameID.toString();
                JSONArray compsArray = (JSONArray)gamesJson.get((Object)"Comps");
                JSONArray scores = (JSONArray)gamesJson.get((Object)"Scrs");
                JSONArray eventsArray = (JSONArray)gamesJson.get((Object)"Events");
                JSONArray aggScore = null;
                if (gamesJson.get((Object)"AggregatedScore") != null) {
                    aggScore = (JSONArray)gamesJson.get((Object)"AggregatedScore");
                }
                int teamNum = 1;
                gameInfo[11] = "1";
                Arrays.sort(favTeams);
                for (Object comp2 : compsArray) {
                    JSONObject compsJson = (JSONObject)comp2;
                    String team = (String)compsJson.get((Object)"Name");
                    int teamID = ((Number)compsJson.get((Object)"ID")).intValue();
                    gameInfo[teamNum++] = team;
                    if (!gameInfo[11].equalsIgnoreCase("1") || Arrays.binarySearch(favTeams, teamID) >= 0) continue;
                    gameInfo[11] = "0";
                }
                gameInfo[3] = FootballpushServlet.getCompetitionfromID(compID).toUpperCase();
                int penHomeScore = ((Number)scores.get(8)).intValue();
                int penAwayScore = ((Number)scores.get(9)).intValue();
                gameInfo[4] = String.valueOf(((Number)scores.get(0)).intValue());
                gameInfo[5] = String.valueOf(((Number)scores.get(1)).intValue());
                String penScore = FootballpushServlet.getPenScore(gameInfo, penHomeScore, penAwayScore);
                String aggregatedScore = FootballpushServlet.getAggScore(gameInfo, aggScore);
                String goals = null;
                String yel = null;
                String red = null;
                String totalEvents = FootballpushServlet.getEvents(gameInfo, eventsArray, aggregatedScore, goals, yel, red);
                log.info(String.valueOf(status) + totalEvents);
                if (penScore != "") {
                    gameInfo[2] = String.valueOf(gameInfo[2]) + penScore;
                }
                gameInfo[6] = totalEvents;
                gameInfo[7] = status;
                String scorerow = null;
                scorerow = gameInfo[4].equalsIgnoreCase("-1") ? String.valueOf(gameInfo[1]) + " vs " + gameInfo[2] + "\n" + gameInfo[7] + "\n" : String.valueOf(gameInfo[7]) + " - " + gameInfo[1] + " " + gameInfo[4] + " - " + gameInfo[5] + " " + gameInfo[2] + "\n" + gameInfo[6] + "\n";
                gameInfo[8] = FootballpushServlet.getStats(gameInfo, gamesJson);
                gameInfo[9] = FootballpushServlet.getLineups(gameInfo, gamesJson);
                gameInfo[10] = gamesJson.get((Object)"Venue") != null ? (String)((JSONObject)gamesJson.get((Object)"Venue")).get((Object)"Name") : null;
                activeGames[gameNum++] = gameInfo;
            }
        }
        catch (Exception e) {
            log.log(Level.SEVERE, e.getMessage(), e);
            e.printStackTrace();
        }
        return activeGames;
    }

    public static String getAggScore(String[] gameInfo, JSONArray aggScore) {
        String aggregatedScore = "";
        try {
            if (aggScore != null) {
                String aggHomeScore = String.valueOf(((Number)aggScore.get(0)).intValue());
                String aggAwayScore = String.valueOf(((Number)aggScore.get(1)).intValue());
                aggregatedScore = "(Agg " + aggHomeScore + "-" + aggAwayScore + ")";
                gameInfo[2] = String.valueOf(gameInfo[2]) + " " + "(Agg " + aggHomeScore + "-" + aggAwayScore + ")";
            }
        }
        catch (Exception e) {
            return "";
        }
        return aggregatedScore;
    }

    public static String getPenScore(String[] gameInfo, int penHomeScore, int penAwayScore) {
        String penScore = "";
        try {
            if (penHomeScore > 0) {
                penScore = penHomeScore > penAwayScore ? " (" + gameInfo[1] + " won " + penHomeScore + "-" + penAwayScore + " on penalties)" : " (" + gameInfo[2] + " won " + penAwayScore + "-" + penHomeScore + " on penalties)";
            }
        }
        catch (Exception e) {
            return "";
        }
        return penScore;
    }

    public void getPolls(HttpServletResponse resp, String[] gameInfo, JSONObject gamesJson) throws IOException {
        try {
            JSONObject pollsJson = (JSONObject)gamesJson.get((Object)"WhoWillWinReults");
            float homeTeamPoll = ((Number)pollsJson.get((Object)"Vote1")).floatValue();
            float awayTeamPoll = ((Number)pollsJson.get((Object)"Vote2")).floatValue();
            float drawPoll = ((Number)pollsJson.get((Object)"VoteX")).floatValue();
            float total = homeTeamPoll + awayTeamPoll + drawPoll;
            resp.getWriter().println("Predictions:\n" + gameInfo[1] + ": " + Math.round(homeTeamPoll / total * 100.0f) + "%\n" + gameInfo[2] + ": " + Math.round(awayTeamPoll / total * 100.0f) + "%\nDraw: " + Math.round(drawPoll / total * 100.0f) + "%");
        }
        catch (Exception pollsJson) {
            // empty catch block
        }
    }

    public static String getLineups(String[] gameInfo, JSONObject gamesJson) throws IOException {
        JSONArray lineupArray = (JSONArray)gamesJson.get((Object)"Lineups");
        HashMap<Integer, String> Players = new HashMap<Integer, String>();
        String lineups = "Starting XI:\n";
        try {
            if (lineupArray != null) {
                int i = 1;
                for (Object lineup : lineupArray) {
                    JSONObject lineupJson = (JSONObject)lineup;
                    JSONArray playersArray = (JSONArray)lineupJson.get((Object)"Players");
                    String playing = "";
                    String subs = "";
                    int playerNum = 1;
                    for (Object player : playersArray) {
                        JSONObject playerJson = (JSONObject)player;
                        if (!String.valueOf((Number)playerJson.get((Object)"Status")).equalsIgnoreCase("1")) continue;
                        if (playerJson.get((Object)"FieldPosition") != null) {
                            if (String.valueOf((Number)playerJson.get((Object)"FieldPosition")).equalsIgnoreCase("1")) {
                                Players.put(((Number)playerJson.get((Object)"FieldPosition")).intValue(), String.valueOf((String)playerJson.get((Object)"PlayerName")) + "(GK)");
                                continue;
                            }
                            Players.put(((Number)playerJson.get((Object)"FieldPosition")).intValue(), (String)playerJson.get((Object)"PlayerName"));
                            continue;
                        }
                        Players.put(playerNum++, (String)playerJson.get((Object)"PlayerName"));
                    }
                    int j = 1;
                    lineups = String.valueOf(lineups) + gameInfo[i] + ": ";
                    while (j < 12) {
                        lineups = String.valueOf(lineups) + (String)Players.get(j++) + ", ";
                    }
                    lineups = String.valueOf(lineups) + "\n\n";
                    ++i;
                    playing = "";
                    subs = "";
                }
            }
        }
        catch (Exception e) {
            return "";
        }
        return lineups;
    }

    public static String getEvents(String[] gameInfo, JSONArray eventsArray, String aggregatedScore, String goals, String yel, String red) {
        String totalEvents = "";
        try {
            if (eventsArray != null) {
                for (Object event : eventsArray) {
                    Number comp;
                    String team;
                    String time;
                    JSONObject eventsJson = (JSONObject)event;
                    Number type = (Number)eventsJson.get((Object)"Type");
                    if (type.intValue() == 0) {
                        if (goals == null) {
                            goals = "GOALS:\n";
                        }
                        int subType = ((Number)eventsJson.get((Object)"SType")).intValue();
                        String goalMode = "";
                        if (subType == 1) {
                            goalMode = "(OWN GOAL)";
                        }
                        if (subType == 2) {
                            goalMode = "(PEN)";
                        }
                        String player = (String)eventsJson.get((Object)"Player");
                        String time2 = String.valueOf(((Number)eventsJson.get((Object)"GT")).intValue());
                        Number comp2 = (Number)eventsJson.get((Object)"Comp");
                        String team2 = "";
                        team2 = gameInfo[comp2.intValue()].replace(aggregatedScore, "");
                        goals = String.valueOf(goals) + time2 + "' - " + player + goalMode + " (" + team2 + ")\n";
                    }
                    if (type.intValue() == 2) {
                        if (red == null) {
                            red = "RED CARDS:\n";
                        }
                        String player = (String)eventsJson.get((Object)"Player");
                        time = String.valueOf(((Number)eventsJson.get((Object)"GT")).intValue());
                        comp = (Number)eventsJson.get((Object)"Comp");
                        team = "";
                        team = gameInfo[comp.intValue()].replace(aggregatedScore, "");
                        red = String.valueOf(red) + time + "' - " + player + " (" + team + ")\n";
                    }
                    if (type.intValue() != 1) continue;
                    if (yel == null) {
                        yel = "YELLOW CARDS:\n";
                    }
                    String player = (String)eventsJson.get((Object)"Player");
                    time = String.valueOf(((Number)eventsJson.get((Object)"GT")).intValue());
                    comp = (Number)eventsJson.get((Object)"Comp");
                    team = "";
                    team = gameInfo[comp.intValue()].replace(aggregatedScore, "");
                    yel = String.valueOf(yel) + time + "' - " + player + " (" + team + ")\n";
                }
            }
            if (goals != null) {
                totalEvents = String.valueOf(totalEvents) + "-\n" + goals;
            }
            if (red != null) {
                totalEvents = String.valueOf(totalEvents) + "-\n" + red;
            }
        }
        catch (Exception e) {
            return "";
        }
        return totalEvents;
    }

    public static String getMatchStatus(JSONObject gamesJson, String status) throws ParseException {
        try {
            if (status.equalsIgnoreCase("2")) {
                SimpleDateFormat format = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.ENGLISH);
                String startTime = (String)gamesJson.get((Object)"STime");
                Date today = new Date();
                Date date = format.parse(startTime);
                Calendar c = Calendar.getInstance();
                String dayOfWeek = c.getDisplayName(7, 1, Locale.US);
                SimpleDateFormat format1 = new SimpleDateFormat("MMM dd EEE, hh:mma");
                format1.setTimeZone(TimeZone.getTimeZone("IST"));
                status = format1.format(date);
                if (status.contains(dayOfWeek)) {
                    format1 = new SimpleDateFormat("hh:mma");
                    format1.setTimeZone(TimeZone.getTimeZone("IST"));
                    status = format1.format(date);
                }
                if (gamesJson.get((Object)"Countdown") != null) {
                    int totalSecs = ((Number)gamesJson.get((Object)"Countdown")).intValue();
                    int hours = totalSecs / 3600;
                    int minutes = totalSecs % 3600 / 60;
                    int seconds = totalSecs % 60;
                    status = " Starts in " + String.format("%02d", hours) + ":" + String.format("%02d", minutes) + ":" + String.format("%02d", seconds) + "\n";
                }
            }
            if (status.equalsIgnoreCase("3")) {
                status = "FT";
            }
            if (status.equalsIgnoreCase("7")) {
                status = "HT";
            }
            if (status.equalsIgnoreCase("154")) {
                status = "AFTER ET";
            }
            if (status.equalsIgnoreCase("155")) {
                status = "FT";
            }
            if (status.equalsIgnoreCase("6") || status.equalsIgnoreCase("8") || status.equalsIgnoreCase("10")) {
                status = "LIVE: " + (String)gamesJson.get((Object)"GTD");
            }
        }
        catch (Exception format) {
            // empty catch block
        }
        return status;
    }

    private static String getStats(String[] gameInfo, JSONObject gamesJson) {
        String stats = "STATS:\n";
        try {
            JSONArray statsArray = (JSONArray)gamesJson.get((Object)"Statistics");
            for (Object stat : statsArray) {
                JSONObject statJson = (JSONObject)stat;
                String type = String.valueOf(((Number)statJson.get((Object)"Type")).intValue());
                JSONArray vals = (JSONArray)statJson.get((Object)"Vals");
                HashMap<String, String> dictionary = new HashMap<String, String>();
                dictionary.put("1", "Yellow cards");
                dictionary.put("3", "Total Shots");
                dictionary.put("4", "Shots on Target");
                dictionary.put("10", "Possession(%)");
                dictionary.put("12", "Fouls");
                if (dictionary.get(type) == null) continue;
                stats = String.valueOf(stats) + (String)dictionary.get(type) + ": " + vals.get(0) + " - " + vals.get(1) + "\n";
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            return "";
        }
        stats="";
        return stats;
    }
}
