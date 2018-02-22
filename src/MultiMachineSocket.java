import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

public class MultiMachineSocket {
	private InetAddress masterAddr = null;
	private int numSlaves = 0;
	
	// id = -1 is master, id >= 0 are slaves 
	private int id;
	public int getId(){
		return id;
	}
	private InetAddress slaveAddr[];
	
	private int port = 0;
	
	private ServerSocket masterSockets[] = null;
	
	private void setMasterAddr(String line) throws UnknownHostException{
		masterAddr = InetAddress.getByName(line);
	}
	
	private void setNumSlaves(String line){
		numSlaves = Integer.parseInt(line);
	}
	public int getNumSlaves(){
		return numSlaves;
	}
	
	private void setId(int i){
		id = i;
	}
	
	private void setOneSlave(int i, String line) throws UnknownHostException{
		slaveAddr[i] = InetAddress.getByName(line);
	}
	
	private void setPort(int i){
		port = i;
	}
	
	
	public void config() throws IOException{
		BufferedReader b=new BufferedReader(new FileReader("config.txt"));
		String line = "";
		
		//	1)read master address
		line = b.readLine();
		setMasterAddr(line);
		System.out.println("master address: " + line);
		
		
		// 2)read number of slaves
		line = b.readLine();
		setNumSlaves(line);
		System.out.println("number of slaves: " + line);
		
		
		
		// 3)read slave addresses
		slaveAddr = new InetAddress[numSlaves];
		for(int i = 0; i < numSlaves; i++){
			line = b.readLine();
			setOneSlave(i, line);
			System.out.println("slave " + i + " address: " + line);
		}
		
		// 4) set port(port for connection with first slave)
		line = b.readLine();
		setPort( Integer.parseInt(line));
		System.out.println(" first port: " + port);
	}
	
	public Socket[] connect() throws IOException{
		System.out.println("My IP is: " + InetAddress.getLocalHost().toString());
		Socket sockets[] = null;
		// if this machine is master
    	if(masterAddr.equals(InetAddress.getByName("localhost")) || masterAddr.equals(InetAddress.getLocalHost())){
    		setId(-1);
    		System.out.println("I am master");
    		// create serverSocket for each slave, and then listen to request from each slave
    		masterSockets = new ServerSocket[numSlaves];
    		for(int i = 0; i < numSlaves; i++){
    			masterSockets[i] = new ServerSocket(port + i);
    			sockets = new Socket[numSlaves];
    			
    			while(true){
    				sockets[i] = masterSockets[i].accept();
    				//System.out.println("*****tag****");
    				if(sockets[i] != null)
    					break;	
    			}
    			System.out.println("connection estblished " + i);
    		}
    		return sockets;
    	}
    	//else(is slave)
    	else{
    		sockets = new Socket[1];
    		// find out which slave it is
    		for(int i = 0; i < numSlaves; i++){
    			if(slaveAddr[i].equals(InetAddress.getByName("localhost")) || slaveAddr[i].equals(InetAddress.getLocalHost())){
    				setId(i);
    			}
    		}
    		System.out.println("I am slave " + id);
    		System.out.println("slave " + id + "will try to connect with master");
    		//System.out.println(InetAddress.getByName("localhost").toString() + "    " + (port + id));
    		Socket socket = new Socket(masterAddr, port + id);
    		sockets[0] = socket;
    		return sockets;
    	}
	}
//	
//	// tell master one slave have finished the job
//	public void finishSignal(){
//		
//	}
//	
//	// send result back to master
//	public void sendResult(){
//		
//	}
//	
}
