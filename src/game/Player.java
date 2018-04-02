package game;

import constants.C;

import java.awt.*;

public class Player {
	final GameModel gm;
	public final byte id;
	public int x, lastX;
	public int y, lastY;
	public int dir;
	public final String name;
	final Color col, colb;

	// flags
	public byte isDead = C.PLS_ALIVE; // contains message id
	public byte wasKilledByWhom = -1; // this field is ignored if isDead != C.PLS_KILLED
	public boolean hasClaimed = false;

	void clearFlags() {
		assert isDead == C.PLS_ALIVE; // todo
		assert wasKilledByWhom == -1;
		hasClaimed = false;
	}

	public Player(GameModel gm, String name, byte id, int x, int y, int dir) {
		this.id = id;
		this.gm = gm;
		this.x = lastX = x;
		this.y = lastY = y;
		this.dir = dir;
		this.name = name;
		col = C.getUnusedColor(); // todo na ja.
		colb = C.brighten(col);
	}

	Player(Player p, GameModel to) {
		id = p.id;
		gm = to;
		x = p.x;
		y = p.y;
		dir = p.dir;
		name = p.name;
		col = p.col;
		colb = p.colb;
		// flags are ok.
	}

	void move() {
		if (isDead != C.PLS_ALIVE)
			return; // this player just died :D
		boolean onTrail = false;
		if (gm.map.map[x][y] != id) { // this is not our territory
			gm.map.map[x][y] = (byte) (-id); // place trail
			onTrail = true;
		}
		lastX = x;
		lastY = y;
		switch (dir) {
			case C.DIR_N:
				y--;
				break;
			case C.DIR_S:
				y++;
				break;
			case C.DIR_E:
				x++;
				break;
			case C.DIR_W:
				x--;
				break;
		}
		if (x < 0 || y < 0 || x == gm.map.w || y == gm.map.w) {
			// ran into a wall
			isDead = C.PLS_WALL;
			gm.map.remove(id);
			// todo maybe provoke ^^ aioobe
			x = -20;
			y = -20;
			return;
		}
		byte mapid = gm.map.map[x][y];
		if (mapid != 0) {
			if (onTrail && mapid == id) {
				// claim
				gm.map.fill(id);
				hasClaimed = true;
				// check if this killed some players
				for (Player p : gm.getPlayers()) {
					if (p.isDead == C.PLS_ALIVE && p != this
							&& gm.map.map[p.x][p.y] == id) {
						p.isDead = C.PLS_KILLED;
						p.wasKilledByWhom = id;
						// gm.map.remove is not necessary because fill already deleted the player´s stuff
					}
				}
			} else if (mapid == -id) {
				// suicide, hit own trail
				isDead = C.PLS_OWNTRAIL;
				gm.map.remove(id);
			} else /*mapid is enemy id*/ if (mapid < 0) {
				// enemy tail
				// kill enemy
				// todo it should be impossible that player p is dead....
				Player p = gm.playerById((byte) Math.abs(mapid));

				p.isDead = C.PLS_KILLED;
				p.wasKilledByWhom = id;
			}
		}
	}
}
