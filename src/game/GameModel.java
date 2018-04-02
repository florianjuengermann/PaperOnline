package game;

import constants.C;

import java.awt.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import static java.lang.Math.round;

public class GameModel {
	public Map map;
	private List<Player> players = new LinkedList<>();
	private Player[] playersById = new Player[128];

	private static float EPSILON = 1e-3f;

	public GameModel copyTo() {
		return copyTo(null);
	}

	public GameModel copyTo(GameModel to) {
		if (to == null)
			to = new GameModel(map.w);
		// copy map
		for (int i = 0; i < map.w; i++) {
			System.arraycopy(map.map[i], 0, to.map.map[i], 0, map.w);
		}
		// copy players
		to.players.clear();
		for (int i = 0; i < 128; i++)
			to.playersById[i] = null;
		for (Player n : players) {
			to.addPlayer(new Player(n, to), false);
		}
		// done.
		return to;
	}

	public GameModel(int mapsize) {
		map = new Map(mapsize, this);
	}

	public void mainTick() {
		for (Player p : players)
			p.clearFlags();
		for (Player p : players) {
			p.move(); // makes all stuff
			if (p.isDead != C.PLS_ALIVE)
				zombies.add(p); // will be removed later **IF** removeZombies is called!!!!!!
		}
		// flags are ready now
	}

	Queue<Player> zombies = new LinkedList<>();

	public void removeZombies() {
		for (Player zombie : zombies) {
			removePlayer(zombie);
		}
	}

	public void removePlayer(Player p) {
		playersById[p.id] = null;
		players.remove(p);
		// delete player from map
		map.remove(p.id); // if he isn´t there anymore... dont care.
	}

	public Player playerById(byte id) {
		return playersById[id];
	}

	public void addPlayer(Player p, boolean spawn) {
		assert playersById[p.id] == null;
		if (spawn)
			map.spawn(p.id, p.x, p.y);
		playersById[p.id] = p;
		players.add(p);
	}


	public List<Player> getPlayers() {
		return players;
	}


	public synchronized void draw(Graphics2D g, int ox, int oy, float scale, float perc) {
//		if(perc  > 0.9)
//		System.out.println(perc);
		// border
		g.setColor(Color.DARK_GRAY);
		g.fillRect(ox, oy, round(scale * (map.w + 2)), round(scale * (map.w + 2)));
		// background
		g.setColor(Color.GRAY);
		g.fillRect(round(ox + scale), round(oy + scale),
				round(scale * (map.w)), round(scale * (map.w)));

		for (int i = 0; i < map.w; i++) {
			for (int j = 0; j < map.w; j++) {
				byte mapid = map.map[i][j];
				if (mapid != 0) {
					if (mapid > 0)
						g.setColor(playerById((byte) Math.abs(mapid)).col);
					else
						g.setColor(playerById((byte) Math.abs(mapid)).colb);
					fillSquare(g, i + 1, j + 1, ox, oy, scale, false);
				}
			}
		}
		// players
		for (Player p : players) {
			g.setColor(p.col);
			fillSquare(g, p.lastX + 1 + perc * (p.x - p.lastX), p.lastY + 1 + perc * (p.y - p.lastY), ox, oy, scale, true);
		}
	}


	public static void fillSquare(Graphics2D g, float x, float y, int ox, int oy, float s, boolean border) {
		fillRect(g, x, y, 1, 1, ox, oy, s, border);
	}

	public static void fillRect(Graphics2D g, float x, float y, float width, float height, int ox, int oy, float s, boolean border) {
		float posX = x * s + s * width;
		float posY = y * s + s * height;
		int rwidth = round(posX + EPSILON) - round(x * s);
		int rheight = round(posY + EPSILON) - round(y * s);
		g.fillRect(round(x * s) + ox, round(y * s) + oy, rwidth, rheight);
		if (border) {
			g.setColor(Color.BLACK);
			g.drawRect(round(x * s) + ox, round(y * s) + oy, rwidth, rheight);
		}
	}

}
