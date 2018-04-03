import client.ClientMain;
import server.ServerMain;

import javax.swing.*;
import java.awt.*;

public class Main {

	static boolean ss = false;
	static ServerMain sm = null;
	static boolean DEBUG = true;
	public static int standardport = 5555;

	public static void main(String args[]) {


		if (args.length > 0) {
			if (args[0].equals("s") || args[0].equals("server")) {
				int port = standardport;
				if (args.length >= 2) {
					try {
						port = Integer.parseInt(args[1]);
						if (port < 0 || port > 65535)
							throw new NumberFormatException();
					} catch (NumberFormatException e) {
						System.out.println("ERROR: invalid parameter, exected port number.\n");
						printHelp();
					}
				}
				System.out.println("Launching server on port " + port + "...");
				new ServerMain(port);
			} else {
				System.out.println("ERROR: invalid parameter, use 's' for starting the server.\n");
				printHelp();
			}
		} else {
			System.out.println("Launching GUI…");
			startSetup();
		}
	}

	private static void printHelp() {
		System.out.println("U S A G E:   ");
		System.out.println("--------------------------------------");
		System.out.println("java -jar PaperOnline.jar [s [<port>]]\n");
	}

	static String s;

	private static void startSetup() {
		JFrame frame = new JFrame("Start PaperOnline");
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setLayout(new FlowLayout());
		JButton bServer = new JButton("Start server");
		JButton bClient = new JButton("Start game");

		bServer.addActionListener(actionEvent -> {
			if (ss) {
				sm.shutdown(); // todo sm is null when pressing "stop server" btn
				bServer.setText("Start server");
				ss = false;
			} else {
				if (DEBUG)
					new Thread(() -> new ServerMain(standardport)).start();
				else
					new Thread(() -> new ServerMain((s = JOptionPane.showInputDialog("Enter port (" + standardport + " is default)")).isEmpty() ? standardport : Integer.parseInt(s))).start();
				bServer.setText("Stop server");
				ss = true;
			}
		});
		bClient.addActionListener(actionEvent -> new Thread(ClientMain::new).start());
		frame.add(bServer);
		frame.add(bClient);
		frame.pack();
		frame.setLocation(200, 200);
		frame.setResizable(false);
		frame.setVisible(true);
	}
}
