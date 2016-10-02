# SYSC-3303-Project

Setup instructions: 

	1: Run Server.java first(main)
	
	
Client.java: 

	-> Creates an instance of client which sends and receives files the server through an intermediate host

IntermediateHost.java:
	
	->Creates an intermediate host that mediates requests between server and client
	
Server.java: 

	-> The server receives messages from intermediate host and responds
	-> Sends a response that changes depending on messages (0301 for read; 0400 for write; 	exits for invalid)
	
Converter.java:

	->Converts a byte array into a string
