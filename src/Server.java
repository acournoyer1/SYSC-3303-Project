import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;



public class Server extends Thread {
	DatagramSocket receiveSocket;
	DatagramSocket sendSocket;
	ArrayList<Thread> threads;
	
	private static final int PORT_NUMBER = 69;
	
	public Server()
	{
		threads = new ArrayList<Thread>();
		try {
			receiveSocket = new DatagramSocket(PORT_NUMBER);
		} catch (SocketException e) {
			e.printStackTrace();
		}
		try {
			sendSocket = new DatagramSocket();
		} catch (SocketException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	public synchronized void sendReceive()
	{
		while(true)
		{
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
			
			int index = -1;
			for(int i = 4; i < msg.length; i++)
			{
				if(msg[i] == 0)
				{
					index = i;
					i = msg.length;
				}
			}
			byte[] b = new byte[index - 4];
			int j = 4;
			for(int i = 0; i < b.length; i++, j++)
			{
				b[i] = msg[j];
			}
			
			String filename = new String(b);
			
			if(msg[1] == 1)
			{
				System.out.println("The request is a valid read request.");
				addThread(new ReadThread(receivedPacket.getPort(), filename));
			}
			else
			{
				System.out.println("The request is a valid write request.");
				addThread(new WriteThread(receivedPacket.getPort(), filename));
			}
		}
	}
	
	@Override
	public void run()
	{
		this.sendReceive();
	}
	
	private void addThread(Thread t)
	{
		threads.add(t);
		t.start();
	}

	private class ReadThread extends Thread
	{
		private DatagramSocket socket;
		private int hostPort;
		private String filename;
		
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
		
		private synchronized void sendReceive()
		{
			DatagramPacket message = buildRequest("ocTeT", filename);
	    	System.out.println("Sending request to Host: " + Converter.convertMessage(message.getData()));
	    	try {
	    		socket.send(message);
	    	} catch (IOException e) {
	    		e.printStackTrace();
	    	}
		
	    	byte[] data = new byte[512];
	    	FileInputStream is = null;
			try {
				is = new FileInputStream("c" + filename);
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
		
		private synchronized DatagramPacket buildRequest(String mode, String filename)
	    {
			//Build Request is a read for read thread. 
	        byte[] request = new byte[100];
	        request[0] = 0;
	        request[1] = 1;
	        
	        int i = 2;
	        
	        if(filename.getBytes() != null) {
	            for(byte b: filename.getBytes()) {
	                request[i++] = b;
	            }
	        }
	        request[i++] = 0;
	        if(mode.getBytes() != null) {
	            for(byte b: mode.getBytes()) {
	                request[i++] = b;
	            }
	        }
	        request[i++] = 0;
	        try {
	        	return new DatagramPacket(request, request.length, InetAddress.getLocalHost(), hostPort);
	        } catch (UnknownHostException e) {
	        	e.printStackTrace();
	        	return null;
	        }
	    }
		
		private synchronized DatagramPacket buildData(byte[] data, byte blockNumber, int portNumber)
	    {
	        byte[] msg = new byte[516];
	        msg[1] = 3;
	        msg[3] = blockNumber;
	        for(int j = 0, k = 4; j < data.length && k < msg.length; j++, k++)
	        {
	        	msg[k] = data[j];
	        }
	        DatagramPacket send = null;
	        try {
	        	send = new DatagramPacket(msg, msg.length, InetAddress.getLocalHost(), portNumber);
	        } catch (UnknownHostException e) {
	        	// TODO Auto-generated catch block
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
	
	private class WriteThread extends Thread
	{
		private DatagramSocket socket;
		private int hostPort;
		private String filename;
		
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
		
		private synchronized void sendReceive()
		{
			DatagramPacket message = buildRequest("ocTeT", filename);
	    	System.out.println("Sending request to Host: " + Converter.convertMessage(message.getData()));
	    	try {
	    		socket.send(message);
	    	} catch (IOException e) {
	    		e.printStackTrace();
	    	}
		
	    	int index = -1;
	    	//File file = new File("Server//" + filename);
	    	byte[] receiveMsg;
	    	FileOutputStream fos = null;
	    	try {
	    		fos = new FileOutputStream(new File("s" + filename));
	    	} catch (FileNotFoundException e1) {
	    		e1.printStackTrace();
	    	}
		
	    	while(index == -1) {
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
	    		if(index == -1){
	    			try {
	    				fos.write(receiveMsg);
	    			} catch (IOException e) {
	    				e.printStackTrace();
	    			}
	    			//LAST ADDITION ALEX, feel free to change variable names to something else, I just didn't want to break anything
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
				
	    		} else{
	    			try {
	    				fos.write(receiveMsg, 0, index);
	    			} catch (IOException e) {
	    				// TODO Auto-generated catch block
	    				e.printStackTrace();
	    			}
	    		}
	    	}//END While
		}
		
		private synchronized DatagramPacket buildRequest(String mode, String filename)
	    {
	        byte[] request = new byte[100];
	        request[0] = 0;
	        request[1] = 2;
	        
	        int i = 2;
	        if(filename.getBytes() != null) {       	
	            for(byte b: filename.getBytes()) {
	                request[i++] = b;
	            }//endFor 
	        }
	        request[i++] = 0;
	        
	        if(mode.getBytes() != null) {
	            for(byte b: mode.getBytes()) {
	                request[i++] = b;
	            }
	        }
	        request[i++] = 0;
	        
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
	
	
	public static void main(String args[])
    {
    	Thread server = new Server();
    	Thread host = new IntermediateHost();
    	Thread client = new Client();
    	
    	server.start();
    	host.start();
    	try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
    	client.start();
    }
}
