import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Scanner;

import javax.swing.JFileChooser;

/*
 * Creates a instance of client which sends and receives files from/to server through the intermediate host
 * 
 * Team 11  
 * V1.16
 */
public class Client extends Thread
{
	private static final int PORT_NUMBER = 23;
	private DatagramSocket socket;
	private File directory;
	private boolean verbose;
	//TODO: private int prevBlockNum;
	/*
	 * Constructor for objects of class Client
	 */
	public Client()
	{
		directory = null;
		try
		{
			socket = new DatagramSocket();	//Creates socket that sends/receives DataPackets
		}
		catch(IOException e)
		{
			e.printStackTrace();
			System.exit(1);
		}
	}

	/*
	 *	Creates the initial request DatagramPacket (read or write)
	 */
	private synchronized DatagramPacket buildRequest(String mode, String filename, ActionType action)
	{
		//Determines if request is a read, write or invalid
		byte[] request = new byte[100];
		request[0] = 0;
		//TODO switch to Case Statement
		if(action == ActionType.READ) {		
			request[1] = 1;
		}
		else if(action == ActionType.WRITE) {
			request[1] = 2;
		}
		else {
			request[1] = 3;
		}
		int i = 2;
		//Adds the filename to the byte array
		if(filename.getBytes() != null) {	
			for(byte b: filename.getBytes()) {
				request[i++] = b;
			}
		}
		request[i++] = 0;
		//Adds the mode to the byte array
		if(mode.getBytes() != null) {		
			for(byte b: mode.getBytes()) {
				request[i++] = b;
			}
		}
		request[i++] = 0;

		//Creates the DatagramPacket from the byte array and sends it back
		try {					
			return new DatagramPacket(request, request.length, InetAddress.getLocalHost(), PORT_NUMBER);
		} catch (UnknownHostException e) {
			e.printStackTrace();
			return null;
		}

	}

	/*
	 *	Creates a DatagramPacket that contains the data requested
	 */
	private synchronized DatagramPacket buildData(byte[] data, int blockNumber, int portNumber)
	{
		//Adds "3" for data packet format followed by block number
		byte[] msg = new byte[516];
		msg[1] = 3;				
		msg[2] =(byte)((blockNumber >> 8) & 0xFF);
		msg[3] =(byte)(blockNumber & 0xFF);
		System.out.println("Making  DatagramPacket with send number "+msg[1]+", "+msg[2]+", "+msg[3] );
		//Goes through the data and adds it to the message
		for(int j = 0, k = 4; j < data.length && k < msg.length; j++, k++)	
		{
			msg[k] = data[j];
		}

		//Creates DatagramPacket containing the data and sends it back
		DatagramPacket send = null;
		try {
			send = new DatagramPacket(msg, msg.length, InetAddress.getLocalHost(), portNumber); 
		}
		catch(IOException e){
			e.printStackTrace();
		}
		return send;
	}

	/*
	 *	Runs the read request (ie. sends initial request then reads the data from the server)
	 *  		sends ACKS 
	 *  		receives DataBlocks
	 */
	private synchronized void sendReadReceive(String filename)
	{
		//Creates read request DatagramPacket and sends it to the intermediate host
		DatagramPacket message = buildRequest("ocTeT", filename, ActionType.READ);		
		System.out.println("Sending request to Host: " + Converter.convertMessage(message.getData()));
		System.out.println(filename);
		try {
			socket.send(message);	
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		//Check if the user is trying to overwrite an existing file in their local directory
		if(Files.exists(Paths.get(directory.getAbsolutePath() + "\\" + filename))){
			System.out.println("Failed to write: 0506 - File already exists on local disk: " + filename);
			System.out.println("Stopping thread process . . .");
			System.exit(0);
		}
		
		//Error Handling Variables:
		int blockNum=-1;
		int tempIncomingBlockNum=-1;
		boolean delayed=false; 
		boolean lost=false;
		//Creates a file where the data read from sever is stored
		int index = -1;
		byte[] receiveMsg;
		FileOutputStream fos=null;
		try {
			fos = new FileOutputStream(new File(directory.getAbsolutePath() + "\\" + filename)); 
		} catch (FileNotFoundException e1) {
			System.out.println("Error packet received. Code: 0501 File not found. Stopping request.");
			e1.printStackTrace();
			System.exit(0);
		}
		
		//Keeps reading data until server is done sending for the request
		while(index == -1) {
			//Receives the DatagramPacket sent from server/intermediate host
			receiveMsg = new byte[516];
			DatagramPacket receivePacket = new DatagramPacket(receiveMsg, receiveMsg.length);
			//delayed and lost handling
			try {
				socket.setSoTimeout(2000);  //set timeout 
				socket.receive(receivePacket);	
			} catch (SocketTimeoutException ste){
				System.out.println("Socket timeout file delayed or lost");
				delayed = true;
			}catch (IOException e) {e.printStackTrace();System.exit(1);}
			if (delayed){
				try {
					socket.setSoTimeout(5000);  //set timeout 
					socket.receive(receivePacket);	
				} catch (SocketTimeoutException ste){
					System.out.println("Socket timeout file declared lost\nAsking to be sent again.");
					lost = true;
					delayed = false;///if delayed keep that info till the end of the loop
				}catch (IOException e) { e.printStackTrace();}
			}
			//Start of Run after packet Received. 
			if (lost){
				System.out.println("Lost Packet resending if message was ack");
				lost=false;
				try{
					//re-send ACK or Request. 
					if(message.getData()[1]!=1) socket.send(message);
				} catch(IOException e){
					e.printStackTrace();
				}
			} //not lost but has an error
			else if((receivePacket.getData()[3] == 1 || receivePacket.getData()[3] == 2) && receivePacket.getData()[1] == 5)
			{
				//Check for block received is error packet:
				//TODO: code not necessary to run until known that getData()[1]==5;
				String code="";
				switch(receivePacket.getData()[3]){
					case 1:
						code = "File not found";
						break;
					case 2:
						code = "Access Violation";
						break;
				}
				
				System.out.println("Error packet received. Code: 050" + receivePacket.getData()[3] + " " + code + ". Stopping request.");
				try {
					fos.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				try {
					Files.delete(Paths.get(directory.getAbsolutePath() + "\\" + filename));
				} catch (IOException e) {
					e.printStackTrace();
				}
				System.exit(0);
			} 
			//Else No Errors in packet, check for duplicate and run normally. 
			else {
				//TODO: clean this up a bit remove extraneous conditions 
				//Error Handling Iteration 3: 
				//parse bytes into int:
				tempIncomingBlockNum = ((receiveMsg[2] & 0xFF)<<8) | (receiveMsg[3] & 0xFF);		
				if (tempIncomingBlockNum == blockNum+1){ //expected condition incomingBolockNum == blockNum+1: run normally
					blockNum=tempIncomingBlockNum;
					System.out.println("recieved Block Num "+blockNum);
					byte[] data = new byte[512];
			    	for(int i = 0, j = 4; i < data.length && j<receiveMsg.length; i++, j++)
			    	{
			    		data[i] = receiveMsg[j];
			    	}
				
			    	for(int i = 0; i < data.length; i++) {
			    		if(data[i] == 0){
			    			index = i;
			    			i = data.length;
			    		}
			    	}
			    	//send ACK 
					byte[] b = {0, 4, 0, 0};
					b[2]=(byte)((blockNum >>8) & 0xFF);
					b[3]=(byte)(blockNum & 0xFF);
					try {
						message = new DatagramPacket(b, b.length, InetAddress.getLocalHost(), receivePacket.getPort());
					} catch(UnknownHostException e) {
						e.printStackTrace();
						System.exit(1);
					}
					try {
						socket.send(message);
					} catch (IOException IOE){
						IOE.printStackTrace();
						System.exit(1);
					}
					//Writes received message into the file
					if(index == -1){			
						try {
							fos.write(data);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					//Writes last bit of the received data
					else{	
			  			try {
			 				fos.write(data, 0, index);
			 			} catch (IOException e) {
			 				e.printStackTrace();
			 			}
			 			try {
			 				fos.close();
			  			} catch (IOException e) {
			  				e.printStackTrace();
			  			}
			 			//Checks to see if server had any issues with final ACK
			 			boolean received=false; 
						do{
							received = true;
							try {
								socket.setSoTimeout(10000);
								socket.receive(receivePacket);
							}catch (SocketTimeoutException ste){
								received = false;
								System.out.println("Assumed that the Server Received the Final ACK after 10 secconds without a message");
							} catch(IOException e){
								e.printStackTrace();
							}
							
							if (received){
								tempIncomingBlockNum = ((receiveMsg[2] & 0xFF)<<8) | (receiveMsg[3] & 0xFF);
								System.out.println("Another dataPacket recieved from Server, Addressing issue, ACK#: "+tempIncomingBlockNum);
								if(tempIncomingBlockNum <= blockNum){
									try{ socket.send(message);} catch(IOException e) {e.printStackTrace();}
									System.out.println("Resending ACK");
								}
							}
						}while(received);
					}
				//incomingBlockNum <= BlockNum, is duplicate packet, ignore and re-send ACK,
				//return to top of loop. and wait for server response unless index has been set and the file is done transferring. 
				}else if(tempIncomingBlockNum <= blockNum){
					//is a duplicate restart loop
					System.out.println("Incoming Data block is a duplicate");
				//incomingBlockNum Higher than blockNum should never happen, print error and resend ack.	
				}else{
					System.out.println("Unexpected Error Occured, Recieved Future Data Packet before ACK sent for present\n...Restarting Loop");
				} 	
			}
		}//END while
	}

	/*
	 *	Runs the write request (ie. sends initial request then writes the data to the server)
	 */
	private synchronized void sendWriteReceive(String filename)
	{
		File f = new File(directory.getAbsolutePath() + "\\" + filename);
		//Check if the user is trying to write a file that does not exist
		if(!f.exists()){
			System.out.println("Failed to write: 0501 - File does not exist: " + filename);
			System.out.println("Stopping thread process . . .");
			System.exit(0);
		}
		
		//Creates fileInputStream with given filename that will be sent
		byte[] data = new byte[512];
		FileInputStream is = null;		
		try {
			is = new FileInputStream(directory.getAbsolutePath() + "\\" + filename);
		} catch (FileNotFoundException e2) {
			System.out.println("Failed to write: 0502 - Access Violation. " + filename);
			System.out.println("Stopping thread process . . .");
			System.exit(0);
		}
		
		//Creates write request DatagramPacket and sends it
		DatagramPacket message = buildRequest("ocTeT", filename, ActionType.WRITE);	
		System.out.println("Sending request to Host: " + Converter.convertMessage(message.getData()));
		try {
			socket.send(message);	
		} catch (IOException e) {
			e.printStackTrace();
		}
		byte[] receiveMsg = new byte[4];
		//Error detection variables
		int tempIncomingACK=0; 
		int ACKcounter=0;
		int dataBlockCounter=0;
		boolean ACKdelayed=false;
		boolean ACKlost=false;
		
		int available = 0;
		try {
			available = is.available();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		while(available > 0) {		
			receiveMsg = new byte[4];
			//TODO: condense code and place in method for next iteration. 
			//Receives response from server
			DatagramPacket receivePacket = new DatagramPacket(receiveMsg, receiveMsg.length);	
			try {
				socket.setSoTimeout(2000);
				System.out.println("Waiting for response.");
				socket.receive(receivePacket);
			} catch (SocketTimeoutException ste){
				System.out.println("ACK Packet "+dataBlockCounter+" is declared delayed");
				ACKdelayed=true;
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (ACKdelayed){
				try {
					socket.setSoTimeout(4000);
					System.out.println("Waiting for response.");
					socket.receive(receivePacket);
				} catch (SocketTimeoutException ste){
					System.out.println("ACK Packet is declared lost\nResending Data Packet");
					ACKdelayed=false;
					ACKlost = true; 
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			
			if (ACKlost){
				try {
					socket.send(message);
					ACKlost=false;
				} catch (IOException e){
					e.printStackTrace();
				}
			}//Check for File related errors
			else if((receivePacket.getData()[3] == 2 || receivePacket.getData()[3] == 3 || receivePacket.getData()[3] == 6) && receivePacket.getData()[1] == 5){
				String code = "";
				switch(receiveMsg[3]){
				case 2:
					code = "Access Violation";
					break;
				case 3:
					code = "Disk full or allocation exceeded";
					break;
				case 6:
					code = "File already exists";
					break;
				}
				
				System.out.println("Error packet received. Code: 050" + receivePacket.getData()[3] + " " + code + ". Stopping request.");
				System.exit(0);
			}//run and parse for duplicate, delayed, and lost errors 
			else {
				
				tempIncomingACK = ((receiveMsg[2] & 0xFF)<<8) | (receiveMsg[3] & 0xFF);
				System.out.println("The value coming in as an ack number is"+tempIncomingACK + " while dataBlockCounter : "+dataBlockCounter);
				System.out.println("Response received from Host: " + Arrays.toString(receiveMsg) + "\n");
				
				if(tempIncomingACK == dataBlockCounter){
					ACKcounter = tempIncomingACK;
					//Reads data into the file
					data = new byte[512];
					try {				
						is.read(data);
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					message = buildData(data, ++dataBlockCounter, receivePacket.getPort());
					try {
						System.out.println("Sending data. . .");
						socket.send(message);
					} catch (IOException e) {
						e.printStackTrace();
					}
					try {
						available = is.available();
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					System.out.println("Bytes left in file"+available);
				} else if(tempIncomingACK < dataBlockCounter){ 
					//incoming ACK is for block before the one just sent out by this Client
					System.out.println("CASE: Duplicate ACK.. RESPONSE: resending packet");
					try{
						socket.send(message);
					} catch(IOException e){
						e.printStackTrace();
					}
				} else {
					System.out.println("CASE: Unexpected Error Occurred(ACK for future packet Recieved) RESPONSE: resending packet");
					try{
						socket.send(message);
					} catch(IOException e){
						e.printStackTrace();
					}
				}			
			}
		}//END Loop
		//TODO: add to method reduce "loose" code;  
		//Receive Final ACK to make sure that the thing sent:
		while (ACKcounter < dataBlockCounter) {
			System.out.println("entered seccond loop because ACKcounter is "+ACKcounter + " while block number is: "+dataBlockCounter);
			
			ACKdelayed=false; ACKlost=false;
			
			receiveMsg = new byte[4];
			DatagramPacket receivePacket = new DatagramPacket(receiveMsg, receiveMsg.length);
			try {
				socket.setSoTimeout(2000);
				socket.receive(receivePacket);
			} catch (SocketTimeoutException ste){
				System.out.println("Ack is delayed for block number "+(dataBlockCounter));
				ACKdelayed = true;
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (ACKdelayed){
				try {
					socket.setSoTimeout(4000);
					socket.receive(receivePacket);
				} catch (SocketTimeoutException ste){
					System.out.println("Ack is considdered lost.. Resending packet");
					ACKdelayed = false;
					ACKlost = true;
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (ACKlost){//re-send packet
				try{ 
					socket.send(message);
				} catch(IOException e){
					e.printStackTrace();
				}
			}else {
				System.out.println("Response received from Host: " + Arrays.toString(receiveMsg) + "\n");
				tempIncomingACK = ((receiveMsg[2] & 0xFF)<<8) | (receiveMsg[3] & 0xFF);
				if(tempIncomingACK == dataBlockCounter) {
					ACKcounter = tempIncomingACK;
					System.out.println("ACK recieved ");
					break;
				}else {
					try{socket.send(message);} catch(IOException e){ e.printStackTrace();}
				}
			}
		}//END loop 
		
		try {
			is.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	/*
	 * Enables the user to select which directory will act as the client's file system
	 */
	private File getDirectory()
	{
		JFileChooser directoryFinder = new JFileChooser();
		directoryFinder.setDialogTitle("Client Directory");
		directoryFinder.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		directoryFinder.setAcceptAllFileFilterUsed(false);
		if (directoryFinder.showOpenDialog(directoryFinder) == JFileChooser.APPROVE_OPTION) { 
			return directoryFinder.getSelectedFile();
		}
		else
		{
			return null;
		}
	}

	@Override 
	public void run()
	{
		directory = getDirectory();
		if(directory == null)
		{
			System.out.println("Creation of client cancelled.");
			return;
		}
		boolean newRequest = true;
		boolean verboseCheck = false;
		while(newRequest)
		{
			newRequest = false;
			verboseCheck = false;
			Scanner s = new Scanner(System.in);
			System.out.println("For a read request, enter r or read.");
			System.out.println("For a write request, enter w or write.");
			String request = s.next();
			System.out.println("Please write the name of the file you would like to read/write.");
			String filename = s.next();
			do{
				System.out.println("For verbose mode, enter v or verbose.");
				System.out.println("For quiet mode, enter q or quiet.");
				String verbose = s.next();
				if(verbose.equals("v") || verbose.equals("verbose"))
				{
					this.verbose = true;
					verboseCheck = true;
				}
				else if (verbose.equals("q") || verbose.equals("quiet"))
				{
					this.verbose = false;
					verboseCheck = true;
				}
				if(!verboseCheck)
				{
					System.out.println("Please enter a valid string for verbose/quiet mode.");
				}
			}while(!verboseCheck);
			if(request.equals("read") || request.equals("R") || request.equals("r"))
			{
				sendReadReceive(filename);
			}
			else if(request.equals("write") || request.equals("W") || request.equals("w"))
			{
				sendWriteReceive(filename);
			}
			else
			{
				System.out.println("Please enter a valid request.");
			}
			System.out.println("If you would like to make another request, enter c, if not, enter anything else");
			String continueRequests = s.next();
			if(continueRequests.equals("c") || continueRequests.equals("C"))
			{
				newRequest = true;
			}
			else
			{
				s.close();
			}
		}
		socket.close();
	}

	/*
	 *    Creates a host instance and runs it
	 */
	public static void main(String args[])
	{
		Thread client = new Client();
		client.start();
	}

	public enum ActionType
	{
		READ, WRITE, INVALID
	}
}
