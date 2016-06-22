package com.push.footballpush;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Logger;

import javax.servlet.http.*;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;

@SuppressWarnings("serial")
public class FootballServlet extends HttpServlet {
	private static Map<String, ArrayList<String[]>> games;
	private static String[][] competitions;
	private static final Logger log = Logger.getLogger(FootballServlet.class
			.getName());
	public String gamesUri = "http://ws.365scores.com/Data/Games/?lang=10&uc=80&competitions=6316,5694,572,573,570,6071,7,11,17,&competitors=5491,105,106,108,104,110,131,132,134,331,341,224,227,226&startdate=%s&enddate=%s&FullCurrTime=true&uid=%s";

	// Manu - 105
	// chelsea - 106
	// liv - 108
	// arsenal - 104
	// manc - 110
	//
	// real -131
	// barca-132
	// atl. mad - 134
	//
	// bayern - 331
	// dort - 341
	//
	// inter - 224
	// Ac milan - 227
	// juve - 226

	// india - 5491

	// Bundes - 25
	// epl - 7
	// la liga - 11
	// serie a - 17
	// ucl - 572
	// uel - 573

	public void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		int hour = new Date().getHours();
		resp.getWriter()
				.println(
						"<!DOCTYPE html><html><head><meta name='txtweb-appkey' content='fa0d179a-9e40-4d3f-a0f6-cba551dfd3a8'> </head><body>");
		DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
		Calendar cal = Calendar.getInstance();
		String todayDate = dateFormat.format(cal.getTime());
			cal.add(Calendar.DATE, -1);
		String yestDate = dateFormat.format(cal.getTime());
		if (hour > 7)
		{
			yestDate ="";
		}
		String url = String.format(gamesUri, yestDate, todayDate, "-1");

		// String url="http://sms.cricbuzz.com/chrome/alert.json";
		resp.setContentType("text/html");
		games = getGames(url);

		if(games==null)
			resp.getWriter().println("No active matches!");
		else
		{
			if (req.getParameter("id") == null)
				getAllGames(resp);
			else
				getDetails(resp, req.getParameter("id"));
		}
	}

	private void getDetails(HttpServletResponse resp, String matchId)
			throws IOException {
		Iterator it = games.entrySet().iterator();
		String league = null;
		ArrayList<String[]> games = null;
		while (it.hasNext()) {
			Map.Entry pair = (Map.Entry) it.next();
			league = (String) pair.getKey();
			games = (ArrayList<String[]>) pair.getValue();
			// resp.getWriter().println(league + "<br>");
			for (String[] game1 : games)
				if (game1 != null) {
					if (game1[0].equalsIgnoreCase(matchId)) {
						String scorerow = null;
						if (game1[4].equalsIgnoreCase("-1"))
							scorerow = game1[7] + " - " + game1[1] + " vs "
									+ game1[2] + "<br>";
						else
							scorerow = game1[7] + " - " + game1[1] + " "
									+ game1[4] + " - " + game1[5] + " "
									+ game1[2] + "<br>" + game1[6];
						resp.getWriter().println(scorerow);
						break;
					}
				}
			games = null;
			league = null;
			it.remove(); // avoids a ConcurrentModificationException
		}

	}

	public void getAllGames(HttpServletResponse resp) throws IOException {
		Iterator it = games.entrySet().iterator();
		String league = null;
		ArrayList<String[]> games = null;
		while (it.hasNext()) {
			Map.Entry pair = (Map.Entry) it.next();
			league = (String) pair.getKey();
			games = (ArrayList<String[]>) pair.getValue();
			resp.getWriter().println(league + "<br>");
			for (String[] game1 : games)
				if (game1 != null) {
					String scorerow = null;
					if (game1[4].equalsIgnoreCase("-1"))
						scorerow = game1[7] + " - " + game1[1] + " vs "
								+ game1[2] + "<br>";
					else
						scorerow = "<a href='http://footballpush.appspot.com/football?id="
								+ game1[0]
								+ "'> "
								+ game1[7]
								+ " - "
								+ game1[1]
								+ " "
								+ game1[4]
								+ " - "
								+ game1[5]
								+ " " + game1[2] + "</a><br>";
					resp.getWriter().println(scorerow);

				}
			resp.getWriter().println("<br>");
			games = null;
			league = null;
			it.remove(); // avoids a ConcurrentModificationException
		}
	}

	private static String getCompetitionfromID(Number compID) {
		String compFound = null;
		for (String comp[] : competitions) {
			if (Integer.parseInt(comp[0]) == compID.intValue()) {
				compFound = comp[1];
				break;
			}
		}
		return compFound;

	}

	private static String readUrl(String urlString) {
		BufferedReader reader = null;
		log.info("Url: "+urlString);
		StringBuffer buffer = new StringBuffer();
		try {
			URL url = new URL(urlString);
			reader = new BufferedReader(new InputStreamReader(url.openStream()));

			int read;
			char[] chars = new char[1024];
			while ((read = reader.read(chars)) != -1)
				buffer.append(chars, 0, read);
			// log.info(buffer.toString());
			System.out.println(buffer.toString());
			return buffer.toString();
		} catch (Exception e) {
			log.info("Ex: " + e);
			// readUrl(urlString);
			return buffer.toString();
		} finally {

			if (reader != null)
				try {
					reader.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}
	}

	/*
	 * Notification Types: 11 - Yellow Card "Params": [ { "Num": 1, "Value": "2"
	 * - Team num }, { "Num": 2, "Value": "21" - time }, { "Num": 3, "Value":
	 * "Coe N." - Player } ], 10 - Goal "Params": [ { "Num": 1, "Value": "2" -
	 * Scored team num }, { "Num": 2, "Value": "18" - time }, { "Num": 3,
	 * "Value": "" - Goal scorer }, { "Num": 4, "Value": "0" - Home Score }, {
	 * "Num": 5, "Value": "1" - Away Score }, { "Num": 6, "Value": "" } 9 - Half
	 * time { "Params": [ { "Num": 1, "Value": "Halftime" } ], "Type": 9,
	 * "EntID": 672454 }
	 */

	public static Map<String, ArrayList<String[]>> getGames(String url)
			throws FileNotFoundException, IOException {
		JSONParser parser = new JSONParser();
		String[][] activeGames = null;
		Map<String, ArrayList<String[]>> map = new HashMap<String, ArrayList<String[]>>();
		try {
			String jsonString = readUrl(url);
//			if(jsonString == null)
//				return null;
			Object obj = parser.parse(jsonString);

			JSONObject jsonObject = (JSONObject) obj;

			jsonObject.get("LastUpdateID");
			JSONArray gamesArray = (JSONArray) jsonObject.get("Games");
//			if(gamesArray == null)
//				return null;
			activeGames = new String[gamesArray.size()][];
			int gameNum = 0, compNum = 0;
			// String[] gameInfo = new String[3];
			JSONArray competitionsArray = (JSONArray) jsonObject
					.get("Competitions");
			competitions = new String[competitionsArray.size()][];
			for (Object comp : competitionsArray) {
				String[] compInfo = new String[2];
				JSONObject compsJson = (JSONObject) comp;
				Number compID = (Number) compsJson.get("ID");
				String competition = (String) compsJson.get("Name");
				compInfo[0] = compID.toString();
				compInfo[1] = competition;
				competitions[compNum++] = compInfo;
			}

			for (Object game : gamesArray) {
				String[] gameInfo = new String[8];
				JSONObject gamesJson = (JSONObject) game;
				Number gameID = (Number) gamesJson.get("ID");
				Number compID = (Number) gamesJson.get("Comp");
				String status = String.valueOf((Number) gamesJson.get("STID"));
				if (status.equalsIgnoreCase("2")) {
					DateFormat format = new SimpleDateFormat(
							"dd-MM-yyyy HH:mm", Locale.ENGLISH);
					// format.setTimeZone((TimeZone.getTimeZone("IST")));
					Date date = format.parse((String) gamesJson.get("STime"));
					DateFormat format1 = new SimpleDateFormat(
							"MMM dd EEE, hh:mmaa");
					format1.setTimeZone((TimeZone.getTimeZone("IST")));
					status = format1.format(date);

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

					status = "AFTER PEN.";
				}
				if (status.equalsIgnoreCase("6")
						|| status.equalsIgnoreCase("8")
						|| status.equalsIgnoreCase("10")) {

					status = "LIVE: " + (String) gamesJson.get("GTD");
				}
				gameInfo[0] = gameID.toString();
				JSONArray compsArray = (JSONArray) gamesJson.get("Comps");
				JSONArray scores = (JSONArray) gamesJson.get("Scrs");
				// JSONArray events = (JSONArray) gamesJson.get("Events");
				JSONArray eventsArray = (JSONArray) gamesJson.get("Events");
				int teamNum = 1;
				for (Object comp : compsArray) {
					JSONObject compsJson = (JSONObject) comp;
					String team = (String) compsJson.get("Name");
					gameInfo[teamNum++] = team;
				}
				gameInfo[3] = getCompetitionfromID(compID).toUpperCase();
				gameInfo[4] = String.valueOf(((Number) scores.get(0))
						.intValue());
				gameInfo[5] = String.valueOf(((Number) scores.get(1))
						.intValue());

				String goals = null, yel = null, red = null;
				if (eventsArray != null)
					for (Object event : eventsArray) {
						JSONObject eventsJson = (JSONObject) event;

						Number type = (Number) eventsJson.get("Type");
						// Goal
						if (type.intValue() == 0) {
							if (goals == null)
								goals = "GOALS:<br>";
							String player = (String) eventsJson.get("Player");
							String time = String.valueOf(((Number) eventsJson
									.get("GT")).intValue());
							Number comp = (Number) eventsJson.get("Comp");
							String team = "";
							team = gameInfo[comp.intValue()];

							goals += "" + time + "' - " + player + " (" + team
									+ ")<br>";
						}
						if (type.intValue() == 2) {
							if (red == null)
								red = "RED CARDS:<br>";
							String player = (String) eventsJson.get("Player");
							String time = String.valueOf(((Number) eventsJson
									.get("GT")).intValue());
							Number comp = (Number) eventsJson.get("Comp");

							String team = "";
							team = gameInfo[comp.intValue()];

							red += "" + time + "' - " + player + " (" + team
									+ ")<br>";
						}
						if (type.intValue() == 1) {
							if (yel == null)
								yel = "YELLOW CARDS:<br>";
							String player = (String) eventsJson.get("Player");
							String time = String.valueOf(((Number) eventsJson
									.get("GT")).intValue());
							Number comp = (Number) eventsJson.get("Comp");

							String team = "";
							team = gameInfo[comp.intValue()];

							yel += "" + time + "' - " + player + " (" + team
									+ ")<br>";
						}
					}
				// System.out.println(goals);
				String totalEvents = "";
				if (goals != null)
					totalEvents += "-<br>" + goals;
				if (yel != null)
					totalEvents += "-<br>" + yel;
				if (red != null)
					totalEvents += "-<br>" + red;
				gameInfo[6] = totalEvents;
				gameInfo[7] = status;
				String key = gameInfo[3];
				if (map.containsKey(key)) {
					ArrayList<String[]> match = (ArrayList<String[]>) map
							.get(key);
					match.add(gameInfo);
					map.put(key, match);
				} else {
					ArrayList<String[]> match = new ArrayList<String[]>();
					match.add(gameInfo);
					map.put(key, match);
				}
				System.out.println((Arrays.toString(gameInfo)));
				activeGames[gameNum++] = gameInfo;

			}

			// games=jsonString;

		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println(Arrays.toString(activeGames));
		return map;

	}
}
/*
 * 
 * { "LastUpdateID": 117063592, "Notifications": [ { "Params": [ { "Num": 1,
 * "Value": "2" }, { "Num": 2, "Value": "18" }, { "Num": 3, "Value": "" }, {
 * "Num": 4, "Value": "0" }, { "Num": 5, "Value": "1" }, { "Num": 6, "Value": ""
 * } ], "Type": 10, "EntID": 555483 }, { "Params": [], "Type": 32, "EntID":
 * 672457 }, { "Params": [], "Type": 32, "EntID": 672455 }, { "Params": [ {
 * "Num": 1, "Value": "2" }, { "Num": 2, "Value": "21" }, { "Num": 3, "Value":
 * "Coe N." } ], "Type": 11, "EntID": 555483 }, { "Params": [ { "Num": 1,
 * "Value": "1" }, { "Num": 2, "Value": "22" }, { "Num": 3, "Value": "" }, {
 * "Num": 4, "Value": "1" }, { "Num": 5, "Value": "1" }, { "Num": 6, "Value": ""
 * } ], "Type": 10, "EntID": 555483 } ], "Games": [ { "SID": 1, "ID": 672454,
 * "Comp": 652, "Active": true, "Paused": false, "STID": 6, "GT": 36, "GTD":
 * "36'", "HasBets": false, "Winner": -1, "HasTips": false }, { "SID": 1, "ID":
 * 555483, "Comp": 151, "Active": true, "Paused": false, "STID": 6, "GT": 25,
 * "GTD": "25'", "Scrs": [ 1, 1, -2, -2, -2, -2, -2, -2, -2, -2 ], "Events": [ {
 * "Type": 1, "SType": -1, "Num": 1, "Comp": 1, "GT": 9, "Player":
 * "Russell Griffiths" }, { "Type": 0, "SType": 0, "Num": 1, "Comp": 2, "GT":
 * 18, "Player": "Fahid Ben Khalfallah" }, { "Type": 1, "SType": -1, "Num": 2,
 * "Comp": 2, "GT": 21, "Player": "Coe N." }, { "Type": 0, "SType": 2, "Num": 2,
 * "Comp": 1, "GT": 22, "Player": "Andrew Keogh" } ], "HasBets": false,
 * "Winner": -1, "HasTips": false, "HasStatistics": true }, { "SID": 1, "ID":
 * 672457, "Comp": 652, "Active": true, "Paused": false, "STID": 6, "GT": 5,
 * "GTD": "5'", "Scrs": [ 0, 0, -2, -2, -2, -2, -2, -2, -2, -2 ], "HasBets":
 * false, "Winner": -1, "HasTips": false }, { "SID": 1, "ID": 672455, "Comp":
 * 652, "Active": true, "Paused": false, "STID": 6, "GT": 3, "GTD": "3'",
 * "Scrs": [ 0, 0, -2, -2, -2, -2, -2, -2, -2, -2 ], "HasBets": false, "Winner":
 * -1, "HasTips": false } ], "RequestedUpdateID": 117061713, "CurrentDate":
 * "25-01-2015 09:34:16", "TTL": 10 }
 */