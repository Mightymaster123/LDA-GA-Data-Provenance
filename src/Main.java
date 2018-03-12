
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StreamTokenizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

public class Main {

	static KeywordExtractor kwe;
	static HashMap<String, Long> javaKeys;

	// List of all documents
	static List<Document> documentList = new ArrayList<Document>();

	// Hashmap of of articles.
	// Key: file name
	// Value: useful words in article
	// All the files ending with $AAA$ are articles. For example: Wikipedia articles
	static Map<String, Article> articleMap = new HashMap<String, Article>();

	// Hashmap of code-source-files.
	// Key: file name
	// Value: useful words in code-source-file
	// For example: .java source file
	static Map<String, SourceFile> sourceFileMap = new HashMap<String, SourceFile>();

	public static void init(String s) {
		kwe = KeywordExtractor.getInstance();
		javaKeys = new HashMap<String, Long>();

		try {
			File f = new File(s);
			BufferedReader br = new BufferedReader(new FileReader(f));
			String key;
			while ((key = br.readLine()) != null) {
				javaKeys.put(key.trim(), new Long(0));
			}
			br.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// If the string s is useful, this function return true. If not, returns false.
	// This function returns false if the token is a Java keyword or stop-word, else
	// it returns true so that the token is retained
	static boolean categorize(String s) {
		// Split current token, if need be
		ArrayList<String> al = kwe.processCode(s);
		Iterator<String> it = al.iterator();
		// For each split part, check if it is a java keyword, etc.
		while (it.hasNext()) {
			String ss = (String) it.next();
			ss = ss.trim();
			if (ss != null && !javaKeys.containsKey(ss) && ss.indexOf('.') == -1) {
				if (!ss.matches("\\d*"))
					return true;
			}
		}
		return false;
	}

	// This function extract useful words from files in baseDir directory, then save
	// these useful words in mirrorDir directory.
	// This function recurses into the source directory containing .java source
	// files or Wikipedia articles,then
	// It tokenizes each .java file, removes comments, or removes unused words in
	// Wikipedia articles.
	public static void recurse(String baseDir, String mirrorDir) throws IOException, InterruptedException {
		// Initialize a stream tokenizers

		File dir = new File(baseDir);

		String[] files = dir.list();

		for (String file : files) {
			// If the file is a subdirectory, recurse
			if (new File(baseDir + "/" + file).isDirectory())
				recurse(baseDir + "/" + file, mirrorDir + "/" + file);
			else {

				// Initialize a stream tokenizer
				FileReader rd = new FileReader(baseDir + "/" + file);
				StreamTokenizer st = new StreamTokenizer(rd);

				// Prepare the tokenizer for Java-style tokenizing rules
				st.parseNumbers();
				st.wordChars('_', '_');
				// st.wordChars('.', '.');
				st.eolIsSignificant(true);

				// Parse file
				int token = st.nextToken();
				String content = "";// todo: use StringBuffer [liudong]
				String previous = "";
				while (token != StreamTokenizer.TT_EOF) {
					switch (token) {

					case StreamTokenizer.TT_WORD:
						// Check if it is a package name from package import statement
						if (previous.compareTo("package") == 0 || previous.compareTo("import") == 0) {
							String[] fields = st.sval.split("\\.");
							for (int i = 0; i < fields.length; i++) {
								previous = fields[i];
								if (categorize(fields[i]))
									content += fields[i] + " ";
							}
							break;
						}
						previous = st.sval;
						// Check if the word a stopword, etc.
						// If not, append it to the content to be written back
						if (categorize(st.sval))
							content += st.sval.toLowerCase() + " ";
						break;

					case StreamTokenizer.TT_NUMBER:
						// Check for numbers, decimal and hexadecimal
						if ((token = st.nextToken()) != StreamTokenizer.TT_EOF) {
							if (token == StreamTokenizer.TT_WORD && st.sval.startsWith("x"))
								;
							else
								st.pushBack();
						} else
							st.pushBack();
						break;

					default:
						// Ignore every other case
						break;
					}
					token = st.nextToken();
				}
				rd.close();

				// check if the file is of the type article
				// if the file is of the type article it has an _a in it
				if (file.contains("$AAA$")) {
					Article newArticle = new Article(file, content);
					documentList.add(newArticle);
					articleMap.put(file, newArticle);
				} else {
					SourceFile newSource = new SourceFile(file, content);
					documentList.add(newSource);
					sourceFileMap.put(file, newSource);
				}

				// System.out.println(content);

				// Write content to the file
				if (content.length() != 0) {
					File newDir = new File(mirrorDir);
					if (newDir.exists() == false)
						newDir.mkdirs();
					FileWriter wt = null;
					wt = new FileWriter(mirrorDir + "/" + file);

					wt.write(content);
					wt.close();
				}
			}
		}

		MalletInput.createMalletInput(documentList);

	}

	public static void printOutput(List<Cluster> clusters) {
		for (int i = 0; i < clusters.size(); i++) {
			System.out.print(clusters.get(i).clusterNo);
			System.out.print(clusters.get(i).articles + "        ");
			System.out.println(clusters.get(i).sourceFiles);
			System.out.println();
		}

	}

	public static void calculatePrecisionRecall(List<Cluster> clusters) throws FileNotFoundException {

		if(clusters==null || clusters.size()<=0)
		{
			System.out.println("PRECISION : " + 0);
			System.out.println("RECALL : " + 0);
			return;
		}
		// read the truth file
		Scanner truthFile = new Scanner(new File("truthfile.txt"));

		Hashtable<String, String> truthData = new Hashtable<String, String>();

		// get the data and construct a hash table with it
		while (truthFile.hasNextLine()) {
			String line = truthFile.nextLine();
			String[] split = line.split("# ");
			truthData.put(split[0], split[1]);
		}

		float[] precision = new float[clusters.size()];
		float[] recall = new float[clusters.size()];

		for (int i = 0; i < clusters.size(); i++) {
			Cluster cl = clusters.get(i);

			String name = cl.articles.get(0);
			List<String> sources = cl.sourceFiles;

			// retrieve the article from the truth file
			String trueSource = truthData.get(name);
			String[] trueSourceSplit = trueSource.split(" ");

			// calculating precision
			if (sources.size() == 0) {
				precision[i] = 0;
			} else {
				int precise_count = 0;
				for (int j = 0; j < sources.size(); j++) {
					if (trueSource.contains(sources.get(j))) {
						precise_count++;
					}
				}
				precision[i] = (float) precise_count / (float) sources.size();
			}

			// calculate recall
			recall[i] = 0;
			// convert the list of source files to a set
			Set<String> sourceSet = new HashSet<String>();
			for (String sourceName : sources) {
				sourceSet.add(sourceName);
			}
			if (trueSourceSplit == null || trueSourceSplit.length<=0) {
				if (sourceSet.contains(trueSource)) {
					recall[i] = 1;
				}
			} else {
				int recall_count = 0;
				for (int j = 0; j < trueSourceSplit.length; j++) {
					if (sourceSet.contains(trueSourceSplit[j])) {
						recall_count++;
					}
				}
				recall[i] = (float) recall_count / (float) trueSourceSplit.length;
			}
		}

		// calculating the average precision and recall
		float precision_total = 0;
		float recall_total = 0;

		for (int i = 0; i < precision.length; i++) {
			precision_total = precision_total + precision[i];
			recall_total = recall_total + recall[i];
		}

		float precision_percentage = (float) (precision_total / precision.length) * 100;
		float recall_percentage = (float) (recall_total / recall.length) * 100;

		System.out.println("PRECISION : " + precision_percentage);
		System.out.println("RECALL : " + recall_percentage);
		
		truthFile.close();
	}
	

	public static void main(String[] argv) throws IOException, InterruptedException, ClassNotFoundException {
		//Build connection between multiple machines: 1 master, multiple slaves
		MultiMachineSocket mms = new MultiMachineSocket();
		mms.config();

		long startTime = System.currentTimeMillis();

		// pass the stop words list as the parameter. Stop words are useless information. 
		init("stopwords.txt");

		String dataDir = "txtData"; // name of the directory that contains the original source data
		String mirrorDir = "processed-data"; // name of the directory where the useful data is stored

		// Mirror directory structure while retaining only tokenized source files (eg.
		// PDF files, CSV files, etc. from handlers in pkg1)
		recurse(dataDir, mirrorDir);

		// Print out each article's title along with it's keywords
		System.out.println("The number of articles is " + articleMap.size());
		int article_index = 0;
		for (String article : articleMap.keySet()) {
			Article ar = articleMap.get(article);
			System.out.println((article_index+1)+".\t"+ar.name + "    " + ar.getKeyWords());
			++article_index;
		}

		// Output the time it took to find all article's titles and keywords
		long preprocessEndTime = System.currentTimeMillis();
		System.out.println("Preprocessing takes " + (preprocessEndTime - startTime) + "ms");

		// call the genetic logic function that calls the topic modeling
		// this completes all LDA function
		// the distribution is found in distribution.txt
		// the code to write the topics to a file is still to be written.

		// This call handles threading among the multiple machine sockets. It also uses
		// threads and the number of cores
		// on the master and worker machine to find the best iteration and fitness from
		// the set of documents
		geneticLogic.geneticLogic(mms);

		// Outputs the time it took to finish the genetic algorithm
		long geneticEndTime = System.currentTimeMillis();
		System.out.println("Genetic algorithm takes " + (geneticEndTime - preprocessEndTime) + "ms");

		// create clusters based on the distribution.txt
		List<Cluster> clusters = Cluster.createClusters();

		// by cleaning the clusters
		// we got through the obtained list of clusters
		// check for conditions where there are more than 2 articles in the same cluster
		// perform the job of splitting the cluster into 2
		Cluster.cleanCluster(clusters, articleMap, sourceFileMap);

		System.out.println("clusters before cleaning source file \n \n ");
		printOutput(clusters);

		// there might be some clusters with no article in them but all source files
		// to handle that we use the following technique/function
		Cluster.cleanSourceFileCluster(clusters, sourceFileMap);
		System.out.println("Clusters after cleaning the source file");
		printOutput(clusters);

		long clusteringEndTime = System.currentTimeMillis();
		System.out.println("Clustering takes " + (clusteringEndTime - geneticEndTime) + "ms");

		calculatePrecisionRecall(clusters);

		long endTime = System.currentTimeMillis();

		long totalTime = endTime - preprocessEndTime;
		System.out.println("execution time : " + totalTime + "ms");
	}
}
