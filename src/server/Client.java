package server;

import constants.C;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Queue;

public class Client {
	private Socket s;
	private ServerMain sm;
	private BufferedInputStream in;
	private BufferedOutputStream out;
	private Thread inthread, outthread;
	private Queue<byte[]> tosend = new LinkedList<>();
	String name;
	byte id;
	private boolean hasRequestedLeave = false, isAway = false, joined = false;

	public Client(Socket s, ServerMain sm) throws IOException {
		this.s = s;
		this.sm = sm;
		in = new BufferedInputStream(s.getInputStream());
		out = new BufferedOutputStream(s.getOutputStream());

		outthread = new Thread(this::startSending);
		outthread.start();
		inthread = new Thread(this::startReceiving);
		inthread.start();
	}

	private void startReceiving() {
		try {
			for (; ; ) {
				int code = in.read();
				// shou	ld block.
				// DDOS possible SCHAAADE
				System.out.println("SERVER: Incomming: " + code);
				if (!joined) {
					if (code == C.REQ_JOIN)
						login();
					else
						throw new ProtocolException();
				} else
					// ingame requests, not join
					switch (code) {
						case C.REQ_MOVE:
							byte dir = (byte) in.read();
							synchronized (sm.gm) {
								sm.gm.gm.playerById(id).dir = dir;
							}
							System.out.println("SERVER: req move from player " + id + " dir: " + dir);
							sm.gm.moveRequest(this, dir);
							break;
						case C.REQ_LEAVE:
							hasRequestedLeave = true;
							/**
							 * UPDATE TODO
							 * Spieler aus Listen entfernen, nur Sockets offen gammeln lassen, bis sie sterben.
							 * Bei Socketfehler einfach auch aus Listen entfernen
							 * Das hier ist etwas hirnlos.
							 */
							sm.gm.broadcastDeath(id, C.CODE_LEAVE);
							break;
						default:
							throw new ProtocolException();
					}
			}
		} catch (IOException e) {
			connectionLost();
		}
	}

	void send(byte[] data) {
		if (!hasRequestedLeave) {
			synchronized (this) {
				tosend.add(data);
			}
//			System.out.println("SERVER: Data added to queue, notifying sendthread.");
			synchronized (outthread) {
				outthread.notify();
			}
		}
	}

	/**
	 * TODO
	 * viele Richtungsänderungen von Clients ignorieren, um Überlastung zu vermeiden
	 * vllt unnötig, weil man nicht spamt
	 * ggf im Client Bremse einbauen
	 */

	private void startSending() {
		try {
			while (!outthread.isInterrupted()) {
//				System.out.println("SERVER: Sending thread is waiting.");
				synchronized (outthread) {
					outthread.wait();
				}
//				System.out.println("SERVER: Sending thread woke up.");
				while (true) {
					byte[] data;
					synchronized (this) {
						if (tosend.isEmpty())
							break;
						data = tosend.poll();
					}
					synchronized (data) {
						System.out.println("SERVER: sending: " + data[0] + "...");
//						Thread.sleep(800);//TODO!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
						out.write(data);
					}
					out.flush();
				}
			}
		} catch (Exception e) {
			connectionLost();
		}
	}

	private synchronized void connectionLost() {
		if (isAway)
			// only for the two threads to sync
			return;

		if (!hasRequestedLeave)
			sm.gm.broadcastDeath(id, C.CODE_ERR);
		// else ignore, we already broadcasted this.

		sm.removeClient(this);
		sm.gm.removePlayer(this); // should normally already be removed
		isAway = true; // otherwise the other io thread will broadcast again
	}

	private void login() throws IOException {
		// TODO send 'full' mesg
		// receive name
		byte[] name = new byte[in.read()];
		in.read(name);
		this.name = new String(name);
		System.out.println("SERVER: Name: " + this.name);

		// send ok, ID
		id = sm.gm.getID();
		sm.gm.newPlayer(this, this.name);
		sm.gm.broadcastJoin(id);
		joined = true;
	}


	public void stop() {
		try {
			s.close();
		} catch (IOException e) {
		}
	}
}

class ProtocolException extends IOException {
}