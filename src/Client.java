import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;

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
			return new DatagramPacket(request, request.length, InetAddress.getLocalHost(), portNumber);
		} catch (UnknownHostException e) {
			e.printStackTrace();
			return null;
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
		msg[2] = (byte) ((byte)dataBlockCounter/256);
		msg[3] = (byte) ((byte)dataBlockCounter - dataBlockCounter/256);

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
				System.out.println("Lost Packet");
				lost=false;
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
					try {
						socket.send(message);
					}catch(IOException e){e.printStackTrace();} 
					
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
		private JButton browseButton;
		private JButton okButton;
		private JButton cancelButton;
		
		public ClientSetup()
		{
			this.file = FileSystemView.getFileSystemView().getHomeDirectory();
			this.directoryPath = new JTextField(file.getAbsolutePath());
			this.directoryPath.setColumns(25);
			this.fileField = new JTextField("");
			this.fileField.setColumns(5);
			this.browseButton = new JButton("Browse...");
			this.okButton = new JButton("OK");
			this.cancelButton = new JButton("Cancel");
			
			this.verboseRadio = new JRadioButton("Verbose", true);
			this.testRadio = new JRadioButton("Test", true);
			this.readRadio = new JRadioButton("Read", true);
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
			
			s3.setTopComponent(transferPanel);
			s3.setBottomComponent(filePanel);
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
					if(readRadio.isSelected()) sendReadReceive(filename);
					else sendWriteReceive(filename);
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
