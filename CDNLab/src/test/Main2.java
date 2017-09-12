package test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class Main2 {
	public static void main(String[] args) throws IOException {
		File f = new File("files/routers.txt");
		FileReader fileReader = new FileReader(f);

		// Always wrap FileReader in BufferedReader.
		BufferedReader bufferedReader = new BufferedReader(fileReader);
		String line;
		while ((line = bufferedReader.readLine()) != null) {
			System.out.println(line);
		}

		// Always close files.
		bufferedReader.close();
	}
}
