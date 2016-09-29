import java.io.*;
import java.net.*;
import java.util.Arrays;

/**
 * Write a description of class Client here.
 * 
 * @author (your name) 
 * @version (a version number or a date)
 */
public class Client extends Thread
{
	private static final int PORT_NUMBER = 23;
	
    private DatagramSocket socket;
    

    /**
     * Constructor for objects of class Client
     */
    public Client()
    {
        try
        {
            socket = new DatagramSocket();
        }
        catch(IOException e)
        {
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private synchronized DatagramPacket buildPacket(String mode, String filename, ActionType action)
    {
        byte[] request = new byte[100];
        request[0] = 0;
        if(action == ActionType.READ)
        {
            request[1] = 1;
        }
        else if(action == ActionType.WRITE)
        {
            request[1] = 2;
        }
        else
        {
            request[1] = 3;
        }
        int i = 2;
        if(filename.getBytes() != null)
        {
	    FileIm
            for(byte b: filename.getBytes())
            {
                request[i++] = b;
            }
        }
        request[i++] = 0;
        if(mode.getBytes() != null) 
        {
            for(byte b: mode.getBytes())
            {
                request[i++] = b;
            }
        }
        request[i++] = 0;
        try {
		return new DatagramPacket(request, request.length, InetAddress.getLocalHost(), PORT_NUMBER);
	} catch (UnknownHostException e) {
		e.printStackTrace();
		return null;
	}
        
    }
    
    private synchronized void sendReadReceive(DatagramPacket message)
    {
    	System.out.println("Sending request to Host: " + Converter.convertMessage(message.getData()));
    	try {
		socket.send(message);
	} catch (IOException e) {
		e.printStackTrace();
	}
		
	if (message.getData()[1] == 2) byte[] receiveMsg = new byte[4];
	else byte[] receiveMsg = new byte[516];
	
    	DatagramPacket receivePacket = new DatagramPacket(receiveMsg, receiveMsg.length);
    	try {
		socket.receive(receivePacket);
	} catch (IOException e) {
		e.printStackTrace();
	}
	FileOutputStream file = new FileOutputStream(
	System.out.println("Response received from Host: " + Arrays.toString(receiveMsg) + "\n");
    	if (message.getData()[1] == 2)
    }
    
    private synchronized void sendWriteReceive(DatagramPacket message, String filename)
    {
    	byte[] data = message.getData();
    	FileInputStream is = new FileInputStream(filename);
    	System.out.println("Sending request to Host: " + Converter.convertMessage(message.getData()));
    	try {
		socket.send(message);
	} catch (IOException e) {
		e.printStackTrace();
	}
		
	byte[] receiveMsg = new byte[4];
	
    	DatagramPacket receivePacket = new DatagramPacket(receiveMsg, receiveMsg.length);
    	try {
		socket.receive(receivePacket);
	} catch (IOException e) {
		e.printStackTrace();
	}
	FileOutputStream file = new FileOutputStream(
	System.out.println("Response received from Host: " + Arrays.toString(receiveMsg) + "\n");
    	if (message.getData()[1] == 2)
    }
    
    @Override 
    public void run()
    {
    	DatagramPacket msg;
    		msg = buildPacket("ocTeT", "test.txt", ActionType.READ);
		sendReadReceive(msg, "test.txt");		
    	}
    	else
    	{
    		msg = buildPacket("ocTeT", "test.txt", ActionType.WRITE);
		sendWriteReceive(msg, "test.txt");
    	}
   
    	msg = buildPacket("netASCII", "test.txt", ActionType.INVALID);
    	//sendReceive(msg, "test.txt");
    	socket.close();
    }
    
    public enum ActionType
    {
        READ, WRITE, INVALID
    }
    
    public static void main(String args[])
    {
    	//Thread serverThread = new Server();
    	//Thread hostThread = new IntermediateHost();
    	//Thread clientThread = new Client();
    	
    	//serverThread.start();
    	//hostThread.start();
    	//try {
//		Thread.sleep(1000);
//	} catch (InterruptedException e) {
//		e.printStackTrace();
	//}
        // clientThread.start();
    }
}
