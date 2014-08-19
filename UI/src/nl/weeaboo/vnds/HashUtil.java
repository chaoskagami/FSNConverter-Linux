package nl.weeaboo.vnds;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtil {
	
	public static String generateHash(String string) throws NoSuchAlgorithmException, IOException {
		return hashToString(generateHash(string.getBytes("UTF-8")));
	}
	public static byte[] generateHash(File file) throws IOException, NoSuchAlgorithmException {
		InputStream in = new BufferedInputStream(new FileInputStream(file));
		byte hash[] = null;
		
		try {
			hash = generateHash(in);
		} finally {
			in.close();
		}
		
		return hash;
	}
	public static byte[] generateHash(InputStream in) throws IOException, NoSuchAlgorithmException {
		MessageDigest md = MessageDigest.getInstance("MD5");
		
		byte buffer[] = new byte[16 * 1024];
		while (true) {
			int read = in.read(buffer, 0, buffer.length);
			if (read == -1) {
				break;
			}
			md.update(buffer, 0, read);
		}
		
		return md.digest();
	}
	
	public static byte[] generateHash(byte data[]) throws NoSuchAlgorithmException {
		return generateHash(data, 0, data.length);
	}
	public static byte[] generateHash(byte data[], int offset, int length) throws NoSuchAlgorithmException {
		MessageDigest md = MessageDigest.getInstance("MD5");
		md.update(data, offset, length);
		return md.digest();
	}
	
	public static String hashToString(byte hash[]) {
		StringBuilder sb = new StringBuilder();
		for (byte b : hash) {
			int nibble1 = (b & 0xF0) >> 4;
			int nibble2 = b & 0x0F;
			sb.append(Integer.toHexString(nibble1));
			sb.append(Integer.toHexString(nibble2));
		}
		return sb.toString();
	}
	
}