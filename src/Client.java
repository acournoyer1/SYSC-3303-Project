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
	FileOutputStream fos=null;
	try {
		fos = new FileOutputStream(new File("c" + filename));
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
			
		}
		else{
			try {
				fos.write(receiveMsg, 0, index);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
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

    		System.out.println("Response received from Host: " + Arrays.toString(receiveMsg) + "\n");
		
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
}
