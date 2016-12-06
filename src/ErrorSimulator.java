import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.MaskFormatter;

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
	private static String hostIP;		//IP address of the new host (localHost by default)
	//Well known ports for intermediate and server
	private static final int PORT_NUMBER = 23;
	private static final int SERVER_PORT_NUMBER = 69;
	
	/*
	*   Creates new thread and receiveSocket
	*/
	public ErrorSimulator()
	{
		threads = new ArrayList<Thread>();
		hostIP = readFile("IPAddress.txt");		//host IP Address
		//hostIP = 192.168.0.18;
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
	
	/**
	 * Creates a new error simulator instance
	 */
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
			sendPacketServer = new DatagramPacket(request, request.length,  createIp(hostIP), SERVER_PORT_NUMBER);
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
			packet = new DatagramPacket(data, data.length, createIp(hostIP), clientPort);
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
					case CORRUPTED:
						break;
					case WRONG_TID:
						if(verbose) System.out.println("Sending an extra " + pt + " to the destination from a wrong TID.");
						socket.send(packet);
						new WrongTIDThread(packet.getData(), clientPort).start();
						error.execute();
						break;
					default:
						break;
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
				packet = new DatagramPacket(data, data.length,  createIp(hostIP), currentDest);
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
							if(verbose)
									System.out.println("Delaying packet. . . " + pt);
							//Delay the packet by sleeping the thread before sending
							new DelayThread(this.socket, error.getDelay(), packet).start();
							error.execute();
							break;
						case CORRUPTED:
							break;
						case WRONG_TID:
							if(verbose) System.out.println("Sending an extra " + pt + " to the destination from a wrong TID.");
							socket.send(packet);
							new WrongTIDThread(packet.getData(), currentDest).start();
							error.execute();
							break;
						default:
							break;
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
	
	/**
	 * Creates a thread that will delay the datagram being sent to
	 * simulate a delayed packet as well as lost packets
	 * 
	 * @author Team 11
	 *
	 */
	private class DelayThread extends Thread 
	{
		private DatagramSocket socket;
		private DatagramPacket p;
		private int delay;
		
		public DelayThread(DatagramSocket socket, int delay, DatagramPacket p)
		{
			this.socket = socket;
			this.delay = delay;
			this.p = p;
		}
		
		@Override
		public void run()
		{
			try {
				Thread.sleep(delay);
				socket.send(p);
			} catch (InterruptedException | IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Creates a thread that will simulate a invalid TID error.
	 * 
	 * @author Team 11
	 *
	 */
	private class WrongTIDThread extends Thread
	{
		private DatagramSocket socket;
		private byte[] msg;
		private int destination;
		
		public WrongTIDThread(byte[] msg, int destination)
		{
			try {
				this.socket = new DatagramSocket();
			} catch (SocketException e) {
				e.printStackTrace();
			}
			this.destination = destination;
			this.msg = msg;
		}
		
		@Override
		public void run()
		{
			try {
				DatagramPacket p = new DatagramPacket(msg, msg.length, createIp(hostIP), destination);
				socket.send(p);
			} catch (IOException e) {
				e.printStackTrace();
			}
			byte[] receiveMsg = new byte[100];
			DatagramPacket receivePacket = new DatagramPacket(receiveMsg, receiveMsg.length);
			try {
				socket.receive(receivePacket);
				System.out.println("Error packet received. " + Converter.convertErrorMessage(receivePacket.getData()) + ".\n");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Creates the base GUI that the host information will be displayed 
	 * within. 
	 * 
	 * @author Team 11
	 *
	 */
	@SuppressWarnings("serial")
	private class HostSetup extends JDialog
	{
		private ErrorPane errorPane;
		private JRadioButton verboseRadio;
		private JButton okButton;
		private JButton cancelButton;
		private JRadioButton defaultIPRadio;
		private JTextField IPAddressField;
		private ErrorSimulator h;
		
		public HostSetup(ErrorSimulator h)
		{
			
			try {
				this.IPAddressField = new JFormattedTextField(new MaskFormatter("###.###.###.###"));
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			this.IPAddressField.setColumns(10);
			this.h = h;
			this.errorPane = new ErrorPane();
			this.verboseRadio = new JRadioButton("Verbose", true);
			this.okButton = new JButton("OK");
			this.cancelButton = new JButton("Cancel");
			this.defaultIPRadio = new JRadioButton("Default IP", true);
			JRadioButton newIPRadio = new JRadioButton("New IP");
			JRadioButton quietRadio = new JRadioButton("Quiet");
			ButtonGroup g1 = new ButtonGroup();
			ButtonGroup g2 = new ButtonGroup();
			g1.add(verboseRadio);
			g1.add(quietRadio);
			g2.add(defaultIPRadio);
			g2.add(newIPRadio);
			
			JPanel p1 = new JPanel();
			JPanel p2 = new JPanel();
			p1.add(verboseRadio);
			p1.add(quietRadio);
			p2.add(defaultIPRadio);
			p2.add(newIPRadio);
			p2.add(IPAddressField);
		
			
			JPanel buttons = new JPanel();
			buttons.add(okButton);
			buttons.add(cancelButton);
			
			this.add(p1, BorderLayout.WEST);
			this.add(p2, BorderLayout.EAST);
			this.add(errorPane, BorderLayout.NORTH);
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
					
					if(defaultIPRadio.isSelected()){
						////FOUND IP CHANGER
						try {
					
						addIPAddress(InetAddress.getLocalHost().toString());
						} catch (UnknownHostException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
					}
					else{
						addIPAddress(IPAddressField.getText());		//if newIp is selected, write the new IP to the text file
					}
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
	
	/**
	 * Creates the GUI panel in which the user can choose which error
	 * scenario they wish to simulate
	 * 
	 * @author Team 11
	 *
	 */
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
				@SuppressWarnings({ "unchecked", "static-access" })
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
				@SuppressWarnings({ "unchecked", "static-access" })
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
		
		@SuppressWarnings("unused")
		public Field getField()
		{
			return field;
		}
		
	}
	
	

	/**
	 *readFile method:
	 *reads a text file line by line and returns a string
	 */
	private static String readFile(String path) 
	{
			  byte[] encoded;
			  String s = "";
			try {
				encoded = Files.readAllBytes(Paths.get(path));
				s = new String(encoded);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			  return s;
	}
	
	/**
	 * createIp method: 
	 *creates a InetAddress object with a string. Takes a string input i.e "127.0.0.1"
	 */
	private InetAddress createIp(String ip){		
		
		InetAddress host = null;
		try {
			host = InetAddress.getByName(ip);
		} catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		return host;


		

			
		
	}
	
	/**
	 * addIPAddress method:
	 * Writes to a text file a given IP address 
	 * @param ip
	 */
	private void addIPAddress(String ip){
		
		FileWriter fw;
		PrintWriter pw;
		try {
			fw = new FileWriter("IPAddress.txt");		//writes to the file the local IP Address if the file is empty
		    pw = new PrintWriter(fw);
		    pw.print(ip);
			pw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	
	/**
	 * 	Defines all of the types of errors that are available
	 */
	private enum ErrorType
	{
		LOST, DELAYED, DUPLICATED, CORRUPTED, WRONG_TID
	}
	
	/**
	 * Defines the type of packet that a error is being generated in
	 */
	private enum PacketType
	{
		DATA, ACK, REQUEST
	}
	
	/**
	 *	Extra fields to change the type of error
	 */
	private enum Field
	{
		OPCODE, BLOCKNUMBER, DATA, FILENAME, DELAY_TIME
	}
}

