package client;

import constants.C;
import game.GameModel;
import game.Player;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Queue;

public class ClientMain implements WindowListener, KeyListener, ComponentListener {
	Socket socket;
	String name;
	BufferedInputStream in;
	BufferedOutputStream out;

	// GUI
	JFrame frame;
	BufferStrategy bufferStrategy;
	BufferedImage offImg;
	Graphics2D offGr;

//	JPanel dp; // drawpanel.. ^^

	// Threads
	Thread inT, outT, gameT, drawT;
	// io
	Queue<byte[]> tosend = new LinkedList<>();

	// GAME
	GameModel[] models = new GameModel[C.MODEL_COUNT];
//	byte[] myDirections = new byte[C.MODEL_COUNT];
	byte id;
	int currModelIndex = 0;
	boolean iJoined = false, dead = false;

	long startTime;
	int tickcounter;
	int mapSize;

	// drawing
	private int ox, oy;
	private float scale;
	private float tickPercentage;

//	public static void main(String args[]) {
//		new ClientMain("192.168.178.39", 3456, "Player " + (int) (Math.random() * 1000));
//	}


	public ClientMain(String ip, int port, String name) {
		init(ip, port, name);
	}

	// todo main should get these params!
	public ClientMain() {
//		String[] connDeatails = showSetupWindow("192.168.178.42", 4576, "Player " + (int) (Math.random() * 1000 + 1), null);
		String[] connDeatails = showSetupWindow("localhost", 5555, "Player " + ((int) (Math.random() * 1000) + 1), null);
		if (connDeatails != null)
			init(connDeatails[0], Integer.parseInt(connDeatails[1]), connDeatails[2]);
	}

	void init(String ip, int port, String name) {
		this.name = name;
		try {
			socket = new Socket(ip, port);
			in = new BufferedInputStream(socket.getInputStream());
			out = new BufferedOutputStream(socket.getOutputStream());
			startEverything();
		} catch (Exception e) {
			errDia(e, null);
			// if init fails the reference to clientmain is wasted.
		}
	}

	boolean initOK = false;

	private void startEverything() throws IOException, InterruptedException {
		Thread connT = new Thread(this::connect);
		connT.start();
		connT.join(2000);//todo
		if (!initOK) {
			// ERROR
			try {
				socket.close();
			} catch (Exception ignored) {
			}
			throw new IOException("Server doesn´t answer");
		}

		// start gui
		makeGUI();

		// init threads (io, game)
		gameT = new Thread(this::gameloop, "Gamethread");
		drawT = new Thread(this::drawloop, "Drawthread");

		outT = new Thread(() -> {
			try {
				startSending();
			} catch (Exception e) {
				stopEverything();
			}
		}, "Outthread");
		inT = new Thread(() -> {
			try {
				startReceiving();
			} catch (Exception e) {
				stopEverything();
			}
		}, "Inthread");

		inT.start();
		outT.start();
		gameT.start();
		drawT.start();
	}


	//##################################################
	// LOOPS, Threads, bla
	//##################################################

	// GAMETHREAD
	private void gameloop() {
		while (!gameT.isInterrupted()) {
			if (!dead) { // todo wollen wir, dass es nach dem eigenen Tod als "spectator" weiterläuft?
				gameTick();
//				repaint();
				gameDelay(); // increments tickcounter
			}
		}
	}

	private synchronized void gameTick() {
		// set my direction
//		if (iJoined)
//			models[currModelIndex].playerById(id).dir = myDirections[currModelIndex];//TODO throws NPE when players leave
		// copy the current state

		GameModel g = models[currModelIndex++].copyTo(models[currModelIndex %= models.length]);
//		myDirections[currModelIndex] = myDirections[currModelIndex == 0 ? models.length - 1 : currModelIndex - 1];
		models[currModelIndex] = g;
		// tick the copy!
		g.mainTick();
		g.removeZombies();
	}

	private void gameDelay() {
		int tickcouter;
		synchronized (this) {
			this.tickcounter++;
			tickcouter = this.tickcounter;
		}
		try {
			long d = startTime + tickcouter * C.DELAY - System.currentTimeMillis();
//			System.out.printf("CLIENT: Sleeping for %d millis (startime: %d, tickcouter %d).\n", d, startTime, tickcouter);
			Thread.sleep(Math.max(0, d));
		} catch (InterruptedException e) {
			//happens when game is exited
			if (!dead)
				e.printStackTrace();
		}
	}


	// DRAWTHREAD
	int c = 0;

	private void drawloop() {
		long lastDraw = System.nanoTime();
		while (!drawT.isInterrupted()) {
			synchronized (this) {
				long currTickTime = startTime + (tickcounter - 1) * C.DELAY;
				tickPercentage = (System.currentTimeMillis() - currTickTime) / (float) C.DELAY;


				models[currModelIndex].draw(offGr, ox, oy, scale, tickPercentage);// -> offImg
			}
			Graphics2D buffGr = (Graphics2D) bufferStrategy.getDrawGraphics();

			buffGr.drawImage(offImg, 0, 0, null);
			buffGr.dispose();
			bufferStrategy.show();
			Toolkit.getDefaultToolkit().sync();

			long nanoSleep = lastDraw + (long) 16e6 - System.nanoTime();
			c++;
//			if (c % 100 == 0) {
//				System.out.println((-lastDraw + System.nanoTime()) * 1e-6);
//			}
			lastDraw = System.nanoTime();
			try {
				if (nanoSleep > 0)
					Thread.sleep(nanoSleep / (long) 1e6, (int) (nanoSleep % (long) 1e6));
			} catch (InterruptedException ignored) {
			}
		}
	}

	//####################################################
	// UPDATE-METHODS, game logic, ..
	//####################################################

	synchronized void movementUpdate(byte pid, byte newdir, int tick) {
		int dt = toPast(tick);

		if (dt > 0) { // debug todo
			System.out.println("                              dt > 0!!!!");
		}

		models[currModelIndex].playerById(pid).dir = newdir; // change the past

		if (pid == id) {
			toPresent(dt, newdir);
		} else {
			toPresent(dt);
		}
	}

	synchronized void joinUpdate(Player p, int tick) {
		if (p.id == id)
			iJoined = true;

		int dt = toPast(tick);
		models[currModelIndex].addPlayer(p, true); // change the past

//		if (p.id == id) {
//			myDirections[currModelIndex] = (byte) p.dir;
//			toPresent(dt);
//		} else
			toPresent(dt, (byte) p.dir);
	}

	synchronized void leaveUpdate(byte pid, int tick) {
		if (pid == id) { // oh, it´s me
			stopEverything(); // TODO dialog first!?
			JOptionPane.showMessageDialog(frame, "You died :("); // TODO display reason
		}
		int dt = toPast(tick);
		//######
		Player p = models[currModelIndex].playerById(pid);
		if (p != null)
			models[currModelIndex].removePlayer(p);
		//######
		toPresent(dt);
	}

	private int toPast(int tick) {
		int dt = tickcounter - tick; // delta tick ^^
		if (dt < 0) {
			System.out.printf("tick %d , tickcounter %d \n", tick, tickcounter);
			System.exit(0xDEADBEEF); // we don´t like forecasting
		}
		if (dt >= models.length)
			System.exit(0xDECAF);// TODO throw timeout: not enough buffer for so big delays!!

		currModelIndex = (currModelIndex - dt + models.length) % models.length;
		return dt;
	}

	private void toPresent(int dt) {
		for (int i = 0; i < dt; i++) { // run up to now
			gameTick();
		}
	}
	// if player is this player

	private void toPresent(int dt, byte myDir) {
		// this player
		for (int i = 0; i < dt; i++) { // run up to now
//			myDirections[currModelIndex] = myDir;
			gameTick();
		}
	}


	// inT

	private void startReceiving() throws IOException {
		while (!inT.isInterrupted()) {
			byte code = (byte) in.read();
			switch (code) {
				// who needs this
				case C.UPDATE_CLAIM: {
					byte pid = (byte) in.read();
					int tick = readInt();
					break;
				}
				case C.UPDATE_KILL: {
					byte pid = (byte) in.read();
					byte reason = (byte) in.read();
					if (reason == C.CODE_KILLED) {
						int idKiller = (byte) in.read();
					}
					int tick = readInt();
					leaveUpdate(pid, tick);
					break;
				}
				case C.UPDATE_JOIN:
					synchronized (this) {
						Player p = readPlayer(models[currModelIndex]);
						int tick = readInt();
//						if (p.id != id)
						joinUpdate(p, tick);
						System.out.printf("CLIENT: Player added in tick %d \n.", tick);
					}
					break;
				case C.UPDATE_MOVEMENT: {
					byte pid = ((byte) in.read());
					byte dir = ((byte) in.read());
					int tick = readInt();
//					if (pid != id)
					movementUpdate(pid, dir, tick);
					break;
				}

			}
		}
	}
	// outT

	private void startSending() throws InterruptedException, IOException {
		while (!outT.isInterrupted()) {
			synchronized (outT) {
				outT.wait(); // todo dont do this, use monitor object
			}
			while (true) {
				byte[] data;
				synchronized (tosend) {
					if (tosend.isEmpty())
						break;
					data = tosend.poll();
				}
				synchronized (data) {
					out.write(data);
				}
				out.flush();
			}
		}
	}

	void send(byte[] data) {
		synchronized (tosend) {
			tosend.add(data);
		}
		synchronized (outT) {
			outT.notify();
		}
	}

	Player readPlayer(GameModel gm) throws IOException {
		int nameLength = in.read();
		byte[] nameArray = new byte[nameLength];
		in.read(nameArray);
		byte id = (byte) in.read();
		int x = readInt();
		int y = readInt();
		int dir = in.read();
		System.out.println("CLIENT: read player with dir " + dir);
		return new Player(gm, new String(nameArray), id, x, y, dir);
	}

	//###########################
	// IO, network
	//###########################

	void connect() {
		try {
			// trim name
			if (name.length() > 127)
				name = name.substring(0, 128);
			byte[] tosend = new byte[1 + 1 + name.length()];
			tosend[0] = C.REQ_JOIN;
			tosend[1] = (byte) name.length();
			System.arraycopy(name.getBytes(), 0, tosend, 2, name.getBytes().length);
			out.write(tosend);
			long startT = System.currentTimeMillis();
			out.flush();

			// receive welcome message
			int code = in.read();
			long rtt = System.currentTimeMillis() - startT;
			System.out.println("CLIENT: rtt: " + rtt);
			System.out.println("CLIENT: connect-Code: " + code);
			if (code == C.ANS_WELCOME) {
				id = (byte) in.read();
				tickcounter = readInt();
				startTime = ((long) readInt()) << 32 | readInt() & 0xFFFFFFFFL;
				long serverTime = ((long) readInt()) << 32 | readInt() & 0xFFFFFFFFL;
				long diff = serverTime + rtt / 2 - System.currentTimeMillis();
				startTime -= diff;
				System.out.println("CLIENT: tickcounter= " + tickcounter);

				mapSize = readInt();

				GameModel gm = new GameModel(mapSize);
				for (int x = 0; x < mapSize; ++x) {
					for (int y = 0; y < mapSize; ++y) {
						gm.map.map[x][y] = (byte) in.read();
					}
				}
				System.out.print("CLIENT: Adding  players: ");
				byte pCount = (byte) in.read();
				for (int i = 0; i < pCount; ++i) {
					Player p = readPlayer(gm);
					System.out.print(p.name + ", ");
					gm.addPlayer(p, false);
				}
				System.out.println(".");
				models[currModelIndex] = gm; // thread-safe, runns in advance
				initOK = true;
			} else {
				errDia("Server refused, error code: " + code, frame);
			}
		} catch (IOException e) {
			try {
				socket.close();
			} catch (IOException ignored) {
			}
		}
	}

	int readInt() throws IOException {
		int i = in.read() << 24;
		i += in.read() << 16;
		i += in.read() << 8;
		i += in.read() << 0;
		return i;
	}


	private void sendLeaveRequest() {
		send(new byte[]{C.REQ_LEAVE});
	}

	void sendDir(byte dir) {
		//TODO check if own player already exists
		send(new byte[]{C.REQ_MOVE, dir});
//		synchronized (this) {
//			myDirections[currModelIndex] = dir;
//		}
	}

	private void stopEverything() {
		dead = true;
		if (!socket.isClosed())
			try {
				socket.close();
			} catch (IOException e) {
				errDia(e, frame);
			} finally {
				try {
					socket.close();
				} catch (IOException ignored) {
					// da kann jetzt echt keiner mehr helfen.
				}
			}
		if (!drawT.isInterrupted())
			drawT.interrupt();
		if (!gameT.isInterrupted())
			gameT.interrupt();
		if (!inT.isInterrupted())
			inT.interrupt();
		if (!outT.isInterrupted())
			outT.interrupt();
		// ciao
	}

	//###########################
	// G U I
	//###########################

	private GraphicsConfiguration config;

	private void makeGUI() {
		frame = new JFrame("PaperOnline Player: " + name);
		frame.setMinimumSize(new Dimension(300, 300));
		frame.setSize(new Dimension(C.FRAME_SIZE, C.FRAME_SIZE));
//		frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
		frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(this);
		frame.addKeyListener(this);
		frame.addComponentListener(this);
		frame.setIgnoreRepaint(true);
		config = GraphicsEnvironment.getLocalGraphicsEnvironment()
				.getDefaultScreenDevice()
				.getDefaultConfiguration();
		Canvas canvas = new Canvas(config);
		canvas.setSize(C.FRAME_SIZE, C.FRAME_SIZE);
		frame.add(canvas);
		frame.setVisible(true);
		canvas.addKeyListener(this);

		canvas.createBufferStrategy(2);
		bufferStrategy = canvas.getBufferStrategy();

		resize(20, 20);
	}


	private BufferedImage getNewOffImage(int w, int h) {
		return config.createCompatibleImage(w, h, Transparency.OPAQUE);
	}

	public synchronized void resize(int ox, int oy) {
		this.ox = ox;
		this.oy = oy;
		offImg = getNewOffImage(frame.getWidth(), frame.getHeight()); // EXPENSIVE!!!
		offGr = (Graphics2D) offImg.getGraphics();
		scale = Math.min((float) (frame.getContentPane().getWidth() - ox) / (mapSize + 3),
				(float) (frame.getContentPane().getHeight() - oy) / (mapSize + 3));
	}

	// gui: open dialogs

	static String[] showSetupWindow(String ip, int port, String name, Component parent) {
		JPanel pan = new JPanel();
		pan.setLayout(new GridLayout(0, 2));
		JTextField ipfield = new JTextField(ip, 6); // todo regex for ip
		JTextField namefield = new JTextField(name, 6);
		JTextField portfield = new JTextField(String.valueOf(port), 6);
		pan.add(new JLabel("Server IP"));
		pan.add(ipfield);
		pan.add(new JLabel("Port"));
		pan.add(portfield);
		pan.add(new JLabel("Name"));
		pan.add(namefield);
//		JOptionPane.showMessageDialog(parent, pan);
		if (JOptionPane.showConfirmDialog(parent, pan, "Connect to server...", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.CANCEL_OPTION)
			return null;
		return new String[]{ipfield.getText(), portfield.getText(), namefield.getText()};
	}

	private void errDia(Exception e, Component c) {
		errDia(e.getMessage(), c);
		e.printStackTrace(); // debug todo
	}

	private void errDia(String mesg, Component c) {
		JOptionPane.showMessageDialog(c, mesg, "ERROR!", JOptionPane.ERROR_MESSAGE);
	}

	// Event handlers
	Thread resizeDelayer = null;

	@Override
	public void componentResized(ComponentEvent componentEvent) {
		// call resize... but not poll it
		if (resizeDelayer == null) {
			resizeDelayer = new Thread(() -> {
				try {
					Thread.sleep(500);
				} catch (InterruptedException ignored) {
				}
				resize(20, 20);
				resizeDelayer = null;
			});
			resizeDelayer.start();
		}
	}

	@Override
	public void windowClosing(WindowEvent e) {
		if (JOptionPane.showConfirmDialog(frame, "Exit?", "Exit", JOptionPane.YES_NO_OPTION)
				== JOptionPane.OK_OPTION) {
			sendLeaveRequest();
			stopEverything();
			stopEverything();
			frame.dispose();
		}
	}

	int lastMoveSend = -1;

	@Override
	public void keyPressed(KeyEvent e) {
		int code = e.getKeyCode();
		System.out.println("Client: key pressed: " + code);
		if (code != lastMoveSend) {
			switch (code) {
				case KeyEvent.VK_W:
					sendDir(C.DIR_N);
					break;
				case KeyEvent.VK_S:
					sendDir(C.DIR_S);
					break;
				case KeyEvent.VK_A:
					sendDir(C.DIR_W);
					break;
				case KeyEvent.VK_D:
					sendDir(C.DIR_E);
					break;
				case KeyEvent.VK_UP:
					sendDir(C.DIR_N);
					break;
				case KeyEvent.VK_DOWN:
					sendDir(C.DIR_S);
					break;
				case KeyEvent.VK_LEFT:
					sendDir(C.DIR_W);
					break;
				case KeyEvent.VK_RIGHT:
					sendDir(C.DIR_E);
					break;
			}
			lastMoveSend = code;
		}
	}

	//##########################################################
	//##########################################################
	//    SHIT FOLLOWING
	//##########################################################
	//##########################################################

	@Override
	public void keyTyped(KeyEvent e) {

	}


	@Override
	public void keyReleased(KeyEvent e) {

	}

	@Override
	public void windowOpened(WindowEvent e) {

	}

	@Override
	public void windowClosed(WindowEvent e) {

	}

	@Override
	public void windowIconified(WindowEvent e) {

	}

	@Override
	public void windowDeiconified(WindowEvent e) {

	}

	@Override
	public void windowActivated(WindowEvent e) {

	}

	@Override
	public void windowDeactivated(WindowEvent e) {

	}


	@Override
	public void componentMoved(ComponentEvent componentEvent) {

	}

	@Override
	public void componentShown(ComponentEvent componentEvent) {

	}

	@Override
	public void componentHidden(ComponentEvent componentEvent) {

	}

}
