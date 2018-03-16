import java.io.IOException;
import java.util.ArrayList;

public class Slave implements NetworkManager.ReceivedProtocolHandler {
	private DataProvenance mDataProvenance = null;
	private ArrayList<NetworkManager.ReceivedProtocol> mListReceivedProtocol = new ArrayList<NetworkManager.ReceivedProtocol>();
	
	
	public void processProtocol(NetworkManager.ReceivedProtocol protocol)
	{
		if (protocol.protocol ==  NetworkManager.PROTOCOL_STOP)
		{
			if(mDataProvenance!=null)
			{
				mDataProvenance.stop();
			}
		}else
		{
			mListReceivedProtocol.add(protocol);
		}
	}
	
	public void run() throws IOException, InterruptedException, ClassNotFoundException {
		NetworkManager.getInstance().registerReceivedProtocolHandler(this);
		NetworkManager.getInstance().sendProtocol_SlaveStatus(NetworkManager.SLAVE_STATUS_IDLE);
		while(true)
		{
			NetworkManager.getInstance().dispatchProtocols();
			if(mListReceivedProtocol.size()<=0)
			{
				Thread.sleep(1);
				continue;
			}
			NetworkManager.ReceivedProtocol protocol = mListReceivedProtocol.get(0);
			mListReceivedProtocol.remove(0);
			if(protocol==null)
			{
				System.out.println("protocol is null");
				continue;
			}

			if(protocol.protocol == NetworkManager.PROTOCOL_START_ORIGINAL )
			{
				NetworkManager.getInstance().sendProtocol_SlaveStatus(NetworkManager.SLAVE_STATUS_WORKING);
				mDataProvenance = new DataProvenance();
				mDataProvenance.run(true);
				mDataProvenance = null;
				NetworkManager.getInstance().sendProtocol_SlaveStatus(NetworkManager.SLAVE_STATUS_IDLE);
			}else if(protocol.protocol == NetworkManager.PROTOCOL_PREPARE_NEW)
			{
				mDataProvenance = new DataProvenance();
				mDataProvenance.run(false);
			}else if(protocol.protocol == NetworkManager.PROTOCOL_PROCESS_SUB_POPULATION_NEW)
			{
				if(mDataProvenance!=null)
				{
					NetworkManager.getInstance().sendProtocol_SlaveStatus(NetworkManager.SLAVE_STATUS_WORKING);			
					mDataProvenance.StartSubPopulation(protocol);
					NetworkManager.getInstance().sendProtocol_SlaveStatus(NetworkManager.SLAVE_STATUS_IDLE);
				}else
				{
					System.out.println("mDataProvenance should not be null. "+protocol.to_string());
				}
			}else if(protocol.protocol == NetworkManager.PROTOCOL_SHUTDOWN_PROCESS)
			{
				NetworkManager.getInstance().sendProtocol_SlaveStatus(NetworkManager.SLAVE_STATUS_NONE);
				break;
			}else
			{
				System.out.println("Slaves should not receive procotol: "+protocol.to_string());
			}
			NetworkManager.getInstance().sendProtocol_SlaveStatus(NetworkManager.SLAVE_STATUS_IDLE);
		}
		NetworkManager.getInstance().unregisterReceivedProtocolHandler(this);
	}
}






