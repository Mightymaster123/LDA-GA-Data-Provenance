/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**  
 * Following piece of code has been written by Erik Linstead
 * when he was a PhD candidate at UC Irvine.
 * This code implements functionality to test if each Java token 
 * presented to it is one of the Java keywords or 
 * stopwords to see if needs to be excluded.
 */

/**  
 * This class is used to extract keywords from source code
 * comments or method/class/variable names.
 *
 *  @author Erik Linstead (elinstea@ics.uci.edu)
 */
//package edu.uci.ics.mondego.codeindexer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;

//import edu.uci.ics.mondego.common.gof.Singleton;
//import edu.uci.ics.mondego.common.gof.SingletonManager;

public class KeywordExtractor { //implements Singleton {
    
	//=================================================================================
	private static KeywordExtractor instance = null;

	public static KeywordExtractor getInstance() {
		if (instance == null) {
			instance = new KeywordExtractor();
			
			//SingletonManager.getInstance().register(instance);
		}
		return instance;
	}

	/**
	 * this method is for testing
	 */
/*	public void destroyInstance() {
		SingletonManager.getInstance().unregister(instance);
		instance = null;
	}*/
	
	//=================================================================================	
	
    private KeywordExtractor(){
        super();
    }
    

    
    public ArrayList<String> processComment(String comment){
        ArrayList<String> result = new ArrayList<String>();
        String[] tokens = comment.split("[\\s]+");
        for(int i = 0; i< tokens.length;++i){
            result.add(tokens[i]);
        }
        return result;
    }
    
    public ArrayList<String> processCode(String codeFragment){
        ArrayList<String> result = new ArrayList<String>();
        
        if (codeFragment == null || codeFragment.equals("")) {
        	return result;
        }
        ArrayList<String> unprocessed = new ArrayList<String>();
        //result.add(codeFragment);
        //unprocessed.add(codeFragment);
        String prototype = stripMethodSyntax(codeFragment);
        String[] components = prototype.split("[\\s]+");
        for(int i =0; i<components.length;++i){
            result.add(components[i]);
            unprocessed.add(components[i]);
        }
        ArrayList<String> firstCut = removeUnderScore(result);
        ArrayList<String> secondCut = removeHiphen(firstCut);
        ArrayList<String> caseCut = parseCase(secondCut);
       //replace this with better rules later
      /*  if(caseCut.size()==1 && 
        		!(codeFragment.
                compareToIgnoreCase((String)caseCut.get(0))==0)) {
            caseCut.add(codeFragment);
            
        } else if (caseCut.size()>1){
            caseCut.add(codeFragment);
            
        } */
        //caseCut.addAll(unprocessed);
        //caseCut.addAll(firstCut);
        return caseCut;
    }
    
    
    public ArrayList<String> processJavaClass(Class c) {
    	ArrayList<String> result = new ArrayList<String>();
    	
        Method[] m = c.getMethods();
        for(int j = 0; j<m.length;++j){
            ArrayList<String> a = processCode(m[j].getName());
            result.addAll(a);
        }
        
        return result;
    }
    
    
    private ArrayList<String> removeUnderScore(ArrayList<String> tokens){
        ArrayList<String> result = new ArrayList<String>();
        Iterator<String> it = tokens.iterator();
        while(it.hasNext()){
            String s = (String)it.next();
            String[] words = s.split("[_$.]");
            for(int i = 0; i< words.length;++i){
                result.add(words[i].trim());
            }
        }
        return result;
    }
    
    private ArrayList<String> removeHiphen(ArrayList<String> tokens){
        ArrayList<String> result = new ArrayList<String>();
        Iterator<String> it = tokens.iterator();
        while(it.hasNext()){
            String s = (String)it.next();
            String[] words = s.split("[—$.]");
            for(int i = 0; i< words.length;++i){
                result.add(words[i].trim());
            }
        }
        return result;
    }
    
    private String stripMethodSyntax(String s){
        String result = s.replaceAll("[\\s]+","");
        result = result.replace('(',(char)32);
        result = result.replace(')',(char)32);
        result = result.replace(',',(char)32);
        return result;
    }
    
    private ArrayList<String> parseCase(ArrayList<String> tokens){
        ArrayList<String> result = new ArrayList<String>();
        Iterator<String> it = tokens.iterator();
        while(it.hasNext()){
            String s = (String) it.next();
            int end = s.length();
            ArrayList<Integer> index = new ArrayList<Integer>();
            index.add(new Integer(end));
            for(int i = end-1; i > 0; --i){
                char e = s.charAt(i);
                char b = s.charAt(i-1);
                boolean endLetter = Character.isLetter(e);
                boolean beginLetter = Character.isLetter(b);
                if(endLetter && beginLetter && Character.isUpperCase(e) &&
                        Character.isLowerCase(b)){
                    index.add(new Integer(i));
                } else if(endLetter && !beginLetter){
                    index.add(new Integer(i));
                } 
            }
            int beginIndex = 0;
            for(int j = index.size()-1;j >= 0;--j){                
                int endIndex = ((Integer)index.get(j)).intValue();
                result.add(s.substring(beginIndex,endIndex));
                
                beginIndex = endIndex;
            }
        }
        return result;
    }
}