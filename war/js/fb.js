 	  var selector1;
       
       var friendIds=[];
       
       var loggedIn=false;
       
       var friendNames = {};
       
       var currentUserName;
       
       var currentUserId;
       
       var results;
       
       var theScope='user_status,user_likes,user_actions.videos,user_actions.music,friends_likes,friends_status,friends_actions.music,friends_actions.video'
       
       
       function addUI() {
           var html="<div id='fb_friend_select'></div>"+
           "<input type='button' value='Login To Facebook' onclick='loginFB()' id='fb_login'  class='btn btn-primary' style='margin-right : 5px'>"+
            "<input type='button' value='Add Facebook Friends' onclick='selectFriends()'  class='btn btn-primary'><br><br><br>"+
            "<div id='friend_display_area'></div>";
               $("#modal_body").html(html);
         }
       
       function worksUI() {
           var html="<h3>Permissions</h3><br> <ol><li>Your status updates</li><li>Your friends status updates</li>"+
             "<li>Your likes</li><li>Your friends likes</li><li>Music you like</li><li>Music your friends like</li>"+
             "<li>Videos you like</li><li>Videos your friends like</li></ol>"+
             "<p>NB : Without granting the application the required permissions it will not work</p>"+
            "<h3>Privacy</h3><br><p>We <b>never</b> share your data with anyone</p>"+
            "<h3>How it works</h3><br><p>This application compares you with the <b>Facebook</b> friends you select below and tells you who among them is most like you based on"+
            " your <b>likes</b>, <b>status updates</b> and <b>other data</b> on facebook. Click on add facebook friends and select upto a maximum of <b>ten friends</b> to compare. " +
            " When the results are generated, you can click on an individual result to see what is common between you and your friend, e.g common music by clicking on Music</p>";
            $("#modal-content").html(html);
         }
       
       function onLoad(){
          //ui.staticmodal("content","How it works","worksUI()");
          ui.staticmodal("content","Which of your friends is most like you on facebook<span id='load_area' style='float: right; display : none;'><img src='img/ajax-loader.gif'></span>","addUI()");
          ui.staticmodal("copyright","","copyrightUI()");
       }
       
       function showAbout(){
     	  ui.modal("About","worksUI()","dismissModal()","");  
       }
       
       
       function copyrightUI(){
    	  var html = "<span><b>Copyright 2014, Quest LTD Nairobi,Kenya</b></span>";
    	  $("#modal_body").html(html);
       }
       
       
       function dismissModal(){
     	  $("#alert-window").modal('hide');
       }
       
       
   
       
       window.onload=function(){
    	   onLoad();
    	   initFB();
    	   window.onresize=ui.resize;
       }
       
       function initFB(){
    	   FB.init({
    		   appId: '137400156430331', 
    		   status: true, 
    		   cookie: false, 
    		   xfbml: false, 
    		   oauth: true
    		   });
    	   FB.XFBML.parse()
    	   ensureFBLogin();
       }
       
       function ensureFBLogin(){
    	   FB.getLoginStatus(function(response) {
      			if (response.authResponse) {
      				$("#fb_login").val("Already Logged In")
      				$("#fb_login").attr("disabled","true");
      				loggedIn=true;
      				FB.api('/me', function(response) {
      					currentUserName=response.name;
      					currentUserId=response.id;
					});
      				TDFriendSelector.init();
      				selector1 = TDFriendSelector.newInstance({
      					callbackSubmit           : callbackSubmit,
      					friendsPerPage           : 5,
      					maxSelection             : 10,
      					autoDeselection          : true
      				});
      			} else {
      				loggedIn=false;
      			}
      		}); 
       }
       
     
       
       function loginFB(){
    	   FB.login(function (response) {
				if (response.authResponse) {
					$("#fb_login").val("Already Logged In")
					$("#fb_login").attr("disabled","true");
					loggedIn=true;
					FB.api('/me', function(response) {
						currentUserName=response.name;
						currentUserId=response.id;
					});
					TDFriendSelector.init();
      				selector1 = TDFriendSelector.newInstance({
      					callbackSubmit           : callbackSubmit,
      					friendsPerPage           : 5,
      					maxSelection             : 5,
      					autoDeselection          : true
      				});
				} else {
					loggedIn=false;
				}
			}, {scope: theScope});
       }
       
      function logoutFB(){
    	  FB.logout(function (response) {
				if (response.authResponse) {
					loggedIn=false;
				} else {
					loggedIn=true;
				}
			});  
      } 
      
       function selectFriends(){
    	  if(!loggedIn){
    		loginFB();
    	  }
    	  if(selector1){
    		selector1.reset();
    	    selector1.showFriendSelector();
    	  } 
       }
       
		// When the user clicks OK, log a message
		callbackSubmit = function(selectedFriendIds) {
			friendIds=selectedFriendIds;
			friendNames = {};
			renderFriendProfiles();
		};
		
		function renderFriendProfiles(){
		  var table=$("<table class='table table-condensed' style='font-size: 16px;' id='friend_result_area'>");
		  var th=$("<th style='width : 100px'>");
		  var th1=$("<th>");
		  var th2=$("<th>");
		  var th3=$("<th>");
		  th.html("Photo");
		  th1.html("Name");
		  th2.html("Compatibility Score(%)");
		  th3.html("Favorite Word");
		  table.append(th).append(th1).append(th2).append(th3);
		  var count = 0;
		  for(var x=0; x<friendIds.length; x++){
			   FB.api({
				      method: 'fql.query',
				      query: 'SELECT name, pic_square,uid FROM user WHERE uid='+friendIds[x]
				    },
				    function(response) {
				      if(response[0]){
				    	friendNames[response[0].uid] = response[0].name;
				      	table.append("<tr><td><img src="+response[0].pic_square+"></td><td><a href='https://www.facebook.com/"+response[0].uid+"' target='_blank' style='text-decoration: none;color: #000;'>" +response[0].name+"</a></td><td id='"+response[0].uid+"_percent'></td><td id='"+response[0].uid+"_fav_word'></td></tr><br>" );
				     	$("#friend_display_area").html(table);
				        }
				      count++;
				   });
			}
		  var time=setInterval(function(){
	          if(count === friendIds.length ){
	            clearInterval(time); 
	            sendToServer();
	          }  
	      },5); 
		  table.append("<tr><td></td><td></td><td><a href='#' onclick='shareResults()'>Share results to timeline</a></td><td></td></tr>");
		}
		

		
		function sendToServer(){
			$("#load_area").css("display","block");
			if(friendIds.indexOf("me")===-1){
				friendIds.push("me");
			}
			if(friendIds.length === 1){
			  return;
			}
			 var json={
                    request_header : {
                        request_msg : "access_token"
                     },
                     request_object : {  
                       access_token : FB.getAccessToken(),
                       friend_ids :   friendIds,
                       friend_names : friendNames,
                       current_user_name : currentUserName,
                       current_user_id : currentUserId
                     }
                 };
                 
                  ajax.run({
                     url : "/fb",
                     type : "post",
                     data : json,
                     error : function(err){
                    	 ui.modal("Error","modalData('Whoops an error occurred!')","dismissModal()","");
                    	 $("#load_area").css("display","none");
                     },
                     success : function(json){
                    	 console.log(json);
                    	 results = json;
                    	 for(var id in json.data.percentages){
                    		var percent = json.data.percentages[id].total;
                    		var color = "black";
                    		if(percent>60){
                    		  color = "lightgreen";	
                    		}
                    		else if(percent>30){
                    		  color = "orange";
                    		}
                    		else {
                    		  color = "#FF8F00";	
                    		}
                    		var table=$("<table class='table table-bordered' style='width : auto ;font-size: 14px;'>");
                    		var tr=$("<tr></tr>");
                    		var td1=$("<td class='link' onclick=showResults('music_intersect','"+id+"') style='border-right-color : #51CBEE; width : 50px;'>Music</td>");
                    		var td2=$("<td style='border-color : #51CBEE; background : #51CBEE; width : 30px;'>"+Math.round(json.data.percentages[id].music)+"</td>");
                    		var td3=$("<td class='link' onclick=showResults('word_intersect','"+id+"') style='border-right-color : #51CBEE; width : 50px;'>Statuses</td>");
                    		var td4=$("<td style='border-color : #51CBEE; background : #51CBEE;width : 30px;'>"+Math.round(json.data.percentages[id].statuses)+"</td>");
                    		var td5=$("<td class='link' onclick=showResults('like_intersect','"+id+"') style='border-right-color : #51CBEE; width : 50px;'>Likes</td>");
                    		var td6=$("<td style='border-color : #51CBEE; background : #51CBEE;width : 30px;'>"+Math.round(json.data.percentages[id].likes)+"</td>");
                    		var td7=$("<td class='link' onclick=showResults('movie_intersect','"+id+"') style='border-right-color : #51CBEE; width : 50px;'>Movies</td>");
                    		var td8=$("<td style='border-color : #51CBEE; background : #51CBEE;width : 30px;'>"+Math.round(json.data.percentages[id].movies)+"</td>");
                    		var td9=$("<td class='link' onclick=showResults('book_intersect','"+id+"') style='border-right-color : #51CBEE; width : 50px;'>Books</td>");
                    		var td10=$("<td style='border-color : #51CBEE; background : #51CBEE;width : 30px;'>"+Math.round(json.data.percentages[id].books)+"</td>");
                    		var td11=$("<td style='background : "+color+"; border-color : "+color+" ; width : 30px;'>"+Math.round(percent)+"</td>");
                    		tr.append(td1).append(td2).append(td3).append(td4).append(td5).append(td6).append(td7);
                    		tr.append(td8).append(td9).append(td10).append(td11);
                    		table.append(tr);
                    		$("#"+id+"_percent").append(table);
                    		$("#"+id+"_fav_word").html("<b>"+json.data.fav_words[id]+"</b>");
                    	 }
                    	 $("#load_area").css("display","none");
                   } 
             });
		}
		
		
		 function modalData(txt){
        	  $("#modal-content").html(txt);	
          }
         
         function dismissModal(){
       	  $("#alert-window").modal('hide');
         }
		
         
         function resultsUI(type,friendId) {
              $("#modal-content").html("");
              if(results.data[type][friendId]){
            	  var header = "Common ";
            	  if(type === "word_intersect"){
            		  header = header + "Words";
            	  }
            	  else if(type === "music_intersect"){
            		  header = header + "Music";
            	  }
            	  else if(type === "like_intersect"){
            		  header = header + "Likes";
            	  }
            	  else if(type === "movie_intersect"){
            		  header = header + "Movies";
            	  }
            	  else if(type === "book_intersect"){
            		  header = header + "Books";
            	  }
            	  else{
            		  header = header + "Results";
            	  }
            	  ui.table("modal-content",[header],[results.data[type][friendId]],true);  
              }
          }
         
         
         function showResults(type,friendId){
            var name = TDFriendSelector.getFriendById(friendId).name
            var title = "Me and "+name+"<br><a href='#' onclick=shareResultsToTimeline('"+type+"','"+friendId+"')>Share to Timeline</a>";
       	  	ui.modal(title,"resultsUI('"+type+"','"+friendId+"')","dismissModal()","");  
         }
        /*
		function fetchSingleFriendData(friendId){
			FB.api('/'+friendId+'/statuses',{'limit': '50'}, function(response){
		        friendData[friendId].statuses=response.data;
		     });
			FB.api('/'+friendId+'/music', {'limit': '100'},function(response) {
			     friendData[friendId].music=response.data;
		      });
			FB.api('/'+friendId+'/movies',{'limit': '100'}, function(response){
			      friendData[friendId].movies=response.data;
		      });
			FB.api('/'+friendId+'/likes',{'limit': '100'}, function(response) {
				  friendData[friendId].likes=response.data;
		      });
			FB.api('/'+friendId+'/books',{'limit': '100'}, function(response) {
			      friendData[friendId].books=response.data;
		      });
		 }
		*/
		
		function shareResults(){
		  var msg = "How i match up against my friends ";
		  theScope = theScope + ",publish_actions";
		  FB.login(function (response) {
				if (response.authResponse) {
					  for(var x=1; x<friendIds.length; x++){
						var name=dom.el("friend_result_area").children[4].children[x].children[1].firstChild.innerHTML;
						var percent =dom.el("friend_result_area").children[4].children[x].children[2].firstChild.firstChild.firstChild.children[10].innerHTML;
						var id = friendIds[x-1];
						if(x===friendIds.length-1){
						  msg = msg+name+" : "+percent+"% ";
						}
						else{
						  msg = msg+name+" : "+percent+"%, ";	
						}
					  }
					  shareToTimeline(msg);
				} else {
					
				}
			}, {scope: theScope});
		 
		}
		
		function shareToTimeline(message){
			FB.ui({
					    method: 'feed',
					    name: 'CompatibilityMatcher',
					    link: 'https://am-compatible.appspot.com/',
					    picture: 'https://am-compatible.appspot.com/img/compatible.png',
					    description: message
					  },
					  function(response) {
					    if (response && response.post_id) {
					      ui.modal("Success","modalData('Post was published to timeline successfully')","dismissModal()","");
					      noteShareToServer();
					    } else {
					      ui.modal("Error","modalData('Whoops an error occurred! post was not shared to timeline')","dismissModal()","");
				     }
			 });	
		}
		
		function shareResultsToTimeline(type,friendId){
			var header = "Common ";
      	  	if(type === "word_intersect"){
      		  header = header + "Words";
      	  	}
      	  	else if(type === "music_intersect"){
      		  header = header + "Music";
      	  	}
      	  	else if(type === "like_intersect"){
      		  header = header + "Likes";
      	  	}
      	  	else if(type === "movie_intersect"){
      		  header = header + "Movies";
      	  	}
      	  	else if(type === "book_intersect"){
      		  header = header + "Books";
      	  	}
      	  	else{
      		  header = header + "Results";
      	  	}
      	  	var data = results.data[type][friendId];
      	  	if(!data || data.length === 0){
      	  	   alert("Nothing to share")
      	  	   return;
      	  	}
      	  	var name = TDFriendSelector.getFriendById(friendId).name
      	  	var extra = " between Me and "+name+" : ";
      	    var message = header + extra + data.toString();
      	    shareToTimeline(message);
		}
        
		
		function noteShareToServer(){
			 var json={
	                    request_header : {
	                        request_msg : "share_post"
	                     },
	                     request_object : {  
	                       current_user_name : currentUserName,
	                       current_user_id : currentUserId
	                     }
	                 };
	                 
	                  ajax.run({
	                     url : "/fb",
	                     type : "post",
	                     data : json,
	                     error : function(err){
	                    	
	                     },
	                     success : function(json){
	                    	
	                   } 
	             });	
		}