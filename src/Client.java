import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
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
	private synchronized DatagramPacket buildData(byte[] data, byte blockNumber, int portNumber)
	{
		//Adds "3" for data packet format followed by block number
		byte[] msg = new byte[516];
		msg[1] = 3;				
		msg[3] = blockNumber;

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
			try {
				socket.receive(receivePacket);	
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			String code = "";
			switch(receivePacket.getData()[3]){
			case 1:
				code = "File not found";
				break;
			case 2:
				code = "Access Violation";
				break;
			}

			if(receivePacket.getData()[3] == 1 || receivePacket.getData()[3] == 2 && receivePacket.getData()[1] == 5){
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
			
			//Copies the data into a new array
	    	byte[] data = new byte[512];
	    	for(int i = 0, j = 4; i < data.length; i++, j++)
	    	{
	    		data[i] = receiveMsg[j];
	    	}
		
	    	for(int i = 0; i < data.length; i++) {
	    		if(data[i] == 0){
	    			index = i;
	    			i = data.length;
	    		}
	    	}

			//Writes received message into the file
			if(index == -1){			
				try {
					fos.write(data);
				} catch (IOException e) {
					e.printStackTrace();
				}

				//Creates and sends back an acknowledgment 
				byte[] b = {0, 4, 0, 0};	
				try {			
					DatagramPacket ack = new DatagramPacket(b, b.length, InetAddress.getLocalHost(), receivePacket.getPort());
					try {
						socket.send(ack);
					} catch (IOException IOE){
						IOE.printStackTrace();
						System.exit(1);
					}
				} catch (UnknownHostException e){
					e.printStackTrace();
					System.exit(1);
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
			}
		}
	}

	/*
	 *	Runs the write request (ie. sends initial request then writes the data to the server)
	 */
	private synchronized void sendWriteReceive(String filename)
	{
		//Check if the user is trying to write a file that does not exist
		if(!Files.exists(Paths.get(directory.getAbsolutePath() + "\\" + filename))){
			System.out.println("Failed to write: 0501 - File does not exist: " + filename);
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
		
		//Creates fileInputStream with given filename that will be sent
		byte[] data = new byte[512];
		FileInputStream is = null;		
		try {
			is = new FileInputStream(directory.getAbsolutePath() + "\\" + filename);
		} catch (FileNotFoundException e2) {
			e2.printStackTrace();
		}
		byte[] receiveMsg = new byte[4];
		byte i = 0;

		int available = 0;
		try {
			available = is.available();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		while(available > 0) {
			//Receives response from server
			receiveMsg = new byte[4];

			DatagramPacket receivePacket = new DatagramPacket(receiveMsg, receiveMsg.length);	
			try {
				socket.receive(receivePacket);
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			String code = "";
			switch(receivePacket.getData()[3]){
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

			if(receivePacket.getData()[3] == 2 || receivePacket.getData()[3] == 3 || receivePacket.getData()[3] == 6 && receivePacket.getData()[1] == 5){
				System.out.println("Error packet received. Code: 050" + receivePacket.getData()[3] + " " + code + ". Stopping request.");
				System.exit(0);
			}
			
			System.out.println("Response received from Host: " + Arrays.toString(receiveMsg) + "\n");

			//Reads data into the file
			try {			
				is.read(data);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			DatagramPacket msg = buildData(data, i++, receivePacket.getPort());
			try {
				System.out.println("test");
				socket.send(msg);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}//END Loop
		try {
			System.out.println("hello");
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
		while(directory == null)
		{
			directory = getDirectory();
		}
		boolean newRequest = true;
		while(newRequest)
		{
			newRequest = false;
			Scanner s = new Scanner(System.in);
			System.out.println("For a read request, enter r or read.");
			System.out.println("For a write request, enter w or write.");
			String request = s.next();
			System.out.println("Please write the name of the file you would like to read/write.");
			String filename = s.next();
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
