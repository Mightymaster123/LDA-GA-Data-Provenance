import java.io.IOException;
import java.util.ArrayList;

public class Master implements NetworkManager.ReceivedProtocolHandler{

	static final int TEST_COUNT = 10;
	
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
		
		ArrayList<ResultStatistics> listResultOriginal = new ArrayList<ResultStatistics>(TEST_COUNT);
		for (int i = 0; i < TEST_COUNT; ++i) {
			NetworkManager.getInstance().waitForAllSlaves();
			mDataProvenance = new DataProvenance();
			listResultOriginal.add(mDataProvenance.run(true));
			NetworkManager.getInstance().sendProtocol_StopAllSlaves();
			mDataProvenance = null;
		}
		ArrayList<ResultStatistics> listResultNew = new ArrayList<ResultStatistics>(TEST_COUNT);
		for (int i = 0; i < TEST_COUNT; ++i) {
			NetworkManager.getInstance().waitForAllSlaves();
			mDataProvenance = new DataProvenance();
			listResultNew.add(mDataProvenance.run(false));
			NetworkManager.getInstance().sendProtocol_StopAllSlaves();
			mDataProvenance = null;
		}
		mDataProvenance = null;
		NetworkManager.getInstance().sendProtocol_ShutdownProcess();

		System.out.println(	"\n\n" + NetworkManager.getInstance().getMachineCount() + " machines"+
							"    "+geneticLogic.THREADS_PER_MACHINE+" threads-per-machine"+
							"    topic-range:["+PopulationConfig.MIN_TOPIC_COUNT+","+PopulationConfig.MAX_TOPIC_COUNT +"]"+
							"    iteration-range:["+PopulationConfig.MIN_ITERATION_COUNT+","+PopulationConfig.MAX_ITERATION_COUNT+ "]");
		
		//print out result
		System.out.println("\noriginal_version:");
		for (int i = 0; i < listResultOriginal.size(); ++i) {
			ResultStatistics result = listResultOriginal.get(i);
			if (result != null && NetworkManager.getInstance().isMaster()) {
				System.out.println(result.to_string(String.format("  %3d.", (i + 1))));
			}
		}
		System.out.println("\nnew_version:");
		for (int i = 0; i < listResultNew.size(); ++i) {
			ResultStatistics result = listResultNew.get(i);
			if (result != null && NetworkManager.getInstance().isMaster()) {
				System.out.println(result.to_string(String.format("  %3d.", (i + 1))));
			}
		}
		System.out.println("\n\n\n");
		
		NetworkManager.getInstance().unregisterReceivedProtocolHandler(this);
	}
}






