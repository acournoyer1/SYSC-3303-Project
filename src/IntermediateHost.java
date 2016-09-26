import java.io.*;
import java.net.*;
import java.util.Arrays;

public class IntermediateHost extends Thread {
	private DatagramSocket receiveSocket;
	private DatagramSocket sendReceiveSocket;
	
	private static final int PORT_NUMBER = 23;
	private static final int SERVER_PORT_NUMBER = 69;
	
	public IntermediateHost()
	{
		try {
			receiveSocket = new DatagramSocket(PORT_NUMBER);
		} catch (SocketException e) {
			e.printStackTrace();
			System.exit(1);
		}
		try {
			sendReceiveSocket = new DatagramSocket();
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
			
			DatagramPacket sendPacketServer = null;
			try {
				sendPacketServer = new DatagramPacket(msg, msg.length, InetAddress.getLocalHost(), SERVER_PORT_NUMBER);
			} catch (UnknownHostException e) {
				e.printStackTrace();
				System.exit(1);
			}
			
			System.out.println("Sending request to Server: " + Converter.convertMessage(msg));
			try{
				sendReceiveSocket.send(sendPacketServer);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			System.out.println("Host waiting...");
			msg = new byte[4];
			DatagramPacket receivedPacketServer = new DatagramPacket(msg, msg.length);
			
			try{
				sendReceiveSocket.receive(receivedPacketServer);
			} catch (IOException e){
				e.printStackTrace();
				System.exit(1);
			}
			
			System.out.println("Response received from Server: " + Arrays.toString(msg));
			
			DatagramPacket sendPacketClient = null;
			try{
				sendPacketClient = new DatagramPacket(msg, msg.length, InetAddress.getLocalHost(), receivedPacketClient.getPort());
			} catch (UnknownHostException e){
				e.printStackTrace();
				System.exit(1);
			}
			
			System.out.println("Sending response to Client: " + Arrays.toString(msg) + "\n");
			try{
				sendReceiveSocket.send(sendPacketClient);
			} catch(IOException e){
				e.printStackTrace();
				System.exit(1);
			}
		}
	}
	
	@Override
	public void run()
	{
		this.sendReceive();
	}
}
