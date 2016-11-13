# SYSC-3303-Project

Iteration 3

Setup instructions: 
	
	
	1: Run Server.java and choose a directory to read/write from.
		-*NOTE* for testing purposes we point to the project folder as it has test files for us to read/write.
		-The user is prompted to run it in either verbose or quiet mode
		-The user is also asked if it should be run in test or normal mode
		
	2. Run IntermediateHost.java choose to run it in verbose or quiet mode
		
		
	3. Run Client.java and choose a directory to read/write from.
		-*NOTE* for testing purposes we point to the project folder as it has test files for us to read/write.
		-The user is prompted to run it in either verbose or quiet mode
		-The user is prompted to either write or read from the client
			-If write, then type in the name of the file you would like to write to 
			-If read, then type in the name of the file you would like to read from 
	

Testing instructions: 
	
	*NOTE* Each of these instructions are to be followed after reaching the prompt in client.java that asks the user to choose read or write
	Each step is testing for a different error, therefore
	
	-In order test for File not Found (error 1):
		-Choose read and type in fileNotFound.txt for the file name
		-The server should respond with an error 0501
		
	-In order to test for Access violation (error 2):
		-In the project folder their is a file called accessDenied.txt and its permissions need to be denied. 
			-In order to deny permissions on the file:
				-Right-clicking the file --> properties --> Security --> Edit --> Check off deny read/write
		-In the client thread, choose read and type in accessDenied.txt for the file name
		-The server should respond with error code 0502
	
	-In order to test for file already exists (error 6):
		-Choose write and type in test.txt
		-The server should respond with error code 0506
	
	-In order to test for disk full:
		-Restart all the threads and choose a filled drive as the source directory for both the server and client. (for testing we used a filled USB with a single test.txt file in it)
		- A partition can also be created on the drive in order to test (make a test.txt file in said partition)
		-Choose write and type in test.txt
		-The server should respond with error code 0503
	
	
	Files in this project: 
	
Client.java: 

	-> Creates an instance of client which sends and receives files to the server through an intermediate host

IntermediateHost.java:
	
	->Creates an intermediate host that mediates requests between server and client
	
Server.java: 

	-> The server receives messages from intermediate host and responds
	-> Sends a response that changes depending on messages (0301 for read; 0400 for write; 	exits for invalid)
	-> Checks for tftp error codes 1, 2, 3 and 6 and returns an error packet accordingly
	
Converter.java:

	->Converts a byte array into a string

Diagrams:

	->ReadRequest.pdf
	->UCM.pdf
	->UML Diagram.png
	->WriteRequest.pdf
	->l2.class.violet.html (UML class diagrams)
	
	
	

Work Done:

	->Dan

	->Ryan 	
	- Error Simulator Algorithm
	- Server shutdown
	- Verbose implementation
	
	->Alex
	- Setup GUIs
	- Some work on Error Simulator
	
	->Daman
	- UML Diagrams
	
	->Brendan
	- Network Error Handling 
