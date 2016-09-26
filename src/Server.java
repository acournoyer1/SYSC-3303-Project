import java.io.*;
import java.net.*;
import java.util.Arrays;

public class Server extends Thread {
	DatagramSocket receiveSocket;
	DatagramSocket sendSocket;
	
	private static final int PORT_NUMBER = 69;
	
	public Server()
	{
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
			byte[] response = {0, 0, 0, 0};
			if(msg[1] == 1)
			{
				System.out.println("The request is a valid read request.");
				response[1] = 3;
				response[3] = 1;
			}
			else
			{
				System.out.println("The request is a valid write request.");
				response[1] = 4;
			}
			DatagramPacket sendPacket = null; 
			try {
				sendPacket = new DatagramPacket(response, response.length, InetAddress.getLocalHost(), receivedPacket.getPort());
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}

			System.out.println("Sending Response to Host: " + Arrays.toString(response) + "\n");
			try {
				sendSocket.send(sendPacket);
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
	}
	
	@Override
	public void run()
	{
		this.sendReceive();
	}
}
