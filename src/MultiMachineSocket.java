import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

public class MultiMachineSocket {
	private InetAddress masterAddr = null;
	private int numSlaves = 0;

	// id = -1 is master, id >= 0 are slaves
	private int id;

	public int getId() {
		return id;
	}

	private InetAddress slaveAddr[];

	private int port = 0;

	private ServerSocket masterSockets[] = null;

	private void setMasterAddr(String line) throws UnknownHostException {
		masterAddr = InetAddress.getByName(line);
	}

	private void setNumSlaves(String line) {
		numSlaves = Integer.parseInt(line);
	}

	public int getNumSlaves() {
		return numSlaves;
	}

	private void setId(int i) {
		id = i;
	}

	private void setOneSlave(int i, String line) throws UnknownHostException {
		slaveAddr[i] = InetAddress.getByName(line);
	}

	private void setPort(int i) {
		port = i;
	}

	public void config() throws IOException {
		BufferedReader b = new BufferedReader(new FileReader("config.txt"));
		String line = "";

		// 1)read master address
		line = b.readLine();
		setMasterAddr(line);
		System.out.println("master address: " + line);

		// 2)read number of slaves
		line = b.readLine();
		setNumSlaves(line);
		System.out.println("number of slaves: " + line);

		// 3)read slave addresses
		slaveAddr = new InetAddress[numSlaves];
		for (int i = 0; i < numSlaves; i++) {
			line = b.readLine();
			setOneSlave(i, line);
			System.out.println("slave " + i + " address: " + line);
		}

		// 4) set port(port for connection with first slave)
		line = b.readLine();
		setPort(Integer.parseInt(line));
		System.out.println(" first port: " + port);
	}

	boolean isMyIP(InetAddress inetAddress) {
		try {
			System.out.println("My full list of Network Interfaces:");
			for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				System.out.println("    " + intf.getName() + " " + intf.getDisplayName());
				for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress address = enumIpAddr.nextElement();
					System.out.println("        " + address.toString());
					if (inetAddress.equals(address)) {
						return true;
					}
				}
			}
		} catch (SocketException e) {
			System.out.println(" (error retrieving network interface list)");
		}
		return false;
	}

	public Socket[] connect() throws IOException {
		// System.out.println("My IP is: " + InetAddress.getLocalHost().toString());
		Socket sockets[] = null;

		// if this machine is master
		// if(masterAddr.equals(InetAddress.getByName("localhost")) ||
		// masterAddr.equals(InetAddress.getLocalHost())){
		if (isMyIP(masterAddr)) {
			setId(-1);
			System.out.println("I am master");
			// create serverSocket for each slave, and then listen to request from each
			// slave
			masterSockets = new ServerSocket[numSlaves];
			for (int i = 0; i < numSlaves; i++) {
				try {
					Process p = Runtime.getRuntime().exec("fuser -k " + (port + i) + "/tcp");
					p.waitFor();
				} catch (Exception e) {
					e.printStackTrace();
				}
				masterSockets[i] = new ServerSocket(port + i);
				sockets = new Socket[numSlaves];
				for (int j = 0; j < numSlaves; ++j) {
					sockets[j] = null;
				}
				while (true) {
					sockets[i] = masterSockets[i].accept();
					// System.out.println("*****tag****");
					if (sockets[i] != null)
						break;
				}
				System.out.println("slave connection estblished " + i);
			}
			return sockets;
		}
		// else(is slave)
		else {
			sockets = new Socket[1];
			// find out which slave it is
			for (int i = 0; i < numSlaves; i++) {
				if (isMyIP(slaveAddr[i])) {
					setId(i);
					break;
				}
			}
			System.out.println("I am slave " + id);
			System.out.println("slave " + id + " will try to connect with master");
			// System.out.println(InetAddress.getByName("localhost").toString() + " " +
			// (port + id));
			try {
				Socket socket = new Socket(masterAddr, port + id);
				sockets[0] = socket;
			} catch (IOException e) {
				System.out.println("\n\nPlease start master first\n\n");
				throw e;
			}
			return sockets;
		}
	}

	public void close() {
		if (masterSockets != null) {
			for (int i = 0; i < masterSockets.length; ++i) {
				try {
					if (!masterSockets[i].isClosed()) {
						masterSockets[i].close();
					}
				} catch (Exception e) {
					e.printStackTrace(System.err);
				}
			}
			masterSockets = null;
		}
	}
}
