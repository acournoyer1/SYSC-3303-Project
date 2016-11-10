import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

import javax.swing.JFileChooser;


/**
 * Creates a instance of server that receives requests from client/intermediate host and responds
 * 
 * Team 11  
 * V1.16
 */
public class Server extends Thread {
	private DatagramSocket receiveSocket;
	private ArrayList<Thread> threads;
	private File directory;
	private boolean verbose;

	//Well-known server port number
	private static final int PORT_NUMBER = 69;

	/*
	 *   Creates new thread, receiveSocket, and sendSocket
	 */
	public Server()
	{
		directory = null;
		threads = new ArrayList<Thread>();
		try {
			receiveSocket = new DatagramSocket(PORT_NUMBER);
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}

	/*
	 *   Waits for request then processes that request  
	 */
	public synchronized void sendReceive()
	{
		while(true)
		{
			//Waits to receive DatagramPacket from intermediate host
			System.out.println("Server waiting...");
			byte[] msg = new byte[100];
			DatagramPacket receivedPacket = new DatagramPacket(msg, msg.length);
			try {
				receiveSocket.receive(receivedPacket);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			System.out.println("Request received from Host: " + Converter.convertMessage(msg));
			
			addThread(new ControlThread(receivedPacket));
		}
	}

	/*
	 * Enables the user to select which directory will act as the server's file system
	 */
	private File getDirectory()
	{
		JFileChooser directoryFinder = new JFileChooser();
		directoryFinder.setDialogTitle("Server Directory");
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
			System.out.println("Creation of server cancelled.");
			return;
		}
		Scanner s = new Scanner(System.in);
		boolean verboseCheck = false;
		while(!verboseCheck)
		{
			System.out.println("For verbose mode, enter v or verbose.");
			System.out.println("For quiet mode, enter q or quiet.");
			String verbose = s.nextLine();
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
			else
			{
				System.out.println("Please enter a valid string for verbose/quiet mode.");
			}
		}
		s.close();
		this.sendReceive();
	}

	/*
	 *   Adds a new thread
	 */
	private void addThread(Thread t)
	{
		threads.add(t);
		t.start();
	}
	
	private void removeThread(Thread t)
	{
		threads.remove(t);
	}
	
	private class ControlThread extends Thread
	{
		private DatagramPacket packet;
		
		public ControlThread(DatagramPacket packet)
		{
			this.packet = packet;
		}
		
		@Override
		public void run()
		{
			byte[] msg = packet.getData();
			//Checks if request is valid (read or write)
			if(!(msg[0] == 0 && (msg[1] == 1 || msg[1] == 2)))
			{
				System.out.println("Request is invalid.");
				System.exit(1);
			}

			int zeroCount = 0;
			int count = 0;
			for(int i = 2; i < msg.length; i++)
			{
				if(zeroCount < 2)
				{
					if(msg[i] == 0 && count == 0)
					{
						System.out.println("Request is invalid.");
						System.exit(1);
					}
					else if(msg[i] == 0)
					{
						count = 0;
						zeroCount++;
					}
					else
					{
						count++;
					}
				}
				else
				{
					if(msg[i] != 0)
					{
						System.out.println("Request is invalid.");
						System.exit(1);
					}
				}
			}

			//Extracts the filename
			int index = -1;
			for(int i = 2; i < msg.length; i++)
			{
				if(msg[i] == 0)
				{
					index = i;
					i = msg.length;
				}
			}
			byte[] b = new byte[index - 2];
			int j = 2;
			for(int i = 0; i < b.length; i++, j++)
			{
				b[i] = msg[j];
			}
			//Turns filename that is a byte array into a string
			String filename = new String(b);
			//Build "file" object from the specified filepath
			File f = new File(directory + "\\" + filename);
			Path path = Paths.get(directory + "\\" + filename);
			
			//If Else determine Read request or Write request
			//Creates new read thread with filename
			if(msg[1] == 1)
			{
				//Check if file exists
				if(!f.exists())
				{
					System.out.println("Failed to read: 0501 - File not found. " + filename);
					System.out.println("Sending error packet . . .");
					createSendError(new Byte("1"), packet, receiveSocket);
				}
				//Check if the file can be read
				else if(!Files.isReadable(path)){
					System.out.println("Failed to read: 0502 - Access Violation. " + filename);
					createSendError(new Byte("2"), packet, receiveSocket);
				}
				//No errors, send valid response
				else{
					System.out.println("The request is a valid read request.");
					addThread(new ReadThread(packet.getPort(), filename));
				}
			}
			//Creates new write thread with filename
			else
			{
				//Check if the file already exists
				 if(Files.exists(path)){
					System.out.println("Failed to write: 0506 - File already exists " + filename);
					System.out.println("Sending error packet . . .");
					createSendError(new Byte("6"), packet, receiveSocket);
				}
				//Check if can write
				 else if(Files.isWritable(path)){
					System.out.println("Failed to read: 0502 - Access Violation. " + filename);
					System.out.println("Sending error packet . . .");
					createSendError(new Byte("2"), packet, receiveSocket);
				}
				//Check if there is enough space on the server
				else if(f.getParentFile().getFreeSpace() < packet.getData().length){
					System.out.println("Failed to write: 0503 - Not enough disk space. " + filename);
					System.out.println("Sending error packet . . .");
					createSendError(new Byte("3"), packet, receiveSocket);
				}

				else{
					System.out.println("The request is a valid write request.");
					addThread(new WriteThread(packet.getPort(), filename));
				}
			}
			//Close and Erase this instance of the control thread as it is no longer useful
			removeThread(this);
		}

		private void createSendError(byte errorNum, DatagramPacket receivePacket, DatagramSocket socket){
			byte[] b = {0, 5, 0, errorNum};
			try {			
				DatagramPacket ack = new DatagramPacket(b, b.length, InetAddress.getLocalHost(), receivePacket.getPort());
				try {
					socket.send(ack);
				}catch (IOException IOE){
					IOE.printStackTrace();
					System.exit(1);
				}
			} catch (UnknownHostException e){
				e.printStackTrace();
				System.exit(1);
			}
		}
	}

	/*
	 *   Creates a thread that will process a read request
	 *   -- data being read from Server and sent to Client
	 */
	private class ReadThread extends Thread
	{
		private DatagramSocket socket;
		private int hostPort;
		private String filename;
		
		/*
		 *   Creates new socket and initiates filename and port
		 */
		public ReadThread(int hostPort, String filename)
		{
			this.hostPort = hostPort;
			this.filename = filename;
			try {
				socket = new DatagramSocket();
			} catch (SocketException e) {
				e.printStackTrace();
			}
		}

		/*
		 *   Sends data back to the client and waits for an ack  
		 */
		private synchronized void sendReceive()
		{
			//Reads from the file	
			byte[] data = new byte[512];
			FileInputStream is = null;
			try {
				System.out.println(filename);
				System.out.println(directory.getAbsolutePath() + "\\" + filename);
				is = new FileInputStream(directory.getAbsolutePath() + "\\" + filename);
			} catch (FileNotFoundException e2) {
				e2.printStackTrace();
			}
			byte[] receiveMsg = new byte[4];
			int available = 0;
			
			//Error Verification Variables 
			int dataBlockCounter = 0;
			int ACKcounter=0; 
			int tempIncomingACK=0;
			boolean ACKdelay = false; 
			boolean ACKlost = false; 
			//TODO: probably don't need ACKduplicate seeing as duplicate acks cause resend. 
			boolean ACKduplicate = false; 
			
			try {
				available = is.available();
			} catch (IOException e1) {
				e1.printStackTrace();
			}

			try {
				is.read(data);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			DatagramPacket msg = buildData(data, dataBlockCounter++, hostPort);
			try {
				socket.send(msg);
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			//Continually reads data from the file until no more data is available
			while(available > 0) {
				ACKdelay = false;
				ACKlost = false;
				
				receiveMsg = new byte[4];

				DatagramPacket receivePacket = new DatagramPacket(receiveMsg, receiveMsg.length);
				try {
					socket.setSoTimeout(2000);
					socket.receive(receivePacket);
				} catch (SocketTimeoutException ste){
					System.out.println("Ack is delayed for block number "+ACKcounter);
					ACKdelay = true;
				} catch (IOException e) {
					e.printStackTrace();
				}
				if (ACKdelay){
					try {
						socket.setSoTimeout(4000);
						socket.receive(receivePacket);
					} catch (SocketTimeoutException ste){
						System.out.println("Ack is considdered lost.. Resending packet");
						ACKdelay = false;
						ACKlost = true;
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				if (ACKlost){//re-send packet
					try{ 
						socket.send(msg);
					} catch(IOException e){
						e.printStackTrace();
					}
				} else {
					System.out.println("Response received from Client: " + Arrays.toString(receiveMsg) + "\n");
					//Check for Problems with the ACK 
					//--Ack is already been received
					// -- ACK is lower than dataBlockCounter-1; ack should be ignored; 
					// -- Ack is higher or equal to dataBlockCounter; shouldnt happen
					
					//parse two bytes into int.
					tempIncomingACK = ((receiveMsg[2] & 0xFF)<<8) | (receiveMsg[3] & 0xFF);
					
					//duplicate/delayed ack packet restart loop:
					if (tempIncomingACK <= ACKcounter){
						//msg should contain previous DatagramPacket to send
						System.out.println("Received old ACK statent, delay and/or duplication Error detected\n..Recomencing Loop");
						try{ 
							socket.send(msg);
						} catch(IOException e){
							e.printStackTrace();
						}
					} //expected result Ack corresponds to last block sent
					else if(tempIncomingACK == ACKcounter+1){
						ACKcounter = tempIncomingACK;
						
						try {
							is.read(data);
						} catch (IOException e1) {
							e1.printStackTrace();
						}
						msg = buildData(data, dataBlockCounter++, hostPort);
						try {
							socket.send(msg);
						} catch (IOException e) {
							e.printStackTrace();
						}
						try {
							available = is.available();
						} catch (IOException e1) {
							e1.printStackTrace();
						}					
					}//default case for circumstance where tempIncomingACK > ACKcounter which should never be possible.
					else{
						System.out.println("Unexpected Error Occurred, ACK for future packet Recieved");
					}
				}		
			}//END Loop
			try {
				is.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		/*
		 *    Builds a DatagramPacket containing the data
		 */
		private synchronized DatagramPacket buildData(byte[] data, int blockNumber, int portNumber)
		{
			//Adds the code for data block(03) followed by block number
			byte[] msg = new byte[516];
			msg[1] = 3;
			msg[2] = (byte)(blockNumber & 0xFF);
			msg[3] = (byte)((blockNumber >> 8) & 0xFF);

			//Adds the data to the byte array
			for(int j = 0, k = 4; j < data.length && k < msg.length; j++, k++)
			{
				msg[k] = data[j];
			}

			//Creates DatagramPacket and returns it
			DatagramPacket send = null;
			try {
				send = new DatagramPacket(msg, msg.length, InetAddress.getLocalHost(), portNumber);
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}
			return send;
		}

		@Override
		public void run()
		{
			sendReceive();
			removeThread(this);
		}
	}

	/*
	 *   Creates a thread that will process a write request
	 */
	private class WriteThread extends Thread
	{
		private DatagramSocket socket;
		private int hostPort;
		private String filename;

		/*
		 *   Creates new socket and initiates filename and port
		 */
		public WriteThread(int hostPort, String filename)
		{
			this.hostPort = hostPort;
			this.filename = filename;
			try {
				socket = new DatagramSocket();
			} catch (SocketException e) {
				e.printStackTrace();
			}
		}
		
		public String getFilename()
		{
			return filename;
		}

		/*
		 *   Sends request to intermediate host then writes to the file  
		 */
		private synchronized void sendReceive()
		{
			int ACKcounter=0;
			byte[] b = new byte[4];
			b[0]=0;
			b[1]=4;
			b[2]=0;
			b[3]=(byte)ACKcounter;
			DatagramPacket ack = null;
			try {
				ack = new DatagramPacket(b, b.length, InetAddress.getLocalHost(), hostPort);
			} catch (UnknownHostException e2) {
				e2.printStackTrace();
			}

			try {
				socket.send(ack);
			} catch (IOException e2) {
				e2.printStackTrace();
			}
			//Sets up Error Detection Variables
			int blockID=-1;
			int incomingBlockID=0;
			boolean delayed = false; 
			boolean lost = false; 
			//Sets up fileOuputStream
			int index = -1;
			byte[] receiveMsg;
			FileOutputStream fos = null;
			try {
				fos = new FileOutputStream(new File(directory.getAbsolutePath() + "\\" + filename));
			} catch (FileNotFoundException e1) {
				e1.printStackTrace();
			}

			//Continually write to the file
			while(index == -1) {
				
				//resets the packet duplicate checker;
				
				//Receives data from intermediate host
				receiveMsg = new byte[516];
				DatagramPacket receivePacket = new DatagramPacket(receiveMsg, receiveMsg.length);
				try {
					socket.setSoTimeout(2000);
					System.out.println("Waiting for data. . .");
					socket.receive(receivePacket);
				}catch (SocketTimeoutException ste){
					delayed=true; 
				} catch (IOException e) {
					e.printStackTrace();
				}
				if(delayed){
					try {
						socket.setSoTimeout(4000);
						System.out.println("Packet declared delayed, Still waiting for data. . .");
						socket.receive(receivePacket);
						
					} catch (SocketTimeoutException ste) {
						lost = true;
						delayed= false;
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				
				if (lost){
					try{
						socket.send(ack);
					} catch (IOException e){
						e.printStackTrace();
					}
				} else {
					incomingBlockID = ((receivePacket.getData()[2]&0xFF)<<8) | (receivePacket.getData()[3] & 0xFF);
					
					if(incomingBlockID == ACKcounter+1){
						ACKcounter=incomingBlockID;
						b[2]=(byte)(ACKcounter & 0xFF);
						b[3]=(byte)((ACKcounter >> 8)& 0xFF);
						try {
							ack = new DatagramPacket(b, b.length, InetAddress.getLocalHost(), hostPort);
						} catch (UnknownHostException e2) {
							e2.printStackTrace();
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
						//Writes data to the file
						if(index == -1){
							try {
								fos.write(data);
							} catch (IOException e) {
								e.printStackTrace();
							}

							//Creates and sends acknowledgement to the intermediate host
							try {
								socket.send(ack);
							}catch (IOException IOE){
								IOE.printStackTrace();
								System.exit(1);
							}
							//Writes the last bit of data to the file
						} else{
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
					}else if (incomingBlockID <= ACKcounter){
						System.out.println("incoming block is a duplicate, resending previous ACK");
						try{
							socket.send(ack);
						} catch (IOException e){
							e.printStackTrace();
						}
					} else {
						System.out.println("Unexpected Error Occured, Recieved Future data Packet");
						try {
							socket.send(ack);
						} catch(IOException e){
							e.printStackTrace();
						}
					}
				} 
			}//END While
		}
		@Override
		public void run()
		{
			sendReceive();
			removeThread(this);
		}
	}

	/*
	 *    Creates a server instance and runs it
	 */
	public static void main(String args[])
	{
		Thread server = new Server();
		server.start();
	}
}
