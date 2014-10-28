package com.quest.compatible;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Text;

import edu.stanford.nlp.tagger.maxent.MaxentTagger;


/**
 *  This application attempts to rank the similarity of people on facebook 
 *  based on the below data : 
 *  <ol>
 *   <li>Music - this is the music a person has liked on facebook - currently this has a weight of 35%</li>
 *   <li>Statuses - these are status updates made by an individual on the social network, currently this has a weight of 35%</li>
 *   <li>Likes - this is content an individual likes on the social network - this accounts for 20%</li>
 *   <li>Movies - these are the movies an individual has already watched - this accounts for 5%</li>
 *   <li>Books - these are the books a user likes or has read - this accounts for 5%</li>
 *  </ol>
 *  <p>
 *  The algorithm employed shifts the weight of a given area based on how much data or content we have about that area,
 *  for example if we are comparing two users based on their music and realise that user A has very little data on music we
 *  will shift part of the music weight to an area such as statuses in order to get a fair comparison, similarly if we find that
 *  we still dont have enough data on statuses we will shift the weight to likes and so on...
 *  </p>
 *  <p>
 *  For the music,likes,movies and books we simply go through the list of the current users music for example (me) and 
 *  compare one by one to the friends music, we then count how many bands or artists in the friends lists are similar to the current
 *  user based on this number of similar bands and artists we calculate the similarity this way :
 *  </p>
 *  <p>Taking <code>s<code> to represent the number of similar items between two users, then the percentage similarity is given by:
 *    <code>(2s*s/(t1+t2) + 2s/(t1+t2) )*weight </code> if the value of the formula is greater than 1 we round it down to 1.
 *  <p>
 *  For the statuses we use this formula to calculate the similarity. Remember two users to be compared each have a list of words
 *  </p>
 *  <p>Taking <code>k</code> to represent the weight assigned to statuses which as shown above is 20%</p>
 *  <p>Taking <code>a</code> to represent the larger frequency of a given word between two users and <code>b</code> the smaller frequency of a given word between two users</p>
 *  <p>Taking <code>t1</code> to represent the total number of words for one user and <code>t2</code> to represent the total number of words for the other user</p>
 *  <p>The formula is <code>kb(a+b)/(a(t1+t2))</code></p>
*/

@SuppressWarnings("serial")
public class CompatibilityMatcherServlet extends HttpServlet {
	private static MaxentTagger tagger=new MaxentTagger("models/english-left3words-distsim.tagger");
	
	private static String FB_URL = "https://graph.facebook.com";
	
	private static double NORMALIZE_FACTOR=5;
	
	//------------------------------------Weights
	
	private static int MUSIC_WEIGHT = 35;
	
	private static int STATUSES_WEIGHT = 35;
	
	private static int MOVIES_WEIGHT = 5;
	
	private static int BOOKS_WEIGHT = 5;
	
	private static int LIKES_WEIGHT = 20;
	
	//------------------------------------------
	
	//-----------------------------------------Limits
	
	private static int MUSIC_LIMIT = 500;
	
	private static int STATUSES_LIMIT = 100;
	
	private static int MOVIES_LIMIT = 500;
	
	private static int BOOKS_LIMIT = 500;
	
	private static int LIKES_LIMIT = 500;
	
	//-----------------------------------------
	
	private static int FRIENDS_PER_REQUEST = 10;
	
	private static DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
	
	public void doGet(HttpServletRequest req, HttpServletResponse resp)throws IOException {
		processRequest(req,resp);
	}
	
	public void doPost(HttpServletRequest req, HttpServletResponse resp)throws IOException {
		processRequest(req,resp);
	}
	
	protected void processRequest(HttpServletRequest req, HttpServletResponse resp){
		   try{
			   resp.setContentType("text/html;charset=UTF-8");
			   String json = req.getParameter("json");
			   if(json==null){
				   return;
				}
			   JSONObject obj=new JSONObject(json);
			   JSONObject headers = obj.optJSONObject("request_header");
			   String msg=headers.optString("request_msg");
			   JSONObject requestData=(JSONObject)obj.optJSONObject("request_object");;
			   if(msg.equals("access_token")){
				   accessToken(requestData,resp);
		        }
		       else if(msg.equals("share_post")){
		           postShared(requestData,resp);
		       }
	          
		   }
		   catch(Exception e){
			   Logger.getLogger(CompatibilityMatcherServlet.class.getName()).log(Level.SEVERE, e.toString());  
		   }
	}
	
 private void postShared(JSONObject requestData,HttpServletResponse resp){
	  String currentUserName = requestData.optString("current_user_name");
	  String currentUserId = requestData.optString("current_user_id");
	  Entity share=new Entity("Share");
	  share.setProperty("requestor_id", currentUserId);
	  share.setProperty("requestor_name",currentUserName);
	  share.setProperty("timestamp",System.currentTimeMillis());
	  datastore.put(share);
	  toClient("success",resp);
 }
	
 private void saveUserInfo(JSONObject names,JSONArray ids, String currentUserName,String currentUserId,JSONObject percentages){	
    Entity en=new Entity("User");
    en.setProperty("requestor_name", currentUserName);
    en.setProperty("requestor_id", currentUserId);
    en.setProperty("timestamp",System.currentTimeMillis());
    en.setProperty("friends_analyzed", names.toString());
    en.setProperty("friends_percentages",new Text(percentages.toString()));
    datastore.put(en);	
 }
	
 private void accessToken(JSONObject requestData,HttpServletResponse resp){
    try{
	  String accessToken = requestData.optString("access_token");
	  JSONArray ids = requestData.optJSONArray("friend_ids");
	  JSONObject names = requestData.optJSONObject("friend_names");
	  String currentUserName = requestData.optString("current_user_name");
	  String currentUserId = requestData.optString("current_user_id");
	  JSONObject allData = new JSONObject();
	  int length = ids.length() > FRIENDS_PER_REQUEST ? FRIENDS_PER_REQUEST+1 : ids.length();
	  for(int x=0; x<length; x++){
		  String friendId = ids.optString(x);
		  String statusUrl = FB_URL +"/"+friendId+"/statuses";  
		  String musicUrl = FB_URL +"/"+friendId+"/music"; 
		  String movieUrl = FB_URL +"/"+friendId+"/movies"; 
		  String likeUrl = FB_URL +"/"+friendId+"/likes"; 
		  String bookUrl = FB_URL +"/"+friendId+"/books"; 
		  JSONObject statuses=sendRemoteRequest(statusUrl,accessToken,STATUSES_LIMIT);
		  JSONObject music=sendRemoteRequest(musicUrl,accessToken,MUSIC_LIMIT);
		  JSONObject movies=sendRemoteRequest(movieUrl,accessToken,MOVIES_LIMIT);
		  JSONObject likes=sendRemoteRequest(likeUrl,accessToken,LIKES_LIMIT);
		  JSONObject books=sendRemoteRequest(bookUrl,accessToken,BOOKS_LIMIT);
		  allData.put(friendId,new JSONObject());
		  allData.optJSONObject(friendId).put("statuses", statuses.optJSONArray("data"));
		  allData.optJSONObject(friendId).put("music", music.optJSONArray("data"));
		  allData.optJSONObject(friendId).put("movies", movies.optJSONArray("data"));
		  allData.optJSONObject(friendId).put("likes", likes.optJSONArray("data"));
		  allData.optJSONObject(friendId).put("books", books.optJSONArray("data"));
		  allData.optJSONObject(friendId).put("id", friendId);
	  }
	  JSONObject percentages = analyzeFBFriends(allData);
	  saveUserInfo(names,ids,currentUserName,currentUserId,percentages.optJSONObject("percentages"));
	  toClient(percentages,resp);
    }
    catch(Exception e){
      Logger.getLogger(CompatibilityMatcherServlet.class.getName()).log(Level.SEVERE, null, e);
      toClient("fail",resp);
    }
	}
	
    private static void log(Object msg, Level level){
    	Logger.getLogger(CompatibilityMatcherServlet.class.getName()).log(level,msg.toString());
    }
	private static JSONObject sendRemoteRequest(String remoteUrl,String accessToken,Integer limit){
        try {
            remoteUrl=remoteUrl+"?"+URLEncoder.encode("access_token", "UTF-8") + "=" + URLEncoder.encode(accessToken, "UTF-8");
            remoteUrl=remoteUrl+"&"+URLEncoder.encode("app_id", "UTF-8") + "=" + URLEncoder.encode("137400156430331", "UTF-8");
            remoteUrl =remoteUrl+"&"+URLEncoder.encode("limit", "UTF-8") + "=" + URLEncoder.encode(limit.toString(), "UTF-8");
            URL url = new URL(remoteUrl);
            URLConnection conn = url.openConnection();
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(0); 
            HttpURLConnection httpConn = (HttpURLConnection) conn;
            int responseCode = httpConn.getResponseCode();
            BufferedReader reader;
            if(responseCode == 200) {
                reader = new BufferedReader(new InputStreamReader(httpConn.getInputStream()));
                String inputLine = reader.readLine();
                reader.close();
                return new JSONObject(inputLine);
            } else {
               return null;
            }
        } catch (Exception ex) {
            Logger.getLogger(CompatibilityMatcherServlet.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
  }
	

  private void shiftWeight(HashMap<String,?> analysis,JSONObject weights, String previousKey,String nextKey){
	 try{
	  for(String friendId : analysis.keySet()){
		  Object [] data = (Object[]) analysis.get(friendId);
		  Double larger;
		  Double smaller;
		  Double friendTotal = (Double) data[1];
		  Double meTotal = (Double) data[2];
		  larger = friendTotal > meTotal ? friendTotal : meTotal;
		  smaller = friendTotal < meTotal ? friendTotal : meTotal;
		  if(larger==0 || smaller==0){
			  larger = 1.0;
			  smaller = 1.0;
		  }
		 if((larger/smaller)>NORMALIZE_FACTOR){
			//get the previous weight and inflate it by ((NORMALIZE_FACTOR-1)/(NORMALIZE_FACTOR))*previousWeight 
			 Double previousWeight = weights.optJSONObject(friendId).optDouble(previousKey);
			 Double nextWeight = weights.optJSONObject(friendId).optDouble(nextKey);
			 Double newWeight = (((NORMALIZE_FACTOR-1)/(NORMALIZE_FACTOR))*previousWeight)+nextWeight;
			 weights.optJSONObject(friendId).put(nextKey, newWeight);
		 }
	  }
	 }
	 catch(Exception e){
		 
	 }
  }
	
  private JSONObject analyzeFBFriends(JSONObject data){
	  try{
	   Iterator<String> iter=data.keys();
	   JSONObject compareTo = null;
	   ArrayList<JSONObject> allFriendData=new ArrayList<>();
	   JSONObject percentages=new JSONObject();
	   JSONObject favWords = new JSONObject();
	   JSONObject weights = new JSONObject();
	   JSONObject wordIntersect = new JSONObject();
	   JSONObject musicIntersect = new JSONObject();
	   JSONObject likeIntersect = new JSONObject();
	   JSONObject movieIntersect = new JSONObject();
	   JSONObject bookIntersect = new JSONObject();
	   while(iter.hasNext()){
		  String fbID=iter.next();
		  JSONObject singleFriendData=data.optJSONObject(fbID);
		  if(fbID.equals("me")){
			 compareTo =singleFriendData;
		  }
		  else{
			 allFriendData.add(singleFriendData);
			 JSONObject friendWeight = new JSONObject();
			 friendWeight.put("statuses",STATUSES_WEIGHT);
			 friendWeight.put("music",MUSIC_WEIGHT);
			 friendWeight.put("likes",LIKES_WEIGHT);
			 friendWeight.put("movies",MOVIES_WEIGHT);
			 friendWeight.put("books",BOOKS_WEIGHT);
			 weights.put(fbID, friendWeight);
		  }
	   }
	     HashMap<String,Object[]> statusAnalysis = analyzeStatuses(compareTo, allFriendData,weights);
	     shiftWeight(statusAnalysis,weights,"statuses","music");
		 HashMap<String,Object[]> musicAnalysis = analyzeMusic(compareTo, allFriendData,weights);
		 shiftWeight(musicAnalysis,weights,"music","likes");
		 HashMap<String,Object[]> likeAnalysis = analyzeLikes(compareTo, allFriendData,weights);
		 shiftWeight(likeAnalysis,weights,"likes","movies");
		 HashMap<String,Object[]> movieAnalysis = analyzeMovies(compareTo, allFriendData,weights);
		 shiftWeight(movieAnalysis,weights,"movies","books");
		 HashMap<String,Object[]> bookAnalysis =  analyzeBooks(compareTo, allFriendData,weights);
		 for(String userId : musicAnalysis.keySet() ){
		  try{
			double musicVal = (Double)musicAnalysis.get(userId)[0];
			Double statusVal = (Double)statusAnalysis.get(userId)[0];
			double likeVal = (Double)likeAnalysis.get(userId)[0];
			double movieVal = (Double)movieAnalysis.get(userId)[0];
			double bookVal = (Double)bookAnalysis.get(userId)[0];
			double total = musicVal+statusVal+likeVal+movieVal+bookVal;
			double normalizedTotal = total * NORMALIZE_FACTOR;
			if(normalizedTotal>100){
			  normalizedTotal = 100;
			}
			if(total == 0){
				total = 1;
			}
			percentages.put(userId,new JSONObject());
			percentages.optJSONObject(userId).put("total",normalizedTotal);
			percentages.optJSONObject(userId).put("music",(musicVal/total)*normalizedTotal);
			percentages.optJSONObject(userId).put("statuses",(statusVal/total)*normalizedTotal);
			percentages.optJSONObject(userId).put("likes",(likeVal/total)*normalizedTotal);
			percentages.optJSONObject(userId).put("movies",(movieVal/total)*normalizedTotal);
			percentages.optJSONObject(userId).put("books",(bookVal/total)*normalizedTotal);
			
			wordIntersect.put(userId, statusAnalysis.get(userId)[5]);
			musicIntersect.put(userId,musicAnalysis.get(userId)[3]);
			likeIntersect.put(userId,likeAnalysis.get(userId)[3]);
			movieIntersect.put(userId,movieAnalysis.get(userId)[3]);
			bookIntersect.put(userId,bookAnalysis.get(userId)[3]);
			
			favWords.put(userId, statusAnalysis.get(userId)[4]);
			
		   }
		   catch(Exception e){
			 e.printStackTrace();
		   }
		 }
		 JSONObject all = new JSONObject();
		 all.put("percentages",percentages);
		 all.put("fav_words",favWords);
		 all.put("word_intersect", wordIntersect);
		 all.put("music_intersect",musicIntersect);
		 all.put("like_intersect",likeIntersect);
		 all.put("movie_intersect",movieIntersect);
		 all.put("book_intersect",bookIntersect);
	     return all;
	  }
	  catch(Exception e){
		e.printStackTrace();
		return null;
	  }
	}
	
 // //statusAnalysis.put(friendId,new Object[]{percentSimilarity,friendWordTotal.doubleValue(),myWordsTotal.doubleValue()});
  private Double calculateExtraSimilarity(String itemName,Double myWordsTotal,Double weight,String friendId,HashMap<String,Object[]> statusAnalysis){
	 StringTokenizer st=new StringTokenizer(itemName, " ");
	 Object[] currentUser = statusAnalysis.get(friendId);
	 HashMap<String,Integer> meWordCloud = (HashMap<String,Integer>)currentUser[3];
	 Double friendWordTotal = (Double)currentUser[1];
	 Double totalSimilarity = 0.0;
	 while(st.hasMoreTokens()){
		String word = st.nextToken();
		Integer smallerFreq = st.countTokens();
		Integer largerFreq = meWordCloud.get(word);
		if(largerFreq == null){
		   largerFreq = 1;
		   smallerFreq = 0;
		}
		largerFreq = largerFreq > smallerFreq ? largerFreq : smallerFreq;
		smallerFreq = largerFreq < smallerFreq ? largerFreq : smallerFreq;
		Double similarity = (weight.doubleValue()*smallerFreq.doubleValue()*(largerFreq.doubleValue()+smallerFreq.doubleValue()))/(largerFreq.doubleValue()*(friendWordTotal+myWordsTotal));
		totalSimilarity = totalSimilarity+similarity;
	 }
	 return totalSimilarity;
  }
	
  private HashMap<String,Object[]> analyzeMusic(JSONObject me,ArrayList<JSONObject> friendData,JSONObject weights){
	try{
	  JSONArray meMusic=me.optJSONArray("music");
	  HashMap<String,Object[]> musicAnalysis=new HashMap<String,Object[]>();
	  ArrayList<String> meMusicNames=new ArrayList<>();
	  Integer meTotal=meMusic.length();
	  for(int z=0; z<meTotal; z++){
		  meMusicNames.add(meMusic.optJSONObject(z).optString("name"));
	  }
	  for(int x=0; x<friendData.size();x++){
		JSONArray friendMusic=friendData.get(x).optJSONArray("music");
		JSONArray musicIntersect = new JSONArray();
		String friendId=friendData.get(x).optString("id");
		Integer friendSimilarCount=0;
		Integer friendTotal=friendMusic.length();
		Double percent=0.0;
		for(int y=0; y<friendTotal; y++){
		   JSONObject fMusic=friendMusic.optJSONObject(y);
		   String friendBandName=fMusic.optString("name");
		   int index=meMusicNames.indexOf(friendBandName);
		   if(index>-1){
			  friendSimilarCount++; //we count how many bands this friend has in common with me
			  musicIntersect.put(friendBandName);
		   }
		   
		}
		//(2s*s/(t1+t2)(t1+t2) + 2s/(t1+t2) )*weight
		double val=( (2*friendSimilarCount.doubleValue()*friendSimilarCount.doubleValue())/((meTotal.doubleValue()+friendTotal.doubleValue())*(meTotal.doubleValue()+friendTotal.doubleValue())) + 2*friendSimilarCount.doubleValue()/(meTotal.doubleValue()+friendTotal.doubleValue()));  
		val = val > 1 ? 1 : val;
		Double weight = weights.optJSONObject(friendId).optDouble("music");
		percent=percent+val*weight.doubleValue();
		musicAnalysis.put(friendId,new Object[]{percent,friendTotal.doubleValue(),meTotal.doubleValue(),musicIntersect});
	  }
	  return musicAnalysis;
	 }
	 catch(Exception e){
		return null;
	 }
	}
  
  private HashMap<String,Object[]> analyzeLikes(JSONObject me,ArrayList<JSONObject> friendData,JSONObject weights){
	try{
	  JSONArray meLikes=me.optJSONArray("likes");
	  HashMap<String,Object[]> likeAnalysis=new HashMap<String,Object[]>();
	  ArrayList<String> meLikeNames=new ArrayList<>();
	  Integer meTotal=meLikes.length();
	  for(int z=0; z<meTotal; z++){
		  meLikeNames.add(meLikes.optJSONObject(z).optString("name"));
	  }
	  for(int x=0; x<friendData.size();x++){
		JSONArray friendLikes=friendData.get(x).optJSONArray("likes");
		String friendId=friendData.get(x).optString("id");
		JSONArray likeIntersect = new JSONArray();
		Integer friendSimilarCount=0;
		Integer friendTotal=friendLikes.length();
		Double percent=0.0;
		for(int y=0; y<friendTotal; y++){
		   JSONObject fLike=friendLikes.optJSONObject(y);
		   String friendLikeName=fLike.optString("name");
		   int index=meLikeNames.indexOf(friendLikeName);
		   if(index>-1){
			 friendSimilarCount++; //we count how many likes this friend has in common with me
			 likeIntersect.put(friendLikeName);
		   }
		}
		//(2s*s/(t1+t2)(t1+t2) + 2s/(t1+t2) )*weight
	    double val=( (2*friendSimilarCount.doubleValue()*friendSimilarCount.doubleValue())/((meTotal.doubleValue()+friendTotal.doubleValue())*(meTotal.doubleValue()+friendTotal.doubleValue())) + 2*friendSimilarCount.doubleValue()/(meTotal.doubleValue()+friendTotal.doubleValue()));  
		val = val > 1 ? 1 : val;
		Double weight = weights.optJSONObject(friendId).optDouble("likes");
		percent=percent+val*weight.doubleValue();
		likeAnalysis.put(friendId,new Object[]{percent,friendTotal.doubleValue(),meTotal.doubleValue(),likeIntersect});
	  	}
	  	return likeAnalysis;
	}
	catch(Exception e){
		return null;
	}
	}
  
  private HashMap<String,Object[]> analyzeBooks(JSONObject me,ArrayList<JSONObject> friendData,JSONObject weights){
	 try{
	  JSONArray meBooks=me.optJSONArray("books");
	  HashMap<String,Object[]> bookAnalysis=new HashMap<String,Object[]>();
	  ArrayList<String> meBookNames=new ArrayList<>();
	  Integer meTotal=meBooks.length();
	  for(int z=0; z<meTotal; z++){
		  meBookNames.add(meBooks.optJSONObject(z).optString("name"));
	  }
	  for(int x=0; x<friendData.size();x++){
		JSONArray friendBooks=friendData.get(x).optJSONArray("books");
		JSONArray bookIntersect = new JSONArray();
		String friendId=friendData.get(x).optString("id");
		Integer friendSimilarCount=0;
		Integer friendTotal=friendBooks.length();
		Double percent=0.0;
		for(int y=0; y<friendTotal; y++){
		   JSONObject fBook=friendBooks.optJSONObject(y);
		   String friendBookName=fBook.optString("name");
		   int index=meBookNames.indexOf(friendBookName);
		   if(index>-1){
			 friendSimilarCount++; //we count how many bands this friend has in common with me
			 bookIntersect.put(friendBookName);
		   }
		}
		//(2s*s/(t1+t2)(t1+t2) + 2s/(t1+t2) )*weight
	    double val=( (2*friendSimilarCount.doubleValue()*friendSimilarCount.doubleValue())/((meTotal.doubleValue()+friendTotal.doubleValue())*(meTotal.doubleValue()+friendTotal.doubleValue())) + 2*friendSimilarCount.doubleValue()/(meTotal.doubleValue()+friendTotal.doubleValue()));  
		val = val > 1 ? 1 : val;
		Double weight = weights.optJSONObject(friendId).optDouble("books");
		percent=percent+val*weight.doubleValue();
		bookAnalysis.put(friendId,new Object[]{percent,friendTotal.doubleValue(),meTotal.doubleValue(),bookIntersect});
	  }
	  return bookAnalysis;
	 }
	 catch(Exception e){
		return null;
	 }
	}
  
  public static void main(String [] args){
	  Integer friendSimilarCount=10;
	  Integer meTotal = 100;
	  Integer friendTotal = 90;
	  //(2s*s/(t1+t2)(t1+t2) + 2s/(t1+t2) )*weight
	  double val=( (2*friendSimilarCount.doubleValue()*friendSimilarCount.doubleValue())/((meTotal.doubleValue()+friendTotal.doubleValue())*(meTotal.doubleValue()+friendTotal.doubleValue())) + 2*friendSimilarCount.doubleValue()/(meTotal.doubleValue()+friendTotal.doubleValue()));  
	  System.out.println(val);
  }
  
  private HashMap<String,Object[]> analyzeMovies(JSONObject me,ArrayList<JSONObject> friendData,JSONObject weights){
	try{
	  JSONArray meMovies=me.optJSONArray("movies");
	  HashMap<String,Object[]> movieAnalysis=new HashMap<String,Object[]>();
	  ArrayList<String> meMovieNames=new ArrayList<>();
	  Integer meTotal=meMovies.length();
	  for(int z=0; z<meTotal; z++){
		  meMovieNames.add(meMovies.optJSONObject(z).optString("name"));
	  }
	  for(int x=0; x<friendData.size();x++){
		JSONArray friendMovies=friendData.get(x).optJSONArray("movies");
		JSONArray movieIntersect = new JSONArray();
		String friendId=friendData.get(x).optString("id");
		Integer friendSimilarCount=0;
		Integer friendTotal=friendMovies.length();
		Double percent=0.0;
		for(int y=0; y<friendTotal; y++){
		   JSONObject fMovie=friendMovies.optJSONObject(y);
		   String friendMovieName=fMovie.optString("name");
		   int index=meMovieNames.indexOf(friendMovieName);
		   if(index>-1){
			 friendSimilarCount++; //we count how many movies this friend has in common with me
			 movieIntersect.put(friendMovieName);
		   }
		}
	
		//(2s*s/(t1+t2)(t1+t2) + 2s/(t1+t2) )*weight
		double val=( (2*friendSimilarCount.doubleValue()*friendSimilarCount.doubleValue())/((meTotal.doubleValue()+friendTotal.doubleValue())*(meTotal.doubleValue()+friendTotal.doubleValue())) + 2*friendSimilarCount.doubleValue()/(meTotal.doubleValue()+friendTotal.doubleValue()));  
		val = val > 1 ? 1.0 : val;
		Double weight = weights.optJSONObject(friendId).optDouble("movies");
		percent=percent+val*weight.doubleValue();
		movieAnalysis.put(friendId,new Object[]{percent,friendTotal.doubleValue(),meTotal.doubleValue(),movieIntersect});
	  }
	  return movieAnalysis;
	}
	catch(Exception e){
		return null;
	}
	}
  
  private HashMap<String,Object[]> analyzeStatuses(JSONObject me,ArrayList<JSONObject> friendData,JSONObject weights){
	try{
	 Object[] meWordCloud=getIndividualWordCloud(me);
	 ArrayList<Object[]> friendWordClouds=new ArrayList<Object[]>();
	 HashMap<String,Object[]> statusAnalysis=new HashMap<String,Object[]>();
	 JSONObject wordIntersect = new JSONObject();
	 
	 for(int x=0; x<friendData.size(); x++ ){
		Object[] friendWordCloud = getIndividualWordCloud(friendData.get(x));
		friendWordClouds.add(friendWordCloud);
	 }
	 
	 HashMap<String,Integer> myWordCloud = (HashMap<String, Integer>) meWordCloud[1];
	 Integer myWordsTotal = (int) meWordCloud[2];
	 for(Object word : myWordCloud.keySet()){
		String theWord = word.toString();
		Integer freq = myWordCloud.get(theWord); // the frequency of the word in the users posts
		for(Object [] friendWords : friendWordClouds){
		  String friendId=(String) friendWords[0];
		  String favWord = (String)friendWords[3];
		  HashMap<String,Integer> friendWordCloud = (HashMap<String, Integer>) friendWords[1];
		  Integer friendWordTotal = (Integer) friendWords[2];
		  Integer friendFreq = friendWordCloud.get(theWord);
		  if(friendFreq==null){
			 friendFreq=0;
		  }
		  else{
			  //update : we need to note the similar words used by the friend and me
			  if(theWord.length() > 2){ //words start from 3 letters
				  wordIntersect.accumulate(friendId, theWord);  
			  }
		  }
		  //kb(a+b)/(a(t1+t2))	
		  Integer largerFreq = friendFreq > freq ? friendFreq : freq;
		  Integer smallerFreq = friendFreq < freq ? friendFreq : freq;
		  Double weight = weights.optJSONObject(friendId).optDouble("statuses");
		  Double similarity = (weight.doubleValue()*smallerFreq.doubleValue()*(friendFreq.doubleValue()+freq.doubleValue()))/(largerFreq.doubleValue()*(friendWordTotal.doubleValue()+myWordsTotal.doubleValue()));
		  Object [] existingVals = statusAnalysis.get(friendId);
		  Double percentSimilarity = existingVals == null ? 0.0 : (Double)existingVals[0];
		  percentSimilarity=percentSimilarity+similarity;
		  statusAnalysis.put(friendId,new Object[]{percentSimilarity,friendWordTotal.doubleValue(),myWordsTotal.doubleValue(),myWordCloud,favWord,wordIntersect.optJSONArray(friendId)});
		}
	 }
	 return statusAnalysis;
	}
	catch(Exception e){
      return null;
	}
  }
  

  
  private Object[] getIndividualWordCloud(JSONObject me){
	     JSONArray meStatuses=me.optJSONArray("statuses");
	     String friendId = me.optString("id");
		 int meTotal=meStatuses.length();
		 HashMap<String,Integer> meWordCloud=new HashMap<String,Integer>();
		 int totalNounCount=0;
		 int max =0;
		 String favoriteWord = "";
		 for(int z=0; z<meTotal; z++){
			String meStatus= meStatuses.optJSONObject(z).optString("message");
			ArrayList<String> nouns=getNouns(meStatus);
			totalNounCount=totalNounCount+nouns.size();
			for(int y=0; y<nouns.size(); y++){
				String theNoun=nouns.get(y);
				Integer count=meWordCloud.get(theNoun);
				if(count==null){
					meWordCloud.put(theNoun, 1);
				}
				else{
					int currentCount=meWordCloud.get(theNoun);
					currentCount++;
					meWordCloud.put(theNoun,currentCount);
				}
				int finalCount = meWordCloud.get(theNoun);
				if(finalCount > max && theNoun.length()>2){ //we want words only not letters
				   max = finalCount; 
				   favoriteWord = theNoun;
				}
			}
		 }
	  return new Object[]{friendId,meWordCloud,totalNounCount,favoriteWord};
  }
  
  
	
	  public static ArrayList<String> getNouns(String sentence){
	        String tStr  = tagger.tagString(sentence); //out of memory error
	        Pattern ptrn = Pattern.compile("\\w+_NN[A-Z]*");
	        Matcher m = ptrn.matcher(tStr);
	        //System.out.println(tStr);
	        ArrayList<String> results=new ArrayList<String>();
		    while (m.find()){
		      results.add(m.group().substring(0, m.group().lastIndexOf('_')));			
		   }
	       return results;
	    }
	
	 private void toClient(Object response,HttpServletResponse resp){
         try {
             JSONObject toClient=new JSONObject();
             toClient.put("data", response);
             PrintWriter writer = resp.getWriter();
             writer.println(toClient);
         } catch (Exception ex) {
            System.out.println(ex);
       }
  }
		
}
