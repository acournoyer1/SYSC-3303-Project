import java.io.UnsupportedEncodingException;
import java.util.Arrays;

/**
 * Creates a converter that turn a byte array into a string which it returns
 * 
 * Team 11  
 * V1.16
 */
public class Converter {

	/*
	*   Converts byte array into a string
	*/
	public static String convertMessage(byte[] message)
	{
		//Initiates string with type of request
		String byteString = "[" + message[0] + ", " + message[1];
		String s = "[" + message[0] + ", " + message[1];
		
		int startIndex = 2;
		int endIndex = 0;
		int zeroCount = 0;
		//Keeps running until it gets two zero's in a row
		for(int i = 2; zeroCount < 2 && i<516; i++)
		{
			byteString += ", " + message[i];
			if(message[i] == 0)
			{
				zeroCount++;
				endIndex = i;
				//Creates byte array containing bytes for filename or mode
				byte[] str = new byte[endIndex - startIndex];
				int k = 0;
				for(int j = startIndex; j < endIndex && k < str.length; j++, k++)
				{
					str[k] = message[j];
				}
				//Turns byte array into a string
				s += ", " + new String(str) + ", 0";
				startIndex = i+1;
			}
		}
		//Ends string and returns it
		byteString += "]";
		s += "] " + byteString; 
		
		return s;
	}
	
	/*
	*   Converts byte array into a string for the error message
	*/
	public static String convertErrorMessage(byte[] message)
	{
		String decodedDataUsingUTF8 = null;
		try {
			byte[] c = Arrays.copyOfRange(message, 4, message.length);
			decodedDataUsingUTF8 = new String(c, "UTF-8");
		    System.out.println(decodedDataUsingUTF8);
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
		}
		return decodedDataUsingUTF8;
	}
}
