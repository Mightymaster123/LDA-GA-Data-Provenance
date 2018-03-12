import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class MySocket {
	public Socket socket;
	public int target_machine_id;

	public MySocket(Socket _socket, int _target_machine_id) {
		socket = _socket;
		target_machine_id = _target_machine_id;
	}

	String to_string(population_config[] cfg_array) {
		if (cfg_array == null || cfg_array.length <= 0) {
			return "";
		}
		String text = "";
		for (int i = 0; i < cfg_array.length; ++i) {
			text += "  " + cfg_array[i].to_string();
		}
		return text;
	}

	public boolean send(population_config[] cfg_array) {
		try {
			ObjectOutputStream output = null;
			output = new ObjectOutputStream(socket.getOutputStream());
			output.writeObject(cfg_array);
			System.out.println("send to machine " + target_machine_id + to_string(cfg_array));
			return true;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	public population_config[] receive() {
		ObjectInputStream input = null;
		try {
			input = new ObjectInputStream(socket.getInputStream());
			if (input != null) {
				population_config[] cfg_array = (population_config[]) input.readObject();
				System.out.println("receive from machine " + target_machine_id + to_string(cfg_array));
				return cfg_array;
			}
		} catch (EOFException e) {
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public void close() {
		if (socket != null) {
			try {
				if (!socket.isClosed()) {
					socket.close();
				}
			} catch (Exception e) {
				e.printStackTrace(System.err);
			}
			socket = null;
		}
	}
}