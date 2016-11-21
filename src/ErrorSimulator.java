import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
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
public class ErrorSimulator {
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
	public ErrorSimulator()
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
			if(verbose)
				System.out.println("Host waiting...");
			byte[] msg = new byte[100];
			DatagramPacket receivedPacketClient = new DatagramPacket(msg, msg.length);
			try {
				receiveSocket.receive(receivedPacketClient);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			if(verbose)
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
    	ErrorSimulator host = new ErrorSimulator();
    	host.setUp();
    }
	
	public void setUp()
	{
		new HostSetup(this);
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
			if(verbose)
				System.out.println("Sending request to Server: " + Converter.convertMessage(request));
			try{
				socket.send(sendPacketServer);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			
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
				//Check if the requested block matches the packet block
				PacketType pt = packet.getData()[1] == 3 ? PacketType.DATA : PacketType.ACK;
				if(!error.hasExecuted() && error.getBlock() == (packet.getData()[2]/256 + packet.getData()[3]) && error.getPacketType() == pt){
					switch(error.getError()){
					case LOST:
						if(verbose)
							System.out.println("Losing packet. . . " + pt);
						error.execute();
						break;
					case DUPLICATED:
						try{
							if(verbose)
								System.out.println("Duplicating Packet. . . " + pt);
							socket.send(packet);
							socket.send(packet);
							error.execute();
						}catch(SocketException e){}
						break;
					case DELAYED:
						try{
							if(verbose)
								System.out.println("Delaying packet. . . " + pt);
							//Delay the packet by sleeping the thread before sending
							Thread.sleep(error.getDelay());
							socket.send(packet);
							error.execute();
							break;
						} catch(InterruptedException ie){
							ie.printStackTrace();
						}
					}
				}
				else
					socket.send(packet);
			} catch (IOException e) {
				e.printStackTrace();
			}

			while(true)
			{
				data = new byte[516];
				packet = new DatagramPacket(data, data.length);
				try {
					socket.receive(packet);
				} catch (IOException e) {
					e.printStackTrace(); 
				}
				int currentDest = packet.getPort() == serverPort ? clientPort : serverPort;
				try {
					packet = new DatagramPacket(data, data.length, InetAddress.getLocalHost(), currentDest);
				} catch (UnknownHostException e) {
					e.printStackTrace();
				}
				try {
					//Check if the requested block matches the packet block
					PacketType pt = packet.getData()[1] == 3 ? PacketType.DATA : PacketType.ACK;
					if(!error.hasExecuted() && error.getBlock() == (packet.getData()[2]/256 + packet.getData()[3]) && error.getPacketType() == pt){
						switch(error.getError()){
						case LOST:
							if(verbose)
								System.out.println("Losing packet. . . " + pt);
							error.execute();
							break;
						case DUPLICATED:
							try{
								if(verbose)
									System.out.println("Duplicating Packet. . . " + pt);
								socket.send(packet);
								socket.send(packet);
								error.execute();
							}catch(SocketException e){}
							break;
						case DELAYED:
							try{
								if(verbose)
									System.out.println("Delaying packet. . . " + pt);
								//Delay the packet by sleeping the thread before sending
								Thread.sleep(error.getDelay());
								socket.send(packet);
								error.execute();
								break;
							} catch(InterruptedException ie){
								ie.printStackTrace();
							}
						}
					}
					else
						socket.send(packet);
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
	
	@SuppressWarnings("serial")
	private class HostSetup extends JDialog
	{
		private ErrorPane errorPane;
		private JRadioButton verboseRadio;
		private JButton okButton;
		private JButton cancelButton;
		
		private ErrorSimulator h;
		
		public HostSetup(ErrorSimulator h)
		{
			this.h = h;
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
			this.setTitle("Error Generator Setup");
			this.pack();
			this.setLocationRelativeTo(null);
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
					h.sendReceive();
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
		private JComboBox<Field> fields;
		private JTextField blockField;
		private JTextField corruptionField;
		
		private JPanel topPanel;
		private JPanel bottomPanel;
		
		public ErrorPane()
		{
			topPanel = new JPanel();
			bottomPanel = new JPanel();
			errorType = new JComboBox<ErrorType>(ErrorType.values());
			packetType = new JComboBox<PacketType>(PacketType.values());
			fields = new JComboBox<Field>(Field.values());
			fields.removeItem(Field.FILENAME);
			fields.setEnabled(false);
			blockField = new JTextField("0");
			corruptionField = new JTextField("0");
			corruptionField.setEditable(false);
			
			topPanel.add(new JLabel("Error: "));
			topPanel.add(errorType);
			topPanel.add(packetType);
			topPanel.add(blockField);
			blockField.setColumns(2);
			
			bottomPanel = new JPanel();
			bottomPanel.add(new JLabel("Change "));
			bottomPanel.add(fields);
			bottomPanel.add(new JLabel(" to "));
			bottomPanel.add(corruptionField);
			corruptionField.setColumns(5);
			
			JSplitPane split = new JSplitPane();
			split.setOrientation(JSplitPane.VERTICAL_SPLIT);
			split.setTopComponent(topPanel);
			split.setBottomComponent(bottomPanel);
			split.setBorder(BorderFactory.createEmptyBorder());
			split.setDividerSize(0);
			split.setEnabled(false);
			
			errorType.addItemListener(new ItemListener()
			{
				@Override
				public void itemStateChanged(ItemEvent e) 
				{
					if(e.getStateChange() == e.SELECTED)
					{
						if(((JComboBox<ErrorType>)e.getSource()).getSelectedItem() == ErrorType.CORRUPTED)
						{
							fields.setEnabled(true);
							corruptionField.setEditable(true);
						}
						else if(((JComboBox<ErrorType>)e.getSource()).getSelectedItem() == ErrorType.DELAYED)
						{
							fields.removeAllItems();
							fields.addItem(Field.DELAY_TIME);
							fields.setEnabled(true);
							corruptionField.setEditable(true);
						}
						else
						{
							fields.setEnabled(false);
							corruptionField.setEditable(false);
						}
					}	
				}	
			});
			packetType.addItemListener(new ItemListener()
			{
				@Override
				public void itemStateChanged(ItemEvent e) 
				{
					if(e.getStateChange() == e.SELECTED && ((JComboBox<ErrorType>)e.getSource()).getSelectedItem() == ErrorType.CORRUPTED)
					{
						if(((JComboBox<PacketType>)e.getSource()).getSelectedItem() == PacketType.REQUEST)
						{
							fields.removeAllItems();
							fields.addItem(Field.OPCODE);
							fields.addItem(Field.FILENAME);
						}
						else if(((JComboBox<PacketType>)e.getSource()).getSelectedItem() == PacketType.ACK)
						{
							fields.removeAllItems();
							fields.addItem(Field.OPCODE);
							fields.addItem(Field.BLOCKNUMBER);
						}
						else if(((JComboBox<PacketType>)e.getSource()).getSelectedItem() == PacketType.DATA)
						{
							fields.removeAllItems();
							fields.addItem(Field.OPCODE);
							fields.addItem(Field.BLOCKNUMBER);
							fields.addItem(Field.DATA);
						}
					}	
				}
			});
			
			this.add(split);
		}
		
		public Error getError()
		{
			if(errorType.getSelectedItem() != ErrorType.CORRUPTED)
			{
				Error e = new Error((ErrorType)errorType.getSelectedItem(), (PacketType)packetType.getSelectedItem(), Integer.parseInt(blockField.getText()));
				if(errorType.getSelectedItem() == ErrorType.DELAYED) e.setDelay(Integer.parseInt(corruptionField.getText()));
				return e;
			}
			else
			{
				return new CorruptionError((ErrorType)errorType.getSelectedItem(), (PacketType)packetType.getSelectedItem(), Integer.parseInt(blockField.getText()), (Field)fields.getSelectedItem());
			}
			
		}
	}
	
	private class Error
	{
		private ErrorType errorType;
		private PacketType packetType;
		private int blockNumber;
		private int delayTime;
		private boolean executed;
		
		public Error(ErrorType e, PacketType p, int blockNumber)
		{
			this.errorType = e;
			this.packetType = p;
			this.blockNumber = blockNumber;
			this.executed = false;
		}
		
		public void setDelay(int delay)
		{
			delayTime = delay;
		}
		
		public int getDelay()
		{
			return delayTime;
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
	
	private class CorruptionError extends Error 
	{
		private Field field;
		
		public CorruptionError(ErrorType e, PacketType p, int blockNumber, Field f)
		{
			super(e, p, blockNumber);
			this.field = f;
		}
		
		public Field getField()
		{
			return field;
		}
		
	}
	
	private enum ErrorType
	{
		LOST, DELAYED, DUPLICATED, CORRUPTED
	}
	
	private enum PacketType
	{
		DATA, ACK, REQUEST
	}
	
	private enum Field
	{
		OPCODE, BLOCKNUMBER, DATA, FILENAME, DELAY_TIME
	}
}

