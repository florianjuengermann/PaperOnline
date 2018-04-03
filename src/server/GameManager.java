package server;

import constants.C;
import game.GameModel;
import game.Player;

public class GameManager implements Runnable {
	private Thread maint;
	private final ServerMain sm;
	int tickCounter = 0;
	final int w;
	final GameModel gm;
	long startTime;

	private static final int MAP_SIZE = 50;

	// MAIN GAME VARIABLES/STEPS SYNCED BY THIS!!

	public GameManager(ServerMain serverMain, boolean headless) {
		sm = serverMain;
		gm = new GameModel(MAP_SIZE);
		w = MAP_SIZE;
		maint = new Thread(this);
		if(!headless)
			new ServerRenderer(this).start();
		startTicking();
	}

	byte[] genWelcomeMessage(byte id) {
		//convert to byte[]
		//TODO send delay?
		// C.WELCOME, ID, tickcounter, startTime, MAP_SIZE, map, players.size(), players
		int length = 1 + 1 + 4 + 8 + 8 + 4 + MAP_SIZE * MAP_SIZE + 1 + (4 * 2 + 2) * gm.getPlayers().size(); // 1B id + 1B name length + 3x 4B ints
		for (Player p : gm.getPlayers())
			length += 1 + p.name.getBytes().length;

		byte[] data = new byte[length];
		int i = 0;
		data[i++] = C.ANS_WELCOME;
		data[i++] = id;
		writeInt(data, i, tickCounter);
		i += 4;
		System.out.printf("SERVER: Starttime= %x (%x , %x)\n", startTime, (int) (startTime >> 32), (int) startTime);
		System.out.printf("SERVER: Starttime= %d \n", startTime);
		long time = System.currentTimeMillis();
		writeInt(data, i, (int) (startTime >> 32));
		i += 4;
		writeInt(data, i, (int) startTime);
		i += 4;
		writeInt(data, i, (int) (time >> 32));
		i += 4;
		writeInt(data, i, (int) time);
		i += 4;
		writeInt(data, i, MAP_SIZE);
		i += 4;
		for (int x = 0; x < MAP_SIZE; ++x) {
			for (int y = 0; y < MAP_SIZE; ++y) {
				data[i++] = gm.map.map[x][y];
			}
		}
		data[i++] = (byte) gm.getPlayers().size();
		for (Player p : gm.getPlayers()) {
			byte[] a = sendPlayer(p);
			System.arraycopy(a, 0, data, i, a.length);
			i += a.length;
		}
		return data;
	}

	private void writeInt(byte[] map, int i, int a) {
		map[i++] = (byte) ((a >> 24) & 0xFF);
		map[i++] = (byte) ((a >> 16) & 0xFF);
		map[i++] = (byte) ((a >> 8) & 0xFF);
		map[i++] = (byte) ((a >> 0) & 0xFF);
	}

	byte getID() {
		byte id;
		do {
			id = (byte) (Math.random() * 127);
		} while (gm.playerById(id) != null);
		return id;
	}

	void removePlayer(Client c) {
		if (gm.playerById(c.id) != null)
			gm.removePlayer(gm.playerById(c.id));
	}


	void newPlayer(Client c, String name) {
		byte[] data;
		synchronized (this) {
			data = genWelcomeMessage(c.id);
		}
		System.out.println("SERVER: Welcome message generated.");
		//send welcome message
		c.send(data);

		// *now* update map
		int posX = (int) (Math.random() * (MAP_SIZE - 2) + 1);
		int posY = (int) (Math.random() * (MAP_SIZE - 2) + 1);
		// TODO check if position is valid
		Player p = new Player(gm, name, c.id, posX, posY, C.DIR_W);
		gm.addPlayer(p, true);

		synchronized (this) {
			gm.map.spawn(c.id, posX, posY);
		}

	}

	void moveRequest(Client client, int direction) {
		broadcastMovement(client.id, direction, getTick());
	}

	private int getTick() {
		int i;
		synchronized (this) {
			i = tickCounter;
		}
		return i;
	}

	@Override
	public void run() {
		startTime = System.currentTimeMillis();
		while (!maint.isInterrupted()) {
			mainTick();
		}
	}

	/**
	 * THIS SERVER CAN BE STOPPED WITH MAINT.INTERRUPT
	 */

	private void mainTick() {
		synchronized (this) {
			gm.mainTick();
			// now all flags are set!
			// read flags, manage stuff
			for (Player p : gm.getPlayers()) {
				if (p.isDead != C.PLS_ALIVE) {
					switch (p.isDead) {
						case C.PLS_KILLED:
							broadcastDeath(p.id, C.CODE_KILLED, p.wasKilledByWhom);
							break;
						case C.PLS_WALL:
							broadcastDeath(p.id, C.CODE_WALL);
							break;
						case C.PLS_OWNTRAIL:
							broadcastDeath(p.id, C.CODE_OWNTRAIL);
							break;
					}
					// the dead players will be removed by call of removeZombies ^^
				} else if (p.hasClaimed) {
					broadcastClaim(p.id);
				}
			}
			gm.removeZombies();
			tickCounter++;
		}
		try {
			Thread.sleep(Math.max(0, startTime + C.DELAY * tickCounter - System.currentTimeMillis()));
		} catch (InterruptedException e) {
			maint.interrupt();
		}
	}

	private void startTicking() {
		maint.start();
	}

	byte[] sendPlayer(Player p) {
		byte[] name = p.name.getBytes();
		byte[] data = new byte[1 + name.length + 1 + 4 + 4 + 1];
		int i = 0;
		data[i++] = (byte) name.length;
		System.arraycopy(name, 0, data, i, name.length);
		i += name.length;
		data[i++] = p.id;
		writeInt(data, i, p.x);
		i += 4;
		writeInt(data, i, p.y);
		i += 4;
		data[i++] = (byte) p.dir;
		return data;
	}

	void broadcastJoin(int pid) {
		System.out.println("SERVER: Broadcasting join, id = " + pid);
		Player p = gm.playerById(((byte) pid));
		byte[] d = sendPlayer(p);
		byte[] data = new byte[d.length + 1 + 4];
		data[0] = C.UPDATE_JOIN;
		System.arraycopy(d, 0, data, 1, d.length);
		writeInt(data, d.length + 1, getTick());
		broadcast(data);
	}

	void broadcastMovement(int pid, int direction, int tick) {
		System.out.println("SERVER: Broadcasting movement, id = " + pid);
		byte[] data = new byte[1 + 1 + 1 + 4];
		int i = 0;
		data[i++] = C.UPDATE_MOVEMENT;
		data[i++] = (byte) pid;
		data[i++] = (byte) direction;
		writeInt(data, i, tick);
		broadcast(data);
	}

	void broadcastDeath(int pid, byte reason) {
		System.out.println("SERVER: Broadcasting death, id = " + pid + " reason: " + reason);
		byte[] data = new byte[1 + 1 + 1 + 4];
		int i = 0;
		data[i++] = C.UPDATE_KILL;
		data[i++] = (byte) pid;
		data[i++] = reason;
		writeInt(data, i, getTick());
		broadcast(data);
	}

	void broadcastDeath(int pid, byte reason, byte killer) {//reason == C.CODE_KILLED
		System.out.println("SERVER: Broadcasting killed, id = " + pid);
		byte[] data = new byte[1 + 1 + 1 + 1 + 4];
		int i = 0;
		data[i++] = C.UPDATE_KILL;
		data[i++] = (byte) pid;
		data[i++] = reason; //C.CODE_KILLED
		data[i++] = killer;
		writeInt(data, i, getTick());
		broadcast(data);
	}

	void broadcastClaim(int pid) {
		byte[] data = new byte[1 + 1 + 4];
		int i = 0;
		data[i++] = C.UPDATE_CLAIM;
		data[i++] = (byte) pid;
		writeInt(data, i, getTick());
		broadcast(data);
	}

	void broadcast(byte[] data) {
		synchronized (sm) {
			for (Client c : sm.clients) {
				c.send(data);
			}
		}
	}

}