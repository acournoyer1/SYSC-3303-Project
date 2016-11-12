import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * Creates a instance of intermediate host the mediates requests between client and server
 * 
 * Team 11  
 * V1.16
 */
public class IntermediateHost {
	private DatagramSocket receiveSocket;
	private ArrayList<Thread> threads;
	private boolean verbose;
	private Error error;
	
	//Well known ports for intermediate and server
	private static final int PORT_NUMBER = 23;
	private static final int SERVER_PORT_NUMBER = 69;
	
	/*
	*   Creates new thread and receiveSocket
	*/
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
	
	/*
	*   Waits for DatagramPacket from client then creates new client to deal with it
	*/
	private synchronized void sendReceive()
	{
		while(true)
		{
			//Receives DatagramPacket from client
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
			
			//Creates new thread to deal with DatagramPacket
			HostThread thread = new HostThread(receivedPacketClient.getPort(), msg);
			threads.add(thread);
			thread.start();
		}
	}
	
	
	/*
	*    Creates a host instance and runs it
	*/
	public static void main(String args[])
    {
    	IntermediateHost host = new IntermediateHost();
    	host.setUp();
    }
	
	public void setUp()
	{
		new HostSetup();
	}
	
	/*
	*   Creates the host thread that deals with the DatagramPacket
	*/
	private class HostThread extends Thread
	{
		int clientPort;
		int serverPort;
		DatagramSocket socket;
		byte[] request;
		
		/*
		*   Creates new socket for thread
		*/
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
		
		/*
		*    Sends DatagramPacket to the server and process the request
		*/
		private void sendReceive()
		{
			//Creates packet and sends it to the server
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
			
			//If request is a read
			if(request[1] == 1)
			{
				System.out.println("Read request");
				while(true)
				{
					//Creates a datagramPacket that will receive data from the server and sends it to the client
					byte[] data = new byte[516];
					DatagramPacket packet = new DatagramPacket(data, data.length);
					try {
						socket.receive(packet);
					} catch (IOException e) {
						e.printStackTrace();
					}
					serverPort = packet.getPort();
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
					//Creates acknowledgement packet and sends it to the server
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
			//If request is a write
			else if(request[1] == 2) 
			{
				while(true)
				{
					//Creates a acknowledgement packet and sends it to the client
					byte[] ack = new byte[4];
					DatagramPacket packet = new DatagramPacket(ack, ack.length);
					try {
						System.out.println("Receiving ACK packet");
						socket.receive(packet);
					} catch (IOException e) {
						e.printStackTrace();
					}
					serverPort = packet.getPort();
					try {
						System.out.println("Cloning ack");
						packet = new DatagramPacket(ack, ack.length, InetAddress.getLocalHost(), clientPort);
					} catch (UnknownHostException e) {
						e.printStackTrace();
					}
					try {
						System.out.println("Sending Ack to client: " + clientPort);
						socket.send(packet);
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					//Creates a datagramPacket that will receive data from the client and sends it to the server
					byte[] data = new byte[516];
					packet = new DatagramPacket(data, data.length);
					try {
						System.out.println("Receiving Data from client");
						socket.receive(packet);
					} catch (IOException e) {
						e.printStackTrace();
					}
					try {
						System.out.println("Cloning data packet");
						packet = new DatagramPacket(data, data.length, InetAddress.getLocalHost(), serverPort);
					} catch (UnknownHostException e) {
						e.printStackTrace();
					}
					try {
						System.out.println("Sending data packet to server:" + serverPort);
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
	
	@SuppressWarnings("serial")
	private class HostSetup extends JDialog
	{
		private ErrorPane errorPane;
		private JRadioButton verboseRadio;
		private JButton okButton;
		private JButton cancelButton;
		
		public HostSetup()
		{
			this.errorPane = new ErrorPane();
			this.verboseRadio = new JRadioButton("Verbose", true);
			this.okButton = new JButton("OK");
			this.cancelButton = new JButton("Cancel");
			
			JRadioButton quietRadio = new JRadioButton("Quiet");
			ButtonGroup group = new ButtonGroup();
			group.add(verboseRadio);
			group.add(quietRadio);
			
			JPanel p = new JPanel();
			p.add(verboseRadio);
			p.add(quietRadio);
			
			JPanel buttons = new JPanel();
			buttons.add(okButton);
			buttons.add(cancelButton);
			
			this.add(p, BorderLayout.NORTH);
			this.add(errorPane, BorderLayout.CENTER);
			this.add(buttons, BorderLayout.SOUTH);
			this.setResizable(false);
			errorPane.setBorder(new EmptyBorder(this.getInsets()));
			setUpListeners();
			this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
			this.setLocationRelativeTo(null);
			this.setTitle("Error Generator Setup");
			this.pack();
			this.setVisible(true);
		}
		
		private void setUpListeners()
		{
			okButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e) 
				{
					error = errorPane.getError();
					if(verboseRadio.isSelected()) verbose = true;
					else verbose = false;
					dispose();
					sendReceive();
				}
			});
			cancelButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e) 
				{
					System.out.println("Error Generator creation cancelled.\n");
					dispose();
					System.exit(0);
				}
			});
		}
	}
	
	@SuppressWarnings("serial")
	private class ErrorPane extends JPanel
	{
		private JComboBox<ErrorType> errorType;
		private JComboBox<PacketType> packetType;
		private JTextField blockField;
		
		public ErrorPane()
		{
			errorType = new JComboBox<ErrorType>(ErrorType.values());
			packetType = new JComboBox<PacketType>(PacketType.values());
			blockField = new JTextField("1");
			
			this.add(new JLabel("Error: "));
			this.add(errorType);
			this.add(packetType);
			this.add(blockField);
			blockField.setColumns(2);
		}
		
		public Error getError()
		{
			return new Error((ErrorType)errorType.getSelectedItem(), (PacketType)packetType.getSelectedItem(), Integer.parseInt(blockField.getText()));
		}
	}
	
	private class Error
	{
		private ErrorType errorType;
		private PacketType packetType;
		private int blockNumber;
		private boolean executed;
		
		public Error(ErrorType e, PacketType p, int blockNumber)
		{
			this.errorType = e;
			this.packetType = p;
			this.blockNumber = blockNumber;
			this.executed = false;
		}
		
		public boolean hasExecuted()
		{
			return executed;
		}
		
		public void execute()
		{
			executed = true;
		}
		
		public ErrorType getError()
		{
			return errorType;
		}
		
		public PacketType getPacketType()
		{
			return packetType;
		}
		
		public int getBlock()
		{
			return blockNumber;
		}
	}
	
	public enum ErrorType
	{
		LOST, DELAYED, DUPLICATED
	}
	
	public enum PacketType
	{
		DATA, ACK
	}
}

