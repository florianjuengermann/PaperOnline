package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import java.util.List;

public class ServerMain {
	private ServerSocket ss;
	List<Client> clients = new LinkedList<>();
	GameManager gm;

	public ServerMain(int port) {
		this(port, false);
	}

	public ServerMain(int port, boolean headless) {
		gm = new GameManager(this, headless);
		try {
			ss = new ServerSocket(port);
			System.out.println("Server started...");
			for (;;) {
				Socket s = ss.accept();
				System.out.println("Client accepted: " + s.getInetAddress());
				synchronized (this) {
					clients.add(new Client(s, this));
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	synchronized void removeClient(Client c) {
		clients.remove(c);
	}
	public void shutdown(){
		try {
			ss.close();
		} catch (IOException e) {
		}
		for (Client c : clients)
			c.stop();

	}
}
