package constants;

import java.awt.*;

public class C {
	// requests (client)
	public static final byte REQ_JOIN = 1;
	public static final byte REQ_MOVE = 3, REQ_LEAVE = 4;

	//answers (server)
	public static final byte UPDATE_MOVEMENT = 101,
			UPDATE_KILL = 102,
			UPDATE_CLAIM = 103,
			UPDATE_JOIN = 104;
	public static final byte ANS_WELCOME = 2;

	// messages
	public static final String MSG_LEAVE = "%s left the game.";
	public static final String MSG_WALL = "%s crashed into a wall.";
	public static final String MSG_OWNTRAIL = "%s ran into his own trail.";
	public static final String MSG_KILL = "%s killed %s.";
	public static final String MSG_KILLED = "%s was killed by %s.";

	// reasons for a player to leave, are broadcasted
	public static final byte CODE_LEAVE = 11; // player requested to leave
	public static final byte CODE_WALL = 12; // player ran into a wall
	public static final byte CODE_OWNTRAIL = 15; // player ran into his own trail
	public static final byte CODE_KILLED = 13; // player was killed
	public static final byte CODE_ERR = 14; // connection error

	// FLAGS in Player-OBJEKTEN
	// player states
	public static final byte PLS_ALIVE = 0;
	public static final byte PLS_WALL = 1, PLS_KILLED = 2, PLS_OWNTRAIL = 3;
	// directions
	public static final byte DIR_N = 0, DIR_E = 2, DIR_S = 1, DIR_W = 3;



	public static final long DELAY = 500; // ms

	// CLIENT


	private static Color[] cols = new Color[]{
			new Color(0, 97, 255),
			new Color(158, 0, 0),
			new Color(32, 167, 27),
			new Color(255, 187, 0),
			new Color(255, 70, 0),
	};
	public static int FRAME_SIZE = 800;

	// TODO könnte besser sein
	// man kümmere sich zunächst um andere Problemchen.
	public static Color brighten(Color col) {
		int r = 255 - (255 - col.getRed()) / 2;
		int g = 255 - (255 - col.getGreen()) / 2;
		int b = 255 - (255 - col.getBlue()) / 2;
		return new Color(r, g, b);
	}

	private static int currcol = 0;

	public static Color getUnusedColor() {
		return cols[(currcol++) % cols.length];
	}


	public static int MODEL_COUNT = 10;


}