import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.Arrays;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import javax.swing.text.MaskFormatter;

/*
 * Creates a instance of client which sends and receives files from/to server through the intermediate host
 * 
 * Team 11  
 * V1.16
 */
public class Client
{
	private static final int SERVER_PORT = 69;
	private static final int HOST_PORT = 23;
	
	private int portNumber = HOST_PORT;
	private DatagramSocket socket;
	private File directory;
	private boolean verbose;
	private String filename;
	private static String hostIP;		//IP address of the new host (localHost by default)
	//private static InetAddress defaultIp;	//IP address of the default host (localHost by default)
	private Integer hostTID;

	/*
	 * Constructor for objects of class Client
	 */
	public Client()
	{
		directory = null;
		
		hostIP = readFile("IPAddress.txt");		//host IP Address
		
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

		return new DatagramPacket(request, request.length, createIp(hostIP), portNumber);
	}

	/**
	 * Sends error packet to the client
	 * 
	 * @param corresponding error number
	 * @param corresponding packet
	 * @param socket to send off of from current thread
	 */
	private void createSendError(byte errorNum, DatagramPacket receivePacket, DatagramSocket socket, String message){
		
		byte[] b = message.getBytes() != null ? new byte[4 + message.getBytes().length + 1] : new byte[4];
		b[0] = 0;
		b[1] = 5;
		b[2] = 0;
		b[3] = errorNum;
		
		int i = 4;
		
		if(message.getBytes() != null){
			for(byte msgB : message.getBytes()){
				b[i++] = msgB;
			}
		}
		
		b[i++] = 0;
		
		try {			
			DatagramPacket ack = new DatagramPacket(b, b.length, createIp(hostIP), receivePacket.getPort());
			socket.send(ack);
		} catch (UnknownHostException e){
			e.printStackTrace();
			System.exit(1);
		} catch (IOException IOE){
			IOE.printStackTrace();
			System.exit(1);
		}
	}
	/*
	 *	Creates a DatagramPacket that contains the data requested
	 */
	private synchronized DatagramPacket buildData(byte[] data, int dataBlockCounter, int portNumber)
	{
		//Adds "3" for data packet format followed by block number
		byte[] msg = new byte[516];
		msg[1] = 3;				
		msg[2] = (byte)(dataBlockCounter/256);
		msg[3] = (byte)(dataBlockCounter%256);
		System.out.println(msg[2]+", "+msg[3]);
		//Goes through the data and adds it to the message
		for(int j = 0, k = 4; j < data.length && k < msg.length; j++, k++)	
		{
			msg[k] = data[j];
		}

		//Creates DatagramPacket containing the data and sends it back
		DatagramPacket send = null;
		send = new DatagramPacket(msg, msg.length, createIp(hostIP), portNumber);
		return send;
	}

	/*
	 *	Runs the read request (ie. sends initial request then reads the data from the server)
	 */
	private synchronized void sendReadReceive(String filename) throws IOException
	{
		//Check if the user is trying to overwrite an existing file in their local directory
		if(Files.exists(Paths.get(directory.getAbsolutePath() + "\\" + filename))){
			System.out.println("Failed to write: 0506 - File already exists on local disk: " + filename);
			System.out.println("Stopping thread process . . .");
			System.exit(0);
		}
	
		//Creates read request DatagramPacket and sends it to the intermediate host
		DatagramPacket receivePacket;
		DatagramPacket message = buildRequest("ocTeT", filename, ActionType.READ);
		if(verbose){
			System.out.println("Sending request to Host: " + Converter.convertMessage(message.getData()));
			System.out.println(filename);
		}
		
		socket.send(message);	
		
		//Error Handling Variables:
		final int OVERLAP = 65535;
		int dataBlockCounter=-1;
		int incomingBlockID=-1;
		int incomingByteNums=0;
		int overlapCounter = 0; 
		boolean delayed=false; 
		boolean lost=false;
		//Creates a file where the data read from sever is stored
		int index = -1;
		byte[] data;
		byte[] receiveMsg;
		byte[] b = {0,4,0,0};
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
			receivePacket = new DatagramPacket(receiveMsg, receiveMsg.length);
			//delayed and lost handling
			/*if(!checkIfValidPacket(receiveMsg)){
				System.out.println("Invalid packet format: 0504 - Invalid packet. ");
			}*/
			
			try {
				socket.setSoTimeout(2000);  //set timeout 
				socket.receive(receivePacket);	
			} catch (SocketTimeoutException ste){
				System.out.println("Socket timeout file delayed or lost");
				delayed = true;
			}
			if (delayed){
				try {
					socket.setSoTimeout(5000);  //set timeout 
					socket.receive(receivePacket);	
				} catch (SocketTimeoutException ste){
					System.out.println("Socket timeout file declared lost\nAsking to be sent again.");
					lost = true;
					delayed = false;///if delayed keep that info till the end of the loop
				}
			}
			//Start of Run after packet Received. 
			if (lost){
				System.out.println("Lost Packet");
				lost=false;
			} //not lost but has an error
			else if((receivePacket.getData()[3] == 1 || receivePacket.getData()[3] == 2) && receivePacket.getData()[1] == 5)
			{
				if(hostTID == null) hostTID = receivePacket.getPort();
				//Verify that the TID of the received packet is correct
				if(receivePacket.getPort() != hostTID){
					//If the ports do not match, send an errorpacket to the received packet
					createSendError(new Byte("5"), receivePacket, socket,"Received ACK from port:" + receivePacket.getPort() + " when expecting port:" + hostTID);
					//"Continue" by sending the thread back to the beginning of the while loop
					continue;
				}
				fos.close();
				Files.delete(Paths.get(directory.getAbsolutePath() + "\\" + filename));
				System.exit(0);
			} 
			//Else No Errors in packet, check for duplicate and run normally. 
			else {
				if(hostTID == null) hostTID = receivePacket.getPort();
				//Verify that the TID of the received packet is correct
				if(hostTID instanceof Integer && receivePacket.getPort() != hostTID){
					//If the ports do not match, send an errorpacket to the received packet
					createSendError(new Byte("5"), receivePacket, socket, "Received ACK from port:" + receivePacket.getPort() + " when expecting port:" + hostTID);
					//"Continue" by sending the thread back to the beginning of the while loop
					continue;
				}
				
				//Error Handling Iteration 3: 
				//parse bytes into int:
				if(verbose)
					System.out.println("Response received from server: " + Arrays.toString(receiveMsg));
				incomingByteNums =(0x00 << 16) | ((receiveMsg[2] & 0xff)<<8) | (receiveMsg[3] & 0xff);
				incomingBlockID= incomingByteNums + overlapCounter*(OVERLAP+1);	
				if (verbose) System.out.println("incoming packet number: "+incomingBlockID + " expected number: "+(dataBlockCounter+1));
				if (incomingBlockID == dataBlockCounter+1){ //expected condition incomingBolockNum == dataBlockCounter+1: run normally
					dataBlockCounter=incomingBlockID;
					if (incomingByteNums == OVERLAP) {
						if (verbose) System.out.println("overlap neccessary file exceeds max byte range");
						overlapCounter++;
					}
					if(verbose) {
						System.out.println("Recieved Block Num "+dataBlockCounter);
						System.out.println("From IP: " + receivePacket.getAddress());
						System.out.println("From Port: " + receivePacket.getPort());
					}
					data = new byte[512];
			    	for(int i = 0, j = 4; i < data.length && j<receiveMsg.length; i++, j++)
			    	{
			    		data[i] = receiveMsg[j];
			    		if(data[i] == 0){
			    			index = i;
			    			i = data.length;
			    		}
			    	}
			    	//send ACK 
					b[2]=(byte)(dataBlockCounter/256);
					b[3]=(byte)(dataBlockCounter%256);
					try {
						message = new DatagramPacket(b, b.length, InetAddress.getLocalHost(), receivePacket.getPort());
						socket.send(message);
					} catch(UnknownHostException e) {
						e.printStackTrace();
						System.exit(1);
					} 
					//Writes received message into the file
					if(index == -1){			
						fos.write(data);
					}
					//Writes last bit of the received data
					else{	
			  			fos.write(data, 0, index);
		 				fos.close();
			 			//Checks to see if server had any issues with final ACK
			 			boolean received; 
						do{
							receiveMsg = new byte[512];
							receivePacket = new DatagramPacket(receiveMsg, receiveMsg.length);
							received = true;
							try {
								socket.setSoTimeout(10000);
								socket.receive(receivePacket);
							}catch (SocketTimeoutException ste){
								received = false;
								if(verbose) { 
									System.out.println("Assumed that the Server Received the Final ACK after 10 secconds without a message");
									System.out.println("Waiting for next request...\n");
								}
							}
							
							if (received){
								//Verify that the TID of the received packet is correct
								if(hostTID instanceof Integer && receivePacket.getPort() != hostTID){
									//If the ports do not match, send an errorpacket to the received packet
									createSendError(new Byte("5"), receivePacket, socket, "Received ACK from port:" + receivePacket.getPort() + " when expecting port:" + hostTID);
									//"Continue" by sending the thread back to the beginning of the while loop
									continue;
								}
								incomingByteNums =(0x00 << 16) | ((receiveMsg[2] & 0xff)<<8) | (receiveMsg[3] & 0xff);
								incomingBlockID = incomingByteNums + overlapCounter*(1+OVERLAP);
								if(verbose)
									System.out.println("Response recieved from server: " + Arrays.toString(receiveMsg));
									System.out.println("Another dataPacket recieved from Server, Addressing issue, ACK#: "+incomingBlockID);
								if(incomingBlockID <= dataBlockCounter){
									if(verbose)
										System.out.println("incomingBlock: "+ incomingBlockID + " while data block should be: " + dataBlockCounter);
								}
							}
						} while(received);
					} 
				//incomingdataBlockCounter <= dataBlockCounter, is duplicate packet, ignore and re-send ACK,
				//return to top of loop. and wait for server response unless index has been set and the file is done transferring. 
				}else if(incomingBlockID <= dataBlockCounter){
					//is a duplicate restart loop
					if(verbose)	System.out.println("Incoming Data block is a duplicate");
				//incomingdataBlockCounter Higher than dataBlockCounter should never happen, print error 
				}else{
					if(verbose)System.out.println("Unexpected Error Occured, Recieved Future Data Packet before ACK sent for present\n...Restarting Loop");
				} 	
			}
		}//END while
	}
	
	/**
	 * Checks if the incoming packet is in the correct format and contains
	 * valid information
	 * 
	 * @param packet that was received and will be processed
	 * @return true if it is valid; false if it isn't valid
	 */
	public boolean checkIfValidPacket(byte[] packet) {
		//Packet needs to starts with zero for all cases
		if(packet[0] != new Byte("0")) return false;
		//Checks it as a RRQ/WRQ packet
		if(packet[1] == new Byte("1") || packet[1] == new Byte("2")) {
			int numberOfZeroes = 0;
			//Checks to make sure there are 2 zeroes after the read/write bytes
			for (int i = 2; i <= packet.length-2; i++) {		
				if (packet[i] == 0) {	
					numberOfZeroes++;
					//Makes sure filename and mode isn't missing
					if (packet[i+1] == 0 || packet[i-1] == 0 || i == 2) return false;		
				} 
			}
			//File should not have more or less than 2 zeroes (potential corruption)
			if (numberOfZeroes == 2) return true;
		} 
		//Checks it as a DATA packet
		else if (packet[1] == new Byte("3")) return true;
		//Checks it as a ACK packet
		else if ((packet[1] == new Byte("4")) && (packet.length == 4)) return true;
		//Checks it as a ERROR packet
		else if (packet[1] == new Byte("5")) {
			String errorMessage;
			String message1a = "Failed to read: 0501 - File not found.";
			String message1b = "Failed to read: 0501 - File not found.";
			String message2a = "Failed to read: 0502 - Access Violation.";
			String message2b = "Failed to read: 0502 - Access Violation.";
			String message3a = "Failed to read: 0503 - Disk full or allocation exceeded.";
			String message3b = "Failed to read: 0503 - Disk full or allocation exceeded.";
			//String message4a = "Failed to read: 0504 - Illegal TFTP operation.";
			//String message4b = "Failed to read: 0504 - Illegal TFTP operation.";
			String message5a = "Failed to read: 0505 - Unknown transfer ID.";
			String message5b = "Failed to read: 0505 - Unknown transfer ID.";
			byte[] error= new byte[96];
			
			if (packet[packet.length-1] != 0) return false;
			for (int i = 0; packet[i] != 0; i++) {
				error[i] = packet[i+4];
			}
			errorMessage = error.toString();
			if ((packet[2] == new Byte("0")) && (packet[3] == new Byte("1")) && ((errorMessage == message1a) || (errorMessage == message1b))) return true;
			else if ((packet[2] == new Byte("0")) && (packet[3] == new Byte("2")) && ((errorMessage == message2a) || (errorMessage == message2b))) return true;
			else if ((packet[2] == new Byte("0")) && (packet[3] == new Byte("3")) && ((errorMessage == message3a) || (errorMessage == message3b))) return true;
			//else if ((packet[2] == new Byte("0")) && (packet[3] == new Byte("4")) && ((errorMessage == message4a) || (errorMessage == message4b))) return true;
			else if ((packet[2] == new Byte("0")) && (packet[3] == new Byte("5")) && ((errorMessage == message5a) || (errorMessage == message5b))) return true;
		}
		return false;
	}

	/*
	 *	Runs the write request (ie. sends initial request then writes the data to the server)
	 */
	private synchronized void sendWriteReceive(String filename) throws IOException
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
		socket.send(message);	
		
		int available = 1;
		byte[] receiveMsg = new byte[4];
		//Error detection variables
		final int OVERLAP = 65535;
		int overlapCounter=0;
		int tempIncomingACK=0; 
		int ACKcounter=0;
		int dataBlockCounter=0;
		boolean ACKdelayed=false;
		boolean ACKlost=false;
		boolean emptyPacketSend = false;
		
		while(available > 0 || ACKcounter < dataBlockCounter || emptyPacketSend) {		
			receiveMsg = new byte[100];
			//Receives response from server
			DatagramPacket receivePacket = new DatagramPacket(receiveMsg, receiveMsg.length);	
			try {
				socket.setSoTimeout(2000);
				if(verbose) System.out.println("Waiting for response... \n");
				socket.receive(receivePacket);
			} catch (SocketTimeoutException ste){
				if(verbose) System.out.println("ACK Packet "+dataBlockCounter+" is declared delayed");
				ACKdelayed=true;
			}
			if (ACKdelayed){
				try {
					socket.setSoTimeout(4000);
					if(verbose) System.out.println("Waiting for response... \n");
					socket.receive(receivePacket);
					if(!(hostTID instanceof Integer))
						hostTID = receivePacket.getPort();
				} catch (SocketTimeoutException ste){
					if(verbose)
						System.out.println("ACK Packet is declared lost\nResending Data Packet");
					ACKdelayed=false;
					ACKlost = true; 
				} 
			}
			if (ACKlost){
				socket.send(message);
				ACKlost=false;
			}//Check for File related errors
			else if((receivePacket.getData()[3] == 2 || receivePacket.getData()[3] == 3 || receivePacket.getData()[3] == 6) && receivePacket.getData()[1] == 5){
				//Verify that the TID of the received packet is correct
				if(hostTID instanceof Integer && receivePacket.getPort() != hostTID){
					//If the ports do not match, send an errorpacket to the received packet
					createSendError(new Byte("5"), receivePacket, socket, "Received ACK from port:" + receivePacket.getPort() + " when expecting port:" + hostTID);
					//"Continue" by sending the thread back to the beginning of the while loop
					continue;
				}
				System.out.println("Error packet received. " + Converter.convertErrorMessage(receivePacket.getData()) + ". \nStopping request.");
				System.exit(0);
			}//run and parse for duplicate, delayed, and lost errors 
			else {
				//Verify that the TID of the received packet is correct
				if(hostTID instanceof Integer && receivePacket.getPort() != hostTID){
					System.out.println("CASE: Invalid TID.. RESPONSE: reset the thread.");
					//If the ports do not match, send an error packet to the received packet
					createSendError(new Byte("5"), receivePacket, socket, "Received ACK from port:" + receivePacket.getPort() + " when expecting port:" + hostTID);
					//"Continue" by sending the thread back to the beginning of the while loop
					continue;
				}
				tempIncomingACK = ((0x00 << 16) | ((receiveMsg[2] & 0xff)<<8) | (receiveMsg[3] & 0xff)) + overlapCounter*(OVERLAP+1);
				if(verbose){
					System.out.println("The value coming in as an ack number is"+tempIncomingACK + " while dataBlockCounter : "+dataBlockCounter);
					System.out.println("Response received from Host: " + Arrays.toString(receiveMsg) + "\n");
				}
				if(tempIncomingACK == dataBlockCounter){
					ACKcounter = tempIncomingACK;
					if (dataBlockCounter%OVERLAP==0 && dataBlockCounter>0){
						if(verbose)System.out.println("Outgoing data packet is increased over maximum representable threshold.");
						overlapCounter++;
					}
					//Reads data into the file
					data = new byte[512];		
					if(available>0) is.read(data);
					
					if(available>0 || emptyPacketSend)
						message = buildData(data, ++dataBlockCounter, receivePacket.getPort());
					
					if(verbose) {
						System.out.println("Sending data. . .");
						System.out.println("Destination IP: " + message.getAddress());
					}
					socket.send(message);
				
					if (available<=0 && emptyPacketSend) emptyPacketSend=false;
					
					available = is.available();
					
					if (available == 512 && !emptyPacketSend) emptyPacketSend=true;
					
					if (verbose)System.out.println("Bytes left in file"+available);
				} else if(tempIncomingACK < dataBlockCounter){ 
					//incoming ACK is for block before the one just sent out by this Client
					if(verbose)
						System.out.println("CASE: Duplicate ACK.. RESPONSE: resending packet");
					
					socket.send(message);				
				} else {
					if(verbose) System.out.println("CASE: Unexpected Error Occurred(ACK for future packet Recieved) RESPONSE: resending packet");
					socket.send(message);
				}			
			}
		}//END Loop
		is.close();
	}


	/*
	 * Enables the user to select which directory will act as the client's file system
	 */
	private File getDirectory()
	{
		JFileChooser directoryFinder = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
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

	/*
	 *reads a text file line by line and returns a string
	 */
	private static String readFile(String path) 
	{
			  byte[] encoded;
			  String s = "";
			try {
				encoded = Files.readAllBytes(Paths.get(path));
				s = new String(encoded);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			  return s;
	}
	
	/*
	 *creates a InetAddress object with a string. Takes a string input i.e "127.0.0.1"
	 */
	private InetAddress createIp(String ip){		
		
		InetAddress host = null;
		try {
			host = InetAddress.getByName(ip);
		} catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		return host;
	}
	
	
	private void addIPAddress(String ip){
		
		FileWriter fw;
		PrintWriter pw;
		try {
			fw = new FileWriter("IPAddress.txt");		//writes to the file the local IP Address if the file is empty
		    pw = new PrintWriter(fw);
		    pw.print(ip);
			pw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/*
	 *    Creates a host instance and runs it
	 */
	public static void main(String args[])
	{
		Client client = new Client();
		client.setUp();
	}
	
	public void setUp()
	{
		new ClientSetup();
	}

	public enum ActionType
	{
		READ, WRITE, INVALID
	}
	
	@SuppressWarnings("serial")
	private class ClientSetup extends JDialog
	{
		private File file;
		private JRadioButton verboseRadio;
		private JRadioButton testRadio;
		private JRadioButton readRadio;
		private JTextField directoryPath;
		private JTextField fileField;
		private JTextField IPAddressField;
		private JButton browseButton;
		private JButton okButton;
		private JButton cancelButton;
		private JRadioButton defaultIPRadio;
		
		
		public ClientSetup()
		{
			this.file = FileSystemView.getFileSystemView().getHomeDirectory();
			this.directoryPath = new JTextField(file.getAbsolutePath());
			this.directoryPath.setColumns(25);
			this.fileField = new JTextField("");
			this.IPAddressField = new JTextField();
			this.fileField.setColumns(5);
			this.IPAddressField.setColumns(10);
			this.browseButton = new JButton("Browse...");
			this.okButton = new JButton("OK");
			this.cancelButton = new JButton("Cancel");
			
			this.verboseRadio = new JRadioButton("Verbose", true);
			this.testRadio = new JRadioButton("Test", true);
			this.readRadio = new JRadioButton("Read", true);
			this.defaultIPRadio = new JRadioButton("Default IP", true);
			JRadioButton newIPRadio = new JRadioButton("New IP");
			JRadioButton quietRadio = new JRadioButton("Quiet");
			JRadioButton normalRadio = new JRadioButton("Normal");
			JRadioButton writeRadio = new JRadioButton("Write");
			
			
			ButtonGroup g1 = new ButtonGroup();
			g1.add(verboseRadio);
			g1.add(quietRadio);
			ButtonGroup g2 = new ButtonGroup();
			g2.add(testRadio);
			g2.add(normalRadio);
			ButtonGroup g3 = new ButtonGroup();
			g3.add(readRadio);
			g3.add(writeRadio);
			ButtonGroup g4 = new ButtonGroup();
			g4.add(defaultIPRadio);
			g4.add(newIPRadio);
			
			JPanel directoryPanel = new JPanel();
			directoryPanel.add(new JLabel("Client Directory: "));
			directoryPanel.add(this.directoryPath);
			directoryPanel.add(this.browseButton);
			
			JPanel outputPanel = new JPanel();
			outputPanel.add(this.verboseRadio);
			outputPanel.add(quietRadio);
			
			JPanel modePanel = new JPanel();
			modePanel.add(this.testRadio);
			modePanel.add(normalRadio);
			
			JPanel transferPanel = new JPanel();
			transferPanel.add(this.readRadio);
			transferPanel.add(writeRadio);
			
			JPanel defaultIPPanel = new JPanel();
			defaultIPPanel.add(this.defaultIPRadio);
			defaultIPPanel.add(newIPRadio);
			defaultIPPanel.add(IPAddressField);
			
			JPanel filePanel = new JPanel();
			filePanel.add(new JLabel("Filename: "));
			filePanel.add(this.fileField);
			
			JPanel buttonPanel = new JPanel();
			buttonPanel.add(this.okButton);
			buttonPanel.add(this.cancelButton);
			
			JSplitPane s1 = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
			s1.setDividerSize(0);
			s1.setBorder(BorderFactory.createEmptyBorder());
			JSplitPane s2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
			s2.setDividerSize(0);
			s2.setBorder(BorderFactory.createEmptyBorder());
			JSplitPane s3 = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
			s3.setDividerSize(0);
			s3.setBorder(BorderFactory.createEmptyBorder());
			JSplitPane s4 = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
			s4.setDividerSize(0);
			s4.setBorder(BorderFactory.createEmptyBorder());
			JSplitPane s5 = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
			s5.setDividerSize(0);
			s5.setBorder(BorderFactory.createEmptyBorder());
			
			s3.setTopComponent(transferPanel);
			s3.setBottomComponent(s5);
			s5.setTopComponent(defaultIPPanel);
			s5.setBottomComponent(filePanel);
			s2.setTopComponent(s3);
			s2.setBottomComponent(buttonPanel);
			s4.setTopComponent(outputPanel);
			s4.setBottomComponent(modePanel);
			s1.setTopComponent(directoryPanel);
			s1.setBottomComponent(s4);
			
			JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
			split.setDividerSize(0);
			split.setTopComponent(s1);
			split.setBottomComponent(s2);
			this.add(split);
			this.pack();
			this.setUpListeners();
			this.setLocationRelativeTo(null);
			this.setTitle("Client Setup");
			this.setVisible(true);
			this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		}
		
		private void setUpListeners()
		{
			okButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e) 
				{
					directory = file;
					filename = fileField.getText();
					if(verboseRadio.isSelected()) verbose = true;
					else verbose = false;
					if(testRadio.isSelected()) portNumber = HOST_PORT;
					else portNumber = SERVER_PORT;
					dispose();
					if(!defaultIPRadio.isSelected()){
						addIPAddress(IPAddressField.getText());
					}	
					if(readRadio.isSelected()) 
						try{ sendReadReceive(filename); } catch (IOException e1){e1.printStackTrace();}
					else
						try{ sendWriteReceive(filename); } catch (IOException e1){e1.printStackTrace();}		
				}
				
			});
			cancelButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e) 
				{
					System.out.println("Client creation cancelled.\n");
					dispose();
					System.exit(0);
				}
			});
			browseButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e) 
				{
					File tempFile = getDirectory();
					if(tempFile != null)
					{
						file = tempFile;
						directoryPath.setText(tempFile.getAbsolutePath());
					}
				}
			});
		}
		
	}
}
