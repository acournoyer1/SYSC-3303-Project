# SYSC-3303-Project

Setup instructions: 
	
	
	1: Run Server.java and choose a directory to read/write from.
		-*NOTE* for testing purposes we point to the project folder as it has test files for us to read/write.
		-The user is prompted to run it in either verbose or quiet mode
		
	2. Run IntermediateHost.java choose to run it in verbose or quiet mode
		
		
	3. Run Client.java and choose a directory to read/write from.
		-*NOTE* for testing purposes we point to the project folder as it has test files for us to read/write.
		-The user is prompted to run it in either verbose or quiet mode
		-The user is prompted to either write or read from the client
			-If write, then type in the name of the file you would like to write to 
			-If read, then type in the name of the file you would like to read from 
	

Testing instructions: 
	
	*NOTE* These instructions are to be followed after reaching the prompt in client.java that asks the user to choose read or write
		Each step is testing for a different error, therefore
	
	1. In order test for File not Found (error 1):
		-Choose read and type in fileNotFound.txt for the file name
		-The server should respond with an error 0501
		
	2. In order to test for Access violation (error 2):
		-Choose read and type in accessDenied.txt for the file name
			-In the project folder their is a file called accessDenied.txt and it has all its read and write permissions denied.
			-In the case that the file does not have its permissions denied, you can:
				-Edit the file permissions by right-clicking the file --> properties --> Security tab --> Edit --> Check off deny read or write
		-The server should respond with error code 0502
	
	3. In order to test for file already exists (error 6):
		-Choose write and type in test.txt
		-The server should respond with error code 0506
	
	4. In order to test for disk full:
		-Restart all the threads and choose a filled drive as the source directory for both the server and client. (for testing we used a filled USB with a single test.txt file in it)
		- A partition can also be created on the drive in order to test (make a test.txt file in said partition)
		-Choose write and type in test.txt
		-The server should respond with error code 0503
	
	
	
	
Client.java: 

	-> Creates an instance of client which sends and receives files the server through an intermediate host

IntermediateHost.java:
	
	->Creates an intermediate host that mediates requests between server and client
	
Server.java: 

	-> The server receives messages from intermediate host and responds
	-> Sends a response that changes depending on messages (0301 for read; 0400 for write; 	exits for invalid)
	
Converter.java:

	->Converts a byte array into a string
