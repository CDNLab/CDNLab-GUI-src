package util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ConverterUtil {
	public static void main(String[] args) {
		System.out.println(ipToLong("212.78.238.39"));
	}

	public static String toHex(String ipAddress) {
		return Long.toHexString(ConverterUtil.ipToLong(ipAddress));
	}

	public static long ipToLong(String ipAddress) {
		long result = 0;
		String[] atoms = ipAddress.split("\\.");

		for (int i = 3; i >= 0; i--) {
			result |= (Long.parseLong(atoms[3 - i]) << (i * 8));
		}

		return result & 0xFFFFFFFF;
	}

	public static String longToIp(long ip) {
		StringBuilder sb = new StringBuilder(15);

		for (int i = 0; i < 4; i++) {
			sb.insert(0, Long.toString(ip & 0xff));

			if (i < 3) {
				sb.insert(0, '.');
			}

			ip >>= 8;
		}

		return sb.toString();
	}
	
	public static String dateToEpochTime(String strDate) throws ParseException{
		//Example "05/Dec/2010:22:13:47";
		SimpleDateFormat df = new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss");
	    Date date = df.parse(strDate);
	    long epoch = date.getTime();
	    return String.valueOf(epoch);
	}
}