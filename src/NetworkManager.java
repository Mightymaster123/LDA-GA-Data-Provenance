
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;

public class NetworkManager {

	// Network protocol types.
	public final static int PROTOCOL_START_ORIGINAL = 1; // Master tells the slaves to start working (original version)
	public final static int PROTOCOL_STOP_ORIGINAL = 2; // Master tells the slaves to stop working (original version)
	public final static int PROTOCOL_FINISH_ORIGINAL = 3; // A slave tells the master that he has finished his job (original version)
	public final static int PROTOCOL_PREPARE_NEW = 11; // Master tells the slaves to prepare for work (new version)
	public final static int PROTOCOL_PROCESS_SUB_POPULATION_NEW = 12; // Master tells the slaves to work on a sub population
	public final static int PROTOCOL_STOP_NEW = 13; // Master tells the slaves to stop working (new version)
	public final static int PROTOCOL_FINISH_NEW = 14; // A slave tells the master that he has finished his job (new version)
	public final static int PROTOCOL_SHUTDOWN_PROCESS = 100;// Master tells the slaves to shutdown their processes.
	public final static int PROTOCOL_SLAVE_STATUS = 101; // Whether a slave is ready to work

	public final static int SLAVE_STATUS_NONE = 0;
	public final static int SLAVE_STATUS_IDLE = 1;
	public final static int SLAVE_STATUS_WORKING = 2;

	class ReceivedProtocol {
		public int targetMachineID;
		public int protocol;
		public Object obj;

		public ReceivedProtocol() {

		}

		public ReceivedProtocol(int _targetMachineID, int _protocol, Object _obj) {
			targetMachineID = _targetMachineID;
			protocol = _protocol;
			obj = _obj;
		}

		String to_string() {
			String str = "targetMachineID:" + targetMachineID + " protocol:" + protocol + NetworkManager.to_string(obj);
			return str;
		}
	}
	
	public class emptyObject implements java.io.Serializable{
	}

	public interface ReceivedProtocolHandler {
		void processProtocol(ReceivedProtocol protocol);
	}

	private static NetworkManager sInstance = new NetworkManager();

	public static NetworkManager getInstance() {
		return sInstance;
	}

	private InetAddress mMasterInetAddress = null;
	private int mNumSlaves = 0;
	private InetAddress mSlaveInetAddress[];
	private int mFirstPort = 0;

	private int mMyMachineID; // mMachineID = -1 is master, mMachineID >= 0 are slaves
	private ServerSocket mMasterServerSockets[] = null;
	private ListenerThread[] mListenerThread = null;
	private Socket[] mSockets = null;

	private ArrayList<ReceivedProtocol> mListReceivedProtocol = new ArrayList<ReceivedProtocol>();
	private ArrayList<ReceivedProtocolHandler> mReceivedProtocolHandler = new ArrayList<ReceivedProtocolHandler>();

	private int[] mSlaveStatus = null;

	public boolean isMaster() {
		return (-1 == mMyMachineID);
	}

	public boolean isSlave() {
		return !isMaster();
	}

	public int getSlaveCount() {
		return mNumSlaves;
	}

	public int getMachineCount() {
		return (mNumSlaves + 1);
	}

	public int getMyMachineID() {
		return mMyMachineID;
	}

	public void init() throws IOException {
		BufferedReader b = new BufferedReader(new FileReader("config.txt"));
		String line = "";

		// 1)read master address
		line = b.readLine();
		mMasterInetAddress = InetAddress.getByName(line);
		System.out.println("master address: " + line);

		// 2)read number of slaves
		line = b.readLine();
		mNumSlaves = Integer.parseInt(line);
		System.out.println("number of slaves: " + line);

		if (mNumSlaves > 0) {
			// 3)read slave addresses
			mSlaveInetAddress = new InetAddress[mNumSlaves];
			for (int i = 0; i < mNumSlaves; i++) {
				line = b.readLine();
				mSlaveInetAddress[i] = InetAddress.getByName(line);
				System.out.println("slave " + i + " address: " + line);
			}

			// 4) set port(port for connection with first slave)
			line = b.readLine();
			mFirstPort = Integer.parseInt(line);
			System.out.println(" first port: " + mFirstPort);
		}
		b.close();
		Connect();
	}

	public static boolean isMyIP(InetAddress inetAddress) {
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

	private boolean Connect() throws IOException {
		close();

		// if this machine is master
		if (isMyIP(mMasterInetAddress)) {
			mMyMachineID = -1;
			System.out.println("I am master");
			// create serverSocket for each slave, and then listen to request from each
			// slave

			mSlaveStatus = new int[mNumSlaves];
			for (int i = 0; i < mNumSlaves; ++i) {
				mSlaveStatus[i] = SLAVE_STATUS_NONE;
			}

			mMasterServerSockets = new ServerSocket[mNumSlaves];
			for (int i = 0; i < mNumSlaves; i++) {
				int currentPort = mFirstPort + i;
				try {
					Process p = Runtime.getRuntime().exec("fuser -k " + currentPort + "/tcp");
					p.waitFor();
				} catch (Exception e) {
					e.printStackTrace();
				}
				mMasterServerSockets[i] = new ServerSocket(currentPort);
				mSockets = new Socket[mNumSlaves];
				for (int j = 0; j < mNumSlaves; ++j) {
					mSockets[j] = null;
				}
				while (true) {
					mSockets[i] = mMasterServerSockets[i].accept();
					// System.out.println("*****tag****");
					if (mSockets[i] != null)
						break;
				}
				System.out.println("slave connection estblished " + i);
			}
		}
		// else(is slave)
		else {
			mMyMachineID = mNumSlaves;
			// find out which slave it is
			for (int i = 0; i < mNumSlaves; i++) {
				if (isMyIP(mSlaveInetAddress[i])) {
					mMyMachineID = i;
					break;
				}
			}
			if (mMyMachineID >= 0 && mMyMachineID < mNumSlaves) {
				System.out.println("I am slave " + mMyMachineID);
				System.out.println("slave " + mMyMachineID + " will try to connect with master");
				mSockets = new Socket[1];
				int seconds = 0;
				while (true) {
					try {
						mSockets[0] = new Socket(mMasterInetAddress, mFirstPort + mMyMachineID);
						break;
					} catch (IOException e) {
						e.printStackTrace();
						System.out.println("Connecting to master: " + seconds + " seconds\n\n");
					}

					try {
						Thread.sleep(1000);
					} catch (InterruptedException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					++seconds;
				}
			} else {
				System.out.println("I am neither a master nor a slave");
			}
		}
		// Create threads to listen to another machine
		if (mSockets != null && mSockets.length != 0) {
			mListenerThread = new ListenerThread[mSockets.length];
			for (int i = 0; i < mSockets.length; i++) {
				mListenerThread[i] = new ListenerThread(mSockets[i], i);
				mListenerThread[i].start();
				if (isMaster()) {
					System.out.println("Start thread to listen to slave " + i);
				} else {
					System.out.println("Start thread to listen to master");
				}
			}
		}

		return (mSockets != null && mSockets.length > 0);
	}

	public void close() {
		if (mListenerThread != null) {
			for (int i = 0; i < mListenerThread.length; ++i) {
				if (mListenerThread[i] != null) {
					mListenerThread[i].running = false;
					mListenerThread[i].interrupt();
				}
			}
			mListenerThread = null;
		}
		if (mMasterServerSockets != null) {
			for (int i = 0; i < mMasterServerSockets.length; ++i) {
				try {
					if (mMasterServerSockets[i] != null && !mMasterServerSockets[i].isClosed()) {
						mMasterServerSockets[i].close();
					}
				} catch (Exception e) {
					e.printStackTrace(System.err);
				}
			}
			mMasterServerSockets = null;
		}
		if (mSockets != null) {
			for (int i = 0; i < mSockets.length; ++i) {
				try {
					if (mSockets[i] != null && !mSockets[i].isClosed()) {
						mSockets[i].close();
					}
				} catch (Exception e) {
					e.printStackTrace(System.err);
				}
			}
			mSockets = null;
		}
		synchronized (NetworkManager.getInstance().mListReceivedProtocol) {
		mListReceivedProtocol.clear();
		}
		mReceivedProtocolHandler.clear();
		mSlaveStatus = null;
	}

	public void SetSlaveStatus(int slaveMachineID, int status) {
		if (mSlaveStatus != null && slaveMachineID >= 0 && slaveMachineID < mSlaveStatus.length) {
			mSlaveStatus[slaveMachineID] = status;
		}
	}

	public boolean IsAllSlavesIdle() {
		if (mSlaveStatus != null) {
			for (int i = 0; i < mSlaveStatus.length; ++i) {
				if (mSlaveStatus[i] != SLAVE_STATUS_IDLE) {
					return false;
				}
			}
		}
		return true;
	}

	public void waitForAllSlaves() {
		double seconds = 0.0f;
		try {
			while (!IsAllSlavesIdle()) {
				System.out.println("Some slaves are not idle. Wait for them: " + seconds + " seconds");
				Thread.sleep(100);
				seconds += 0.1f;
				dispatchProtocols();
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void registerReceivedProtocolHandler(ReceivedProtocolHandler handler) {
		if (!mReceivedProtocolHandler.contains(handler)) {
			mReceivedProtocolHandler.add(handler);
		}
	}

	public void unregisterReceivedProtocolHandler(ReceivedProtocolHandler handler) {
		while (mReceivedProtocolHandler.remove(handler)) {
		}
	}

	public void dispatchProtocols() {
		ArrayList<ReceivedProtocol> list = null;
		synchronized (NetworkManager.getInstance().mListReceivedProtocol) {
			if (NetworkManager.getInstance().mListReceivedProtocol != null && NetworkManager.getInstance().mListReceivedProtocol.size() > 0) {
				list = new ArrayList<>(NetworkManager.getInstance().mListReceivedProtocol);
			}
		}
		if (list == null) {
			return;
		}
		// dispatch protocols
		for (int iList = 0; iList < list.size(); ++iList) {
			ReceivedProtocol protocol = list.get(iList);
			if (protocol == null) {
				System.out.println("DispatchProtocols: protocol is null");
				continue;
			}
			if (protocol.protocol == NetworkManager.PROTOCOL_SLAVE_STATUS) {
				NetworkManager.getInstance().SetSlaveStatus(protocol.targetMachineID, (int) protocol.obj);
				continue;
			}
			for (int j = 0; j < mReceivedProtocolHandler.size(); ++j) {
				ReceivedProtocolHandler handler = mReceivedProtocolHandler.get(j);
				if (handler == null) {
					System.out.println("DispatchProtocols: handler " + j + " is null. protocol:" + protocol.to_string());
					continue;
				}
				try {
					handler.processProtocol(protocol);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	static String to_string(Object obj) {
		String msg = "";
		if (obj instanceof PopulationConfig) {
			PopulationConfig cfg = (PopulationConfig) obj;
			if (cfg != null) {
				msg += " " + cfg.to_string();
			}
		}
		if (obj instanceof PopulationConfig[]) {
			PopulationConfig[] cfgs = (PopulationConfig[]) obj;
			if (cfgs != null) {
				msg += " ";
				for (int j = 0; j < cfgs.length; ++j) {
					if (cfgs[j] != null) {
						msg += "  " + cfgs[j].to_string();
					}
				}
			}
		}
		if (obj instanceof Integer) {
			int num = (int)obj;
			msg += " " + num;
		}
		return msg;
	}

	private boolean send(int protocol, Object obj) {
		if (mSockets == null) {
			return false;
		}
		for (int iSocket = 0; iSocket < mSockets.length; ++iSocket) {
			Socket socket = mSockets[iSocket];
			if (socket == null) {
				continue;
			}
			try {
				ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
				output.writeObject(protocol);
				output.writeObject(obj != null ? obj : new emptyObject());
				System.out.println("Send to machine " + (isMaster() ? iSocket : -1) + "  protocol:" + protocol + " " + to_string(obj));
				return true;
			} catch (EOFException e) {
				e.printStackTrace();
			} catch (java.net.SocketException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return false;
	}

	public boolean sendProtocol_StartOriginal() {

		return send(PROTOCOL_START_ORIGINAL, null);
	}

	public boolean sendProtocol_StopOriginal() {

		return send(PROTOCOL_STOP_ORIGINAL, null);
	}

	public boolean sendProtocol_FinishOriginal(PopulationConfig cfg) {
		return send(PROTOCOL_FINISH_ORIGINAL, cfg);
	}

	public boolean sendProtocol_PrepareNew() {

		return send(PROTOCOL_PREPARE_NEW, null);
	}

	public boolean sendProtocol_ProcessSubPopulationNew(int targetMachineID, PopulationConfig[] cfgs) {
		if (mSockets == null) {
			return false;
		}
		int iSocket = targetMachineID;
		if (iSocket >= 0 && iSocket < mSockets.length) {
			Socket socket = mSockets[iSocket];
			if (socket != null) {
				try {
					int protocol = PROTOCOL_PROCESS_SUB_POPULATION_NEW;
					ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
					output.writeObject(protocol);
					output.writeObject(cfgs);
					System.out.println("Send to machine " + iSocket + "  protocol:" + protocol + " " + to_string(cfgs));
					return true;
				} catch (EOFException e) {
					e.printStackTrace();
				} catch (java.net.SocketException e) {
					e.printStackTrace();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return false;
	}

	public boolean sendProtocol_StopNew() {

		return send(PROTOCOL_STOP_NEW, null);
	}

	public boolean sendProtocol_FinishNew(PopulationConfig[] cfgs) {
		return send(PROTOCOL_FINISH_NEW, cfgs);
	}

	public boolean sendProtocol_ShutdownProcess() {

		return send(PROTOCOL_SHUTDOWN_PROCESS, null);
	}

	public boolean sendProtocol_SlaveStatus(int status) {

		return send(PROTOCOL_SLAVE_STATUS, status);
	}

	private class ListenerThread extends Thread {
		private Socket mSocket;
		private int mTargetMachineID;
		public boolean running = true;

		public ListenerThread(Socket socket, int targetMachineID) {
			mSocket = socket;
			mTargetMachineID = targetMachineID;
		}

		@Override
		public void run() {
			try {
				ObjectInputStream input = new ObjectInputStream(mSocket.getInputStream());
				while (input != null && running) {
					try {
						int protocol = (int) input.readObject();
						Object obj = input.readObject();
						System.out.println("Receive from machine " + mTargetMachineID + "  protocol:" + protocol + " " + to_string(obj));
						if (running) {
							synchronized (NetworkManager.getInstance().mListReceivedProtocol) {
								NetworkManager.getInstance().mListReceivedProtocol.add(new ReceivedProtocol(mTargetMachineID, protocol, obj));
							}
						}
					} catch (EOFException e) {
						e.printStackTrace();
						break;
					} catch (java.net.SocketException e) {
						e.printStackTrace();
						break;
					} catch (Exception e) {
						e.printStackTrace();
						break;
					}
				}
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

}
