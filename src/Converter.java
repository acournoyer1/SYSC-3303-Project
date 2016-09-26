
public class Converter {

	public static String convertMessage(byte[] message)
	{
		String byteString = "[" + message[0] + ", " + message[1];
		String s = "[" + message[0] + ", " + message[1];
		
		int startIndex = 2;
		int endIndex = 0;
		int zeroCount = 0;
		for(int i = 2; zeroCount < 2; i++)
		{
			byteString += ", " + message[i];
			if(message[i] == 0)
			{
				zeroCount++;
				endIndex = i;
				byte[] str = new byte[endIndex - startIndex];
				int k = 0;
				for(int j = startIndex; j < endIndex && k < str.length; j++, k++)
				{
					str[k] = message[j];
				}
				s += ", " + new String(str) + ", 0";
				startIndex = i+1;
			}
		}
		byteString += "]";
		s += "] " + byteString; 
		
		return s;
	}
}
