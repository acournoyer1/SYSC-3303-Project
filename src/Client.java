import java.io.*;
import java.net.*;
import java.util.Arrays;

/**
 * Write a description of class Client here.
 * 
 * Team 11  
 * V1.0
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
    
    private synchronized DatagramPacket buildRequest(String mode, String filename, ActionType action)
    {
        byte[] request = new byte[100];
        request[0] = 0;
        if(action == ActionType.READ) {
            request[1] = 1;
        }
        else if(action == ActionType.WRITE) {
            request[1] = 2;
        }
        else {
            request[1] = 3;
        }
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
		return new DatagramPacket(request, request.length, InetAddress.getLocalHost(), PORT_NUMBER);
	} catch (UnknownHostException e) {
		e.printStackTrace();
		return null;
	}
        
    }
    
    private synchronized DatagramPacket buildData(byte[] data, int block#)
    {
        byte[] msg = new byte[516]
	byte[1] = 3;
	byte[3] = i;
	for(j = 0, k = 4; j < data.length() && k < msg.length; j++, k++){
		msg[k] = data[j];
	}
	return msg;
    }
    
    private synchronized void sendReadReceive(String filename)
    {
   	DatagramPacket message = buildRequest("ocTeT", filename, ActionType.WRITE);
    	System.out.println("Sending request to Host: " + Converter.convertMessage(message.getData()));
    	try {
		socket.send(message);
	} catch (IOException e) {
		e.printStackTrace();
	}
	
	int index = -1;
	//File file = new File("Client//" + filename);
	byte[] receiveMsg;
	FileOutputStream fos = new FileOutputStream(new File("Client//" + filename));
	
	while(index == -1) {
		receiveMsg = new byte[516];
    		DatagramPacket receivePacket = new DatagramPacket(receiveMsg, receiveMsg.length);
    		try {
			socket.receive(receivePacket);
		} catch (IOException e) {
			e.printStackTrace();
		}
	
    		for(int i = 4; i < msg.length; i++) {
			if(receiveMsg[i] == 0){
				index = i;
				i = data.length;
			}
		}
		if(index == -1) fos.write(receiveMsg);
		else fos.write(data, 0, index);
	}
    }
    
    private synchronized void sendWriteReceive(String filename)
    {
	DatagramPacket message = buildRequest("ocTeT", filename, ActionType.WRITE);
    	System.out.println("Sending request to Host: " + Converter.convertMessage(message.getData()));
    	try {
		socket.send(message);
	} catch (IOException e) {
		e.printStackTrace();
	}
	
    	byte[] data = new byte[512];
    	FileInputStream is = new FileInputStream(filename);
	byte[] receiveMsg = new byte[4];
	int = 0;
	
	While(is.available()) {
		receiveMsg = new byte[4];
	
    		DatagramPacket receivePacket = new DatagramPacket(receiveMsg, receiveMsg.length);
    		try {
			socket.receive(receivePacket);
		} catch (IOException e) {
			e.printStackTrace();
		}

		System.out.println("Response received from Host: " + Arrays.toString(receiveMsg) + "\n");
		
		is.read(data);
		DatagramPacket msg = buildData(data, i++);
		try {
			socket.send(msg);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
    }
    
    @Override 
    public void run()
    {
	sendReadReceive("test.txt");	
	sendWriteReceive("test.txt");
   
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
	//	Thread.sleep(1000);
	//} catch (InterruptedException e) {
	//	e.printStackTrace();
	//}
        // clientThread.start();
    }
}
