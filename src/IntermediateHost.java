import java.io.*;
import java.net.*;
import java.util.ArrayList;

public class IntermediateHost extends Thread {
	private DatagramSocket receiveSocket;
	private ArrayList<Thread> threads;
	
	private static final int PORT_NUMBER = 23;
	private static final int SERVER_PORT_NUMBER = 69;
	
	public IntermediateHost()
	{
		threads = new ArrayList<Thread>();
		try {
			receiveSocket = new DatagramSocket(PORT_NUMBER);
		} catch (SocketException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	private synchronized void sendReceive()
	{
		while(true)
		{
			System.out.println("Host waiting...");
			byte[] msg = new byte[100];
			DatagramPacket receivedPacketClient = new DatagramPacket(msg, msg.length);
			try {
				receiveSocket.receive(receivedPacketClient);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			System.out.println("Request received from Client: " + Converter.convertMessage(msg));
			
			HostThread thread = new HostThread(receivedPacketClient.getPort(), msg);
			thread.start();
		}
	}
	
	@Override
	public void run()
	{
		this.sendReceive();
	}
	
	private class HostThread extends Thread
	{
		int clientPort;
		int serverPort;
		DatagramSocket socket;
		byte[] request;
		
		public HostThread(int port, byte[] msg)
		{
			this.clientPort = port;
			this.request = msg;
			try {
				socket = new DatagramSocket();
			} catch (SocketException e) {
				e.printStackTrace();
			}
		}
		
		private void sendReceive()
		{
			DatagramPacket sendPacketServer = null;
			try {
				sendPacketServer = new DatagramPacket(request, request.length, InetAddress.getLocalHost(), SERVER_PORT_NUMBER);
			} catch (UnknownHostException e) {
				e.printStackTrace();
				System.exit(1);
			}
			
			System.out.println("Sending request to Server: " + Converter.convertMessage(request));
			try{
				socket.send(sendPacketServer);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			
			if(request[1] == 1) //i.e. a READ
			{
				while(true)
				{
					byte[] data = new byte[516];
					DatagramPacket packet = new DatagramPacket(data, data.length);
					try {
						socket.receive(packet);
					} catch (IOException e) {
						e.printStackTrace();
					}
					try {
						packet = new DatagramPacket(data, data.length, InetAddress.getLocalHost(), clientPort);
					} catch (UnknownHostException e) {
						e.printStackTrace();
					}
					try {
						socket.send(packet);
					} catch (IOException e) {
						e.printStackTrace();
					}
					byte[] ack = new byte[4];
					packet = new DatagramPacket(ack, ack.length);
					try {
						socket.receive(packet);
					} catch (IOException e) {
						e.printStackTrace();
					}
					try {
						packet = new DatagramPacket(ack, ack.length, InetAddress.getLocalHost(), serverPort);
					} catch (UnknownHostException e) {
						e.printStackTrace();
					}
					try {
						socket.send(packet);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
			else if(request[1] == 2) //i.e. a WRITE
			{
				while(true)
				{
					byte[] ack = new byte[4];
					DatagramPacket packet = new DatagramPacket(ack, ack.length);
					try {
						socket.receive(packet);
					} catch (IOException e) {
						e.printStackTrace();
					}
					try {
						packet = new DatagramPacket(ack, ack.length, InetAddress.getLocalHost(), clientPort);
					} catch (UnknownHostException e) {
						e.printStackTrace();
					}
					try {
						socket.send(packet);
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					byte[] data = new byte[516];
					packet = new DatagramPacket(data, data.length);
					try {
						socket.receive(packet);
					} catch (IOException e) {
						e.printStackTrace();
					}
					try {
						packet = new DatagramPacket(data, data.length, InetAddress.getLocalHost(), serverPort);
					} catch (UnknownHostException e) {
						e.printStackTrace();
					}
					try {
						socket.send(packet);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		
		@Override
		public void run()
		{
			this.sendReceive();
		}
	}
}

