
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.StreamTokenizer;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

public class DataProvenance {
	public static final String ORIGINAL_DATA_DIRECTORY = "txtData"; // name of the directory that contains the original
																	// source data
	public static final String PROCESSED_DATA_DIRECTORY = "processed-data"; // name of the directory where the useful
																			// data is stored

	private KeywordExtractor kwe;
	private HashMap<String, Long> javaKeys;

	// List of all documents
	private List<Document> documentList = new ArrayList<Document>();

	// Hash map of of articles.
	// Key: file name
	// Value: useful words in article
	// All the files ending with $AAA$ are articles. For example: Wikipedia articles
	private Map<String, Article> articleMap = new HashMap<String, Article>();

	// Hash map of code-source-files.
	// Key: file name
	// Value: useful words in code-source-file
	// For example: .java source file
	private Map<String, SourceFile> sourceFileMap = new HashMap<String, SourceFile>();

	private geneticLogicOriginal mGeneticLogicOriginal = null;
	private geneticLogic mGeneticLogic = null;
	private boolean mIsRunning = true;

	public boolean isRunning() {
		return mIsRunning;
	}

	private void init_stop_words(String s) {
		kwe = KeywordExtractor.getInstance();
		javaKeys = new HashMap<String, Long>();

		FileReader fr = null;
		BufferedReader br = null;
		try {
			File f = new File(s);
			fr = new FileReader(f);
			br = new BufferedReader(fr);
			String key;
			while ((key = br.readLine()) != null) {
				javaKeys.put(key.trim(), new Long(0));
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				br = null;
			}
			if(fr!=null)
			{
				try {
					fr.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				fr = null;
			}
		}
	}

	// If the string s is useful, this function return true. If not, returns false.
	// This function returns false if the token is a Java keyword or stop-word, else
	// it returns true so that the token is retained
	boolean categorize(String s) {
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

	// This function extracts useful words from files in original_directory, then
	// saves these useful words in processed_directory. This function recurses into
	// the original_directory containing .java source files or Wikipedia
	// articles,then it tokenizes each .java file, removes comments, or removes
	// unused words in Wikipedia articles.
	public void extract_useful_words(String original_directory, String processed_directory) {
		// Initialize a stream tokenizers

		File dir = new File(original_directory);

		String[] files = dir.list();
		try {
			for (String file : files) {
				// If the file is a subdirectory, recurse
				if (new File(original_directory + "/" + file).isDirectory())
					extract_useful_words(original_directory + "/" + file, processed_directory + "/" + file);
				else {
					String content = "";// todo: use StringBuffer [liudong]

					// Initialize a stream tokenizer
					FileReader rd = null;
					try {
						rd = new FileReader(original_directory + "/" + file);
						StreamTokenizer st = new StreamTokenizer(rd);

						// Prepare the tokenizer for Java-style tokenizing rules
						st.parseNumbers();
						st.wordChars('_', '_');
						// st.wordChars('.', '.');
						st.eolIsSignificant(true);

						// Parse file
						int token = st.nextToken();
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
					} catch (Exception e) {
						e.printStackTrace();
					} finally {
						if (rd != null) {
							rd.close();
							rd = null;
						}
					}

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
						File newDir = new File(processed_directory);
						if (newDir.exists() == false)
							newDir.mkdirs();
						FileWriter wt = null;
						try {
							wt = new FileWriter(processed_directory + "/" + file);
							wt.write(content);
						} catch (Exception e) {
							e.printStackTrace();
						} finally {
							if (wt != null) {
								wt.close();
							}
						}
					}
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		try {
			MalletInput.createMalletInput(documentList);
		} catch (FileNotFoundException | UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public void printOutput(List<Cluster> clusters) {
		for (int i = 0; i < clusters.size(); i++) {
			System.out.print(clusters.get(i).clusterNo);
			System.out.print(clusters.get(i).articles + "        ");
			System.out.println(clusters.get(i).sourceFiles);
			System.out.println();
		}

	}

	public void calculatePrecisionRecall(ResultStatistics result, List<Cluster> clusters) {

		if (clusters == null || clusters.size() <= 0) {
			System.out.println("Precision : " + 0);
			System.out.println("Recall : " + 0);
			return;
		}
		Hashtable<String, String> truthData = new Hashtable<String, String>();
		// read the truth file
		Scanner truthFile = null;
		try {
			truthFile = new Scanner(new File("truthfile.txt"));
			// get the data and construct a hash table with it
			while (truthFile.hasNextLine()) {
				String line = truthFile.nextLine();
				String[] split = line.split("# ");
				truthData.put(split[0], split[1]);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (truthFile != null) {
				truthFile.close();
				truthFile = null;
			}
		}

		float[] precision = new float[clusters.size()];
		float[] recall = new float[clusters.size()];

		for (int i = 0; i < clusters.size(); i++) {
			precision[i] = 0;
			recall[i] = 0;

			Cluster cl = clusters.get(i);

			String name = cl.articles.get(0);
			List<String> sources = cl.sourceFiles;

			// retrieve the article from the truth file
			String trueSource = truthData.get(name);
			if (trueSource == null || trueSource == "") {
				System.out.println("Failed to find truth data: " + name);
				continue;
			}
			String[] trueSourceSplit = trueSource.split(" ");

			// calculating precision
			if (sources.size() != 0) {
				int precise_count = 0;
				for (int j = 0; j < sources.size(); j++) {
					if (trueSource.contains(sources.get(j))) {
						precise_count++;
					}
				}
				precision[i] = (float) precise_count / (float) sources.size();
			}

			// calculate recall
			// convert the list of source files to a set
			Set<String> sourceSet = new HashSet<String>();
			for (String sourceName : sources) {
				sourceSet.add(sourceName);
			}
			if (trueSourceSplit == null || trueSourceSplit.length <= 0) {
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

		result.precision_percentage = (float) (precision_total / precision.length) * 100;
		result.recall_percentage = (float) (recall_total / recall.length) * 100;

		System.out.println("Precision: " + result.precision_percentage + "%");
		System.out.println("Recall:    " + result.recall_percentage + "%");

	}

	public void delete_directory(String directory_name) {
		File dir = new File(directory_name);
		if (dir.exists()) {
			String[] entries = dir.list();
			if (entries != null) {
				for (String s : entries) {
					File currentFile = new File(dir.getPath(), s);
					if (currentFile.isDirectory()) {
						delete_directory(currentFile.getPath());
					} else {
						currentFile.delete();
					}
				}
			}

			dir.delete();
		}
	}

	public ResultStatistics run(boolean original_version) {
		ResultStatistics result = null;

		long startTime = System.currentTimeMillis();

		// pass the stop words list as the parameter. Stop words are useless
		// information.
		init_stop_words("stopwords.txt");
		if (!mIsRunning) {
			return result;
		}

		// Mirror directory structure while retaining only tokenized source files (eg.
		// PDF files, CSV files, etc. from handlers in pkg1)
		delete_directory(PROCESSED_DATA_DIRECTORY);
		extract_useful_words(ORIGINAL_DATA_DIRECTORY, PROCESSED_DATA_DIRECTORY);
		if (!mIsRunning) {
			return result;
		}

		// Print out each article's title along with it's keywords
		System.out.println("The number of articles is " + articleMap.size());
		int article_index = 0;
		for (String article : articleMap.keySet()) {
			Article ar = articleMap.get(article);
			System.out.println((article_index + 1) + ".\t" + ar.name + "    " + ar.getKeyWords());
			++article_index;
		}
		if (!mIsRunning) {
			return result;
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
		try {
			if (mGeneticLogicOriginal != null) {
				System.out.println("Error: An old GeneticLogicOriginal is running.");
				mGeneticLogicOriginal.isRunning = false;
			}
			if (mGeneticLogic != null) {
				System.out.println("Error: An old GeneticLogic is running.");
				mGeneticLogic.isRunning = false;
			}
			if (original_version) {
				mGeneticLogicOriginal = new geneticLogicOriginal();
				result = mGeneticLogicOriginal.run();
			} else {
				mGeneticLogic = new geneticLogic();
				result = mGeneticLogic.run();
			}
		} catch (Exception e) {
			e.printStackTrace(System.err);
			return result;
		}
		if (!mIsRunning) {
			return result;
		}
		if (result == null) {
			result = new ResultStatistics();
		}

		// Outputs the time it took to finish the genetic algorithm
		long geneticEndTime = System.currentTimeMillis();
		System.out.println("Genetic algorithm takes " + (geneticEndTime - preprocessEndTime) + "ms");

		if (NetworkManager.getInstance().isMaster()) {
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

			calculatePrecisionRecall(result, clusters);
		}

		long endTime = System.currentTimeMillis();

		result.execution_milliseconds = endTime - preprocessEndTime;
		System.out.println("Execution time : " + result.execution_milliseconds + "ms");

		return result;
	}

	public void stop() {
		mIsRunning = false;
		if (mGeneticLogicOriginal != null) {
			mGeneticLogicOriginal.isRunning = false;
		}
		if (mGeneticLogic != null) {
			mGeneticLogic.isRunning = false;
		}
	}

	public void SlaveFinish(NetworkManager.ReceivedProtocol protocol) {
		if (mGeneticLogicOriginal != null && protocol != null && protocol.protocol == NetworkManager.PROTOCOL_FINISH_ORIGINAL) {
			mGeneticLogicOriginal.SlaveFinish(protocol);
		}
		if (mGeneticLogic != null && protocol != null && protocol.protocol == NetworkManager.PROTOCOL_FINISH_NEW) {
			mGeneticLogic.SlaveFinish(protocol);
		}
	}

	public void StartSubPopulation(NetworkManager.ReceivedProtocol protocol) {
		if (mGeneticLogic != null) {
			mGeneticLogic.StartSubPopulation(protocol);
		}
	}
}
