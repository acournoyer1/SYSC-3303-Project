import java.io.*;
import java.net.*;
import java.util.Arrays;

/*
 * Creates a instance of client which sends and receives files from/to server through the intermediate host
 * 
 * Team 11  
 * V1.16
 */
public class Client extends Thread
{
    private static final int PORT_NUMBER = 23;
    private DatagramSocket socket;
    

    /*
     * Constructor for objects of class Client
     */
    public Client()
    {
        try
        {
            socket = new DatagramSocket();	//Creates socket that sends/receives DataPackets
        }
        catch(IOException e)
        {
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /*
    *	Creates the initial request DatagramPacket (read or write)
    */
    private synchronized DatagramPacket buildRequest(String mode, String filename, ActionType action)
    {
    	//Determines if request is a read, write or invalid
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
	
	//Adds the filename to the byte array
        if(filename.getBytes() != null) {	
            for(byte b: filename.getBytes()) {
                request[i++] = b;
            }
        }
        request[i++] = 0;
	
	//Adds the mode to the byte array
        if(mode.getBytes() != null) {		
            for(byte b: mode.getBytes()) {
                request[i++] = b;
            }
        }
        request[i++] = 0;
	
	//Creates the DatagramPacket from the byte array and sends it back
        try {					
        	return new DatagramPacket(request, request.length, InetAddress.getLocalHost(), PORT_NUMBER);
        } catch (UnknownHostException e) {
        	e.printStackTrace();
        	return null;
        }
        
    }
    
    /*
    *	Creates a DatagramPacket that contains the data requested
    */
    private synchronized DatagramPacket buildData(byte[] data, byte blockNumber, int portNumber)
    {
    	//Adds "3" for data packet format followed by block number
        byte[] msg = new byte[516];
        msg[1] = 3;				
        msg[3] = blockNumber;
	
	//Goes throught the data and adds it to the message
	for(int j = 0, k = 4; j < data.length && k < msg.length; j++, k++)	
	{
		msg[k] = data[j];
	}
	
	//Creates DatagramPacket containing the data and sends it back
	DatagramPacket send = null;
	try {
		send = new DatagramPacket(msg, msg.length, InetAddress.getLocalHost(), portNumber); 
		e.printStackTrace();
	}
	return send;
    }
    
    /*
    *	Runs the read request (ie. sends initial request then reads the data from the server)
    */
    private synchronized void sendReadReceive(String filename)
    {
    	//Creates read request DatagramPacket and sends it to the intermediate host
   	DatagramPacket message = buildRequest("ocTeT", filename, ActionType.READ);		
    	System.out.println("Sending request to Host: " + Converter.convertMessage(message.getData()));
    	try {
		socket.send(message);	
	} catch (IOException e) {
		e.printStackTrace();
	}
	
	//Creates a file where the data read from sever is stored
	int index = -1;
	byte[] receiveMsg;
	FileOutputStream fos=null;
	try {
		fos = new FileOutputStream(new File("c" + filename)); 
	} catch (FileNotFoundException e1) {
		e1.printStackTrace();
	}
	
	//Keeps reading data until server is done sending for the request
	while(index == -1) {	
		//Receives the DatagramPacket sent from server/intermediate host
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
		
		//Writes received message into the file
		if(index == -1){			
			try {
				fos.write(receiveMsg);
			} catch (IOException e) {
				e.printStackTrace();
			}

			//Creates and sends back an acknowledgment 
			byte[] b = {0, 4, 0, 0};	
			try {			
				DatagramPacket ack = new DatagramPacket(b, b.length, InetAddress.getLocalHost(), receivePacket.getPort());
				try {
					socket.send(ack);
				} catch (IOException IOE){
					IOE.printStackTrace();
					System.exit(1);
				}
			} catch (UnknownHostException e){
				e.printStackTrace();
				System.exit(1);
			}
		}
		//Writes last bit of the received data
		else{		
			try {
				fos.write(receiveMsg, 0, index);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
    }
    
    /*
    *	Runs the read request (ie. sends initial request then reads the data from the server)
    */
    private synchronized void sendWriteReceive(String filename)
    {
    	//Creates write request DatagramPacket and sends it
    	DatagramPacket message = buildRequest("ocTeT", filename, ActionType.WRITE);	
    	System.out.println("Sending request to Host: " + Converter.convertMessage(message.getData()));
    	try {
    		socket.send(message);	
    	} catch (IOException e) {
    		e.printStackTrace();
    	}
	
	//Creates fileInputStream with given filename that will be sent
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
		//Receives response from server
		receiveMsg = new byte[4];
	
    		DatagramPacket receivePacket = new DatagramPacket(receiveMsg, receiveMsg.length);	
    		try {
    			socket.receive(receivePacket);
    		} catch (IOException e) {
    			e.printStackTrace();
    		}

    		System.out.println("Response received from Host: " + Arrays.toString(receiveMsg) + "\n");
		
		//Writes data into the file
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
