import java.io.IOException;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

public class Main {

	public static void main(String[] argv) throws IOException, InterruptedException, ClassNotFoundException {
		// Build connection between multiple machines: 1 master, multiple slaves
		NetworkManager.getInstance().init();

		if (NetworkManager.getInstance().isMaster()) {
			Master master = new Master();
			master.run();
		} else {
			Slave slave = new Slave();
			slave.run();
		}
		
		NetworkManager.getInstance().close();

	}
}
