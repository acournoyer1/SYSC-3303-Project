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
