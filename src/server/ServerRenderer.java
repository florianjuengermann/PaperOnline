package server;

import constants.C;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;

public class ServerRenderer extends Thread {
	JFrame frame;
	BufferStrategy bufferStrategy;
	BufferedImage offImg;
	Graphics2D offGr;
	private GraphicsConfiguration config = GraphicsEnvironment.getLocalGraphicsEnvironment()
			.getDefaultScreenDevice()
			.getDefaultConfiguration();

	GameManager gm;

	public ServerRenderer(GameManager gm) {
		this.gm = gm;
		JFrame frame = new JFrame("SERVER");
		frame.setMinimumSize(new Dimension(300, 300));
		frame.setSize(new Dimension(C.FRAME_SIZE, C.FRAME_SIZE+30));
//		frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
		frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		frame.setIgnoreRepaint(true);


		Canvas canvas = new Canvas(config);
		canvas.setSize(C.FRAME_SIZE, C.FRAME_SIZE);
		frame.add(canvas);
		frame.setVisible(true);

		canvas.createBufferStrategy(2);
		bufferStrategy = canvas.getBufferStrategy();

		offImg = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB); // EXPENSIVE!!!
		offGr = (Graphics2D) offImg.getGraphics();
	}

	@Override
	public void run() {
		long lastDraw = System.nanoTime();
		while (!this.isInterrupted()) {
			synchronized (gm) {
				long currTickTime = gm.startTime + (gm.tickCounter - 1) * C.DELAY;
				float tickPercentage = (System.currentTimeMillis() - currTickTime) / (float) C.DELAY;


				gm.gm.draw(offGr, 10, 10, 15, tickPercentage);// -> offImg
			}
			Graphics2D buffGr = (Graphics2D) bufferStrategy.getDrawGraphics();

			buffGr.drawImage(offImg, 0, 0, null);
			buffGr.dispose();
			bufferStrategy.show();
			Toolkit.getDefaultToolkit().sync();

			long nanoSleep = lastDraw + (long) 16e6 - System.nanoTime();

			lastDraw = System.nanoTime();
			try {
				if (nanoSleep > 0)
					Thread.sleep(nanoSleep / (long) 1e6, (int) (nanoSleep % (long) 1e6));
			} catch (InterruptedException ignored) {
			}
		}
	}
}
