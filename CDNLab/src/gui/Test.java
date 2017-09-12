package gui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;

public class Test {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		File src=new File(System.getProperty("user.dir") + "\\cdnlab_files\\cdnlab");
		File target=new File("C:\\Users\\Jalal\\Desktop\\CDN\\"+"\\cdnlab");
		try {
			FileUtils.copyDirectory(src, target);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
