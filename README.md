# SYSC-3303-Project
Client.java: 

	-> The client class that sends the messages
	-> Sends 10 messages; alternates between read and write
	-> After the 10 messages; sends an invalid messages
	-> After sending messages to intermediate, waits for a response
	-> While it sends the first messages, must be started lastly
	-> In order to run this class; run the main method

Intermediate.java:
	
	-> The intermediate class that transfers messages and responses between server and client
	-> Must be started before client but after server
	-> Runs infinitely to keep transferring messages and responses
	-> In order to run this class; run the main method

Server.java: 

	-> The server class that receives messages from intermediate and responds
	-> Sends a response that changes depending on messages (0301 for read; 0400 for write; 	exits for invalid)
	-> Runs infinitely to keep receiving messages and sending responses (until invalid message)
	-> Must be started first in order to receive a messages
	-> In order to run this class; run the main method

Instructions: 

	1: Run Server(main)
	2: Run Intermediate(main)
	3: Run Client(main)
