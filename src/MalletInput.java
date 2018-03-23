import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.List;

public class MalletInput {
	public static final String INPUT_FILE_NAME = "input1.txt";
	public static final String INPUT_FILE_CHARSET = "UTF-8";

	// the main function reads the input from the written preprocessed files
	public static void main(String[] args) {
		// TODO Auto-generated method stub

		// pass the location and name of the processed data dir
		File dir = new File(DataProvenance.PROCESSED_DATA_DIRECTORY);

		// the output is written to the file input.txt
		PrintWriter writer = null;
		try {
			writer = new PrintWriter("input.txt", "UTF-8");

			String[] files = dir.list();

			for (String file : files) {
				File rd = new File(DataProvenance.PROCESSED_DATA_DIRECTORY + "/" + file);
				System.out.println(rd.getName());
				FileReader fr = new FileReader(rd);
				BufferedReader bufferedReader = null;
				try {
					bufferedReader = new BufferedReader(fr);
					writer.print(rd.getName() + "\tX\t");
					String line;
					while ((line = bufferedReader.readLine()) != null) {
						writer.print(line);
					}
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					if (bufferedReader != null) {
						bufferedReader.close();
						bufferedReader = null;
					}
				}
				writer.println();

			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (writer != null) {
				writer.close();
				writer = null;
			}
		}
	}

	// this function is written in an effort to avoid writing to the preprocessed
	// folder and reading it back again
	public static void createMalletInput(List<Document> documentList) throws FileNotFoundException, UnsupportedEncodingException {

		System.out.println("The number of documents is " + documentList.size());
		PrintWriter writer = new PrintWriter(INPUT_FILE_NAME, INPUT_FILE_CHARSET);
		for (Document d : documentList) {
			writer.println(d.name + "	X	" + d.getKeyWords());
		}
		writer.close();
	}

}
