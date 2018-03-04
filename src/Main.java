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

    //make a list of documents;
    static List<Document> documentList = new ArrayList<Document>();

    //make a list/hashmap of of articles
    //add all the files ending with _a to this Map indicating that they are articles
    static Map<String, Article> articleMap = new HashMap<String, Article>();

    //make a list of source files
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

    // This function returns false if the token is a Java keyword or stopword
    // Else it returns true so that the token is retained
    static boolean categorize(String s) {
        // Split current token, if need be
        ArrayList al = kwe.processCode(s);
        Iterator it = al.iterator();
        // For each split part, check if it is a java keyword, etc.
        while (it.hasNext()) {
            String ss = (String) it.next();
            ss = ss.trim();
            if (s != null && !javaKeys.containsKey(ss) && ss.indexOf('.') == -1) {
                if (!ss.matches("\\d*"))
                    return true;
            }
        }
        return false;
    }

    // This function recurses into the source directory containing .java source files
    // It tokenizes each .java file, removes comments,
    public static void recurse(String baseDir, String mirrorDir) throws IOException, InterruptedException {
        // Initialize a stream tokenizers
        File dir = new File(baseDir);
        String[] files = dir.list();

        // Make sure that the current directory has files
        assert files != null : "There is nothing to process in the current directory";
        for (String file : files) {
            // If the file is a subdirectory, recurse
            if (new File(baseDir + "/" + file).isDirectory()) {
                recurse(baseDir + "/" + file, mirrorDir + "/" + file);
            }
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
                StringBuilder content = new StringBuilder();
                String previous = "";
                while (token != StreamTokenizer.TT_EOF) {
                    switch (token) {
                        case StreamTokenizer.TT_WORD:
                            // Check if it is a package name from package import statement
                            if (previous.compareTo("package") == 0 || previous.compareTo("import") == 0) {
                                String[] fields = st.sval.split("\\.");
                                for (String field : fields) {
                                    previous = field;
                                    if (categorize(field))
                                        content.append(field).append(" ");
                                }
                                break;
                            }
                            previous = st.sval;
                            // Check if the word a stopword,  etc.
                            // If not, append it to the content to be written back
                            if (categorize(st.sval))
                                content.append(st.sval.toLowerCase()).append(" ");
                            break;
                        case StreamTokenizer.TT_NUMBER:
                            // Check for numbers, decimal and hexadecimal
                            if ((token = st.nextToken()) != StreamTokenizer.TT_EOF) {
                                if (token == StreamTokenizer.TT_WORD && st.sval.startsWith("x")) ;
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

                // Check if the file is of type article
                // An _a denotes an article
                if (file.contains("$AAA$")) {
                    System.out.println("File has $AAA$, Article found...");
                    Article newArticle = new Article(file, content.toString());
                    documentList.add(newArticle);
                    articleMap.put(file, newArticle);
                } else {
                    System.out.println("File is not an article...");
                    SourceFile newSource = new SourceFile(file, content.toString());
                    documentList.add(newSource);
                    sourceFileMap.put(file, newSource);
                }

                // Write content to the file
                if (content.length() != 0) {
                    File newDir = new File(mirrorDir);
                    if (!newDir.exists()) {
                        newDir.mkdirs();
                    }
                    FileWriter wt = null;
                    wt = new FileWriter(mirrorDir + "/" + file);
                    wt.write(content.toString());
                    wt.close();
                }
            }
        }
        MalletInput.createMalletInput(documentList);
    }

    public static void printOutput(List<Cluster> clusters) {
        for (Cluster cluster : clusters) {
            System.out.print(cluster.clusterNo);
            System.out.print(cluster.articles + "        ");
            System.out.println(cluster.sourceFiles);
            System.out.println();
        }

    }

    public static void calculatePrecisionRecall(List<Cluster> clusters) throws FileNotFoundException {
        // Read the truth file
        Scanner truthFile = new Scanner(new File("truthfile.txt"));

        Hashtable<String, String> truthData = new Hashtable<String, String>();

        // Get the data and construct a hashtable with it
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

            // Retrieve the article from the truth file
            String sourceFiles = truthData.get(name);
            String[] sourceSplit = sourceFiles.split(" ");

            // Calculate the precision
            int deno = sources.size();
            int numo = 0;
            for (String source : sources) {
                if (sourceFiles.contains(source)) {
                    numo++;
                }
            }
            if (deno == 0) {
                precision[i] = 0;
            } else {
                precision[i] = (float) numo / deno;
            }

            // Calculate the recall
            numo = 0;
            if (sourceSplit == null) {
                deno = 1;
            } else {
                deno = sourceSplit.length;
            }

            // Convert the list of source files to a set
            Set<String> sourceSet = new HashSet<String>(sources);
            if (deno == 1) {
                if (sourceSet.contains(sourceFiles)) {
                    numo++;
                }
            } else {
                for (String aSourceSplit : sourceSplit) {
                    if (sourceSet.contains(aSourceSplit)) {
                        numo++;
                    }
                }
            }
            recall[i] = (float) numo / deno;
        }

        //calculating the average precision and recall
        float precitotal = 0;
        float recatotal = 0;

        for (int i = 0; i < precision.length; i++) {
            precitotal = precitotal + precision[i];
            recatotal = recatotal + recall[i];
        }
        float precisionPercentage = precitotal / precision.length * 100;
        float recallPercentage = recatotal / recall.length * 100;

        System.out.println("PRECISION : " + precisionPercentage);
        System.out.println("RECALL : " + recallPercentage);
    }


    public static void main(String[] argv) throws IOException, InterruptedException, ClassNotFoundException {
        // Build connection between multiple machines
        MultiMachineSocket mms = new MultiMachineSocket();
        mms.config();

        long startTime = System.currentTimeMillis();

        // Pass the stopwords list as the parameter
        init("stopwords.txt");

        String dataDir = "rawData";                 // name of the directory that contains the original source data
        String mirrorDir = "processed-data";        //name of the directory where the modified data is to be stored

        // Mirror directory structure while retaining only tokenized source files (eg. PDF files, CSV files, etc. from handlers in pkg1)
        // I feel like this recurse function processes article titles and keywords into a txt file...
        recurse(dataDir, mirrorDir);

        // Output the time it took to find all article's titles and keywords
        long preprocessEndTime = System.currentTimeMillis();
        System.out.println("Preprocessing takes " + (preprocessEndTime - startTime) + "ms");

        //call the genetic logic function that calls the topic modelling
        //this completes all LDA function
        //the distribution is found in distribution.txt
        //the code to write the topics to a file is still to be written.

        // This call handles threading among the multiple machine sockets. It also uses threads and the number of cores
        // on the master and worker machine to find the best iteration and fitness from the set of documents
        geneticLogic.geneticLogic(mms);

        // Outputs the time it took to finish the genetic algorithm
        long geneticEndTime = System.currentTimeMillis();
        System.out.println("Genetic algorithm takes " + (geneticEndTime - preprocessEndTime) + "ms");

        //create clusters based on the distribution.txt
        List<Cluster> clusters = Cluster.createClusters();

        //by cleaning the clusters
        //we got through the obtained list of clusters
        //check for conditions where there are more than 2 articles in the same cluster
        //perform the job of splitting the cluster into 2
        Cluster.cleanCluster(clusters, articleMap, sourceFileMap);

        System.out.println("clusters before cleaning source file \n \n ");
        printOutput(clusters);

        //there might be some clusters with no article in them but all source files
        //to handle that we use the following technique/function
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