import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;

public class BlackboardRetriever extends Retriever{

	Networker net;
	
	public LinkedList<Assignment> retrieve() throws InterruptedException, IOException, URISyntaxException {
		net = getNetworker();
		login();
		return getAssns(getAllClassIDs());
	}

	private LinkedList<Assignment> getAssns(LinkedList<String> classIds) throws URISyntaxException, IOException, InterruptedException {
		LinkedList<Assignment> assns = new LinkedList<>();
		String nextUrl;

		//threads here
		/*for(String id : classIds){
			nextUrl = "https://blackboard.sc.edu/webapps/blackboard/execute/modulepage/view?course_id=" + id + "&cmp_tab_id=_564695_1&mode=view";
			buildCookieRequest(nextUrl);
			String classHtml = stdResponseBodyWithSend();

			if(classHtml.contains("Assignments")){

				String link = "https://blackboard.sc.edu" + extractAssnsLink(classHtml, "/webapps/blackboard/content/listContent.jsp\\?course_id=" + id + "&content_id=[^&]+&mode=reset(?=[\\s\\S]{0,100}Assignments)", "Link To Assns Page", "/webapps/blackboard/content/listContent.jsp?course_id=" + id, "&mode=reset");
				assns.addAll(getAssns(id, link));

			}
		}*/
		
		for(String id : classIds){
			new ClassThread(assns, id, net.getCookie()).start();
		}

		return assns;
	}
	
	private class ClassThread extends Thread {
		String id;
		LinkedList<Assignment> assns;
		Networker networker;
		
		public ClassThread(LinkedList<Assignment> assns, String id) {
			init(assns,id);
		}
		
		public ClassThread(LinkedList<Assignment> assns, String id, String cookie) {
			init(assns,id);
			networker.setCookie(cookie);
		}
		
		private void init(LinkedList<Assignment> assns, String id) {
			this.id = id;
			this.assns = assns;
			this.networker = new Networker();
		}
		
		public void run() {
			try {
				String nextUrl = "https://blackboard.sc.edu/webapps/blackboard/execute/modulepage/view?course_id=" + id + "&cmp_tab_id=_564695_1&mode=view";
				networker.buildCookieRequest(nextUrl);
				String classHtml = networker.stdResponseBodyWithSend();
	
				if(classHtml.contains("Assignments")){
	
					String link = "https://blackboard.sc.edu" + extractAssnsLink(classHtml, "/webapps/blackboard/content/listContent.jsp\\?course_id=" + id + "&content_id=[^&]+&mode=reset(?=[\\s\\S]{0,100}Assignments)", "Link To Assns Page", "/webapps/blackboard/content/listContent.jsp?course_id=" + id, "&mode=reset");
					assns.addAll(getAssns(networker, id, link));
	
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	private class AssnsThread extends Thread {
		
		LinkedList<Assignment> assns;
		String type, subjectID, contentID, responseHTML;
		Networker networker;
		
		public AssnsThread(LinkedList<Assignment> assns, String type, String subjectID, String contentID, String responseHTML, String cookie) {
			this.assns = assns;
			this.type = type;
			this.subjectID = subjectID;
			this.contentID = contentID;
			this.responseHTML = responseHTML;
			networker = new Networker();
			networker.setCookie(cookie);
		}
		
		public void run() {
			try {
				switch (type) {
					case "Assignment": assns.add(parseAssn(networker, subjectID, contentID)); break;
					case "Test" : assns.add(parseTest(networker, subjectID, contentID)); break;
					case "Content Folder" : assns.addAll(getAssns(networker, subjectID, "https://blackboard.sc.edu" + fetchValue(responseHTML, "/webapps/blackboard/content/listContent.jsp\\?course_id=\\S{0,15}&content_id=" + contentID))); break; //todo add link
					case "McGraw-Hill Assignment Dynamic": assns.add(parseMGH(contentID, responseHTML)); break;
					case "Survey": assns.add(parseSurvery(subjectID)); break;
					default: parseOther(type);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
	}

	private LinkedList<Assignment> getAssns(Networker networker, String subjectID, String link) throws URISyntaxException, IOException, InterruptedException {
		networker.buildCookieRequest(link);
		String responseHTML = networker.stdResponseBodyWithSend();

		LinkedList<Assignment> assns = new LinkedList<>();

		//content types from clearfix liItem read
		LinkedList<String> contentTypes = extractValues(responseHTML, "clearfix liItem read", "(?<=<li[\\s\\S]{0,100}img alt=\")[^\"]+(?=\" src=\")", "Assn Page Content Types");
		//content ids from item clearfix
		LinkedList<String> contentIDs = extractValues(responseHTML, "item clearfix", "(?<=<li[\\s\\S]{0,100}img alt=\")[^\"]+(?=\" src=\")", "Content IDs");

		int idsLength = contentIDs.size(), typesLength = contentTypes.size();
		if(idsLength == 0 || typesLength == 0) {
			if(idsLength != typesLength)
				System.out.println("Uneven content and id length");
			return assns;
		}


		//threads here
		for(Iterator<String> idIterator = contentIDs.iterator(), typesIterator = contentTypes.iterator(); idIterator.hasNext() && typesIterator.hasNext();){
			String contentID = idIterator.next();
			String type = typesIterator.next();

			/*switch (type) {
				case "Assignment": assns.add(parseAssn(subjectID, contentID)); break;
				case "Test" : assns.add(parseTest(subjectID, contentID)); break;
				case "Content Folder" : assns.addAll(getAssns(subjectID, "https://blackboard.sc.edu" + fetchValue(responseHTML, "/webapps/blackboard/content/listContent.jsp\\?course_id=\\S{0,15}&content_id=" + contentID), net)); break; //todo add link
				case "McGraw-Hill Assignment Dynamic": assns.add(parseMGH(contentID, responseHTML)); break;
				case "Survey": assns.add(parseSurvery(subjectID)); break;
				default: parseOther(type);
			}*/
			String s = networker.getCookie();
			new AssnsThread(assns, type, subjectID, contentID, responseHTML, networker.getCookie()).start();
		}

		return assns;
	}

	private Assignment parseMGH(String contentID, String toParse)  {
//		LinkedList<String> values = fetchValues(toParse, "(?<=" + contentID + "\" ><span style=\"color:#000000;\">)[^<]+|(?<=Due Date: )[^<]+(?=[\\s\\S]{0,200}id=\"" + contentID + ")");

		String assnSubsection = extractTag(toParse, ("li id=\"contentListItem:" + contentID + "\"\tclass=\"clearfix liItem read\""), "li");

		String name = extractTag(assnSubsection, "span style=\"color:#000000;\"", "span");
		String due = extractTag(assnSubsection, "i", "i");
		due = due.substring(due.indexOf(":") + 2);

		return new Assignment(name, due);

//		return null;
	}

	private String[] getAssnNameDate(String toParse){
		String title = extractTag(toParse, "title", "title");
		if(title.startsWith("Review Submission History:"))
			return getAssnNameDateSubmitted(toParse);
		else if(title.startsWith("Upload Assignment:"))
			return getAssnNameDateNormal(toParse);
		else {
			return new String[] {"FAIL", "FAIL"};
		}

	}

	private String[] getAssnNameDateNormal(String toParse){
		//submitted assn values and shit but needs editing

		String due = extractTag(toParse, "div class=\"metaField\" aria-describedby=\"assignMeta2\"", "div");
		String name = extractTag(toParse, "span id=\"crumb_3\"", "span");

		due = due.substring(0,due.indexOf("<")).trim() + " " + extractTag(due, "span class=\"metaSubInfo\"", "span").trim();
		name = name.substring(name.indexOf(":") + 1).trim();
		return new String[] {name, due};
	}

	private String[] getAssnNameDateSubmitted(String toParse){
		String divContents = extractTag(toParse, "div class=\"attempt gradingPanelSection\"", "div");

		char[] divChars = divContents.toCharArray();
		String firstHalf = "", secondHalf = "";
		StringBuilder builder = new StringBuilder();
		int i;
		for(i=0;i<divChars.length;i++){
			builder.append(divChars[i]);
			if(divChars[i] == '\n' && divChars[i+1] == '\n')
				break;
		}
		firstHalf = builder.toString();
		builder.setLength(0);
		for(;i<divChars.length;i++){
			builder.append(divChars[i]);
		}
		secondHalf = builder.toString();

		firstHalf = extractTag(firstHalf, "p", "p");
		secondHalf = extractTag(secondHalf, "p","p");

		return new String[] {firstHalf, secondHalf};
	}

	private Assignment parseAssn(Networker networker, String subjectID, String contentID) throws URISyntaxException, IOException, InterruptedException {
		networker.buildCookieRequest("https://blackboard.sc.edu/webapps/assignment/uploadAssignment?content_id=" + contentID + "&course_id=" + subjectID +"&group_id=&mode=view");

		String toParse = networker.stdResponseBodyWithSend();
		String[] values = getAssnNameDate(toParse);

		return new Assignment(values[0], values[1]);
	}

	private Assignment parseTest(Networker networker, String subjectID, String contentID) throws URISyntaxException, IOException, InterruptedException {
		networker.buildCookieRequest("https://blackboard.sc.edu/webapps/assessment/take/launchAssessment.jsp?content_id=" + contentID + "&course_id=" + subjectID +"&group_id=&mode=view");

		String htmlToParse = networker.stdResponseBodyWithSend();

		char[] htmlChars = htmlToParse.toCharArray();
		char[] firstComparator = "Due Date".toCharArray();
		int linesToGet = 10;
		StringBuilder[] linesFound = new StringBuilder[linesToGet];
		final int[] LINES_TO_PARSE = {5,9};
		int[] findersLowerBounds = {"This Test is due on ".length(), "</li> Click <strong>Begin</strong> to start: ".length()};
		char findersUpperBoundHelper = '.';
		String[] values = new String[2];

		int firstStop = -1;
		firstloop:
		for(int i=0;i<htmlChars.length;i++)
			for(int j=0;j<firstComparator.length;j++) {
				if(htmlChars[i + j] != firstComparator[j])
					break;
				else if(j==firstComparator.length-1) {
					firstStop = i + j + 2;
					break firstloop;
				}
			}

		if(firstStop == -1){
			System.out.println("FAIL FAIL");
			return null;
		}

		for(int i=0;i<linesFound.length;i++)
			linesFound[i] = new StringBuilder();

		char current = ' ';
		for(int i=firstStop, line = 0;line < linesToGet;i++) {
			current = htmlChars[i];
			linesFound[line].append(current);
			if(current == '\n')
				line++;
		}

		for(int i=0, parsed=0;i<linesFound.length;i++)
			if(i == LINES_TO_PARSE[parsed])
				values[parsed++] = linesFound[i].toString();

		for(int i=0;i<values.length;i++) {
			String trimmed = values[i].trim();
			values[i] = trimmed.substring(findersLowerBounds[i], trimmed.indexOf(findersUpperBoundHelper));
		}

		return new Assignment(values[1], values[0]);
	}

	private Assignment parseSurvery(String subjectID) {
		//System.out.println("Survey");

		return new Assignment("Unknown survey name", "Unknown Due");
	}

	private Assignment parseOther(String type){
		//System.out.println(type);

		return new Assignment("Unknown *unsupported name*", "Unknown Due");
	}

	private LinkedList<String> getAllClassIDs() throws URISyntaxException, IOException, InterruptedException {
		String nextUrl = "https://blackboard.sc.edu/learn/api/v1/users/_1710804_1/memberships?expand=course.effectiveAvailability,course.permissions,courseRole&includeCount=true&limit=10000";
		net.buildCookieRequest(nextUrl);
		String jsonToParse = net.stdResponseBodyWithSend();

		LinkedList<String> classLines = splitJson(jsonToParse);
		classLines.removeIf(classLine -> !classLine.contains(getTermName()));

		LinkedList<String> classIDs = new LinkedList<>();
		for(String classLine : classLines){
			String homePageUrlLine = extractValue(classLine, "homePageUrl", "(?<=homePageUrl\":\"/webapps/blackboard/execute/courseMain\\?course_id=)[^\"]+", "Extract IDs");
			classIDs.add(homePageUrlLine.substring(homePageUrlLine.indexOf("=") + 1));
		}

		return classIDs;
	}

	private LinkedList<String> splitJson(String json) {
		char[] jsonChars = json.toCharArray();
		StringBuilder builder = new StringBuilder();
		int braceCount = 0, startIndex = json.indexOf('[') + 1;
		LinkedList<String> splitJson = new LinkedList<>();

		for(int i=startIndex,length=jsonChars.length;i<length;i++){
			if(jsonChars[i] == '{')
				braceCount++;
			else if(jsonChars[i] == '}') {
				braceCount--;
				if(braceCount == 0){
					splitJson.add(builder.toString());
					builder.setLength(0);
				}
			} else if(braceCount > 0)
				builder.append(jsonChars[i]);

		}

		if(splitJson.size() == 0) {
			System.out.println("splitJson resorted to regex");
			return new LinkedList<>(Arrays.asList(json.split("\"role\":\"S\"")));
		}

		return splitJson;
	}

	protected void login() throws URISyntaxException, IOException, InterruptedException {

		String nextUrl = "https://cas.auth.sc.edu/cas/login?service=https%3A%2F%2Fblackboard.sc.edu%2Fwebapps%2Fbb-auth-provider-cas-BB5dd6acf5e22a7%2Fexecute%2FcasLogin%3Fcmd%3Dlogin%26authProviderId%3D_132_1%26redirectUrl%3Dhttps%253A%252F%252Fblackboard.sc.edu%252Fultra%26globalLogoutEnabled%3Dtrue";
		net.buildStdRequest(nextUrl);

		String exec = extractValue(net.stdResponseBodyWithSend(),"execution", "(?<=name=\"execution\" value=\")[^\"]*", "Extract Exec");

		nextUrl = "https://cas.auth.sc.edu/cas/login";
		String LoginPOSTbody = "username=" + USER + "&password=" + PASS + "&execution=" + exec + "&_eventId=submit&geolocation=";
		net.setRequest(net.stdRequestBuilder(nextUrl).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(LoginPOSTbody)).build());

		LinkedList<String> cookies = new LinkedList<>(net.stdResponse().headers().allValues("set-cookie"));

		net.setCookie(cookies.get(0) + ";" + cookies.get(1) + ";" + cookies.get(2));
	}

	private String extractAssnsLink(String toParse, final String REGEX, String name, String... matchContains){
		LinkedList<String> finds = new LinkedList<>();
		char[] htmlAsArray = toParse.toCharArray();
		boolean quoteFound = false;
		String find = "";
		StringBuilder builder = new StringBuilder();

		for(char currentChar : htmlAsArray){
			if(currentChar == '"') {
				if(quoteFound){
					find = builder.toString();
					if(allContained(find, matchContains))
						finds.add(find);

					builder.setLength(0);
				}

				quoteFound = !quoteFound;
			} else if(quoteFound)
				builder.append(currentChar);
		}

		if(finds.get(1).equals("")){
			doDebug(name + " resorted to regex");
			return fetchValue(toParse, REGEX);
		}

		return finds.get(1);
	}

	private String getTermName(){
		Date date = new Date();
		LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		int month = localDate.getMonthValue();
		int year = localDate.getYear();

		if(month >= 8)
			return "Fall " + year;
		else if(month <= 5)
			return "Spring " + year;
		else
			return "Summer " + year;
	}

}
