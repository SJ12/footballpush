package com.push.footballpush;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.http.*;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;

import com.push.footballpush.twilio_sms;


@SuppressWarnings("serial")
public class FootballpushServlet extends HttpServlet {
	private static String[][] games;
	private static String[][] competitions;
	private static final Logger log = Logger
			.getLogger(FootballpushServlet.class.getName());
	public String gamesUri = "http://ws.365scores.com/Data/Games/?lang=10&uc=80&competitions=5694,572,573,570,6071&competitors=5491,105,106,108,104,110,131,132,134,331,341,224,227,226&startdate=%s&enddate=%s&FullCurrTime=true&uid=%s";

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

		sendMessage("testing message");
		DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
		Calendar cal = Calendar.getInstance();
		if (req.getParameter("results") != null)
			cal.add(Calendar.DATE, -1);
		String yestDate = dateFormat.format(cal.getTime());
		
		String url = String.format(gamesUri, yestDate,"","-1");
		// String url="http://sms.cricbuzz.com/chrome/alert.json";
		resp.setContentType("text/html");
		games = getGames(url);

		for (String[] game : games) {
			if (game != null)
				resp.getWriter().println(
						game[0] + " - " + game[1] + " " + game[4] + " - "
								+ game[5] + " " + game[2] + "<br>");
		}
		if (req.getParameter("results") != null)
			getResults("all","");
		else
			getNotifications();
	}

	private void getResults(String gameID,String type) {
		String message = "";
		for (String[] game : games) {
			message += game[3] + ":<br>" + game[1] + " " + game[4] + " - "
					+ game[5] + " " + game[2] + "<br>" + game[6];
			if(gameID.equalsIgnoreCase("all"))
				sendMessage(message);
			else
				if(gameID.equalsIgnoreCase(game[0]))
				{
					sendMessage(message+"<br>"+type);
					break;
				}
					
			message = "";
		}

	}

	private void getNotifications() {
		log.info("inside getNotifications");
		String uid = null;
		DatastoreService datastore = DatastoreServiceFactory
				.getDatastoreService();
		Query q = new Query("LastEntry");
		PreparedQuery pq = datastore.prepare(q);
		for (Entity result : pq.asIterable()) {
			uid = (String) result.getProperty("uid");
		}
		String url = String.format(gamesUri, "","",uid);
		try {
			String jsonString = readUrl(url);
			JSONParser parser = new JSONParser();
			Object obj = parser.parse(jsonString);

			JSONObject jsonObject = (JSONObject) obj;
			Number updateID = (Number) jsonObject.get("LastUpdateID");
			log.info("updateID:" + updateID);
			Entity lastEntry = new Entity("LastEntry", 117882041);
			lastEntry.setProperty("uid", updateID.toString());
			datastore.put(lastEntry);
			JSONArray notsArray = (JSONArray) jsonObject.get("Notifications");
			log.info("Nots: " + notsArray + " " + notsArray.size());
			for (Object not : notsArray) {
				JSONObject notJson = (JSONObject) not;
				Number type = (Number) notJson.get("Type");

				Number gameID = (Number) notJson.get("EntID");
				log.info("type:" + type + " gameID:" + gameID);
				JSONArray paramsArray = (JSONArray) notJson.get("Params");
				handlePush(paramsArray, type, gameID);
			}

		} catch (Exception e) {

		}

	}

	private void handlePush(JSONArray paramsArray, Number type, Number gameID) {
		log.info("inside handlePush");
		String[] teams = getGamefromID(gameID);
		String message = null;
		// Goal
		log.info("Value: " + type.intValue());
		log.info("ParamsArray: " + paramsArray);

		// red card
		if (type.intValue() == 12) {
			String TeamNum = (String) ((JSONObject) paramsArray.get(0))
					.get("Value");
			String time = (String) ((JSONObject) paramsArray.get(1))
					.get("Value");
			String player = (String) ((JSONObject) paramsArray.get(2))
					.get("Value");

			message = "RED CARD<br>(" + time + "') " + player + " ("
					+ teams[Integer.parseInt(TeamNum)] + ")";
		}
		// Halftime
		if (type.intValue() == 9) {
			String state = (String) ((JSONObject) paramsArray.get(0))
					.get("Value");
			if (state.equalsIgnoreCase("Halftime"))
			{
				message = state + "<br>" + teams[1] + " " + teams[4] + " - "
						+ teams[5] + " " + teams[2];
				getResults(String.valueOf(gameID.intValue()),"(Half Time)");
				message=null;
			}
		}
		if (type.intValue() == 32) {

			message = "MATCH STARTED" + "<br>" + teams[1] + " vs " + teams[2];
		}
		if (type.intValue() == 33) {

			message = "MATCH FINISHED" + "<br>" + teams[1] + " " + teams[4]
					+ " - " + teams[5] + " " + teams[2];
			getResults(String.valueOf(gameID.intValue()),"(Full Time)");
			message=null;
		}
		log.info(message);
		if (message != null) {
			message += "<br>(" + teams[3] + ")";
			sendMessage(message);
		}

	}

	public void sendMessage(String message) {
		try {
			log.info("inside sendMessage........");

			//using twilio sms
			new twilio_sms("+919620950489",message);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String[] getGamefromID(Number gameID) {
		String[] gameFound = null;
		for (String[] game : games) {
			log.info("Game: " + game[1] + " - " + game[2]);
			if (Integer.parseInt(game[0]) == gameID.intValue()) {
				gameFound = game;
				break;
			}
		}
		return gameFound;

	}

	private static String getCompetitionfromID(Number compID) {
		String compFound = null;
		 log.info("inside getCompetitionfromID");
		for (String comp[] : competitions) {
			 log.info("Competition: " + comp[1]);
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

	public static String[][] getGames(String url) throws FileNotFoundException,
			IOException {
		JSONParser parser = new JSONParser();
		String[][] activeGames = null;
		try {
			String jsonString = readUrl(url);
			Object obj = parser.parse(jsonString);

			JSONObject jsonObject = (JSONObject) obj;

			jsonObject.get("LastUpdateID");
			JSONArray gamesArray = (JSONArray) jsonObject.get("Games");
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
				String[] gameInfo = new String[7];
				JSONObject gamesJson = (JSONObject) game;
				Number gameID = (Number) gamesJson.get("ID");
				Number compID = (Number) gamesJson.get("Comp");
				gameInfo[0] = gameID.toString();
				JSONArray compsArray = (JSONArray) gamesJson.get("Comps");
				JSONArray scores = (JSONArray) gamesJson.get("Scrs");
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

				String goals = "";
				if (eventsArray != null)
					for (Object event : eventsArray) {
						JSONObject eventsJson = (JSONObject) event;

						// System.out.println(eventsJson);
						Number type = (Number) eventsJson.get("Type");
						// Goal
						if (type.intValue() == 0) {
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
							String player = (String) eventsJson.get("Player");
							String time = String.valueOf(((Number) eventsJson
									.get("GT")).intValue());
							Number comp = (Number) eventsJson.get("Comp");

							String team = "";
							team = gameInfo[comp.intValue()];

							goals += "" + time + "' - " + player
									+ " (RED CARD - " + team + ")<br>";
						}
					}
				// System.out.println(goals);
				gameInfo[6] = goals;
				System.out.println((Arrays.toString(gameInfo)));
				activeGames[gameNum++] = gameInfo;

			}

			// games=jsonString;

		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println(Arrays.toString(activeGames));
		return activeGames;

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