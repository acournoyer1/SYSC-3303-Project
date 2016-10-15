import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;

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
			int j = 4;
			for(int i = 0; i < b.length; i++, j++)
			{
				b[i] = msg[j];
			}
			//Turns filename that is a byte array into a string
			String filename = new String(b);
			
			//Creates new read thread with filename
			if(msg[1] == 1)
			{
				System.out.println("The request is a valid read request.");
				System.out.println(filename);
				addThread(new ReadThread(receivedPacket.getPort(), filename));
			}
			//Creates new write thread with filename
			else
			{
				System.out.println("The request is a valid write request.");
				System.out.println(filename);
				addThread(new WriteThread(receivedPacket.getPort(), filename));
			}
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
		while(directory == null)
    	{
    		directory = getDirectory();
    	}
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

	/*
	*   Creates a thread that will process a read request
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
		*   Sends request to intermediate host then writes to the file  
		*/
		private synchronized void sendReceive()
		{
			//Sends request to the intermediate host
			DatagramPacket message = buildRequest("ocTeT", filename);
	    		System.out.println("Sending request to Host: " + Converter.convertMessage(message.getData()));
	    		try {
	    			socket.send(message);
	    		} catch (IOException e) {
	    			e.printStackTrace();
	    		}
		
			//Writes to the file
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
			byte i = 0;
		
			int available = 0;
			try {
				available = is.available();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			//Continually writes data to the file until no more data is available
			while(available > 0) {
				receiveMsg = new byte[4];
		
	    			DatagramPacket receivePacket = new DatagramPacket(receiveMsg, receiveMsg.length);
	    			try {
	    				socket.receive(receivePacket);
	    			} catch (IOException e) {
	    				e.printStackTrace();
	    			}

	    			System.out.println("Response received from Client: " + Arrays.toString(receiveMsg) + "\n");
				
	    			try {
	    				is.read(data);
				} catch (IOException e1) {
	    				e1.printStackTrace();
	    			}
	    			DatagramPacket msg = buildData(data, i++, receivePacket.getPort());
	    			try {
	    				socket.send(msg);
	    			} catch (IOException e) {
	    				e.printStackTrace();
	    			}
			}//END Loop
		}
		
		/*
		*  Builds the DatagramPacket that contains a request
		*/
		private synchronized DatagramPacket buildRequest(String mode, String filename)
		{
			//Initiates packet with 01 for read 
	        	byte[] request = new byte[100];
	        	request[0] = 0;
	        	request[1] = 1;
	        
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
			
			//Creates the DatagramPacket and returns it
	        	try {
	        		return new DatagramPacket(request, request.length, InetAddress.getLocalHost(), hostPort);
	        	} catch (UnknownHostException e) {
	        		e.printStackTrace();
	        		return null;
	        	}
	    	}
		
		/*
		*    Builds a DatagramPacket containing the data
		*/
		private synchronized DatagramPacket buildData(byte[] data, byte blockNumber, int portNumber)
	    	{
			//Adds the code for data block(03) followed by block number
	        	byte[] msg = new byte[516];
	        	msg[1] = 3;
	        	msg[3] = blockNumber;
			
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
		
		/*
		*   Sends request to intermediate host then writes to the file  
		*/
		private synchronized void sendReceive()
		{
			//Builds request packet and sends it to the intermediate host
			DatagramPacket message = buildRequest("ocTeT", filename);
	    		System.out.println("Sending request to Host: " + Converter.convertMessage(message.getData()));
	    		System.out.println(filename);
				System.out.println(directory.getAbsolutePath() + "\\" + filename);
	    		try {
	    			socket.send(message);
	    		} catch (IOException e) {
	    			e.printStackTrace();
	    		}
		
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
				//Receives data from intermediate host
	    			receiveMsg = new byte[516];
	    			DatagramPacket receivePacket = new DatagramPacket(receiveMsg, receiveMsg.length);
	    			try {
	    				socket.receive(receivePacket);
	    			} catch (IOException e) {
	    				e.printStackTrace();
	    			}
		
	    			for(int i = 4; i < receiveMsg.length; i++) {
	    				if(receiveMsg[i] == 0){
	    					index = i;
	    					i = receiveMsg.length;
	    				}
	    			}
				//Writes data to the file
	    			if(index == -1){
	    				try {
	    					fos.write(receiveMsg);
	    				} catch (IOException e) {
	    					e.printStackTrace();
	    				}

					//Creates and sends acknowledgement to the intermediate host
					byte[] b = {0, 4, 0, 0};
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
				//Writes the last bit of data to the file
	    			} else{
	    				try {
	    					fos.write(receiveMsg, 0, index);
	    				} catch (IOException e) {
	    					e.printStackTrace();
	    				}
	    			}
	    		}//END While
		}
		
		/*
		*  Builds the DatagramPacket that contains a request
		*/
		private synchronized DatagramPacket buildRequest(String mode, String filename)
	    	{
			//Initializes the byte array with a write request(02)
	       		byte[] request = new byte[100];
	       		request[0] = 0;
	        	request[1] = 2;
	        
			//Adds the filename to the byte array
	        	int i = 2;
	        	if(filename.getBytes() != null) {       	
	            		for(byte b: filename.getBytes()) {
	              			request[i++] = b;
	            		}//endFor 
	        	}
	        	request[i++] = 0;
	        
			//Adds the mode to the byte array
	        	if(mode.getBytes() != null) {
	            		for(byte b: mode.getBytes()) {
	                		request[i++] = b;
	            		}
	        	}
	       		request[i++] = 0;
	        
			//Creates the DatagramPacket with the byte array and then sends it to the intermediate host
	        	try {
	        		return new DatagramPacket(request, request.length, InetAddress.getLocalHost(), hostPort);
	        	} catch (UnknownHostException e) {
	        		e.printStackTrace();
	        		return null;
	        	}
	    	}
		
		@Override
		public void run()
		{
			sendReceive();
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
