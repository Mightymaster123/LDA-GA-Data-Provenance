import java.io.IOException;
import java.util.ArrayList;

public class Master implements NetworkManager.ReceivedProtocolHandler{

	static final int CHECK_COUNT = 5;
	
	private DataProvenance mDataProvenance = null;
	
	public void processProtocol(NetworkManager.ReceivedProtocol protocol)
	{
		if (protocol.protocol ==  NetworkManager.PROTOCOL_FINISH_ORIGINAL || protocol.protocol ==  NetworkManager.PROTOCOL_FINISH_NEW)
		{
			if(mDataProvenance!=null)
			{
				mDataProvenance.SlaveFinish(protocol);
			}
		}else
		{
			System.out.println("The Master should not receive procotol: "+protocol.protocol);
		}
	}
	
	public void run() throws IOException, InterruptedException, ClassNotFoundException {
		NetworkManager.getInstance().registerReceivedProtocolHandler(this);
		
		ArrayList<ResultStatistics> listResultOriginal = new ArrayList<ResultStatistics>(CHECK_COUNT);
		for (int i = 0; i < CHECK_COUNT; ++i) {
			NetworkManager.getInstance().waitForAllSlaves();
			mDataProvenance = new DataProvenance();
			listResultOriginal.add(mDataProvenance.run(true));
			NetworkManager.getInstance().sendProtocol_StopAllSlaves();
		}
		ArrayList<ResultStatistics> listResultNew = new ArrayList<ResultStatistics>(CHECK_COUNT);
		for (int i = 0; i < CHECK_COUNT; ++i) {
			NetworkManager.getInstance().waitForAllSlaves();
			mDataProvenance = new DataProvenance();
			listResultNew.add(mDataProvenance.run(false));
			NetworkManager.getInstance().sendProtocol_StopAllSlaves();
		}
		mDataProvenance = null;
		NetworkManager.getInstance().sendProtocol_ShutdownProcess();

		//print out result
		System.out.println("\n\n\n original_version:");
		for (int i = 0; i < listResultOriginal.size(); ++i) {
			ResultStatistics result = listResultOriginal.get(i);
			if (result != null && NetworkManager.getInstance().isMaster()) {
				System.out.println(result.to_string("    " + (i + 1) + "."));
			}
		}
		System.out.println("\n\n\n new_version:");
		for (int i = 0; i < listResultNew.size(); ++i) {
			ResultStatistics result = listResultNew.get(i);
			if (result != null && NetworkManager.getInstance().isMaster()) {
				System.out.println(result.to_string(String.format("    %3d.", (i + 1))));
			}
		}
		System.out.println("\n\n\n");
		
		NetworkManager.getInstance().unregisterReceivedProtocolHandler(this);
	}
}






